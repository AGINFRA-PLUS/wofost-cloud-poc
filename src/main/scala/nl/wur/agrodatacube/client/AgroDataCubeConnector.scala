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

import java.net.{HttpURLConnection, URL}
import java.util.Locale

import akka.event.LoggingAdapter
import spray.json._

import scala.collection.mutable.ListBuffer


/**
  * Handles connections to the [[http://agrodatacube.wur.nl AgroDataCube]] via
  * its API, using HTTP GET or POST requests. AgroDataCube responses are in
  * GeoJSON format. Case classes implementing the
  * [[nl.wur.agrodatacube.client.AgroDataCubeConnector.FromGeoJSON]] trait will
  * be automatically converted.
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
object AgroDataCubeConnector {

  /** Main URL to use to connect to AgroDataCube. */
  val BASE_URL = "https://agrodatacube.wur.nl/api/v2/rest/"
  /** REST resource for crop fields (parcel) information. */
  val FIELDS_RESOURCE = "fields"
  /** REST resource for information about provinces. */
  val PROVINCES_RESOURCE = "regions/provences"
  /** REST resource for accessing meteo station information. */
  val METEOSTATIONS_RESOURCE = "meteostations"
  /** REST resource for reading meteo observation data. */
  val METEODATA_RESOURCE = "meteodata"
  /** REST resource for retrieving physical soil parameters. */
  val SOILPARAMS_RESOURCE = "soilparams"
  val METEOSTATION_PARAM = "meteostation"
  val FROMDATE_PARAM = "fromdate"
  val TODATE_PARAM = "todate"
  val INPUT_EPSG = "epsg"
  val OUTPUT_EPSG = "output_epsg"
  val EPSG_WGS84 = "4326"
  val EPSG_RD = "28992"
  val PAGE_SIZE = "page_size"
  val PAGE_OFFSET = "page_offset"
  val MAX_PAGE_SIZE = 1000

  val INPUT_EPSG_WGS84 = s"$INPUT_EPSG=$EPSG_WGS84"
  val INPUT_EPSG_RD = s"$INPUT_EPSG=$EPSG_RD"
  val OUTPUT_EPSG_WGS84 = s"$OUTPUT_EPSG=$EPSG_WGS84"
  val OUTPUT_EPSG_RD = s"$OUTPUT_EPSG=$EPSG_RD"
  val PAGE_SIZE_MAX = s"$PAGE_SIZE=$MAX_PAGE_SIZE"


  def agroDataCubeGet(url: String, token: String,
                        log: LoggingAdapter,
                        requestMethod: String = "GET",
                        connectTimeout: Int = 20000,
                        readTimeout: Int = 120000
                      ): Either[Throwable, String] =
  {
    log.debug(s"request $requestMethod: $url")
    try {
      val startTime = System.nanoTime()
      val connection = requestMethod match {
        case "POST" =>
          val urlParts = url.split("\\?")
          val con = new URL(urlParts(0)).openConnection.asInstanceOf[HttpURLConnection]
          con.setConnectTimeout(connectTimeout)
          con.setReadTimeout(readTimeout)
          con.setRequestMethod("POST")
          con.setRequestProperty("token", token)
          if (urlParts.size > 1) {
            val postDataBytes = urlParts(1).getBytes("UTF-8")
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            con.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length))
            con.setDoOutput(true)
            con.getOutputStream.write(postDataBytes)
          }
          con
        case _ =>
          val con = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
          con.setConnectTimeout(connectTimeout)
          con.setReadTimeout(readTimeout)
          con.setRequestMethod("GET")
          con.setRequestProperty("token", token)
          con
      }

      val content = connection.getResponseCode match {
        case 200 =>
          val inputStream = connection.getInputStream
          val body = io.Source.fromInputStream(inputStream).mkString
          if (inputStream != null) inputStream.close()
          body
        case code =>
          throw new RuntimeException(s"HTTP request failed, status $code - ${connection.getResponseMessage}")
      }

      val endTime = System.nanoTime()
      log.debug("response: %d - %s (%.3f sec)".
        formatLocal(Locale.US, connection.getResponseCode, connection.getResponseMessage, (endTime - startTime)/1e9))

      Right(content)
    } catch {
      case e: Throwable => Left(e)
    }
  }


  /**
    * Transfers GeoJSON information already split into a geometry and a
    * properties JsObject into the case class members.
    */
  trait FromGeoJSON {
    def fromGeoJSON(geomJson: Option[JsObject], propsJson: Option[JsObject])
  }

  def handleSinglePageForRequest[T <: FromGeoJSON](request: String, fac: () => T,
                                                   token: String, log: LoggingAdapter,
                                                   usePost: Boolean = false): Either[Throwable, List[T]] = {
    val result = new ListBuffer[T]()

    agroDataCubeGet(request, token, log, if (usePost) "POST" else "GET") match {
      case Left(e) =>
        Left(e)
      case Right(json) =>
        val rootObj = json.parseJson.asJsObject
        if (rootObj.fields.contains("status")) {
          // intercept AgroDataCube status message in response
          val statusMsg: JsValue = rootObj.fields("status")
          Left(new RuntimeException(statusMsg.toString()))
        } else {
          try {
            val featuresJsArray = rootObj.fields("features").asInstanceOf[JsArray]

            for (featureJson <- featuresJsArray.elements) {
              val geometryJson = featureJson.asJsObject.fields("geometry") match {
                case v: JsObject => Some(v)
                case _ => None
              }
              val propertiesJson = featureJson.asJsObject.fields("properties") match {
                case v: JsObject => Some(v)
                case _ => None
              }
              val item = fac()
              item.fromGeoJSON(geometryJson, propertiesJson)
              result += item
            }

            log.debug(s"Items received: ${result.size}")
            Right(result.toList)
          } catch {
            case e: Throwable => Left(e)
          }
        }
    }
  }

}
