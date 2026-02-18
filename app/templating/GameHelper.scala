package lila.app
package templating

import controllers.routes
import play.api.i18n.Lang

import shogi.Clock
import shogi.Color
import shogi.Mode
import shogi.variant.Variant
import shogi.{ Status => S }

import lila.api.Context
import lila.app.ui.ScalatagsTemplate._
import lila.common.Icons
import lila.game.Game
import lila.game.Namer
import lila.game.Player
import lila.game.Pov
import lila.game.Source
import lila.i18n.defaultLang
import lila.i18n.{ I18nKeys => trans }
import lila.user.User

trait GameHelper {
  self: I18nHelper
    with AssetHelper
    with UserHelper
    with StringHelper
    with ShogigroundHelper
    with ShogiHelper =>

  private val dataLive = attr("data-live")

  def netBaseUrl: String

  def povOpenGraph(pov: Pov)(implicit lang: Lang) =
    lila.app.ui.OpenGraph(
      image = gameThumbnail(pov),
      title = titleGame(pov.game),
      url = s"$netBaseUrl${routes.Round.watcher(pov.gameId, pov.color.name).url}",
      description = describePov(pov),
    )

  def gameThumbnail(p: Pov) =
    Game.gifVariants.contains(p.game.variant) option cdnUrl(
      routes.Export.gameThumbnail(p.gameId).url,
    )

  // Shogi - Dalliard vs Smith
  // Chushogi - PeterFile vs FilePeter
  def titleGame(g: Game)(implicit lang: Lang) =
    s"${if (g.imported) variantName(g.variant) else g.perfType.trans} - ${trans.xVsY.txt(playerText(g.sentePlayer), playerText(g.gotePlayer))}"

  // Beethoven played Handel - Rated Shogi (5|3) - Handel won! Click to replay, analyse, and discuss the game!
  def describePov(pov: Pov)(implicit lang: Lang) = {
    import pov._
    if (game.imported) s"${trans.importedGame.txt()} ${trans.clickGame.txt()}"
    else {
      val sentePlayer = playerText(game.player(shogi.Sente), withRank = false)
      val gotePlayer  = playerText(game.player(shogi.Gote), withRank = false)
      val players =
        if (game.finishedOrAborted) trans.xPlayedY.txt(sentePlayer, gotePlayer)
        else trans.xIsPlayingY.txt(sentePlayer, gotePlayer)
      val gameDesc =
        List(
          modeName(game.mode),
          game.perfType.trans,
          game.clock.map(_.config) ?? { clock => s"(${clock.show})" },
        ).filter(_.nonEmpty).mkString(" ")
      val result = game.winner.map(w => trans.xWon.txt(playerText(w))) getOrElse {
        if (game.finishedOrAborted) trans.gameWasDraw.txt()
        else trans.winnerIsNotYetDecided.txt()
      }
      s"$players - $gameDesc - $result ${trans.clickGame.txt()}"
    }
  }

  def shortClockName(clock: Option[Clock.Config])(implicit lang: Lang): Frag =
    clock.fold[Frag](trans.unlimited())(shortClockName)

  def shortClockName(clock: Clock.Config): Frag = raw(clock.show)

  def modeName(mode: Mode)(implicit lang: Lang): String =
    mode match {
      case Mode.Casual => trans.casual.txt()
      case Mode.Rated  => trans.rated.txt()
    }

  @inline def variantClass(v: Variant): String =
    s"v-${v.key}"

  @inline def mainVariantClass(v: Variant): String =
    s"main-v-${v.key}"

  def engineSpan(ec: lila.game.EngineConfig)(implicit lang: Lang): Tag =
    span(cls := "engine")(
      span(cls := "engine-lvl")(
        Namer.engineLevel(ec),
        nbsp,
      ),
      Namer.engineName(ec),
    )

