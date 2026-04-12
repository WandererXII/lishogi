package views.html.challenge

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.challenge.Challenge.Status

object mine {

  def apply(c: lila.challenge.Challenge, json: play.api.libs.json.JsObject, error: Option[String])(
      implicit ctx: Context,
  ) = {

    val cancelForm =
      postForm(action := routes.Challenge.cancel(c.id), cls := "cancel xhr")(
        submitButton(cls := "button button-red text", dataIcon := Icons.cancel)(trans.cancel()),
      )

    views.html.base.layout(
      title = challengeTitle(c),
      openGraph = challengeOpenGraph(c).some,
      moreJs = bits.js(c, json, true),
      moreCss = cssTag("challenge.page"),
    ) {
      val challengeLink = s"$netBaseUrl${routes.Round.gameOrChallengeDefault(c.id)}"
      main(cls := "page-small challenge-page box box-pad")(
        c.status match {
          case Status.Created | Status.Offline =>
            div(id := "ping-challenge")(
              h1(if (c.isOpen) trans.openChallenge.txt() else trans.challengeToPlay.txt()),
              bits.details(c, true),
              c.destUserId.map { destId =>
                div(cls := "waiting")(
                  showUsernameById(destId.some),
                  span(trans.waitingForOpponent()),
                  i(cls := "ddloader"),
                )
              } getOrElse {
                if (c.isOpen)
                  div(cls := "waiting waiting-open")(
                    span(trans.waitingForOpponent()),
                    i(cls := "ddloader"),
                  )
                else
                  div(cls := "invite")(
                    div(
                      h2(cls := "ninja-title", trans.toInviteSomeoneToPlayGiveThisUrl(), ": "),
                      br,
                      p(cls := "challenge-id-form")(
                        input(
                          id         := "challenge-id",
                          cls        := "copyable autoselect",
                          spellcheck := "false",
                          readonly,
                          value := challengeLink,
                        ),
                        button(
                          title    := "Copy URL",
                          cls      := "copy button",
                          dataRel  := "challenge-id",
                          dataIcon := Icons.copy,
                        ),
                      ),
                      p(trans.theFirstPersonToComeOnThisUrlWillPlayWithYou()),
                    ),
                    ctx.isAuth option div(
                      h2(cls := "ninja-title", "Or invite a Lishogi user:"),
                      br,
                      postForm(cls := "user-invite", action := routes.Challenge.toFriend(c.id))(
                        input(
                          name        := "username",
                          cls         := "friend-autocomplete",
                          placeholder := trans.search.search.txt(),
                        ),
                        error.map { badTag(_) },
                      ),
                    ),
                  )
              },
              c.initialSfen.map { sfen =>
                frag(
                  br,
                  div(
                    cls := "board-preview",
                    views.html.game.bits.miniBoard(sfen, color = c.finalColor, variant = c.variant),
                  ),
                )
              },
              !c.isOpen option cancelForm,
              !c.hasClock option div(cls := "corres-desc")(
                trans.challengeCorrespondenceDescription(),
              ),
            )
          case Status.Declined =>
            div(cls := "follow-up")(
              h1(trans.challengeDeclined()),
              bits.details(c, true),
              bits.failButton(c),
            )
          case Status.Accepted =>
            div(cls := "follow-up")(
              h1(trans.challengeAccepted()),
              bits.details(c, true),
              a(
                id   := "challenge-redirect",
                href := routes.Round.gameOrChallengeDefault(c.id),
                cls  := "button-fat",
              )(
                trans.joinTheGame(),
              ),
            )
          case Status.Canceled =>
            div(cls := "follow-up")(
              h1(trans.challengeCanceled()),
              bits.details(c, true),
              bits.failButton(c),
            )
        },
      )
    }
  }
}
