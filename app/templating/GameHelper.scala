package lila.app
package templating

import shogi.{ Clock, Color, Mode, Status => S }
import controllers.routes
import play.api.i18n.Lang

import lila.api.Context
import lila.app.ui.ScalatagsTemplate._
import lila.game.{ Game, Namer, Player, Pov }
import lila.i18n.{ defaultLang, I18nKeys => trans }
import lila.user.{ Title, User }

trait GameHelper {
  self: I18nHelper
    with UserHelper
    with AiHelper
    with StringHelper
    with ShogigroundHelper
    with ColorNameHelper =>

  private val dataLive     = attr("data-live")
  private val dataColor    = attr("data-color")
  private val dataSfen     = attr("data-sfen")
  private val dataLastmove = attr("data-lastmove")
  private val dataVariant  = attr("data-variant")

  def netBaseUrl: String
  def cdnUrl(path: String): String

  def povOpenGraph(pov: Pov)(implicit lang: Lang) =
    lila.app.ui.OpenGraph(
      image = gameThumbnail(pov),
      title = titleGame(pov.game),
      url = s"$netBaseUrl${routes.Round.watcher(pov.gameId, pov.color.name).url}",
      description = describePov(pov)
    )

  def gameThumbnail(p: Pov) =
    p.game.variant.standard option cdnUrl(routes.Export.gameThumbnail(p.gameId).url)

  // Rapid Shogi - Dalliard vs Smith
  // Chushogi - PeterFile vs FilePeter
  def titleGame(g: Game)(implicit lang: Lang) = {
    val perf    = g.perfType ?? (_.trans)
    val variant = g.variant.standard ?? s" ${trans.shogi.txt()}"
    s"$perf$variant - ${playerText(g.sentePlayer)} vs ${playerText(g.gotePlayer)}"
  }

  // Beethoven played Handel - Rated Blitz Shogi (5+3) - Handel won! Click to replay, analyse, and discuss the game!
  def describePov(pov: Pov)(implicit lang: Lang) = {
    import pov._
    val sentePlayer = playerText(game.player(shogi.Sente), withRating = false)
    val gotePlayer = playerText(game.player(shogi.Gote), withRating = false)
    val players = if (game.finishedOrAborted) trans.xPlayedY.txt(sentePlayer, gotePlayer)
      else trans.xIsPlayingY.txt(sentePlayer, gotePlayer)
    val gameDesc =
      if (game.imported) trans.importedGame.txt()
      else
        List(
          modeName(game.mode),
          game.perfType ?? (_.trans),
          game.variant.standard ?? trans.shogi.txt(),
          game.clock.map(_.config) ?? { clock => s"(${clock.show})" }
        ).filter(_.nonEmpty).mkString(" ")
    val result = game.winner.map(w => trans.xWon.txt(playerText(w))).getOrElse(trans.gameWasDraw.txt())
    s"$players - $gameDesc - $result ${trans.clickGame.txt()}"
  }

  def shortClockName(clock: Option[Clock.Config])(implicit lang: Lang): Frag =
    clock.fold[Frag](trans.unlimited())(shortClockName)

  def shortClockName(clock: Clock.Config): Frag = raw(clock.show)

  def modeName(mode: Mode)(implicit lang: Lang): String =
    mode match {
      case Mode.Casual => trans.casual.txt()
      case Mode.Rated  => trans.rated.txt()
    }

  def variantName(v: shogi.variant.Variant)(implicit lang: Lang): String =
    v match {
      case shogi.variant.Minishogi  => trans.minishogi.txt()
      case shogi.variant.Chushogi   => trans.chushogi.txt()
      case shogi.variant.Annanshogi => trans.annanshogi.txt()
      case shogi.variant.Kyotoshogi => trans.kyotoshogi.txt()
      case _                        => trans.standard.txt()
    }

  private def variantNameOrShogi(v: shogi.variant.Variant)(implicit lang: Lang): String =
    if (v.standard) trans.shogi.txt()
    else variantName(v)

  def variantIcon(v: shogi.variant.Variant): String =
    v match {
      case shogi.variant.Minishogi  => ","
      case shogi.variant.Chushogi   => "("
      case shogi.variant.Annanshogi => ""
      case shogi.variant.Kyotoshogi => ""
      case _                        => "C"
    }

