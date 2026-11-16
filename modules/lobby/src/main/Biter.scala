package lila.lobby

import shogi.Position

import lila.game.Game
import lila.game.Player
import lila.lobby.actorApi.JoinHook
import lila.lobby.actorApi.JoinSeek
import lila.socket.Socket.Sri
import lila.user.User

final private class Biter(
    userRepo: lila.user.UserRepo,
    gameRepo: lila.game.GameRepo,
)(implicit
    ec: scala.concurrent.ExecutionContext,
    idGenerator: lila.game.IdGenerator,
) {

  def apply(hook: Hook, sri: Sri, user: Option[LobbyUser]): Fu[JoinHook] =
    if (canJoin(hook, user)) join(hook, sri, user)
    else fufail(s"$user cannot bite hook $hook")

  def apply(seek: Seek, user: LobbyUser): Fu[JoinSeek] =
    if (canJoin(seek, user)) join(seek, user)
    else fufail(s"$user cannot join seek $seek")

  private def join(hook: Hook, sri: Sri, lobbyUserOption: Option[LobbyUser]): Fu[JoinHook] =
    for {
      userOption   <- lobbyUserOption.map(_.id) ?? userRepo.byId
      ownerOption  <- hook.userId ?? userRepo.byId
      creatorColor <- assignCreatorColor(ownerOption, userOption, hook.realColor)
      game <- makeGame(
        hook,
        senteUser = creatorColor.fold(ownerOption, userOption),
        goteUser = creatorColor.fold(userOption, ownerOption),
      ).withUniqueId
      _ <- gameRepo insertDenormalized game
    } yield {
      lila.mon.lobby.hook.join.increment()
      JoinHook(sri, hook, game, creatorColor)
    }

  private def join(seek: Seek, lobbyUser: LobbyUser): Fu[JoinSeek] =
    for {
      user         <- userRepo byId lobbyUser.id orFail s"No such user: ${lobbyUser.id}"
      owner        <- userRepo byId seek.user.id orFail s"No such user: ${seek.user.id}"
      creatorColor <- assignCreatorColor(owner.some, user.some, seek.realColor)
      game <- makeGame(
        seek,
        senteUser = creatorColor.fold(owner.some, user.some),
        goteUser = creatorColor.fold(user.some, owner.some),
      ).withUniqueId
      _ <- gameRepo insertDenormalized game
    } yield JoinSeek(user.id, seek, game, creatorColor)

  private def assignCreatorColor(
      creatorUser: Option[User],
      joinerUser: Option[User],
      color: Color,
  ): Fu[shogi.Color] =
    color match {
      case Color.Random =>
        userRepo.firstGetsSente(
          creatorUser.map(_.id),
          joinerUser.map(_.id),
        ) map shogi.Color.fromSente
      case Color.Sente => fuccess(shogi.Sente)
      case Color.Gote  => fuccess(shogi.Gote)
    }

  private def makeGame(hook: Hook, senteUser: Option[User], goteUser: Option[User]) = {
    val position = Position(hook.realVariant)
    val clock    = hook.clock.toClock(position.color)
    val perfType = lila.rating.PerfType.from(hook.realVariant, hasClock = true)
    Game
      .make(
        position = position,
        clock = clock.some,
        initialSfen = None,
        sentePlayer = Player.make(shogi.Sente, senteUser, perfType),
        gotePlayer = Player.make(shogi.Gote, goteUser, perfType),
        mode = hook.realMode,
        proMode = false,
        source = lila.game.Source.Lobby,
        notationImport = None,
      )
      .start
  }

  private def makeGame(seek: Seek, senteUser: Option[User], goteUser: Option[User]) = {
    val perfType = lila.rating.PerfType.from(seek.realVariant, hasClock = false)
    Game
      .make(
        position = Position(seek.realVariant),
        initialSfen = None,
        sentePlayer = Player.make(shogi.Sente, senteUser, perfType),
        gotePlayer = Player.make(shogi.Gote, goteUser, perfType),
        mode = seek.realMode,
        proMode = false,
        source = lila.game.Source.Lobby,
        daysPerTurn = seek.daysPerTurn,
        notationImport = None,
      )
      .start
  }

  // HOOKS

  def canSee(hook: Hook, user: Option[LobbyUser]): Boolean =
    user.fold(true) { u =>
      u.lame == hook.lame &&
      !hook.userId.??(u.blocking.contains) &&
      !hook.user.??(_.blocking).contains(u.id)
    }

  def canSee(hook: Hook, member: LobbySocket.Member): Boolean =
    hook.sri == member.sri || canSee(hook, member.user)

  def canJoin(hook: Hook, user: Option[LobbyUser]): Boolean =
    canSee(hook, user) && (user.isDefined || !hook.isAuth) && user.fold(true) { u =>
      !hook.userId.contains(u.id) &&
      hook.realRatingRange.fold(true) { range =>
        range.contains(u.ratingAt(hook.perfType))
      }
    }

  // do not auto join users with anons
  def canAutoJoin(hook: Hook, user: Option[LobbyUser]): Boolean =
    user.isDefined == hook.isAuth && canJoin(hook, user)

  // SEEKS

  def canSee(seek: Seek, user: LobbyUser): Boolean =
    seek.user.id == user.id || ((seek.realMode.casual || user.lame == seek.user.lame) &&
      !(user.blocking contains seek.user.id) &&
      !(seek.user.blocking contains user.id))

  def canJoin(seek: Seek, user: LobbyUser): Boolean =
    seek.user.id != user.id && canSee(seek, user) && seek.realRatingRange.fold(true) { range =>
      range.contains(user.ratingAt(seek.perfType))
    }

}
