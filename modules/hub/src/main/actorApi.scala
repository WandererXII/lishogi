package lila.hub
package actorApi

import scala.concurrent.Promise

import play.api.libs.json._

import org.joda.time.DateTime

import shogi.format.usi.Usi

// announce something to all clients
case class Announce(msg: String, date: DateTime, json: JsObject)

package streamer {
  case class StreamStart(userId: String)
}

package map {
  case class Tell(id: String, msg: Any)
  case class TellIfExists(id: String, msg: Any)
  case class TellMany(ids: Seq[String], msg: Any)
  case class TellAll(msg: Any)
  case class Exists(id: String, promise: Promise[Boolean])
}

package socket {
  case class SendTo(userId: String, message: JsObject)
  object SendTo {
    def apply[A: Writes](userId: String, typ: String, data: A): SendTo =
      SendTo(userId, Json.obj("t" -> typ, "d" -> data))
  }
  case class SendTos(userIds: Set[String], message: JsObject)
  object SendTos {
    def apply[A: Writes](userIds: Set[String], typ: String, data: A): SendTos =
      SendTos(userIds, Json.obj("t" -> typ, "d" -> data))
  }
  object remote {
    case class TellSriIn(sri: String, user: Option[String], msg: JsObject)
    case class TellSriOut(sri: String, payload: JsValue)
    case class TellUserIn(user: String, msg: JsObject)
  }
  case class ApiUserIsOnline(userId: String, isOnline: Boolean)
}

package clas {
  case class IsTeacherOf(teacherId: String, studentId: String, promise: Promise[Boolean])
}

package report {
  case class Cheater(userId: String, text: String)
  case class Shutup(userId: String, text: String, major: Boolean)
  case class Booster(winnerId: String, loserId: String)
  case class AutoFlag(suspectId: String, resource: String, text: String)
  case class CheatReportCreated(userId: String)
}

package security {
  case class GarbageCollect(userId: String)
  case class GCImmediateSb(userId: String)
  case class CloseAccount(userId: String)
}

package msg {
  case class SystemMsg(userId: String, text: String)
}

package storm {
  case class StormRun(userId: String, score: Int)
}

package shutup {
  case class RecordPublicForumMessage(userId: String, text: String)
  case class RecordTeamForumMessage(userId: String, text: String)
  case class RecordPrivateMessage(userId: String, toUserId: String, text: String)
  case class RecordPrivateChat(chatId: String, userId: String, text: String)
  case class RecordPublicChat(userId: String, text: String, source: PublicSource)

  sealed abstract class PublicSource(val parentName: String)
  object PublicSource {
    case class Tournament(id: String)  extends PublicSource("tournament")
    case class Simul(id: String)       extends PublicSource("simul")
    case class Study(id: String)       extends PublicSource("study")
    case class Watcher(gameId: String) extends PublicSource("watcher")
    case class Team(id: String)        extends PublicSource("team")
    case class Unknown(source: String) extends PublicSource("unknown")
  }
}

package mod {
  case class MarkCheater(userId: String, value: Boolean)
  case class MarkBooster(userId: String)
  case class ChatTimeout(mod: String, user: String, reason: String, text: String)
  case class Shadowban(user: String, value: Boolean)
  case class KickFromRankings(userId: String)
  case class SetPermissions(userId: String, permissions: List[String])
  case class AutoWarning(userId: String, subject: String)
  case class Impersonate(userId: String, by: Option[String])
  case class DisableUser(userId: String)
  case class Alert(msg: String)
}

package playban {
  case class Playban(userId: String, mins: Int, inTournament: Boolean)
}

package captcha {
  case object AnyCaptcha
  case class GetCaptcha(id: String)
  case class ValidCaptcha(id: String, solution: String)
}

package lobby {
  case class ReloadTournaments(html: String)
}

package simul {
  case class GetHostIds(promise: Promise[Set[String]])
  case class PlayerMove(gameId: String)
}

