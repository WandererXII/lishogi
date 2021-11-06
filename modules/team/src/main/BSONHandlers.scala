package lishogi.team

import reactivemongo.api.bson.Macros

import lishogi.hub.LightTeam

private object BSONHandlers {

  import lishogi.db.dsl.BSONJodaDateTimeHandler
  implicit val TeamBSONHandler      = Macros.handler[Team]
  implicit val RequestBSONHandler   = Macros.handler[Request]
  implicit val MemberBSONHandler    = Macros.handler[Member]
  implicit val LightTeamBSONHandler = Macros.handler[LightTeam]
}
