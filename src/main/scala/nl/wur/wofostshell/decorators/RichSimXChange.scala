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
package nl.wur.wofostshell.decorators

import nl.wur.wiss.core.SimXChange


final class RichSimXChange(val self: SimXChange) extends AnyVal {

  // TODO: add filter and transformation methods ...
  //  make it a monad (support identity and flatmap)
  //  SimXChange is essentially a collection of timeseries, with support methods.
  //  Need to pimp it with additional methods for further processing, without
  //  having to alter the original class.

  /*
      Should support changing a SimXChange into another one with a selected
      set of states, where some might be calculated from others, in order
      to retrieve a SimXChange with only main variables.

      [Hendrik] Bij deze een lijstje:

      Meteo:
      SIMMETEO.TM_MAX (maximum temperature in degrees Celsius)
      SIMMETEO.TM_MIN (minimum temperature in degrees Celsius)
      SIMMETEO.Q_CU (global radiation in kJ.m-2.d-1)
      SIMMETEO.VP_AV (vapour pressure in hPa)
      SIMMETEO.WS2_AV (wind speed at 2 metres in m.s-1)
      SIMMETEO.PR_CU (rainfall in mm.d-1)
      SIMMETEO.ET0 (evapotranspiration in mm.d-1)

      Crop:
      SIMWATBALCUSTOM.SM (soil moisture)
      SIMCROPPHENOLOGYTYPE1_CROPNO_1.DVS (development stage)
      SIMCROPTYPE1_CROP_NO_1.LAI (leaf area index)
      SIMCROPTYPE1_CROP_NO_1.WRT (weight of living roots in kg.ha-1)
      SIMCROPTYPE1_CROP_NO_1.WST (weight of living stems in kg.ha-1)
      SIMCROPTYPE1_CROP_NO_1.WLV (weight of living leaves in kg.ha-1)
      SIMCROPTYPE1_CROP_NO_1.WSO (weight of living storage organs in kg.ha-1)
      SIMCROPTYPE1_CROP_NO_1.WRT (weight of dead roots in kg.ha-1)
      SIMCROPTYPE1_CROP_NO_1.WST (weight of dead stems in kg.ha-1)
      SIMCROPTYPE1_CROP_NO_1.WLV (weight of dead leaves in kg.ha-1)
      SIMCROPTYPE1_CROP_NO_1.WSO (weight of dead storage organs in kg.ha-1)

      Bovenstaande 8 zou ik liever combineren door dead en living op tellen dus (ik heb er een ‘T’ voor gezet van total):
      TWRT (weight of roots in kg.ha-1)
      TWST (weight of stems in kg.ha-1)
      TWLV (weight of leaves in kg.ha-1)
      TWSO (weight of storage organs in kg.ha-1)

      Plus een extra: TAGP = TWST+TWLV+TWSO (total above ground production in kg.ha-1)

      En tenslotte:
      SIMCROPTYPE1_CROP_NO_1.RD (rooting depth in cm)
      SIMCROPTYPE1_CROP_NO_1.TRA (crop transpiration in mm.d-1)

      Zodra je de water-gelimiteerde ook meeneemt, komen er wellicht meer output bij? Die moeten we dan ook nog even nalopen.
      WWLOW

      Is dit duidelijk? Kunnen de eenheden er bij?
   */

}
