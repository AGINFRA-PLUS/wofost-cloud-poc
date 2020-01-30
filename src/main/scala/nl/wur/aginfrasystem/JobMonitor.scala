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

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import com.typesafe.config.ConfigFactory
import nl.wur.aginfrasystem.AppProtocol._
import nl.wur.aginfrasystem.JobMonitor.{CheckAgain, Done}
import nl.wur.dataminer.client.DataMinerConnector

import scala.concurrent.duration._
import scala.xml.{Elem, XML}


object JobMonitor {
  final case class Done(e: Either[JobFinishedOk, JobFailed], sender: ActorRef)
  final case class CheckAgain(job: AppProtocol.JobSpec, jobStatusUrl: String, token: String, sender: ActorRef)
  def props: Props = Props(new JobMonitor())
}


class JobMonitor extends Actor with ActorLogging with Stash {
  import context._

  private val config = ConfigFactory.load()
  private val dataMinerUrl = config.getString("application.gcube.dataminerurl")
  private val jobMonitorDelaySec = config.getInt("application.gcube.job-monitor-delay-sec")
  private val fidLogFilePath = config.getString("application.worker.output-fid-log")
  private val fidStatesFilePath = config.getString("application.worker.output-fid-simulation-states")
  private val fidSummaryFilePath = config.getString("application.worker.output-fid-simulations-summary")

  override def receive: Receive = ready

  private def ready: Receive = {
    case m: Msg => m match {
      case MonitorJob(job, jobStatusUrl, token, _) if job.isInstanceOf[JobSpec] => startMonitoring(job.asInstanceOf[JobSpec], jobStatusUrl, token, sender())
      case PassFailedJob(job, reason, _) => passFailedJob(job.asInstanceOf[JobSpec], reason, sender())
      case _ => log.warning(s"Don't know how to handle message $m")
    }
  }

  private def busy: Receive = {
    case CheckAgain(job, jobStatusUrl, token, sender) =>
      checkStatus(job, jobStatusUrl, token, sender)
    case Done(e, s) =>
      process(e, s)
      unstashAll()
      context.become(ready)
    case _ =>
      stash()
  }

  private def process(r: Either[JobFinishedOk, JobFailed], sender: ActorRef): Unit = {
    r fold (
      f => {
        sender ! f
      },
      s => sender ! s)
  }

  private def passFailedJob(job: AppProtocol.JobSpec, reason: String, sender: ActorRef): Unit = {
    context.become(busy)
    context.self ! Done(Right(JobFailed(job, s"$reason (not submitted to DataMiner)")), sender)
  }

  private def startMonitoring(job: AppProtocol.JobSpec, jobStatusUrl: String, token: String, sender: ActorRef): Unit = {
    context.become(busy)
    checkStatus(job, jobStatusUrl, token, sender)
  }

  private def extractResultFileUrl(xml: Elem, fid: String): Option[String] = {
    val results = xml \\ "Result"
    results.foreach { result =>
      result.attribute("fid") match {
        case None => None
        case Some(s) => {
          if (fid.equalsIgnoreCase(s.head.text)) {
            val resultData = (result \ "Data").text
            val resultDescription = (result \ "Description").text
            val resultMimeType = (result \ "MimeType").text
            log.debug(s"DataMiner result file $fid: $resultDescription ($resultMimeType) - $resultData")
            return Some(resultData)
          }
        }
      }
    }
    None
  }

  private def checkStatus(job: AppProtocol.JobSpec, jobStatusUrl: String, token: String, sender: ActorRef): Unit = {
    try {
      log.debug(s"[${job.id}] Checking status")
      DataMinerConnector.dataMinerGet(jobStatusUrl, Some(token), log) match {
        case Left(e) =>
          context.self ! Done(Right(JobFailed(job, s"No status information available, exception: ${e.getMessage}")), sender)
        case Right(body) =>
          val xmlResponse = XML.loadString(body)
          if ((xmlResponse \\ "ProcessSucceeded").nonEmpty) {
            val logFileUrl = extractResultFileUrl(xmlResponse, fidLogFilePath)
            val simStatesFileUrl = extractResultFileUrl(xmlResponse, fidStatesFilePath)
            val simSummaryFileUrl = extractResultFileUrl(xmlResponse, fidSummaryFilePath)
            log.info(s"[${job.id}] DataMiner process succeeded")
            context.self ! Done(Left(JobFinishedOk(job, logFileUrl, simStatesFileUrl, simSummaryFileUrl)), sender)
          }
          if ((xmlResponse \\ "ProcessFailed").nonEmpty) {
            log.warning(s"[${job.id}] DataMiner process failed")
            val logFileUrl = extractResultFileUrl(xmlResponse, fidLogFilePath)
            context.self ! Done(Right(JobFailed(job, "DataMiner process failed", logFileUrl)), sender)
          } else {
            context.system.scheduler.scheduleOnce(jobMonitorDelaySec.second, self, CheckAgain(job, jobStatusUrl, token, sender))
          }
      }
    } catch {
      case e@(_: Exception | _: Error) =>
        log.error(s"[${job.id}] DataMiner status check failed with exception: ${e.getMessage}", e)
        context.self ! Done(Right(JobFailed(job, s"Exception ${e.getMessage}")), sender)
    }
  }

}
