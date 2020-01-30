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
package nl.wur.wofostshell.parameters

import nl.wur.wiss.mathutils.{Interpolator, InterpolatorExtrapolationType}
import nl.wur.wiss.core.{ParXChange, ScientificUnit}
import org.apache.commons.math3.linear.{Array2DRowRealMatrix, RealMatrix}
import org.scalatest.{FlatSpec, Matchers}


class ParameterProviderSpec extends FlatSpec with Matchers {

  def fixture =
    new {
      val pp = new ParameterProvider

      // store an integer parameter value
      val pdFoo = ParameterDefinition("foo", "foo parameter", Array(ScientificUnit.NA))
      pp.put(pdFoo, 42)

      // store a double parameter value
      val pdBar = ParameterDefinition("bar", "bar parameter", Array(ScientificUnit.CELSIUS))
      pp.put(pdBar, 2.4)
    }

  "A ParameterProvider" should "store ParameterDefinitions" in {
    val f = fixture
    f.pp.get(f.pdFoo) should be (Some(42))
  }

  "A ParameterProvider" should "return a parameter by name" in {
    val f = fixture
    f.pp.get("foo") should be (Some(42))
  }

  "A ParameterProvider" should "return a parameter by name ignoring case" in {
    val f = fixture
    f.pp.get("FoO") should be (Some(42))
  }

  "A ParameterProvider" should "return a sorted list of all parameters" in {
    val f = fixture
    f.pp.availableParameters() should be (List(f.pdBar, f.pdFoo))
  }

  "A ParameterProvider" should "return a sorted list of all parameter names" in {
    val f = fixture
    f.pp.availableParameterNames() should be (List("bar", "foo"))
  }

  "A ParameterProvider" should "indicate correct contents" in {
    val f = fixture
    f.pp.contains(f.pdFoo) should be (true)
    f.pp.contains("foo") should be (true)
    f.pp.contains("foobar") should be (false)
    f.pp.contains("fOo", ignoreCase = true) should be (true)
    f.pp.contains("fOo", ignoreCase = false) should be (false)
  }

  "A ParameterProvider" should "remove parameter definitions correctly" in {
    val f = fixture
    f.pp.contains(f.pdFoo) should be (true)
    f.pp.remove(f.pdFoo)
    f.pp.contains(f.pdFoo) should be (false)
  }

  "A ParameterProvider" should "be able to return default value" in {
    val f = fixture
    f.pp.getOrElse(f.pdFoo, 0) should be (42)
    val pd = ParameterDefinition("foobar", "foobar parameter", Array(ScientificUnit.DATE))
    f.pp.getOrElse(pd, 0) should be (0)
  }

  "A ParameterProvider" should "transfer values to ParXChange" in {
    val f = fixture
    val p = new ParXChange()
    f.pp.transferTo(p)
    // note that ParXChange stores values as Java types
    p.contains("foo", classOf[java.lang.Integer]) should be (true)
    p.contains("bar", classOf[java.lang.Double]) should be (true)
  }

  "A ParameterProvider" should "be able to handle Interpolators" in {
    val f = fixture
    val p = new ParXChange()

    // create as an Interpolator
    val amaxtb: Interpolator = new Interpolator("AMAXTB", ScientificUnit.NODIM, ScientificUnit.KG_HA1HR1)
    amaxtb.setExtrapolationType(InterpolatorExtrapolationType.CONSTANTEXTRAPOLATION)
    amaxtb.add(0.0D, 70.0D)
    amaxtb.add(1.25D, 70.0D)
    amaxtb.add(1.5D, 63.0D)
    amaxtb.add(1.75D, 49.0D)
    amaxtb.add(2.0D, 21.0D)

    val pdAmaxtb = ParameterDefinition("amaxtb_interpolator", "amaxtb interpolator parameter", Array(ScientificUnit.NODIM, ScientificUnit.KG_HA1HR1))
    f.pp.put(pdAmaxtb, amaxtb)

    // create as a matrix
    val matrix = new Array2DRowRealMatrix(5, 2)
    matrix.setEntry(0, 0, 0.0D)
    matrix.setEntry(0, 1, 70.0D)
    matrix.setEntry(1, 0, 1.25D)
    matrix.setEntry(1, 1, 70.0D)
    matrix.setEntry(2, 0, 1.5D)
    matrix.setEntry(2, 1, 1.5D)
    matrix.setEntry(3, 0, 1.75D)
    matrix.setEntry(3, 1, 49.0D)
    matrix.setEntry(4, 0, 2.0D)
    matrix.setEntry(4, 1, 21.0D)
    val pdMatrix = ParameterDefinition("amaxtb_matrix", "amaxtb matrix interpolator parameter", Array(ScientificUnit.NODIM, ScientificUnit.KG_HA1HR1))
    f.pp.put(pdMatrix, matrix)

    // transfer to ParXChange
    f.pp.transferTo(p)

    // verify
    p.contains("amaxtb_interpolator", classOf[Interpolator]) should be (true)
    p.contains("amaxtb_matrix", classOf[Interpolator]) should be (true)
  }

}
