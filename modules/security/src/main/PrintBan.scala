package lila.security

import org.joda.time.DateTime
import reactivemongo.api.bson._

import lila.db.dsl._

final class PrintBan(coll: Coll)(implicit ec: scala.concurrent.ExecutionContext) {

  @volatile private var current: Set[String] = Set.empty

  def blocks(hash: FingerHash): Boolean = current contains hash.value

  def toggle(hash: FingerHash, block: Boolean): Funit = {
    synchronized {
      current = if (block) current + hash.value else current - hash.value
    }
    if (block)
      coll.update
        .one(
          $id(hash.value),
          $doc("_id" -> hash.value, "date" -> DateTime.now),
          upsert = true,
        )
        .void
    else coll.delete.one($id(hash.value)).void
  }

  coll.secondaryPreferred
    .distinctEasy[String, Set]("_id", $empty)
    .map { hashes =>
      synchronized {
        current = hashes
      }
      lila.mon.security.firewall.prints.update(hashes.size)
    }
    .logFailure(logger, e => s"PrintBan load: $e")
    .unit
}
