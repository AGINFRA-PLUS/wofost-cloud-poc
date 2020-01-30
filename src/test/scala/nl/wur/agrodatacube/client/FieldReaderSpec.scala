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
package nl.wur.agrodatacube.client

import akka.actor.ActorSystem
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}


class FieldReaderSpec extends FlatSpec with Matchers {

  val system = ActorSystem("scalatest")
  val log = Logging.getLogger(system, this)

  val config = ConfigFactory.load()
  val token = config.getString("application.agrodatacube.token")

  "A FieldReader" should "read a list of fields for a WGS84 point" in {
    val r = new FieldReader()

    // get field data including geometry
    r.getFieldsForPointLocation(5.2, 52.0, token, log, false) match {
      case Left(e) => fail(e)
      case Right(fields) =>
        fields.size should be (11)
    }

    // get field data excluding geometry
    r.getFieldsForPointLocation(5.2, 52.0, token, log, true) match {
      case Left(e) => fail(e)
      case Right(fields) =>
        fields.size should be (11)
    }
  }

  "A FieldReader" should "read a list of fields, filtered on location, year, and crop" in {
    val r = new FieldReader()

    // get field data excluding geometry
    r.getFieldsForPointLocation(5.2, 52.0, token, log, true) match {
      case Left(e) => fail(e)
      case Right(fields) =>
        fields.size should be (11)
        /*
          0 = {FieldReader$Field@4561} "Field(,725300,2009,259,Mais, snij-)"
          1 = {FieldReader$Field@4562} "Field(,1201141,2010,259,Mais, snij-)"
          2 = {FieldReader$Field@4563} "Field(,2803067,2012,266,Grasland, tijdelijk)"
          3 = {FieldReader$Field@4564} "Field(,5081262,2015,266,Grasland, tijdelijk)"
          4 = {FieldReader$Field@4565} "Field(,4192206,2014,266,Grasland, tijdelijk)"
          5 = {FieldReader$Field@4566} "Field(,3220699,2013,266,Grasland, tijdelijk)"
          6 = {FieldReader$Field@4567} "Field(,8321566,2018,265,Grasland, blijvend)"
          7 = {FieldReader$Field@4568} "Field(,6452021,2017,265,Grasland, blijvend)"
          8 = {FieldReader$Field@4569} "Field(,5721927,2016,266,Grasland, tijdelijk)"
          9 = {FieldReader$Field@4570} "Field(,1770673,2011,259,Mais, snij-)"
         */
    }

    r.getFieldsForWktLocation("POINT(5.2 52.0)", "4326", token, log, Some(2018), Some("265"), true, true) match {
      case Left(e) => fail (e)
      case Right(fields) =>
        fields.size should be (1)
    }
  }

  "A FieldReader" should "return a field count based on location, year, and crop" in {
    val r = new FieldReader()
    r.getFieldCountForLocation("POINT(5.2 52.0)", "4326", token, log, Some(2018), Some("265")) match {
      case Left(e) => fail (e)
      case Right(info) =>
        info.size should be (1)
    }
  }

}
