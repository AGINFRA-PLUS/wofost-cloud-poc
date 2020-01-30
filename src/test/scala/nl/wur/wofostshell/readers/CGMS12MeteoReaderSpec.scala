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

import java.sql.SQLException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Properties

import nl.wur.wiss.core.ScientificUnit
import nl.wur.wiss.meteoutils.MeteoElement
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters


class CGMS12MeteoReaderSpec extends FlatSpec with Matchers {

  private val gridcellNr: Long = 110097

  object Fixture {
    var db: Option[CGMS12MeteoReader] = None

    // change LOG4J settings
    Configurator.initialize("logger", classOf[CGMS12MeteoReaderSpec].getResource("/log4j2.xml").getPath)
    Configurator.setRootLevel(Level.ERROR)

    // get configuration file and use it to create a database reader
    val p = new Properties()
    p.load(classOf[CGMS12SmuReaderSpec].getResourceAsStream("/wofostshell.properties"))
    val user = if (p.containsKey("db_user")) Some(p.getProperty("db_user")) else None
    val pass = if (p.containsKey("db_password")) Some(p.getProperty("db_password")) else None
    db = Some(new CGMS12MeteoReader(p.getProperty("db_url"), user, pass))
  }


  "A CGMS12MeteoReader" should "connect successfully" in {
    try {
      Fixture.db.get.init(gridcellNr)
    } catch {
      case e: SQLException =>
        fail(s"Database connection failed: $e.getMessage")
    }
  }


  "A CGMS12MeteoReader" should "retrieve correct location data" in {
    Fixture.db.get.init(gridcellNr)
    Fixture.db.get.getAltitudeM should be (1.0 +- 0.00001)
    Fixture.db.get.getLatitudeDD should be (52.84634 +- 0.00001)
    Fixture.db.get.getLongitudeDD should be (5.52014 +- 0.00001)
  }


  "A CGMS12MeteoReader" should "prepare weather values for a year" in {
    Fixture.db.get.init(gridcellNr)

    val elements = Set(MeteoElement.TM_MN, MeteoElement.TM_MX, MeteoElement.TM_AV,
      MeteoElement.VP_AV,
      MeteoElement.WS2_AV, MeteoElement.WS10_AV,
      MeteoElement.PR_CU, MeteoElement.Q_CU,
      MeteoElement.E0, MeteoElement.ES0, MeteoElement.ET0
    )
    Fixture.db.get.prepare(LocalDate.of(2010, 1, 1), LocalDate.of(2010, 12, 31), JavaConverters.setAsJavaSet(elements))
    Fixture.db.get.getStore.size() should be (365)
  }


  "A CGMS12MeteoReader" should "prepare weather values for a single day" in {
    Fixture.db.get.init(gridcellNr)

    val elements = Set(MeteoElement.TM_MN, MeteoElement.TM_MX, MeteoElement.TM_AV,
      MeteoElement.VP_AV,
      MeteoElement.WS2_AV, MeteoElement.WS10_AV,
      MeteoElement.PR_CU, MeteoElement.Q_CU,
      MeteoElement.E0, MeteoElement.ES0, MeteoElement.ET0
    )
    Fixture.db.get.prepare(LocalDate.of(2010, 1, 1), LocalDate.of(2010, 1, 1), JavaConverters.setAsJavaSet(elements))
    Fixture.db.get.getStore.size() should be (1)
  }


  "A CGMS12MeteoReader" should "prepare only the requested weather values" in {
    Fixture.db.get.init(gridcellNr)

    val elements = Set(MeteoElement.TM_MN, MeteoElement.TM_MX, MeteoElement.TM_AV)
    Fixture.db.get.prepare(LocalDate.of(2010, 1, 1), LocalDate.of(2010, 12, 31), JavaConverters.setAsJavaSet(elements))
    Fixture.db.get.getStore.size() should be (365)
    Fixture.db.get.getPreparedElements.containsAll(JavaConverters.setAsJavaSet(elements)) should be (true)
  }


  "A CGMS12MeteoReader" should "retrieve the correct temperature values" in {
    val elements = Set(MeteoElement.TM_MN, MeteoElement.TM_MX, MeteoElement.TM_AV)
    val startDate = LocalDate.of(2010, 8, 10)
    val endDate = LocalDate.of(2010, 8, 16)
    val nrDays = ChronoUnit.DAYS.between(startDate, endDate) + 1

    // load the data
    Fixture.db.get.init(gridcellNr)
    Fixture.db.get.prepare(startDate, endDate, JavaConverters.setAsJavaSet(elements))

    // validation date
    val tmAV = List(17.5, 18.1, 16.0, 17.0, 16.7, 17.5, 19.3)

    // validate
    Fixture.db.get.getStore.size() should be (nrDays)
    Fixture.db.get.getPreparedElements.containsAll(JavaConverters.setAsJavaSet(elements)) should be (true)

    for (i <- 0 until nrDays.toInt) {
      val v = Fixture.db.get.getValue(startDate.plusDays(i), MeteoElement.TM_AV, ScientificUnit.CELSIUS)
      v should be (tmAV(i) +- 0.01)
    }
  }

}
