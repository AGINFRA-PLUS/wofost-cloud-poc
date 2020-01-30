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

import java.sql.{Connection, DriverManager, ResultSet}

import scala.collection.mutable.ListBuffer

// TODO: switch to using akka logging framework

object PostgresqlConnection {

  var connection: Connection = null

  def connect(): Unit = {
    if (connection == null) {
      // connect to the database
      val driver = "<JDBC DRIVER CLASS>"
      val url = "<JDBC CONNECTION URL>"
      val username = "<USERNAME>"
      val password = "<PASSWORD>"

      try {
        Class.forName(driver)
        connection = DriverManager.getConnection(url, username, password)
      } catch {
        case e: Throwable => e.printStackTrace()
      }
    }
  }


  def query(sql: String): String = {
    var t0: Long = 0.longValue()
    var t1: Long = 0.longValue()

    val response = if (connection != null) {
      try {
        t0 = System.nanoTime()
        val statement = connection.createStatement()
        val resultSet = statement.executeQuery(sql)
        t1 = System.nanoTime()
        // TODO: strategy for writing output (add as function parameter?)
        // TODO: use GeoJSON (different output format?)
        toJson(createResultTable(resultSet))
      } catch {
        case e: Throwable => {
          e.printStackTrace()
          s"""{ "error" : "${e.getMessage}" }"""
        }
      }
    }

    val result = new StringBuilder()
    result.append("{ ")
    result.append("\"request\": { ")
    result.append(s""""query": "$sql", """)
    result.append(s""""time_msec": ${(t1 - t0).toDouble/1e6}""")
    result.append(" }, ")
    result.append(s""""response": $response""")
    result.append(" }")
    result.toString()
  }


  def disconnect(): Unit = {
    if (connection != null) {
      connection.close()
      connection = null
    }
  }


  def createResultTable(rs: ResultSet): List[List[Any]] = {
    var table = new ListBuffer[List[Any]]()

    try {
      // header rows
      val headers = new ListBuffer[String]()
      for (index <- 1 to rs.getMetaData.getColumnCount) {
        headers += rs.getMetaData.getColumnName(index)
      }
      table += headers.toList

      // data
      while (rs.next()) {
        var values = new ListBuffer[Any]()
        for (index <- 1 to rs.getMetaData.getColumnCount) {
          values += rs.getObject(index)
        }
        table += values.toList
      }
    } catch {
      case e: Throwable => e.printStackTrace()
    }

    table.toList
  }

  def toJson(resultTable: List[List[Any]]): String = {
    var json = new StringBuilder("[")

    if ((resultTable != null) && resultTable.nonEmpty) {
      val headers = resultTable.head
      for (index <- 1 until resultTable.size) {
        val line = new StringBuilder()
        if (index > 1) {
          line.append(",")
        }
        line.append(" { ")
        for (j <- headers.indices) {
          if (j > 0) {
            line.append(", ")
          }

          val key = s""""${headers(j)}""""
          val value = resultTable(index)(j) match {
            case null => s""" "NA" """
            case v: Number => v
            case s: java.lang.String => if (s.startsWith("{") || s.startsWith("[")) s else s""" "$s" """
            case other => s""" "$other" """
          }
          line.append(s" $key : $value")
        }
        line.append(" } ")
        json.append(line)
      }
    }

    json.append("]").toString()
  }

}
