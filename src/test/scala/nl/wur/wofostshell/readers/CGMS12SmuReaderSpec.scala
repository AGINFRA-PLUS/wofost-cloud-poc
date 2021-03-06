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
package nl.wur.wofostshell.readers

import java.util.Properties

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.scalatest.{FlatSpec, Matchers}


/**
  * Unit test for the Oracle CMGS12 database simulation units reader.
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
class CGMS12SmuReaderSpec extends FlatSpec with Matchers {

  private val gridcellNr: Long = 49152
  private val cropNr: Int = 2

  object Fixture {
    var db: Option[CGMS12SmuReader] = None

    // change LOG4J settings
    Configurator.initialize("logger", classOf[CGMS12SmuReaderSpec].getResource("/log4j2.xml").getPath)
    Configurator.setRootLevel(Level.ERROR)

    // get configuration file and use it to create a database reader
    val p = new Properties()
    p.load(classOf[CGMS12SmuReaderSpec].getResourceAsStream("/wofostshell.properties"))
    val user = if (p.containsKey("db_user")) Some(p.getProperty("db_user")) else None
    val pass = if (p.containsKey("db_password")) Some(p.getProperty("db_password")) else None
    db = Some(new CGMS12SmuReader(p.getProperty("db_url"), user, pass))
  }


  "A CGMS12SmuReader" should "initialize correctly" in {
    Fixture.db.get.init(cropNr, Some(List(gridcellNr)))

    Fixture.db.get.cropNr should be (Some(2))
    Fixture.db.get.simulationUnits.size should be (1)
    Fixture.db.get.simulationUnits(49152).size should be (37)
  }

}
