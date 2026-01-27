package views.html.user.show

import controllers.routes

import lila.api.Context
import lila.app.mashup.UserInfo.Angle
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.richText
import lila.user.User

object header {

  private val dataToints = attr("data-toints")
  private val dataTab    = attr("data-tab")

  private def buttonRack(
      u: User,
      social: lila.app.mashup.UserInfo.Social,
  )(implicit ctx: Context) = {
    val isSystem = u.id == User.lishogiId

    val more = div(cls := "none click-menu")(
      if (ctx is u)
        frag(
          a(
            href     := routes.Game.exportByUser(u.username),
            dataIcon := Icons.download,
          )(trans.exportGames()),
          a(
            href     := routes.Relation.following(u.username, 1),
            dataIcon := Icons.people,
          )(trans.following()),
          a(
            href     := routes.Relation.followers(u.username, 1),
            dataIcon := Icons.people,
          )(trans.followers()),
          a(
            href     := routes.User.opponents,
            dataIcon := Icons.people,
          )(trans.favoriteOpponents()),
          a(
            href     := routes.Relation.blocks(),
            dataIcon := Icons.forbidden,
          )(trans.listBlockedPlayers()),
        )
      else
        frag(
          a(
            href     := routes.Msg.convo(u.id),
            dataIcon := Icons.talk,
          )(trans.composeMessage()),
          social.relation match {
            case None =>
              frag(
                social.followable && !social.blocked option a(
                  cls      := "relation-button",
                  href     := routes.Relation.follow(u.id),
                  dataIcon := Icons.thumbsUp,
                )(trans.follow()),
                !isSystem option a(
                  cls      := "relation-button",
                  href     := routes.Relation.block(u.id),
                  dataIcon := Icons.forbidden,
                )(trans.block()),
              )
            case Some(true) =>
              a(
                cls      := "relation-button",
                dataIcon := Icons.thumbsUp,
                href     := routes.Relation.unfollow(u.id),
              )(trans.following())
            case Some(false) =>
              a(
                cls      := "relation-button",
                dataIcon := Icons.forbidden,
                href     := routes.Relation.unblock(u.id),
              )(trans.blocked())
          },
          (ctx.isAuth && ctx.noKid && !isSystem) option a(
            href     := s"${routes.Report.form}?username=${u.username}",
            dataIcon := Icons.warning,
          )(trans.reportXToModerators(u.username)),
        ),
      !isSystem option a(
        href     := routes.User.tv(u.username),
        dataIcon := Icons.television,
      )(trans.watchGames()),
    )

    div(cls := "user-actions btn-rack")(
      isGranted(_.UserSpy) option
        a(
          cls      := "btn-rack__btn mod-zone-toggle",
          href     := routes.User.mod(u.username),
          dataIcon := Icons.agent,
        ),
      (ctx is u) option
        a(
          cls      := "btn-rack__btn text",
          href     := routes.Account.profile,
          dataIcon := Icons.gear,
        )(trans.editProfile()),
      (!ctx.is(u) && !social.blocked && !isSystem) option
        a(
          href     := s"${routes.Lobby.home}?user=${u.id}#friend",
          cls      := "btn-rack__btn text",
          dataIcon := Icons.challenge,
        )(trans.challengeToPlay()),
      a(
        cls      := "btn-rack__btn click-menu-open",
        dataIcon := Icons.ellipsis,
      ),
      more,
    )
  }

  def stats(
      u: User,
      info: lila.app.mashup.UserInfo,
  )(implicit ctx: Context) =
    div(cls := "stats")(
      info.completionRatePercent.map { c =>
        div(cls := "stats_badge text", dataIcon := Icons.challenge)(
          trans.gameCompletionRate(timeTag(s"$c%")),
        )
      },
      u.playTime.map { playTime =>
        frag(
          div(cls := "stats_badge text", dataIcon := Icons.clock)(
            trans.tpTimeSpentPlaying(timeTag(showPeriod(playTime.totalPeriod))),
          ),
          playTime.nonEmptyTvPeriod.map { tvPeriod =>
            div(cls := "stats_badge text", dataIcon := Icons.television)(
              trans.tpTimeSpentOnTV(timeTag(showPeriod(tvPeriod))),
            )
          },
        )
      },
      (info.insightsVisible || isGranted(_.SeeInsights)) option
        a(
          cls      := "insights",
          href     := routes.Insights.user(u.username, ""),
          dataIcon := Icons.target,
        )(
          span(
            strong(trans.insights.insights()),
          ),
        ),
    )

