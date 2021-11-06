package lishogi.forum

import lishogi.db.dsl._
import reactivemongo.api.bson._

private object BSONHandlers {

  implicit val CategBSONHandler = Macros.handler[Categ]

  implicit val PostEditBSONHandler = Macros.handler[OldVersion]
  implicit val PostBSONHandler     = Macros.handler[Post]

  implicit val TopicBSONHandler = Macros.handler[Topic]
}
