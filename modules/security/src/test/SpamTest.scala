package lila.security

import org.specs2.mutable.Specification

import lila.common.Strings

class SpamTest extends Specification {

  val spam   = new Spam(() => Strings(Nil))
  val foobar = """foo bar"""
  val _c2    = """https://chess24.com?ref=masterpart"""

  "spam" should {
    "detect" in {
      spam.detect(foobar) must beFalse
      spam.detect(_c2) must beTrue
    }
    "ignore empty keywords" in {
      val iso = lila.common.Iso.strings(",")
      iso.from("").value must beEmpty
      iso.from("foo, ,,bar").value must_== List("foo", "bar")
      new Spam(() => iso.from("")).detect(foobar) must beFalse
      new Spam(() => iso.from("baz, ,")).detect(foobar) must beFalse
    }
    "replace" in {
      spam.replace(foobar) must_== foobar
      spam.replace(_c2) must_== """https://chess24.com"""
    }
  }
}