  def subHeader(
      u: User,
      info: lila.app.mashup.UserInfo,
      social: lila.app.mashup.UserInfo.Social,
  )(implicit ctx: Context) =
    div(cls := "user-show__sub-header")(
      div(cls := "number-menu")(
        u.noBot option a(
          href       := routes.UserTournament.ofPlayer("recent", u.username.some),
          cls        := "nm-item tournament_stats",
          dataToints := u.toints,
        )(
          splitNumber(trans.nbTournamentPoints.pluralSame(u.toints)),
        ),
        a(href := routes.Study.byOwnerDefault(u.username), cls := "nm-item")(
          splitNumber(trans.`nbStudies`.pluralSame(info.nbStudies)),
        ),
        a(
          cls  := "nm-item",
          href := ctx.noKid option routes.ForumPost.search("user:" + u.username, 1).url,
        )(
          splitNumber(trans.nbForumPosts.pluralSame(info.nbPosts)),
        ),
        (ctx.isAuth && ctx.noKid && !ctx.is(u)) option
          a(cls := "nm-item note-zone-toggle")(splitNumber(s"${social.notes.size} Notes")),
      ),
      buttonRack(u, social),
    )

  def notes(
      u: User,
      social: lila.app.mashup.UserInfo.Social,
  )(implicit ctx: Context) =
    (ctx.noKid && !ctx.is(u)) option div(cls := "note-zone")(
      postForm(action := s"${routes.User.writeNote(u.username)}?note")(
        textarea(
          name        := "text",
          placeholder := trans.writeAPrivateNoteAboutThisUser.txt(),
        ),
        if (isGranted(_.ModNote))
          div(cls := "mod-note")(
            submitButton(cls := "button")(trans.send()),
            div(
              div(form3.cmnToggle("note-mod", "mod", true)),
              label(`for` := "note-mod")("For moderators only"),
            ),
            isGranted(_.Doxing) option div(
              div(form3.cmnToggle("note-dox", "dox", false)),
              label(`for` := "note-dox")("Doxing info"),
            ),
          )
        else
          frag(
            input(tpe := "hidden", name := "mod", value := "false"),
            submitButton(cls := "button")(trans.send()),
          ),
      ),
      social.notes.isEmpty option div(trans.noNoteYet()),
      social.notes
        .filter { n =>
          ctx.me.exists(n.isFrom) ||
          isGranted(_.Doxing) ||
          (!n.dox && isGranted(_.ModNote))
        }
        .map { note =>
          div(cls := "note")(
            p(cls := "note__text")(richText(note.text)),
            p(cls := "note__meta")(
              showUsernameById(note.from.some),
              br,
              note.dox option "dox ",
              momentFromNow(note.date),
              (ctx.me.exists(note.isFrom) && !note.mod) option frag(
                br,
                postForm(action := routes.User.deleteNote(note._id))(
                  submitButton(
                    cls      := "button-empty button-red confirm button text",
                    style    := "float:right",
                    dataIcon := Icons.trashBin,
                  )(trans.delete()),
                ),
              ),
            ),
          )
        },
    )

  def times(
      u: User,
  )(implicit ctx: Context) =
    div(cls := "profile-times")(
      p(cls := "thin text")(
        trans.memberSince(),
        " ",
        timeTag(showDate(u.createdAt)),
      ),
      u.seenAt.map { seen =>
        p(cls := "thin text")(
          trans.lastSeenActive(momentFromNow(seen)),
        )
      },
    )

