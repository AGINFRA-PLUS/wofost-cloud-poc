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
package nl.wur.agrodatacube

/** Simple client for retrieving data from AgroDataCube.
  *
  * == Overview ==
  * [[http://agrodatacube.wur.nl AgroDataCube]] provides a large set of data
  * relevant for e.g. running crop simulations. Classes in this package realise
  * a basic client for retrieving data from the AgroDataCube by using its REST
  * API.
  *
  * The AgroDataCube returns data in GeoJSON format, which is used to populate
  * case classes based on resource type. For example fields, meteo, and soil.
  *
  * The [[nl.wur.agrodatacube.client.AgroDataCubeConnector]] handles the
  * http connection for GET and POST requests, passing the AgroDataCube token
  * when needed. Other classes use the object to handle the requests for
  * specific resource types.
  *
  *  - [[nl.wur.agrodatacube.client.FieldReader]] to retrieve data about
  *    crop fields (parcels).
  *  - [[nl.wur.agrodatacube.client.MeteoReader]] for retrieving data about
  *    weather stations and weather observations.
  *  - [[nl.wur.agrodatacube.client.SoilReader]] to retrieve data about soil
  *    types and physical soil parameters.
  *  - [[nl.wur.agrodatacube.client.RegionReader]] for retrieving information
  *    about administrative spatial regions.
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
package object client { }
