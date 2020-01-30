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

import java.sql.{Connection, ResultSet, SQLException}
import java.time.LocalDate
import java.util.Locale

import nl.wur.wiss.core.ScientificUnit
import nl.wur.wofostshell.parameters.{ParameterDefinition, ParameterProvider}
import org.apache.commons.logging.LogFactory
import org.apache.commons.math3.linear.Array2DRowRealMatrix

import scala.collection.mutable

// TODO: switch to using akka logging framework

/**
  * A reader that retrieves crop parameter data from a CGMS12 database,
  * using a JDBC connection.
  *
  * The reader highly depends on the specifics of how the data is stored in
  * the database. Changes to that will require updating of the reader.
  *
  * @param dbUrl JDBC url for database to connect to
  * @param dbUser user name for database login
  * @param dbPassword password for database login
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
class CGMS12CropReader(dbUrl: String,
                       dbUser: Option[String] = None,
                       dbPassword: Option[String] = None)
  extends ParameterProvider {

  // some internal data classes to cache data
  case class GridLocationInfo(gridCellNr: Long,
                              longitude: Double, latitude: Double,
                              altitude: Double)

  case class CropCalendarEntry(year: Int, cropNr: Int,
                               varietyNr: Int, cropVarietyName: String,
                               startType: String, startDate: LocalDate,
                               endType: String, endDate: LocalDate,
                               maxDuration: Int)

  case class CropParameterEntry(parameterCode: String,
                                xOrdering: Int,
                                var xValue: BigDecimal,
                                var yValue: Option[BigDecimal],
                                paramType: String, description: String,
                                units: String)

  // fields
  private var cropNr: Option[BigInt] = None
  def getCropNr: Option[BigInt] = cropNr

  private var cropVariety: Option[String] = None
  def getCropVariety: Option[String] = cropVariety

  private var gridCellNr: Option[BigInt] = None
  def getGridCellNr: Option[BigInt] = gridCellNr

  private var gridLocation: Option[GridLocationInfo] = None
  def getGridLocation: Option[GridLocationInfo] = gridLocation

  private var gridLocationName: Option[String] = None
  def getGridLocationName: Option[String] = gridLocationName

  // maps for caching data
  private val cropCalendarEntries = new mutable.HashMap[Int, CropCalendarEntry]()
  def getCropCalendarEntries: Map[Int, CropCalendarEntry] = cropCalendarEntries.toMap

  private val cropParameterEntries = new mutable.HashMap[(String, Int), CropParameterEntry]()
  def getCropParameterEntries: Map[(String, Int), CropParameterEntry] = cropParameterEntries.toMap

  // database connector
  private val dBConnector = new JDBCConnector(dbUrl, dbUser, dbPassword)

  private val logger = LogFactory.getLog(classOf[CGMS12CropReader])

  /**
    * Initialises the reader for the specified grid cell and crop number.
    * Needs to be called after construction and before further use of the
    * reader.
    */
  @throws[SQLException]
  def init(cropNr: Int, gridCellNr: Long): CGMS12CropReader = {
    // clear state
    this.cropNr = None
    this.cropVariety = None
    this.gridCellNr = None
    this.description = "Undefined"

    clear()
    cropCalendarEntries.clear()
    cropParameterEntries.clear()

    // initialise some data
    val conn = dBConnector.connect()
    try {
      this.source = conn.getMetaData.getURL
      if (!fetchLocation(conn, gridCellNr) || !fetchCropCalendarEntries(conn, gridCellNr, cropNr)) {
        val msg = s"Could not initialize crop data reader for grid: $gridCellNr"
        logger.error(msg)
        throw new RuntimeException(msg)
      } else {
        this.gridCellNr = Some(gridCellNr)
        this.cropNr = Some(cropNr)
      }
    } finally {
      if (conn != null) conn.close()
    }
    this
  }

  /**
    * Reads the location information for the specified grid cell from the
    * database.
    */
  @throws[SQLException]
  private def fetchLocation(conn: Connection, gridCellNr: BigInt): Boolean = {
    var failed = false
    val stmt = conn.createStatement()
    try {
      // first query to get coordinates of grid cell
      var resultSet1: ResultSet = null
      try {
        val sql1 =
          """SELECT GRID_NO, LATITUDE, LONGITUDE, ALTITUDE
            | FROM GRID WHERE GRID_NO = %d"""
            .stripMargin.replaceAll("\n", " ").formatLocal(Locale.US, gridCellNr)
        logger.debug(s"Processing query: $sql1")

        resultSet1 = stmt.executeQuery(sql1)
        if (!resultSet1.isBeforeFirst) {
          gridLocation = None
          logger.error(s"Failed deriving location information from 'grid' table for grid: $gridCellNr")
          failed = true
        } else {
          resultSet1.next()
          gridLocation = Some(GridLocationInfo(gridCellNr.longValue(),
            resultSet1.getBigDecimal(3).doubleValue(),
            resultSet1.getBigDecimal(2).doubleValue(),
            resultSet1.getBigDecimal(4).doubleValue()))
        }
      } finally {
        if (resultSet1 != null) resultSet1.close()
      }

      // additional queries to find name of region (optional, only log warnings on errors)
      var regMapId: Option[Long] = None
      gridLocationName = Some("Unknown")

      var resultSet2: ResultSet = null
      try {
        val sql2 =
          """SELECT REG_MAP_ID
            | FROM (SELECT REG_MAP_ID, SUM(AREA)
            | FROM LINK_EMU_REGION
            | WHERE GRID_NO = %d GROUP BY REG_MAP_ID)
            | ORDER BY "SUM(AREA)" DESC"""
            .stripMargin.replaceAll("\n", " ").formatLocal(Locale.US, gridCellNr)
        logger.debug(s"Processing query: $sql2")

        resultSet2 = stmt.executeQuery(sql2)
        if (!resultSet2.isBeforeFirst) {
          logger.warn(s"Failed deriving grid region ID from 'link_emu_region' table for grid: $gridCellNr")
        } else {
          resultSet2.next()
          regMapId = Some(resultSet2.getBigDecimal(1).longValue())
        }
      } catch {
        case e: Exception => logger.warn("Could not retrieve name of region from the database, error: " + e.getMessage)
      } finally {
        if (resultSet2 != null) resultSet2.close()
      }

      if (regMapId.isDefined) {
        var resultSet3: ResultSet = null
        try {
          val sql3 =
            """
              |SELECT REG_NAME FROM REGION WHERE REG_MAP_ID = %d
            """.stripMargin.replaceAll("\n", " ").formatLocal(Locale.US, regMapId.get)
          logger.debug(s"Processing query: $sql3")

          resultSet3 = stmt.executeQuery(sql3)
          if (!resultSet3.isBeforeFirst) {
            logger.warn(s"Failed deriving grid region name from 'region' table for reg_map_id: ${regMapId.get}")
          } else {
            resultSet3.next()
            gridLocationName = Some(resultSet3.getString(1))
          }
        } catch {
          case e: Exception => logger.warn("Could not retrieve name of region from the database, error: " + e.getMessage)
        } finally {
          if (resultSet3 != null) resultSet3.close()
        }
      }
    } finally {
      if (stmt != null) stmt.close()
    }

    !failed
  }

  /**
    * Reads the crop calendar entries from the database for the specified
    * grid cell and crop number.
    */
  @throws[SQLException]
  private def fetchCropCalendarEntries(conn: Connection, gridCellNr: Long, cropNr: Int): Boolean = {
    val stmt = conn.createStatement()
    try {
      var resultSet: ResultSet = null
      try {
        val sql =
          """SELECT a.YEAR, a.CROP_NO, a.VARIETY_NO, b.CROP_NAME, a.START_TYPE, a.START_DATE,
            | a.END_TYPE, a.END_DATE, a.MAX_DURATION FROM CROP_CALENDAR a
            | JOIN CROP b ON a.crop_no = b.crop_no WHERE GRID_NO = %d AND a.CROP_NO = %d
          """.stripMargin.replaceAll("\n", "").formatLocal(Locale.US, gridCellNr, cropNr)

        logger.debug(s"Processing query: $sql")

        resultSet = stmt.executeQuery(sql)
        if (!resultSet.isBeforeFirst) {
          gridLocation = None
          logger.error(s"Failed deriving available years and varieties from 'crop_calendar' table for grid: $gridCellNr")
          false
        } else {
          var count = 0
          while (resultSet.next()) {
            val year = resultSet.getBigDecimal(1).intValue()
            val crop = resultSet.getBigDecimal(2).intValue()
            val variety = resultSet.getBigDecimal(3).intValue()
            val name = resultSet.getString(4).toLowerCase.replace(' ', '_')
            val startType = resultSet.getString(5).toLowerCase
            val startDate = resultSet.getDate(6).toLocalDate
            val endType = resultSet.getString(7).toLowerCase
            val endDate = resultSet.getDate(8).toLocalDate
            val maxDuration = resultSet.getBigDecimal(9).intValue()

            val entry = CropCalendarEntry(year, crop, variety, s"${name}_$variety",
              startType, startDate, endType, endDate, maxDuration)

            cropCalendarEntries.put(year, entry)
            count += 1
          }
          logger.debug(s"$count records selected from crop calendar for grid: $gridCellNr and crop: $cropNr")
          true
        }
      } finally {
        if (resultSet != null) resultSet.close()
      }
    } finally {
      if (stmt != null) stmt.close()
    }
  }

  /**
    * Reads all crop parameter entries for the specified crop variety from
    * the database. The crop variety number can be retrieved from the crop
    * calendar data. The database has a table for the generic parameters of
    * crops, and a table for the crop variety specific parameter values.
    * The later overwrite the generic crop parameter values.
    */
  @throws[SQLException]
  private def fetchCropParameterEntries(conn: Connection, varietyNr: Int): Boolean = {
    val cropSql =
      """SELECT a.PARAMETER_CODE, a.X_ORDERING, a.PARAMETER_XVALUE, a.PARAMETER_YVALUE,
        | b.TYPE, b.DESCRIPTION, b.UNITS
        | FROM CROP_PARAMETER_VALUE_WISS a
        | JOIN PARAMETER_DESCRIPTION_WISS b
        | ON a.parameter_code = b.parameter_code
        | WHERE a.CROP_NO = %d
      """.stripMargin.replaceAll("\n", "").formatLocal(Locale.US, cropNr)

    val varietySql =
      """SELECT
        | PARAMETER_CODE, X_ORDERING, PARAMETER_XVALUE, PARAMETER_YVALUE
        | FROM VARIETY_PARAMETER_VALUE_WISS
        | WHERE CROP_NO = %d AND VARIETY_NO = %d
      """.stripMargin.replaceAll("\n", "").formatLocal(Locale.US, cropNr, varietyNr)

    // start a database statement
    val stmt = conn.createStatement()
    try {
      // load base crop parameter data
      var cropResultSet: ResultSet = null
      try {
        logger.debug(s"Processing query: $cropSql")
        cropResultSet = stmt.executeQuery(cropSql)
        if (!cropResultSet.isBeforeFirst) {
          logger.error(s"Failed deriving crop parameters for crop: $cropNr")
          false
        } else {
          var count = 0
          while (cropResultSet.next()) {
            val code = cropResultSet.getString(1)
            val ord = cropResultSet.getBigDecimal(2).intValue()
            val entry = CropParameterEntry(
              code, ord,
              cropResultSet.getBigDecimal(3),
              Some(cropResultSet.getBigDecimal(4)),
              cropResultSet.getString(5),
              cropResultSet.getString(6),
              cropResultSet.getString(7)
            )
            cropParameterEntries.put((code, ord), entry)
            count += 1
          }
          logger.debug(s"$count crop parameter records selected for crop: $cropNr")
        }
      } finally {
        if (cropResultSet != null) cropResultSet.close()
      }

      // overwrite with crop variety specific parameters, if any
      var varietyResultSet: ResultSet = null
      try {
        logger.debug(s"Processing query: $varietySql")
        varietyResultSet = stmt.executeQuery(varietySql)
        if (!varietyResultSet.isBeforeFirst) {
          var count = 0
          while (varietyResultSet.next()) {
            val code = varietyResultSet.getString(1)
            val ord = varietyResultSet.getInt(2)
            val xValue = varietyResultSet.getBigDecimal(3)
            val yValue = varietyResultSet.getBigDecimal(4)

            val entry = cropParameterEntries.get((code, ord))
            if (entry.isDefined) {
              entry.get.xValue = xValue
              if (entry.get.yValue.isDefined) {
                entry.get.yValue = Some(yValue)
              }
              count += 1
            }
          }
          logger.debug(s"$count crop variety parameter overwrite records selected for crop: $cropNr variety: $varietyNr")
        }
        true
      } finally {
        if (varietyResultSet != null) varietyResultSet.close()
      }
    } finally {
      if (stmt != null) stmt.close()
    }
  }


  /**
    * Prepare the reader for providing crop parameter values for the year
    * specified. This should be called after init() and before trying to
    * retrieve crop parameters from the reader. It will read the entries
    * for crop parameters from the database and parse them into Crop-
    * Parameter instances. Any errors will be logged, and if the total
    * error count is > 0 an exception will be thrown.
    */
  @throws[SQLException]
  def prepare(year: Int): Unit = {
    if (!hasYear(year)) {
      throw new IllegalArgumentException(s"Requested crop parameters for crop: $cropNr year: $year grid: $gridCellNr are not available!")
    }

    var errorCount = 0
    val entry = cropCalendarEntries.get(year)

    if (entry.isDefined && cropVariety.isDefined && (entry.get.cropVarietyName != cropVariety.get)) {
      clear()
      cropParameterEntries.clear()
      cropVariety = Some(entry.get.cropVarietyName)

      val conn = dBConnector.connect()
      try {
        fetchCropParameterEntries(conn, entry.get.varietyNr)

        // create list of unique parameter codes
        val codes = mutable.HashSet[String]()
        cropParameterEntries.keys foreach { case (code, _) => codes.add(code) }

        // for each code:
        codes.toSeq.sorted foreach { code =>
          // get all key pairs (code, ord)
          val keys = cropParameterEntries.keys filter { case(c, _) => c.equalsIgnoreCase(code) }
          if (keys.size == 0) {
            logger.error(s"No data in database for parameter $code")
            errorCount += 1
          } else if (keys.size == 1) {
            // create a scalar CropParameter (Int or Double)
            val cpEntry = cropParameterEntries.get(keys.head)
            if (cpEntry.isDefined) {
              // get units text from database and reformat to match ScientificUnit notation requirements
              // 'ha.kg-1' -> [ha.kg-1]
              // '-', 'ha.kg-1' -> [-; ha.kg-1]
              val units = ScientificUnit.fromTxt(s"[${cpEntry.get.units.replace(" ", "").replace("'", "").replace(",", ";")}]")
              val pd = new ParameterDefinition(cpEntry.get.parameterCode, cpEntry.get.description, units)

              if (isIntegerValue(cpEntry.get.xValue)) {
                put(pd, cpEntry.get.xValue.intValue())
              } else {
                put(pd, cpEntry.get.xValue.doubleValue())
              }
              logger.debug(s"Added value ${cpEntry.get.xValue} for $pd")
            }
          } else if (keys.size > 1) {
            // create table CropParameter (Array2DRowRealMatrix)
            val sortedKeys = keys.toSeq sortBy { case(_, ord) => ord }
            val minOrdKey = sortedKeys minBy { case(_, ord) => ord }
            val maxOrdKey = sortedKeys maxBy { case(_, ord) => ord }
            if ((minOrdKey._2 != 1) || (maxOrdKey._2 - minOrdKey._2 + 1 != keys.size)) {
              logger.error(s"Ordering error in database for parameter $code: $sortedKeys")
              errorCount += 1
            } else {
              var matrix = new Array2DRowRealMatrix(keys.size, 2)
              var pd: Option[ParameterDefinition] = None

              // process all keys in order
              for (i <- 0 until maxOrdKey._2) {
                val cpEntry = cropParameterEntries.get(sortedKeys(i))
                if (cpEntry.isDefined) {
                  // define crop parameter using first available key
                  if (pd.isEmpty) {
                    val units = ScientificUnit.fromTxt(s"[${cpEntry.get.units.replace(" ", "").replace("'", "").replace(",", ";")}]")
                    pd = Some(ParameterDefinition(cpEntry.get.parameterCode, cpEntry.get.description, units))
                  }
                  if (cpEntry.get.yValue.isEmpty) {
                    logger.error(s"Parameter $code has undefined y-value in database for x-value ${cpEntry.get.xValue}")
                    errorCount += 1
                  } else {
                    if ((i > 0) && (cpEntry.get.xValue.doubleValue() < matrix.getEntry(i - 1, 0))) {
                      logger.error(s"Parameter $code has non increasing x-value for table definition in database")
                      errorCount += 1
                    } else {
                      matrix.setEntry(i, 0, cpEntry.get.xValue.doubleValue())
                      matrix.setEntry(i, 1, cpEntry.get.yValue.get.doubleValue())
                    }
                  }
                }
              }
              if (pd.isDefined) {
                put(pd.get, matrix)
                logger.debug(s"Added value $matrix for $pd")
              } else {
                logger.error(s"No crop parameter could be defined from the database for $code")
                errorCount += 1
              }
            }
          }
        }

        if (errorCount > 0) {
          val msg = s"Found $errorCount error(s) while loading crop parameters from the database, please check the logs."
          logger.error(msg)
          throw new RuntimeException(msg)
        }

      } finally {
        if (conn != null) conn.close()
      }
    }

    // TODO all the above still needs testing ... time to write the unit tests for this class
  }


  /**
    * Checks if the specified year is part of the crop calendar info read
    * from the database.
    */
  def hasYear(year: Int): Boolean = cropCalendarEntries.contains(year)


  /**
    * Check if specified BigDecimal is an integer or not.
    */
  private def isIntegerValue(bd: BigDecimal): Boolean = (bd.signum == 0) || (bd.scale <= 0)
}
