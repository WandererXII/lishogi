package lila.streamer

import scala.concurrent.duration._

import akka.actor._
import akka.pattern.ask
import alleycats.Zero

import lila.memo.CacheApi._
import lila.streamer.makeTimeout.short
import lila.user.User

case class LiveStreams(streams: List[Stream]) {

  private lazy val streamerIds: Set[Streamer.Id] = streams.view.map(_.streamer.id).to(Set)

  def has(id: Streamer.Id): Boolean    = streamerIds(id)
  def has(streamer: Streamer): Boolean = has(streamer.id)

  def get(streamer: Streamer) = streams.find(_ is streamer)

  def homepage(max: Int) =
    LiveStreams {
      streams
        .take(max)
        .filter(_.streamer.approval.tier > 0)
    }
}

object LiveStreams {

  implicit val zero: Zero[LiveStreams] = Zero(LiveStreams(Nil))
}

final class LiveStreamApi(
    cacheApi: lila.memo.CacheApi,
    streamingActor: ActorRef,
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val cache = cacheApi.unit[LiveStreams] {
    _.refreshAfterWrite(3 seconds)
      .buildAsyncFuture { _ =>
        streamingActor ? Streaming.Get mapTo manifest[LiveStreams] dmap { s =>
          LiveStreams(s.streams.sortBy(-_.streamer.approval.tier))
          // useForTesting(s)
        } addEffect { s =>
          userIdsCache = s.streams.map(_.streamer.userId).toSet
        }
      }
  }
  private var userIdsCache = Set.empty[User.ID]

  def all: Fu[LiveStreams] = cache.getUnit

  // import org.joda.time.DateTime
  // def useForTesting(s: LiveStreams) = LiveStreams(
  //   s.streams ::: List(
  //     Stream.Twitch.Stream(
  //       "test",
  //       "Playing shogi on lishogi.org, come and watch!",
  //       Streamer(
  //         _id = Streamer.Id("test"),
  //         listed = Streamer.Listed(true),
  //         approval = Streamer.Approval(
  //           requested = false,
  //           granted = true,
  //           ignored = false,
  //           tier = 5,
  //           chatEnabled = true,
  //           lastGrantedAt = DateTime.now.some,
  //         ),
  //         picturePath = Streamer.PicturePath("test/c288da66-bbda-4c24-8be9-62b4872ccbc5").some,
  //         name = Streamer.Name("Mr. Streamer"),
  //         headline = Streamer.Headline("Streaming shogi for fun").some,
  //         description = Streamer.Description("Very long description for a very good streamer").some,
  //         twitch = Streamer.Twitch("test").some,
  //         youTube = Streamer.YouTube("UCtesttesttesttesttestte").some,
  //         seenAt = DateTime.now,
  //         liveAt = DateTime.now.some,
  //         createdAt = DateTime.now,
  //         updatedAt = DateTime.now,
  //       ),
  //     ),
  //   ),
  // )

  def of(s: Streamer.WithUser): Fu[Streamer.WithUserAndStream] =
    all.map { live =>
      Streamer.WithUserAndStream(s.streamer, s.user, live get s.streamer)
    }
  def userIds                                  = userIdsCache
  def isStreaming(userId: User.ID)             = userIdsCache contains userId
  def one(userId: User.ID): Fu[Option[Stream]] = all.map(_.streams.find(_ is userId))
  def many(userIds: Seq[User.ID]): Fu[List[Stream]] =
    all.map(_.streams.filter(s => userIds.exists(s.is)))
}
