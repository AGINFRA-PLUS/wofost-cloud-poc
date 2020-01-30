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
package nl.wur.agrodatacube.client

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import akka.event.LoggingAdapter
import nl.wur.agrodatacube.client.AgroDataCubeConnector.FromGeoJSON
import spray.json.JsObject
import spray.json.DefaultJsonProtocol._


class MeteoReader {

  case class FieldMeteoStation(var fieldId: Long = 0, var stationId: Long = 0, var rank: Int = 0,
                               var distance: Double = 0.0) extends FromGeoJSON {
    override def fromGeoJSON(geomJson: Option[JsObject], propsJson: Option[JsObject]): Unit = {
      // ignore the geometry
      //      this.geomJson = geomJson match {
      //        case Some(v) => v.toString()
      //        case None => ""
      //      }
      if (propsJson.isDefined) {
        this.fieldId = propsJson.get.fields("fieldid").convertTo[Long]
        this.stationId = propsJson.get.fields("meteostationid").convertTo[Long]
        this.rank = propsJson.get.fields("rank").convertTo[Int]
        this.distance = propsJson.get.fields("distance").convertTo[Double]
      }
    }
  }

  case class MeteoStation(var stationId: Long = 0,
                          var name: String = "", var wmoCode: Long = 0, var lon: Double = 0.0,
                          var lat: Double = 0.0, var alt: Double = 0.0, var source: String = "",
                          var provider: String = "") extends FromGeoJSON {
    override def fromGeoJSON(geomJson: Option[JsObject], propsJson: Option[JsObject]): Unit = {
      // ignore the geometry
      //      this.geomJson = geomJson match {
      //        case Some(v) => v.toString()
      //        case None => ""
      //      }
      if (propsJson.isDefined) {
        this.stationId = propsJson.get.fields("meteostationid").convertTo[Long]
        this.name = propsJson.get.fields("name").convertTo[String]
        this.lon = propsJson.get.fields("lon").convertTo[Double]
        this.lat = propsJson.get.fields("lat").convertTo[Double]
        this.alt = propsJson.get.fields("alt").convertTo[Double]

        if (propsJson.get.fields.contains("wmocode")) {
          this.wmoCode = propsJson.get.fields("wmocode").convertTo[Long]
        }
        if (propsJson.get.fields.contains("source")) {
          this.source = propsJson.get.fields("source").convertTo[String]
        }
        if (propsJson.get.fields.contains("provider")) {
          this.provider = propsJson.get.fields("provider").convertTo[String]
        }
      }
    }
  }

  case class MeteoData(var stationId: Long = 0, var date: String = "",
                       var meanTemp: Double = 0.0, var minTemp: Double = 0.0, var maxTemp: Double = 0.0,
                       var sunshineDuration: Double = 0.0, var globalRadiation: Double = 0.0,
                       var precipitation: Double = 0.0,
                       var windspeed: Double = 0.0,
                       var meanSeaLevelPressure: Double = 0.0, var meanDailyCloudCover: Double = 0.0,
                       var meanHumidity: Double = 0.0, var maxHumidity: Double = 0.0,
                       var potentialEvapoTranspiration: Double = 0.0
                      ) extends FromGeoJSON {
    override def fromGeoJSON(geomJson: Option[JsObject], propsJson: Option[JsObject]): Unit = {
      // ignore the geometry
      //      this.geomJson = geomJson match {
      //        case Some(v) => v.toString()
      //        case None => ""
      //      }
      if (propsJson.isDefined) {
        // see https://git.wur.nl/rande001/AgroDataCube/wikis/Meteo-data-afhandeling
        // some defensive programming required
        this.stationId = propsJson.get.fields("meteostationid").convertTo[Long]
        this.date = propsJson.get.fields("datum").convertTo[String]
        this.meanTemp = getDoublePropertyOrElse(propsJson.get, "mean_temperature", 0.0)
        this.minTemp = getDoublePropertyOrElse(propsJson.get, "min_temperature", 0.0)
        this.maxTemp = getDoublePropertyOrElse(propsJson.get, "max_temperature", 0.0)
        this.sunshineDuration = getDoublePropertyOrElse(propsJson.get, "sunshine_duration", 0.0)
        this.globalRadiation = getDoublePropertyOrElse(propsJson.get, "global_radiation", 0.0)
        // ... and even more
        this.precipitation = getDoublePropertyOrElse(propsJson.get, "precipitation", 0.0)
        if (this.precipitation < 0) {
          this.precipitation = 0.0
        }
        this.windspeed = getDoublePropertyOrElse(propsJson.get, "windspeed", 0.0)
        this.meanSeaLevelPressure = getDoublePropertyOrElse(propsJson.get, "mean_sea_level_pressure", 0.0)
        this.meanDailyCloudCover = getDoublePropertyOrElse(propsJson.get, "mean_daily_cloud_cover", 0.0)
        this.meanHumidity = getDoublePropertyOrElse(propsJson.get, "mean_humidity", 0.0)
        this.maxHumidity = getDoublePropertyOrElse(propsJson.get, "max_humidity", 0.0)
        this.potentialEvapoTranspiration = getDoublePropertyOrElse(propsJson.get, "potential_evapotranspiration", 0.0)
      }
    }
  }

