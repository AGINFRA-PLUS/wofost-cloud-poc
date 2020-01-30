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
package nl.wur.dataminer.client

import java.net.{HttpURLConnection, URL}
import java.util.Locale

import akka.event.LoggingAdapter


object DataMinerConnector {

  val defaultConnectionTimeoutSec: Int = 50000
  val defaultReadTimeoutSec: Int = 30000

  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  def dataMinerGet(url: String, gCubeToken: Option[String],
                   log: LoggingAdapter,
                   connectTimeout: Int = defaultConnectionTimeoutSec,
                   readTimeout: Int = defaultReadTimeoutSec): Either[Throwable, String] =
  {
    log.debug(s"request: $url")
    try {
      val startTime = System.nanoTime()
      val connection = gCubeToken match {
        case None => new URL(url).openConnection.asInstanceOf[HttpURLConnection]
        case Some(token) => new URL(s"$url&gcube-token=$token").openConnection.asInstanceOf[HttpURLConnection]
      }
      connection.setConnectTimeout(connectTimeout)
      connection.setReadTimeout(readTimeout)
      connection.setRequestMethod("GET")

      val content = connection.getResponseCode match {
        case 200 =>
          val inputStream = connection.getInputStream
          val body = io.Source.fromInputStream(inputStream).mkString
          if (inputStream != null) inputStream.close()
          body
        case code =>
          throw new RuntimeException(s"HTTP request failed, status ${connection.getResponseCode} - ${connection.getResponseMessage}")
      }

      val endTime = System.nanoTime()
      log.debug("response: %d - %s (%.3f sec)".
        formatLocal(Locale.US, connection.getResponseCode, connection.getResponseMessage, (endTime - startTime)/1e9))

      Right(content)
    } catch {
      case e: Throwable => Left(e)
    }
  }

  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  def dataMinerPost(url: String, gCubeToken: Option[String], postData: String,
                    log: LoggingAdapter,
                    connectTimeout: Int = defaultConnectionTimeoutSec,
                    readTimeout: Int = defaultReadTimeoutSec): Either[Throwable, String] =
  {
    log.debug(s"request: $url")
    try {
      val startTime = System.nanoTime()

      val postDataBytes = postData.toString.getBytes("UTF-8")

      val connection = gCubeToken match {
        case None => new URL(url).openConnection.asInstanceOf[HttpURLConnection]
        case Some(token) => new URL(s"$url&gcube-token=$token").openConnection.asInstanceOf[HttpURLConnection]
      }
      connection.setConnectTimeout(connectTimeout)
      connection.setReadTimeout(readTimeout)
      connection.setRequestMethod("POST")
      connection.setRequestProperty("Content-Type", "text/xml")
      connection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length))
      connection.setDoOutput(true)
      connection.getOutputStream.write(postDataBytes)

      val content = connection.getResponseCode match {
        case 200 =>
          val inputStream = connection.getInputStream
          val body = io.Source.fromInputStream(inputStream).mkString
          if (inputStream != null) inputStream.close()
          body
        case code =>
          throw new RuntimeException(s"HTTP request failed, status $code - ${connection.getResponseMessage}")
      }

      val endTime = System.nanoTime()
      log.debug("response: %d - %s (%.3f sec)".
        formatLocal(Locale.US, connection.getResponseCode, connection.getResponseMessage, (endTime - startTime)/1e9))

      Right(content)
    } catch {
      case e: Throwable => Left(e)
    }
  }

}
