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
package nl.wur.wofostshell.parameters

import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source


class ParXChangeMarshallerSpec extends FlatSpec with Matchers {

  private val inputFileName = "data/cgms12eu2-crop_2-grid_64189-year_2010-stu_9001121_params.json"

  def fixture =
    new {
      val bufferedSource = Source.fromFile(inputFileName)
      val json = bufferedSource.getLines().mkString
      val data = ParXChangeMarshaller.fromJSON(json)
      val parXChange = ParXChangeMarshaller.deserialize(data)
      bufferedSource.close()
    }

  "A ParXChangeMarshaller" should "serialize parameters" in {
    val f = fixture
    val data = ParXChangeMarshaller.serialize(f.parXChange)

    // verify total size (number of variables defined)
    data.size should be (85)

    // VarInt(WOFOST,TSUMEM,[C.d],false,false,110)
    data.find { p => p.name.equalsIgnoreCase("tsumem")} match {
      case Some (varInfo) => varInfo.asInstanceOf[VarInt].value should be (110)
      case None => fail("No TSUMEM VarInfo found")
    }

    // VarDouble(WOFOST,TSUM1,[C.d],false,false,754.645)
    data.find { p => p.name.equalsIgnoreCase("tsum1")} match {
      case Some (varInfo) => varInfo.asInstanceOf[VarDouble].value should be (754.645)
      case None => fail("No TSUM1 VarInfo found")
    }

    // VarString(WOFOST,PUBLISHER_E0,[NA],false,false,SimMeteo)
    data.find { p => p.name.equalsIgnoreCase("publisher_e0")} match {
      case Some (varInfo) => varInfo.asInstanceOf[VarString].value should be ("SimMeteo")
      case None => fail("No PUBLISHER_E0 VarInfo found")
    }

    // VarLocalDate(TIMEDRIVER,ENDDATE,[NA],false,false,2011-05-06)
    data.find { p => p.name.equalsIgnoreCase("enddate")} match {
      case Some (varInfo) =>
        varInfo.asInstanceOf[VarDateString].value should be ("06/05/2011")
      case None => fail("No ENDDATE VarInfo found")
    }

    // VarControllerArrayList(WOFOST,CONTROLLERS,[NA],false,false,
    //   [ENVIRONMENT_WB_FREEDRAIN, CROP_ROTATION_1, CROP_HARVESTER_1])
    data.find { p => p.name.equalsIgnoreCase("controllers")} match {
      case Some (varInfo) =>
        val list = varInfo.asInstanceOf[VarControllerList].controllers
        list.size should be (3)
        list(0) should be ("ENVIRONMENT_WB_FREEDRAIN")
        list(1) should be ("CROP_ROTATION_1")
        list(2) should be ("CROP_HARVESTER_1")
      case None => fail("No CONTROLLERS VarInfo found")
    }

    // VarInterpolator(WOFOST,AMAXTB,[NA],true,false,CONSTANTEXTRAPOLATION,[-],[kg.ha-1.hr-1],
    //  Vector((0.0,70.0), (1.25,70.0), (1.5,63.0), (1.75,49.0), (2.0,21.0)))
    data.find { p => p.name.equalsIgnoreCase("amaxtb")} match {
      case Some (varInfo) =>
        val interpolator = varInfo.asInstanceOf[VarInterpolator]
        interpolator.extrapolationType should be ("CONSTANTEXTRAPOLATION")
        interpolator.unitsX should be ("[-]")
        interpolator.unitsY should be ("[kg.ha-1.hr-1]")
        interpolator.points.size should be (5)
        interpolator.points(0)._1 should be (0.0)
        interpolator.points(0)._2 should be (70.0)
        interpolator.points(1)._1 should be (1.25)
        interpolator.points(1)._2 should be (70.0)
        interpolator.points(2)._1 should be (1.5)
        interpolator.points(2)._2 should be (63.0)
        interpolator.points(3)._1 should be (1.75)
        interpolator.points(3)._2 should be (49.0)
        interpolator.points(4)._1 should be (2.0)
        interpolator.points(4)._2 should be (21.0)
      case None => fail("No ENDDATE VarInfo found")
    }

    // verify the meteo data
    data.find { p => p.name.equalsIgnoreCase("meteo")} match {
      case Some (varInfo) => {
        val meteoData = varInfo.asInstanceOf[VarMeteoData]
        meteoData.variables.size should be (11)
        meteoData.values.size should be (491)
      }
      case None => fail("No MeteoReader VarInfo found")
    }
  }

  "A ParXChangeMarshaller" should "be able to create JSON" in {
    val f = fixture
    val data = ParXChangeMarshaller.serialize(f.parXChange)
    val json = ParXChangeMarshaller.asJSON(data)

//    val file = new File("data/cgms12eu2-crop_2-grid_64189-year_2010-stu_9001121_params.json")
//    val bw = new BufferedWriter(new FileWriter(file))
//    bw.write(json)
//    bw.close()

    // println(json)
    // TODO check expected content of the (compact) json string
  }

  // TODO check specific values, e.g. meteo observations timeseries

  "A ParXChangeMarshaller" should "support round-trip conversion" in {
    val f = fixture
    val data = ParXChangeMarshaller.serialize(f.parXChange)
    val back = ParXChangeMarshaller.deserialize(data)

    back.size() should be (f.parXChange.size())
    back.forEach { varInfo =>
      f.parXChange.contains(varInfo.ucVarName, varInfo.cls, true) should be (true)
    }
  }

  "A ParXChangeMarshaller" should "be able to read JSON" in {
    val f = fixture
    val data = ParXChangeMarshaller.serialize(f.parXChange)
    val json = ParXChangeMarshaller.asJSON(data)

    val dataBack = ParXChangeMarshaller.fromJSON(json)
    val parXChangeBack = ParXChangeMarshaller.deserialize(dataBack)

    parXChangeBack.size() should be (f.parXChange.size())
    f.parXChange.forEach { varInfo =>
      parXChangeBack.contains(varInfo.ucVarName, varInfo.cls, true) should be (true)
    }
  }

}
