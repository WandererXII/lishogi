package lishogi.evalCache

import org.joda.time.DateTime
import play.api.libs.json.JsObject
import scala.concurrent.duration._

import shogi.format.{ FEN, Forsyth }
import shogi.variant.Variant
import lishogi.db.dsl._
import lishogi.memo.CacheApi._
import lishogi.socket.Socket
import lishogi.user.User

final class EvalCacheApi(
    coll: Coll,
    truster: EvalCacheTruster,
    upgrade: EvalCacheUpgrade,
    cacheApi: lishogi.memo.CacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  import EvalCacheEntry._
  import BSONHandlers._

  def evalCacheRouteEndpoint(userId: User.ID, data: JsObject) =
    truster cachedTrusted userId foreach {
      _ foreach { tu =>
        JsonHandlers.readPut(tu, data) foreach {
          put(tu, _, None)
        }
      }
    }

  def getEvalJson(variant: Variant, fen: FEN, multiPv: Int): Fu[Option[JsObject]] =
    getEval(
      id = Id(variant, SmallFen.make(variant, fen)),
      multiPv = multiPv
    ) map {
      _.map { JsonHandlers.writeEval(_, fen) }
    } addEffect { res =>
      Forsyth getPly fen.value foreach { ply =>
        lishogi.mon.evalCache.request(ply, res.isDefined).increment()
      }
    }

  def put(trustedUser: TrustedUser, candidate: Input.Candidate, sri: Option[Socket.Sri]): Funit =
    candidate.input ?? { put(trustedUser, _, sri) }

  def shouldPut = truster shouldPut _

  def getSinglePvEval(variant: Variant, fen: FEN): Fu[Option[Eval]] =
    getEval(
      id = Id(variant, SmallFen.make(variant, fen)),
      multiPv = 1
    )

  private[evalCache] def drop(variant: Variant, fen: FEN): Funit = {
    val id = Id(shogi.variant.Standard, SmallFen.make(variant, fen))
    coll.delete.one($id(id)).void >>- cache.invalidate(id)
  }

  private val cache = cacheApi[Id, Option[EvalCacheEntry]](65536, "evalCache") {
    _.expireAfterAccess(5 minutes)
      .buildAsyncFuture(fetchAndSetAccess)
  }

  private def getEval(id: Id, multiPv: Int): Fu[Option[Eval]] =
    getEntry(id) map {
      _.flatMap(_ makeBestMultiPvEval multiPv)
    }

  private def getEntry(id: Id): Fu[Option[EvalCacheEntry]] = cache get id

  private def fetchAndSetAccess(id: Id): Fu[Option[EvalCacheEntry]] =
    coll.ext.find($id(id)).one[EvalCacheEntry] addEffect { res =>
      if (res.isDefined) coll.updateFieldUnchecked($id(id), "usedAt", DateTime.now)
    }

  private def put(trustedUser: TrustedUser, input: Input, sri: Option[Socket.Sri]): Funit =
    Validator(input) match {
      case Some(error) =>
        logger.info(s"Invalid from ${trustedUser.user.username} $error ${input.fen}")
        funit
      case None =>
        getEntry(input.id) map {
          case None =>
            val entry = EvalCacheEntry(
              _id = input.id,
              nbMoves = destSize(input.fen),
              evals = List(input.eval),
              usedAt = DateTime.now
            )
            coll.insert.one(entry).recover(lishogi.db.recoverDuplicateKey(_ => ())) >>-
              cache.put(input.id, fuccess(entry.some)) >>-
              upgrade.onEval(input, sri)
          case Some(oldEntry) =>
            val entry = oldEntry add input.eval
            !(entry similarTo oldEntry) ?? {
              coll.update.one($id(entry.id), entry, upsert = true).void >>-
                cache.put(input.id, fuccess(entry.some)) >>-
                upgrade.onEval(input, sri)
            }

        }
    }

  private def destSize(fen: FEN): Int =
    shogi.Game(shogi.variant.Standard.some, fen.value.some).situation.destinations.size
}
