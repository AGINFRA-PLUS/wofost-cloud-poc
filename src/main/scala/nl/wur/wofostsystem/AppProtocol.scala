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
package nl.wur.wofostsystem

import java.util.Locale

import nl.wur.wiss.core.{ParXChange, SimXChange}
import nl.wur.wofostshell.parameters.VarInfo

import scala.compat.Platform
import spray.json._
import spray.json.DefaultJsonProtocol._


/**
  * Protocol for the WOFOST system, an actor based system for running
  * WOFOST crop model simulations.
  */
object AppProtocol {

  // TODO disentangle study specs and which actor can handle each one
  //  e.g. have sweep study handle by Researcher, while an AgroDataCubeGeometryStudy
  //  can not be directly handled by a Researcher.

  sealed trait StudyType
  case object SimpleStudy extends StudyType
  case object SimpleSweepStudy extends StudyType
  case object AgroDataCubeFieldIdStudy extends StudyType
  case object AgroDataCubeFieldIdsStudy extends StudyType
  case object AgroDataCubeGeometryStudy extends StudyType

  /**
    * Common description of a crop model simulation study.
    */
  sealed trait StudySpec {
    def id: String
    def title: String
    def description: String
    def studyType: StudyType
    def timeoutSec: Int
  }

  final case class SimpleStudySpec(id: String, title: String, description: String,
                                   inputFileName: String, timeoutSec: Int = 30) extends StudySpec {
    override def studyType: StudyType = SimpleStudy
  }

  final case class SimpleSweepStudySpec(id: String, title: String, description: String,
                                        sweepParamName: String, sweepParamOwner: String,
                                        stepSize: Double = 10.0,
                                        nrRunsBeforeBaseValue: Int = 5, nrRunsAfterBaseValue: Int = 5,
                                        inputFileName: String,
                                        timeoutSec: Int = 30) extends StudySpec {
    override def studyType: StudyType = SimpleSweepStudy
  }

  final case class AgroDataCubeFieldIdStudySpec(id: String, title: String, description: String,
                                                selectionFieldId: Long,
                                                selectionYear: Int,
                                                timeoutSec: Int = 30) extends StudySpec {
    override def studyType: StudyType = AgroDataCubeFieldIdStudy
  }

  final case class AgroDataCubeFieldIdsStudySpec(id: String, title: String, description: String,
                                                 selectionFieldIds: Seq[Long],
                                                 selectionYear: Int,
                                                 timeoutSec: Int = 30) extends StudySpec {
    override def studyType: StudyType = AgroDataCubeFieldIdsStudy
  }

  final case class AgroDataCubeGeometryStudySpec(id: String, title: String, description: String,
                                                 selectionGeometryWkt: String, selectionGeometryEpsg: String,
                                                 selectionYear: Int, selectionCropCode: String,
                                                 pageSize: Int = 1000, pageOffset: Int = 0,
                                                 timeoutSec: Int = 30) extends StudySpec {
    override def studyType: StudyType = AgroDataCubeGeometryStudy
  }


  /**
    * Spray JSON marshaller for StudySpecs.
    *
    * @see https://github.com/spray/spray-json
    */
  object StudySpecMarshaller {
    implicit val SimpleStudySpecFormat: RootJsonFormat[SimpleStudySpec] = jsonFormat5(SimpleStudySpec)
    implicit val SimpleSweepStudySpecFormat: RootJsonFormat[SimpleSweepStudySpec] = jsonFormat10(SimpleSweepStudySpec)
    implicit val AgroDataCubeFieldIdStudySpecFormat: RootJsonFormat[AgroDataCubeFieldIdStudySpec] = jsonFormat6(AgroDataCubeFieldIdStudySpec)
    implicit val AgroDataCubeFieldIdsStudySpecFormat: RootJsonFormat[AgroDataCubeFieldIdsStudySpec] = jsonFormat6(AgroDataCubeFieldIdsStudySpec)
    implicit val AgroDataCubeGeometryStudySpecFormat: RootJsonFormat[AgroDataCubeGeometryStudySpec] = jsonFormat10(AgroDataCubeGeometryStudySpec)

    implicit val studySpecJsonReader: JsonReader[StudySpec] = new JsonReader[StudySpec] {
      override def read(json: JsValue): StudySpec = {
        json.asJsObject.getFields("studyType") match {
          case Seq(JsString(kind)) => {
            kind match {
              case "SimpleStudy" => json.convertTo[SimpleStudySpec]
              case "SimpleSweepStudy" => json.convertTo[SimpleSweepStudySpec]
              case "AgroDataCubeFieldIdStudy" => json.convertTo[AgroDataCubeFieldIdStudySpec]
              case "AgroDataCubeFieldIdsStudy" => json.convertTo[AgroDataCubeFieldIdsStudySpec]
              case "AgroDataCubeGeometryStudy" => json.convertTo[AgroDataCubeGeometryStudySpec]
            }
          }
          case _ => throw DeserializationException("No studyType specified, unable to deserialize the json")
        }
      }
    }

    def fromJson(json:String): StudySpec = json.parseJson.convertTo[StudySpec]
  }

  // --------------------------------------------------------------------------

  /**
    * Trait for actor messages.
    */
  trait Msg extends java.io.Serializable {
    def dateInMillis: Long
  }

