package lila.article

import org.specs2.mutable.Specification

class MarkdownTest extends Specification {

  def render(text: String) = Markdown.render(text)

  "Markdown.render" should {

    "render basic paragraph" in {
      render("Hello world") must contain("Hello world")
    }

    "escape raw HTML" in {
      render("<script>alert('xss')</script>") must not contain "<script>"
    }

    "render bold and italic" in {
      render("**bold**") must contain("<strong>bold</strong>")
      render("*italic*") must contain("<em>italic</em>")
    }

    "render strikethrough" in {
      render("~~struck~~") must contain("<del>struck</del>")
    }

    "render a table with slist class" in {
      val table = "| A | B |\n|---|---|\n| 1 | 2 |"
      render(table) must contain("class=\"slist\"")
    }

    "render fenced code blocks" in {
      render("```\nval x = 1\n```") must contain("<code>")
    }

    "not render indented code blocks" in {
      render("    val x = 1") must not contain "<code>"
    }

    "convert soft breaks to <br/>" in {
      render("line one\nline two") must contain("<br/>")
    }

    "convert @mentions to profile links" in {
      render("hello @Magnus") must contain("""href="/@/Magnus"""")
    }

    "handle @mention with underscores in username" in {
      render("@chess_player123") must contain("""href="/@/chess_player123"""")
    }

    "add target=_blank to links" in {
      render("[lishogi](https://lishogi.org)") must contain("""target="_blank"""")
    }

    "add rel=nofollow noreferrer to links" in {
      render("[lishogi](https://lishogi.org)") must contain("""rel="nofollow noreferrer"""")
    }

    "strip UTM tracking parameters from links" in {
      render("[link](https://example.com?utm_source=newsletter)") must not contain "utm_source"
    }

    "strip multiple tracking parameters" in {
      val url = "https://example.com?utm_source=x&utm_medium=y&gclid=abc"
      val out = render(s"[link]($url)")
      out must not contain "utm_source"
      out must not contain "utm_medium"
      out must not contain "gclid"
    }

    "preserve non-tracking query parameters" in {
      render("[link](https://example.com?page=2)") must contain("page=2")
    }

    "handle autolinks" in {
      render("Visit https://lishogi.org today") must contain("""href="https://lishogi.org"""")
    }

    "add target=_blank to autolinks" in {
      render("https://lishogi.org") must contain("""target="_blank"""")
    }

    "add anchor links to headings" in {
      render("## My Section") must contain("""id="my-section"""")
    }

    "prevent stack overflow from excessive underscores" in {
      val manyUnderscores = "_" * 100
      render(manyUnderscores) must not(throwAn[StackOverflowError])
    }

    "prevent stack overflow from deeply nested quotes" in {
      val deepQuotes = "> " * 30 + "text"
      render(deepQuotes) must not(throwA[StackOverflowError])
    }

    "cap nested blockquotes at 5 levels" in {
      val deepQuotes = "> " * 20 + "text"
      val out        = render(deepQuotes)
      out.sliding(2).count(_ == "> ") must be lessThan 6
    }

    "not render HTML block tags" in {
      render("<div>hello</div>") must not contain "<div>"
    }

    "render an empty string without error" in {
      render("") must beEmpty
    }
  }

  "Markdown.unlink" should {

    "remove link URLs but keep label" in {
      Markdown.unlink("[click here](https://example.com)") mustEqual "[click here]"
    }

    "remove image syntax but keep alt text" in {
      Markdown.unlink("![alt text](https://example.com/img.png)") mustEqual "[alt text]"
    }

    "leave plain text unchanged" in {
      Markdown.unlink("just some text") mustEqual "just some text"
    }

    "handle multiple links in one string" in {
      Markdown.unlink("[a](url1) and [b](url2)") mustEqual "[a] and [b]"
    }
  }
}
