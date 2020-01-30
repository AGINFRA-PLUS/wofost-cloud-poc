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

import java.util.Locale
import java.util.concurrent.ThreadLocalRandom

import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.pattern.ask
import akka.routing.FromConfig
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import nl.wur.aginfrasystem.AppProtocol._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source
import scala.util.{Failure, Success}


object Scheduler {
  Locale.setDefault(Locale.US)

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val system: ActorSystem = ActorSystem("aginfrasystem")

  private val config = ConfigFactory.load()
  private val dataMinerUrl = config.getString("application.gcube.dataminerurl")
  private val agroDataCubeToken = config.getString("application.agrodatacube.token")
  private val log = Logging.getLogger(system, this)

  private val gCubeToken: String = readGCubeToken(log) match {
    case Some(t) => t
    case None =>
      log.info("Using gcube_token from configuration file")
      config.getString("application.gcube.token")
  }

  def scheduleJobs(title: String, geometryWkt: String, geometryEpsg: String,
                           parcelYear: Int, parcelCropCode: String,
                           batchSize: Int = 1000, nrBatches: Int = 10,
                           batchTimeoutSec: Int = 60): Unit = {

    assert(batchSize > 0)
    assert(nrBatches > 0)

    implicit val timeout: Timeout = (nrBatches * batchTimeoutSec).seconds

    try {
      writeStatusFile(0, log)
      val jobStarter = system.actorOf(FromConfig.props(JobStarter.props), "job-starter-dispatcher")
      val jobMonitor = system.actorOf(FromConfig.props(JobMonitor.props), "job-monitor-dispatcher")
      val logProcessor = system.actorOf(FromConfig.props(LogProcessor.props), "log-processor-dispatcher")
      val summaryProcessor = system.actorOf(FromConfig.props(SummaryProcessor.props), "summary-processor-dispatcher")
      writeStatusFile(10, log)

      // calculate status percentage step size
      val statusPercentageStep = Math.floor(40.0 / nrBatches)
      var currentStatusPercentage = 10.0

      val baseJobId = title.toLowerCase.replace(" ", "-")

      // first scatter phase
      val monitoringFutures = (1 to nrBatches).map { batchIndex =>
        val jobSpec = AgroDataCubeGeometryJobSpec(f"$baseJobId%s-batch-$batchIndex%04d",
          title, "", geometryWkt, geometryEpsg, parcelYear, parcelCropCode,
          agroDataCubeToken, batchSize, batchIndex - 1
        )
        log.info(s"[${jobSpec.id}] Starting")

        // add a little delay to avoid starting lots of jobs at roughly the same time
        val delay: FiniteDuration = (ThreadLocalRandom.current().nextInt(1, 4 + 1) * 2).seconds
        val jobFuture = jobStarter ? AppProtocol.StartJobDelayed(StartJob(jobSpec, gCubeToken, None), delay)
        // val jobFuture = jobStarter ? AppProtocol.StartJob(jobSpec, gCubeToken, None)

        val monitorFuture = jobFuture.flatMap {
          case r: StartJobOk =>
            log.info(s"[${r.job.id}] Started, tracking progress")
            log.debug(s"[${r.job.id}] Status url: ${r.statusUrl.toString}")
            currentStatusPercentage = currentStatusPercentage + statusPercentageStep
            writeStatusFile(currentStatusPercentage, log)
            jobMonitor ? AppProtocol.MonitorJob(r.job, r.statusUrl.toString, gCubeToken)
          case r: StartJobFailed =>
            log.info(s"[${r.job.id}] Start failed: ${r.reason}")
            currentStatusPercentage = currentStatusPercentage + statusPercentageStep
            writeStatusFile(currentStatusPercentage, log)
            jobMonitor ? AppProtocol.PassFailedJob(r.job, r.reason)
        }

        // keep track of completed work
        monitorFuture.onComplete {
          case Success(value) =>
            currentStatusPercentage = currentStatusPercentage + statusPercentageStep
            writeStatusFile(currentStatusPercentage, log)
          case Failure(exception) =>
            currentStatusPercentage = currentStatusPercentage + statusPercentageStep
            writeStatusFile(currentStatusPercentage, log)
        }

        monitorFuture
      }

      // first gather phase
      log.info("Awaiting completion of all jobs ...")
      val monitoringResults = Await.result(Future.sequence(monitoringFutures), timeout.duration)

      // second scatter phase
      writeStatusFile(90, log)
      log.info("Post processing results ...")
      val postProcessingFutures = monitoringResults.flatMap {
        case r: JobFailed =>
          log.error(s"[${r.job.id}] Failed: ${r.reason}")
          if (r.logFileUrl.isDefined) {
            log.info(s"Logfile: ${r.logFileUrl.get}")
          }
          val f1 = logProcessor ? AppProtocol.ProcessLog(r.job, r.logFileUrl, gCubeToken)
          List(f1)
        case r: JobFinishedOk =>
          log.info(s"[${r.job.id}] Completed ok")
          if (r.logFileUrl.isDefined) {
            log.info(s"Logfile: ${r.logFileUrl.get}")
          }
          if (r.simSummaryUrl.isDefined) {
            log.info(s"Summary output: ${r.simSummaryUrl.get}")
          }
          val f1 = summaryProcessor ? AppProtocol.ProcessSummary(r.job, r.simSummaryUrl, gCubeToken)
          val f2 = logProcessor ? AppProtocol.ProcessLog(r.job, r.logFileUrl, gCubeToken)
          List(f1, f2)
      }

      // second gather phase
      val postProcessingResults = Await.result(Future.sequence(postProcessingFutures), timeout.duration)

      writeStatusFile(95, log)
      log.info("Wrapping up ...")

      // start an actor to create a html report
      val reporter = system.actorOf(Reporter.props, "reporter")
      val info = ReportInfo(title, geometryWkt, geometryEpsg, parcelYear, parcelCropCode, batchSize, nrBatches, batchTimeoutSec)
      reporter ! StartNewReport("WOFOST Crop Simulations", "Wageningen University and Research", info)

      var nrOk = 0
      var nrFailed = 0
      var nrResults = 0
      postProcessingResults.foreach {
        case r: LogProcessingFailed =>
          log.error(s"[${r.job.id}] Log processing failed: ${r.reason}")
          if (r.logFileUrl.isDefined) {
            log.info(s"[${r.job.id}]\tdetails: ${r.logFileUrl.get}")
          }
          nrFailed += 1

        case r: LogProcessingOk =>
          log.info(s"[${r.job.id}] ${r.details.mkString(", ")}")
//          if (r.logFileUrl.isDefined) {
//            log.info(s"[${r.job.id}]\tdetails: ${r.logFileUrl.get}")
//          }
          if (r.errorCount == 0) {
            nrOk += 1
          } else {
            nrFailed += 1
          }
          reporter ! AddToReport(r.job, r.details)

        case r: SummaryProcessingFailed =>
          log.error(s"[${r.job.id}] Summary file processing failed: ${r.reason}")
          if (r.simSummaryUrl.isDefined) {
            log.info(s"[${r.job.id}]\tdetails: ${r.simSummaryUrl.get}")
          }

        case r: SummaryProcessingOk =>
          log.info(s"[${r.job.id}] ${r.details.mkString(", ")}")
//          if (r.simSummaryUrl.isDefined) {
//            log.info(s"[${r.job.id}]\tdetails: ${r.simSummaryUrl.get}")
//          }
          nrResults += r.simCount
          reporter ! AddToReport(r.job, r.details)

        case _ => log.error("Received an unexpected message")
      }
      val footer = s"${monitoringResults.size} Jobs were executed, $nrOk successful and $nrFailed failed. Total simulations = $nrResults."
      writeFinalReport(reporter, footer, log)
      log.info(footer)
    } finally {
      writeStatusFile(100, log)
      system.terminate()
    }
  }


