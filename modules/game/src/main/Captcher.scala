package lila.game

import scala.util.Success

import akka.actor._
import akka.pattern.pipe
import cats.data.NonEmptyList

import shogi.format.Reader
import shogi.format.Tags
import shogi.{ Game => ShogiGame }

import lila.common.Captcha
import lila.hub.actorApi.captcha._

final private class Captcher(gameRepo: GameRepo)(implicit ec: scala.concurrent.ExecutionContext)
    extends Actor {

  def receive = {

    case AnyCaptcha => sender() ! Impl.current

    case GetCaptcha(id: String) => Impl.get(id).pipeTo(sender()).unit

    case actorApi.NewCaptcha => Impl.refresh.unit

    case ValidCaptcha(id: String, solution: String) =>
      Impl.get(id).map(_ valid solution).pipeTo(sender()).unit
  }

  private object Impl {

    def get(id: String): Fu[Captcha] =
      find(id) match {
        case None    => getFromDb(id) map (c => (c | Captcha.default) ~ add)
        case Some(c) => fuccess(c)
      }

    def current = challenges.head

    def refresh =
      createFromDb andThen { case Success(Some(captcha)) =>
        add(captcha)
      }

    // Private stuff

    private val capacity   = 256
    private var challenges = NonEmptyList.one(Captcha.default)

    private def add(c: Captcha): Unit = {
      find(c.gameId) ifNone {
        challenges = NonEmptyList(c, challenges.toList take capacity)
      }
    }

    private def find(id: String): Option[Captcha] =
      challenges.find(_.gameId == id)

    private def createFromDb: Fu[Option[Captcha]] =
      findCheckmateInDb(10) flatMap {
        _.fold(findCheckmateInDb(1))(g => fuccess(g.some))
      } map { _ ?? makeCaptcha }

    private def findCheckmateInDb(distribution: Int): Fu[Option[Game]] =
      gameRepo findRandomMinishogiCheckmate distribution

    private def getFromDb(id: String): Fu[Option[Captcha]] =
      gameRepo game id map { _ ?? makeCaptcha }

    private def makeCaptcha(game: Game): Option[Captcha] =
      for {
        _         <- game.variant.minishogi option true // sanity check
        rewinded  <- rewind(game.usis)
        solutions <- solve(rewinded)
      } yield Captcha(game.id, sfen(rewinded), rewinded.color.sente, solutions)

    // Not looking for drop checkmates or checking promotions
    private def solve(game: ShogiGame): Option[Captcha.Solutions] =
      game.situation
        .moveActorsOf(game.situation.color)
        .view
        .flatMap { case moveActor =>
          moveActor.toUsis filter { usi =>
            game.situation(usi).toOption.fold(false)(_.checkmate)
          }
        }
        .to(List) map { usi =>
        s"${usi.orig}${usi.dest}" // we don't care about promotions
      } toNel

    private def rewind(moves: Usis): Option[ShogiGame] =
      Reader
        .fromUsi(
          moves.dropRight(1),
          none,
          shogi.variant.Minishogi,
          Tags.empty,
        )
        .valid
        .map(_.state)
        .toOption

    private def sfen(game: ShogiGame): String =
      game.situation.toSfen.value.split(' ').take(1).mkString(" ")
  }
}
