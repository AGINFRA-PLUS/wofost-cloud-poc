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

import java.io.{FileInputStream, ObjectInputStream}

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.event.{Logging, LoggingAdapter}
import akka.routing.FromConfig
import akka.util.Timeout
import nl.wur.wiss.core.ParXChange
import nl.wur.aginfrasystem.Reporter
import nl.wur.wofostshell.parameters.{ParXChangeMarshaller, VarDouble, VarInfo}
import nl.wur.wofostsystem.AppProtocol._
import nl.wur.wofostsystem.actors.Researcher

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.Source


object FileBasedStudyExecutor {

  def performStudy(spec: SimpleStudySpec): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val timeout: Timeout = spec.timeoutSec.seconds

    val scenario = ParXChangeMarshaller.serialize(createParXChange(spec.inputFileName))
    val system = ActorSystem("wofostsystem")
    val log = Logging.getLogger(system, this)

    try {
      log.info(s"Processing study: ${spec.title}")
      writeStatusFile(0, log)
      val researcher: ActorRef = system.actorOf(Researcher.props, "researcher")
      val reporter = system.actorOf(Reporter.props, "reporter")
      reporter ! StartNewReport(spec)
      writeStatusFile(10, log)

      log.info(s"Processing inputs from file: ${spec.inputFileName}")
      val future = researcher ? AppProtocol.Research(SimpleStudySpec(s"1", "run-1", spec.title, spec.inputFileName), None, scenario)
      writeStatusFile(50, log)
      log.info("awaiting completion of crop simulation ...")
      val result = Await.result(future, timeout.duration)
      writeStatusFile(90, log)
      log.info("processing results ...")
      result match {
        case r: ResearchFailed => log.error(s"${r.study.title}, ${r.reason}")
        case r: ResearchOk =>
          if (r.states.nonEmpty) {
            log.info("writing simulation states output")
            r.states.get.report("simulation_states.csv", ";", "#", "NaN")
          }
          reporter ! AddToReport(spec, r.locationInfo, r.summary)
          writeFinalReport(reporter, spec, log)
        case _ => log.error("received an unexpected message")
      }
    } finally {
      writeStatusFile(100, log)
      system.terminate()
    }
  }

  def performStudy(spec: SimpleSweepStudySpec): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val timeout: Timeout = spec.timeoutSec.seconds

    val totalRuns = spec.nrRunsAfterBaseValue + spec.nrRunsAfterBaseValue + 1
    val system = ActorSystem("wofostsystem")
    val log = Logging.getLogger(system, this)

    try {
      log.debug(s"Processing study: ${spec.title}")
      writeStatusFile(0, log)
      val researcher = system.actorOf(FromConfig.props(Researcher.props), "researcher-dispatcher")
      val reporter = system.actorOf(Reporter.props, "reporter")
      reporter ! StartNewReport(spec)
      writeStatusFile(10, log)

      // read base scenario
      val baseScenario = ParXChangeMarshaller.serialize(createParXChange(spec.inputFileName))
      val basename = getFileNameWithoutExtension(spec.inputFileName)

      // calculate starting sweep parameter value for a series of crop model runs
      var sweepParameterIndex = baseScenario.indexWhere { v => v.name.equalsIgnoreCase(spec.sweepParamName) }
      if (sweepParameterIndex < 0) {
        throw new IllegalArgumentException(s"No sweep parameter ${spec.sweepParamName} found in scenario to create reruns for.")
      }
      var sweepParameterValue = baseScenario(sweepParameterIndex).asInstanceOf[VarDouble].value -
        (spec.nrRunsBeforeBaseValue * spec.stepSize)

      // scatter phase
      val scatterFutures = (0 to spec.nrRunsBeforeBaseValue + spec.nrRunsAfterBaseValue + 1).map { runNr =>
        val simId = s"${spec.sweepParamName}=$sweepParameterValue"
        // create parameters for this scenario
        val param: VarInfo = baseScenario(sweepParameterIndex).asInstanceOf[VarDouble].copy(value = sweepParameterValue)
        val scenario: Seq[VarInfo] = baseScenario.updated(sweepParameterIndex, param)
        sweepParameterValue = sweepParameterValue + spec.stepSize
        log.info(s"Processing parameter sweep: $simId")
        researcher ? AppProtocol.Research(spec, None, scenario)
      }
      writeStatusFile(50, log)

      log.info("awaiting completion of crop simulations ...")
      val scatterResults = Await.result(Future.sequence(scatterFutures), timeout.duration)

      // gather phase
      log.info("processing results ...")
      scatterResults.foreach {
        case r: ResearchOk => reporter ! AddToReport(spec, r.locationInfo, r.summary)
        case r: ResearchFailed => log.error(s"error for ${r.study.id} ${r.locationInfo}: ${r.reason}")
        case _ => log.error("received an unexpected message")
      }
      writeStatusFile(90, log)
      writeFinalReport(reporter, spec, log)
      writeEmptyStatesFile()
    } finally {
      writeStatusFile(100, log)
      system.terminate()
    }
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
      case _ => log.info("no report received")
    }
  }

  def getFileNameWithoutExtension(fileName: String): String = {
    fileName.dropRight(fileName.length - fileName.lastIndexOf("."))
  }

  def getFileNameExtension(fileName: String): String = {
    fileName.drop(fileName.lastIndexOf("."))
  }

  def createParXChange(inputFileName: String): ParXChange = {
    getFileNameExtension(inputFileName).toLowerCase match {
      case ".ser" =>
        val in: ObjectInputStream = new ObjectInputStream(new FileInputStream(inputFileName))
        val par: ParXChange = in.readObject.asInstanceOf[ParXChange]
        in.close()
        par
      case ".json" =>
        val bufferedSource = Source.fromFile(inputFileName)
        val json = bufferedSource.getLines().mkString
        val data = ParXChangeMarshaller.fromJSON(json)
        val par = ParXChangeMarshaller.deserialize(data)
        bufferedSource.close()
        par
      case _ =>
        throw new IllegalArgumentException(s"Unrecognized input file type, currently supported are *.ser and *.json!")
    }
  }

}
