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
import nl.wur.aginfrasystem.AppProtocol.{JobSpec, Msg, ProcessSummary, SummaryProcessingFailed, SummaryProcessingOk}
import nl.wur.aginfrasystem.SummaryProcessor.Done
import nl.wur.dataminer.client.DataMinerConnector
import spray.json._


object SummaryProcessor {
  final case class Done(e: Either[SummaryProcessingOk, SummaryProcessingFailed], sender: ActorRef)
  def props: Props = Props(new SummaryProcessor())
}


class SummaryProcessor extends Actor with ActorLogging with Stash {

  override def receive: Receive = ready

  private def ready: Receive = {
    case m: Msg => m match {
      case ProcessSummary(job, simSummaryUrl, token, _) if job.isInstanceOf[JobSpec] =>
        aggregateFieldData(job.asInstanceOf[JobSpec], simSummaryUrl, token, sender())
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

  private def process(r: Either[SummaryProcessingOk, SummaryProcessingFailed], sender: ActorRef): Unit = {
    r fold (
      f => {
        sender ! f
      },
      s => sender ! s)
  }

  private def aggregateFieldData(job: AppProtocol.JobSpec, simSummaryUrl: Option[String], token: String, sender: ActorRef): Unit = {
    context.become(busy)

    try {
      if (simSummaryUrl.isEmpty) {
        log.debug(s"[${job.id}] No simulations summary file specified")
        context.self ! Done(Right(SummaryProcessingFailed(job, "No simulations summary file specified", simSummaryUrl)), sender)
      } else {
        log.debug(s"[${job.id}] Aggregating data from simulations summary file")
        DataMinerConnector.dataMinerGet(simSummaryUrl.get, None, log) match {
          case Left(e) =>
            context.self ! Done(Right(SummaryProcessingFailed(job, s"Simulations summary file could not be retrieved, exception: ${e.getMessage}", simSummaryUrl)), sender)
          case Right(body) =>
            val rootObj = body.parseJson.asJsObject

            val studyFieldCount = if (rootObj.fields.contains("study")) {
              val studyObj = rootObj.fields("study").asJsObject
              val description = studyObj.fields("description")
              val fieldIds = studyObj.fields("field_ids").asInstanceOf[JsArray]
              val id = studyObj.fields("id")
              val title = studyObj.fields("title")
              val studyType = studyObj.fields("type")
              val studyYear = studyObj.fields("year")
              fieldIds.elements.length
            } else {
              0
            }

            val (resultsCount, totalArea, lowDvsCount, avgTagpEnd, avgHiEnd, avgLaiMax, avgTSumEnd) = if (rootObj.fields.contains("results")) {
              val resultsJsArray = rootObj.fields("results").asInstanceOf[JsArray]

              //  sum all areas of all fields (m2, ha = 10.000m2)
              val totalArea = try {
                val fieldAreas = resultsJsArray.elements.map(o => o.asJsObject.fields("area").asInstanceOf[JsNumber].value.doubleValue())
                fieldAreas.sum / 10000
              } catch {
                case e: Throwable =>
                  log.warning(s"[${job.id}] could not calculated total area for fields: ${e.getMessage}")
                  0.0
              }

              // calculate average tagp_end value
              val tagpEnds = resultsJsArray.elements.map(o => o.asJsObject.fields("tagp_end").asInstanceOf[JsNumber].value.doubleValue())
              val avgTagpEnd = tagpEnds.foldLeft((0.0, 1)) { case ((avg, idx), next) => (avg + (next - avg)/idx, idx + 1) }._1

              // calculate average hi_end (harvest index) value
              val hiEnds = resultsJsArray.elements.map(o => o.asJsObject.fields("hi_end").asInstanceOf[JsNumber].value.doubleValue())
              val avgHiEnd = hiEnds.foldLeft((0.0, 1)) { case ((avg, idx), next) => (avg + (next - avg)/idx, idx + 1) }._1

              // calculate average lai_max (leaf area index) value
              val laiMaxs = resultsJsArray.elements.map(o => o.asJsObject.fields("lai_max").asInstanceOf[JsNumber].value.doubleValue())
              val avgLaiMax = laiMaxs.foldLeft((0.0, 1)) { case ((avg, idx), next) => (avg + (next - avg)/idx, idx + 1) }._1

              // calculate average tsum_end (temperature sum) value
              val tsumEnds = resultsJsArray.elements.map(o => o.asJsObject.fields("tsum_end").asInstanceOf[JsNumber].value.doubleValue())
              val avgTsumEnd = tsumEnds.foldLeft((0.0, 1)) { case ((avg, idx), next) => (avg + (next - avg)/idx, idx + 1) }._1

              // count too low dvs values
              val lowDvsCount = resultsJsArray.elements.count(o => o.asJsObject.fields("dvs_end").asInstanceOf[JsNumber].value < 2.0)

              (resultsJsArray.elements.length, totalArea, lowDvsCount, avgTagpEnd, avgHiEnd, avgLaiMax, avgTsumEnd)
            } else {
              (0, 0.0, 0, 0.0, 0.0, 0.0, 0.0)
            }

            val details = Map(
              "fields[#]" -> studyFieldCount, "results[#]" -> resultsCount,
              "sum(AREA)[Ha]" -> f"$totalArea%.2f",
              "DVS<2[#]" -> lowDvsCount,
              "avg(LAI_MAX)[area/area]" -> f"$avgLaiMax%.5f", "avg(TSUM_END)[C.d]" -> f"$avgTSumEnd%.2f",
              "avg(TAGP_END)[Kg/Ha]" -> f"$avgTagpEnd%.2f", "avg(HI_END)[%]" -> f"$avgHiEnd%.5f",
              "summaryUrl" -> simSummaryUrl.get
            )
            log.info(s"[${job.id}] ${details.mkString(", ")}")
            context.self ! Done(Left(SummaryProcessingOk(job, resultsCount, details, simSummaryUrl)), sender)
        }
      }
    } catch {
      case e@(_: Exception | _: Error) =>
        log.error(s"[${job.id}] Simulations summary file processing failed with exception: ${e.getMessage}", e)
        context.self ! Done(Right(SummaryProcessingFailed(job, s"Exception ${e.getMessage}", simSummaryUrl)), sender)
    }
  }

}