  def profileSide(
      u: User,
      info: lila.app.mashup.UserInfo,
  )(implicit ctx: Context) = {
    val profile = u.profileOrDefault
    div(id := "us_profile")(
      if (info.ratingChart.isDefined && (!u.lame || ctx.is(u) || isGranted(_.UserSpy)))
        views.html.user.bits.ratingHistoryContainer
      else
        div(cls := "rating-history-container-stub")(
          if (ctx.is(u))
            a(href := routes.User.newPlayer)(
              trans.welcomeToX("lishogi.org"),
            )
          else p(trans.clas.nothingHere()),
        ),
      div(cls := "profile-side")(
        div(cls := "user-infos")(
          !ctx.is(u) option frag(
            u.lame option div(cls := "warning tos_warning")(
              span(dataIcon := Icons.error, cls := "is4"),
              trans.thisAccountViolatedTos(),
            ),
          ),
          if (ctx.noKid && (!u.marks.troll || ctx.is(u)))
            frag(
              div(cls := "ps-personal")(
                strong(cls := "name text", dataIcon := Icons.person)(
                  raw(profile.nonEmptyRealName(u.realLang).getOrElse(u.username)),
                ),
                profile.nonEmptyLocation.ifTrue(ctx.noKid) map { l =>
                  span(cls := "location text", dataIcon := Icons.pin)(
                    l,
                  )
                },
                profile.countryInfo.map { c =>
                  span(cls := "country")(
                    flagImage(c.code),
                  )
                },
              ),
              times(u),
              p(cls := "bio")(
                profile.nonEmptyBio.ifFalse(u.lameOrTroll || u.disabled) map { bio =>
                  richText(bio, nl2br = true)
                },
              ),
              profile.actualLinks.nonEmpty option div(cls := "social_links")(
                div(cls := "title text")(trans.socialMediaLinks()),
                profile.actualLinks.map { link =>
                  a(
                    href   := link.url,
                    target := "_blank",
                    rel    := "nofollow noopener noreferrer",
                  )(
                    link.site.name,
                  )
                },
              ),
              info.teamIds.nonEmpty option div(cls := "teams")(
                div(cls := "title text")(trans.team.teams()),
                info.teamIds.sorted.map { t =>
                  teamLink(t, withIcon = false)
                },
              ),
            )
          else
            times(u),
        ),
      ),
    )
  }

  def apply(
      u: User,
      info: lila.app.mashup.UserInfo,
      angle: lila.app.mashup.UserInfo.Angle,
      social: lila.app.mashup.UserInfo.Social,
  )(implicit ctx: Context) = {
    val isSystem = u.id == User.lishogiId

    frag(
      div(cls := "box__top user-show__header")(
        h1(
          frag(
            // we want a link to /patron on wings
            u.isPatron option a(cls := "patron-link", href := routes.Plan.index)(nbsp),
            showUsername(
              u,
              withLink = false,
              withOnline = true,
              withPowerTip = false,
              withFlag = false,
            ),
            u.title
              .ifTrue(u.noBot)
              .map(t => frag(" - ", span(cls := "official-title")(lila.user.Title.trans(t)))),
          ),
        ),
        div(
          cls := List(
            "trophies" -> true,
            "packed"   -> (info.countTrophiesAndPerfCups > 7),
          ),
        )(
          views.html.user.bits.perfTrophies(u, info.ranks),
          otherTrophies(info),
          u.plan.active option
            a(
              href := routes.Plan.index,
              cls  := "trophy award patron icon3d",
              ariaTitle(s"Patron since ${showDate(u.plan.sinceDate)}"),
            )(Icons.patron),
        ),
        u.disabled option span(cls := "closed")("CLOSED"),
      ),
      subHeader(u, info, social),
      notes(u, social),
      ((ctx is u) && u.perfs.bestStandardRating > 2500 && !u.hasTitle && !u.isBot && !ctx.pref.hasSeenVerifyTitle) option
        views.html.user.bits.claimTitle,
      isGranted(_.UserSpy) option div(cls := "mod-zone none"),
      standardFlash(),
      angle match {
        case Angle.Games(Some(searchForm)) => views.html.search.user(u, searchForm)
        case _                             => profileSide(u, info)
      },
      !isSystem option stats(u, info),
      div(cls := "angles number-menu number-menu--tabs menu-box-pop")(
        a(
          dataTab := "activity",
          cls := List(
            "nm-item to-activity" -> true,
            "active"              -> (angle == Angle.Activity),
          ),
          href := routes.User.show(u.username),
        )(trans.activity.activity()),
        !isSystem option a(
          dataTab := "games",
          cls := List(
            "nm-item to-games" -> true,
            "active"           -> (angle.key == "games"),
          ),
          href := routes.User.gamesAll(u.username),
        )(
          trans.nbGames.plural(info.user.count.game, info.user.count.game.localize),
          info.nbs.playing > 0 option
            span(
              cls   := "unread",
              title := trans.nbPlaying.pluralTxt(info.nbs.playing, info.nbs.playing.localize),
            )(
              info.nbs.playing,
            ),
        ),
      ),
    )
  }
}