  /**
    * Case class for passing optional location (meta) information between actors.
    *
    * @param label Text label for the location
    * @param geometryWkt Optional geometry as WKT
    * @param geometryEpsg Optional EPSG code for the WKT geometry specified
    * @param fieldId Optional ID of the crop parcel being simulated
    * @param area Optional area of the region being simulated
    * @param perimeter Optional perimeter of the region begin simulated
    */
  final case class LocationInfo(label: String,
                                geometryWkt: Option[String], geometryEpsg: Option[String],
                                fieldId: Option[Long],
                                area: Option[Float], perimeter: Option[Float]) {
    def asJson(): String = {
      s"""
        |"label": "$label"
        |${geometryWkt map (t => s""", "geometry_wkt": "$t"""") getOrElse ""}
        |${geometryEpsg map (t => s""", "geometry_epsg": "$t"""") getOrElse ""}
        |${fieldId map (t => s""", "field_id": $t""") getOrElse ""}
        |${area map (t => """, "area": %.2f""".formatLocal(Locale.US, t)) getOrElse ""}
        |${perimeter map (t => """, "perimeter": %.2f""".formatLocal(Locale.US, t)) getOrElse ""}
        |""".trim.stripMargin.replaceAll("\n", "")
    }
  }

  // --- AgroDataCubeLibrarian ------------------------------------------------

  final case class Prepare(study: AgroDataCubeFieldIdsStudySpec, fieldIdIndex: Int, dateInMillis: Long = Platform.currentTime)
  extends Msg {
    require(study != null, "Study is required.")
    require((fieldIdIndex >= 0) && (fieldIdIndex < study.selectionFieldIds.size), "Valid fieldIdIndex is required.")
  }

  final case class PreparationOk(study: AgroDataCubeFieldIdsStudySpec, fieldIdIndex: Int,
                                 locationInfo: Option[LocationInfo],
                                 modelInputs: Seq[VarInfo], dateInMillis: Long = Platform.currentTime)
    extends Msg {
    require(study != null, "Study is required.")
    require((fieldIdIndex >= 0) && (fieldIdIndex < study.selectionFieldIds.size), "Valid fieldIdIndex is required.")
    require(modelInputs.nonEmpty, "modelInputs are required.")
  }

  final case class PreparationFailed(study: AgroDataCubeFieldIdsStudySpec, fieldIdIndex: Int, reason: String, dateInMillis: Long = Platform.currentTime)
    extends Msg {
    require(study != null, "Study is required.")
    require((fieldIdIndex >= 0) && (fieldIdIndex < study.selectionFieldIds.size), "Valid fieldIdIndex is required.")
    require(reason.nonEmpty, "Reason is required.")
  }

  // --- Researcher -----------------------------------------------------------

  final case class Research(study: StudySpec, locationInfo: Option[LocationInfo], modelInputs: Seq[VarInfo], dateInMillis: Long = Platform.currentTime)
  extends Msg {
    require(study != null, "Study is required.")
    require(modelInputs.nonEmpty, "Model input parameters are required.")
  }

  final case class PassResearch(study: StudySpec, locationInfo: Option[LocationInfo], dateInMillis: Long = Platform.currentTime)
  extends Msg {
    require(study != null, "Study is required.")
  }

  final case class ResearchOk(study: StudySpec, locationInfo: Option[LocationInfo], summary: Map[String, Map[String, Double]],
                              states: Option[SimXChange], dateInMillis: Long = Platform.currentTime)
  extends Msg {
    require(study != null, "Study is required.")
    require(summary.nonEmpty, "Summary are required.")
  }

  final case class ResearchFailed(study: StudySpec, locationInfo: Option[LocationInfo], reason: String, dateInMillis: Long = Platform.currentTime)
    extends Msg {
    require(study != null, "Study is required.")
    require(reason.nonEmpty, "Reason is required.")
  }

  // --- Simulator ------------------------------------------------------------

  final case class RunSimulation(simID: String, locationInfo: Option[LocationInfo], simData: ParXChange, dateInMillis: Long = Platform.currentTime)
  extends Msg {
    require(simData != null, "SimData is required.")
  }

  final case class SimulationOk(simID: String, locationInfo: Option[LocationInfo], simData: SimXChange, dateInMillis: Long = Platform.currentTime)
  extends Msg {
    require(simData != null, "SimData is required.")
  }

  final case class SimulationFailed(simID: String, locationInfo: Option[LocationInfo], reason: String, dateInMillis: Long = Platform.currentTime)
    extends Msg {
    require(reason.nonEmpty, "Reason is required.")
  }

  // --- Reporter -------------------------------------------------------------

  final case class StartNewReport(study: StudySpec, dateInMillis: Long = Platform.currentTime)
    extends Msg {
    require(study != null, "Study is required.")
  }

  final case class AddToReport(study: StudySpec, locationInfo: Option[LocationInfo],
                               results: Map[String, Map[String, Double]], dateInMillis: Long = Platform.currentTime)
    extends Msg {
    require(study != null, "Study is required.")
    require(results.nonEmpty, "Results are required.")
  }

  final case class SendFinalReport(study: StudySpec, dateInMillis: Long = Platform.currentTime) extends Msg {
    require(study != null, "Study is required.")
  }

  final case class Report(study: StudySpec, reportText: String, dateInMillis: Long = Platform.currentTime) extends Msg {
    require(study != null, "Study is required.")
    require(reportText.nonEmpty, "ReportText is required.")
  }
}
