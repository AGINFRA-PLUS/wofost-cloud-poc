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

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import com.typesafe.config.ConfigFactory
import nl.wur.aginfrasystem.AppProtocol.{StartJobFailed, StartJobOk}
import nl.wur.aginfrasystem.JobStarter.Done
import nl.wur.agrodatacube.client.FieldReader
import nl.wur.dataminer.client.DataMinerConnector

import scala.concurrent.duration._
import scala.xml.XML


object JobStarter {
  final case class Done(e: Either[StartJobOk, StartJobFailed], sender: ActorRef)
  def props: Props = Props(new JobStarter())
}

class JobStarter extends Actor with ActorLogging with Stash {
  import AppProtocol._
  import context._

  private val config = ConfigFactory.load()
  private val dataMinerUrl = config.getString("application.gcube.dataminerurl")
  private val jobStarterDelaySec = config.getInt("application.gcube.job-starter-delay-sec")

  private implicit val processId: String = config.getString("application.worker.process-id")
  private implicit val inputIdTitle: String = config.getString("application.worker.input-id-title")
  private implicit val inputIdFieldIds: String = config.getString("application.worker.input-id-agrodatacube-field-ids")
  private implicit val inputIdSimulationYear: String = config.getString("application.worker.input-id-simulation-year")
  private implicit val inputIdCalculationsTimeout: String = config.getString("application.worker.input-id-calculations-timeout")

  override def receive: Receive = ready

