package lila.blog

import lila.common.config.MaxPerPage
import lila.common.paginator._
import lila.prismic._

private object PrismicPaginator {

  def apply(response: Response, page: Int, maxPerPage: MaxPerPage): Paginator[Document] =
    Paginator.fromResults(
      currentPageResults = response.results,
      nbResults = response.totalResultsSize,
      currentPage = page,
      maxPerPage = maxPerPage,
    )
}
