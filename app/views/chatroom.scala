package views.html

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object chatroom {

  def show(
      chat: lila.chat.Chat.Mine,
      socketVersion: lila.socket.Socket.SocketVersion,
  )(implicit
      ctx: Context,
  ) =
    views.html.base.layout(
      title = trans.chatRoom.txt(),
      wrapClass = "full-screen-force",
      moreJs = moduleJsTag(
        "misc.chatroom",
        Json.obj(
          "socketVersion" -> socketVersion.value,
          "chat" -> views.html.chat.json(
            chat.chat,
            name = trans.chatRoom.txt(),
            timeout = chat.timeout,
            resourceId = lila.chat.Chat.ResourceId(s"chatroom/${chat.chat.id}"),
            palantir = false,
          ),
        ),
      ),
      moreCss = cssTag("misc.chatroom"),
    ) {
      main(
        cls := "chat-room box",
      )(
        div(cls := "box__top")(
          h1(cls := "text", dataIcon := Icons.teapot)(trans.chatRoom()),
        ),
        views.html.chat.members,
        div(cls := "chat-room__content")(
          views.html.chat.frag,
        ),
      )
    }

}
