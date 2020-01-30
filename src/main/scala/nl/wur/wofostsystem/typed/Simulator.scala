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
package nl.wur.wofostsystem.typed

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import nl.wur.wiss.core.{ParXChange, SimXChange}

// TODO: move to using new Akka Typed Actors
//  These are some first tries ...

object Simulator {
  def apply(simulatorId: String): Behavior[SimulatorMessage] =
    Behaviors.setup(context => new Simulator(context, simulatorId))

  // typed messages
  sealed trait SimulatorMessage
  final case class RunSimulation(id: String, input: ParXChange, replyTo: ActorRef[SimulatorResult]) extends SimulatorMessage
  final case class SimulatorResult(id: String, result: Either[SimulatorError, SimXChange]) extends SimulatorMessage

  // typed errors
  sealed trait SimulatorError {
    def msg: String
  }
  final case class NotImplementedError(msg: String = "Not Implemented Yet") extends SimulatorError
}

class Simulator(context: ActorContext[Simulator.SimulatorMessage], simulatorId: String)
  extends AbstractBehavior[Simulator.SimulatorMessage] {

  import Simulator._

  context.log.info("Simulator {} started", simulatorId)

  override def onMessage(msg: SimulatorMessage): Behavior[SimulatorMessage] = {
    msg match {
      case RunSimulation(id, input, replyTo) =>
        // TODO: run simulation and send response back
        replyTo ! SimulatorResult(id, Left(NotImplementedError()))
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[SimulatorMessage]] = {
    case PostStop =>
      context.log.info("Device {} stopped", simulatorId)
      this
  }
}