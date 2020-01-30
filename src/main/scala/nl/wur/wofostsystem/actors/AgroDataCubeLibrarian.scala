/* ---------------------------------------------------------------------------
 * EUROPEAN UNION PUBLIC LICENCE v. 1.2
 *
 * For licensing information please read the included LICENSE.txt file.
 *
 * Unless required by applicable law or agreed to in writing, this software
 * is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied.
 * ---------------------------------------------------------------------------
 * Author(s):
 * - Rob Knapen
 *   Wageningen University and Research
 *   The Netherlands
 *   https://www.wur.nl/
 * ---------------------------------------------------------------------------
 * Acknowledgment(s):
 * - This work has received funding from the European Union’s Horizon 2020
 *   research and innovation programme under AGINFRA PLUS project (grant
 *   agreement No 731001).
 * ---------------------------------------------------------------------------
 */
package nl.wur.wofostsystem.actors

import java.io.{File, FileNotFoundException}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.DAYS
import java.util

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.event.LoggingAdapter
import akka.routing.RoundRobinPool
import com.typesafe.config.ConfigFactory
import nl.wur.wiss.core._
import nl.wur.wiss.meteoutils.{InMemoryMeteoReader, MeteoElement}
import nl.wur.agrodatacube.client.{FieldReader, MeteoReader}
import nl.wur.wissmodels.wofost.WofostModel
import nl.wur.wissmodels.wofost.simobjects.SimMeteo
import nl.wur.wofostshell.parameters._
import nl.wur.wofostshell.readers.YamlCropReader
import nl.wur.wofostsystem.AppProtocol
import nl.wur.wofostsystem.actors.AgroDataCubeLibrarian.Done

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Failure, Success, Try}


object AgroDataCubeLibrarian {
  import AppProtocol._

  final case class Done(e: Either[PreparationOk, PreparationFailed], librarian: ActorRef)

  def props: Props = Props(new AgroDataCubeLibrarian())

  def propsWithDispatcherAndRoundRobinRouter(dispatcher: String, nrOfInstances: Int): Props = {
    props.withDispatcher(dispatcher).withRouter(RoundRobinPool(nrOfInstances = nrOfInstances))
  }
}

class AgroDataCubeLibrarian extends Actor with ActorLogging with Stash {
  import AppProtocol._

  // get the API token required for reading from the AgroDataCube
  val config = ConfigFactory.load()
  val token = config.getString("application.agrodatacube.token")

  // data caches
  val cropDataCache = collection.mutable.Map[String, Option[Seq[VarInfo]]]()
  val meteoDataCache = collection.mutable.Map[Long, Option[Seq[VarInfo]]]()

  override def receive: Receive = ready

  private def ready: Receive = {
    case m: Msg => m match {
      case r: Prepare => prepareStudy(r.study, r.fieldIdIndex)
    }
  }

  private def busy: Receive = {
    case Done(e, s) =>
      process(e, s)
      unstashAll()
      context.become(ready)
    case _ =>
      stash()
  }