  def playerUsername(player: Player, withRating: Boolean = true, withTitle: Boolean = true): Frag =
    player.aiLevel.fold[Frag](
      player.userId.flatMap(lightUser).fold[Frag](lila.user.User.anonymous) { user =>
        val title = user.title ifTrue withTitle map { t =>
          frag(
            span(
              cls := "title",
              (Title(t) == Title.BOT) option dataBotAttr,
              st.title := Title titleName Title(t)
            )(t),
            " "
          )
        }
        if (withRating) frag(title, user.name, " ", "(", lila.game.Namer ratingString player, ")")
        else frag(title, user.name)
      }
    ) { level =>
      raw(s"A.I. level $level")
    }

  def playerText(player: Player, withRating: Boolean = false) =
    Namer.playerTextBlocking(player, withRating)(lightUser)

  def gameVsText(game: Game, withRatings: Boolean = false): String =
    Namer.gameVsTextBlocking(game, withRatings)(lightUser)

  val berserkIconSpan = iconTag("`")
  val statusIconSpan  = i(cls := "status")

  def playerLink(
      player: Player,
      cssClass: Option[String] = None,
      withOnline: Boolean = true,
      withRating: Boolean = true,
      withDiff: Boolean = true,
      engine: Boolean = false,
      withStatus: Boolean = false,
      withBerserk: Boolean = false,
      mod: Boolean = false,
      link: Boolean = true
  )(implicit lang: Lang): Frag = {
    val statusIcon =
      if (withStatus) statusIconSpan.some
      else if (withBerserk && player.berserk) berserkIconSpan.some
      else none
    player.userId.flatMap(lightUser) match {
      case None =>
        val klass = cssClass.??(" " + _)
        span(cls := s"user-link$klass")(
          (player.aiLevel, player.name) match {
            case (Some(level), _) => aiNameFrag(level, withRating)
            case (_, Some(name))  => name
            case _                => User.anonymous
          },
          statusIcon
        )
      case Some(user) =>
        frag(
          (if (link) a else span) (
            cls  := userClass(user.id, cssClass, withOnline),
            href := s"${routes.User show user.name}${if (mod) "?mod" else ""}"
          )(
            withOnline option frag(lineIcon(user), " "),
            playerUsername(player, withRating),
            (player.ratingDiff ifTrue withDiff) map { d =>
              frag(" ", showRatingDiff(d))
            },
            engine option span(
              cls   := "tos_violation",
              title := trans.thisAccountViolatedTos.txt()
            )
          ),
          statusIcon
        )
    }
  }

  def gameEndStatus(game: Game)(implicit ctx: Context): String =
    game.status match {
      case S.Aborted => trans.gameAborted.txt()
      case S.Mate    => trans.checkmate.txt()
      case S.Resign =>
        game.loserColor
          .map(l => transWithColorName(trans.xResigned, l, game.isHandicap))
          .getOrElse(trans.finished.txt())
      case S.UnknownFinish  => trans.finished.txt()
      case S.Stalemate      => trans.stalemate.txt()
      case S.TryRule        => "Try Rule" // games before July 2021 might still have this status
      case S.Impasse27      => trans.impasse.txt()
      case S.PerpetualCheck => trans.perpetualCheck.txt()
      case S.RoyalsLost     => trans.royalsLost.txt()
      case S.BareKing       => trans.bareKing.txt()
      case S.Timeout =>
        game.loserColor
          .map(l => transWithColorName(trans.xLeftTheGame, l, game.isHandicap))
          .getOrElse(
            trans.draw.txt()
          )
      case S.Repetition => trans.repetition.txt()
      case S.Draw       => trans.draw.txt()
      case S.Outoftime  => trans.timeOut.txt()
      case S.NoStart =>
        game.loserColor
          .map(l => transWithColorName(trans.xDidntMove, l, game.isHandicap))
          .getOrElse(trans.finished.txt())
      case S.Cheat => trans.cheatDetected.txt()
      case _       => ""
    }

