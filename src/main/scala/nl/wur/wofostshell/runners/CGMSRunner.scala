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

// TODO: Implement. See Kotlin version in WISS/wofostshell
// Given the main loop below, need to decide the relevant Actors to make
// good use of the current wofostsystem and possibly aginfrasystem to
// distribute the crop simulations on the D4Science cluster.

// TODO: Replace CGMSRunner with a wofostsystem configuration.

// TODO: Replace CGMS12JSONRunner with a wofostsystem configuration as well.
//    Makes sense to isolate the simulation verification code and add it
//    to e.g. the Simulator actor, or the Researcher, or to a new actor.
//    Running and verying crop simulations based on JSON input with values
//    to check can then be distributed across multiple actors.

/**
  * Runs a series of WOFOST simulations based on CGMS database content.
  *
  * The 'CGMS Loop' in short:
  *   Input: crop nr, start date, end date for simulation period
  *
  *   retrieve simulation units (smu's) for crop and grid cells
  *   process all keys (grid cells), for each grid cell:
  *     prepare crop and meteo data readers for the specific grid cell
  *     process all sowing years, for each year:
  *       get crop calendar info
  *       set start and end choices based on crop calendar
  *       prepare the crop and meteo data
  *       initialize the soil data
  *       get all unique soil typologic units (stu's) for the grid cell
  *       filter down selected stu's before running simulations
  *       for each selected stu:
  *         read soil data for stu
  *         create the WOFOST parameter object
  *         run WOFOST simulation
  *         collect simulation data for summary
  *   write summary of all simulations
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */

class CGMSRunner extends Runner {
  override def setUp(): Unit = ???

  override def run(): Unit = ???

  override def cleanUp(): Unit = ???
}