  private def writeStatusFile(percentageDone: Double, log: LoggingAdapter): Unit = {
    import java.io._
    if ((percentageDone >= 0.0) && (percentageDone <= 100.0)) {
      val perc = Math.round(percentageDone / 5.0).toInt * 5
      log.info(s"Percentage done: $perc%")
      val pw = new PrintWriter(new File("status.txt" ))
      pw.write(s"$perc")
      pw.close()
    }
  }

  private def writeFinalReport(reporter: ActorRef, footer: String, log: LoggingAdapter)(implicit timeout: Timeout): Unit = {
    val reportFuture = reporter ? SendFinalReport(footer, "Powered By AgInfra+ and D4Science")
    val reportResult = Await.result(reportFuture, timeout.duration)

    reportResult match {
      case r: Report => log.info("writing processing report output")
        import java.io._
        val pw = new PrintWriter(new File("processing_report.html" ))
        pw.write(r.reportText)
        pw.close()
      case _ => log.info("No report received")
    }
  }

  def readGCubeToken(log: LoggingAdapter): Option[String] = {
    var token: Option[String] = None
    try {
      log.debug("Trying to read gcube_token from globalvariables.csv file")
      val bufferedSource = Source.fromFile("./globalvariables.csv")
      for (line <- bufferedSource.getLines) {
        val strings = line.split(",").map(_.replaceAll("^\"|\"$", ""))
        if ((strings.length == 2) && strings(0).equalsIgnoreCase("gcube_token")) {
          token = Some(strings(1))
          log.info(s"Using gcube_token ${token.get}")
        }
      }
      bufferedSource.close
      token
    } catch {
      case e@(_: Exception | _: Error) =>
        log.error(e.getMessage, e)
        None
    }
  }

}
