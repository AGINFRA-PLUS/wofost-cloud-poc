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

import nl.wur.agrodatacube.processors.waterretention.WaterRetentionCalculatorError.{InvalidNumberOfColumnsInInputFile, InvalidStaringreeksCodeError}
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.{EitherValues, Matchers, WordSpec}


class StaringWaterRetentionCalculatorSpec extends WordSpec with Matchers with TypeCheckedTripleEquals with EitherValues {

  // read the data (file is expected to not have problems)
  val calculator = StaringWaterRetentionCalculator.load("/data/staringreeks_ksat_vmc_lookup.csv").right.value

  "A water retention calculator" should {
    "have read the proper data for code B1" in {
      calculator.lookupStaringCode("B1") match {
        case None => fail()
        case Some(p) =>
          p.ksat should ===(33.3)
          p.vmcSat should ===(0.371)
          p.vmcFc should ===(0.201)
          p.vmcWp should ===(0.03)
      }
    }

    "have read the proper data for code O12" in {
      calculator.lookupStaringCode("O12") match {
        case None => fail()
        case Some(p) =>
          p.ksat should === (10.8)
          p.vmcSat should === (0.49)
          p.vmcFc should === (0.452)
          p.vmcWp should ===(0.313)
      }
    }

    "not provide results for an unknown code C8" in {
      calculator.lookupStaringCode("C8") match {
        case Some(p) => fail()
        case None => // void
      }
    }

    "provide multiple error messages when reading a corrupt file" in {
      StaringWaterRetentionCalculator.load("/data/staringreeks_ksat_vmc_lookup_corrupt.csv") match {
        case Left(list) =>
          list.length should === (3)
          list.count(_.isInstanceOf[InvalidNumberOfColumnsInInputFile]) should === (1)
          list.count(_.isInstanceOf[InvalidStaringreeksCodeError]) should === (2)
        case Right(_) => fail()
      }
    }
  }

}
