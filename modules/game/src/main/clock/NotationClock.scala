package lila.game.clock

import shogi.format.Tag
import shogi.format.Tags

object NotationClock {
  def timeControlKif(clock: Option[Clock.Config]): Tag =
    Tag(
      _.TimeControl,
      clock.fold("") { c =>
        s"${c.limitSeconds}秒|${c.byoyomiSeconds}秒(${c.periodsTotal})+${c.incrementSeconds}秒"
      },
    )

  def config(tags: Tags): Option[Clock.Config] = {
    val baseClk = tags.value.collectFirst { case Tag(Tag.TimeControl, str) =>
      str
    } flatMap { c => Clock.readKifConfig(c) orElse Clock.readCsaConfig(c) }
    tags(_.Byoyomi)
      .flatMap(Clock.parseJPTime _)
      .fold(baseClk)(byo => baseClk.map(_.copy(byoyomiSeconds = byo)))
  }

  def timeControlCsa(clock: Option[Clock.Config]) =
    Tag(
      Tag.TimeControl,
      clock.fold("") { c =>
        val init =
          if (c.limit.roundSeconds % 60 == 0) f"00:${c.limit.roundSeconds / 60}%02d"
          else f"00:00:${c.limit.roundSeconds}%02d"
        val byo = f"+${c.byoyomi.roundSeconds}%02d"
        val inc = if (c.hasIncrement) s"+${c.incrementSeconds}" else ""
        s"$init$byo$inc"
      },
    )
}