  def playerText(player: Player, withRank: Boolean = false)(implicit lang: Lang) =
    Namer.playerTextBlocking(player, withRank = withRank)(lightUser, lang)

  def gameVsText(game: Game, withRanks: Boolean = false)(implicit lang: Lang): String =
    Namer.gameVsTextBlocking(game, withRanks = withRanks)(lightUser, lang)

  val berserkIconSpan = i(dataIcon := Icons.berserk, cls := "berserk")

  def ratingString(p: Player) =
    p.rating.map(r => s" ($r${if (p.provisional) "?" else ""}) ")

  def showBasePlayer(
      player: Player,
      withRank: Boolean = true,
      withFlag: Boolean = true,
      withLink: Boolean = false,
  )(implicit lang: Lang): Tag =
    showPlayer(
      player,
      withRank = withRank,
      withRating = false,
      withDiff = false,
      withOnline = false,
      withPowerTip = false,
      withFlag = withFlag,
      withLink = withLink,
      withModLink = false,
      withBerserk = false,
    )

  def showPlayer(
      player: Player,
      withRank: Boolean = true,
      withRating: Boolean = true,
      withDiff: Boolean = true,
      withOnline: Boolean = true,
      withPowerTip: Boolean = true,
      withFlag: Boolean = true,
      withLink: Boolean = true,
      withModLink: Boolean = false,
      withBerserk: Boolean = false,
  )(implicit lang: Lang): Tag = {
    player.userId
      .flatMap(lightUser)
      .fold {
        span(cls := "user-link")(
          player.engineConfig
            .fold(player.name.map(span(_)).getOrElse(anonSpan))(ec => engineSpan(ec)),
        )
      } { user =>
        val userCls = userClass(user.name, withOnline, withPowerTip)
        val tag =
          if (withLink) a(cls := userCls, href     := userUrl(user.name, mod = withModLink))
          else span(cls       := userCls, dataHref := userUrl(user.name, mod = withModLink))

        span(
          tag(
            withOnline ?? lineIcon(user),
            if (user.isBot) botTag
            else
              withRank ?? player.stableRating.map(r => rankTag(lila.rating.Rank.fromRating(r))),
            span(cls := "name")(user.name),
            user.countryCode.ifTrue(withFlag).map(c => flagImage(c)),
          ),
          withRating ?? ratingString(player),
          player.ratingDiff.ifTrue(withDiff).map(showRatingDiff),
          (withBerserk && player.berserk) option berserkIconSpan,
        )
      }
  }

  def gameEndStatus(game: Game)(implicit ctx: Context): String =
    game.status match {
      case S.Resign =>
        game.loserColor
          .map(l => transWithColorName(trans.xResigned, l, game.isHandicap))
          .getOrElse(trans.finished.txt())
      case S.Timeout =>
        game.loserColor
          .map(l => transWithColorName(trans.xLeftTheGame, l, game.isHandicap))
          .getOrElse(
            trans.draw.txt(),
          )
      case S.NoStart =>
        game.loserColor
          .map(l => transWithColorName(trans.xDidntMove, l, game.isHandicap))
          .getOrElse(trans.finished.txt())
      case s => statusName(s, game.variant.some)
    }

  def sourceName(source: Source)(implicit lang: Lang): String =
    source match {
      case Source.Lobby      => trans.lobby.txt()
      case Source.Friend     => trans.friends.txt()
      case Source.Ai         => trans.computer.txt()
      case Source.Api        => "API"
      case Source.Tournament => trans.tournaments.txt()
      case Source.Position   => trans.fromPosition.txt()
      case Source.Import     => trans.importCategory.txt()
      case Source.ImportLive => trans.broadcast.broadcasts.txt()
      case Source.Simul      => trans.simultaneousExhibitions.txt()
    }

