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
import spray.json.JsObject
import spray.json.DefaultJsonProtocol._

// TODO: requests need to be updated for changes in AgroDataCube API V2

case class SoilUnits(var id: Long = 0, var soilCode: String = "", var soilName: String = "",
                     var soilType: String = "")


case class FieldSoil(var fieldId: Long = 0, var soilParamId: Long = 0, var pawn: Int = 0,
                     var area: Double = 0.0, var perimeter: Double = 0.0,
                    ) extends FromGeoJSON {
  override def fromGeoJSON(geomJson: Option[JsObject], propsJson: Option[JsObject]): Unit = {
    // ignore the geometry
    //      this.geomJson = geomJson match {
    //        case Some(v) => v.toString()
    //        case None => ""
    //      }
    if (propsJson.isDefined) {
      this.fieldId = propsJson.get.fields("fieldid").convertTo[Long]
      this.soilParamId = propsJson.get.fields("soilparamid").convertTo[Long]
      this.pawn = propsJson.get.fields("pawn").convertTo[Int]
      this.area = propsJson.get.fields("area").convertTo[Double]
      this.perimeter = propsJson.get.fields("perimeter").convertTo[Double]
    }
  }
}

// example JSON properties:
// Some({"cac03":0.0,"end_depth":20.0,"orgstof_p10":3.0,"ph_kcl":5.4,"silt":22.0,"ph_p10":4.5,
// "gebruik":"G","eenheid":"kHn21","hor_code":"1Ap","m50_p10":130.0,"lutum":13.0,"ph_p90":6.5,
// "bodem_nr":4040,"layer_nr":1,"lutum_p90":35.0,"m50":155.0,"leem_p90":50.0,"orgstof":8.0,
// "dichtheid":1.213312679,"lutum_p10":8.0,"soilparamid":307,"start_depth":0.0,"orgstof_p90":12.0,
// "opp_aandeel":49.0,"a_waarde":1,"materiaal":410.0,"leem":35.0,"leem_p10":25.0,"m50_p90":180.0,
// "staring_bouwsteen":"B8"})
case class SoilParams(var soilParamId: Long = 0,
                      var oppAandeel: Double = 0.0, var bodemNr: Int = 0,
                      var eenheid: String = "", var layerNr: Int = 0, var horCode: String = "",
                      var startDepth: Double = 0.0, var endDepth: Double = 0.0,
                      var dichtheid: Double = 0.0, var materiaal: Double = 0.0,
                      var aWaarde: Int = 0, var staringBouwsteen: String = "") extends FromGeoJSON {
  override def fromGeoJSON(geomJson: Option[JsObject], propsJson: Option[JsObject]): Unit = {
    // ignore the geometry
    //      this.geomJson = geomJson match {
    //        case Some(v) => v.toString()
    //        case None => ""
    //      }
    if (propsJson.isDefined) {
      this.soilParamId = propsJson.get.fields("soilparamid").convertTo[Long]
      this.layerNr = propsJson.get.fields("layer_nr").convertTo[Int]
      this.startDepth = propsJson.get.fields("start_depth").convertTo[Double]
      this.endDepth = propsJson.get.fields("end_depth").convertTo[Double]
      this.oppAandeel = propsJson.get.fields("opp_aandeel").convertTo[Double]
      this.bodemNr = propsJson.get.fields("bodem_nr").convertTo[Int]
      this.eenheid = propsJson.get.fields("eenheid").convertTo[String]
      this.horCode = propsJson.get.fields("hor_code").convertTo[String]
      this.dichtheid = propsJson.get.fields("dichtheid").convertTo[Double]
      this.materiaal = propsJson.get.fields("materiaal").convertTo[Double]
      this.aWaarde = propsJson.get.fields("a_waarde").convertTo[Int]
      this.staringBouwsteen = propsJson.get.fields("staring_bouwsteen").convertTo[String]
    }
  }
}

class SoilReader {
  def getFieldSoils(fieldId: Long, token: String, log: LoggingAdapter,
                    maxResults: Int = AgroDataCubeConnector.MAX_PAGE_SIZE): Either[Throwable, List[FieldSoil]] = {
    val requestUrl =
      s"""
         |${AgroDataCubeConnector.BASE_URL}${AgroDataCubeConnector.FIELDS_RESOURCE}/
         |$fieldId/${AgroDataCubeConnector.SOILPARAMS_RESOURCE}
         |?${AgroDataCubeConnector.OUTPUT_EPSG_WGS84}
         |&${AgroDataCubeConnector.PAGE_SIZE}=$maxResults
     """.trim.stripMargin.replaceAll("\n", "")

    AgroDataCubeConnector.handleSinglePageForRequest[FieldSoil](requestUrl, () => FieldSoil(), token, log)
  }

  def getSoilParams(soilParamId: Long, token: String, log: LoggingAdapter,
                    maxResults: Int = AgroDataCubeConnector.MAX_PAGE_SIZE): Either[Throwable, List[SoilParams]] = {
    val requestUrl =
      s"""
         |${AgroDataCubeConnector.BASE_URL}${AgroDataCubeConnector.SOILPARAMS_RESOURCE}/$soilParamId
         |?${AgroDataCubeConnector.PAGE_SIZE}=$maxResults
     """.trim.stripMargin.replaceAll("\n", "")

    AgroDataCubeConnector.handleSinglePageForRequest[SoilParams](requestUrl, () => SoilParams(), token, log)
  }

}
