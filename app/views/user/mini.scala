package views.html.user

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.user.User

object mini {

  def apply(
      u: User,
      playing: Option[lila.game.Pov],
      blocked: Boolean,
      followable: Boolean,
      rel: Option[lila.relation.Relation],
      ping: Option[Int],
      crosstable: Option[lila.game.Crosstable],
  )(implicit ctx: Context) =
    frag(
      div(cls := "upt__info")(
        div(cls := "upt__info__top")(
          div(cls := "left")(
            showUsername(u, withPowerTip = false),
            u.title
              .ifTrue(u.noBot)
              .map(t => frag(" - ", span(cls := "official-title")(lila.user.Title.trans(t)))),
          ),
          ping map bits.signalBars,
        ),
        if (u.lame && !ctx.me.has(u) && !isGranted(_.UserSpy))
          div(cls := "upt__info__warning")(trans.thisAccountViolatedTos())
        else
          div(cls := "upt__info__ratings")(u.best8Perfs map { pt =>
            span(cls := "text", dataIcon := pt.icon)(
              rankTag(u.perfs(pt), withUnknown = true),
            )
          }),
      ),
      ctx.userId map { myId =>
        frag(
          (myId != u.id && u.enabled) option div(cls := "upt__actions btn-rack")(
            a(
              dataIcon := Icons.television,
              cls      := "btn-rack__btn",
              title    := trans.watchGames.txt(),
              href     := routes.User.tv(u.username),
            ),
            !blocked option frag(
              a(
                dataIcon := Icons.talk,
                cls      := "btn-rack__btn",
                title    := trans.chat.txt(),
                href     := routes.Msg.convo(u.username),
              ),
              a(
                dataIcon := Icons.challenge,
                cls      := "btn-rack__btn",
                title    := trans.challengeToPlay.txt(),
                href     := s"${routes.Lobby.home}?user=${u.username}#friend",
              ),
            ),
            views.html.relation.mini(u.id, blocked, followable, rel),
          ),
          crosstable.flatMap(_.nonEmpty) map { cross =>
            a(
              cls   := "upt__score",
              href  := s"${routes.User.games(u.username, "me")}#games",
              title := trans.nbGames.pluralTxt(cross.nbGames, cross.nbGames.localize),
            )(trans.yourScore(raw(s"""<strong>${cross.showScore(myId)}</strong> - <strong>${~cross
                .showOpponentScore(myId)}</strong>""")))
          },
        )
      },
      isGranted(_.UserSpy) option div(cls := "upt__mod")(
        trans.nbGames.plural(u.count.game, u.count.game.localize),
        momentFromNowOnce(u.createdAt),
        (u.lameOrTroll || u.disabled) option span(cls := "upt__mod__marks")(mod.userMarks(u, None)),
      ),
      playing map { pov =>
        frag(
          gameSfen(pov),
          div(cls := "upt__game-legend")(
            i(dataIcon := pov.game.perfType.icon, cls := "text")(
              pov.game.clock.map(_.config.show),
            ),
            showBasePlayer(pov.opponent),
          ),
        )
      },
    )
}
