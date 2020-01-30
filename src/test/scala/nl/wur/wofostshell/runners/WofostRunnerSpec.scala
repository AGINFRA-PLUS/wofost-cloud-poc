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
package nl.wur.wofostshell.runners

import java.util.Properties

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.scalatest.{FlatSpec, Matchers}


/**
  * Unit test for the WOFOST simulation model runner.
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
class WofostRunnerSpec extends FlatSpec with Matchers {

  object Fixture {
    val logLevel: Level = Level.DEBUG
    val logConfig: String = classOf[WofostRunnerSpec].getResource("/log4j2.xml").getPath

    // change LOG4J settings
    Configurator.initialize("logger", logConfig)
    Configurator.setRootLevel(logLevel)

    // get configuration file and use it to create a database reader
    private val p = new Properties()
    p.load(classOf[WofostRunnerSpec].getResourceAsStream("/wofostshell.properties"))

    val dbUrl: Option[String] = if (p.containsKey("db_url")) Some(p.getProperty("db_url")) else None
    val dbUser: Option[String] = if (p.containsKey("db_user")) Some(p.getProperty("db_user")) else None
    val dbPass: Option[String] = if (p.containsKey("db_password")) Some(p.getProperty("db_password")) else None
  }

  private var runner: Option[WofostRunner] = None
  // TODO: private var reporter: WofostSummaryReporter
  private var simGridCellNr: Long = 0
  private var simRegionName: String = ""
  private var simStuNr: Long = 0
  private var simCropNr: Int = 0
  private var simYear: Int = 0

}
