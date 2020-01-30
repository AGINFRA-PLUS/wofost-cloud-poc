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

import java.io.InputStream

import nl.wur.agrodatacube.client.{FieldSoil, SoilParams}
import nl.wur.agrodatacube.processors.waterretention.WaterRetentionCalculatorError._

/*
  Water retention curve
  A water retention curve is the relationship between the water content, θ,
  and the soil water potential, Ψ. This curve is characteristic for different
  types of soils, and is also called the soil moisture characteristic. It is
  used to predict the soil water storage, water supply to the plants (field
  capacity) and soil aggregate stability. Due to the hysteretic effect of
  water filling and draining the pores, different wetting and drying curves
  may be distinguished.
  At any given potential, peaty soils will usually display much higher
  moisture contents than clayey soils, which would be expected to hold
  more water than sandy soils. The water holding capacity of any soil is
  due to the porosity and the nature of the bonding in the soil.
 */


object StaringWaterRetentionCalculator {
  def load(fileName: String): Either[List[WaterRetentionCalculatorError], StaringWaterRetentionCalculator] = {
    new StaringWaterRetentionCalculator().loadLookUpTable(fileName)
  }
}


class StaringWaterRetentionCalculator private {

  var parameters: Option[Map[String, Parameters]] = None

  def loadLookUpTable(fileName: String): Either[List[WaterRetentionCalculatorError], StaringWaterRetentionCalculator] = {
      readParameters(fileName) match {
        case Left(list) =>
          parameters = None
          Left(list)
        case Right(map) =>
          parameters = Some(map)
          Right(this)
      }
  }

  def lookupStaringCode(code: String): Option[Parameters] = {
    if ((parameters.isDefined) && (parameters.get.contains(code))) {
      Some(parameters.get(code))
    } else {
      None
    }
  }

  /**
    *
    * @param soilInfo Complete definition of all the soils within a parcel and
    *                 all the layers for each of the soils.
    */
  def calculateForSoils(soilInfo: Map[FieldSoil, List[SoilParams]]): Unit = ???


  /*
    Lookup table is based on "cubicle spline" approach (Wesseling) used in Bofek 2012, Alterra report 2387.
    This is a better approximation than the VanGenuchten formulas.

    # Bij drukhoogte h in cm:
    # Saturated hydraulic conductivity (KSAT)                 : K (h=-1)
    # Volumetric moisture content at saturation (VMC-SAT)     : θ (h=-1)
    # Volumetric moisture content at field capacity (VMC-FC)  : θ (h=-100)
    # Volumetric moisture content at wilting point (VMC-WP)   : θ (h=-1000) ondergrond, θ (h=-160000) bovengrond

    # Bovengronden (Drukhoogte h in cm)
    # Textuur,K (h=-1),θ (h=-1),θ (h=-100),θ (h=-1000),θ (h=-160000), omschrijving,Staringreeks_versie
     "B1", 33.30,0.371,0.201,0.074,0.030,"Leemarm; zeer fijn tot matig fijn zand",1987

      1) Need to average KSAT and VMCs values for all soil layers, since WOFOST is a single soil layer model
      2) There can multiple soil types in a parcel! What to do?
            weighted average based on area %
   */

  /**
    *
    * @param staringCode code of the Staring bouwsteen
    * @param staringYear year of Staring reeks the Staring bouwsteen appears in
    * @param textureName descriptive name of soil texture
    * @param ksat Saturated hydraulic conductivity: K (h=-1 cm)
    * @param vmcSat Volumetric moisture content at saturation: θ (h=-1 cm)
    * @param vmcFc Volumetric moisture content at field capacity: θ (h=-100 cm)
    * @param vmcWp Volumetric moisture content at wilting point: θ (h=-1000 cm) ondergrond, θ (h=-160000 cm) bovengrond
    */
  case class Parameters(staringCode: String, staringYear: Int, textureName: String,
                        ksat: Double, vmcSat: Double, vmcFc: Double, vmcWp: Double)

  /**
    * Read the parameter file to create a look-up table.
    *
    * @param fileName
    * @return
    */
  def readParameters(fileName: String): Either[List[WaterRetentionCalculatorError], Map[String, Parameters]] = {
    val stream: InputStream = getClass.getResourceAsStream(fileName)
    val bufferedSource = io.Source.fromInputStream(stream)

    def isCommentLine(line: String): Boolean = line.isEmpty || line.startsWith("#")

    // create parameter instance and add to list
    // e.g.: "B1", 33.30,0.371,0.201,0.074,0.030,"Leemarm; zeer fijn tot matig fijn zand",1987
    def extractParameters(line: String): Either[WaterRetentionCalculatorError, Parameters] = {
      val cols = line.split(",").map(_.replace("\"", "").trim)
      if (cols.length != 8) {
        Left(InvalidNumberOfColumnsInInputFile(line))
      } else {
        cols(0).trim.toUpperCase match {
          case key: String if key.startsWith("O") || key.startsWith("B") =>
            try {
              Right(Parameters(key, cols(7).toInt, cols(6), cols(1).toDouble, cols(2).toDouble,
                cols(3).toDouble, if (key.contains("O")) cols(4).toDouble else cols(5).toDouble))
            } catch {
              case e: Exception => Left(NumberConversionError(line, e))
            }
          case code => Left(InvalidStaringreeksCodeError(line, code))
        }
      }
    }

    // TODO: Use Cats Validated ADT to clean this up
    val errors = scala.collection.mutable.ListBuffer.empty[WaterRetentionCalculatorError]
    val data = scala.collection.mutable.Map.empty[String, Parameters]

    bufferedSource.getLines().foreach { line =>
      if (!isCommentLine(line)) {
        extractParameters(line) match {
          case Left(e) => errors += e
          case Right(p) => data += (p.staringCode -> p)
        }
      }
    }
    bufferedSource.close

    if (errors.nonEmpty) Left(errors.toList) else Right(data.toMap)
  }

}
