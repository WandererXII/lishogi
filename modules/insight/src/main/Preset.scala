package lila.insight

import lila.rating.PerfType

case class Preset(name: String, question: Question[_])

object Preset {

  import lila.insight.{ Dimension => D, Metric => M }

  private val filterBlitzPlus = List(
    Filter(D.Perf, List(PerfType.Blitz, PerfType.Rapid, PerfType.Classical))
  )

  val forMod = List(
    Preset(
      "ACPL by date",
      Question(D.Date, M.MeanCpl, filterBlitzPlus)
    ),
    Preset(
      "Blurs by date",
      Question(D.Date, M.Blurs, filterBlitzPlus)
    ),
    Preset(
      "ACPL by blur",
      Question(D.Blur, M.MeanCpl, filterBlitzPlus)
    ),
    Preset(
      "Blurs by result",
      Question(D.Result, M.Blurs, filterBlitzPlus)
    ),
    Preset(
      "ACPL by time variance",
      Question(D.TimeVariance, M.MeanCpl, Nil)
    ),
    Preset(
      "Blur by time variance",
      Question(D.TimeVariance, M.Blurs, filterBlitzPlus)
    ),
    Preset(
      "Time variance by date",
      Question(D.Date, M.TimeVariance, Nil)
    )
  )

  val base = List(
    Preset(
      "Do I gain more rating points against weaker or stronger opponents?",
      Question(D.OpponentStrength, M.RatingDiff, Nil)
    ),
    Preset(
      "How quickly do I move each piece in bullet and blitz games?",
      Question(
        D.PieceRole,
        M.Movetime,
        List(
          Filter(D.Perf, List(PerfType.Bullet, PerfType.Blitz))
        )
      )
    ),
    Preset(
      "What is the Win-Rate of my favourite openings as sente?",
      Question(
        D.Opening,
        M.Result,
        List(
          Filter(
            D.Perf,
            List(PerfType.Bullet, PerfType.Blitz, PerfType.Rapid, PerfType.Classical, PerfType.Correspondence)
          ),
          Filter(D.Color, List(shogi.Sente))
        )
      )
    ),
    Preset(
      "How often do I punish blunders made by my opponent during each game phase?",
      Question(D.Phase, M.Opportunism, Nil)
    ),
    Preset(
      "When I trade bishops, how do games end?",
      Question(
        D.Perf,
        M.Result,
        List(
          Filter(D.BishopTrade, List(BishopTrade.Yes))
        )
      )
    ),
    Preset(
      "When I trade rooks, how do games end?",
      Question(
        D.Perf,
        M.Result,
        List(
          Filter(D.RookTrade, List(RookTrade.Yes))
        )
      )
    ),
    Preset(
      "What is the average rating of my opponents across each variant?",
      Question(D.Perf, M.OpponentRating, Nil)
    ),
    Preset(
      "How well do I move each piece in the opening?",
      Question(
        D.PieceRole,
        M.MeanCpl,
        List(
          Filter(D.Phase, List(Phase.Opening))
        )
      )
    )
  )
}
