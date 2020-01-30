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
package nl.wur.wofostsystem.actors

import java.util.Locale

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import nl.wur.wofostsystem.AppProtocol

import scala.collection.mutable

object Reporter {
  def props: Props = Props(new Reporter())
}

class Reporter extends Actor with ActorLogging with Stash {
  import AppProtocol._

  private var reports = new mutable.HashMap[String, Seq[String]]()

  override def receive: Receive = {
    case m: Msg => m match {
      case r: StartNewReport => startNewReport(r.study)
      case r: AddToReport => addToReport(r.study, r.locationInfo, r.results)
      case r: SendFinalReport => sendFinalReport(r.study, sender())
    }
  }

  private def startNewReport(spec: AppProtocol.StudySpec): Unit = {
    if (reports.contains(spec.id)) reports -= spec.id

    val builder = StringBuilder.newBuilder
    builder.append("""{ "study": { """)
    builder.append(s""""id": "${spec.id}", """)
    builder.append(s""""type": "${spec.studyType}", """)
    builder.append(s""""title": "${spec.title}", """)
    builder.append(s""""description": "${spec.description}", """)
    builder.append(s""""timeout": "${spec.timeoutSec} sec"""")

    spec match {
      case s: SimpleStudySpec =>
        builder.append(s""", "parameter_file": "${s.inputFileName}"""")
      case s: SimpleSweepStudySpec =>
        builder.append(s""", "parameter_file": "${s.inputFileName}"""")
        builder.append(s""", "sweep_parameter_name": "${s.sweepParamName}"""")
        builder.append(s""", "sweep_parameter_owner": "${s.sweepParamOwner}"""")
        builder.append(s""", "sweep_nr_runs_before_base_value": ${s.nrRunsBeforeBaseValue}""")
        builder.append(s""", "sweep_nr_runs_after_base_value": ${s.nrRunsAfterBaseValue}""")
        builder.append(s""", "sweep_step_size": ${s.stepSize}""")
      case s: AgroDataCubeFieldIdStudySpec =>
        builder.append(s""", "year": ${s.selectionYear}""")
        builder.append(s""", "field_id": ${s.selectionFieldId}""")
      case s: AgroDataCubeFieldIdsStudySpec =>
        builder.append(s""", "year": ${s.selectionYear}""")
        builder.append(s""", "field_ids": [ ${s.selectionFieldIds.mkString(", ")} ]""")
      case s: AgroDataCubeGeometryStudySpec =>
        builder.append(s""", "year": ${s.selectionYear}""")
        builder.append(s""", "crop_code": ${s.selectionCropCode}""")
        builder.append(s""", "geometry_wkt": "${s.selectionGeometryWkt}"""")
        builder.append(s""", "geometry_epsg": "${s.selectionGeometryEpsg}"""")
      case _ =>
        throw new IllegalArgumentException("Unsupported study spec received by reporter")
    }

    builder.append(""" }, "results": [""")
    var report = Seq[String](builder.toString())
    reports.put(spec.id, report)
  }

  private def addToReport(spec: AppProtocol.StudySpec, locationInfo: Option[LocationInfo], results: Map[String, Map[String, Double]]): Unit = {
    if (!reports.contains(spec.id)) {
      startNewReport(spec)
    }
    var report = reports(spec.id)
    if (report.nonEmpty) {
      val joiner = if (report.last.endsWith("}")) ", " else " "
      results.zipWithIndex.foreach { case (r, i) =>
        val info = locationInfo map {t => s"${t.asJson()}, "} getOrElse ""
        val newReport = report :+
          s"""$joiner{ ${info}"crop_idx": $i, ${r._2.map(d => """"%s": %.5f""".formatLocal(Locale.US, d._1, d._2)).mkString(", ")} }""".toLowerCase
        reports += (spec.id -> newReport)
      }
    }
  }

  private def sendFinalReport(spec: AppProtocol.StudySpec, ref: ActorRef): Unit = {
    if (reports.contains(spec.id)) {
      var report = reports(spec.id) :+ "]}"
      ref ! Report(spec, report.mkString(""))
    } else {
      log.error(s"no report for ${spec.id}")
    }
  }

}
