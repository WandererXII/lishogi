package lila.shoginet

import org.specs2.mutable._

import shogi.format.forsyth.Sfen

final class ForYaneuraOuTest extends Specification {

  private def forYaneuraOu(sfen: String): Boolean =
    ShoginetApi.forYaneuraOu(
      Sfen(sfen)
        .toPosition(shogi.variant.Standard)
        .getOrElse(sys.error(s"Invalid sfen: $sfen")),
    )

  "forYaneuraOu" should {
    "accept the initial position" in {
      forYaneuraOu("lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1") must beTrue
    }
    "accept promoted pieces on the board" in {
      forYaneuraOu("3gk4/9/2+B1+P4/9/9/9/9/9/4K4 b R2P 1") must beTrue
      forYaneuraOu("4k4/9/9/9/9/9/9/+R+R7/4K4 b - 1") must beTrue
    }
    "accept full material when a promoted piece replaces another base piece" in {
      // 1i lance is a tokin instead: 18 pawn-type pieces, 3 lances
      forYaneuraOu("lnsgkgsnl/1r5b1/pppppppp1/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSN+P b - 1") must beTrue
    }
    "reject too many of a piece in hand" in {
      forYaneuraOu("4k4/9/9/9/9/9/9/9/4K4 b 3R 1") must beFalse
    }
    "count promoted pieces as their base piece" in {
      // 2 dragons + 1 rook in hand = 3 rooks
      forYaneuraOu("4k4/9/9/9/9/9/9/+R+R7/4K4 b R 1") must beFalse
      // 1 tokin + 18 pawns in hands = 19 pawns
      forYaneuraOu("4k4/9/9/9/9/9/9/9/+P3K4 b 9P9p 1") must beFalse
    }
  }
}
