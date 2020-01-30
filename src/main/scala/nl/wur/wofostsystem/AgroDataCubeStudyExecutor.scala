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

import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.pattern.ask
import akka.routing.FromConfig
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import nl.wur.agrodatacube.client.FieldReader
import nl.wur.wofostshell.parameters.{ParXChangeMarshaller, VarInfo}
import nl.wur.wofostsystem.AppProtocol._
import nl.wur.wofostsystem.actors.{AgroDataCubeLibrarian, Reporter, Researcher}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


object AgroDataCubeStudyExecutor {

  val config = ConfigFactory.load()
  val token = config.getString("application.agrodatacube.token")

  def performStudy(spec: AgroDataCubeFieldIdStudySpec): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val timeout: Timeout = spec.timeoutSec.seconds

    val system = ActorSystem("wofostsystem")
    val log = Logging.getLogger(system, this)

    try {
      log.debug(s"Processing study ${spec.title}")
      writeStatusFile(0, log)
      val librarian = system.actorOf(AgroDataCubeLibrarian.props)
      val researcher = system.actorOf(Researcher.props)
      val reporter = system.actorOf(Reporter.props, "reporter")
      reporter ! StartNewReport(spec)
      writeStatusFile(10, log)

      // scatter phase
      log.info(s"Processing field ${spec.selectionFieldId}")
      val researcherFuture = {
        val fieldSpec = AgroDataCubeFieldIdsStudySpec(s"${spec.id}-field_${spec.selectionFieldId}", spec.title, spec.description,
          Seq[Long](spec.selectionFieldId), spec.selectionYear, spec.timeoutSec)
        val librarianFuture = librarian ? AppProtocol.Prepare(fieldSpec, 0)
        librarianFuture.flatMap {
          case r: PreparationOk =>
            log.debug(s"Prepared ${r.study.id}")
            val fileName = s"${r.study.id}_params.ser"
            log.info(s"writing simulation inputs to $fileName")
            writeSimulationInputs(fileName, r.modelInputs)
            researcher ? AppProtocol.Research(spec, r.locationInfo, r.modelInputs)
          case r: PreparationFailed =>
            log.error(s"Error preparing ${r.study.id}: ${r.reason}")
            researcher ? AppProtocol.PassResearch(spec, None)
        }
      }
      writeStatusFile(50, log)

      // gather phase
      log.info("Awaiting completion of crop simulation ...")
      val result = Await.result(researcherFuture, timeout.duration)
      writeStatusFile(90, log)

      log.info("Processing results ...")
      result match {
        case r: ResearchFailed => log.error(s"error for ${r.study.id} ${r.locationInfo}: ${r.reason}")
        case r: ResearchOk =>
          if (r.states.nonEmpty) {
            // TODO: Write custom states reporter that can replace the used
            //    cryptical variable names with something more readable for
            //    non crop model experts.
            log.info("Writing simulation states output")
            r.states.get.report("simulation_states.csv", ";", "#", "NaN")
          }
          reporter ! AddToReport(spec, r.locationInfo, r.summary)
          writeFinalReport(reporter, spec, log)
        case _ => log.error("Received an unexpected message!")
      }
    } finally {
      writeStatusFile(100, log)
      system.terminate()
    }
  }

  def performStudy(spec: AgroDataCubeFieldIdsStudySpec): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val timeout: Timeout = spec.timeoutSec.seconds

    val system = ActorSystem("wofostsystem")
    val log = Logging.getLogger(system, this)

    try {
      log.info(s"Processing study ${spec.title}")
      writeStatusFile(0, log)
      val librarian = system.actorOf(FromConfig.props(AgroDataCubeLibrarian.props), "librarian-dispatcher")
      val researcher = system.actorOf(FromConfig.props(Researcher.props), "researcher-dispatcher")
      val reporter = system.actorOf(Reporter.props, "reporter")
      reporter ! StartNewReport(spec)
      writeStatusFile(10, log)

      // scatter phase
      val researcherFutures = spec.selectionFieldIds.indices.map { index =>
        log.info(s"Processing field ${spec.selectionFieldIds(index)}")
        val librarianFuture = librarian ? AppProtocol.Prepare(spec, index)
        val researcherFuture = librarianFuture.flatMap {
          case r: PreparationOk =>
            log.info(s"Prepared ${r.study.id}")
            researcher ? AppProtocol.Research(spec, r.locationInfo, r.modelInputs)
          case r: PreparationFailed =>
            log.error(s"Error preparing ${r.study.id}: ${r.reason}")
            researcher ? AppProtocol.PassResearch(spec, None)
        }
        researcherFuture
      }
      writeStatusFile(50, log)

      // gather phase
      log.info("Awaiting completion of crop simulations ...")
      val researcherResults = Await.result(Future.sequence(researcherFutures), timeout.duration)
      writeStatusFile(90, log)
      summarizeResearcherResults(spec, researcherResults.toList, reporter, log)
    } finally {
      writeStatusFile(100, log)
      system.terminate()
    }
  }

  def performStudy(spec: AgroDataCubeGeometryStudySpec): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val timeout: Timeout = spec.timeoutSec.seconds

    val system = ActorSystem("wofostsystem")
    val log = Logging.getLogger(system, this)

    try {
      log.info(s"Processing study ${spec.title}")
      writeStatusFile(0, log)

      // get all fields
      log.debug("Retrieving fields in study area")
      val fieldReader = new FieldReader()

      val fields = fieldReader.getFieldsForWktLocation(spec.selectionGeometryWkt, spec.selectionGeometryEpsg,
        token, log, Some(spec.selectionYear), Some(spec.selectionCropCode),
        true, true, spec.pageSize, spec.pageOffset) match {
        case Left(e) => throw e
        case Right(v) => v
      }
      writeStatusFile(10, log)

      if (fields.isEmpty) {
        log.info(s"Nothing to do, no fields in selection")
        return
      } else {
        log.info(s"Number of fields in selection: ${fields.size}")
      }

      val librarian = system.actorOf(FromConfig.props(AgroDataCubeLibrarian.props), "librarian-dispatcher")
      val researcher = system.actorOf(FromConfig.props(Researcher.props), "researcher-dispatcher")
      val reporter = system.actorOf(Reporter.props, "reporter")
      reporter ! StartNewReport(spec)

      // scatter phase
      val researcherFutures = fields.map { field =>
        log.info(s"Processing field: ${field.fieldId}, crop: ${field.cropCode} (${field.cropName})")
        val fieldSpec = AgroDataCubeFieldIdsStudySpec(s"${spec.id}-field_${field.fieldId}", spec.title, spec.description,
          Seq[Long](field.fieldId), spec.selectionYear, spec.timeoutSec)
        val librarianFuture = librarian ? AppProtocol.Prepare(fieldSpec, 0)
        val researcherFuture = librarianFuture.flatMap {
          case r: PreparationOk =>
            log.info(s"Prepared ${r.study.id}")
            researcher ? AppProtocol.Research(fieldSpec, r.locationInfo, r.modelInputs)
          case r: PreparationFailed =>
            log.error(s"Error preparing ${r.study.id}: ${r.reason}")
            researcher ? AppProtocol.PassResearch(fieldSpec, None)
        }
        researcherFuture
      }
      writeStatusFile(50, log)

      // gather phase
      log.info("Awaiting completion of crop simulations ...")
      val researcherResults = Await.result(Future.sequence(researcherFutures), timeout.duration)
      writeStatusFile(90, log)
      summarizeResearcherResults(spec, researcherResults, reporter, log)
    } finally {
      writeStatusFile(100, log)
      system.terminate()
    }
  }

  private def summarizeResearcherResults(spec: StudySpec, researcherResults: List[Any], reporter: ActorRef, log: LoggingAdapter)(implicit timeout: Timeout): Unit = {
    log.info("Summarizing results ...")
    var nrOk = 0
    var nrFailed = 0
    researcherResults.foreach {
      case r: ResearchFailed =>
        log.error(s"Error for ${r.study.id} ${r.locationInfo}: ${r.reason}")
        nrFailed += 1
      case r: ResearchOk =>
        reporter ! AddToReport(spec, r.locationInfo, r.summary)
        nrOk += 1
      case _ => log.error("Received an unexpected message")
    }
    writeFinalReport(reporter, spec, log)
    writeEmptyStatesFile()
    log.info(s"${researcherResults.size} Simulations performed, $nrOk successful and $nrFailed failed")
  }

  private def writeSimulationInputs(fileName: String, inputs: Seq[VarInfo]): Unit = {
    import java.io._
    val parXChange = ParXChangeMarshaller.deserialize(inputs)
    val out = new ObjectOutputStream(new FileOutputStream(fileName))
    out.writeObject(parXChange)
    out.close()
  }

  private def writeStatusFile(percentageDone: Int, log: LoggingAdapter): Unit = {
    import java.io._
    if ((percentageDone >= 0) && (percentageDone <= 100)) {
      val perc = Math.round(percentageDone / 5.0).toInt * 5
      log.debug(s"Percentage done: $perc")
      val pw = new PrintWriter(new File("status.txt" ))
      pw.write(s"$perc")
      pw.close()
    }
  }

  private def writeEmptyStatesFile(): Unit = {
    import java.io._
    val pw = new PrintWriter(new File("simulation_states.csv" ))
    pw.write("Only summary information is available. Run an individual simulation to get the daily states output.")
    pw.close()
  }

  private def writeFinalReport(reporter: ActorRef, spec: StudySpec, log: LoggingAdapter)(implicit timeout: Timeout): Unit = {
    val reportFuture = reporter ? SendFinalReport(spec)
    val reportResult = Await.result(reportFuture, timeout.duration)

    reportResult match {
      case r: Report => log.info("writing summary output")
        import java.io._
        val pw = new PrintWriter(new File("simulations_summary.json" ))
        pw.write(r.reportText)
        pw.close()
      case _ => log.info("No report received")
    }
  }

}
