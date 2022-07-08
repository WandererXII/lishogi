package lila.coordinate

import lila.user.User

case class Score(
    _id: User.ID,
    sente: List[Int] = Nil,
    gote: List[Int] = Nil,
    senteNameSquare: List[Int] = Nil,
    goteNameSquare: List[Int] = Nil
)
