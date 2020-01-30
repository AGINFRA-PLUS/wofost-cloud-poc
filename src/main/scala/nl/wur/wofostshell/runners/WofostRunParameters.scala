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

import java.time.LocalDate

import nl.wur.wiss.meteoutils.MeteoReader
import nl.wur.wofostshell.parameters.ParameterProvider
import nl.wur.wofostshell.runners.EndChoice.EndChoice
import nl.wur.wofostshell.runners.SimulationType.SimulationType
import nl.wur.wofostshell.runners.StartChoice.StartChoice
import org.apache.logging.log4j.Level


/*
    IENCHO=0:
    vrije simulatie, maar gewassimulatie stopt bij DVS=DVSMaturity, ook als dat lang duurt. Er komt een error
    als ie DVSMaturity niet haalt op de overall einddatum van de simulatie.
    Melding "Crop did not reach DVSMATURITY".

    IENCHO=1:
    vrije simulatie, maar DVS=DVSMaturity is niet van belang, dus blijft gewoon doorgaan totdat (vanzelfsprekend)
    de overall einddatum van de simulatie bereikt is. Er komt een error als ie nog geen yield formation gehad
    heeft (dus als WSO=0 aan het eind).
    Melding "Crop did not reach yield formation".

    IENCHO=2:
    simulatie met maximale duur, als DVS=DVSMaturity wordt gehaald VOOR de maximale duur stopt de simulatie gewoon
    zonder error, maar als de maximale duur wel bereikt wordt en DVS is ongelijk DVSMaturity dan volgt error.
    Melding "Crop did not reach DVSMATURITY".
    // max duration, required for IENCHO=2
    public static final String IDURMX = "IDURMX";

    IENCHO=3:
    simulatie met geforceerde duur, gewassimulatie draait altijd tot geforceerde duur, criterium DVS=DVSMaturity
    is niet van belang, error als ie geen yield formation gehad heeft (dus als WSO=0 aan het eind).
    Melding "Crop did not reach yield formation".
    // forced duration, required for IENCHO=3
    public static final String IDURF  = "IDURF";
*/


object SimulationType extends Enumeration {
  type SimulationType = Value
  val OPTIMAL_PRODUCTION, WATER_LIMITED_FREE_DRAINAGE = Value
}


object StartChoice extends Enumeration {
  type StartChoice = Value
  val FIXED_EMERGENCE_DAY, FIXED_SOWING_DAY, VARIABLE_SOWING_DAY = Value
}


object EndChoice extends Enumeration {
  type EndChoice = Value
  val STOP_AT_DVSMATURITY, STOP_AT_END_DATE, STOP_AT_MAX_DURATION_OR_DVSMATURITY, STOP_AT_FORCED_DURATION = Value
}


case class WofostRunParameters(
  runId: String,
  startDate: LocalDate, endDate: LocalDate,
  simulationType: SimulationType,
  cropNr: Integer, cropName: String,
  cropParameters: ParameterProvider, soilParameters: ParameterProvider,
  gridCellNr: Long, soilTypologicUnitNr: Long,
  meteoReader: MeteoReader,
  logConfigLocation: String, logLevel: Level,
  startChoice: StartChoice, IDEM: LocalDate, IDSOW: LocalDate, IDESOW: LocalDate, IDLSOW: LocalDate,
  endChoice: EndChoice, IDURMX: Int, IDURF: Int
)
