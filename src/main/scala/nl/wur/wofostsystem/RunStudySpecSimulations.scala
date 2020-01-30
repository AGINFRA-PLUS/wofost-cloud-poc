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

import nl.wur.wofostsystem.AppProtocol._

import scala.io.Source


object RunStudySpecSimulations {
  def main(args: Array[String]): Unit = {
    Locale.setDefault(Locale.US)

    try {
      if (args.length != 1) {
        throw new RuntimeException("usage: study_spec.json")
      }

      // read the requested study specification
      val studySpecFileName = args(0)
      val bufferedSource = Source.fromFile(studySpecFileName)
      val spec = StudySpecMarshaller.fromJson(bufferedSource.getLines().mkString)
      bufferedSource.close()

      spec.studyType match {
        case SimpleStudy =>
          FileBasedStudyExecutor.performStudy(spec.asInstanceOf[SimpleStudySpec])
        case SimpleSweepStudy =>
          FileBasedStudyExecutor.performStudy(spec.asInstanceOf[SimpleSweepStudySpec])
        case AgroDataCubeFieldIdStudy =>
          AgroDataCubeStudyExecutor.performStudy(spec.asInstanceOf[AgroDataCubeFieldIdStudySpec])
        case AgroDataCubeFieldIdsStudy =>
          AgroDataCubeStudyExecutor.performStudy(spec.asInstanceOf[AgroDataCubeFieldIdsStudySpec])
        case AgroDataCubeGeometryStudy =>
          AgroDataCubeStudyExecutor.performStudy(spec.asInstanceOf[AgroDataCubeGeometryStudySpec])
        case _ =>
          throw new IllegalArgumentException(s"Unsupported studyType ${spec.studyType} specified in study specification file.")
      }
    } catch {
      case e: Throwable => throw e
    }
  }

}
