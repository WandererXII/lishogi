package lila.article

import play.api.data.Forms._
import play.api.data._

import lila.common.Form._

object ArticleForm {

  def create(langCode: Article.Lang.Code) =
    contentSetup fill ContentSetup(
      langCode = langCode,
      title = "",
      image = none,
      intro = "",
      body = "",
      translator = none,
    )

  def translate(content: Article.Content) =
    contentSetup fill ContentSetup(
      langCode = "",
      title = content.title,
      image = content.image.some.filter(_.nonEmpty),
      intro = content.intro,
      body = content.body,
      translator = none,
    )

  def edit(langCode: Article.Lang.Code, content: Article.Content) =
    contentSetup fill ContentSetup(
      langCode = langCode,
      title = content.title,
      image = content.image.some,
      intro = content.intro,
      body = content.body,
      translator = content.translator,
    )

  val contentSetup = Form(
    mapping(
      "langCode"   -> cleanText(minLength = 2, maxLength = 3),
      "title"      -> cleanText(minLength = 3, maxLength = 50),
      "image"      -> optional(cleanText(minLength = 5, maxLength = 250)),
      "intro"      -> cleanText(minLength = 10, maxLength = 300),
      "body"       -> cleanText(minLength = 50, maxLength = 20000),
      "translator" -> optional(cleanText(minLength = 3, maxLength = 50)),
    )(ContentSetup.apply)(ContentSetup.unapply),
  )

  val state = Form(
    single(
      "state" -> stringIn(Article.State.all.map(_.key)),
    ),
  )

  val systemKey = Form(
    single(
      "systemKey" -> cleanText,
    ),
  )

  case class ContentSetup(
      langCode: Article.Lang.Code,
      title: String,
      image: Option[String],
      intro: String,
      body: String,
      translator: Option[String],
  ) {
    def slug = lila.common.String.slugify(title)
  }

}
