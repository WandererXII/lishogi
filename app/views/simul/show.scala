package views.html.simul

import play.api.libs.json.Json

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.common.String.html.safeJsonValue

import controllers.routes

object show {

  def apply(
      sim: lishogi.simul.Simul,
      socketVersion: lishogi.socket.Socket.SocketVersion,
      data: play.api.libs.json.JsObject,
      chatOption: Option[lishogi.chat.UserChat.Mine],
      stream: Option[lishogi.streamer.Stream],
      team: Option[lishogi.team.Team]
  )(implicit ctx: Context) =
    views.html.base.layout(
      moreCss = cssTag("simul.show"),
      title = sim.fullName,
      moreJs = frag(
        jsModule("simul"),
        embedJsUnsafe(s"""lishogi.simul=${safeJsonValue(
          Json.obj(
            "data"          -> data,
            "i18n"          -> bits.jsI18n(),
            "socketVersion" -> socketVersion.value,
            "userId"        -> ctx.userId,
            "chat" -> chatOption.map { c =>
              views.html.chat.json(
                c.chat,
                name = trans.chatRoom.txt(),
                timeout = c.timeout,
                public = true,
                resourceId = lishogi.chat.Chat.ResourceId(s"simul/${c.chat.id}")
              )
            }
          )
        )}""")
      )
    ) {
      main(
        cls := List(
          "simul"         -> true,
          "simul-created" -> sim.isCreated
        )
      )(
        st.aside(cls := "simul__side")(
          div(cls := "simul__meta")(
            div(cls := "game-infos")(
              div(cls := "header")(
                iconTag("f"),
                div(
                  span(cls := "clock")(sim.clock.config.show),
                  div(cls := "setup")(
                    sim.variants.map(_.name).mkString(", "),
                    " • ",
                    trans.casual()
                  )
                )
              ),
              trans.simulHostExtraTime(),
              ": ",
              pluralize("minute", sim.clock.hostExtraMinutes),
              br,
              trans.hostColorX(sim.color match {
                case Some("gote")  => trans.white.txt() + "/" + trans.uwate.txt()
                case Some("sente") => trans.black.txt() + "/" + trans.shitate.txt()
                case _             => trans.randomColor()
              }),
              sim.position map { pos =>
                frag(
                  br,
                  a(target := "_blank", rel := "noopener", href := pos.url)(
                    strong(pos.eco),
                    " ",
                    pos.name
                  ),
                  " • ",
                  a(href := routes.UserAnalysis.parseArg(pos.fen.replace(" ", "_")))(
                    trans.analysis()
                  )
                )
              }
            ),
            trans.by(userIdLink(sim.hostId.some)),
            team map { t =>
              frag(
                br,
                trans.mustBeInTeam(a(href := routes.Team.show(t.id))(t.name))
              )
            }
          ),
          stream.map { s =>
            views.html.streamer.bits.contextual(s.streamer.userId)
          },
          chatOption.isDefined option views.html.chat.frag
        ),
        div(cls := "simul__main box")(spinner)
      )
    }
}
