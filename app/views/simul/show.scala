package views.html.simul

import controllers.routes
import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object show {

  def apply(
      sim: lila.simul.Simul,
      socketVersion: lila.socket.Socket.SocketVersion,
      data: play.api.libs.json.JsObject,
      chatOption: Option[lila.chat.Chat.Mine],
      stream: Option[lila.streamer.Stream],
      team: Option[lila.team.Team],
  )(implicit ctx: Context) =
    views.html.base.layout(
      moreCss = cssTag("simul.show"),
      title = trans.xSimul.txt(sim.name),
      moreJs = frag(
        moduleJsTag(
          "simul",
          Json.obj(
            "data"          -> data,
            "socketVersion" -> socketVersion.value,
            "userId"        -> ctx.userId,
            "chat" -> chatOption.map { c =>
              views.html.chat.json(
                c.chat,
                name = trans.chatRoom.txt(),
                timeout = c.timeout,
                resourceId = lila.chat.Chat.ResourceId(s"simul/${c.chat.id}"),
                localMod = ctx.userId has sim.hostId,
              )
            },
          ),
        ),
      ),
    ) {
      val variantOpt    = sim.variants.headOption.ifTrue(sim.variants.size == 1)
      val variantForced = variantOpt.getOrElse(shogi.variant.Standard)
      val handicapOpt = (for {
        variant <- variantOpt
        sfen    <- sim.position
        handicap <- shogi.Handicap.allByVariant
          .get(variant)
          .flatMap(hs => hs.find(_.sfen.truncate == sfen.truncate))
      } yield (handicap))
      main(cls := "simul")(
        st.aside(cls := "simul__side")(
          div(cls := "simul__meta")(
            div(cls := "game-infos")(
              div(cls := "header")(
                iconTag(Icons.people),
                div(
                  span(cls := "clock")(sim.clock.config.show),
                  div(cls := "setup")(
                    sim.variants.map(v => variantName(v)).mkString(", "),
                    " - ",
                    trans.casual(),
                    (isGranted(_.ManageSimul) || ctx.userId
                      .has(sim.hostId)) && sim.isCreated option frag(
                      " - ",
                      a(href := routes.Simul.edit(sim.id), title := trans.edit.txt())(
                        iconTag(Icons.gear),
                      ),
                    ),
                  ),
                ),
              ),
              ~sim.proMode option div(cls := "text", dataIcon := Icons.crown)(
                trans.proMode(),
              ),
              trans.simulHostExtraTime(),
              ": ",
              trans.nbMinutes.pluralSameTxt(sim.clock.hostExtraMinutes),
              br,
              trans.hostColorX(sim.hostColor match {
                case Some(color) => {
                  if (handicapOpt.isDefined) handicapColorName(color)
                  else standardColorName(color)
                }
                case _ => trans.randomColor()
              }),
              handicapOpt map { h =>
                frag(
                  br,
                  strong(h.japanese),
                  " ",
                  h.english,
                  " - ",
                  views.html.base.bits.sfenAnalysisLink(variantForced, h.sfen),
                )
              } orElse sim.position.map { sfen =>
                frag(
                  br,
                  trans.fromPosition(),
                  " - ",
                  views.html.base.bits.sfenAnalysisLink(variantForced, sfen),
                )
              },
            ),
            trans.by(showUsernameById(sim.hostId.some)),
            team map { t =>
              frag(
                br,
                trans.mustBeInTeam(a(href := routes.Team.show(t.id))(t.name)),
              )
            },
            sim.estimatedStartAt map { d =>
              frag(
                br,
                absClientDateTime(d),
              )
            },
          ),
          stream.map { s =>
            views.html.streamer.bits.contextualWrap(s.streamer.userId)
          },
          chatOption.isDefined option views.html.chat.frag,
        ),
        div(cls := "simul__main box")(spinner),
      )
    }
}
