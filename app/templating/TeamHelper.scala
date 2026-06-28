package lila.app
package templating

import controllers.routes

import scalatags.Text.all.Tag

import lila.api.Context
import lila.app.ui.ScalatagsTemplate._
import lila.common.Icons

trait TeamHelper { self: HasEnv =>

  def myTeam(teamId: String)(implicit ctx: Context): Boolean =
    ctx.userId.?? { env.team.api.syncBelongsTo(teamId, _) }

  def teamIdToName(id: String): String = env.team.getTeamName(id).getOrElse(id)

  def teamLink(id: String, withIcon: Boolean = true): Tag =
    teamLink(id, teamIdToName(id), withIcon)

  def teamLink(id: String, name: Frag, withIcon: Boolean): Tag =
    a(
      href     := routes.Team.show(id),
      dataIcon := withIcon.option(Icons.banner),
      cls      := withIcon option "text",
    )(name)

  def teamForumUrl(id: String) = routes.ForumCateg.show("team-" + id)
}
