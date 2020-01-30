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
package nl.wur.wofostshell.parameters

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import nl.wur.wiss.mathutils.{Interpolator, InterpolatorExtrapolationType}
import nl.wur.wiss.core._
import nl.wur.wiss.meteoutils.{InMemoryMeteoReader, MeteoElement, MeteoReader}
import nl.wur.wissmodels.wofost.WofostModel.Controller
import spray.json._

import scala.collection.JavaConverters._
import scala.collection.immutable.IndexedSeq

/*
  A trait and case classes are used here to represent the data that needs to
  be marshalled to and from JSON using the (Akka) Spray framework.
 */

sealed trait VarInfo {
  def kind: String
  def name: String
  def units: String
  def markedFinal: Boolean
  def markedDeleted: Boolean
}

// TODO: ucOwner has been removed from ParXChange VarInfo, need to update for that

final case class VarInt(name: String, units: String,
                  markedFinal: Boolean, markedDeleted: Boolean,
                  value: Int, kind: String = "Int") extends VarInfo

final case class VarDouble(name: String, units: String,
                     markedFinal: Boolean, markedDeleted: Boolean,
                     value: Double, kind: String = "Double") extends VarInfo

final case class VarString(name: String, units: String,
                     markedFinal: Boolean, markedDeleted: Boolean,
                     value: String, kind: String = "String"
                    ) extends VarInfo

// value of Java LocalDate will be represented as a String
final case class VarDateString(name: String, units: String,
                   markedFinal: Boolean, markedDeleted: Boolean,
                   value: String, kind: String = "DateString"
                  ) extends VarInfo

// Java ArrayList[Controller] will be represented as Seq[String]
final case class VarControllerList(name: String, units: String,
                                  markedFinal: Boolean, markedDeleted: Boolean,
                                   controllers: Seq[String], kind: String = "ControllerList"
                                 ) extends VarInfo

// units and extrapolation type will be represented as Strings
final case class VarInterpolator(name: String, units: String,
                                 markedFinal: Boolean, markedDeleted: Boolean,
                                 extrapolationType: String,
                                 unitsX: String, unitsY: String,
                                 points: IndexedSeq[(Double, Double)],
                                 kind: String = "Interpolator"
                          ) extends VarInfo

// several case classes are used to represent meteo station weather observations
final case class VarMeteoData(name: String, units: String,
                              markedFinal: Boolean, markedDeleted: Boolean,
                              location: MeteoLocation,
                              variables: IndexedSeq[MeteoObservedVars],
                              values: Seq[MeteoObservedVals],
                              kind: String = "MeteoData"
                       ) extends VarInfo

final case class MeteoLocation(latitudeDD: Double, longitudeDD: Double, altitudeM: Double)
final case class MeteoObservedVars(meteoElement: String, units: String)
final case class MeteoObservedVals(date: String, values: IndexedSeq[Double])

// support trait collecting all json format instances, needed for the marshalling to work
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val varIntFormat: RootJsonFormat[VarInt] = jsonFormat6(VarInt)
  implicit val varDoubleFormat: RootJsonFormat[VarDouble] = jsonFormat6(VarDouble)
  implicit val varStringFormat: RootJsonFormat[VarString] = jsonFormat6(VarString)
  implicit val varDateStringFormat: RootJsonFormat[VarDateString] = jsonFormat6(VarDateString)
  implicit val varControllerListFormat: RootJsonFormat[VarControllerList] = jsonFormat6(VarControllerList)
  implicit val varInterpolatorFormat: RootJsonFormat[VarInterpolator] = jsonFormat9(VarInterpolator)

  implicit val meteoLocationFormat: RootJsonFormat[MeteoLocation] = jsonFormat3(MeteoLocation)
  implicit val meteoObservedVarsFormat: RootJsonFormat[MeteoObservedVars] = jsonFormat2(MeteoObservedVars)
  implicit val meteoObserveredValsFormat: RootJsonFormat[MeteoObservedVals] = jsonFormat2(MeteoObservedVals)

  implicit val varMeteoDataFormat: RootJsonFormat[VarMeteoData] = jsonFormat8(VarMeteoData)
}