  private def prepareStudy(spec: AgroDataCubeFieldIdsStudySpec, fieldIdIndex: Int): Unit = {
    context.become(busy)

    log.debug(s"Preparing data for field ${spec.selectionFieldIds(fieldIdIndex)}")
    val dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val startDate = LocalDate.of(spec.selectionYear, 1, 1)
    val endDate = LocalDate.of(spec.selectionYear, 12, 31)

    context.self ! Done(
      try {
        val fieldId = spec.selectionFieldIds(fieldIdIndex)
        val r = new FieldReader()
        r.getFieldForId(fieldId, token, log, true) match {
          case Left(e) => throw new IllegalArgumentException(s"No data found for field $fieldId", e)
          case Right(fields: List[r.Field]) =>
            val locationInfo = LocationInfo(s"agrodatacube-field-${fields.head.fieldId}",
              None, None, Some(fields.head.fieldId), Some(fields.head.area), Some(fields.head.perimeter))

            // TODO need to keep more error details in case of missing or wrong input data
            //   it is essential for problem resolution

            val cropVarInfos = cropDataCache.getOrElseUpdate(fields.head.cropCode, readCropParameters(fields.head.cropCode, log))
            val meteoVarInfos = getBestUsableMeteoData(fields.head.fieldId, startDate, endDate, log)

            // TODO get soil parameters and create VarInfo classes (for water limited production simulation)
            //  have to map soil type, soil code, BOFEK code, into soil parameters needed by WOFOST

            // TODO basic WOFOST model inputs should be checked, probably depends on type of crop
            val basics = createBasicModelInputs("optimal", startDate, endDate, s"$fieldId",
              0, startDate, startDate, startDate, endDate,
              0, 400, 100
            )

            if (cropVarInfos.isDefined && meteoVarInfos.isDefined) {
              val modelInputs = basics ++ cropVarInfos.get ++ meteoVarInfos.get
              Left(PreparationOk(spec, fieldIdIndex, Some(locationInfo), modelInputs))
            } else {
              Right(PreparationFailed(spec, fieldIdIndex, s"No usable crop parameters and/or meteo data found for field $fieldId"))
            }
        }
      } catch {
        case e @ (_: Exception | _: Error) =>
          val msg = s"Could not prepare data for field ${spec.selectionFieldIds(fieldIdIndex)}: ${e.getMessage}"
          log.error(msg, e)
          Right(PreparationFailed(spec, fieldIdIndex, msg))
      }, sender())
  }

  private def process(r: Either[PreparationOk, PreparationFailed], sender: ActorRef): Unit = {
    r fold (
      f => {
        sender ! f
      },
      s => sender ! s)
  }

  private def readCropParameters(cropCode: String, log: LoggingAdapter): Option[Seq[VarInfo]] = {
    log.debug(s"Deciding mapping for BRP crop code $cropCode")
    val (source, variety) = cropCode match {
      // crop mappings suggested by Allard de Wit:
      /*
          Winter wheat: Use Winter_wheat_103
          Spring-barley, define a new variety:
                  Spring_barley_NL:
                      <<: *springbarley
                      TSUM1:
                      -  730
                      - temperature sum from emergence to anthesis
                      - ['C.d']
                      TSUM2:
                      -  972
                      - temperature sum from anthesis to maturity
                      - ['C.d']
                      SPAN:
                      - 25.0
                      - life span of leaves growing at 35 Celsius
                      - ['d']
          Sugar beet: Use sugarbeet_601
          Grain maize: Use Grain_maize_203
          Potato: potato_701
       */
      case "233"|"234" => ("wheat_nl.yaml", "Winter_wheat_103")
      case "236" => ("barley.yaml", "Spring_barley_NL")
      case "256"|"257" => ("sugarbeet.yaml", "Sugarbeet_601")
      case "259" => ("maize.yaml", "Grain_maize_203")
      case "252"|"253"|"254"|"255"|"859"|
           "1909"|"1910"|"1911"|"1912"|"1927"|"1928"|"1929"|"1934"|"1935"|
           "2014"|"2015"|"2016"|"2017"|"2025"|"2951"|
           "3730"|"3731"|"3732"|"3792" => ("potato.yaml", "Potato_701")
      case _ =>
        log.warning(s"No mapping available for BRP crop code $cropCode.")
        return None
    }

    readCropParameterResourceFile(source) match {
      case Failure(f) =>
        log.error(s"Could not read crop parameters from resource '$source', error ${f.getMessage}")
        None
      case Success(s: String) =>
        log.debug(s"Retrieving parameters for crop code $cropCode from resource $source - $variety")
        val reader = new YamlCropReader()
        reader.read(s, s"$source - $variety")
        reader.loadCropParametersForVariety(variety)
        val params = new ParXChange()
        reader.transferTo(params)
        Some(ParXChangeMarshaller.serialize(params))
    }
  }

