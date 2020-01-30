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

import akka.actor.ActorSystem
import akka.testkit
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import nl.wur.wofostshell.parameters.VarMeteoData
import nl.wur.wofostsystem.AppProtocol.{AgroDataCubeFieldIdsStudySpec, PreparationFailed, PreparationOk, Prepare}
import nl.wur.wofostsystem.actors.AgroDataCubeLibrarian
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.duration._


class AgroDataCubeLibrarianSpec extends TestKit(ActorSystem("testsystem"))
  with ImplicitSender
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {

  "An AgroDataCubeLibrarian" must {

    "find a proper meteo dataset for field 8574475 (2018)" in {
      val actor = testkit.TestActorRef[AgroDataCubeLibrarian]

      val fieldId = 8574475
      val fieldSpec = AgroDataCubeFieldIdsStudySpec(s"$fieldId", s"Test spec for field $fieldId", "NA", Seq[Long](fieldId), 2018, 60)

      actor ! Prepare(fieldSpec, 0)

      expectMsgPF(30.seconds) {
        case PreparationOk(study, fieldIndex, _, modelInputs, _) =>
          study.id must be(s"$fieldId")
          modelInputs.size must be(112)
          val meteoData = modelInputs.find(p => p.isInstanceOf[VarMeteoData])
          meteoData.nonEmpty must be(true)
          meteoData.get.asInstanceOf[VarMeteoData].name must be("METEO")
          meteoData.get.asInstanceOf[VarMeteoData].values.size must be(365)
        case PreparationFailed(study, fieldIndex, reason, _) =>
          fail(reason)
      }

    }
  }

}
