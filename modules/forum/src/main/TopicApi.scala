package lila.forum

import lila.common.Bus
import lila.common.String.noShouting
import lila.common.paginator._
import lila.db.dsl._
import lila.db.paginator._
import lila.forum.actorApi._
import lila.hub.actorApi.timeline.ForumPost
import lila.hub.actorApi.timeline.Propagate
import lila.security.{ Granter => MasterGranter }
import lila.user.User
import lila.user.UserContext

final private[forum] class TopicApi(
    env: Env,
    indexer: lila.hub.actors.ForumSearch,
    maxPerPage: lila.common.config.MaxPerPage,
    modLog: lila.mod.ModlogApi,
    spam: lila.security.Spam,
    timeline: lila.hub.actors.Timeline,
    shutup: lila.hub.actors.Shutup,
    detectLanguage: lila.common.DetectLanguage,
)(implicit ec: scala.concurrent.ExecutionContext) {

  import BSONHandlers._

  def show(
      categSlug: String,
      slug: String,
      page: Int,
      forUser: Option[User],
  ): Fu[Option[(Categ, Topic, Paginator[Post])]] =
    for {
      data <- env.categRepo bySlug categSlug flatMap {
        _ ?? { categ =>
          env.topicRepo.forUser(forUser).byTree(categSlug, slug) dmap {
            _ map (categ -> _)
          }
        }
      }
      res <- data ?? { case (categ, topic) =>
        lila.mon.forum.topic.view.increment()
        env.postApi.paginator(topic, page, forUser) map { (categ, topic, _).some }
      }
    } yield res

  def makeTopic(
      categ: Categ,
      data: DataForm.TopicData,
      me: User,
  )(implicit ctx: UserContext): Fu[Topic] =
    env.topicRepo.nextSlug(categ, data.name) zip detectLanguage(data.post.text) flatMap {
      case (slug, lang) =>
        val topic = Topic.make(
          categId = categ.slug,
          slug = slug,
          name = noShouting(data.name),
          userId = me.id,
          troll = me.marks.troll,
          hidden = categ.quiet || data.looksLikeVenting,
        )
        val post = Post.make(
          topicId = topic.id,
          author = none,
          userId = ctx.me map (_.id),
          ip = ctx.isAnon option ctx.req.remoteAddress,
          troll = ctx.troll,
          hidden = topic.hidden,
          text = spam.replace(data.post.text),
          lang = lang map (_.language),
          number = 1,
          categId = categ.id,
          modIcon = (~data.post.modIcon && ~ctx.me.map(MasterGranter(_.PublicMod))).option(true),
        )
        env.postRepo.coll.insert.one(post) >>
          env.topicRepo.coll.insert.one(topic withPost post) >>
          env.categRepo.coll.update.one($id(categ.id), categ withTopic post) >>-
          (!categ.quiet ?? (indexer ! InsertPost(post))) >>-
          (!categ.quiet ?? env.recent.invalidate()) >>-
          ctx.userId.?? { userId =>
            val text = s"${topic.name} ${post.text}"
            shutup ! {
              if (post.isTeam) lila.hub.actorApi.shutup.RecordTeamForumMessage(userId, text)
              else lila.hub.actorApi.shutup.RecordPublicForumMessage(userId, text)
            }
          } >>- {
            (ctx.userId ifFalse post.troll ifFalse categ.quiet) ?? { userId =>
              timeline ! Propagate(ForumPost(userId, topic.id.some, topic.name, post.id))
                .toFollowersOf(userId)
            }
            lila.mon.forum.post.create.increment().unit
          } >>- {
            env.mentionNotifier.notifyMentionedUsers(post, topic)
            Bus.publish(actorApi.CreatePost(post), "forumPost")
          } inject topic
    }

  def makeBlogDiscuss(categ: Categ, slug: String, name: String, url: String): Funit = {
    val topic = Topic.make(
      categId = categ.slug,
      slug = slug,
      name = name,
      troll = false,
      userId = User.lishogiId,
      hidden = false,
    )
    val post = Post.make(
      topicId = topic.id,
      author = none,
      userId = User.lishogiId.some,
      ip = none,
      troll = false,
      hidden = false,
      text = s"Comments on $url",
      lang = none,
      number = 1,
      categId = categ.id,
      modIcon = true.some,
    )
    env.postRepo.coll.insert.one(post) >>
      env.topicRepo.coll.insert.one(topic withPost post) >>
      env.categRepo.coll.update.one($id(categ.id), categ withTopic post) >>-
      (indexer ! InsertPost(post)) >>-
      env.recent.invalidate() >>-
      Bus.publish(actorApi.CreatePost(post), "forumPost") void
  }

  def paginator(categ: Categ, page: Int, forUser: Option[User]): Fu[Paginator[TopicView]] = {
    val adapter = new Adapter[Topic](
      collection = env.topicRepo.coll,
      selector = env.topicRepo.forUser(forUser) byCategNotStickyQuery categ,
      projection = none,
      sort = $sort.updatedDesc,
    ) mapFutureList { topics =>
      env.postRepo.coll.optionsByOrderedIds[Post, String](topics.map(_ lastPostId forUser))(
        _.id,
      ) map { posts =>
        topics zip posts map { case (topic, post) =>
          TopicView(categ, topic, post, env.postApi lastPageOf topic, forUser)
        }
      }
    }
    val cachedAdapter =
      if (categ.isTeam) adapter
      else new CachedAdapter(adapter, nbResults = fuccess(1000))
    Paginator(
      adapter = cachedAdapter,
      currentPage = page,
      maxPerPage = maxPerPage,
    )
  }

  def getSticky(categ: Categ, forUser: Option[User]): Fu[List[TopicView]] =
    env.topicRepo.stickyByCateg(categ) flatMap { topics =>
      topics.map { topic =>
        env.postRepo.coll.byId[Post](topic lastPostId forUser) map { post =>
          TopicView(categ, topic, post, env.postApi lastPageOf topic, forUser)
        }
      }.sequenceFu
    }

  def delete(categ: Categ, topic: Topic): Funit =
    env.postRepo.idsByTopicId(topic.id) flatMap { postIds =>
      (env.postRepo removeByTopic topic.id zip env.topicRepo.coll.delete.one($id(topic.id))) >>
        (env.categApi denormalize categ) >>-
        (indexer ! RemovePosts(postIds)) >>-
        env.recent.invalidate()
    }

  def toggleClose(categ: Categ, topic: Topic, mod: User): Funit =
    env.topicRepo.close(topic.id, topic.open) >> {
      MasterGranter(_.ModerateForum)(mod) ??
        modLog.toggleCloseTopic(mod.id, categ.name, topic.name, topic.open)
    }

  def toggleHide(categ: Categ, topic: Topic, mod: User): Funit =
    env.topicRepo.hide(topic.id, topic.visibleOnHome) >> {
      MasterGranter(_.ModerateForum)(mod) ?? {
        env.postRepo.hideByTopic(topic.id, topic.visibleOnHome) >>
          modLog.toggleHideTopic(mod.id, categ.name, topic.name, topic.visibleOnHome)
      } >>- env.recent.invalidate()
    }

  def toggleSticky(categ: Categ, topic: Topic, mod: User): Funit =
    env.topicRepo.sticky(topic.id, !topic.isSticky) >> {
      MasterGranter(_.ModerateForum)(mod) ??
        modLog.toggleStickyTopic(mod.id, categ.name, topic.name, topic.isSticky)
    }

  def denormalize(topic: Topic): Funit =
    for {
      nbPosts       <- env.postRepo countByTopic topic
      lastPost      <- env.postRepo lastByTopic topic
      nbPostsTroll  <- env.postRepo.unsafe countByTopic topic
      lastPostTroll <- env.postRepo.unsafe lastByTopic topic
      _ <-
        env.topicRepo.coll.update
          .one(
            $id(topic.id),
            topic.copy(
              nbPosts = nbPosts,
              lastPostId = lastPost ?? (_.id),
              updatedAt = lastPost.fold(topic.updatedAt)(_.createdAt),
              nbPostsTroll = nbPostsTroll,
              lastPostIdTroll = lastPostTroll ?? (_.id),
              updatedAtTroll = lastPostTroll.fold(topic.updatedAtTroll)(_.createdAt),
            ),
          )
          .void
    } yield ()
}
