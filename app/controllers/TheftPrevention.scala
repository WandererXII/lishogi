package controllers

import lila.api.Context
import lila.app._
import lila.game.AnonCookie
import lila.game.{ Game => GameModel }

private[controllers] trait TheftPrevention { self: LilaController =>

  protected def myGameColor(game: GameModel, cond: Boolean = true)(implicit
      ctx: Context,
  ): Option[shogi.Color] =
    (!game.isNotationImport && cond) ?? {
      ctx.userId
        .fold(
          ctx.req.cookies
            .get(AnonCookie.name)
            .map(_.value)
            .flatMap(game.player)
            .filterNot(_.hasUser),
        )(game.playerByUserId)
        .filterNot(_.isAi)
        .map(_.color)
    }

  protected lazy val theftResponse = Unauthorized(
    jsonError(
      "This game requires authentication",
    ),
  ) as JSON
}
