package lila.importer

import lila.game.Game
import lila.game.GameRepo

final class Importer(gameRepo: GameRepo)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(data: ImportData, user: Option[String], forceId: Option[String] = none): Fu[Game] = {

    def gameExists(processing: => Fu[Game]): Fu[Game] =
      gameRepo.findNotationImport(data.notation) flatMap { _.fold(processing)(fuccess) }

    gameExists {
      (data preprocess user).toFuture flatMap { case Preprocessed(g, _, _) =>
        val game = forceId.fold(g.sloppy)(g.withId)
        (gameRepo.insertDenormalized(game)) >> {
          game.notationImport.flatMap(_.user).isDefined ?? gameRepo.setImportCreatedAt(game)
        } >> {
          gameRepo.finish(
            id = game.id,
            winnerColor = game.winnerColor,
            winnerId = none,
            illegalUsi = game.illegalUsi,
            status = game.status,
          )
        } inject game
      }
    }
  }

}
