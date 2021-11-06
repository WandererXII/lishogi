package lishogi.api

import lishogi.common.Bus

final private[api] class Cli(
    userRepo: lishogi.user.UserRepo,
    security: lishogi.security.Env,
    teamSearch: lishogi.teamSearch.Env,
    forumSearch: lishogi.forumSearch.Env,
    team: lishogi.team.Env,
    puzzle: lishogi.puzzle.Env,
    tournament: lishogi.tournament.Env,
    explorer: lishogi.explorer.Env,
    fishnet: lishogi.fishnet.Env,
    study: lishogi.study.Env,
    studySearch: lishogi.studySearch.Env,
    coach: lishogi.coach.Env,
    evalCache: lishogi.evalCache.Env,
    plan: lishogi.plan.Env,
    msg: lishogi.msg.Env
)(implicit ec: scala.concurrent.ExecutionContext)
    extends lishogi.common.Cli {

  private val logger = lishogi.log("cli")

  def apply(args: List[String]): Fu[String] =
    run(args).dmap(_ + "\n") ~ {
      _.logFailure(logger, _ => args mkString " ") foreach { output =>
        logger.info("%s\n%s".format(args mkString " ", output))
      }
    }

  def process = {
    case "uptime" :: Nil => fuccess(s"${lishogi.common.Uptime.seconds} seconds")
    case "change" :: ("asset" | "assets") :: "version" :: Nil =>
      import lishogi.common.AssetVersion
      AssetVersion.change()
      fuccess(s"Changed to ${AssetVersion.current}")
    case "gdpr" :: "erase" :: username :: "forever" :: Nil =>
      userRepo named username map {
        case None                       => "No such user."
        case Some(user) if user.enabled => "That user account is not closed. Can't erase."
        case Some(user) =>
          Bus.publish(lishogi.user.User.GDPRErase(user), "gdprErase")
          s"Erasing all data about ${user.username} now"
      }
    case "announce" :: "cancel" :: Nil =>
      AnnounceStore set none
      Bus.publish(AnnounceStore.cancel, "announce")
      fuccess("Removed announce")
    case "announce" :: msgWords =>
      AnnounceStore.set(msgWords mkString " ") match {
        case Some(announce) =>
          Bus.publish(announce, "announce")
          fuccess(announce.json.toString)
        case None =>
          fuccess(
            "Invalid announce. Format: `announce <length> <unit> <words...>` or just `announce cancel` to cancel it"
          )
      }
    case "bus" :: "dump" :: Nil =>
      val keys = Bus.keys
      fuccess(s"${keys.size}\n ${keys mkString "\n"}")
  }

  private def run(args: List[String]): Fu[String] = {
    (processors lift args) | fufail("Unknown command: " + args.mkString(" "))
  } recover { case e: Exception =>
    "ERROR " + e
  }

  private def processors =
    security.cli.process orElse
      teamSearch.cli.process orElse
      forumSearch.cli.process orElse
      team.cli.process orElse
      puzzle.cli.process orElse
      tournament.cli.process orElse
      explorer.cli.process orElse
      fishnet.cli.process orElse
      study.cli.process orElse
      studySearch.cli.process orElse
      coach.cli.process orElse
      evalCache.cli.process orElse
      plan.cli.process orElse
      msg.cli.process orElse
      process
}
