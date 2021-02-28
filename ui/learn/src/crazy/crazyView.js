const crazyCtrl = require("./crazyCtrl");
const m = require("mithril");
const oKeys = ["pawn", "lance", "knight", "silver", "gold", "bishop", "rook"];
const eventNames1 = ["mousedown", "touchmove"];
const eventNames2 = ["click"];
const eventNames3 = ["contextmenu"];

function reverse(color) {
  return color == "white" ? "black" : "white";
}

exports.renderPocket = function (ctrl, position, hasPocket) {
  if (!hasPocket) return;
  const bottomColor = ctrl.level.blueprint.color
  const color = position == "bottom" ? bottomColor : reverse(bottomColor)
  const usable = position == "bottom";
  return m(
    `div.pocket.is2d.pocket-${position}.pos-${ctrl.level.blueprint.color}` + (usable ? '.usable' : ''),
    {
      config: function(element, isInitialized) {
        if (isInitialized) return;
        eventNames1.forEach(function(name) {
          element.addEventListener(name, function(e) {
            crazyCtrl.drag(ctrl, color, e);
            m.redraw();
          });
        });
        eventNames2.forEach(function(name) {
          element.addEventListener(name, function(e) {
            crazyCtrl.selectToDrop(ctrl, color, e);
            m.redraw();
          });
        });
        eventNames3.forEach(function(name) {
          element.addEventListener(name, function(e) {
            crazyCtrl.shadowDrop(ctrl, color, e);
          });
        });
      }
    },
    oKeys.map((role) => {
      let nb = /*pocket[role] ||*/ 1;
      const sp = false; //(role == shadowPiece?.role && color == shadowPiece?.color);
      const selectedSquare = false;//(!!ctrl.selected && ctrl.selected[0] === color && ctrl.selected[1] === role && ctrl.shogiground.state.movable.color == color);
      //if (activeColor) {
        //if (dropped === role) nb--;
        //if (captured && captured.role === role) nb++;
      //}
      return m(
        "div.pocket-c1",
        m(
          "div.pocket-c2",
          {
            class: sp ? "shadow-piece" : ""
          },
          m("piece." + role + "." + color, {
            class: selectedSquare ? "selected-square": "",
            "data-role": role,
            "data-color": color,
            "data-nb": nb,
            cursor: "pointer"
          })
        )
      );
    })
  );
}
