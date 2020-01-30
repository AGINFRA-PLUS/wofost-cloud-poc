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

/**
  * A reader that retrieves soil data from a CGMS12 database using a
  * JDBC connection and SQL queries. So far it has been tested with
  * Oracle and SQLite.
  *
  * The reader highly depends on the specifics of how the data is stored in
  * the database. Changes to that will require updating of the reader.
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
class CGMS12SoilReader(dbUrl: String, dbUser: Option[String] = None, dbPassword: Option[String] = None) {

  // TODO: Implement. See Kotlin source code in WISS/wofostshell.

}
