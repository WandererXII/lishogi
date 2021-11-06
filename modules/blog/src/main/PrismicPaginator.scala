package lishogi.blog

import io.prismic._

import lishogi.common.config.MaxPerPage
import lishogi.common.paginator._

private object PrismicPaginator {

  def apply(response: Response, page: Int, maxPerPage: MaxPerPage): Paginator[Document] =
    Paginator.fromResults(
      currentPageResults = response.results,
      nbResults = response.totalResultsSize,
      currentPage = page,
      maxPerPage = maxPerPage
    )
}
