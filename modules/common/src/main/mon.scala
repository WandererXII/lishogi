package lila

import com.github.benmanes.caffeine.cache.{ Cache => CaffeineCache }
import kamon.metric.Counter
import kamon.metric.Timer
import kamon.tag.TagSet

object mon {

  @inline private def tags(elems: (String, Any)*): Map[String, Any] = Map.from(elems)

  object http {
    private val t = timer("http.time")
    def time(action: String, client: String, method: String, code: Int) =
      t.withTags(
        tags(
          "action" -> action,
          "client" -> client,
          "method" -> method,
          "code"   -> code.toLong,
        ),
      )
    def error(action: String, client: String, method: String, code: Int) =
      counter("http.error").withTags(
        tags(
          "action" -> action,
          "client" -> client,
          "method" -> method,
          "code"   -> code.toLong,
        ),
      )
    def path(p: String) = counter("http.path.count").withTag("path", p)
    val userGamesCost   = counter("http.userGames.cost").withoutTags()
    def csrfError(tpe: String, action: String, client: String) =
      counter("http.csrf.error").withTags(
        tags("type" -> tpe, "action" -> action, "client" -> client),
      )
    val fingerPrint          = timer("http.fingerPrint.time").withoutTags()
    def jsmon(event: String) = counter("http.jsmon").withTag("event", event)
    val imageBytes           = histogram("http.image.bytes").withoutTags()
  }
  object syncache {
    def miss(name: String)    = counter("syncache.miss").withTag("name", name)
    def timeout(name: String) = counter("syncache.timeout").withTag("name", name)
    def compute(name: String) = timer("syncache.compute").withTag("name", name)
    def wait(name: String)    = timer("syncache.wait").withTag("name", name)
  }
  def caffeineStats(cache: CaffeineCache[_, _], name: String): Unit = {
    val stats = cache.stats
    gauge("caffeine.request")
      .withTags(tags("name" -> name, "hit" -> true))
      .update(stats.hitCount.toDouble)
    gauge("caffeine.request")
      .withTags(tags("name" -> name, "hit" -> false))
      .update(stats.missCount.toDouble)
    histogram("caffeine.hit.rate").withTag("name", name).record((stats.hitRate * 100000).toLong)
    if (stats.totalLoadTime > 0) {
      gauge("caffeine.load.count")
        .withTags(tags("name" -> name, "success" -> "success"))
        .update(stats.loadSuccessCount.toDouble)
      gauge("caffeine.load.count")
        .withTags(tags("name" -> name, "success" -> "failure"))
        .update(stats.loadFailureCount.toDouble)
      gauge("caffeine.loadTime.cumulated")
        .withTag("name", name)
        .update(stats.totalLoadTime / 1000000d) // in millis; too much nanos for Kamon to handle)
      timer("caffeine.loadTime.penalty")
        .withTag("name", name)
        .record(stats.averageLoadPenalty.toLong)
    }
    gauge("caffeine.eviction.count").withTag("name", name).update(stats.evictionCount.toDouble)
    gauge("caffeine.entry.count").withTag("name", name).update(cache.estimatedSize.toDouble)
    ()
  }
  object mongoCache {
    def request(name: String, hit: Boolean) =
      counter("mongocache.request").withTags(
        tags(
          "name" -> name,
          "hit"  -> hit,
        ),
      )
    def compute(name: String) = timer("mongocache.compute").withTag("name", name)
  }
  object evalCache {
    private val r = counter("evalCache.request")
    def request(ply: Int, isHit: Boolean) =
      r.withTags(tags("ply" -> (if (ply < 15) ply.toString else "15+"), "hit" -> isHit))
    object upgrade {
      val count     = counter("evalCache.upgrade.count").withoutTags()
      val members   = gauge("evalCache.upgrade.members").withoutTags()
      val evals     = gauge("evalCache.upgrade.evals").withoutTags()
      val expirable = gauge("evalCache.upgrade.expirable").withoutTags()
    }
  }
  object lobby {
    object hook {
      val create = counter("lobby.hook.create").withoutTags()
      val join   = counter("lobby.hook.join").withoutTags()
      val size   = histogram("lobby.hook.size").withoutTags()
    }
    object seek {
      val create = counter("lobby.seek.create").withoutTags()
      val join   = counter("lobby.seek.join").withoutTags()
    }
    object socket {
      val getSris         = timer("lobby.socket.getSris").withoutTags()
      val member          = gauge("lobby.socket.member").withoutTags()
      val idle            = gauge("lobby.socket.idle").withoutTags()
      val hookSubscribers = gauge("lobby.socket.hookSubscribers").withoutTags()
    }
    private val lobbySegment = timer("lobby.segment")
    def segment(seg: String) = lobbySegment.withTag("segment", seg)
  }
  object rating {
    def distribution(perfKey: String, rating: Int) =
      gauge("rating.distribution").withTags(tags("perf" -> perfKey, "rating" -> rating.toLong))
    object regulator {
      def micropoints(perfKey: String) = histogram("rating.regulator").withTag("perf", perfKey)
    }
  }
  object perfStat {
    def indexTime = timer("perfStat.indexTime").withoutTags()
  }

