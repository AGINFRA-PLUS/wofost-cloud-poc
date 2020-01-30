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

import java.util.Locale

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import nl.wur.wofostsystem.AppProtocol.{AgroDataCubeFieldIdStudySpec, AgroDataCubeFieldIdsStudySpec, AgroDataCubeGeometryStudySpec}
import org.scalatest.{MustMatchers, WordSpecLike}


class AgroDataCubeStudyExecutorSpec extends TestKit(ActorSystem("testsystem"))
  with ImplicitSender
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {

  Locale.setDefault(Locale.US)

  "An AgroDataCubeStudyExecutor" must {

    // Hendrik's tests - Nagele1, winter wheat (233), 2018
    "Create a binary parameter inputs file for crop field 8402129" in {
      val study = AgroDataCubeFieldIdStudySpec("nagele", "Nagele wintertarwe (233) 2018", "Not provided", 8402129, 2018)
      AgroDataCubeStudyExecutor.performStudy(study)
    }

    // Hendrik's tests - Nagele2, sugarbeet (256), 2018
//    "Create a binary parameter inputs file for crop field 8499523" in {
//      val study = AgroDataCubeFieldIdStudySpec("nagele", "Nagele suikerbieten (256) 2018", "Not provided", 8499523, 2018)
//      AgroDataCubeStudyExecutor.performStudy(study)
//    }
//
//    "Create a binary parameter inputs file for crop field 7977923" in {
//      val study = AgroDataCubeFieldIdStudySpec("limburg", "Limburg Maize (259) 2018", "Not provided", 7977923, 2018)
//      AgroDataCubeStudyExecutor.performStudy(study)
//    }

//    "Run a successful set of crop simulations selected by a geometry" in {
//      val study = AgroDataCubeGeometryStudySpec("sample", "Test AgroDataCube Geometry Job", "Not provided",
//        "POLYGON((8927 314714, 8927 464932, 138515 464932, 138515 314714, 8927 314714))",
//        "28992", 2018, "256", 10, 3, 240)
//      AgroDataCubeStudyExecutor.performStudy(study)
//    }
//
//    "Run a successful crop simulation for a specific crop parcel" in {
//      // Flevoland, Maize (259), 2018: 8269399, 8079885, 8067151, 8386712, 8079398
//      val study = AgroDataCubeFieldIdStudySpec("sample", "Test Specification", "Not provided", 8269399, 2018)
//      AgroDataCubeStudyExecutor.performStudy(study)
//    }
//
//    "Run successful crop simulations for multiple crop parcels" in {
//      // Flevoland, Maize (259), 2018: 8269399, 8079885, 8067151, 8386712, 8079398
//      val study = AgroDataCubeFieldIdsStudySpec("sample", "Test Specification", "Not provided", Seq(8269399, 8079885, 8067151), 2018)
//      AgroDataCubeStudyExecutor.performStudy(study)
//    }
  }

}
