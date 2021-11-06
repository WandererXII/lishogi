package lishogi.app
package templating

import play.api.i18n.Lang

import lishogi.api.Context
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.forum.Post

trait ForumHelper { self: UserHelper with StringHelper with HasEnv =>

  private object Granter extends lishogi.forum.Granter {

    protected def userBelongsToTeam(teamId: String, userId: String): Fu[Boolean] =
      env.team.api.belongsTo(teamId, userId)

    protected def userOwnsTeam(teamId: String, userId: String): Fu[Boolean] =
      env.team.api.leads(teamId, userId)
  }

  def isGrantedWrite(categSlug: String)(implicit ctx: Context) =
    Granter isGrantedWrite categSlug

  def authorName(post: Post)(implicit lang: Lang) =
    post.userId match {
      case Some(userId) => userIdSpanMini(userId, withOnline = true)
      case None         => frag(lishogi.user.User.anonymous)
    }

  def authorLink(
      post: Post,
      cssClass: Option[String] = None,
      withOnline: Boolean = true,
      modIcon: Boolean = false
  )(implicit lang: Lang): Frag =
    if (post.erased) span(cls := "author")("<erased>")
    else
      post.userId.fold(frag(lishogi.user.User.anonymous)) { userId =>
        userIdLink(userId.some, cssClass = cssClass, withOnline = withOnline, modIcon = modIcon)
      }
}