  object round {
    object api {
      val player  = timer("round.api").withTag("endpoint", "player")
      val watcher = timer("round.api").withTag("endpoint", "watcher")
      val embed   = timer("round.api").withTag("endpoint", "embed")
    }
    object forecast {
      val create = counter("round.forecast.create").withoutTags()
    }
    object move {
      object lag {
        val compDeviation         = histogram("round.move.lag.comp_deviation").withoutTags()
        def uncomped(key: String) = histogram("round.move.lag.uncomped_ms").withTag("key", key)
        def uncompStdDev(key: String) =
          histogram("round.move.lag.uncomp_stdev_ms").withTag("key", key)
        val stdDev         = histogram("round.move.lag.stddev_ms").withoutTags()
        val mean           = histogram("round.move.lag.mean_ms").withoutTags()
        val coefVar        = histogram("round.move.lag.coef_var_1000").withoutTags()
        val compEstStdErr  = histogram("round.move.lag.comp_est_stderr_1000").withoutTags()
        val compEstOverErr = histogram("round.move.lag.avg_over_error_ms").withoutTags()
      }
      val time = timer("round.move.time").withoutTags()
    }
    object error {
      val client  = counter("round.error").withTag("from", "client")
      val fishnet = counter("round.error").withTag("from", "fishnet")
      val glicko  = counter("round.error").withTag("from", "glicko")
      val other   = counter("round.error").withTag("from", "other")
    }
    object titivate {
      val time = future("round.titivate.time")
      val game = histogram("round.titivate.game").withoutTags() // how many games were processed
      val total =
        histogram("round.titivate.total").withoutTags() // how many games should have been processed
      val old = histogram("round.titivate.old").withoutTags() // how many old games remain
      def broken(error: String) =
        counter("round.titivate.broken").withTag("error", error) // broken game
    }
    object alarm {
      val time = timer("round.alarm.time").withoutTags()
    }
    object expiration {
      val count = counter("round.expiration.count").withoutTags()
    }
    val ductCount = gauge("round.duct.count").withoutTags()
  }
  object playban {
    def outcome(out: String) = counter("playban.outcome").withTag("outcome", out)
    object ban {
      val count = counter("playban.ban.count").withoutTags()
      val mins  = histogram("playban.ban.mins").withoutTags()
    }
  }
  object timeline {
    val notification = counter("timeline.notification").withoutTags()
  }
  object search {
    def time(op: String, index: String, success: Boolean) =
      timer("search.client.time").withTags(
        tags(
          "op"      -> op,
          "index"   -> index,
          "success" -> successTag(success),
        ),
      )
  }
  object duct {
    def overflow(name: String) = counter("duct.overflow").withTag("name", name)
  }
  object user {
    val online = gauge("user.online").withoutTags()
    object register {
      val count                       = counter("user.register.count").withoutTags()
      def mustConfirmEmail(v: String) = counter("user.register.mustConfirmEmail").withTag("type", v)
      def confirmEmailResult(success: Boolean) =
        counter("user.register.confirmEmail").withTag("success", successTag(success))
      val modConfirmEmail = counter("user.register.modConfirmEmail").withoutTags()
    }
    object auth {
      val bcFullMigrate = counter("user.auth.bcFullMigrate").withoutTags()
      val hashTime      = timer("user.auth.hashTime").withoutTags()
      def count(success: Boolean) =
        counter("user.auth.count").withTag("success", successTag(success))

      def passwordResetRequest(s: String) =
        counter("user.auth.passwordResetRequest").withTag("type", s)
      def passwordResetConfirm(s: String) =
        counter("user.auth.passwordResetConfirm").withTag("type", s)