  private def getDoublePropertyOrElse(obj: JsObject, prop: String, default: Double): Double = {
    if (obj.fields.contains(prop)) {
      obj.fields(prop).convertTo[Double]
    } else
      default
  }

  def getFieldMeteoStations(fieldId: Long, token: String, log: LoggingAdapter,
                            maxResults: Int = AgroDataCubeConnector.MAX_PAGE_SIZE): Either[Throwable, List[FieldMeteoStation]] = {
    val requestUrl =
      s"""
         |${AgroDataCubeConnector.BASE_URL}${AgroDataCubeConnector.FIELDS_RESOURCE}/
         |$fieldId/${AgroDataCubeConnector.METEOSTATIONS_RESOURCE}
         |?${AgroDataCubeConnector.OUTPUT_EPSG_WGS84}
         |&${AgroDataCubeConnector.PAGE_SIZE}=$maxResults
     """.trim.stripMargin.replaceAll("\n", "")

    AgroDataCubeConnector.handleSinglePageForRequest[FieldMeteoStation](requestUrl, () => FieldMeteoStation(), token, log)
  }

  def getMeteoStation(stationId: Long, token: String, log: LoggingAdapter,
                      maxResults: Int = AgroDataCubeConnector.MAX_PAGE_SIZE): Either[Throwable, List[MeteoStation]] = {
    val requestUrl =
      s"""
         |${AgroDataCubeConnector.BASE_URL}${AgroDataCubeConnector.METEOSTATIONS_RESOURCE}/$stationId
         |?${AgroDataCubeConnector.PAGE_SIZE}=$maxResults
     """.trim.stripMargin.replaceAll("\n", "")

    AgroDataCubeConnector.handleSinglePageForRequest[MeteoStation](requestUrl, () => MeteoStation(), token, log)
  }

  def getMeteoData(stationId: Long, startDate: LocalDate, endDate: LocalDate, token: String, log: LoggingAdapter,
                   maxResults: Int = AgroDataCubeConnector.MAX_PAGE_SIZE): Either[Throwable, List[MeteoData]] = {
    val requestUrl =
      s"""
         |${AgroDataCubeConnector.BASE_URL}${AgroDataCubeConnector.METEODATA_RESOURCE}
         |?${AgroDataCubeConnector.METEOSTATION_PARAM}=${stationId}
         |&${AgroDataCubeConnector.FROMDATE_PARAM}=${startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))}
         |&${AgroDataCubeConnector.TODATE_PARAM}=${endDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))}
         |&${AgroDataCubeConnector.OUTPUT_EPSG_WGS84}
         |&${AgroDataCubeConnector.PAGE_SIZE}=$maxResults
     """.trim.stripMargin.replaceAll("\n", "")

    AgroDataCubeConnector.handleSinglePageForRequest[MeteoData](requestUrl, () => MeteoData(), token, log)
  }

}
