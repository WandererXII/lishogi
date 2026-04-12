package views.html

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object chat {

  val frag = st.section(cls := "mchat")(
    div(cls := "mchat__tabs")(
      div(cls := "mchat__tab")(nbsp),
    ),
    div(cls := "mchat__content"),
  )

  import lila.chat.JsonView.writers.chatIdWrites

  def gameJson(
      gameChat: lila.chat.Chat.Game,
      game: lila.game.Game,
      loginRequired: Boolean = true,
  )(implicit ctx: Context) =
    json(
      gameChat.chat,
      name = trans.chatRoom.txt(),
      timeout = gameChat.timeout,
      withNoteAge = ctx.isAuth option game.secondsSinceCreation,
      writeable = true,
      resourceId = lila.chat.Chat.ResourceId(s"game/${gameChat.chat.id}"),
      restricted = gameChat.restricted,
      loginRequired = loginRequired,
      localMod = false,
      palantir = ctx.me.exists(_.canPalantir),
    ) ++ Json
      .obj(
        "players" ->
          Json
            .obj()
            .add("sente" -> game.player(shogi.Sente).userId)
            .add("sente" -> game.player(shogi.Gote).userId),
      )
      .add("finished" -> game.finished)

  def json(
      chat: lila.chat.Chat,
      name: String,
      timeout: Boolean,
      resourceId: lila.chat.Chat.ResourceId,
      withNoteAge: Option[Int] = None,
      writeable: Boolean = true,
      restricted: Boolean = false,
      loginRequired: Boolean = true,
      localMod: Boolean = false,
      palantir: Boolean = false,
  )(implicit ctx: Context) =
    Json
      .obj(
        "data" -> Json
          .obj(
            "id"         -> chat.id,
            "name"       -> name,
            "lines"      -> lila.chat.JsonView(chat),
            "userId"     -> ctx.userId,
            "resourceId" -> resourceId.value,
          )
          .add("loginRequired" -> loginRequired)
          .add("restricted" -> restricted)
          .add("palantir" -> (palantir && ctx.isAuth)),
        "writeable" -> writeable,
        "permissions" -> Json
          .obj("local" -> localMod)
          .add("timeout" -> isGranted(_.ChatTimeout))
          .add("shadowban" -> isGranted(_.Shadowban)),
      )
      .add("kobold" -> ctx.troll)
      .add("blind" -> ctx.blind)
      .add("timeout" -> timeout)
      .add("noteId" -> (withNoteAge.isDefined && ctx.noBlind).option(chat.id.value take 8))
      .add("noteAge" -> withNoteAge)
      .add("timeoutReasons" -> isGranted(_.ChatTimeout).option(lila.chat.JsonView.timeoutReasons))

  def members =
    div(
      cls       := "chat__members none",
      aria.live := "off",
    )(
      div(cls := "line default none")(
        span(cls := "number", dataIcon := Icons.person)("0"),
        " ",
        span(cls := "list"),
      ),
    )

  def membersGame =
    div(
      cls       := "chat__members none",
      aria.live := "off",
    )(
      div(cls := "line game none")(
        span(cls := "number", dataIcon := Icons.randomColor)("0"),
        " ",
        span(cls := "list"),
      ),
      div(cls := "line analysis none")(
        span(cls := "number", dataIcon := Icons.microscope)("0"),
        " ",
        span(cls := "list"),
      ),
    )

}
