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

import scala.collection.immutable.ListMap
import scala.collection.mutable


object Reporter {
  def props: Props = Props(new Reporter())
}

class Reporter extends Actor with ActorLogging with Stash {
  import AppProtocol._

  private var reportHeader: Option[String] = None
  private var reportSubHeader: Option[String] = None
  private var reportInfo: Option[ReportInfo] = None
  private var reportItems = new mutable.HashMap[String, Map[String, Any]]()

  override def receive: Receive = ready

  private def ready: Receive = {
    case m: Msg => m match {
      case r: StartNewReport =>
        // unstashAll()
        context.become(busy)
        startNewReport(r.header, r.subHeader, r.info)
      case _ =>
        log.warning(s"Message $m ignored, please start a new report first.")
        // stash()
    }
  }

  private def busy: Receive = {
    case m: Msg => m match {
      case r: AddToReport => addToReport(r.job, r.details)
      case r: SendFinalReport =>
        sendFinalReport(r.footer, r.subFooter, sender())
        reportHeader = None
        reportSubHeader = None
        reportInfo = None
        reportItems.clear()
        // unstashAll()
        context.become(ready)
      case _ =>
        log.warning(s"Message $m ignored, already working on a report.")
        // stash()
    }
  }

  private def startNewReport(header: String, subHeader: String, info: ReportInfo): Unit = {
    reportHeader = Some(header)
    reportSubHeader = Some(subHeader)
    reportInfo = Some(info)
  }

  private def addToReport(job: AppProtocol.JobSpec, details: Map[String, Any]): Unit = {
    if (reportItems.contains(job.id)) {
      reportItems(job.id) ++= details
    } else {
      reportItems += job.id -> details
    }
  }

  private def sendFinalReport(footer: String, subFooter: String, ref: ActorRef): Unit = {
    reportInfo match {
      case Some(info) =>
        ref ! Report(buildHtmlReport(info, reportItems.toMap, footer, subFooter))
      case None =>
        log.error(s"ReportInfo has not been set yet, can not create the report.")
    }
  }

  private def buildHtmlReport(info: ReportInfo, items: Map[String, Map[String, Any]], footer: String, subFooter: String): String = {

    val styleFrag =
      s"""
         |<style>
         |html {
         |	font-size: 1em;
         |	line-height: 1.4;
         |}
         |body {
         |	font: 10pt "Lucida Grande", Lucida, Verdana, sans-serif;
         |}
         |hr {
         |    display: block;
         |    height: 1px;
         |    border: 0;
         |    border-top: 1px solid #ccc;
         |    margin: 1em 0;
         |    padding: 0;
         |}
         |h1 {
         |	color: green;
         |}
         |h2 {
         |	color: green;
         |}
         |h3 {
         |	color: gray;
         |	font-size: 9pt;
         |}
         |h4 {
         |	color: silver;
         |	font-size: 9pt;
         |}
         |table {
         |	border: 0 none;
         |	margin: 3px;
         |	padding: 3px;
         |}
         |th {
         |	text-align: left;
         |	font: bold 10pt "Lucida Grande", Lucida, Verdana, sans-serif;
         |	background-color: #f8d0aa;
         |	border-style: none;
         |	padding: 2px;
         |}
         |tr {
         |	border-style: none;
         |}
         |td {
         |	font: 10pt "Lucida Grande", Lucida, Verdana, sans-serif;
         |	text-align: left;
         |	white-space: nowrap;
         |	padding: 3px;
         |	margin: 3px;
         |	border-style: none;
         |}
         |a {
         |	color: green;
         |	text-decoration: none;
         |}
         |</style>
       """.stripMargin

    val headingFrag =
      s"""
         |<h1>${reportHeader.get}</h1>
         |<h2><a href="https://www.wur.nl/en.htm">Wageningen University & Research</a></h2>
         |<hr>
       """.stripMargin

    val footerFrag =
      s"""
         |<h3>$footer</h3>
         |<h4>Powered by <a href="http://plus.aginfra.eu">AGINFRA+</a> and <a href="http://www.d4science.org">D4Science</a></h4>
       """.stripMargin

    val settingsTableFrag = s"""
       |<table border=1>
       |<tr><th>Parameter</th><th>Value</th></tr>
       |<tr><td>Title</td><td>${info.title}</td></tr>
       |<tr><td>Parcel year</td><td>${info.parcelYear}</td></tr>
       |<tr><td>Parcel crop code</td><td>${info.parcelCropCode}</td></tr>
       |<tr><td>Selection geometry</td><td>${info.geometryWkt}</td></tr>
       |<tr><td>Geometry epsg</td><td>${info.geometryEpsg}</td></tr>
       |<tr><td>Number of batches</td><td>${info.nrBatches}</td></tr>
       |<tr><td>Batch size</td><td>${info.batchSize}</td></tr>
       |<tr><td>Batch timeout (sec)</td><td>${info.batchTimeoutSec}</td></tr>
       |</table>
       |<hr>
     """.stripMargin

    val list = ListMap(items.toSeq.sortWith(_._1 < _._1):_*)

    val itemTableHeaderFrag = {
      s"""
         |<tr>
         |  <th>Batch ID</th>
         |  <th>Fields</th>
         |  <th>Results</th>
         |  <th>Errors</th>
         |  <th>Warnings</th>
         |  <th>Sum AREA [Ha]</th>
         |  <th>DVS<2</th>
         |  <th>Avg TSUM [C.d]</th>
         |  <th>Avg LAI MAX</th>
         |  <th>Avg TAGP [Kg/Ha]</th>
         |  <th>Avg HI</th>
         |  <th>Details</th>
         |  <th>Log</th>
         |</tr>
       """.stripMargin
    }

    val itemTableFraq = {
      for ((id, details) <- list) yield {
        s"""
           |<tr>
           |  <td>$id</td>
           |  <td>${details.getOrElse("fields[#]", "N/A")}</td>
           |  <td>${details.getOrElse("results[#]", "N/A")}</td>
           |  <td>${details.getOrElse("errors", "N/A")}</td>
           |  <td>${details.getOrElse("warnings", "N/A")}</td>
           |  <td>${details.getOrElse("sum(AREA)[Ha]", "N/A")}</td>
           |  <td>${details.getOrElse("DVS<2[#]", "N/A")}</td>
           |  <td>${details.getOrElse("avg(TSUM_END)[C.d]", "N/A")}</td>
           |  <td>${details.getOrElse("avg(LAI_MAX)[area/area]", "N/A")}</td>
           |  <td>${details.getOrElse("avg(TAGP_END)[Kg/Ha]", "N/A")}</td>
           |  <td>${details.getOrElse("avg(HI_END)[%]", "N/A")}</td>
           |  <td><a href="${details.getOrElse("summaryUrl", "")}">Open</a></td>
           |  <td><a href="${details.getOrElse("logUrl", "")}">Open</a></td>
           |</tr>
         """.stripMargin
      }
    }

    "<!DOCTYPE html>" + "<html>" +
      "<head>" + styleFrag + "</head>" +
      "<body><center>" +
      headingFrag + settingsTableFrag +
      "<table>" + itemTableHeaderFrag + itemTableFraq.mkString + "</table><hr>" +
      footerFrag +
      "</center></body></html>"
  }

}
