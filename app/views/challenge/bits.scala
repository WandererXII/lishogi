package views.html.challenge

import controllers.routes
import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.challenge.Challenge

object bits {

  def js(
      c: Challenge,
      json: play.api.libs.json.JsObject,
      owner: Boolean,
      color: Option[shogi.Color] = None,
  )(implicit
      ctx: Context,
  ) =
    frag(
      moduleJsTag(
        "challenge.page",
        Json.obj(
          "xhrUrl" -> routes.Challenge.show(c.id, color.map(_.name)).url,
          "owner"  -> owner,
          "data"   -> json,
        ),
      ),
    )

  def details(c: Challenge, mine: Boolean)(implicit ctx: Context) =
    div(cls := s"details${c.tourInfo.isDefined ?? " tour-challenge"}")(
      div(
        cls := "variant",
        dataIcon := (if (c.tourInfo.isDefined) Icons.trophy
                     else if (c.initialSfen.isDefined) Icons.position
                     else c.perfType.icon),
      )(
        div(
          views.html.game.bits.variantLink(c.variant, c.perfType.some),
          br,
          span(cls := "clock")(
            c.daysPerTurn map { days =>
              if (days == 1) trans.oneDay()
              else trans.nbDays.pluralSame(days)
            } getOrElse shortClockName(c.clock.map(_.config)),
          ),
        ),
      ),
      c.tourInfo.fold(
        div(cls := "game-color") {
          val handicap =
            c.initialSfen.fold(false)(sfen => shogi.Handicap.isHandicap(sfen, c.variant))
          frag(
            shogi.Color.fromName(c.colorChoice.toString.toLowerCase).fold(trans.randomColor.txt()) {
              color =>
                transWithColorName(trans.youPlayAsX, if (mine) color else !color, handicap)
            },
            " - ",
            transWithColorName(
              trans.xPlays,
              c.initialSfen.flatMap(_.color).getOrElse(shogi.Sente),
              handicap,
            ),
            ~c.proMode option small(cls := "pro-mode text", dataIcon := Icons.crown)(
              trans.proMode(),
            ),
          )
        },
      ) { tourInfo =>
        div(cls := "game-tour")(
          a(
            href := s"${routes.Tournament.show(tourInfo.tournamentId).url}#${arrangementIdToParam(tourInfo.arrangementId)}",
          )(tournamentIdToName(tourInfo.tournamentId)),
          tourInfo.withName option span(arrangementIdToName(tourInfo.arrangementId)),
          ~c.proMode option small(cls := "pro-mode text", dataIcon := Icons.crown)(trans.proMode()),
        )
      },
      div(cls := "mode")(modeName(c.mode)),
    )

  def failButton(c: Challenge)(implicit ctx: Context) =
    c.tourInfo.fold(
      a(cls := "button button-fat", href := routes.Lobby.home)(trans.newOpponent()),
    ) { tourInfo =>
      a(cls := "button button-fat", href := routes.Tournament.show(tourInfo.tournamentId).url)(
        trans.backToTournament(),
      )
    }
}
