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

import java.sql.{Connection, SQLException}
import java.util.Locale

import org.apache.commons.logging.LogFactory

import scala.collection.mutable

// TODO: switch to using akka logging framework


/**
  * A reader that retrieves weather data from an Oracle CGMS12 database
  * using a JDBC connection.
  *
  * The reader highly depends on the specifics of how the data is stored in
  * the database. Changes to that will require updating of the reader.
  *
  * Note that this source code has been more or less automatically converted
  * from Java, so Scala syntax might not be very elegant.
  *
  * @param dbUrl JDBC url for database to connect to
  * @param dbUser user name for database login
  * @param dbPassword password for database login
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
class CGMS12SmuReader(dbUrl: String, dbUser: Option[String] = None, dbPassword: Option[String] = None) {

  private val logger = LogFactory.getLog(classOf[CGMS12SmuReader])
  private val dBConnector = new JDBCConnector(dbUrl, dbUser, dbPassword)

  var cropNr: Option[BigInt] = None
  val simulationUnits = new mutable.HashMap[Long, mutable.Set[Long]]()

  @throws[SQLException]
  def init(cropNr: Int, gridCellNumbers: Option[List[Long]]): CGMS12SmuReader = {
    // clear state
    this.cropNr = None
    this.simulationUnits.clear()

    val conn = dBConnector.connect()
    try {
      if (!fetchSimulationUnitEntries(conn, cropNr, gridCellNumbers)) {
        val msg = s"Could not initialise simulation units data reader for crop: $cropNr"
        logger.error(msg)
        throw new IllegalArgumentException(msg)
      } else {
        this.cropNr = Some(BigInt(cropNr))
      }
    } finally if (conn != null) conn.close()

    this
  }


  @throws[SQLException]
  private def fetchSimulationUnitEntries(conn: Connection, cropNr: BigInt, gridCellNumbers: Option[List[Long]] = None): Boolean = {
    var sql = "SELECT GRID_NO, STU_NO FROM SIMULATION_UNIT WHERE CROP_NO = %d".formatLocal(Locale.US, cropNr)
    if (gridCellNumbers.isDefined) {
      val gridNrList = gridCellNumbers.get.mkString(", ")
      sql = s"$sql AND GRID_NO in ($gridNrList)"
    }

    val stmt = conn.createStatement
    val resultSet = stmt.executeQuery(sql)
    try {
      if (!resultSet.isBeforeFirst) {
        logger.error(s"Failed deriving available simulation units from 'simulation_unit' table for crop: $cropNr")
        false
      } else {
        var count = 0
        while (resultSet.next()) {
          val gridNr = resultSet.getBigDecimal(1).longValue()
          val stuNr = resultSet.getBigDecimal(2).longValue()
          var values = simulationUnits.getOrElse(gridNr, new mutable.HashSet[Long]())
          values += stuNr
          simulationUnits.put(gridNr, values)
          count += 1
        }
        logger.info(s"$count records selected from simulation units for crop: $cropNr")
        true
      }
    } finally {
      if (resultSet != null) resultSet.close()
      if (stmt != null) stmt.close()
    }
  }
}
