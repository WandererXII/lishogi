package views.html
package account

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.pref.PrefCateg

object bits {

  def categName(categ: lishogi.pref.PrefCateg)(implicit ctx: Context): String =
    categ match {
      case PrefCateg.GameDisplay  => trans.preferences.gameDisplay.txt()
      case PrefCateg.ChessClock   => trans.preferences.chessClock.txt()
      case PrefCateg.GameBehavior => trans.preferences.gameBehavior.txt()
      case PrefCateg.Privacy      => trans.privacy.txt()
    }
}
