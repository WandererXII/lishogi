package lishogi.relay

import shogi.format.Tags
import lishogi.study.{ Chapter, Node, NotationImport }

case class RelayGame(
    index: Int,
    tags: Tags,
    variant: shogi.variant.Variant,
    root: Node.Root,
    end: Option[NotationImport.End]
) {

  def staticTagsMatch(chapterTags: Tags): Boolean =
    RelayGame.staticTags forall { name =>
      chapterTags(name) == tags(name)
    }
  def staticTagsMatch(chapter: Chapter): Boolean = staticTagsMatch(chapter.tags)

  def started = root.children.nodes.nonEmpty

  def finished = end.isDefined

  def isEmpty = tags.value.isEmpty && root.children.nodes.isEmpty

  lazy val looksLikeLishogi = tags(_.Site) exists { site =>
    RelayGame.lishogiDomains exists { domain =>
      site startsWith s"https://$domain/"
    }
  }
}

private object RelayGame {

  val lishogiDomains = List("lishogi.org", "lishogi.dev")

  val staticTags = List("sente", "gote", "round", "event", "site")
}
