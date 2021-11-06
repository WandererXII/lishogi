package lishogi.round

import scala.concurrent.duration._

import lishogi.common.IpAddress
import lishogi.game.Game
import lishogi.user.{ User, UserRepo }

final class SelfReport(
    tellRound: TellRound,
    gameRepo: lishogi.game.GameRepo,
    userRepo: UserRepo,
    slackApi: lishogi.slack.SlackApi,
    proxyRepo: GameProxyRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val whitelist = Set("treehugger")

  private object recent {
    private val cache = new lishogi.memo.ExpireSetMemo(10 minutes)
    def isNew(user: User, fullId: Game.FullId): Boolean = {
      val key = s"${user.id}:${fullId}"
      val res = !cache.get(key)
      cache.put(key)
      res
    }
  }

  def apply(
      userId: Option[User.ID],
      ip: IpAddress,
      fullId: Game.FullId,
      name: String
  ): Funit =
    !userId.exists(whitelist.contains) ?? {
      userId.??(userRepo.named) flatMap { user =>
        val known = user.exists(_.marks.engine)
        lishogi.mon.cheat.cssBot.increment()
        // user.ifTrue(!known && name != "ceval") ?? { u =>
        //   Env.report.api.autoBotReport(u.id, referer, name)
        // }
        def doLog(): Unit =
          if (name != "ceval") {
            lishogi
              .log("cheat")
              .branch("jslog")
              .info(
                s"$ip https://lishogi.org/$fullId ${user.fold("anon")(_.id)} $name"
              )
            user.filter(recent.isNew(_, fullId)) ?? { u =>
              slackApi.selfReport(
                typ = name,
                path = fullId.value,
                user = u,
                ip = ip
              )
            }
          }
        if (fullId.value == "________") fuccess(doLog())
        else
          proxyRepo.pov(fullId.value) map {
            _ ?? { pov =>
              if (!known) doLog()
              if (Set("ceval", "rcb", "ccs")(name)) fuccess {
                tellRound(
                  pov.gameId,
                  lishogi.round.actorApi.round.Cheat(pov.color)
                )
              }
              else gameRepo.setBorderAlert(pov).void
            }
          }
      }
    }
}