      def magicLinkRequest(s: String) = counter("user.auth.magicLinkRequest").withTag("type", s)
      def magicLinkConfirm(s: String) = counter("user.auth.magicLinkConfirm").withTag("type", s)

      def reopenRequest(s: String) = counter("user.auth.reopenRequest").withTag("type", s)
      def reopenConfirm(s: String) = counter("user.auth.reopenConfirm").withTag("type", s)
    }
    object oauth {
      def request(success: Boolean) =
        counter("user.oauth.request").withTag("success", successTag(success))
    }
    private val userSegment  = timer("user.segment")
    def segment(seg: String) = userSegment.withTag("segment", seg)
    def leaderboardCompute   = future("user.leaderboard.compute")
  }
  object trouper {
    def queueSize(name: String) = gauge("trouper.queueSize").withTag("name", name)
  }
  object mod {
    object report {
      val unprocessed            = gauge("mod.report.unprocessed").withoutTags()
      val close                  = counter("mod.report.close").withoutTags()
      def create(reason: String) = counter("mod.report.create").withTag("reason", reason)
    }
    object log {
      val create = counter("mod.log.create").withoutTags()
    }
    object comm {
      def segment(seg: String) = timer("mod.comm.segmentLat").withTag("segment", seg)
    }
    def zoneSegment(name: String) = future("mod.zone.segment", name)
  }
  object bot {
    def moves(username: String)   = counter("bot.moves").withTag("name", username)
    def chats(username: String)   = counter("bot.chats").withTag("name", username)
    def gameStream(event: String) = counter("bot.gameStream").withTag("event", event)
  }
  object cheat {
    val cssBot                       = counter("cheat.cssBot").withoutTags()
    val holdAlert                    = counter("cheat.holdAlert").withoutTags()
    def autoAnalysis(reason: String) = counter("cheat.autoAnalysis").withTag("reason", reason)
    val autoMark                     = counter("cheat.autoMark.count").withoutTags()
    val autoReport                   = counter("cheat.autoReport.count").withoutTags()
  }
  object email {
    object send {
      private val c           = counter("email.send")
      val resetPassword       = c.withTag("type", "resetPassword")
      val magicLink           = c.withTag("type", "magicLink")
      val reopen              = c.withTag("type", "reopen")
      val fix                 = c.withTag("type", "fix")
      val change              = c.withTag("type", "change")
      val confirmation        = c.withTag("type", "confirmation")
      val time                = timer("email.send.time").withoutTags()
      def error(name: String) = counter("email.error").withTag("name", name)
    }
    val disposableDomain = gauge("email.disposableDomain").withoutTags()
  }
  object security {
    val torNodes = gauge("security.tor.node").withoutTags()
    object firewall {
      val block  = counter("security.firewall.block").withoutTags()
      val ip     = gauge("security.firewall.ip").withoutTags()
      val prints = gauge("security.firewall.prints").withoutTags()
    }
    object proxy {
      def reason(reason: String) = counter("security.proxy.reason").withTag("reason", reason)
      val request                = future("security.proxy.time")
    }
    def rateLimit(key: String) = counter("security.rateLimit.count").withTag("key", key)
    def concurrencyLimit(key: String) =
      counter("security.concurrencyLimit.count").withTag("key", key)
    object dnsApi {
      val mx = future("security.dnsApi.mx.time")
    }
    object checkMailApi {
      def fetch(success: Boolean, block: Boolean) =
        timer("checkMail.fetch").withTags(tags("success" -> successTag(success), "block" -> block))
    }
    def usersAlikeTime(field: String) = timer("security.usersAlike.time").withTag("field", field)
    def usersAlikeFound(field: String) =
      histogram("security.usersAlike.found").withTag("field", field)
  }
  object tv {
    object streamer {
      def present(n: String) = gauge("tv.streamer.present").withTag("name", n)
      def youTube            = future("tv.streamer.youtube")
      def twitch             = future("tv.streamer.twitch")
    }
  }
  object crosstable {
    val create                      = future("crosstable.create.time")
    def createOffer(result: String) = counter("crosstable.create.offer").withTag("result", result)
    val duplicate                   = counter("crosstable.create.duplicate").withoutTags()
    val found                       = counter("crosstable.create.found").withoutTags()
    val createNbGames               = histogram("crosstable.create.nbGames").withoutTags()
  }
  object playTime {
    val create         = future("playTime.create.time")
    val createPlayTime = histogram("playTime.create.playTime").withoutTags()
  }
  object relation {
    private val c = counter("relation.action")
    val follow    = c.withTag("type", "follow")
    val unfollow  = c.withTag("type", "unfollow")
    val block     = c.withTag("type", "block")
    val unblock   = c.withTag("type", "unblock")
  }
  object coach {
    object pageView {
      def profile(coachId: String) = counter("coach.pageView").withTag("name", coachId)
    }
  }
  object clas {
    def studentCreate(teacher: String) = counter("clas.student.create").withTag("teacher", teacher)
    def studentInvite(teacher: String) = counter("clas.student.invite").withTag("teacher", teacher)
  }
  object tournament {
    object pairing {
      val batchSize         = histogram("tournament.pairing.batchSize").withoutTags()
      val create            = future("tournament.pairing.create")
      val createRanking     = timer("tournament.pairing.create.ranking").withoutTags()
      val createPairings    = timer("tournament.pairing.create.pairings").withoutTags()
      val createPlayerMap   = timer("tournament.pairing.create.playerMap").withoutTags()
      val createInserts     = timer("tournament.pairing.create.inserts").withoutTags()
      val createFeature     = timer("tournament.pairing.create.feature").withoutTags()
      val createAutoPairing = timer("tournament.pairing.create.autoPairing").withoutTags()
      val prep              = future("tournament.pairing.prep")
      val wmmatching        = timer("tournament.pairing.wmmatching").withoutTags()
    }
    object arrangement {
      val create          = future("tournament.arrangement.create")
      val createPlayerMap = timer("tournament.arrangement.create.playerMap").withoutTags()
      val createFeature   = timer("tournament.arrangement.create.feature").withoutTags()
    }
    val created        = gauge("tournament.count").withTag("type", "created")
    val started        = gauge("tournament.count").withTag("type", "started")
    val waitingPlayers = histogram("tournament.waitingPlayers").withoutTags()
    object startedArenaOrganizer {
      val tick         = future("tournament.startedArenaOrganizer.tick")
      val waitingUsers = future("tournament.startedArenaOrganizer.waitingUsers")
    }
    object createdOrganizer {
      val tick = future("tournament.createdOrganizer.tick")
    }
    def standingOverload = counter("tournament.standing.overload").withoutTags()
    def apiShowPartial(partial: Boolean, client: String)(success: Boolean) =
      timer("tournament.api.show").withTags(
        tags(
          "partial" -> partial,
          "success" -> successTag(success),
          "client"  -> client,
        ),
      )
  }
  object plan {
    val paypal  = histogram("plan.amount").withTag("service", "paypal")
    val stripe  = histogram("plan.amount").withTag("service", "stripe")
    val goal    = gauge("plan.goal").withoutTags()
    val current = gauge("plan.current").withoutTags()
    val percent = gauge("plan.percent").withoutTags()
  }
  object forum {
    object post {
      val create = counter("forum.post.create").withoutTags()
    }
    object topic {
      val view = counter("forum.topic.view").withoutTags()
    }
    def reaction(r: String) = counter("forum.reaction").withTag("reaction", r)
  }
  object team {
    def massPm(teamId: String) = histogram("team.mass-pm").withTag("from", teamId)
  }
  object puzzle {
    object selector {
      object user {
        def time(theme: String) = timer("puzzle.selector.user.puzzle").withTag("theme", theme)
        def retries(theme: String) =
          histogram("puzzle.selector.user.retries").withTag("theme", theme)
        def vote(theme: String) = histogram("puzzle.selector.user.vote").withTag("theme", theme)
        def ratingDiff(theme: String, difficulty: String) =
          histogram("puzzle.selector.user.ratingDiff").withTags(
            tags("theme" -> theme, "difficulty" -> difficulty),
          )
        def ratingDev(theme: String) =
          histogram("puzzle.selector.user.ratingDev").withTag("theme", theme)
        def tier(t: String, theme: String, difficulty: String) =
          counter("puzzle.selector.user.tier").withTags(
            tags("tier" -> t, "theme" -> theme, "difficulty" -> difficulty),
          )
        def batch(nb: Int) = timer("puzzle.selector.user.batch").withTag("nb", nb)
      }
      object anon {
        def time(theme: String) = timer("puzzle.selector.anon.puzzle").withTag("theme", theme)
        def batch(nb: Int)      = timer("puzzle.selector.anon.batch").withTag("nb", nb)
        def vote(theme: String) = histogram("puzzle.selector.anon.vote").withTag("theme", theme)
      }
      def nextPuzzleResult(theme: String, difficulty: String, result: String) =
        timer("puzzle.selector.user.puzzleResult").withTags(
          tags("theme" -> theme, "difficulty" -> difficulty, "result" -> result),
        )
    }
    object path {
      def nextFor(
          theme: String,
          tier: String,
          difficulty: String,
          previousPaths: Int,
          compromise: Int,
      ) =
        timer("puzzle.path.nextFor").withTags(
          tags(
            "theme"         -> theme,
            "tier"          -> tier,
            "difficulty"    -> difficulty,
            "previousPaths" -> previousPaths.toString,
            "compromise"    -> compromise.toString,
          ),
        )
    }

