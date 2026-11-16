package lila.study

import scala.util.chaining._

import shogi.format.Tag
import shogi.format.TagType
import shogi.format.Tags

object StudyTags {

  def apply(tags: Tags): Tags =
    tags pipe filterRelevant pipe removeContradictingTermination pipe sort

  def setRootClockFromTags(c: Chapter): Option[Chapter] =
    c.updateRoot {
      _.setClockAt(lila.game.clock.NotationClock.config(c.tags) map (_.limit), Path.root)
    } filter (c !=)

  // just so that csa output isn't all ugly...
  def tagsToAscii(tags: Tags): Tags =
    Tags(
      tags.value.map(t =>
        t.copy(value = t.value.replace("級", "kyu").filter(c => c >= 32 && c < 127)),
      ),
    )

  private def filterRelevant(tags: Tags) =
    Tags(tags.value.filter { t =>
      relevantTypeSet(t.name) && !unknownValues(t.value)
    })

  private def removeContradictingTermination(tags: Tags) =
    if (tags.resultColor.isDefined)
      Tags(tags.value.filterNot { t =>
        t.name == Tag.Termination && t.value.toLowerCase == "unterminated"
      })
    else tags

  private val unknownValues = Set("", "?", "unknown", " ")

  private val sortedTypes: List[TagType] = {
    import Tag._
    List(
      Start,
      End,
      Site,
      Event,
      TimeControl,
      Byoyomi,
      Sente,
      SenteElo,
      SenteTitle,
      SenteTeam,
      Gote,
      GoteElo,
      GoteTitle,
      GoteTeam,
      Opening,
      Result,
      ProblemName,
      ProblemId,
      DateOfPublication,
      Composer,
      Publication,
      Collection,
      Length,
      Prize,
    )
  }

  val typesToString = sortedTypes.map(_.name) mkString ","

  private val relevantTypeSet: Set[TagType] = sortedTypes.toSet

  private val typePositions: Map[TagType, Int] = sortedTypes.zipWithIndex.toMap

  private def sort(tags: Tags) =
    Tags {
      tags.value.sortBy { t =>
        typePositions.getOrElse(t.name, Int.MaxValue)
      }
    }
}
