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

import java.io.{FileInputStream, IOException}

import nl.wur.wiss.core.ScientificUnit
import nl.wur.wofostshell.parameters.{ParameterDefinition, ParameterProvider}
import org.apache.commons.logging.LogFactory
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.yaml.snakeyaml.Yaml

import scala.collection.mutable.ArrayBuffer
import scala.collection.{JavaConverters, mutable}

// TODO: switch to using akka logging framework

/**
  * Reads standardized YAML crop parameter files and makes the parameters
  * available as a ParameterProvider. Each file can have the parameters for
  * several varieties of a crop. After loading the file one specific variety
  * needs to be selected before parameters can be retrieved.
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
class YamlCropReader extends ParameterProvider {

  // name of the crop variety for which parameters are currently loaded
  var selectedCropVariety = ""

  // store all crop varieties read from the Yaml file
  private var cropVarietyData: mutable.Map[String, Any] = _

  private val LOGGER = LogFactory.getLog(classOf[YamlCropReader])

  /**
    * Reads crop parameters from the given file. Note that no parameters
    * will be available until they are loaded for a specific crop variety!
    *
    * @param filePath file to load data from
    * @throws Exception when loading of the data fails
    */
  def read(filePath: String): Unit = {
    source = filePath
    description = "YAML Crop Parameter Data File"
    clear()

    LOGGER.info(s"Reading crop parameters file: $filePath")

    val yaml = new Yaml()
    try {
      // returns java.util.LinkedHashMap
      val data = yaml.load[java.util.Map[String, Any]](new FileInputStream(filePath))
      processData(data)
    } catch {
      case e: IOException => {
        val msg = s"Error opening file: $filePath (systemmessage=${e.getMessage}, exception=${e.getClass})"
        LOGGER.error(msg, e)
        throw new IOException(msg, e)
      }
      case e: Exception => {
        val msg = s"${e.getMessage} (file='$filePath')"
        LOGGER.error(msg, e)
        throw new IOException(msg, e)
      }
    }
  }

  def read(yamlData: String, sourceName: String): Unit = {
    source = sourceName
    description = "YAML Crop Parameter Data"
    clear()
    val yaml = new Yaml()
    val data = yaml.load[java.util.Map[String, Any]](yamlData)
    processData(data)
  }

  private def processData(data: java.util.Map[String, Any]): Unit = {
    val cropParams = data.get("CropParameters").asInstanceOf[java.util.Map[String, Any]]
    val cropVarieties = cropParams.get("Varieties").asInstanceOf[java.util.Map[String, Any]]
    cropVarietyData = JavaConverters.mapAsScalaMap[String, Any](cropVarieties)
  }

  def availableCropVarietyNames(): List[String] = {
    val names = ArrayBuffer[String]()
    cropVarietyData.keys.filter(name => !names.contains(name)).foreach(name => names += name)
    names.sorted.toList
  }

  def hasCropVariety(cropVarietyName: String): Boolean = cropVarietyData.keys.exists(_ equalsIgnoreCase cropVarietyName)

  def getCropParametersForVariety(cropVarietyName: String): Option[mutable.Map[String, Any]] = {
      cropVarietyData.find({ case (key, value) => key.equalsIgnoreCase(cropVarietyName) }) match {
        case Some(value) => {
          // going from Any to Java Map to Scala Map ...
          Some(JavaConverters.mapAsScalaMap(value._2.asInstanceOf[java.util.Map[String, Any]]))
        }
        case None => None
      }
  }

  def loadCropParametersForVariety(cropVarietyName: String): Unit = {
    // check if data is already loaded
    if (cropVarietyName.equalsIgnoreCase(selectedCropVariety)) {
      LOGGER.info(s"Parameters for requested crop variety $cropVarietyName are already loaded")
      return
    }

    // load new data
    clear()
    var errorCount = 0

    // check if crop parameters are available for the variety
    getCropParametersForVariety(cropVarietyName) match {
      case None => {
        LOGGER.error(s"No parameters available for requested crop variety $cropVarietyName")
        errorCount += 1
      }
      case Some(paramsData) => {
        selectedCropVariety = cropVarietyName
        // parse all entries
        paramsData foreach ( (paramData) => {
          val key = paramData._1 // String
          val values = paramData._2.asInstanceOf[java.util.ArrayList[Any]] // (values, descriptions, units)

          if (values.size() != 3) {
            LOGGER.error(s"Invalid data detected for crop variety: $cropVarietyName, expected [values, description, units]")
            errorCount += 1
          } else {
            // LOGGER.info("Parameter: " + JavaConverters.asScalaBuffer(values).mkString(", "))

            /* So far so good ...
              Parameter: [40.0, 0.0, 360.0, 1.0, 720.0, 1.0, 1000.0, 1.0, 2000.0, 1.0], multiplication factor for AMAX to account for an increasing CO2 concentration, [PPM, -]
              Parameter: 4.0, Lower threshold temperature for emergence, [C]
              Parameter: 30.0, maximum effective temperature for emergence, [C]
              Parameter: 110.0, temperature sum from sowing to emergence, [C.d]
             */

            // process the parameter name
            val varName = key.toUpperCase

            // process the units
            var units = Array[ScientificUnit](ScientificUnit.NA)
            try {
              val unitsBuff = JavaConverters.asScalaBuffer[String](values.get(2).asInstanceOf[java.util.List[String]])
              val unitsTxt = "[" + unitsBuff.transform(_.trim.toLowerCase).mkString(";") + "]"
              units = ScientificUnit.fromTxt(unitsTxt)
            } catch {
              case e: Exception => {
                LOGGER.error(s"Can not process units for variable: $varName, crop variety: $cropVarietyName, error: ${e.getMessage}")
                errorCount += 1
              }
            }

            // create a new parameter definition
            val pd = ParameterDefinition(varName, values.get(1).toString, units)

            // parse value
            values.get(0) match {
              case i: Int => put(pd, i)
              case d: Double => put(pd, d)
              case l: java.util.ArrayList[_] => {
                if (l.size() % 2 != 0) {
                  LOGGER.error(s"Tabular data has uneven number of items for variable with name: $varName, crop variety: $cropVarietyName")
                  errorCount += 1
                } else {
                  val matrix = new Array2DRowRealMatrix(l.size() / 2, 2)
                  var i: Int = 0
                  while (i < l.size()) {
                    matrix.setEntry(i / 2, 0, l.get(i).asInstanceOf[Double])
                    matrix.setEntry(i / 2, 1, l.get(i + 1).asInstanceOf[Double])
                    i += 2
                  }
                  put(pd, matrix)
                }
              }
              case _ => {
                LOGGER.error(s"Invalid data detected for crop variety: $cropVarietyName, value should be either Int, Double or List<Double>.")
                errorCount += 1
              }
            }

          }
        })
      }
    }

    // check for errors
    if (errorCount > 0) {
      val msg = s"Found $errorCount problem(s) when processing the crop variety data."
      LOGGER.error(msg)
      throw new Exception(msg)
    }
  }
}
