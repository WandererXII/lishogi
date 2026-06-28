package lila.article

import com.vladsch.flexmark.ast._
import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html._
import com.vladsch.flexmark.html.renderer._
import com.vladsch.flexmark.parser._
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataHolder
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.html.MutableAttributes
import com.vladsch.flexmark.util.misc.Extension

object Markdown {

  type Key = String

  private val extensions = {
    val l = new java.util.ArrayList[Extension]()
    l.add(AnchorLinkExtension.create())
    l.add(TablesExtension.create())
    l.add(StrikethroughExtension.create())
    l.add(AutolinkExtension.create())
    l.add(LilaLinkExtension)
    l
  }

  private val options = {
    val o = new MutableDataSet()
      .set(Parser.EXTENSIONS, extensions)
      .set(HtmlRenderer.ESCAPE_HTML, Boolean.box(true))
      .set(HtmlRenderer.UNESCAPE_HTML_ENTITIES, Boolean.box(false))
      .set(HtmlRenderer.SOFT_BREAK, "<br/>")
      .set(Parser.FENCED_CODE_BLOCK_PARSER, Boolean.box(true))
      .set(Parser.HTML_BLOCK_PARSER, Boolean.box(false))
      .set(Parser.INDENTED_CODE_BLOCK_PARSER, Boolean.box(false))
      .set(AnchorLinkExtension.ANCHORLINKS_WRAP_TEXT, Boolean.box(false))
      .set(TablesExtension.CLASS_NAME, "slist")

    o.toImmutable
  }

  private val parser   = Parser.builder(options).build()
  private val renderer = HtmlRenderer.builder(options).build()

  def render(text: String, key: Key = "markdown"): String =
    lila.common.Chronometer
      .sync {
        try {
          val saferText    = preventStackOverflow(text)
          val withMentions = mentionsToLinks(saferText)
          renderer.render(parser.parse(withMentions))
        } catch {
          case e: StackOverflowError =>
            logger.branch(key).error("StackOverflowError", e)
            text
        }
      }
      .logIfSlow(75, logger.branch(key))(_ => s"slow markdown size:${text.length}")
      .result

  private def mentionsToLinks(markdown: String): String =
    lila.common.String.atUsernameRegex.replaceAllIn(markdown, "[@$1](/@/$1)")

  def unlink(text: String): String =
    text.replaceAll(raw"""(?i)!?\[([^\]\n]*)\]\([^)]*\)""", "[$1]")

  private val tooManyUnderscoreRegex = """(_{6,})""".r
  private val tooManyQuotes          = """^\s*(>\s*){5,}""".r

  private def preventStackOverflow(text: String): String =
    tooManyUnderscoreRegex
      .replaceAllIn(text, "_" * 3)
      .linesIterator
      .map { line =>
        if (line.count(_ == '>') > 15) line.replaceAll(">", "").trim
        else tooManyQuotes.replaceAllIn(line, "> " * 5)
      }
      .mkString("\n")

  private object LilaLinkExtension extends HtmlRenderer.HtmlRendererExtension {
    override def rendererOptions(options: MutableDataHolder): Unit = ()
    override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
      htmlRendererBuilder
        .attributeProviderFactory(new IndependentAttributeProviderFactory {
          override def apply(context: LinkResolverContext): AttributeProvider =
            lilaLinkAttributeProvider
        })
        .unit
    }
  }

  private val rel = "nofollow noreferrer"

  private val lilaLinkAttributeProvider = new AttributeProvider {
    override def setAttributes(
        node: Node,
        part: AttributablePart,
        attributes: MutableAttributes,
    ): Unit = {
      if (
        (node.isInstanceOf[Link] || node.isInstanceOf[AutoLink]) &&
        part == AttributablePart.LINK
      ) {
        attributes.replaceValue("target", "_blank").unit
        attributes.replaceValue("rel", rel).unit
        attributes
          .replaceValue(
            "href",
            removeUrlTrackingParameters(attributes.getValue("href")),
          )
          .unit
      }
    }
  }

  private val trackingParametersRegex =
    """(?i)(?:\?|&(?:amp;)?)(?:utm_\w+|gclid|gclsrc|_ga)=\w+""".r

  private def removeUrlTrackingParameters(url: String): String =
    trackingParametersRegex.replaceAllIn(url, "")
}
