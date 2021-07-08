package shogi
package format

class UsiMoveTest extends ShogiTest {

  "piotr encoding" should {
    "be reflexive" in {
      val move = Usi.Move("a2g7").get
      Usi.Move piotr move.piotr must_== move.some
    }
  }
}