  private def ready: Receive = {
    case m: Msg => m match {
      case StartJobDelayed(startJobMsg, delay, _) =>
        log.debug(s"Delaying start of job by ${delay.toSeconds} seconds to avoid DataMiner congestion")
        context.system.scheduler.scheduleOnce(delay, self, startJobMsg.copy(sender = Some(sender())))
      case StartJob(job, gCubeToken, originalSender, _) if job.isInstanceOf[FieldIdsJobSpec] =>
        startFieldIdsJob(job.asInstanceOf[FieldIdsJobSpec], gCubeToken, originalSender.getOrElse(sender()))
      case StartJob(job, gCubeToken, originalSender, _) if job.isInstanceOf[AgroDataCubeGeometryJobSpec] =>
        startAgroDataCubeGeometryJob(job.asInstanceOf[AgroDataCubeGeometryJobSpec], gCubeToken, originalSender.getOrElse(sender()))
      case _ => log.warning(s"Don't know how to handle message $m!")
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

  private def startFieldIdsJob(job: FieldIdsJobSpec, gCubeToken: String, sender: ActorRef): Unit = {
    context.become(busy)
    log.info(s"[${job.id}] Starting")
    try {
      log.info(s"[${job.id}] Sending request to DataMiner")
      val request: String = multipleFieldsWPSRequest(s"${job.id}", job.fieldIds, job.simulationYear, job.timeoutSec).toString()

      DataMinerConnector.dataMinerPost(dataMinerUrl, Some(gCubeToken), request, log) match {
        case Left(e) =>
          log.warning(s"[${job.id}] Submit failed, exception: ${e.getMessage}")
          context.self ! Done(Right(StartJobFailed(job, s"Submit to DataMiner failed, exception: ${e.getMessage}")), sender)
        case Right(body) =>
          val xmlResponse = XML.loadString(body)
          if ((xmlResponse \ "Status" \ "ProcessAccepted").nonEmpty) {
            log.info(s"[${job.id}] Request was accepted by DataMiner")
            val statusUrl = new URL(xmlResponse.attribute("statusLocation").get.mkString)
            context.system.scheduler.scheduleOnce(jobStarterDelaySec.second, self, Done(Left(StartJobOk(job, statusUrl)), sender))
          } else {
            log.warning(s"[${job.id}] Request was rejected by DataMiner")
            context.self ! Done(Right(StartJobFailed(job, "Request rejected by DataMiner")), sender)
          }
      }
    } catch {
      case e@(_: Exception | _: Error) =>
        val msg = s"[${job.id}] Start failed with exception: ${e.getMessage}"
        log.error(msg, e)
        context.self ! Done(Right(StartJobFailed(job, s"Exception ${e.getMessage}")), sender)
    }
  }

  private def startAgroDataCubeGeometryJob(job: AgroDataCubeGeometryJobSpec, gCubeToken: String, sender: ActorRef): Unit = {
    context.become(busy)
    log.info(s"[${job.id}] Starting")
    try {
      log.info(s"[${job.id}] Retrieving field IDs from AgroDataCube (page ${job.pageOffset} with max ${job.pageSize} items)")
      val fieldReader = new FieldReader()

      val fields = fieldReader.getFieldsForWktLocation(job.selectionGeometryWkt, job.selectionGeometryEpsg,
        job.agroDataCubeToken, log, Some(job.selectionYear), Some(job.selectionCropCode),
        true, true, job.pageSize, job.pageOffset) match {
        case Left(e) => throw e
        case Right(v) => v
      }

      if (fields.isEmpty) {
        log.info(s"[${job.id}] Nothing to do, no fields in selection")
        context.self ! Done(Right(StartJobFailed(job, "Nothing to do, no fields in selection")), sender)
      } else {
        log.info(s"[${job.id}] Number of fields in selection: ${fields.size}")
        val fieldIds = fields collect { case f: fieldReader.Field => f.fieldId }

        log.info(s"[${job.id}] Sending request to DataMiner")
        val request: String = multipleFieldsWPSRequest(s"${job.id}", fieldIds, job.selectionYear, job.timeoutSec).toString()

        DataMinerConnector.dataMinerPost(dataMinerUrl, Some(gCubeToken), request, log) match {
          case Left(e) =>
            log.warning(s"[${job.id}] Submit failed, exception: ${e.getMessage}")
            context.self ! Done(Right(StartJobFailed(job, s"Submit to DataMiner failed, exception: ${e.getMessage}")), sender)
          case Right(body) =>
            val xmlResponse = XML.loadString(body)
            if ((xmlResponse \ "Status" \ "ProcessAccepted").nonEmpty) {
              log.info(s"[${job.id}] Request was accepted by DataMiner")
              val statusUrl = new URL(xmlResponse.attribute("statusLocation").get.mkString)
              context.system.scheduler.scheduleOnce(jobStarterDelaySec.second, self, Done(Left(StartJobOk(job, statusUrl)), sender))
            } else {
              log.warning(s"[${job.id}] Request was rejected by DataMiner")
              context.self ! Done(Right(StartJobFailed(job, "Request rejected by DataMiner")), sender)
            }
        }
      }
    } catch {
      case e@(_: Exception | _: Error) =>
        val msg = s"[${job.id}] Start failed with exception: ${e.getMessage}"
        log.error(msg, e)
        context.self ! Done(Right(StartJobFailed(job, s"Exception ${e.getMessage}")), sender)
    }
  }

  private def process(r: Either[StartJobOk, StartJobFailed], sender: ActorRef): Unit = {
    r fold (
      f => {
        sender ! f
      },
      s => sender ! s)
  }

  private val multipleFieldsWPSRequest = (title: String, fieldIds: Seq[Long], simulationYear: Int, timeoutSec: Int) =>
    <wps100:Execute xmlns:wps100="http://www.opengis.net/wps/1.0.0"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    service="WPS" version="1.0.0"
                    xsi:schemaLocation="http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsExecute_request.xsd">
      <ows110:Identifier xmlns:ows110="http://www.opengis.net/ows/1.1">{processId}</ows110:Identifier>
      <wps100:DataInputs>
        <wps100:Input>
          <ows110:Identifier xmlns:ows110="http://www.opengis.net/ows/1.1">{inputIdTitle}</ows110:Identifier>
          <wps100:Data>
            <wps100:LiteralData>{title}</wps100:LiteralData>
          </wps100:Data>
        </wps100:Input>
        <wps100:Input>
          <ows110:Identifier xmlns:ows110="http://www.opengis.net/ows/1.1">{inputIdFieldIds}</ows110:Identifier>
          <wps100:Data>
            <wps100:LiteralData>{fieldIds.mkString(",")}</wps100:LiteralData>
          </wps100:Data>
        </wps100:Input>
        <wps100:Input>
          <ows110:Identifier xmlns:ows110="http://www.opengis.net/ows/1.1">{inputIdSimulationYear}</ows110:Identifier>
          <wps100:Data>
            <wps100:LiteralData>{simulationYear}</wps100:LiteralData>
          </wps100:Data>
        </wps100:Input>
        <wps100:Input>
          <ows110:Identifier xmlns:ows110="http://www.opengis.net/ows/1.1">{inputIdCalculationsTimeout}</ows110:Identifier>
          <wps100:Data>
            <wps100:LiteralData>{timeoutSec}</wps100:LiteralData>
          </wps100:Data>
        </wps100:Input>
      </wps100:DataInputs>
      <wps100:ResponseForm>
        <wps100:ResponseDocument storeExecuteResponse="true" status="true" lineage="false">
          <wps100:Output >
            <ows:Identifier xmlns:ows="http://www.opengis.net/ows/1.1" xmlns:wps="http://www.opengis.net/wps/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">non_deterministic_output</ows:Identifier>
          </wps100:Output>
        </wps100:ResponseDocument>
      </wps100:ResponseForm>
    </wps100:Execute>

}
