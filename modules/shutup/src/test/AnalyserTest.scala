package lishogi.shutup

import org.specs2.mutable._

class AnalyserTest extends Specification {

  private def find(t: String)  = Analyser(t).badWords
  private def dirty(t: String) = Analyser(t).dirty
  private def ratio(t: String) = Analyser(t).ratio

  "detector" should {
    "find one bad word" in {
      find("fuck") must_== List("fuck")
      find("well fuck me") must_== List("fuck")
    }
    "find many bad words" in {
      find("fucked that shit") must_== List("fucked", "shit")
      find("Beat them cunting nigger faggots with a communist dick") must_==
        List("cunting", "nigger", "faggots", "dick")
    }
    "find no bad words" in {
      find("") must_== Nil
      find("hello there") must_== Nil
      find(
        "A sonnet is a poetic form which originated in Italy; Giacomo Da Lentini is credited with its invention."
      ) must_== Nil
      find("computer analysis") must_== Nil
    }
    "find badly spelled words" in {
      find("fuk") must_== List("fuk")
      find("well fuk me") must_== List("fuk")
      find("foo ashole bar fukd") must_== List("ashole", "fukd")
    }
    "find variants" in {
      find("cunt kunt cunting kawa kunting") must_== List("cunt", "kunt", "cunting", "kunting")
      find("ass as ashole") must_== List("ass", "ashole")
    }
    "find plurals" in {
      find("asses cunts kunts cuntings kawas kuntings") must_== List(
        "asses",
        "cunts",
        "kunts",
        "cuntings",
        "kuntings"
      )
    }
    "find phrases" in {
      find("I think you suck") must_== List("you suck")
      find("you should suck my") must_== List("suck my")
    }
    "50 shades of fuck" in {
      find("fuck fffuuk fektard feak fak phuk") must_== List("fuck", "fffuuk", "fektard", "fak", "phuk")
    }
    "compute ratio" in {
      ratio("fuck that shit") must_== 2d / 3
      ratio("Beat them cunting nigger faggots with a communist dick") must_== 4d / 9
      ratio("hello there") must_== 0d
      ratio("") must_== 0d
    }
    "shogibot variations" in {
      find("foo shogibot bar") must_== List("shogibot")
      find("foo shogi-bot bar") must_== List("shogi-bot")
      find("foo shogi_bot bar") must_== List("shogi_bot")
      find("foo shogi-bot.com bar") must_== List("shogi-bot.com")
      find("foo shogi_bot_com bar") must_== List("shogi_bot_com")
      find("foo shogibotcom bar") must_== List("shogibotcom")
      find("foo http://shogi-bot.com bar") must_== List("shogi-bot.com")
    }
    "russian chars" in {
      find("sеx") must_== List("sex")
    }
    "russian" in {
      find("сука пизда") must_== List("сука", "пизда")
    }
    "japanese" in {
      find("ばか") must_== List("ばか")
    }
  }
  "dirty" should {
    "prod msg" in {
      dirty(
        """Hello fucking arab. It's morning here I am getting ready to fuck your smelly mom and sister together today. Just wanna inform you ;)"""
      ) must beTrue
    }
  }
}