/**
  * Serialize a ParXChange object into a more robust format. Since ParXChange
  * is very generic and can store complete Objects, there is support for some
  * common used types. This might need to be extended in the future.
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
object ParXChangeMarshaller extends Directives with JsonSupport {
  // types to match on
  private val jInterpolator = classOf[nl.wur.wiss.mathutils.Interpolator]
  private val jMeteoReader = classOf[nl.wur.wiss.meteoutils.MeteoReader]
  private val jInteger = classOf[java.lang.Integer]
  private val jDouble = classOf[java.lang.Double]
  private val jString = classOf[java.lang.String]
  private val jLocalDate = classOf[java.time.LocalDate]
  private val jControllerArrayList = classOf[java.util.ArrayList[nl.wur.wiss.core.SimController]]

  // date format to use for LocalDate conversion to string
  private val dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  // JSON writer for supported case classes
  implicit val varInfoJsonWriter: JsonWriter[Seq[VarInfo]] = new JsonWriter[Seq[VarInfo]] {
    def write(data: Seq[VarInfo]): JsArray = JsArray(elements = data.map {
      case v: VarInt => v.toJson
      case v: VarDouble => v.toJson
      case v: VarString => v.toJson
      case v: VarDateString => v.toJson
      case v: VarControllerList => v.toJson
      case v: VarInterpolator => v.toJson
      case v: VarMeteoData => v.toJson
      case _ => throw DeserializationException("No kind specified, unable to serialize to json")
    }.toVector)
  }

  // json reader for supported case classes
  implicit val varInfoJsonReader: JsonReader[Seq[VarInfo]] = new JsonReader[Seq[VarInfo]] {
    override def read(json: JsValue): Seq[VarInfo] = {
      json.asInstanceOf[JsArray].elements.map { value =>
        value.asJsObject.getFields("kind") match {
          case Seq(JsString(kind)) => {
            kind match {
              case "Int" => value.convertTo[VarInt]
              case "Double" => value.convertTo[VarDouble]
              case "String" => value.convertTo[VarString]
              case "DateString" => value.convertTo[VarDateString]
              case "ControllerList" => value.convertTo[VarControllerList]
              case "Interpolator" => value.convertTo[VarInterpolator]
              case "MeteoData" => value.convertTo[VarMeteoData]
            }
          }
          case _ => throw DeserializationException("No kind specified, unable to deserialize the json")
        }
      }
    }
  }

  /**
    * Represents the data in the specified ParXChange object into a sequence
    * of VarInfo case classes. These make it possible to use the Akka HTTP's
    * marshalling and unmarshalling infrastructure.
    *
    * @param p ParXChange to process
    * @return sequence of created VarInfo objects
    */
  def serialize(p: ParXChange): Seq[VarInfo] = {
    var result = Seq[VarInfo]()

    p.forEach { varInfo =>
      val variable = varInfo.cls match {
        case `jInteger` => VarInt(
          name = varInfo.ucVarName, units = varInfo.scientificUnit.getUnitCaption,
          markedFinal = varInfo.markedFinal, markedDeleted = varInfo.markedDeleted,
          value = p.get(varInfo).asInstanceOf[Int])

        case `jDouble` => VarDouble(
          name = varInfo.ucVarName, units = varInfo.scientificUnit.getUnitCaption,
          markedFinal = varInfo.markedFinal, markedDeleted = varInfo.markedDeleted,
          value = p.get(varInfo).asInstanceOf[java.lang.Double].toDouble)

        case `jString` => VarString(
          name = varInfo.ucVarName, units = varInfo.scientificUnit.getUnitCaption,
          markedFinal = varInfo.markedFinal, markedDeleted = varInfo.markedDeleted,
          value = p.get(varInfo).asInstanceOf[String])

        case `jLocalDate` => VarDateString(
          name = varInfo.ucVarName, units = varInfo.scientificUnit.getUnitCaption,
          markedFinal = varInfo.markedFinal, markedDeleted = varInfo.markedDeleted,
          value = p.get(varInfo).asInstanceOf[LocalDate].format(dtf))

        case `jControllerArrayList` =>
          val controllers = p.get(varInfo).asInstanceOf[util.ArrayList[Controller]]
          val controllerList = for (i <- 0 until controllers.size()) yield controllers.get(i).name()
          VarControllerList(
            name = varInfo.ucVarName, units = varInfo.scientificUnit.getUnitCaption,
            markedFinal = varInfo.markedFinal, markedDeleted = varInfo.markedDeleted,
            controllers = controllerList)

        case `jInterpolator` =>
          // extract enough information to be able to recreate the Interpolator
          val interpolator = p.get(varInfo).asInstanceOf[Interpolator]
          val points = for (i <- 0 until interpolator.count())
            yield (interpolator.getX(i), interpolator.getY(i))

          VarInterpolator(
            name = varInfo.ucVarName, units = varInfo.scientificUnit.getUnitCaption,
            markedFinal = varInfo.markedFinal, markedDeleted = varInfo.markedDeleted,
            extrapolationType = interpolator.getExtrapolationType.name(),
            unitsX = interpolator.getxUnit().getUnitCaption,
            unitsY = interpolator.getyUnit().getUnitCaption,
            points = points)

        case `jMeteoReader` =>
          // extract the key meteo information
          val meteoReader = p.get(varInfo).asInstanceOf[MeteoReader]

          // get meteo location details
          val location = MeteoLocation(latitudeDD = meteoReader.getLatitudeDD, longitudeDD = meteoReader.getLongitudeDD,
            altitudeM = meteoReader.getAltitudeM)

          // get info about observed meteo elements (temperature, precipitation, etc.)
          val elements = meteoReader.getSourceElements.asScala.toIndexedSeq
          val meteoVars = for (elem <- elements)
            yield MeteoObservedVars(elem.name(), meteoReader.getNativeUnit(elem).getUnitCaption)

          var meteoVals = Seq[MeteoObservedVals]()
          val size = meteoReader.asInstanceOf[InMemoryMeteoReader].size()
          if (size > 0) {
            // index over all dates
            val firstDate = meteoReader.getSourceFirstDate
            val lastDate = meteoReader.getSourceLastDate

            val dates: IndexedSeq[LocalDate] = (0L to (lastDate.toEpochDay - firstDate.toEpochDay))
              .map(days => firstDate.plusDays(days))

            // retrieve all meteo observations
            for (date <- dates) {
              val values = for (elem <- elements)
                yield meteoReader.getValue(date, elem, meteoReader.getNativeUnit(elem))
              meteoVals = meteoVals :+ MeteoObservedVals(date.format(dtf), values)
            }
          }

          // collect data in a case class
          VarMeteoData(
            name = varInfo.ucVarName, units = varInfo.scientificUnit.getUnitCaption,
            markedFinal = varInfo.markedFinal, markedDeleted = varInfo.markedDeleted,
            location = location,
            variables = meteoVars,
            values = meteoVals
            )

        case t => throw new IllegalArgumentException(s"Unsupported class type in ParXChange object: $t")
      }
      result = result :+ variable
    }
    result
  }

  /**
    * Creates a ParXChange objects and fills it with variable definitions and
    * values based on the specified sequence of VarInfo instances.
    *
    * @param d sequence of VarInfo objects to progress
    * @return filled ParXChange instance
    */
  def deserialize(d: Seq[VarInfo]): ParXChange = {
    val result = new ParXChange()

    d.foreach {
      case VarInt(name, units, markedFinal, markedDeleted, value, _) =>
        result.set(name, classOf[java.lang.Integer], markedFinal,
          value.asInstanceOf[java.lang.Integer], ScientificUnit.fromTxt(units).head)
        if (markedDeleted)
          result.delete(name, classOf[java.lang.Integer])

      case VarDouble(name, units, markedFinal, markedDeleted, value, _) =>
        result.set(name, classOf[java.lang.Double], markedFinal,
          value.asInstanceOf[java.lang.Double], ScientificUnit.fromTxt(units).head)
        if (markedDeleted)
          result.delete(name, classOf[java.lang.Double])

      case VarString(name, units, markedFinal, markedDeleted, value, _) =>
        result.set(name, classOf[java.lang.String], markedFinal,
          value.asInstanceOf[java.lang.String], ScientificUnit.fromTxt(units).head)
        if (markedDeleted)
          result.delete(name, classOf[java.lang.String])

      case VarDateString(name, units, markedFinal, markedDeleted, value, _) =>
        result.set(name, classOf[LocalDate], markedFinal,
          LocalDate.parse(value, dtf), ScientificUnit.fromTxt(units).head)
        if (markedDeleted)
          result.delete(name, classOf[LocalDate])

      case VarControllerList(name, units, markedFinal, markedDeleted, controllerNames, _) =>
        val controllerList = new util.ArrayList[Controller]()
        for (i <- controllerNames.indices) controllerList.add(Controller.valueOf(controllerNames(i)))
        result.set(name, classOf[util.ArrayList[Controller]], markedFinal,
          controllerList, ScientificUnit.fromTxt(units).head)
        if (markedDeleted)
          result.delete(name, classOf[util.ArrayList[Controller]])

      case VarInterpolator(name, units, markedFinal, markedDeleted, extrapolationType, unitsX, unitsY, points, _) =>
        val interpolator = new Interpolator(name, ScientificUnit.fromTxt(unitsX).head, ScientificUnit.fromTxt(unitsY).head)
        interpolator.setExtrapolationType(InterpolatorExtrapolationType.valueOf(extrapolationType))
        points.foreach { p => interpolator.add(p._1, p._2) }
        result.set(name, classOf[Interpolator], markedFinal, interpolator, ScientificUnit.fromTxt(units).head)
        if (markedDeleted)
          result.delete(name, classOf[Interpolator])

      case VarMeteoData(name, units, markedFinal, markedDeleted, location, variables, values, _) =>
        // create an in-memory meteo data storage
        val meteoReader = new InMemoryMeteoReader()
        meteoReader.setLocation(location.longitudeDD, location.latitudeDD, location.altitudeM)

        // add the meteo elements (maintain order since it is needed later)
        var meteoElements = Seq[MeteoElement]()
        var elementUnits = Seq[ScientificUnit]()
        variables.foreach { variable =>
          meteoElements = meteoElements :+ MeteoElement.valueOf(variable.meteoElement)
          elementUnits = elementUnits :+ ScientificUnit.findByTxt(variable.units)
        }
        meteoReader.setSourceElements(new util.HashSet[MeteoElement](meteoElements.asJava))

        // add the meteo data
        values.foreach { value =>
          val meteoData = new InMemoryMeteoReader.MeteoData()
          meteoData.day = LocalDate.parse(value.date, dtf)
          // transfer value for each meteo element (values are in the same order)
          for (i <- meteoElements.indices) {
            // need to make sure all data is added using native units of the meteo reader
            val convertedValue = ScientificUnitConversion.convert(meteoElements(i).name(), value.values(i),
              elementUnits(i), meteoReader.getNativeUnit(meteoElements(i)))
            meteoElements(i) match {
              case MeteoElement.E0 => meteoData.e0 = convertedValue
              case MeteoElement.ES0 => meteoData.es0 = convertedValue
              case MeteoElement.ET0 => meteoData.et0 = convertedValue
              case MeteoElement.PR_CU => meteoData.precipitation = convertedValue
              case MeteoElement.Q_CU => meteoData.radiation = convertedValue
              case MeteoElement.TM_AV => meteoData.temperatureAvg = convertedValue
              case MeteoElement.TM_MN => meteoData.temperatureMin = convertedValue
              case MeteoElement.TM_MX => meteoData.temperatureMax = convertedValue
              case MeteoElement.VP_AV => meteoData.vapourPressure = convertedValue
              case MeteoElement.WS2_AV => meteoData.windSpeed2M = convertedValue
              case MeteoElement.WS10_AV => meteoData.windSpeed10M = convertedValue
              case t => throw new IllegalArgumentException(s"Unsupported meteo element, unable to deserialize to ParXChange: $t")
            }
          }
          meteoReader.put(meteoData.day, meteoData)
        }
        result.set(name, classOf[MeteoReader], markedFinal, meteoReader, ScientificUnit.fromTxt(units).head)
        if (markedDeleted)
          result.delete(name, classOf[MeteoReader])

      case t => throw new IllegalArgumentException(s"Unsupported class type, unable to deserialize to ParXChange: $t")
    }
    result
  }

  /**
    * Turns the specified sequence of VarInfo instances into a JSON text
    * representation.
    *
    * For each type of object described in the JSON a specific case class
    * is required for representing it. These case classes should all implement
    * the VarInfo trait.
    *
    * When new types of objects are introduced this class needs to be updated
    * to include new case classes as well.
    *
    * @param data sequence of VarInfo objects to create JSON from
    * @return JSON representation
    */
  def asJSON(data: Seq[VarInfo]): String = data.toJson.prettyPrint

  /**
    * Turns the specified JSON text into a sequence of VarInfo instances.
    *
    * For each type of object described in the JSON a specific case class
    * is required for representing it. These case classes should all implement
    * the VarInfo trait.
    *
    * When new types of objects are introduced this class needs to be updated
    * to include new case classes as well.
    *
    * @param json JSON representation to process
    * @return sequence of VarInfo objects derived from the JSON
    */
  def fromJSON(json: String): Seq[VarInfo] = json.parseJson.convertTo[Seq[VarInfo]]
}
