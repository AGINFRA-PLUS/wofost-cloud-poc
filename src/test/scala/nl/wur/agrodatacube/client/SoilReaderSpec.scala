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
package nl.wur.agrodatacube.client

import akka.actor.ActorSystem
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}


class SoilReaderSpec extends FlatSpec with Matchers {

  val system = ActorSystem("scalatest")
  val log = Logging.getLogger(system, this)

  val config = ConfigFactory.load()
  val token = config.getString("application.agrodatacube.token")

  "A SoilReader" should "read a list of soils for a field" in {
    val r = new SoilReader()
    r.getFieldSoils(1, token, log) match {
      case Left(e) => fail(e)
      case Right(fieldSoils) =>
        println(fieldSoils)
        fieldSoils.size should be (2)
    }
  }

//  "A SoilReader" should "read soil parameters" in {
//    val r = new SoilReader()
//    r.getSoilParams(317, token, log) match {
//      case Left(e) => fail(e)
//      case Right(soilParams) =>
//        soilParams.size should be (5)
//    }
// }

}
