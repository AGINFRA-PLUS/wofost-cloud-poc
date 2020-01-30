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

import org.apache.commons.math3.linear.{RealMatrix}
import org.scalatest.{FlatSpec, Matchers}


class YamlCropReaderSpec extends FlatSpec with Matchers {

  private val cropParamFileName = "data/WOFOST_crop_parameters/wheat.yaml"
  private val cropVarietyName = "Winter_wheat_101"

  object Fixture {
    val reader = new YamlCropReader()
    reader.read(cropParamFileName)
    reader.loadCropParametersForVariety(cropVarietyName)
  }

  implicit val reader: YamlCropReader = Fixture.reader


  "A YamlCropReader" should "read and retrieve the correct crop varieties from a file" in {
    val names = reader.availableCropVarietyNames().mkString(", ")
    names should be ("Winter_wheat_101, Winter_wheat_102, Winter_wheat_103, Winter_wheat_104, Winter_wheat_105, Winter_wheat_106, Winter_wheat_107")
    reader.hasCropVariety(cropVarietyName) should be (true)
    reader.hasCropVariety("bar") should be (false)
  }


  "A YamlCropReader" should "be able to properly decode the crop parameters" in {
    reader.hasCropVariety(cropVarietyName) should be (true)
    try {
      reader.loadCropParametersForVariety(cropVarietyName)
    } catch {
      case e: Exception => {
        fail(e.getMessage)
      }
    }
  }


  "A YamlCropReader" should "read the correct number of parameters from the wheat.yaml file" in {
    reader.availableParameters().size should be (90)
  }


  "A YamlCropReader" should "set the correct scientific units for a crop parameter" in {
    val tsum1 = reader.parameter("tsum1")
    tsum1 match {
      case Some(pd) => pd.units(0).getUnitCaption should equal ("[C.d]")
      case None => fail()
    }
  }


  "A YamlCropReader" should "read integer crop parameters correctly" in {
    reader.getOrElse("idsl", None) should be (2)
  }


  "A YamlCropReader" should "read double crop parameters correctly" in {
    reader.getOrElse("tsum1", None) should be (543.0)
    reader.getOrElse("tsum2", None) should be (1194.0)
    reader.getOrElse("rms", None) should be (0.015)
  }


  "A YamlCropReader" should "read interpolation table crop parameters correctly" in {
    reader.get("DTSMTB") match {
      case Some(table: RealMatrix) => {
        table.getRowDimension should be (3)
        table.getColumnDimension should be (2)
        table.getEntry(1, 0) should be (30.0 +- 1e-7)
        table.getEntry(2, 0) should be (45.0 +- 1e-7)
        table.getEntry(2, 1) should be (30.0 +- 1e-7)
      }
      case None => fail("DTSMTB crop variable is missing")
      case _ => fail("DTSMTB crop variable is of unexpected type")
    }
  }

}
