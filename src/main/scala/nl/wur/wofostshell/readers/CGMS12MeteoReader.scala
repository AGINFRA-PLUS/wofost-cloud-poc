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

import java.lang.Math.log10
import java.math.BigDecimal
import java.sql._
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.util
import java.util.Locale

import nl.wur.wiss.core.ScientificUnit
import nl.wur.wiss.mathutils.RangeUtils
import nl.wur.wiss.meteoutils.{InMemoryMeteoReader, MeteoElement, MeteoReader}
import org.apache.commons.logging.LogFactory

import scala.collection.JavaConverters

// TODO: switch to using akka logging framework

/**
  * A reader that retrieves weather data from an Oracle CGMS12 database
  * using a JDBC connection.
  *
  * The reader highly depends on the specifics of how the data is stored in
  * the database. Changes to that will require updating of the reader.
  *
  * Note that this source code has been more or less automatically converted
  * from Java, so Scala syntax might not be very elegant.
  *
  * @param dbUrl JDBC url for database to connect to
  * @param dbUser user name for database login
  * @param dbPassword password for database login
  *
  * @author Rob Knapen, Wageningen Environmental Research
  */
@SerialVersionUID(1L)
class CGMS12MeteoReader(dbUrl: String, dbUser: Option[String] = None, dbPassword: Option[String] = None)
  extends MeteoReader with Serializable with Iterable[LocalDate] {

  private val CLASSNAME = classOf[CGMS12MeteoReader].getSimpleName
  private val LOGGER = LogFactory.getLog(classOf[CGMS12MeteoReader])

  // factor to recalculate wind speed at 10 m height to speed at 2 m height
  private val WINDSPEED_AT_10M_TO_2M_FACTOR = log10(2.0 / 0.033) / log10(10 / 0.033)

  // create an unmodifiable set of all meteo elements the CGMS database can provide
  private val SOURCE_ELEMENTS = Set(
    MeteoElement.TM_MN, MeteoElement.TM_MX, MeteoElement.TM_AV,
    MeteoElement.VP_AV, MeteoElement.WS10_AV, MeteoElement.WS2_AV,
    MeteoElement.PR_CU, MeteoElement.Q_CU,
    MeteoElement.E0, MeteoElement.ES0, MeteoElement.ET0
  )

  // database connection information
  private val dbConnector = new JDBCConnector(dbUrl, dbUser, dbPassword)
  private var gridCellNr: Option[Long] = None

  // weather source temporal info
  private var sourceFirstDate: Option[LocalDate] = None
  private var sourceLastDate: Option[LocalDate] = None

  // cached weather data fields
  private var prepared = false
  private val store = new InMemoryMeteoReader

  def getStore: InMemoryMeteoReader = store


  @throws[SQLException]
  def init(gridCellNr: Long): CGMS12MeteoReader = {
    // clear state
    this.gridCellNr = None
    store.clear()
    prepared = false

    // initialise for the specified grid cell (call prepare() afterwards to cache the data)
    val conn = dbConnector.connect()
    try
        if (!fetchLocation(conn, gridCellNr) || (!fetchAvailableWeatherDateRange(conn, gridCellNr))) {
          val msg = "Could not initialise location and available weather date range for grid: " + gridCellNr
          LOGGER.error(msg)
          throw new RuntimeException(msg)
        }
        else this.gridCellNr = Some(gridCellNr)
    finally if (conn != null) conn.close()

    this
  }


  /**
    * Fetches the location information (lat, lon, alt) for the specified grid cell.
    *
    * @param conn       database connection to use for qeuery
    * @param gridCellNr to retrieve information for
    * @return true when data exists, false otherwise
    * @throws SQLException reporting database query problems
    */
  @throws[SQLException]
  private def fetchLocation(conn: Connection, gridCellNr: Long): Boolean = {
    val sql = ("SELECT GRID_NO, LATITUDE, LONGITUDE, ALTITUDE " +
      "FROM GRID WHERE GRID_NO = %d").formatLocal(Locale.US, gridCellNr)

    val stmt = conn.createStatement
    val resultSet = stmt.executeQuery(sql)
    try
        if (!resultSet.isBeforeFirst) {
          store.clearLocation()
          LOGGER.error("Failed deriving location information from 'grid' table for grid: %d".formatLocal(Locale.US, gridCellNr))
          false
        }
        else {
          resultSet.next

          store.setLatitude(resultSet.getBigDecimal(2).doubleValue)
          store.setLongitude(resultSet.getBigDecimal(3).doubleValue)
          store.setAltitude(resultSet.getBigDecimal(4).doubleValue)

          LOGGER.info("Location for grid %d is lat: %.5f long: %.5f alt: %.2f m"
            .formatLocal(Locale.US, gridCellNr, store.getLatitudeDD, store.getLongitudeDD, store.getAltitudeM))

          true
        }
    finally {
      if (resultSet != null) resultSet.close()
      if (stmt != null) stmt.close()
    }
  }


  /**
    * Fetches the date range (min, max) for which weather data is available
    * for the specified grid cell.
    *
    * @param conn       database connection to use for query
    * @param gridCellNr to retrieve information for
    * @return true when data exists, false otherwise
    * @throws SQLException reporting database query problems
    */
  @throws[SQLException]
  private def fetchAvailableWeatherDateRange(conn: Connection, gridCellNr: Long): Boolean = {
    val sql = "SELECT MIN(DAY), MAX(DAY) FROM WEATHER_OBS_GRID WHERE GRID_NO = %d"
      .formatLocal(Locale.US, gridCellNr)

    val stmt = conn.createStatement
    val resultSet = stmt.executeQuery(sql)
    try
        if (!resultSet.isBeforeFirst) {
          this.sourceFirstDate = None
          this.sourceLastDate = None

          LOGGER.error("Failed deriving weather date range from 'WEATHER_OBS_GRID' table for grid: %d"
            .formatLocal(Locale.US, gridCellNr))

          false
        }
        else {
          resultSet.next
          val minTime = resultSet.getTimestamp(1)
          val maxTime = resultSet.getTimestamp(2)
          if ((minTime == null) || (maxTime == null)) {
            LOGGER.error(s"Failed deriving weather date range from 'WEATHER_OBS_GRID' table for grid: $gridCellNr")
            false
          } else {
            this.sourceFirstDate = Some(resultSet.getTimestamp(1).toLocalDateTime.toLocalDate)
            this.sourceLastDate = Some(resultSet.getTimestamp(2).toLocalDateTime.toLocalDate)
            LOGGER.info(s"Weather date range for grid $gridCellNr is ${this.sourceFirstDate} -- ${this.sourceLastDate}")
            true
          }
        }
    finally {
      if (stmt != null) stmt.close()
      if (resultSet != null) resultSet.close()
    }
  }


  /**
    * Tries to fetch the requested meteo elements (weather data) from the
    * database for the specified time period. The init method has to be
    * called first on the instance.
    *
    * @param requestedStartDate to retrieve data for, null for minimum date
    * @param requestedEndDate   to retrieve data for, null for maximum date
    * @param requestedElements  to retrieve data for
    * @return true when requested data was successfully retrieved
    * @throws SQLException reporting database query problems
    */
  @throws[SQLException]
  private def fetchWeather(requestedStartDate: LocalDate, requestedEndDate: LocalDate, requestedElements: util.Set[MeteoElement]): Boolean = {
    throwExceptionIfNotInitialised()

    // default to min or max date when parameter is not specified
    val queryStartDate = if (requestedStartDate == null) LocalDate.MIN else requestedStartDate
    val queryEndDate = if (requestedEndDate == null) LocalDate.MAX else requestedEndDate

    // check requested date ordering
    if (queryStartDate.isAfter(queryEndDate))
      throw new IllegalArgumentException("Requested start date " + queryStartDate + " is after requested end date " + queryEndDate)

    // check if all requested meteo elements can be delivered
    val requested = JavaConverters.asScalaSet(requestedElements)
    if (!requested.subsetOf(SOURCE_ELEMENTS)) {
      val missing = requested.diff(SOURCE_ELEMENTS).mkString(", ")
      throw new IllegalArgumentException("The following requested meteo elements can not be delivered: " + missing)
    }

    // check if requested data is already available
    if (store.hasDataForRange(queryStartDate, queryEndDate)) return true

    // have to load new data
    store.clear()

    // implementation of SQL date functions differ per database ...
    // always get all available elements, could be optimized
    val sql = dbConnector.databaseType() match {
      case DatabaseType.Oracle =>
        ("SELECT GRID_NO, DAY, TEMPERATURE_MIN, TEMPERATURE_MAX, TEMPERATURE_AVG, VAPOURPRESSURE, " +
          "PRECIPITATION, WINDSPEED, RADIATION, SNOWDEPTH, E0, ES0, ET0 FROM WEATHER_OBS_GRID WHERE " +
          "GRID_NO = %d AND (DAY BETWEEN to_date('%d-%d-%d','yyyy-mm-dd') AND to_date('%d-%d-%d', 'yyyy-mm-dd'))")
          .formatLocal(Locale.US, this.gridCellNr.get,
            queryStartDate.getYear, queryStartDate.getMonthValue, queryStartDate.getDayOfMonth,
            queryEndDate.getYear, queryEndDate.getMonthValue, queryEndDate.getDayOfMonth)
      case DatabaseType.SQLite =>
        ("SELECT GRID_NO, DAY, TEMPERATURE_MIN, TEMPERATURE_MAX, TEMPERATURE_AVG, VAPOURPRESSURE, " +
          "PRECIPITATION, WINDSPEED, RADIATION, SNOWDEPTH, E0, ES0, ET0 FROM WEATHER_OBS_GRID WHERE " +
          "GRID_NO = %d AND (DAY BETWEEN date('%04d-%02d-%02d') AND date('%04d-%02d-%02d', '+1 days'))")
          .formatLocal(Locale.US, this.gridCellNr.get,
            queryStartDate.getYear, queryStartDate.getMonthValue, queryStartDate.getDayOfMonth,
            queryEndDate.getYear, queryEndDate.getMonthValue, queryEndDate.getDayOfMonth)
      case _ => {
        val msg = s"Can not generate query for unsupported database type: ${dbConnector.databaseType()}"
        LOGGER.error(msg)
        throw new IllegalArgumentException(msg)
      }
    }

    val conn = dbConnector.connect()
    val stmt = conn.createStatement
    val resultSet = stmt.executeQuery(sql)
    try
        if (!resultSet.isBeforeFirst) {
          LOGGER.info("No weather data available for grid " + gridCellNr + " between " + queryStartDate + " and " + queryEndDate)
          false
        }
        else {
          var count = 0
          while (resultSet.next) {
            val md = new InMemoryMeteoReader.MeteoData
            md.day = resultSet.getTimestamp(2).toLocalDateTime.toLocalDate
            md.temperatureMin = doubleOrNaN(resultSet.getBigDecimal(3)) // degrees C

            md.temperatureMax = doubleOrNaN(resultSet.getBigDecimal(4))
            md.temperatureAvg = doubleOrNaN(resultSet.getBigDecimal(5))
            md.vapourPressure = doubleOrNaN(resultSet.getBigDecimal(6)) // HPA

            md.precipitation = doubleOrNaN(resultSet.getBigDecimal(7)) // mm

            // md.snowDepth = doubleOrNaN(resultSet.getBigDecimal(10));      // cm
            md.e0 = doubleOrNaN(resultSet.getBigDecimal(11))
            md.es0 = doubleOrNaN(resultSet.getBigDecimal(12))
            md.et0 = doubleOrNaN(resultSet.getBigDecimal(13))
            md.windSpeed10M = doubleOrNaN(resultSet.getBigDecimal(8)) // m/s at 10 m height

            md.windSpeed2M = md.windSpeed10M * WINDSPEED_AT_10M_TO_2M_FACTOR // m/s at 10 m height -> at 2 m height

            md.radiation = doubleOrNaN(resultSet.getBigDecimal(9)) / 1000.0 // KJM2 -> want MJM2

            // store weather data
            store.put(md.day, md)
            count += 1
          }

          // check if available data less than expected for specified time period
          val days = DAYS.between(queryStartDate, queryEndDate) + 1
          if (count < days) {
            LOGGER.warn("Only " + count + " records selected from table 'WEATHER_OBS_GRID' for grid " +
              gridCellNr + ", period " + queryStartDate + " -- " + queryEndDate + " (= " + days + " days).")
          }

          LOGGER.info(count + " records selected from table 'WEATHER_OBS_GRID' for grid " +
            gridCellNr + ", period " + queryStartDate + " -- " + queryEndDate + " (= " + days + " days).")
          true
        }
    finally {
      if (resultSet != null) resultSet.close()
      if (stmt != null) stmt.close()
      if (conn != null) conn.close()
    }
  }


  private def doubleOrNaN(value: BigDecimal) = if (value != null) value.doubleValue else Double.NaN


  private def throwExceptionIfNotInitialised(): Unit = {
    if (this.gridCellNr.isEmpty) {
      throw new IllegalStateException("Instance is not properly initialised, no grid cell known.")
    }
  }


  private def throwExceptionIfNotPrepared(): Unit = {
    if (!this.prepared) {
      throw new IllegalStateException("Instance data has not been prepared before trying to retrieve it.")
    }
  }


  /**
    * Fetch (or calculate) the requested weather data for the specified time
    * period so that it later can be retrieved by getValue calls.
    *
    * @param startDate
    * @param endDate
    * @param elements
    */
  override def prepare(startDate: LocalDate, endDate: LocalDate, elements: util.Set[MeteoElement]): Unit = {
    throwExceptionIfNotInitialised()

    if (startDate == null) throw new IllegalArgumentException(s"$CLASSNAME.prepare : No period for meteo preparation. Start date is null.")

    if (endDate == null) throw new IllegalArgumentException(s"$CLASSNAME.prepare : No period for meteo preparation. End date is null.")

    if (endDate.isBefore(startDate)) throw new IllegalArgumentException(String.format("%s.prepare : No period for meteo preparation. End " +
      "date must be later than start date (Start=%s, End=%s).", CLASSNAME, startDate.toString, endDate.toString))

    if (this.sourceFirstDate.isEmpty || this.sourceLastDate.isEmpty) throw new IllegalStateException(s"$CLASSNAME.prepare: Method called while either source first or last date is not set yet.")

    if (!RangeUtils.inRange(startDate, this.sourceFirstDate.get, this.sourceLastDate.get)) throw new IllegalArgumentException(String.format("%s.getValue : Illegal date (%s). Must be between " +
      "source dates %s and %s.", CLASSNAME, startDate.toString, this.sourceFirstDate.toString, this.sourceLastDate.toString))

    if (!RangeUtils.inRange(endDate, this.sourceFirstDate.get, this.sourceLastDate.get)) throw new IllegalArgumentException(String.format("%s.getValue : Illegal date (%s). Must be between " +
      "source dates %s and %s.", CLASSNAME, startDate.toString, this.sourceFirstDate.toString, this.sourceLastDate.toString))

    if (elements.isEmpty) throw new IllegalArgumentException(s"$CLASSNAME.prepare : No meteo elements to prepare.")

    prepared = false
    try {
      fetchWeather(startDate, endDate, elements)
      prepared = true
    } catch {
      case e: SQLException =>
        val msg = "Database problem when trying to prepare for time period " + startDate + " -- " + endDate
        LOGGER.error(msg, e)
        throw new RuntimeException(msg, e)
    }
  }


  // currently there is no difference between the source elements and the prepared elements
  override def getPreparedElements: util.Set[MeteoElement] = {
    throwExceptionIfNotPrepared()
    store.getPreparedElements
  }

  // returns value for element, returns NaN if not available, date must be in prepared period, date in prepared elements
  override def getValue(localDate: LocalDate, meteoElement: MeteoElement, scientificUnit: ScientificUnit): Double = {
    throwExceptionIfNotPrepared()
    store.getValue(localDate, meteoElement, scientificUnit)
  }

  override def getPreparedFirstDate: LocalDate = {
    throwExceptionIfNotPrepared()
    store.getPreparedFirstDate
  }

  override def getPreparedLastDate: LocalDate = {
    throwExceptionIfNotPrepared()
    store.getPreparedLastDate
  }

  override def getSourceElements: util.Set[MeteoElement] = JavaConverters.setAsJavaSet(SOURCE_ELEMENTS)

  override def iterator: Iterator[LocalDate] = JavaConverters.asScalaIterator(store.iterator())

  override def getLatitudeDD: Double = store.getLatitudeDD

  override def getLongitudeDD: Double = store.getLongitudeDD

  override def getAltitudeM: Double = store.getAltitudeM

  override def getSourceFirstDate: LocalDate = this.sourceFirstDate.orNull

  override def getSourceLastDate: LocalDate = this.sourceLastDate.orNull

  override def getNativeUnit(meteoElement: MeteoElement): ScientificUnit = store.getNativeUnit(meteoElement)

}
