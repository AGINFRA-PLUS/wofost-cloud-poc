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

import java.util.Locale

import nl.wur.wofostsystem.AppProtocol.SimpleStudySpec


object RunFileSimulation {
  def main(args: Array[String]): Unit = {
    Locale.setDefault(Locale.US)

    try {
      if (args.length != 3) {
        throw new IllegalArgumentException("usage: title parameters.[json|ser] timeout_sec")
      }

      val title = args(0)
      val paramFile = args(1)
      val timeoutSec = args(2).toInt

      // validate arguments
      if (paramFile.isEmpty) {
        throw new IllegalArgumentException("Please specify a valid WOFOST parameter file.")
      }

      // create the study specification
      val jobId = title.toLowerCase.replace(" ", "-")
      val study = SimpleStudySpec(jobId, title, "Not provided", paramFile, timeoutSec)

      FileBasedStudyExecutor.performStudy(study)
    } catch {
      case e: Throwable => throw e
    }
  }

}
