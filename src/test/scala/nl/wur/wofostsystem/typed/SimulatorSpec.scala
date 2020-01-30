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

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import nl.wur.wiss.core.ParXChange
import org.scalatest.WordSpecLike


class SimulatorSpec extends ScalaTestWithActorTestKit with WordSpecLike {
  import Simulator._

  "Simulator Actor" must {
    "Return a simulation not implemented error result" in {
      val probe = createTestProbe[SimulatorResult]()
      val simulatorActor = spawn(Simulator("simulator1"))

      val params: ParXChange = new ParXChange()

      simulatorActor ! Simulator.RunSimulation(id = "42", input = params, replyTo = probe.ref)

      val response = probe.receiveMessage()
      response.id should ===("42")
      response.result.isLeft should ===(true)
      response.result.left.get shouldBe a [NotImplementedError]
    }
  }

}
