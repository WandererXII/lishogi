package views
package html.site

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object message {

  def apply(
      title: String,
      back: Option[String] = None,
      icon: Option[String] = None,
      moreCss: Option[Frag] = None,
  )(message: Modifier*)(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = ~moreCss,
    ) {
      main(cls := "box box-pad")(
        h1(
          dataIcon := icon ifTrue back.isEmpty,
          cls      := List("text" -> (icon.isDefined && back.isEmpty)),
        )(
          back map { url =>
            a(href := url, dataIcon := Icons.left, cls := "text")
          },
          title,
        ),
        p(message),
      )
    }

  def noBot(implicit ctx: Context) =
    apply("No bot area") {
      frag("Sorry, bot accounts are not allowed here. 申し訳ありませんが、ボットアカウントはここでは許可されていません。")
    }

  def noEngine(implicit ctx: Context) =
    apply("No engine area") {
      "Sorry, engine assisted players are not allowed here. 申し訳ありませんが、エンジン支援を使用するプレイヤーはここでは許可されていません。"
    }

  def noBooster(implicit ctx: Context) =
    apply("No booster area") {
      "Sorry, boosters and sandbaggers are not allowed here. 申し訳ありませんが、レーティング操作（ブースト行為・サンドバッグ行為）は申し訳ありませんが、レーティング操作（ブースト行為・サンドバッグ行為）はこのページでは禁止されています。"
    }

  def privateStudy(study: lila.study.Study)(implicit ctx: Context) =
    apply(
      title = s"${usernameOrId(study.ownerId)}'s study",
      back = routes.Study.allDefault(1).url.some,
    )(
      "Sorry! This study is private, you cannot access it.",
      isGranted(_.StudyAdmin) option postForm(action := routes.Study.admin(study.id.value))(
        submitButton("View as admin")(cls := "button button-red"),
      ),
    )

  def streamingMod(implicit ctx: Context) =
    apply("Disabled while streaming") {
      frag(
        "This moderation feature is disabled while streaming, ",
        "to avoid leaking sensible information.",
      )
    }

  def challengeDenied(msg: String)(implicit ctx: Context) =
    apply(
      title = trans.challengeToPlay.txt(),
      back = routes.Lobby.home.url.some,
    )(msg)

  def teamCreateLimit(implicit ctx: Context) =
    apply("Cannot create a team") {
      "You have already created a team this week."
    }

  def teamJoinLimit(implicit ctx: Context) =
    apply("Cannot join the team") {
      "You have already joined too many teams."
    }

  def authFailed(implicit ctx: Context) =
    apply("403 - Access denied!") {
      "You tried to visit a page you're not authorized to access."
    }

  def temporarilyDisabled(implicit ctx: Context) =
    apply("Temporarily disabled")(
      "Sorry, his feature is temporarily disabled while we figure out a way to bring it back.",
    )

  def tooManyDrafts(nb: Int)(implicit ctx: Context) =
    apply("Too many drafts")(
      s"At most you might have ${nb} unpublished drafts at the same time.",
    )
}
