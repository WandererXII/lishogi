package lila.game.clock

import lila.common.Centis
final case class LagMetrics(
    clientLag: Option[Centis] = None,
    clientStepTime: Option[Centis] = None,
) {

  // Calculate client reported lag given the server's duration for the step.
  def reportedLag(elapsed: Centis): Option[Centis] =
    clientStepTime.fold(clientLag)(mt => Some(elapsed - mt))
}

object LagMetrics {
  def empty = LagMetrics(None, None)
}
