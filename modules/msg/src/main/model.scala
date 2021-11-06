package lishogi.msg

import lishogi.common.LightUser
import lishogi.relation.Relations

case class MsgConvo(
    contact: LightUser,
    msgs: List[Msg],
    relations: Relations,
    postable: Boolean
)