    object batch {
      object selector {
        val count = counter("puzzle.batch.selector.count").withoutTags()
        val time  = timer("puzzle.batch.selector").withoutTags()
      }
      val solve = counter("puzzle.batch.solve").withoutTags()
    }
    object round {
      def attempt(user: Boolean, theme: String) =
        counter("puzzle.attempt.count").withTags(tags("user" -> user, "theme" -> theme))
    }
    def vote(up: Boolean, win: Boolean) = counter("puzzle.vote.count").withTags(
      tags(
        "up"  -> up,
        "win" -> win,
      ),
    )
    def voteTheme(key: String, up: Option[Boolean], win: Boolean) =
      counter("puzzle.vote.theme").withTags(
        tags(
          "up"    -> up.fold("cancel")(_.toString),
          "theme" -> key,
          "win"   -> win,
        ),
      )
    val crazyGlicko = counter("puzzle.crazyGlicko").withoutTags()
  }
  object storm {
    object selector {
      val time                    = timer("storm.selector.time").withoutTags()
      val count                   = histogram("storm.selector.count").withoutTags()
      val rating                  = histogram("storm.selector.rating").withoutTags()
      def ratingSlice(index: Int) = histogram("storm.selector.ratingSlice").withTag("index", index)
    }
    object run {
      def score(auth: Boolean) = histogram("storm.run.score").withTag("auth", auth)
      def sign(cause: String)  = counter("storm.run.sign").withTag("cause", cause)
    }
  }
  object game {
    def finish(variant: String, speed: String, source: String, mode: String, status: String) =
      counter("game.finish").withTags(
        tags(
          "variant" -> variant,
          "speed"   -> speed,
          "source"  -> source,
          "mode"    -> mode,
          "status"  -> status,
        ),
      )
    val fetch            = counter("game.fetch.count").withoutTags()
    val fetchLight       = counter("game.fetchLight.count").withoutTags()
    val loadClockHistory = counter("game.loadClockHistory.count").withoutTags()
    val idCollision      = counter("game.idCollision").withoutTags()
  }
  object chat {
    def message(parent: String, troll: Boolean) =
      counter("chat.message").withTags(
        tags(
          "parent" -> parent,
          "troll"  -> troll,
        ),
      )
    def fetch(parent: String) = timer("chat.fetch").withTag("parent", parent)
  }
  object push {
    object register {
      def in(platform: String) = counter("push.register").withTag("platform", platform)
      val out                  = counter("push.register.out").withoutTags()
    }
    object send {
      private def send(tpe: String)(platform: String, success: Boolean): Unit = {
        counter("push.send")
          .withTags(
            tags(
              "type"     -> tpe,
              "platform" -> platform,
              "success"  -> successTag(success),
            ),
          )
          .increment()
        ()
      }
      val move        = send("move") _
      val takeback    = send("takeback") _
      val corresAlarm = send("corresAlarm") _
      val finish      = send("finish") _
      val message     = send("message") _
      object challenge {
        val create = send("challengeCreate") _
        val accept = send("challengeAccept") _
      }
    }
    val googleTokenTime = timer("push.send.googleToken").withoutTags()
  }
  object fishnet {
    object client {
      object result {
        private val c = counter("fishnet.client.result")
        private def apply(r: String)(client: String) =
          c.withTags(tags("client" -> client, "result" -> r))
        val success     = apply("success") _
        val failure     = apply("failure") _
        val timeout     = apply("timeout") _
        val notFound    = apply("notFound") _
        val notAcquired = apply("notAcquired") _
        val abort       = apply("abort") _
      }
      def status(enabled: Boolean) = gauge("fishnet.client.status").withTag("enabled", enabled)
      def version(v: String)       = gauge("fishnet.client.version").withTag("version", v)
      def stockfish(v: String)     = gauge("fishnet.client.engine.stockfish").withTag("version", v)
      def python(v: String)        = gauge("fishnet.client.python").withTag("version", v)
    }
    def queueTime(sender: String) = timer("fishnet.queue.db").withTag("sender", sender)
    val acquire                   = future("fishnet.acquire")
    def work(typ: String, as: String) =
      gauge("fishnet.work").withTags(tags("type" -> typ, "for" -> as))
    def oldest(as: String) = gauge("fishnet.oldest").withTag("for", as)
    object move {
      def time(client: String) = timer("fishnet.move.time").withTag("client", client)
      def fullTimeLvl1(client: String) =
        timer("fishnet.move.full_time_lvl_1").withTag("client", client)
      val post   = gauge("fishnet.move.post")
      val dbDrop = gauge("fishnet.move.db_drop")
    }
    object analysis {
      object by {
        def hash(client: String)    = gauge("fishnet.analysis.hash").withTag("client", client)
        def threads(client: String) = gauge("fishnet.analysis.threads").withTag("client", client)
        def movetime(client: String) =
          histogram("fishnet.analysis.movetime").withTag("client", client)
        def node(client: String)   = histogram("fishnet.analysis.node").withTag("client", client)
        def nps(client: String)    = histogram("fishnet.analysis.nps").withTag("client", client)
        def depth(client: String)  = histogram("fishnet.analysis.depth").withTag("client", client)
        def pvSize(client: String) = histogram("fishnet.analysis.pvSize").withTag("client", client)
        def pv(client: String, isLong: Boolean) =
          counter("fishnet.analysis.pvs").withTags(tags("client" -> client, "long" -> isLong))
        def totalMeganode(client: String) =
          counter("fishnet.analysis.total.meganode").withTag("client", client)
        def totalSecond(client: String) =
          counter("fishnet.analysis.total.second").withTag("client", client)
      }
      def requestCount(tpe: String) = counter("fishnet.analysis.request").withTag("type", tpe)
      val evalCacheHits             = histogram("fishnet.analysis.evalCacheHits").withoutTags()
    }
    object http {
      def request(hit: Boolean) = counter("fishnet.http.acquire").withTag("hit", hit)
    }
  }
  object study {
    object tree {
      val read  = timer("study.tree.read").withoutTags()
      val write = timer("study.tree.write").withoutTags()
    }
    object sequencer {
      val chapterTime = timer("study.sequencer.chapter.time").withoutTags()
    }
  }
  object api {
    val userGames = counter("api.cost").withTag("endpoint", "userGames")
    val users     = counter("api.cost").withTag("endpoint", "users")
    val game      = counter("api.cost").withTag("endpoint", "game")
    val activity  = counter("api.cost").withTag("endpoint", "activity")
  }
  object notation {
    val game         = counter("notation.export").withTag("type", "game")
    val study        = counter("notation.export").withTag("type", "study")
    val studyChapter = counter("notation.export").withTag("type", "studyChapter")
  }
  object bus {
    val classifiers       = gauge("bus.classifiers").withoutTags()
    def ask(name: String) = future("bus.ask", name)
  }
  object blocking {
    def time(name: String) = timer("blocking.time").withTag("name", name)
  }
  object workQueue {
    def offerFail(name: String, result: String) =
      counter("workQueue.offerFail").withTags(
        tags(
          "name"   -> name,
          "result" -> result,
        ),
      )
    def timeout(name: String) = counter("workQueue.timeout").withTag("name", name)
  }

  def chronoSync[A] = lila.common.Chronometer.syncMon[A] _

  type TimerPath   = lila.mon.type => Timer
  type CounterPath = lila.mon.type => Counter

  private def timer(name: String)     = kamon.Kamon.timer(name)
  private def gauge(name: String)     = kamon.Kamon.gauge(name)
  private def counter(name: String)   = kamon.Kamon.counter(name)
  private def histogram(name: String) = kamon.Kamon.histogram(name)

  private def future(name: String) = (success: Boolean) =>
    timer(name).withTag("success", successTag(success))
  private def future(name: String, segment: String) =
    (success: Boolean) =>
      timer(name).withTags(
        tags("success" -> successTag(success), "segment" -> segment),
      )

  private def successTag(success: Boolean) = if (success) "success" else "failure"

  implicit def mapToTags(m: Map[String, Any]): TagSet = TagSet from m
}
