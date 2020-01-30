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

import java.time.LocalDate

import akka.actor.ActorSystem
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}


class MeteoReaderSpec extends FlatSpec with Matchers {

  val system = ActorSystem("scalatest")
  val log = Logging.getLogger(system, this)

  val config = ConfigFactory.load()
  val token = config.getString("application.agrodatacube.token")

  "A MeteoReader" should "read a list of nearest meteostations for a field" in {
    val r = new MeteoReader()
    r.getFieldMeteoStations(1, token, log) match {
      case Left(e) => fail(e)
      case Right(stations) =>
        stations.size should be (5)
    }
  }

  "A MeteoReader" should "read a details for a meteostation" in {
    val r = new MeteoReader()
    r.getMeteoStation(277, token, log) match {
      case Left(e) => fail(e)
      case Right(stations) =>
        stations.size should be (1)
    }
  }

  "A MeteoReader" should "read meteodata for a meteostation" in {
    val r = new MeteoReader()
    r.getMeteoData(277, LocalDate.of(2017, 1, 1),
      LocalDate.of(2017, 1, 31), token, log) match {
      case Left(e) => fail(e)
      case Right(meteo) =>
        meteo.size should be (31)
    }
  }

}
