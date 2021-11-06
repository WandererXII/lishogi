package lishogi

package object rating extends PackageObject {

  type UserRankMap = Map[lishogi.rating.PerfType, Int]

  type RatingFactors = Map[lishogi.rating.PerfType, RatingFactor]
}
