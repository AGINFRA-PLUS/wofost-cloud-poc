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
package nl.wur.aginfrasystem

import java.time.LocalDate

import akka.actor.ActorSystem
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import nl.wur.agrodatacube.client.FieldReader


object RunFieldsInGeometrySimulations {

  // TODO: should not be hard coded here, request from wofostsystem somehow?
  //  or add a function to very if a certain cropcode can be processed?
  private val supportedCropCodes = Seq("233", "234", "236", "252", "253", "254", "255", "256", "257", "259")

  // geometry: "POLYGON((8927 314714, 8927 464932, 138515 464932, 138515 314714, 8927 314714))", // quarter of NL (epsg 28992)

  def main(args: Array[String]): Unit = {
    if (args.length != 8) {
      throw new IllegalArgumentException("usage: title geometry_wkt geometry_epsg parcel_year parcel_crop_code batch_size max_batches batch_timeout_sec")
    }

    // get the arguments
    val title = args(0)
    val geometryWkt = args(1)
    val geometryEpsg = args(2)
    val parcelYear = args(3).toInt
    val parcelCropCode = args(4)
    val batchSize = args(5).toInt
    val maxBatches = args(6).toInt
    val batchTimeoutSec = args(7).toInt

    // do some argument validation
    if ((batchSize < 10) || (batchSize > 5000)) {
      throw new IllegalArgumentException("Please use a batch size between 10 and 5000 for optimal use of the system.")
    }
    if (parcelYear < 2000) {
      throw new IllegalArgumentException("Please specify a more recent year, data before 2000 is not available.")
    }
    if (parcelYear > LocalDate.now().getYear) {
      throw new IllegalArgumentException("Please specify a year that is not in the future.")
    }
    if (!supportedCropCodes.contains(parcelCropCode)) {
      throw new IllegalArgumentException(s"Please select on of the supported crop codes: ${supportedCropCodes.mkString(",")}")
    }

    // some init is needed
    val system = ActorSystem("run-fields-in-geometry-simulations")
    try {
      val log = Logging.getLogger(system, this)

      // header
      log.info("------------------------------------------------------------")
      log.info("|          AgInfra+ WOFOST Crop Model Simulations          |")
      log.info("|            Wageningen University and Research            |")
      log.info("------------------------------------------------------------")
      log.info(s"Job title        : $title")
      log.info(s"Parcel year      : $parcelYear")
      log.info(s"Parcel crop code : $parcelCropCode")
      log.info(s"Batch size       : $batchSize")
      log.info(s"Max batches      : $maxBatches")
      log.info(s"Batch timeout    : $batchTimeoutSec sec.")
      log.info("------------------------------------------------------------")

      val config = ConfigFactory.load()
      val token = config.getString("application.agrodatacube.token")

      // retrieve number of matching parcels in bounding box
      val fieldReader = new FieldReader()
      val nrFields = fieldReader.getFieldCountForLocation(geometryWkt, geometryEpsg, token, log, Some(parcelYear), Some(parcelCropCode)) match {
        case Left(e) =>
          log.error(s"Could not retrieve number of matching fields, error: ${e.getMessage}")
          return
        case Right(fieldCounts) =>
          log.info(s"Number of matching fields to process: ${fieldCounts.head.count}")
          log.info("------------------------------------------------------------")
          fieldCounts.head.count
      }

      // calculate number of batches to process
      val nrBatches = if (nrFields <= (batchSize * maxBatches)) {
        Math.ceil(nrFields / batchSize).toInt + 1
      } else {
        maxBatches
      }

      // start the scheduler
      Scheduler.scheduleJobs(
        title,
        geometryWkt, geometryEpsg,
        parcelYear, parcelCropCode,
        batchSize, nrBatches,
        batchTimeoutSec
      )
    } finally {
      system.terminate()
    }
  }

}
