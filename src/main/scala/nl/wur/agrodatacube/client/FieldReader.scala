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

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import akka.event.LoggingAdapter
import nl.wur.agrodatacube.client.AgroDataCubeConnector.FromGeoJSON
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject


/**
  * Retrieves crop field (parcel) data from AgroDataCube.
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
class FieldReader {


  /**
    * Data about a crop field (parcel).
    *
    * @param geomJson  Geometry as WKT, when available.
    * @param fieldId   The ID of the field.
    * @param year      Year in which the field existed.
    * @param area      Area of the field (in m2).
    * @param perimeter Perimeter of the field (in m).
    * @param cropCode  Code of the crop grown on the field.
    * @param cropName  Name of the crop grown on the field.
    */
  case class Field(var geomJson: String = "", var fieldId: Long = 0,
                   var year: Int = 0,
                   var area: Float = 0.0f,
                   var perimeter: Float = 0.0f,
                   var cropCode: String = "", var cropName: String = "") extends FromGeoJSON {
    override def fromGeoJSON(geomJson: Option[JsObject], propsJson: Option[JsObject]): Unit = {
      this.geomJson = geomJson match {
        case Some(v) => v.toString()
        case None => ""
      }
      if (propsJson.isDefined) {
        this.fieldId = propsJson.get.fields("fieldid").convertTo[Long]
        this.year = propsJson.get.fields("year").convertTo[Int]
        this.area = propsJson.get.fields("area").convertTo[Float]
        this.perimeter = propsJson.get.fields("perimeter").convertTo[Float]
        this.cropCode = propsJson.get.fields("crop_code").convertTo[String]
        this.cropName = propsJson.get.fields("crop_name").convertTo[String]
      }
    }
  }


  /**
    * Data about a field count. AgroDataCube supports requests that only
    * return the number of matches for given query parameters, as GeoJSON.
    * This case class extracts the 'count' value and keeps it as an member.
    *
    * @param geomJson Geometry as WKT, when available.
    * @param count    The number of matches.
    */
  case class FieldCount(var geomJson: String = "", var count: Long = 0) extends FromGeoJSON {
    override def fromGeoJSON(geomJson: Option[JsObject], propsJson: Option[JsObject]): Unit = {
      this.geomJson = geomJson match {
        case Some(v) => v.toString()
        case None => ""
      }
      if (propsJson.isDefined) {
        this.count = propsJson.get.fields("nrofhits").convertTo[Long]
      }
    }
  }


  /**
    * Retrieves the data for the crop field with the specified field ID.
    *
    * @param fieldId    ID of the field to retrieve the data for.
    * @param token      AgroDataCube access token to use.
    * @param log        Log to write messages to.
    * @param noGeom     Exclude field geometry from the response (false).
    * @param maxResults Maximum number of results to retrieve (1).
    * @return Either exception or list of matching fields.
    */
  def getFieldForId(fieldId: Long, token: String, log: LoggingAdapter,
                    noGeom: Boolean = false,
                    maxResults: Int = 1): Either[Throwable, List[Field]] = {
    val requestUrl =
      s"""
         |${AgroDataCubeConnector.BASE_URL}${AgroDataCubeConnector.FIELDS_RESOURCE}/$fieldId
         |?${AgroDataCubeConnector.OUTPUT_EPSG_WGS84}
         |&noclip${if (noGeom) "&result=nogeom" else ""}
         |&${AgroDataCubeConnector.PAGE_SIZE}=$maxResults
     """.trim.stripMargin.replaceAll("\n", "")

    AgroDataCubeConnector.handleSinglePageForRequest[Field](requestUrl, () => Field(), token, log)
  }

  /**
    * Retrieves the data for all the crop fields that existed in time at the
    * specified point location.
    *
    * @param lat        Latitude of location.
    * @param long       Longitude of location.
    * @param token      AgroDataCube access token to use.
    * @param log        Log to write messages to.
    * @param noGeom     Exclude field geometry from the response (false).
    * @param noClip     Do not clip the field boundaries to the specified point (true).
    * @param maxResults Maximum number of results to retrieve.
    * @return Either exception or list of matching fields.
    */
  def getFieldsForPointLocation(lat: Double, long: Double, token: String, log: LoggingAdapter,
                                noGeom: Boolean = false,
                                noClip: Boolean = true,
                                maxResults: Int = AgroDataCubeConnector.MAX_PAGE_SIZE
                               ): Either[Throwable, List[Field]] = {
    val requestUrl =
      s"""
         |${AgroDataCubeConnector.BASE_URL}${AgroDataCubeConnector.FIELDS_RESOURCE}
         |?geometry=POINT($lat%20$long)${if (noGeom) "&result=nogeom" else ""}${if (noClip) "&noclip" else ""}
         |&${AgroDataCubeConnector.INPUT_EPSG_WGS84}&${AgroDataCubeConnector.OUTPUT_EPSG_WGS84}
         |&${AgroDataCubeConnector.PAGE_SIZE}=$maxResults
     """.trim.stripMargin.replaceAll("\n", "")

    AgroDataCubeConnector.handleSinglePageForRequest[Field](requestUrl, () => Field(), token, log)
  }

  /**
    * Retrieves the the data for all the fields within a specified geometry,
    * optionally further filtered by crop code and year.
    *
    * @param locationWkt  Geometry as WKT for the search region.
    * @param locationEpsg EPSG of the coordinate system used for the geometry.
    * @param token        AgroDataCube access token to use.
    * @param log          Log to write messages to.
    * @param year         Optional year for selecting crop fields.
    * @param cropCode     Optional crop code for selecting crop fields.
    * @param noGeom       Exclude field geometry from the response (false).
    * @param noClip       Do not clip the field boundaries to the specified point (true).
    * @param maxResults   Maximum number of results to retrieve.
    * @param pageOffset   Paging parameter for retrieving next result pages.
    * @return Either exception or list of matching fields.
    */
  def getFieldsForWktLocation(locationWkt: String, locationEpsg: String, token: String, log: LoggingAdapter,
                              year: Option[Int],
                              cropCode: Option[String],
                              noGeom: Boolean = false,
                              noClip: Boolean = true,
                              maxResults: Int = AgroDataCubeConnector.MAX_PAGE_SIZE,
                              pageOffset: Int = 0
                             ): Either[Throwable, List[Field]] = {
    val requestUrl =
      s"""
         |${AgroDataCubeConnector.BASE_URL}${AgroDataCubeConnector.FIELDS_RESOURCE}
         |?geometry=${URLEncoder.encode(locationWkt, StandardCharsets.UTF_8.toString)}
         |&${AgroDataCubeConnector.INPUT_EPSG}=$locationEpsg
         |&${AgroDataCubeConnector.OUTPUT_EPSG_WGS84}
         |${if (year.isDefined) s"&year=${year.get}"}
         |${if (cropCode.isDefined) s"&cropcode=${URLEncoder.encode(cropCode.get, StandardCharsets.UTF_8.toString)}"}
         |${if (noGeom) "&result=nogeom" else ""}
         |${if (noClip) "&noclip" else ""}
         |&${AgroDataCubeConnector.PAGE_SIZE}=$maxResults
         |&${AgroDataCubeConnector.PAGE_OFFSET}=$pageOffset
     """.trim.stripMargin.replaceAll("\n", "")

    // FIXME: use HTTP POST when issue in AgroDataCube has been fixed
    AgroDataCubeConnector.handleSinglePageForRequest[Field](requestUrl, () => Field(), token, log, false)
  }

  /**
    * Returns the number of matching fields within the specified geometry, and
    * optionally filtered by year and crop code.
    *
    * @param locationWkt  Geometry as WKT for the search region.
    * @param locationEpsg EPSG of the coordinate system used for the geometry.
    * @param token        AgroDataCube access token to use.
    * @param log          Log to write messages to.
    * @param year         Optional year for selecting crop fields.
    * @param cropCode     Optional crop code for selecting crop fields.
    * @return Either exception or list of FieldCount objects.
    */
  def getFieldCountForLocation(locationWkt: String, locationEpsg: String, token: String, log: LoggingAdapter,
                               year: Option[Int], cropCode: Option[String]
                              ): Either[Throwable, List[FieldCount]] = {
    val requestUrl =
      s"""
         |${AgroDataCubeConnector.BASE_URL}${AgroDataCubeConnector.FIELDS_RESOURCE}
         |?geometry=${URLEncoder.encode(locationWkt, StandardCharsets.UTF_8.toString)}
         |&${AgroDataCubeConnector.INPUT_EPSG}=$locationEpsg
         |&${AgroDataCubeConnector.OUTPUT_EPSG_WGS84}
         |${if (year.isDefined) s"&year=${year.get}"}
         |${if (cropCode.isDefined) s"&cropcode=${URLEncoder.encode(cropCode.get, StandardCharsets.UTF_8.toString)}"}
         |&result=nrofhits
         |&noclip
         |&${AgroDataCubeConnector.PAGE_SIZE}=1
         |&${AgroDataCubeConnector.PAGE_OFFSET}=0
     """.trim.stripMargin.replaceAll("\n", "")

    // FIXME: use HTTP POST when issue in AgroDataCube has been fixed
    AgroDataCubeConnector.handleSinglePageForRequest[FieldCount](requestUrl, () => FieldCount(), token, log, false)
  }

}
