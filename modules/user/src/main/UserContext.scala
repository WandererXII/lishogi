package lila.user

import play.api.i18n.Lang
import play.api.mvc.Request
import play.api.mvc.RequestHeader

sealed trait UserContext {

  val req: RequestHeader

  val me: Option[User]

  val impersonatedBy: Option[User]

  def lang: Lang
  def withLang(newLang: Lang): UserContext

  def isAuth = me.isDefined

  def isAnon = !isAuth

  def is(name: String): Boolean = me.exists(_.id == User.normalize(name))
  def is(user: User): Boolean   = me contains user

  def userId = me.map(_.id)

  def username = me.map(_.username)

  def troll = me.??(_.marks.troll)

  def ip = lila.common.HTTPRequest lastRemoteAddress req

  def kid   = me.??(_.kid)
  def noKid = !kid
}

sealed abstract class BaseUserContext(
    val req: RequestHeader,
    val me: Option[User],
    val impersonatedBy: Option[User],
    val lang: Lang,
) extends UserContext {

  def withLang(newLang: Lang): BaseUserContext

  override def toString =
    "%s %s %s".format(
      me.fold("Anonymous")(_.username),
      req.remoteAddress,
      req.headers.get("User-Agent") | "?",
    )
}

final class BodyUserContext[A](val body: Request[A], m: Option[User], i: Option[User], l: Lang)
    extends BaseUserContext(body, m, i, l) {

  def withLang(newLang: Lang) = new BodyUserContext(body, m, i, newLang)
}

final class HeaderUserContext(r: RequestHeader, m: Option[User], i: Option[User], l: Lang)
    extends BaseUserContext(r, m, i, l) {

  def withLang(newLang: Lang) = new HeaderUserContext(r, m, i, newLang)
}
trait UserContextWrapper extends UserContext {
  val userContext: UserContext
  val req            = userContext.req
  val me             = userContext.me
  val impersonatedBy = userContext.impersonatedBy
  def isBot          = me.exists(_.isBot)
  def noBot          = !isBot
}

object UserContext {

  def apply(
      req: RequestHeader,
      me: Option[User],
      impersonatedBy: Option[User],
      lang: Lang,
  ): HeaderUserContext =
    new HeaderUserContext(req, me, impersonatedBy, lang)

  def apply[A](
      req: Request[A],
      me: Option[User],
      impersonatedBy: Option[User],
      lang: Lang,
  ): BodyUserContext[A] =
    new BodyUserContext(req, me, impersonatedBy, lang)

  trait ToLang {
    implicit def ctxLang(implicit ctx: UserContext): Lang = ctx.lang
  }
}
