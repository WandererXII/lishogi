package lila.security

import lila.user.User
import lila.user.UserContext

trait SecurityHelper {

  def isGranted(permission: Permission)(implicit ctx: UserContext): Boolean =
    ctx.me ?? Granter(permission)

  def isGranted(permission: Permission.Selector)(implicit ctx: UserContext): Boolean =
    isGranted(permission(Permission))

  def isGranted(permission: Permission, user: User): Boolean =
    Granter(permission)(user)
}
