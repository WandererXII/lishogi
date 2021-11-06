package views.html.challenge

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.challenge.Challenge
import lishogi.challenge.Challenge.Status

import controllers.routes

object theirs {

  def apply(
      c: Challenge,
      json: play.api.libs.json.JsObject,
      user: Option[lishogi.user.User],
      color: Option[shogi.Color]
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = challengeTitle(c),
      openGraph = challengeOpenGraph(c).some,
      moreJs = bits.js(c, json, false, color),
      moreCss = cssTag("challenge.page")
    ) {
      main(cls := "page-small challenge-page challenge-theirs box box-pad")(
        c.status match {
          case Status.Created | Status.Offline =>
            frag(
              h1(
                if (c.isOpen) "Open Challenge"
                else
                  user.fold[Frag]("Anonymous")(u =>
                    frag(
                      userLink(u),
                      " (",
                      u.perfs(c.perfType).glicko.display,
                      ")"
                    )
                  )
              ),
              bits.details(c),
              c.notableInitialFen.map { fen =>
                div(cls := "board-preview", views.html.game.bits.miniBoard(fen, color = !c.finalColor))
              },
              if (color.map(Challenge.ColorChoice.apply).has(c.colorChoice))
                badTag(
                  // very rare message, don't translate
                  s"You have the wrong color link for this open challenge. The ${color.??(_.name)} player has already joined."
                )
              else if (!c.mode.rated || ctx.isAuth) {
                frag(
                  (c.mode.rated && c.unlimited) option
                    badTag(trans.bewareTheGameIsRatedButHasNoClock()),
                  postForm(cls := "accept", action := routes.Challenge.accept(c.id, color.map(_.name)))(
                    submitButton(cls := "text button button-fat", dataIcon := "G")(trans.joinTheGame())
                  )
                )
              } else
                frag(
                  hr,
                  badTag(
                    p("This game is rated"),
                    p(
                      "You must ",
                      a(
                        cls := "button",
                        href := s"${routes.Auth.login()}?referrer=${routes.Round.watcher(c.id, "sente")}"
                      )(trans.signIn()),
                      " to join it."
                    )
                  )
                )
            )
          case Status.Declined =>
            div(cls := "follow-up")(
              h1("Challenge declined"),
              bits.details(c),
              a(cls := "button button-fat", href := routes.Lobby.home())(trans.newOpponent())
            )
          case Status.Accepted =>
            div(cls := "follow-up")(
              h1("Challenge accepted!"),
              bits.details(c),
              a(
                id := "challenge-redirect",
                href := routes.Round.watcher(c.id, "sente"),
                cls := "button button-fat"
              )(
                trans.joinTheGame()
              )
            )
          case Status.Canceled =>
            div(cls := "follow-up")(
              h1("Challenge canceled."),
              bits.details(c),
              a(cls := "button button-fat", href := routes.Lobby.home())(trans.newOpponent())
            )
        }
      )
    }
}
