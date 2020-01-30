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

/** Provides classes for running batches of crop simulations on the AgInfra+
  * cluster.
  *
  * == Overview ==
  * The [[https://aginfra.d4science.org AgInfra+ cluster]] is used to create
  * VREs (Virtual Research Environments)
  * that give researchers collaboration tools and access to storage and compute
  * resources for running algorithms. The core functionality is called the
  * [[https://wiki.gcube-system.org/gcube/DataMiner_Manager 'DataMiner']],
  * and it makes algorithms available as
  * [[https://www.opengeospatial.org/standards/wps Web Processing Services]].
  *
  * Classes in this package make use of DataMiner and WPS to process batches of
  * crop simulations on the AgInfra+ cluster. The main scattering of work and
  * gathering of results is handled by the [[nl.wur.aginfrasystem.Scheduler]].
  *
  * To tackle the asynchronous nature of the processing of batches of work on
  * the cluster, the aginfrasystem is build as a number of actors based on the
  * [[http://akka.io Akka actor framework]]:
  *
  *  - [[nl.wur.aginfrasystem.JobStarter]] starts jobs via WPS on the cluster.
  *  - [[nl.wur.aginfrasystem.JobMonitor]] tracks progress of running jobs.
  *  - [[nl.wur.aginfrasystem.LogProcessor]] scans the log output of a completed
  *    job.
  *  - [[nl.wur.aginfrasystem.SummaryProcessor]] handles post-processing of the
  *    results of a job.
  *
  * DataMiner considers the complete aginfrasystem as a single algorithm and
  * will pass user inputs to it as command line arguments. Therefore a few
  * objects are included that have a main method which translates user input
  * to suitable parameters for a call to the [[nl.wur.aginfrasystem.Scheduler]].
  *
  *  - [[nl.wur.aginfrasystem.RunFieldsInGeometrySimulations]] runs batches of
  *    crop simulations for fields within a certain geometry.
  *  - [[nl.wur.aginfrasystem.RunFieldsInProvinceSimulations]] runs batches of
  *    crop simulations for fields within a (Dutch) province.
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
package object aginfrasystem { }
