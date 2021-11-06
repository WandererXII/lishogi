package lishogi.appeal

import lishogi.db.dsl._
import reactivemongo.api.bson._

private[appeal] object BsonHandlers {

  import Appeal.Status

  implicit val statusHandler = lishogi.db.dsl.quickHandler[Status](
    {
      case BSONString(v) => Status(v) | Status.Closed
      case _             => Status.Closed
    },
    s => BSONString(s.key)
  )

  implicit val appealMsgHandler = Macros.handler[AppealMsg]
  implicit val appealHandler    = Macros.handler[Appeal]
}
