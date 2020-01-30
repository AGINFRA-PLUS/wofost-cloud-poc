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

import java.sql.{Connection, DriverManager, SQLException}

import oracle.jdbc.pool.OracleDataSource
import org.apache.commons.logging.LogFactory

// TODO: Optimize for async use and possible pooling?

/** Currently supported database types. */
object DatabaseType extends Enumeration {
  type DatabaseType = Value
  val Unknown, Oracle, SQLite = Value
}

import DatabaseType._

/** Provides a JDBC connection to a database.
  *
  * @constructor Create a new instance with a `url`, `username`, and `password`.
  * @param dbUrl JDBC connection string
  * @param dbUser user name, can be empty
  * @param dbPassword password, can be empty
  *
  * @todo Integrate with Akka logging framework.
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
class JDBCConnector(dbUrl: String, dbUser: Option[String] = None, dbPassword: Option[String] = None) {

  private val LOGGER = LogFactory.getLog(classOf[JDBCConnector])

  /** Returns the type of database that the connector will connect to.
    *
    * @return [[nl.wur.wofostshell.readers.DatabaseType]]
    */
  def databaseType(): DatabaseType = {
    if (dbUrl.contains("oracle")) return DatabaseType.Oracle
    if (dbUrl.contains("sqlite")) return DatabaseType.SQLite
    DatabaseType.Unknown
  }


  /** Establishes a connection to the database.
    *
    * Don't forget to close the connection when done.
    *
    * @return [[java.sql.Connection]], can be used to communicate with the database.
    * @throws [[java.sql.SQLException]] when failed
    */
  @throws[SQLException]
  def connect(): Connection = {
    var conn: Connection = null

    databaseType() match {
      case Oracle =>
        val ods = new OracleDataSource()
        ods.setURL(dbUrl)
        ods.setUser(dbUser.getOrElse(""))
        ods.setPassword(dbPassword.getOrElse(""))
        conn = ods.getConnection()
        LOGGER.info("Database : " + conn.getMetaData.getDatabaseProductName + ", driver: " + conn.getMetaData.getDriverName)
      case SQLite =>
        try
          Class.forName("org.sqlite.JDBC")
        catch {
          case e: ClassNotFoundException =>
            LOGGER.error("Trying to use SQLite database but can not load SQLite JDBC driver", e)
            throw e
        }
        conn = DriverManager.getConnection(dbUrl)
        LOGGER.info("Database : " + conn.getMetaData.getDatabaseProductName + ", driver: " + conn.getMetaData.getDriverName)
      case _ => LOGGER.warn("Unknown database type, cannot establish a connection.")
    }
    conn
  }

}
