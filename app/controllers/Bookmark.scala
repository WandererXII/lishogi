package controllers

import lishogi.app._

final class Bookmark(env: Env) extends LishogiController(env) {

  private def api = env.bookmark.api

  def toggle(gameId: String) =
    Auth { implicit ctx => me =>
      api.toggle(gameId, me.id)
    }
}
