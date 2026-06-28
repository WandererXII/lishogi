package lila.article

import play.api.libs.json._

import lila.common.Json._

final class JsonView(
    lightUserApi: lila.user.LightUserApi,
) {

  implicit val contentWrites: Writes[Article.Content] = Json.writes[Article.Content]
  implicit val translationsWrites: Writes[Map[String, Article.Content]] =
    Writes.map[Article.Content]

  def apply(
      article: Article,
      langCode: Article.Lang.Code,
      liked: Boolean,
  ) =
    Json
      .obj(
        "id"          -> article.id,
        "author"      -> lightUserApi.sync(article.author),
        "title"       -> article.title(langCode),
        "slug"        -> article.slug(langCode),
        "image"       -> article.image(langCode),
        "intro"       -> article.intro(langCode),
        "body"        -> article.body(langCode),
        "langs"       -> article.langCodes,
        "categories"  -> article.categories.map(_.key),
        "likes"       -> article.likes,
        "liked"       -> liked,
        "state"       -> article.state.key,
        "createdAt"   -> article.createdAt,
        "updatedAt"   -> article.updatedAt,
        "publishedAt" -> article.publishedAt,
      )
      .add(
        "translator" -> article.translator(langCode),
      )

  def internal(
      article: Article,
      langCode: Article.Lang.Code,
  ) =
    Json
      .obj(
        "id"          -> article.id,
        "title"       -> article.title(langCode),
        "slug"        -> article.slug(langCode),
        "image"       -> article.image(langCode),
        "intro"       -> article.intro(langCode),
        "body"        -> article.body(langCode),
        "langs"       -> article.langCodes,
        "createdAt"   -> article.createdAt,
        "updatedAt"   -> article.updatedAt,
        "publishedAt" -> article.publishedAt,
      )
      .add(
        "translator" -> article.translator(langCode),
      )

  // def editor(
  //     article: Article,
  // ) =
  //   Json
  //     .obj(
  //       "id"           -> article.id,
  //       "author"       -> article.author,
  //       "translations" -> article.translations,
  //       "categories"   -> article.categories.map(_.key),
  //       "state"        -> article.state.key,
  //     )

  def pagerData(
      preview: Article.Preview,
      langCode: Article.Lang.Code,
  ) =
    Json
      .obj(
        "id"          -> preview.id,
        "title"       -> preview.title(langCode),
        "slug"        -> preview.slug(langCode),
        "image"       -> preview.image(langCode),
        "publishedAt" -> preview.publishedAt,
        "author"      -> preview.author,
      )

}
