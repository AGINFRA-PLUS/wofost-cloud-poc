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
package nl.wur

import nl.wur.wofostsystem.actors.{AgroDataCubeLibrarian, Reporter, Researcher, Simulator}


/** Provides classes for running parallel crop simulations on a system.
  *
  * == Overview ==
  * Based on the [[http://akka.io Akka actor framework]] a number of actors
  * are available that together can run a large set of crop simulations in
  * parallel on a single computer.
  *
  * At the core the [[http://wofost.wur.nl WOFOST]] model is used, which is
  * a simulation model for the quantitative analysis of the growth and
  * production of annual field crops. It is a mechanistic model that
  * explains crop growth on the basis of the underlying processes, such
  * as photosynthesis, respiration and how these processes are influenced by
  * environmental conditions. The model calculates rates and states of a large
  * number of variables based on daily timesteps. There are no spatial
  * dependencies, simulations can be run for any point on the Earth as long
  * as sufficient input data is available.
  *
  * This package at the moment provides specific support for the
  * [[http://agrodatacube.wur.nl AgroDataCube]] as major source of input data,
  * allowing the running of crop simulations for parcels in The Netherlands. Future
  * updates might add other data sources with data for other regions.
  *
  * Another option is to use input from a file that contains all the required
  * parameters, and can be in JSON or binary format. The WOFOST model uses a
  * class [[nl.alterra.wiss.core.ParXChange]] to hold all the parameters, which
  * allows binary serialisation and deserialisation. Given such a base input it
  * is possible to perform a parameter sweep, where all needed crop simulations
  * will also be run in parallel.
  *
  * The [[nl.wur.wofostsystem.AppProtocol]] describes the message protocols used
  * by the actors, and the Study Specifications possible for defining which crop
  * simulations to run. A study specification can be created programmatically or
  * loaded from a JSON file.
  *
  * In the package there are several objects with a main method as entry points
  * for data processing:
  *
  *  - [[nl.wur.wofostsystem.RunFileSimulation]] runs a crop simulation based
  *    on the parameters specified in a JSON or binary file.
  *  - [[nl.wur.wofostsystem.RunFieldSimulation]] runs a crop simulation for
  *    a specific field from the AgroDataCube, specified by its field ID.
  *  - [[nl.wur.wofostsystem.RunFieldsSimulations]] runs crop simulations for
  *    fields from the AgroDataCube, specified by their field IDs.
  *  - [[nl.wur.wofostsystem.RunFieldsInGeometrySimulations]] runs crop
  *    simulations for all the fields from the AgroDataCube that are within a
  *    specified region (geometry).
  *  - [[nl.wur.wofostsystem.RunStudySpecSimulations]] runs crop simulations
  *    based on a study specification in a give JSON file.
  *
  * These objects simply put together a study specification if needed, and then
  * forward to one of these executor objects that start the actors and control
  * the parallel running of the crop simulations:
  *
  *  - [[nl.wur.wofostsystem.AgroDataCubeStudyExecutor]] for running crop
  *    simulations based on input from AgroDataCube.
  *  - [[nl.wur.wofostsystem.FileBasedStudyExecutor]] for running crop
  *    simulations based on input from a parameter file.
  *
  * Finally, the actors that make up the system and run the crop simulations,
  * are the following:
  *
  *  - [[AgroDataCubeLibrarian]] connects to AgroDataCube
  *    via the API and retrieves data from it relevant for crop simulations.
  *  - [[Researcher]] starts WOFOST crop simulations and
  *    processes the model results.
  *  - [[Simulator]] wraps the WOFOST Java library into
  *    an actor. See the [[nl.wur.wofostshell wofostshell]] package.
  *  - [[Reporter]] creates a summary report from results
  *    of multiple crop simulations.
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
package object wofostsystem { }
