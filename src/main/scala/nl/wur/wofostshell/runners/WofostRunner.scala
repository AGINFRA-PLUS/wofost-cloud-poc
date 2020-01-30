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
package nl.wur.wofostshell.runners

import java.util

import nl.wur.wiss.core._
import nl.wur.wiss.meteoutils.MeteoReader
import nl.wur.wissmodels.wofost.WofostModel
import nl.wur.wissmodels.wofost.simobjects.SimMeteo
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator


// TODO: see if WofostRunner can be replaced by using a minimal wofostsystem.

/**
  * Runs the WISS WOFOST model based on the specified WofostRunParameters.
  *
  * Note(s):
  *      crop parameter files can be downloaded from:
  *      https://github.com/ajwdewit/WOFOST_crop_parameters
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
class WofostRunner(val parameters: WofostRunParameters) extends Runner {

  private val wofostClassName = classOf[WofostModel].getSimpleName
  var model: WofostModel = _
  var driver: TimeDriver = _

  private def initLogging(configLocation: String, rootLevel: Level): Unit = {
    Configurator.initialize("logger", configLocation)
    Configurator.setRootLevel(rootLevel)
  }

  override def setUp(): Unit = {
    // set logging configuration
    initLogging(parameters.logConfigLocation, parameters.logLevel)

    val p = new ParXChange()

    val controllers = new util.ArrayList[WofostModel.Controller]()

    // first add environmental controller to simulation based on selected type
    parameters.simulationType match {
      case SimulationType.OPTIMAL_PRODUCTION => controllers add WofostModel.Controller.ENVIRONMENT_WB_CUSTOM
      case SimulationType.WATER_LIMITED_FREE_DRAINAGE => controllers add WofostModel.Controller.ENVIRONMENT_WB_FREEDRAIN
      case _ => controllers add WofostModel.Controller.ENVIRONMENT_WB_CUSTOM
    }

    // add other controllers for the simulation
    controllers add WofostModel.Controller.CROP_ROTATION_1
    controllers add WofostModel.Controller.CROP_HARVESTER_1

    p.set(new ParValue(controllers, ScientificUnit.NA, WofostModel.CONTROLLERS))

    // simulation parameters
    p.set(new ParValue(parameters.startDate, ScientificUnit.NA, TimeDriver.STARTDATE))
    p.set(new ParValue(parameters.endDate, ScientificUnit.NA, TimeDriver.ENDDATE))

    // cereal type crop with fixed partitioning
    p.set(new ParValue(1, ScientificUnit.NA, WofostModel.CROPTYPE))

    // SimMeteo inputs (important ones first)
    p.set(WofostModel.METEO, classOf[MeteoReader], true, parameters.meteoReader, ScientificUnit.NA)
    p.set(new ParValue(0.29, ScientificUnit.NODIM, WofostModel.ANGSTA))
    p.set(new ParValue(0.42, ScientificUnit.NODIM, WofostModel.ANGSTB))

    // info on publisher of E0, ES0 and ET0 data. Can be 'AUTOMATIC', 'SimMeteo', 'SimPenman', 'SimPenmanMonteithFAO'
    p.set(new ParValue(classOf[SimMeteo].getSimpleName, ScientificUnit.NA, WofostModel.PUBLISHER_E0))
    p.set(new ParValue(classOf[SimMeteo].getSimpleName, ScientificUnit.NA, WofostModel.PUBLISHER_ES0))
    p.set(new ParValue(classOf[SimMeteo].getSimpleName, ScientificUnit.NA, WofostModel.PUBLISHER_ET0))

    // CO2 increase scenarios
    p.set(new ParValue(360.0, ScientificUnit.PPM, WofostModel.CO2REFLEVEL))
    p.set(new ParValue(2016, ScientificUnit.YEAR, WofostModel.CO2REFYEAR))
    // switched off yearly increase, e.g. for doubling in 100 years set to 3.6
    p.set(new ParValue(0.0, ScientificUnit.PPM_Y, WofostModel.CO2YEARINC))

    // DROUGHT_INDEX (only needed for WB_CUSTOM)
    p.set(new ParValue(1.0, ScientificUnit.NODIM, WofostModel.DROUGHT_INDEX))

    // transfer all crop and soil parameters
    parameters.cropParameters.transferTo(p)
    parameters.soilParameters.transferTo(p)

    // start choice
    val istcho = parameters.startChoice.id
    p.set(new ParValue(istcho, ScientificUnit.NA, WofostModel.ISTCHO))
    istcho match {
      case 0 => {
        // start at crop emergence
        p.set(new ParValue(parameters.IDEM, ScientificUnit.DATE, WofostModel.IDEM))
      }
      case 1 => {
        // start at crop sowing
        p.set(new ParValue(parameters.IDSOW, ScientificUnit.DATE, WofostModel.IDSOW))
      }
      case 2 => {
        // variable sowing
        p.set(new ParValue(parameters.IDESOW, ScientificUnit.DATE, WofostModel.IDESOW))
        p.set(new ParValue(parameters.IDLSOW, ScientificUnit.DATE, WofostModel.IDLSOW))
      }
    }

    // end choice
    val iencho = parameters.endChoice.id
    p.set(new ParValue(iencho, ScientificUnit.NA, WofostModel.IENCHO))
    iencho match {
      case 2 => p.set(new ParValue(parameters.IDURMX, ScientificUnit.DAYS, WofostModel.IDURMX))
      case 3 => p.set(new ParValue(parameters.IDURF, ScientificUnit.DAYS, WofostModel.IDURF))
    }

    val simXChange = new SimXChange(parameters.runId)
    model = new WofostModel(p, simXChange)
    model.initLogging(parameters.logConfigLocation, parameters.logLevel)
    driver = new TimeDriver(model)
  }

  override def run(): Unit = {
    driver.run()
  }

  override def cleanUp(): Unit = { }
}
