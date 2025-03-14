package views.html.study

import controllers.routes
import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object show {

  def apply(
      sc: lila.study.Study.WithChapter,
      data: lila.study.JsonView.JsData,
      chatOption: Option[lila.chat.UserChat.Mine],
      socketVersion: lila.socket.Socket.SocketVersion,
      streams: List[lila.streamer.Stream],
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = sc.study.name.value,
      moreCss = cssTag("analyse.study"),
      moreJs = frag(
        ctx.blind option analyseNvuiTag,
        moduleJsTag(
          "analyse",
          Json.obj(
            "mode"     -> "study",
            "study"    -> data.study.add("admin" -> isGranted(_.StudyAdmin)),
            "data"     -> data.analysis,
            "tagTypes" -> lila.study.StudyTags.typesToString,
            "userId"   -> ctx.userId,
            "chat" -> chatOption.map { c =>
              views.html.chat.json(
                c.chat,
                name = trans.chatRoom.txt(),
                timeout = c.timeout,
                writeable = ctx.userId exists sc.study.canChat,
                public = true,
                resourceId = lila.chat.Chat.ResourceId(s"study/${c.chat.id}"),
                palantir = ctx.userId exists sc.study.isMember,
                localMod = ctx.userId exists sc.study.canContribute,
              )
            },
            "socketUrl"     -> socketUrl(sc.study.id.value),
            "socketVersion" -> socketVersion.value,
          ),
        ),
      ),
      robots = sc.study.isPublic,
      shogiground = false,
      zoomable = true,
      csp = defaultCsp.withWebAssembly.withPeer.some,
      openGraph = lila.app.ui
        .OpenGraph(
          title = sc.study.name.value,
          url = s"$netBaseUrl${routes.Study.show(sc.study.id.value).url}",
          description = trans.study.studyByX.txt(usernameOrId(sc.study.ownerId)),
        )
        .some,
      canonicalPath =
        lila.common.CanonicalPath(routes.Study.chapter(sc.study.id.value, sc.chapter.id.value)).some,
    )(
      frag(
        main(cls := "analyse"),
        bits.streamers(streams),
      ),
    )

  def socketUrl(id: String) = s"/study/$id/socket/v5"
}
