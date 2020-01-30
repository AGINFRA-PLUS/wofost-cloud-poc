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

import java.time.LocalDate

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Stash}
import nl.wur.wiss.core.{ScientificUnit, SimXChange}
import nl.wur.wissmodels.wofost.WofostModel
import nl.wur.wofostshell.parameters.{ParXChangeMarshaller, VarInfo}
import nl.wur.wofostsystem.AppProtocol

object Researcher {
  def props: Props = Props(new Researcher())
}


class Researcher extends Actor with ActorLogging with Stash {

  import AppProtocol._

  private var client: Option[ActorRef] = None
  private var study: Option[StudySpec] = None

  override def receive: Receive = ready

  private def ready: Receive = {
    case m: Msg => m match {
      case r: Research => research(r, sender())
      case r: PassResearch => passResearch(r, sender())
        case _ => log.warning(s"don't know how to handle message $m")
    }
  }

  private def busy: Receive = {
    case m: Msg => m match {
      case s: SimulationOk =>
        log.info(s"Simulation finished ${s.simID}")
        sender() ! PoisonPill
        val summary = extractSimResults(s.simData)
        study match {
          case Some(v) =>
            v.studyType match {
              case SimpleStudy | AgroDataCubeFieldIdStudy =>
                client.get ! AppProtocol.ResearchOk(study.get, s.locationInfo, summary, Some(s.simData))
              case _ =>
                client.get ! AppProtocol.ResearchOk(study.get, s.locationInfo, summary, None)
            }
          case _ => // void
        }
        study = None
        client = None
        context.become(ready)
      case s: SimulationFailed =>
        log.info(s"Simulation failed ${s.simID}")
        sender() ! PoisonPill
        client.get ! AppProtocol.ResearchFailed(study.get, s.locationInfo, "Failed")
        study = None
        client = None
        context.become(ready)
      case _ =>
        stash()
    }
  }

  private def passResearch(research: AppProtocol.PassResearch, sender: ActorRef): Unit = {
    log.debug(s"Passing on simulation${research.locationInfo map {t => s" for ${t.label}"} getOrElse ""}")
    sender ! AppProtocol.ResearchFailed(research.study, research.locationInfo, "Failed")
  }

  private def research(research: AppProtocol.Research, sender: ActorRef): Unit = {
    context.become(busy)
    client = Some(sender)
    study = Some(research.study)

    log.debug(s"Starting simulation${research.locationInfo map {t => s" for ${t.label}"} getOrElse ""}")
    research.study match {
      case spec: SimpleStudySpec => runSimulator(spec.id, research.locationInfo, research.modelInputs)
      case spec: SimpleSweepStudySpec => runSimulator(spec.id, research.locationInfo, research.modelInputs)
      case spec: AgroDataCubeFieldIdStudySpec => runSimulator(spec.id, research.locationInfo, research.modelInputs)
      case spec: AgroDataCubeFieldIdsStudySpec => runSimulator(spec.id, research.locationInfo, research.modelInputs)
      case _ =>
        throw new IllegalArgumentException("Unsupported study spec received by researcher")
    }
  }

  private def runSimulator(id: String, locationInfo: Option[LocationInfo], modelInputs: Seq[VarInfo]): Unit = {
    val simulator = context.actorOf(Simulator.props, "simulator")
    val parXChange = ParXChangeMarshaller.deserialize(modelInputs)
    simulator ! RunSimulation(id, locationInfo, parXChange)
  }

  private def extractSimResults(simData: SimXChange): Map[String, Map[String, Double]] = {
    val simStart = simData.getStartDate
    val simEnd   = simData.getEndDate
    val simIds   = simData.getSimIDsByVarName(WofostModel.DVS)

    var results = collection.mutable.Map[String, Map[String, Double]]()
    simIds.forEach { simId =>
      var simResults = collection.mutable.Map[String, Double]()
      val simItem = simData.getSimIDInfoBySimID(simId)
      val simItemEndDate = simData.getDateByDateIndex(simItem.endDateIndex)

      simResults += ("DVS_END" -> retrieveSimVar(simData, WofostModel.DVS, simItemEndDate, ScientificUnit.NODIM))
      simResults += ("LAI_END" -> retrieveSimVar(simData, WofostModel.LAI, simItemEndDate, ScientificUnit.NODIM_AREA))
      simResults += ("LAI_MAX" -> retrieveSimVar(simData, WofostModel.LAI, simItemEndDate, ScientificUnit.NODIM_AREA, Some(SimXChange.AggregationY.MAX)))
      simResults += ("TSUM_END" -> retrieveSimVar(simData, WofostModel.TSUM, simItemEndDate, ScientificUnit.CELSIUS_DAYS))
      simResults += ("RD_END" -> retrieveSimVar(simData, WofostModel.RD, simItemEndDate, ScientificUnit.CM))

      // calculate total dry weights of living and dead plant organs
      val twrtEnd  = retrieveSimVar(simData, WofostModel.WRT, simItemEndDate, ScientificUnit.KG_HA) +
        retrieveSimVar(simData, WofostModel.DWRT, simItemEndDate, ScientificUnit.KG_HA)
      val twstEnd  = retrieveSimVar(simData, WofostModel.WST, simItemEndDate, ScientificUnit.KG_HA) +
        retrieveSimVar(simData, WofostModel.DWST, simItemEndDate, ScientificUnit.KG_HA)
      val twlvEnd  = retrieveSimVar(simData, WofostModel.WLV, simItemEndDate, ScientificUnit.KG_HA) +
        retrieveSimVar(simData, WofostModel.DWLV, simItemEndDate, ScientificUnit.KG_HA)
      val twsoEnd  = retrieveSimVar(simData, WofostModel.WSO, simItemEndDate, ScientificUnit.KG_HA) +
        retrieveSimVar(simData, WofostModel.DWSO, simItemEndDate, ScientificUnit.KG_HA)
      // calculate harvest variables
      val tagpEnd = twlvEnd + twstEnd + twsoEnd
      val hiEnd = twsoEnd / tagpEnd

      // TODO: add WSO end value to results, present summary as 1.000 kg per ha values

      simResults += ("TAGP_END" -> tagpEnd, "HI_END" -> hiEnd)
      results += (simId -> simResults.toMap)
    }
    results.toMap
  }

  private def retrieveSimVar(simData: SimXChange, varName: String, atDate: LocalDate,
                             units: ScientificUnit, aggY: Option[SimXChange.AggregationY] = None): Double = {
    val token = simData.getTokenReadByVarNameDate(varName, atDate, true)
    aggY match {
      case Some(agg) => simData.getValueBySimIDVarNameAgg(token, units, agg)
      case None => simData.getValueBySimIDVarnameDate(token, atDate, units)
    }
  }

}


