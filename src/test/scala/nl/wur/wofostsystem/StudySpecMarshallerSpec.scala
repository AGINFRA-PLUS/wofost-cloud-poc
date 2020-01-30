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

import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source


class StudySpecMarshallerSpec extends FlatSpec with Matchers {

  private val inputFileName = "data/sample_simple_study.json"

  "A StudySpecMarshaller" should "be able to read JSON" in {
    val bufferedSource = Source.fromFile(inputFileName)
    val json = bufferedSource.getLines().mkString
    val spec = AppProtocol.StudySpecMarshaller.fromJson(json)
    println(spec)
  }

}
