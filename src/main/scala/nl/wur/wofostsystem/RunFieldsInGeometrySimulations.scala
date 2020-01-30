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
import java.util.Locale

import nl.wur.wofostsystem.AppProtocol._


object RunFieldsInGeometrySimulations {
  // TODO: should not be hard coded here, request from wofostsystem somehow?
  //  or add a function to very if a certain cropcode can be processed?
  private val supportedCropCodes = Seq("233", "234", "236", "252", "253", "254", "255", "256", "257", "259")

  def main(args: Array[String]): Unit = {
    Locale.setDefault(Locale.US)
    try {
      if (args.length != 5) {
        throw new IllegalArgumentException("usage: title geometry_wkt geometry_epsg parcel_year parcel_crop_code timeout_sec")
      }

      val title = args(0)
      val geometryWkt = args(1)
      val geometryEpsg = args(2)
      val parcelYear = args(3).toInt
      val parcelCropCode = args(4)
      val timeoutSec = args(5).toInt

      if (parcelYear < 2000) {
        throw new IllegalArgumentException("Please specify a more recent year, data before 2000 is not available.")
      }
      if (parcelYear > LocalDate.now().getYear) {
        throw new IllegalArgumentException("Please specify a year that is not in the future.")
      }
      if (!supportedCropCodes.contains(parcelCropCode)) {
        throw new IllegalArgumentException(s"Please select on of the supported crop codes: ${supportedCropCodes.mkString(",")}")
      }

      // create the study specification
      val jobId = title.toLowerCase.replace(" ", "-")
      val study = AgroDataCubeGeometryStudySpec(jobId, title, "Not provided", geometryWkt, geometryEpsg, parcelYear, parcelCropCode, timeoutSec)

      AgroDataCubeStudyExecutor.performStudy(study)
    } catch {
      case e: Throwable => throw e
    }
  }
}
