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

import java.time.LocalDate
import java.util.{Locale, UUID}

import nl.wur.wofostsystem.AppProtocol._


object RunFieldsSimulations {
  def main(args: Array[String]): Unit = {
    Locale.setDefault(Locale.US)

    try {
      if (args.length != 4) {
        throw new IllegalArgumentException("usage: title parcel_id[,...] parcel_year timeout_sec")
      }

      val title = args(0)
      val parcelIds = args(1)
      val parcelYear = args(2).toInt
      val timeoutSec = args(3).toInt

      // validate arguments
      if (parcelYear < 2000) {
        throw new IllegalArgumentException("Please specify a more recent year, data before 2000 is not available.")
      }
      if (parcelYear > LocalDate.now().getYear) {
        throw new IllegalArgumentException("Please specify a year that is not in the future.")
      }
      if (parcelIds.isEmpty) {
        throw new IllegalArgumentException("Please specify one or more valid field IDs.")
      }

      // create the study specification
      val jobId = title.toLowerCase.replace(" ", "-")
      val list = parcelIds.split(",").map { _.trim.toLong }
      val study = AgroDataCubeFieldIdsStudySpec(jobId, title, "Not provided", list, parcelYear, timeoutSec)

      AgroDataCubeStudyExecutor.performStudy(study)
    } catch {
      case e: Throwable => throw e
    }
  }
}