  private def gameTitle(game: Game, color: Color)(implicit lang: Lang): String = {
    val u1 = playerText(game player color, withRating = true)
    val u2 = playerText(game opponent color, withRating = true)
    val clock = game.clock ?? { c =>
      " - " + c.config.show
    }
    val variant = !game.variant.standard ?? s" - ${variantName(game.variant)}"
    s"$u1 vs $u2$clock$variant"
  }

  def gameLink(
      game: Game,
      color: Color,
      ownerLink: Boolean = false,
      tv: Boolean = false
  )(implicit ctx: Context): String = {
    val owner = ownerLink ?? ctx.me.flatMap(game.player)
    if (tv) routes.Tv.index
    else
      owner.fold(routes.Round.watcher(game.id, color.name)) { o =>
        routes.Round.player(game fullIdOf o.color)
      }
  }.toString

  def gameLink(pov: Pov)(implicit ctx: Context): String = gameLink(pov.game, pov.color)

  def gameSfen(
      pov: Pov,
      ownerLink: Boolean = false,
      tv: Boolean = false,
      withTitle: Boolean = true,
      withLink: Boolean = true,
      withLive: Boolean = true
  )(implicit ctx: Context): Frag = {
    val game     = pov.game
    val isLive   = withLive && game.isBeingPlayed
    val cssClass = isLive ?? ("live mini-board-" + game.id)
    val variant  = game.variant
    val tag      = if (withLink) a else span
    tag(
      href  := withLink.option(gameLink(game, pov.color, ownerLink, tv)),
      title := withTitle.option(gameTitle(game, pov.color)),
      cls :=
        s"mini-board mini-board-${game.id} sg-wrap parse-sfen $cssClass d-${variant.numberOfFiles}x${variant.numberOfRanks}",
      dataLive     := isLive.option(game.id),
      dataColor    := pov.color.name,
      dataSfen     := game.situation.toSfen.value,
      dataLastmove := ~game.lastMoveKeys,
      dataVariant  := game.variant.key
    )(sgWrapContent)
  }

  def gameSfenNoCtx(pov: Pov, tv: Boolean = false, blank: Boolean = false): Frag = {
    val isLive  = pov.game.isBeingPlayed
    val variant = pov.game.variant
    a(
      href  := (if (tv) routes.Tv.index else routes.Round.watcher(pov.gameId, pov.color.name)),
      title := gameTitle(pov.game, pov.color)(defaultLang),
      cls := List(
        s"mini-board mini-board-${pov.gameId} sg-wrap parse-sfen d-${variant.numberOfFiles}x${variant.numberOfRanks}" -> true,
        s"live mini-board-${pov.gameId}" -> isLive
      ),
      dataLive     := isLive.option(pov.gameId),
      dataColor    := pov.color.name,
      dataSfen     := pov.game.situation.toSfen.value,
      dataLastmove := ~pov.game.lastMoveKeys,
      dataVariant  := pov.game.variant.key,
      target       := blank.option("_blank")
    )(sgWrapContent)
  }

  // Casual Rapid Shogi (10|0) - Challenge from Wanderer (1500)
  def challengeTitle(c: lila.challenge.Challenge)(implicit lang: Lang) = {
    val perf    = c.perfType.trans
    val variant = c.variant.standard ?? s" ${trans.shogi.txt()}"
    val clock   = c.clock.map(_.config) ?? { clock => s" ${clock.show}" }
    val players =
      if (c.isOpen) trans.openChallenge.txt()
      else {
        val challenger = c.challengerUser.fold(trans.anonymous.txt()) { reg =>
          s"${usernameOrId(reg.id)} (${reg.rating.show})"
        }
        c.destUser.fold(trans.challengeFromX.txt(challenger)) { dest =>
          trans.xChallengesY.txt(challenger, s"${usernameOrId(dest.id)} (${dest.rating.show})")
        }
      }
    s"${modeName(c.mode)} ${perf}${variant}$clock - $players"
  }

  def challengeOpenGraph(c: lila.challenge.Challenge)(implicit lang: Lang) =
    lila.app.ui.OpenGraph(
      title = challengeTitle(c),
      url = s"$netBaseUrl${routes.Round.watcher(c.id, shogi.Sente.name).url}",
      description = trans.challengeDescription.txt()
    )
}
