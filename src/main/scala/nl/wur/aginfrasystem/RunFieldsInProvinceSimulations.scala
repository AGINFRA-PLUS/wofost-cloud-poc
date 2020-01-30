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


object RunFieldsInProvinceSimulations {

  private val boundingBoxes = Map(
    "Drenthe" -> "POLYGON((204348 514313, 204348 580256, 269919 580256, 269919 514313, 204348 514313))",
    "Flevoland" -> "POLYGON((132850 473498, 132850 539705, 197647 539705, 197647 473498, 132850 473498))",
    "Friesland" -> "POLYGON((117000 530832, 117000 617500, 224893 617500, 224893 530832, 117000 530832))",
    "Gelderland" -> "POLYGON((127900 416114, 127900 503929, 254331 503929, 254331 416114, 127900 416114))",
    "Groningen" -> "POLYGON((206912 540354, 206912 621876, 278026 621876, 278026 540354, 206912 540354))",
    "Limburg" -> "POLYGON((167508 306839, 167508 421214, 213448 421214, 213448 306839, 167508 306839))",
    "Noord-Brabant" -> "POLYGON((72069 359069, 72069 426909, 200808 426909, 200808 359069, 72069 359069))",
    "Noord-Holland" -> "POLYGON((92957 464238, 92957 581000, 154329 581000, 154329 464238, 92957 464238))",
    "Overijssel" -> "POLYGON((181470 459721, 181470 541038, 269799 541038, 269799 459721, 181470 459721))",
    "Utrecht" -> "POLYGON((114243 438336, 114243 479600, 171507 479600, 171507 438336, 114243 438336))",
    "Zeeland" -> "POLYGON((10426 357829, 10426 422663, 77880 422663, 77880 357829, 10426 357829))",
    "Zuid-Holland" -> "POLYGON((43663 406692, 43663 483123, 138649 483123, 138649 406692, 43663 406692))"
  )

  // TODO: should not be hard coded here, request from wofostsystem somehow?
  //  or add a function to very if a certain cropcode can be processed?
  private val supportedCropCodes = Seq("233", "234", "236", "252", "253", "254", "255", "256", "257", "259")

  // e.g. "zeeland-2018-wheat" "zeeland" 2016 "233" 1000 10 120 -> 6513 parcels with wheat
  // e.g. "zeeland-2018-wheat" "zeeland" 2017 "233" 1000 10 120 -> 5393 parcels with wheat
  // e.g. "zeeland-2018-wheat" "zeeland" 2018 "233" 1000 10 120 -> 5221 parcels with wheat

  // crop_code: 233/234=winter wheat, 236=barley, 252/253/254/255=potato, 256/257=sugarbeet, 259=maize

  def main(args: Array[String]): Unit = {
    if (args.length != 7) {
      throw new IllegalArgumentException("usage: title dutch_province_name parcel_year parcel_crop_code batch_size max_batches batch_timeout_sec")
    }

    // get the arguments
    val title = args(0)
    val provinceName = args(1)
    val parcelYear = args(2).toInt
    val parcelCropCode = args(3)
    val batchSize = args(4).toInt
    val maxBatches = args(5).toInt
    val batchTimeoutSec = args(6).toInt

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
    val system = ActorSystem("run-fields-in-provinces-simulations")
    try {
      val log = Logging.getLogger(system, this)

      // header
      log.info("------------------------------------------------------------")
      log.info("|          AgInfra+ WOFOST Crop Model Simulations          |")
      log.info("|            Wageningen University and Research            |")
      log.info("------------------------------------------------------------")
      log.info(s"Job title        : $title")
      log.info(s"Province         : $provinceName")
      log.info(s"Parcel year      : $parcelYear")
      log.info(s"Parcel crop code : $parcelCropCode")
      log.info(s"Batch size       : $batchSize")
      log.info(s"Max batches      : $maxBatches")
      log.info(s"Batch timeout    : $batchTimeoutSec sec.")
      log.info("------------------------------------------------------------")

      val config = ConfigFactory.load()
      val token = config.getString("application.agrodatacube.token")

      // TODO: retrieve province information from AgroDataCube and use boundary for parcel selection
      //  implement when related issues with AgroDataCube have been fixed (provences, HTTP POST)
      val geometryWktRD = boundingBoxes.find(_._1.toLowerCase.contains(provinceName.toLowerCase)) match {
        case None =>
          log.error(s"No province found with name similar to $provinceName")
          return
        case Some((name, geom)) =>
          geom
      }

      // retrieve number of matching parcels in bounding box
      val fieldReader = new FieldReader()
      val nrFields = fieldReader.getFieldCountForLocation(geometryWktRD, "28992", token, log, Some(parcelYear), Some(parcelCropCode)) match {
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
        geometryWktRD, "28992",
        parcelYear, parcelCropCode,
        batchSize, nrBatches, batchTimeoutSec
      )

    } finally {
      system.terminate()
    }
  }

}
