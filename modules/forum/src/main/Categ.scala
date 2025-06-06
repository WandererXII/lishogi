package lila.forum

import play.api.i18n.Lang

import lila.user.User

case class Categ(
    _id: String, // slug
    name: String,
    desc: String,
    pos: Int,
    team: Option[String] = None,
    nbTopics: Int,
    nbPosts: Int,
    lastPostId: String,
    nbTopicsTroll: Int,
    nbPostsTroll: Int,
    lastPostIdTroll: String,
    quiet: Boolean = false,
) {

  def id = _id

  def translatedName(implicit lang: Lang): String =
    publicCategIdsTranslated.get(id).map(_.txt()).getOrElse(name)

  def nbTopics(forUser: Option[User]): Int =
    if (forUser.exists(_.marks.troll)) nbTopicsTroll else nbTopics
  def nbPosts(forUser: Option[User]): Int =
    if (forUser.exists(_.marks.troll)) nbPostsTroll else nbPosts
  def lastPostId(forUser: Option[User]): String =
    if (forUser.exists(_.marks.troll)) lastPostIdTroll else lastPostId

  def isTeam = team.nonEmpty

  def withTopic(post: Post): Categ =
    copy(
      nbTopics = if (post.troll) nbTopics else nbTopics + 1,
      nbPosts = if (post.troll) nbPosts else nbPosts + 1,
      lastPostId = if (post.troll) lastPostId else post.id,
      nbTopicsTroll = nbTopicsTroll + 1,
      nbPostsTroll = nbPostsTroll + 1,
      lastPostIdTroll = post.id,
    )

  def slug = id
}
