package lila.i18n

import play.api.i18n.Lang

sealed private trait I18nQuantity

/*
 * Originally ported from
 * https://github.com/populov/android-i18n-plurals/tree/master/library/src/main/java/com/seppius/i18n/plurals
 *
 * Must match CLDR plural rules for whole numbers, since Crowdin creates plural slots from CLDR.
 * Keep in sync with ui/@build/i18n/src/quantity.ts
 * Only covers languages in LangList, anything else gets the default rule.
 */
private object I18nQuantity {

  case object Zero  extends I18nQuantity
  case object One   extends I18nQuantity
  case object Two   extends I18nQuantity
  case object Few   extends I18nQuantity
  case object Many  extends I18nQuantity
  case object Other extends I18nQuantity

  type Selector = Count => I18nQuantity

  def apply(lang: Lang, c: Count): I18nQuantity =
    langMap.getOrElse(lang.code, selectorFor(lang))(c)

  private object selectors {

    def default(c: Count) =
      if (c == 1) One
      else Other

    def french(c: Count) =
      if (c <= 1) One
      else Other

    def filipino(c: Count) = {
      val rem10 = c % 10
      if (c <= 3 || !(rem10 == 4 || rem10 == 6 || rem10 == 9)) One
      else Other
    }

    def czech(c: Count) =
      if (c == 1) One
      else if (c >= 2 && c <= 4) Few
      else Other

    def eastSlavic(c: Count) = {
      val rem100 = c % 100
      val rem10  = c % 10
      if (rem10 == 1 && rem100 != 11) One
      else if (rem10 >= 2 && rem10 <= 4 && !(rem100 >= 12 && rem100 <= 14)) Few
      else Many
    }

    def serbian(c: Count) = {
      val rem100 = c % 100
      val rem10  = c % 10
      if (rem10 == 1 && rem100 != 11) One
      else if (rem10 >= 2 && rem10 <= 4 && !(rem100 >= 12 && rem100 <= 14)) Few
      else Other
    }

    def lithuanian(c: Count) = {
      val rem100 = c % 100
      val rem10  = c % 10
      if (rem10 == 1 && !(rem100 >= 11 && rem100 <= 19)) One
      else if (rem10 >= 2 && rem10 <= 9 && !(rem100 >= 11 && rem100 <= 19)) Few
      else Other
    }

    def polish(c: Count) = {
      val rem100 = c % 100
      val rem10  = c % 10
      if (c == 1) One
      else if (rem10 >= 2 && rem10 <= 4 && !(rem100 >= 12 && rem100 <= 14)) Few
      else Many
    }

    def romanian(c: Count) = {
      val rem100 = c % 100
      if (c == 1) One
      else if (c == 0 || (rem100 >= 1 && rem100 <= 19)) Few
      else Other
    }

    def arabic(c: Count) = {
      val rem100 = c % 100
      if (c == 0) Zero
      else if (c == 1) One
      else if (c == 2) Two
      else if (rem100 >= 3 && rem100 <= 10) Few
      else if (rem100 >= 11 && rem100 <= 99) Many
      else Other
    }

    def hebrew(c: Count) =
      if (c == 1) One
      else if (c == 2) Two
      else Other

    def none(c: Count) = Other
  }

  import selectors._

  private def selectorFor(lang: Lang): Selector =
    lang.language match {
      case "pt"               => if (lang.country == "BR") french _ else default _
      case "fr" | "fa" | "hi" => french _
      case "tl"               => filipino _
      case "cs"               => czech _
      case "ru" | "uk" | "be" => eastSlavic _
      case "sr"               => serbian _
      case "lt"               => lithuanian _
      case "pl"               => polish _
      case "ro"               => romanian _
      case "ar"               => arabic _
      case "he"               => hebrew _
      case "id" | "ja" | "ko" | "th" | "vi" | "zh" => selectors.none _
      case _                                       => default _
    }

  private val langMap: Map[String, Selector] = LangList.all.map { case (lang, _) =>
    lang.code -> selectorFor(lang)
  }
}
