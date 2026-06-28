package lila.app
package templating

import scala.concurrent.duration._

import lila.app.ui.ScalatagsTemplate._

object Environment
    extends lila.Lilaisms
    with StringHelper
    with AssetHelper
    with DateHelper
    with NumberHelper
    with PaginatorHelper
    with FormHelper
    with SetupHelper
    with GameHelper
    with UserHelper
    with ForumHelper
    with I18nHelper
    with SecurityHelper
    with TeamHelper
    with TournamentHelper
    with FlashHelper
    with ShogigroundHelper
    with ShogiHelper {

  private var envVar: Option[Env] = None
  def setEnv(e: Env)              = { envVar = Some(e) }
  def env: Env                    = envVar.get

  type FormWithCaptcha = (play.api.data.Form[_], lila.common.Captcha)

  def netBaseUrl          = env.net.baseUrl.value
  def isGloballyCrawlable = env.net.crawlable

  lazy val netDomain = env.net.domain
  def isProd         = env.isProd
  def isStage        = env.isStage

  val Icons = lila.common.Icons

  def contactEmail = env.net.email.value

  def contactEmailLink = a(href := s"mailto:$contactEmail")(contactEmail)

  def isChatPanicEnabled = env.chat.panic.enabled

  def blockingReportNbOpen: Int = env.report.api.nbOpen.awaitOrElse(20.millis, "nbReports", 0)

}
