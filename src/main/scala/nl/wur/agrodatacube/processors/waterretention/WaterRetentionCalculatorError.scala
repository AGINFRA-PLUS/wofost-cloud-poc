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
package nl.wur.agrodatacube.processors.waterretention

sealed abstract class WaterRetentionCalculatorError(val message: String)

object WaterRetentionCalculatorError {

  case class InvalidNumberOfColumnsInInputFile(line: String)
    extends WaterRetentionCalculatorError(s"Invalid number of columns in line: $line")

  case class NumberConversionError(line: String, e: Throwable)
    extends WaterRetentionCalculatorError(s"Number conversion error trying to parse line: $line, ${e.getMessage}")

  case class InvalidStaringreeksCodeError(line: String, code: String)
    extends WaterRetentionCalculatorError(s"Invalid Staringreeks code $code found in line: $line")
}