  private def gameTitle(game: Game, color: Color)(implicit lang: Lang): String = {
    val u1 = playerText(game player color, withRank = true)
    val u2 = playerText(game opponent color, withRank = true)
    val clock = game.clock ?? { c =>
      " - " + c.config.show
    }
    val variant = !game.variant.standard ?? s" - ${variantName(game.variant)}"
    s"$u1 vs $u2$clock$variant"
  }

  def gameLink(
      game: Game,
      color: Color,
      me: Option[User],
      tv: Boolean = false,
  ): String = {
    if (tv) routes.Tv.index
    else
      me.ifTrue(game.playableEvenPaused || game.aborted)
        .flatMap(game.fullIdOf)
        .fold(
          routes.Round.watcher(game.id, color.name),
        ) { fullId =>
          routes.Round.player(fullId)
        }
  }.toString

  def gameLink(pov: Pov, me: Option[User]): String = gameLink(pov.game, pov.color, me)

  private def miniBoardCls(gameId: String, variant: Variant, isLive: Boolean): String =
    s"mini-board mini-board-${gameId} parse-sfen ${variantClass(variant)}${isLive ?? " live"}"

  def gameSfen(
      pov: Pov,
      me: Option[User],
      tv: Boolean = false,
      withTitle: Boolean = true,
      withLink: Boolean = true,
      withLive: Boolean = true,
  )(implicit ctx: Context): Frag = {
    val game    = pov.game
    val isLive  = withLive && game.isBeingPlayed
    val variant = game.variant
    val tag     = if (withLink) a else span
    tag(
      href        := withLink.option(gameLink(game, pov.color, me, tv)),
      title       := withTitle.option(gameTitle(game, pov.color)),
      cls         := miniBoardCls(game.id, variant, isLive),
      dataLive    := isLive.option(game.id),
      dataColor   := pov.color.name,
      dataSfen    := game.situation.toSfen.value,
      dataLastUsi := ~game.lastUsiStr,
      dataVariant := variant.key,
    )(shogigroundEmpty(variant, pov.color))
  }

  def gameSfenNoCtx(pov: Pov, tv: Boolean = false, blank: Boolean = false): Frag = {
    val isLive  = pov.game.isBeingPlayed
    val variant = pov.game.variant
    a(
      href      := (if (tv) routes.Tv.index else routes.Round.watcher(pov.gameId, pov.color.name)),
      title     := gameTitle(pov.game, pov.color)(defaultLang),
      cls       := miniBoardCls(pov.gameId, variant, isLive),
      dataLive  := isLive.option(pov.gameId),
      dataColor := pov.color.name,
      dataSfen  := pov.game.situation.toSfen.value,
      dataLastUsi := ~pov.game.lastUsiStr,
      dataVariant := variant.key,
      target      := blank.option("_blank"),
    )(shogigroundEmpty(variant, pov.color))
  }

  // Casual Shogi (10|0) - Challenge from Wanderer (1500)
  def challengeTitle(c: lila.challenge.Challenge)(implicit lang: Lang) = {
    val perf  = c.perfType.trans
    val clock = c.clock.map(_.config) ?? { clock => s" ${clock.show}" }
    val players =
      if (c.isOpen) trans.openChallenge.txt()
      else {
        val challenger = c.challengerUser.fold(trans.anonymousUser.txt()) { reg =>
          s"${usernameOrId(reg.id)} (${reg.rating.show})"
        }
        c.destUser.fold(trans.challengeFromX.txt(challenger)) { dest =>
          trans.xChallengesY.txt(challenger, s"${usernameOrId(dest.id)} (${dest.rating.show})")
        }
      }
    s"${modeName(c.mode)} ${perf}$clock - $players"
  }

  def challengeOpenGraph(c: lila.challenge.Challenge)(implicit lang: Lang) =
    lila.app.ui.OpenGraph(
      title = challengeTitle(c),
      url = s"$netBaseUrl${routes.Round.watcher(c.id, shogi.Sente.name).url}",
      description = trans.challengeDescription.txt(),
    )
}