  /**
    * Reads the crop parameter file with the specified name from the crops
    * folder in the resources, if it exists. Some extra code is needed to
    * also be able to read resources from a fat jar.
    *
    * @param cropFileName to read from the resources folder
    * @return the contents of the file as a text string, or None
    */
  def readCropParameterResourceFile(cropFileName: String): Try[String] = {
    log.debug(s"Reading resource '$cropFileName'")
    var fileStream = getClass.getResourceAsStream(cropFileName)
    if (fileStream == null) {
      fileStream = getClass.getClassLoader.getResourceAsStream(cropFileName)
    }
    if (fileStream == null) {
      throw new RuntimeException(s"Could not find a resource with name '${cropFileName}'")
    } else {
      val lines = Source.fromInputStream(fileStream).getLines
      val result = lines.mkString("\n")
      fileStream.close()
      Success(result)
    }
  }

  /**
    * For the field from AgroDataCube matching the specified fieldId, find
    * nearest meteo station for which sufficient meteo data is available.
    * The meteo data is either retrieved from a cache or from the AgroDataCube
    * and then added to the cache. When suitable meteo data is found it is
    * returned as a sequence of VarInfo objects.
    *
    * @param fieldId to return meteo data for
    * @param startDate of required meteo data sequence
    * @param endDate of required meteo data sequence
    * @param log to write messages to
    * @return None, or the meteo data as VarInfo objects
    */
  private def getBestUsableMeteoData(fieldId: Long, startDate: LocalDate, endDate: LocalDate, log: LoggingAdapter): Option[Seq[VarInfo]] = {
    log.debug(s"Finding best usable meteo data for field $fieldId")
    findNearestMeteoStations(fieldId, log) match {
      case None =>
        log.warning(s"No meteostations data found for field $fieldId")
        None
      case Some(meteostations: List[MeteoReader#FieldMeteoStation]) =>
        if (meteostations.isEmpty) {
          log.warning(s"No meteostations data found for field $fieldId")
          None
        } else {
          meteostations.sortBy(_.rank).foreach { meteostation =>
            log.debug(s"Checking meteostation ${meteostation.stationId} for suitable meteo data for field $fieldId")
            val meteoVarInfos = meteoDataCache.getOrElseUpdate(meteostation.stationId,
              readMeteoData(meteostation.stationId, startDate, endDate, log))
            meteoVarInfos match {
              case Some(data: Seq[VarInfo]) =>
                if (data.isInstanceOf[Seq[VarMeteoData]] && data.nonEmpty &&
                  data.asInstanceOf[Seq[VarMeteoData]].head.values.size >= (DAYS.between(startDate, endDate) + 1)) {
                  log.info(s"Using meteo data from meteostation ${meteostation.stationId} for field $fieldId")
                  return Some(data)
                } else {
                  log.warning(s"Meteostation ${meteostation.stationId} has insufficient usable meteo data between ${startDate.toString} - ${endDate.toString} for field $fieldId")
                }
              case None =>
                log.warning(s"Meteostation ${meteostation.stationId} has no usable meteo data between ${startDate.toString} - ${endDate.toString} for field $fieldId")
            }
          }
          log.warning(s"None of the nearest meteostations to field $fieldId has enough usable meteo data")
          None
        }
    }
  }

  private def findNearestMeteoStations(fieldId: Long, log: LoggingAdapter): Option[List[MeteoReader#FieldMeteoStation]] = {
    log.debug(s"Finding nearest meteostation for field $fieldId")
    val m = new MeteoReader()
    m.getFieldMeteoStations(fieldId, token, log, 5) match {
      case Left(e) =>
        log.error(s"Exception ${e.getMessage}", e)
        None
      case Right(stations) =>
        Some(stations)
    }
  }

  private def readMeteoData(stationId: Long, startDate: LocalDate, endDate: LocalDate, log: LoggingAdapter): Option[Seq[VarInfo]] = {
    log.debug(s"Retrieving station information and meteo data for meteostation $stationId")
    val m = new MeteoReader()
    val stationDetails = m.getMeteoStation(stationId, token, log) match {
      case Left(e) =>
        log.error(s"No meteostation information found for meteostation $stationId, Exception ${e.getMessage}", e)
        return None
      case Right(stations: List[m.MeteoStation]) =>
        stations.head
    }

    val stationData = m.getMeteoData(stationId, startDate, endDate, token, log) match {
      case Left(e) =>
        log.error(s"No meteo observations in specified period found for meteostation $stationId, Exception ${e.getMessage}", e)
        return None
      case Right(data: List[m.MeteoData]) =>
        data
    }

    val meteoReader = new InMemoryMeteoReader()
    meteoReader.setLocation(stationDetails.lon, stationDetails.lat, stationDetails.alt)

    val meteoElements = Seq[MeteoElement](MeteoElement.TM_AV, MeteoElement.TM_MN, MeteoElement.TM_MX,
      MeteoElement.Q_CU, MeteoElement.PR_CU, MeteoElement.VP_AV, MeteoElement.WS2_AV)
    meteoReader.setSourceElements(meteoElements.toSet.asJava)

    // TODO automatically verify and set units if needed

    val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    stationData.foreach { item =>
      val meteoData = new InMemoryMeteoReader.MeteoData()
      meteoData.day = LocalDate.parse(item.date, dtf)
      meteoData.temperatureMin = item.minTemp // C
      meteoData.temperatureMax = item.maxTemp // C
      meteoData.temperatureAvg = item.meanTemp // C
      meteoData.radiation = item.globalRadiation * 0.01 // J/cm2 -> MJ/m2
      meteoData.precipitation = item.precipitation //
      meteoData.vapourPressure = item.meanSeaLevelPressure
      meteoData.windSpeed2M = item.windspeed // m/s
      meteoReader.put(meteoData.day, meteoData)
    }

    // TODO: create VarInfo objects directly to avoid the marshalling overhead
    val params = new ParXChange()
    params.set(WofostModel.METEO, classOf[nl.wur.wiss.meteoutils.MeteoReader], meteoReader, ScientificUnit.NA)

    Some(ParXChangeMarshaller.serialize(params))
  }

  private def createBasicModelInputs(simType: String, startDate: LocalDate, endDate: LocalDate, locationInfo: String,
                             istcho: Int, idem: LocalDate, idsow: LocalDate, idesow: LocalDate, idlsow: LocalDate,
                             iencho: Int, idurmx: Int, idurf: Int
                            ): Seq[VarInfo] = {
    val p = new ParXChange()

    val controllers = new util.ArrayList[WofostModel.Controller]()

    // first add environmental controller to simulation based on selected type
    // TODO: add support for:
    //    VARIANT0_OPTIMAL_PRODUCTION,
    //    VARIANT1_WATER_LIMITED,
    //    VARIANT2_OXYGEN_STRESS_ENABLED,
    //    VARIANT3_SURFACE_STORAGE_ENABLED_ALL_RAINFALL_EFFECTIVE,
    //    VARIANT4_SURFACE_STORAGE_ENABLED_NOT_ALL_RAINFALL_EFFECTIVE
    simType.toLowerCase match {
      case "optimal" => controllers add WofostModel.Controller.ENVIRONMENT_WB_CUSTOM
      case "water_limited" => controllers add WofostModel.Controller.ENVIRONMENT_WB_FREEDRAIN
      case _ => controllers add WofostModel.Controller.ENVIRONMENT_WB_CUSTOM
    }

    // add other controllers for the simulation
    controllers add WofostModel.Controller.CROP_ROTATION_1
    controllers add WofostModel.Controller.CROP_HARVESTER_1

    p.set(new ParValue(controllers, ScientificUnit.NA, WofostModel.CONTROLLERS))

    p.set(new ParValue(locationInfo, ScientificUnit.NA, "FIELD"))

    // simulation parameters
    p.set(new ParValue(startDate, ScientificUnit.NA, TimeDriver.STARTDATE))
    p.set(new ParValue(endDate, ScientificUnit.NA, TimeDriver.ENDDATE))

    // cereal type crop with fixed partitioning
    p.set(new ParValue(1, ScientificUnit.NA, WofostModel.CROPTYPE))

    // SimMeteo inputs (important ones first)
    p.set(new ParValue(0.29, ScientificUnit.NODIM, WofostModel.ANGSTA))
    p.set(new ParValue(0.42, ScientificUnit.NODIM, WofostModel.ANGSTB))

    // info on publisher of E0, ES0 and ET0 data. Can be 'AUTOMATIC', 'SimMeteo', 'SimPenman', 'SimPenmanMonteithFAO'
    p.set(new ParValue(classOf[SimMeteo].getSimpleName, ScientificUnit.NA, WofostModel.PUBLISHER_E0))
    p.set(new ParValue(classOf[SimMeteo].getSimpleName, ScientificUnit.NA, WofostModel.PUBLISHER_ES0))
    p.set(new ParValue(classOf[SimMeteo].getSimpleName, ScientificUnit.NA, WofostModel.PUBLISHER_ET0))

    // CO2 increase scenarios
    p.set(new ParValue(360.0, ScientificUnit.PPM, WofostModel.CO2REFLEVEL))
    p.set(new ParValue(2016, ScientificUnit.YEAR, WofostModel.CO2REFYEAR))
    // switched off yearly increase, e.g. for doubling in 100 years set to 3.6
    p.set(new ParValue(0.0, ScientificUnit.PPM_Y, WofostModel.CO2YEARINC))

    // DROUGHT_INDEX (only needed for WB_CUSTOM)
    p.set(new ParValue(1.0, ScientificUnit.NODIM, WofostModel.DROUGHT_INDEX))

    // start choice
    p.set(new ParValue(istcho.asInstanceOf[java.lang.Integer], ScientificUnit.NA, WofostModel.ISTCHO))
    istcho match {
      case 0 => {
        // start at crop emergence
        p.set(new ParValue(idem, ScientificUnit.DATE, WofostModel.IDEM))
      }
      case 1 => {
        // start at crop sowing
        p.set(new ParValue(idsow, ScientificUnit.DATE, WofostModel.IDSOW))
      }
      case 2 => {
        // variable sowing
        p.set(new ParValue(idesow, ScientificUnit.DATE, WofostModel.IDESOW))
        p.set(new ParValue(idlsow, ScientificUnit.DATE, WofostModel.IDLSOW))
      }
      case _ => // void
    }

    // end choice
    p.set(new ParValue(iencho.asInstanceOf[java.lang.Integer], ScientificUnit.NA, WofostModel.IENCHO))
    iencho match {
      case 2 => p.set(new ParValue(idurmx.asInstanceOf[java.lang.Integer], ScientificUnit.DAYS, WofostModel.IDURMX))
      case 3 => p.set(new ParValue(idurf.asInstanceOf[java.lang.Integer], ScientificUnit.DAYS, WofostModel.IDURF))
      case _ => // void
    }

    // TODO: check if these are plausible values
    p.set(new ParValue(0.3173, ScientificUnit.NODIM_VOLUME, WofostModel.SMFCF))
    p.set(new ParValue(0.1513, ScientificUnit.NODIM_VOLUME, WofostModel.SMW))
    p.set(new ParValue(0.4061, ScientificUnit.NODIM_VOLUME, WofostModel.SM0))
    p.set(new ParValue(0.0600, ScientificUnit.NODIM_VOLUME, WofostModel.CRAIRC))

    // TODO: create VarInfo objects directly to avoid the marshalling overhead
    ParXChangeMarshaller.serialize(p)
  }

}
