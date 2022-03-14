package lila.coordinate

case class Score(
    _id: String,
    sente: List[Int] = Nil,
    gote: List[Int] = Nil,
    senteNameSquare: List[Int] = Nil,
    goteNameSquare: List[Int] = Nil
)
