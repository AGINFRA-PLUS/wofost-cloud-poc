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

import java.util.Locale

import nl.wur.wiss.core.{ParValue, ParXChange, ScientificUnit}
import nl.wur.wiss.mathutils.{Interpolator, InterpolatorExtrapolationType}
import org.apache.commons.math3.linear.RealMatrix

import scala.collection.mutable.ListBuffer


case class ParameterDefinition(name: String, description: String, units: Array[ScientificUnit])


class ParameterProvider {

  protected var source = "undefined"
  protected var description = "undefined"
  protected val data = collection.mutable.HashMap[ParameterDefinition, Any]()


  def clear(): Unit = data.clear()

  def put(key: ParameterDefinition, value: Any): Unit = data.put(key, value)

  def remove(key: ParameterDefinition): Unit = data.remove(key)

  def contains(key: ParameterDefinition): Boolean = data.contains(key)

  def get(key: ParameterDefinition): Option[Any] = data.get(key)

  def getOrElse(key: ParameterDefinition, default: Any): Any = data.getOrElse(key, default)


  def availableParameterNames(): List[String] = {
    val names = new ListBuffer[String]()
    data.keys.foreach { pd => names += pd.name }
    names.sorted.toList
  }


  def availableParameters(): List[ParameterDefinition] = {
    val result = new ListBuffer[ParameterDefinition]()
    data.keys.foreach { pd => result += pd }
    result.sortWith((pd1, pd2) => pd1.name < pd2.name).toList
  }


  def parameter(name: String, ignoreCase: Boolean = true): Option[ParameterDefinition] = {
    if (ignoreCase) {
      data.keys.find { pd => pd.name.equalsIgnoreCase(name) }
    } else {
      data.keys.find { pd => pd.name.equals(name) }
    }
  }


  def contains(name: String, ignoreCase: Boolean = true): Boolean = {
    if (ignoreCase) {
      data.keys.exists(pd => pd.name.equalsIgnoreCase(name))
    } else {
      data.keys.exists(pd => pd.name.equals(name))
    }
  }


  def get(name: String, ignoreCase: Boolean = true): Option[Any] = {
    parameter(name, ignoreCase) match {
      case Some(parameterDefinition) => data.get(parameterDefinition)
      case None => None
    }
  }


  def getOrElse(name: String, default: Any, ignoreCase: Boolean = true): Any = {
    get(name, ignoreCase) match {
      case Some(value) => value
      case None => default
    }
  }


  def transferTo(parXChange: ParXChange): Unit = {
    availableParameters().foreach { par =>
      val varName = par.name.toUpperCase(Locale.US)
      get(par) match {
        case Some(v:Double) => parXChange.set(new ParValue(v, par.units(0), varName))

        case Some(v:Int) => parXChange.set(new ParValue(v, par.units(0), varName))

        case Some(v:Interpolator) => parXChange.set(new ParValue(v, ScientificUnit.NA, varName))

        case Some(v:RealMatrix) if v.getColumnDimension == 2 => {
          val interpolator = new Interpolator(par.name.toUpperCase(Locale.US), par.units(0), par.units(1))
          interpolator.setExtrapolationType(InterpolatorExtrapolationType.CONSTANTEXTRAPOLATION)
          for (i <- 0 until v.getRowDimension) {
            interpolator.add(v.getEntry(i, 0), v.getEntry(i, 1))
          }
          parXChange.set(varName, classOf[Interpolator], true, interpolator, ScientificUnit.NA)
        }
      }
    }
  }

}