package timeline {
  case class ReloadTimelines(userIds: List[String])

  sealed abstract class Atom(val channel: String, val okForKid: Boolean) {
    def userIds: List[String]
  }
  case class Follow(u1: String, u2: String) extends Atom("follow", true) {
    def userIds = List(u1, u2)
  }
  case class TeamJoin(userId: String, teamId: String) extends Atom("teamJoin", false) {
    def userIds = List(userId)
  }
  case class TeamCreate(userId: String, teamId: String) extends Atom("teamCreate", false) {
    def userIds = List(userId)
  }
  case class ForumPost(userId: String, topicId: Option[String], topicName: String, postId: String)
      extends Atom(s"forum:${~topicId}", false) {
    def userIds = List(userId)
  }
  case class TourJoin(userId: String, tourId: String, tourName: String)
      extends Atom("tournament", true) {
    def userIds = List(userId)
  }
  case class GameEnd(playerId: String, opponent: Option[String], win: Option[Boolean], perf: String)
      extends Atom("gameEnd", true) {
    def userIds = opponent.toList
  }
  case class SimulCreate(userId: String, simulId: String, simulName: String)
      extends Atom("simulCreate", true) {
    def userIds = List(userId)
  }
  case class SimulJoin(userId: String, simulId: String, simulName: String)
      extends Atom("simulJoin", true) {
    def userIds = List(userId)
  }
  case class StudyCreate(userId: String, studyId: String, studyName: String)
      extends Atom("studyCreate", true) {
    def userIds = List(userId)
  }
  case class StudyLike(userId: String, studyId: String, studyName: String)
      extends Atom("studyLike", true) {
    def userIds = List(userId)
  }
  case class PlanStart(userId: String) extends Atom("planStart", true) {
    def userIds = List(userId)
  }
  case class BlogPost(id: String) extends Atom("blogPost", true) {
    def userIds = Nil
  }
  case class StreamStart(id: String, name: String) extends Atom("streamStart", true) {
    def userIds = List(id)
  }
  case class SystemNotification(msg: String) extends Atom("systemNotification", true) {
    def userIds = Nil
  }

  object propagation {
    sealed trait Propagation
    case class Users(users: List[String]) extends Propagation
    case class Followers(user: String)    extends Propagation
    case class Friends(user: String)      extends Propagation
    case class ExceptUser(user: String)   extends Propagation
    case class ModsOnly(value: Boolean)   extends Propagation
  }

  import propagation._

  case class Propagate(data: Atom, propagations: List[Propagation] = Nil) {
    def toUsers(ids: List[String])  = add(Users(ids))
    def toUser(id: String)          = add(Users(List(id)))
    def toFollowersOf(id: String)   = add(Followers(id))
    def toFriendsOf(id: String)     = add(Friends(id))
    def exceptUser(id: String)      = add(ExceptUser(id))
    def modsOnly(value: Boolean)    = add(ModsOnly(value))
    private def add(p: Propagation) = copy(propagations = p :: propagations)
  }
}

package game {
  case class ChangeFeatured(id: String, msg: JsObject)
  case object Count
}

package tv {
  case class TvSelect(gameId: String, speed: shogi.Speed, data: JsObject)
}

package notify {
  case class Notified(userId: String)
  case class NotifiedBatch(userIds: Iterable[String])
}

package team {
  case class CreateTeam(id: String, name: String, userId: String)
  case class JoinTeam(id: String, userId: String)
  case class IsLeader(id: String, userId: String, promise: Promise[Boolean])
  case class IsLeaderOf(leaderId: String, memberId: String, promise: Promise[Boolean])
  case class KickFromTeam(teamId: String, userId: String)
  case class TeamIdsJoinedBy(userId: String, promise: Promise[List[LightTeam.TeamID]])
}

package fishnet {
  case class AutoAnalyse(gameId: String)
  case class NewKey(userId: String, key: String)
  case class PostGameStudyRequest(
      userId: String,
      gameId: String,
      studyId: String,
      chapterId: String,
  )
  case class StudyChapterRequest(
      studyId: String,
      chapterId: String,
      initialSfen: Option[shogi.format.forsyth.Sfen],
      variant: shogi.variant.Variant,
      moves: List[Usi],
      userId: String,
  )
}

package user {
  case class Note(from: String, to: String, text: String, mod: Boolean)
}

package round {
  case class MoveEvent(
      gameId: String,
      sfen: String,
      usi: String,
  )
  case class CorresMoveEvent(
      move: MoveEvent,
      playerUserId: Option[String],
      mobilePushable: Boolean,
      alarmable: Boolean,
      unlimited: Boolean,
  )
  case class CorresTakebackOfferEvent(gameId: String)
  case class CorresDrawOfferEvent(gameId: String)
  case class BoardDrawEvent(gameId: String)
  case class SimulMoveEvent(
      move: MoveEvent,
      simulId: String,
      opponentUserId: String,
  )
  case class PostGameStudy(studyId: String)
  case class Berserk(gameId: String, userId: String)
  case class IsOnGame(color: shogi.Color, promise: Promise[Boolean])
  case class TourStandingOld(data: JsArray)
  case class TourStanding(tourId: String, data: JsArray)
  case class FishnetPlay(usi: Usi, ply: Int)
  case class BotPlay(
      playerId: String,
      usi: Usi,
      promise: Option[scala.concurrent.Promise[Unit]] = None,
  )
  case class RematchOffer(gameId: String)
  case class RematchYes(playerId: String)
  case class RematchNo(playerId: String)
  case class Abort(playerId: String)
  case class Resign(playerId: String)
  case class Mlat(micros: Int)
}

package evaluation {
  case class AutoCheck(userId: String)
  case class Refresh(userId: String)
}

package bookmark {
  case class Toggle(gameId: String, userId: String)
  case class Remove(gameId: String)
}

package relation {
  case class Block(u1: String, u2: String)
  case class UnBlock(u1: String, u2: String)
  case class Follow(u1: String, u2: String)
  case class UnFollow(u1: String, u2: String)
}

package study {
  case class RemoveStudy(studyId: String, contributors: Set[String])
  case class RoundRematch(studyId: String, gameId: String)
  case class RoundRematchOffer(studyId: String, by: Option[shogi.Color])
}

package plan {
  case class ChargeEvent(username: String, amount: Int, percent: Int, date: DateTime)
  case class MonthInc(userId: String, months: Int)
}
