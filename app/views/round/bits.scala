package views.html
package round

import scala.util.chaining._

import controllers.routes

import shogi.variant.Variant

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.Game
import lila.game.Pov

object bits {

  def layout(
      variant: Variant,
      title: String,
      moreJs: Frag = emptyFrag,
      openGraph: Option[lila.app.ui.OpenGraph] = None,
      moreCss: Frag = emptyFrag,
      shogiground: Boolean = true,
      playing: Boolean = false,
      robots: Boolean = false,
      withHrefLangs: Option[lila.i18n.LangList.AlternativeLangs] = None,
  )(body: Frag)(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      openGraph = openGraph,
      moreJs = moreJs,
      moreCss = frag(
        cssTag("round"),
        ctx.blind option cssTag("round.nvui"),
        variantPieceSprite(variant),
        moreCss,
      ),
      shogiground = shogiground,
      playing = playing,
      robots = robots,
      zoomable = true,
      csp = defaultCsp.withPeer.some,
      withHrefLangs = withHrefLangs,
    )(body)

  def crosstable(cross: Option[lila.game.Crosstable.WithMatchup], game: Game)(implicit
      ctx: Context,
  ) =
    cross map { c =>
      views.html.game.crosstable(ctx.userId.fold(c)(c.fromPov), game.id.some)
    }

  def underchat(game: Game)(implicit ctx: Context) =
    frag(
      views.html.chat.members,
      isGranted(_.ViewBlurs) option div(cls := "round__mod")(
        game.players.filter(p => game.playerBlurPercent(p.color) > 30) map { p =>
          div(cls := s"is color-icon ${p.color.name}")(
            showPlayer(
              p,
              withOnline = false,
              withModLink = true,
              withFlag = false,
            ),
            s" ${p.blurs.nb}/${game.playerMoves(p.color)} blurs ",
            strong(game.playerBlurPercent(p.color), "%"),
          )
        },
      ),
    )

  def others(playing: List[Pov], simul: Option[lila.simul.Simul])(implicit ctx: Context) =
    frag(
      h3(
        simul.map { s =>
          span(cls := "simul")(
            a(href := routes.Simul.show(s.id))("SIMUL"),
            span(cls := "win")(s.wins, " W"),
            " / ",
            span(cls := "draw")(s.draws, " D"),
            " / ",
            span(cls := "loss")(s.losses, " L"),
            " / ",
            s.ongoing,
            " ongoing",
          )
        } getOrElse trans.currentGames(),
        "round-toggle-autoswitch" pipe { id =>
          span(
            cls      := "move-on switcher",
            st.title := trans.automaticallyProceedToNextGameAfterMoving.txt(),
          )(
            label(`for` := id)(trans.autoSwitch()),
            span(cls := "switch")(form3.cmnToggle(id, id, false)),
          )
        },
      ),
      div(cls := "now-playing")(
        playing.partition(_.isMyTurn) pipe { case (myTurn, otherTurn) =>
          (myTurn ++ otherTurn.take(6 - myTurn.size)) take 9 map { pov =>
            a(href := routes.Round.player(pov.fullId), cls := pov.isMyTurn.option("my_turn"))(
              gameSfen(pov, ctx.me, withLink = false, withTitle = false, withLive = false),
              span(cls := "meta")(
                showBasePlayer(pov.opponent),
                span(cls := "indicator")(
                  if (pov.isMyTurn)
                    pov.remainingSeconds.fold[Frag](trans.yourTurn())(secondsFromNow(_, true))
                  else nbsp,
                ),
              ),
            )
          }
        },
      ),
    )

  private[round] def side(
      pov: Pov,
      tour: Option[lila.tournament.TourAndTeamVs],
      simul: Option[lila.simul.Simul],
      userTv: Option[lila.user.User] = None,
      backToGame: Option[lila.game.Player] = None,
      bookmarked: Boolean,
  )(implicit ctx: Context) =
    views.html.game.side(
      pov,
      tour,
      simul = simul,
      userTv = userTv,
      backToGame = backToGame,
      bookmarked = bookmarked,
    )

  private def roundAppClasses(implicit ctx: Context) = List(
    "round__app"     -> true,
    "compact-layout" -> (ctx.pref.boardLayout == 1),
    "small-moves"    -> (ctx.pref.boardLayout == 2),
  )

  def roundAppPreload(pov: Pov, controls: Boolean)(implicit ctx: Context) =
    div(cls := roundAppClasses)(
      div(cls := s"round__app__board main-board ${variantClass(pov.game.variant)}")(
        shogiground(pov),
      ),
      div(cls := "round__app__table"),
      div(cls := "ruser ruser-top user-link")(
        i(cls := "line"),
        a(cls := "text")(playerText(pov.opponent, withRank = true)),
      ),
      div(cls := "ruser ruser-bottom user-link")(
        i(cls := "line"),
        a(cls := "text")(playerText(pov.player, withRank = true)),
      ),
      div(cls := "rclock rclock-top preload")(div(cls := "clock-byo")(nbsp)),
      div(cls := "rclock rclock-bottom preload")(div(cls := "clock-byo")(nbsp)),
      div(cls := "rmoves")(div(cls := "moves")),
      controls option div(cls := "rcontrols")(i(cls := "ddloader")),
    )
}
