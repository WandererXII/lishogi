package lishogi.relay

import lishogi.db.dsl._
import reactivemongo.api.bson._

object BSONHandlers {

  import lishogi.study.BSONHandlers.LikesBSONHandler

  implicit val relayIdHandler = stringAnyValHandler[Relay.Id](_.value, Relay.Id.apply)

  import Relay.Sync
  import Sync.Upstream
  implicit val upstreamHandler = Macros.handler[Upstream]

  import SyncLog.Event
  implicit val syncLogEventHandler = Macros.handler[Event]

  implicit val syncLogHandler = isoHandler[SyncLog, Vector[Event]]((s: SyncLog) => s.events, SyncLog.apply _)

  implicit val syncHandler = Macros.handler[Sync]

  implicit val relayHandler = Macros.handler[Relay]
}
