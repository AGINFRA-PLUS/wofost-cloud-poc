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
package nl.wur.aginfrasystem

import java.net.URL

import akka.actor.ActorRef

import scala.compat.Platform
import scala.concurrent.duration.FiniteDuration

object AppProtocol {

  sealed trait JobSpec {
    def id: String
    def title: String
    def description: String
    def timeoutSec: Int
  }

  final case class FieldIdsJobSpec(id: String, title: String, description: String,
                                   fieldIds: Seq[Long],
                                   simulationYear: Int,
                                   timeoutSec: Int = 300) extends JobSpec

  final case class AgroDataCubeGeometryJobSpec(id: String, title: String, description: String,
                                               selectionGeometryWkt: String, selectionGeometryEpsg: String,
                                               selectionYear: Int, selectionCropCode: String,
                                               agroDataCubeToken: String, pageSize: Int = 1000, pageOffset: Int = 0,
                                               timeoutSec: Int = 300
                                              ) extends JobSpec

  // --------------------------------------------------------------------------

  /**
    * Trait for actor messages.
    */
  trait Msg extends java.io.Serializable {
    def dateInMillis: Long
  }

  final case class ReportInfo(title: String, geometryWkt: String, geometryEpsg: String,
                              parcelYear: Int, parcelCropCode: String,
                              batchSize: Int, nrBatches: Int, batchTimeoutSec: Int)

  // --- JobStarter -----------------------------------------------------------

  final case class StartJob(job: JobSpec, gCubeToken: String, sender: Option[ActorRef], dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
    require(gCubeToken != null, "Token is required.")
  }

  final case class StartJobDelayed(msg: StartJob, delay: FiniteDuration, dateInMillis: Long = Platform.currentTime) extends Msg

  final case class StartJobOk(job: JobSpec, statusUrl: URL, dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
    require(statusUrl != null, "StatusUrl is required.")
  }

  final case class StartJobFailed(job: JobSpec, reason: String, dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
    require(reason.nonEmpty, "Reason is required.")
  }

  // --- JobMonitor -----------------------------------------------------------

  final case class MonitorJob(job: JobSpec, jobStatusUrl: String, gCubeToken: String, dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
    require(jobStatusUrl != null, "StatusUrl is required.")
    require(gCubeToken != null, "Token is required.")
  }

  final case class PassFailedJob(job: JobSpec, reason: String, dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
    require(reason.nonEmpty, "Reason is required.")
  }

  final case class JobFinishedOk(job: JobSpec, logFileUrl: Option[String], simStatesUrl: Option[String],
                                 simSummaryUrl: Option[String], dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
  }

  final case class JobFailed(job: JobSpec, reason: String, logFileUrl: Option[String] = None,
                             dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
    require(reason.nonEmpty, "Reason is required.")
  }

  // --- LogProcessor ---------------------------------------------------------

  final case class ProcessLog(job: JobSpec, logFileUrl: Option[String], gCubeToken: String, dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
    require(gCubeToken != null, "Token is required.")
  }

  final case class LogProcessingFailed(job: JobSpec, reason: String, logFileUrl: Option[String],
                             dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
    require(reason.nonEmpty, "Reason is required.")
  }

  final case class LogProcessingOk(job: JobSpec, errorCount: Int, details: Map[String, Any],
                                   logFileUrl: Option[String],
                                   dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
  }

  // --- SummaryProcessor -----------------------------------------------------

  final case class ProcessSummary(job: JobSpec, simSummaryUrl: Option[String], gCubeToken: String, dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
    require(gCubeToken != null, "Token is required.")
  }

  final case class SummaryProcessingFailed(job: JobSpec, reason: String, simSummaryUrl: Option[String],
                                           dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
    require(reason.nonEmpty, "Reason is required.")
  }

  final case class SummaryProcessingOk(job: JobSpec, simCount: Int, details: Map[String, Any],
                                       simSummaryUrl: Option[String],
                                       dateInMillis: Long = Platform.currentTime) extends Msg {
    require(job != null, "Job is required.")
  }

  // --- Reporter -------------------------------------------------------------

  final case class StartNewReport(header: String, subHeader: String, info: ReportInfo, dateInMillis: Long = Platform.currentTime) extends Msg
  final case class AddToReport(job: JobSpec, details: Map[String, Any], dateInMillis: Long = Platform.currentTime) extends Msg
  final case class SendFinalReport(footer: String, subFooter: String, dateInMillis: Long = Platform.currentTime) extends Msg
  final case class Report(reportText: String, dateInMillis: Long = Platform.currentTime) extends Msg
}
