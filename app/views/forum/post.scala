package views
package html.forum

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.richText
import lila.forum.Post

object post {

  def recent(posts: List[lila.forum.MiniForumPost])(implicit ctx: Context) =
    ol(
      posts map { p =>
        li(
          a(
            cls   := "post_link",
            href  := routes.ForumPost.redirect(p.postId),
            title := p.topicName,
          )(
            p.isTeam option span(
              cls      := "text",
              dataIcon := Icons.people,
            ),
            span(cls := "post_topic")(shorten(p.topicName, 30)),
            span(cls := "post_text")(shorten(p.text, 70)),
          ),
          userIdLink(p.userId, withOnline = false),
        )
      },
    )

  def show(
      categ: lila.forum.Categ,
      topic: lila.forum.Topic,
      post: lila.forum.Post,
      url: String,
      canModCateg: Boolean,
      canReact: Boolean,
  )(implicit ctx: Context) = {
    st.article(cls := List("forum-post" -> true, "erased" -> post.erased), id := post.number)(
      div(cls := "forum-post__metas")(
        div(
          authorLink(
            post = post,
            cssClass = s"author${(topic.userId == post.userId) ?? " author--op"}".some,
            modIcon = ~post.modIcon,
          ),
          a(href := url)(
            post.updatedAt
              .map { updatedAt =>
                frag(
                  span(cls := "post-edited")("edited "),
                  momentFromNow(updatedAt),
                )
              }
              .getOrElse {
                momentFromNow(post.createdAt)
              },
          ),
          isGranted(_.IpBan) option span(cls := "mod postip")(post.ip),
          ctx.userId.fold(false)(post.shouldShowEditForm(_)) option
            a(cls := "mod edit button button-empty text", dataIcon := Icons.pencil)("Edit"),
          canModCateg option a(
            cls      := "mod delete button button-empty button-red",
            href     := routes.ForumPost.delete(categ.slug, post.id),
            dataIcon := Icons.trashBin,
            title    := "Delete",
          ),
        ),
        a(cls := "anchor", href := url)(s"#${post.number}"),
      ),
      p(cls := "forum-post__message expand-text")(
        if (post.erased) "<erased>"
        else richText(post.text),
      ),
      reactions(post, canReact),
      ctx.userId.exists(post.shouldShowEditForm(_)) option
        postForm(cls := "edit-post-form", action := routes.ForumPost.edit(post.id))(
          textarea(
            bits.dataTopic := topic.id,
            name           := "changes",
            cls            := "post-text-area edit-post-box",
            minlength      := 3,
            required,
          )(post.text),
          div(cls := "edit-buttons")(
            a(
              cls   := "edit-post-cancel",
              href  := routes.ForumPost.redirect(post.id),
              style := "margin-left:20px",
            )(
              trans.cancel(),
            ),
            submitButton(cls := "button")(trans.apply()),
          ),
        ),
    )
  }

  def reactions(post: Post, canReact: Boolean)(implicit ctx: Context) = {
    val mine = ctx.me ?? { Post.reactionsOf(~post.reactions, _) }
    div(cls := List("reactions" -> true, "reactions-auth" -> (ctx.isAuth && canReact)))(
      Post.reactionsList.map { r =>
        val users = ~post.reactions.flatMap(_ get r)
        val size  = users.size
        button(
          dataHref := (ctx.isAuth && canReact) option routes.ForumPost
            .react(post.id, r, !mine(r))
            .url,
          cls := List("mine" -> mine(r), "yes" -> (size > 0), "no" -> (size < 1)),
          title := {
            if (size > 0) {
              val who =
                if (size > 10) s"${users take 8 mkString ", "} and ${size - 8} others"
                else users mkString ", "
              s"$who reacted with $r"
            } else r
          },
        )(
          img(src := assetUrl(s"images/emoji/$r.png"), alt := r),
          size > 0 option size,
        )
      },
    )
  }
}
