import BuildSettings._

PlayKeys.playDefaultPort                       := 9663
PlayKeys.externalizeResources                  := false
com.typesafe.sbt.packager.Keys.scriptClasspath := Seq("*")
Assets / resourceDirectory                     := baseDirectory.value / "public-nothanks"
PlayKeys.generateAssetsJar                     := false
routesGenerator                                := LilaRoutesGenerator
maintainer                                     := "contact@lishogi.org"

lazy val root = Project("lila", file("."))
  .enablePlugins(PlayScala)
  .dependsOn(moduleCPDeps: _*)
  .aggregate(moduleRefs: _*)
  .settings(
    libraryDependencies ++= rootLibs,
    buildSettings,
  )

lazy val moduleRefs   = allModules.map(m => LocalProject(m.id))
lazy val moduleCPDeps = moduleRefs map { new sbt.ClasspathDependency(_, None) }

// format: off
// DO NOT EDIT BELOW THIS LINE
// Auto-generated by 'pnpm --filter @bin/utils run module-deps'

lazy val allModules = Seq(commonM, dbM, memoM, hubM, i18nM, socketM, ratingM, userM, oauthM, securityM, chatM, prefM, gameM, relationM, notifyM, shutupM, treeM, historyM, msgM, evalCacheM, analyseM, puzzleM, roomM, playbanM, fishnetM, roundM, reportM, simulM, perfStatM, evaluationM, tournamentM, importerM, modM, studyM, forumM, searchM, teamM, practiceM, challengeM, prismicM, timelineM, lobbyM, forumSearchM, streamerM, botM, coachM, bookmarkM, studySearchM, planM, teamSearchM, tvM, videoM, activityM, appealM, stormM, clasM, coordinateM, gameSearchM, pushM, blogM, apiM, setupM, eventM, learnM)
lazy val notifyM = module("notify", Seq(gameM))
lazy val reportM = module("report", Seq(playbanM))
lazy val learnM = module("learn", Seq(userM))
lazy val modM = module("mod", Seq(evaluationM, perfStatM, reportM, simulM, tournamentM))
lazy val relationM = module("relation", Seq(prefM))
lazy val evalCacheM = module("evalCache", Seq(securityM, treeM))
lazy val historyM = module("history", Seq(gameM))
lazy val practiceM = module("practice", Seq(studyM))
lazy val analyseM = module("analyse", Seq(gameM, treeM))
lazy val coachM = module("coach", Seq(gameM))
lazy val eventM = module("event", Seq(userM))
lazy val roundM = module("round", Seq(fishnetM, playbanM, roomM))
lazy val commonM = module("common", Seq())
lazy val setupM = module("setup", Seq(lobbyM))
lazy val simulM = module("simul", Seq(roundM))
lazy val hubM = module("hub", Seq(commonM))
lazy val apiM = module("api", Seq(bookmarkM, botM, challengeM, coachM, forumSearchM, lobbyM, planM, streamerM, studySearchM, teamSearchM, timelineM))
lazy val blogM = module("blog", Seq(prismicM, timelineM))
lazy val pushM = module("push", Seq(challengeM))
lazy val roomM = module("room", Seq(chatM))
lazy val memoM = module("memo", Seq(dbM))
lazy val gameSearchM = module("gameSearch", Seq(gameM, searchM))
lazy val securityM = module("security", Seq(oauthM))
lazy val teamSearchM = module("teamSearch", Seq(searchM, teamM))
lazy val bookmarkM = module("bookmark", Seq(gameM))
lazy val treeM = module("tree", Seq(commonM))
lazy val botM = module("bot", Seq(roundM))
lazy val coordinateM = module("coordinate", Seq(userM))
lazy val lobbyM = module("lobby", Seq(roundM))
lazy val userM = module("user", Seq(ratingM, socketM))
lazy val planM = module("plan", Seq(notifyM))
lazy val prismicM = module("prismic", Seq(memoM))
lazy val teamM = module("team", Seq(modM))
lazy val dbM = module("db", Seq(commonM))
lazy val gameM = module("game", Seq(chatM))
lazy val tournamentM = module("tournament", Seq(roundM))
lazy val fishnetM = module("fishnet", Seq(analyseM, evalCacheM, puzzleM))
lazy val prefM = module("pref", Seq(userM))
lazy val searchM = module("search", Seq(commonM))
lazy val timelineM = module("timeline", Seq(relationM, securityM))
lazy val clasM = module("clas", Seq(msgM, puzzleM))
lazy val puzzleM = module("puzzle", Seq(historyM, prefM))
lazy val shutupM = module("shutup", Seq(gameM, relationM))
lazy val stormM = module("storm", Seq(puzzleM))
lazy val chatM = module("chat", Seq(securityM))
lazy val msgM = module("msg", Seq(notifyM, shutupM))
lazy val appealM = module("appeal", Seq(userM))
lazy val ratingM = module("rating", Seq(i18nM, memoM))
lazy val socketM = module("socket", Seq(hubM, memoM))
lazy val importerM = module("importer", Seq(gameM))
lazy val forumSearchM = module("forumSearch", Seq(forumM, searchM))
lazy val activityM = module("activity", Seq(forumM, practiceM, teamM))
lazy val i18nM = module("i18n", Seq(commonM)).settings(
  Compile / sourceGenerators += Def.task {
    MessageCompiler(
      sourceDir = new File("translation/source"),
      destDir = new File("translation/dest"),
      dbs = Seq("faq", "insights", "tourArrangements", "tourname", "streamer", "puzzleTheme", "broadcast", "coach", "patron", "perfStat", "search", "preferences", "pieces", "contact", "tfa", "team", "settings", "arena", "study", "emails", "nvui", "storm", "site", "coordinates", "puzzle", "lag", "activity", "class", "learn"),
      compileTo = (Compile / sourceManaged).value
    )
  }.taskValue
)
lazy val evaluationM = module("evaluation", Seq(analyseM))
lazy val oauthM = module("oauth", Seq(userM))
lazy val videoM = module("video", Seq(userM))
lazy val streamerM = module("streamer", Seq(notifyM))
lazy val tvM = module("tv", Seq(roundM))
lazy val perfStatM = module("perfStat", Seq(gameM))
lazy val studySearchM = module("studySearch", Seq(searchM, studyM))
lazy val studyM = module("study", Seq(importerM, roundM))
lazy val challengeM = module("challenge", Seq(roundM))
lazy val forumM = module("forum", Seq(modM))
lazy val playbanM = module("playban", Seq(msgM))
