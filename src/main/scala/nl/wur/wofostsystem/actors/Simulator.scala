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

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import nl.wur.wiss.core.{SimXChange, TimeDriver}
import nl.wur.wissmodels.wofost.WofostModel
import nl.wur.wofostsystem.AppProtocol
import nl.wur.wofostsystem.actors.Simulator.Done


object Simulator {
  import AppProtocol._

  final case class Done(e: Either[SimulationOk, SimulationFailed], researcher: ActorRef)

  def props: Props = Props(new Simulator())
}


class Simulator extends Actor with ActorLogging with Stash {

  import AppProtocol._

  override def receive: Receive = ready

  private def ready: Receive = {
    case m: Msg => m match {
      case r: RunSimulation => runSimulation(r)
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

  private def runSimulation(simulation: AppProtocol.RunSimulation): Unit = {
    context.become(busy)

    // run simulation and send Done message with Either to self when complete
    val simId = s"wofost-run-${simulation.simID}"
    val simXChange = new SimXChange(simId)
    context.self ! Done(
      try {
        log.debug(s"$simId: start simulation")
        val wofost = new WofostModel(simulation.simData, simXChange)
        val timeDriver = new TimeDriver(wofost)
        timeDriver.run()
        log.debug(s"$simId: simulation completed")
        Left(SimulationOk(simId, simulation.locationInfo, simXChange))
      } catch {
        case e@(_: Exception | _: Error) =>
          val msg = s"$simId: simulation failed, ${e.getMessage}"
          log.error(msg, e)
          Right(SimulationFailed(simId, simulation.locationInfo, msg))
      }, sender())
  }

  private def process(r: Either[SimulationOk, SimulationFailed], sender: ActorRef): Unit = {
    r fold (
      f => {
        sender ! f
      },
      s => sender ! s)
  }

}
