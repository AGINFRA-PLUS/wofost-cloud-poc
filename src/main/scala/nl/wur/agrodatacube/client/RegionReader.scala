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

import akka.event.LoggingAdapter
import nl.wur.agrodatacube.client.AgroDataCubeConnector.FromGeoJSON
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject

class RegionReader {

  // TODO: implement when issues with AgroDataCube Provinces resource have been fixed

  case class Province(var geomJson: String = "", var provinceId: Long = 0, var name: String = "",
                      var area: Double = 0.0, var perimeter: Double = 0.0) extends FromGeoJSON {
    override def fromGeoJSON(geomJson: Option[JsObject], propsJson: Option[JsObject]): Unit = {
      this.geomJson = geomJson match {
        case Some(v) => v.toString()
        case None => ""
      }
      if (propsJson.isDefined) {
        this.provinceId = propsJson.get.fields("id").convertTo[Long]
        this.name = propsJson.get.fields("name").convertTo[String]
        this.perimeter = propsJson.get.fields("perimeter").convertTo[Double]
        this.area = propsJson.get.fields("area").convertTo[Double]
      }
    }
  }

  def getProvinces(token: String, log: LoggingAdapter, noGeom: Boolean = false,
                   maxResults: Int = AgroDataCubeConnector.MAX_PAGE_SIZE): Either[Throwable, List[Province]] = {
    val requestUrl =
      s"""
         |${AgroDataCubeConnector.BASE_URL}${AgroDataCubeConnector.PROVINCES_RESOURCE}
         |?${AgroDataCubeConnector.OUTPUT_EPSG_WGS84}
         |&noclip${if (noGeom) "&result=nogeom" else ""}
         |&${AgroDataCubeConnector.PAGE_SIZE}=$maxResults
     """.trim.stripMargin.replaceAll("\n", "")

    AgroDataCubeConnector.handleSinglePageForRequest[Province](requestUrl, () => Province(), token, log)
  }

}
