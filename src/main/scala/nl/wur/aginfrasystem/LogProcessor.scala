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
import nl.wur.aginfrasystem.AppProtocol.{JobSpec, LogProcessingFailed, LogProcessingOk, Msg, ProcessLog}
import nl.wur.aginfrasystem.LogProcessor.Done
import nl.wur.dataminer.client.DataMinerConnector


object LogProcessor {
  final case class Done(e: Either[LogProcessingOk, LogProcessingFailed], sender: ActorRef)
  def props: Props = Props(new LogProcessor())
}


class LogProcessor extends Actor with ActorLogging with Stash {

  override def receive: Receive = ready

  private def ready: Receive = {
    case m: Msg => m match {
      case ProcessLog(job, logFileUrl, token, _) if job.isInstanceOf[JobSpec] => countPerCategory(job.asInstanceOf[JobSpec], logFileUrl, token, sender())
      case _ => log.warning(s"Don't know how to handle message $m")
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

  private def process(r: Either[LogProcessingOk, LogProcessingFailed], sender: ActorRef): Unit = {
    r fold (
      f => {
        sender ! f
      },
      s => sender ! s)
  }

  private def countPerCategory(job: AppProtocol.JobSpec, logFileUrl: Option[String], token: String, sender: ActorRef): Unit = {
    context.become(busy)

    try {
      if (logFileUrl.isEmpty) {
        log.debug(s"[${job.id}] No DataMiner logfile specified")
        context.self ! Done(Right(LogProcessingFailed(job, "No DataMiner logfile specified", logFileUrl)), sender)
      } else {
        log.debug(s"[${job.id}] Checking DataMiner logfile")
        DataMinerConnector.dataMinerGet(logFileUrl.get, None, log) match {
          case Left(e) =>
            context.self ! Done(Right(LogProcessingFailed(job, s"DataMiner logfile could not be retrieved, exception: ${e.getMessage}", logFileUrl)), sender)
          case Right(body) =>
            val lines = body.split("\n")
            val infoLines = lines.count(_.contains("[INFO]"))
            val warnLines = lines.count(_.contains("[WARN]"))
            val errorLines = lines.count(_.contains("[ERROR]"))
            val debugLines = lines.count(_.contains("[DEBUG]"))
            val traceLines = lines.count(_.contains("[TRACE]"))
            val details = Map("errors" -> errorLines, "warnings" -> warnLines, "info" -> infoLines, "debug" -> debugLines, "trace" -> traceLines, "logUrl" -> logFileUrl.get)
            log.info(s"[${job.id}] ${details.mkString(", ")}")
            context.self ! Done(Left(LogProcessingOk(job, errorLines, details, logFileUrl)), sender)
        }
      }
    } catch {
      case e@(_: Exception | _: Error) =>
        log.error(s"[${job.id}] DataMiner logfile processing failed with exception: ${e.getMessage}", e)
        context.self ! Done(Right(LogProcessingFailed(job, s"Exception ${e.getMessage}", logFileUrl)), sender)
    }
  }

}
