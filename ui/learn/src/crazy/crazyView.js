const crazyCtrl = require('./crazyCtrl');
const m = require('mithril');
const oKeys = ['pawn', 'lance', 'knight', 'silver', 'gold', 'bishop', 'rook'];
const eventNames1 = ['mousedown', 'touchmove'];
const eventNames2 = ['click'];
const eventNames3 = ['contextmenu'];

function reverse(color) {
  return color == 'sente' ? 'gote' : 'sente';
}

exports.renderPocket = function (ctrl, position) {
  if (!ctrl.level.pockets) return;
  console.log('crazyView renderPocket', ctrl, position);
  const bottomColor = ctrl.level.blueprint.color
  const color = position == 'bottom' ? bottomColor : reverse(bottomColor)
  console.log('crazyView color', color);
  const usable = position == 'bottom';
  const pocket = ctrl.level.pockets[color];
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
      let nb = pocket[role];
      const sp = false; //(role == shadowPiece?.role && color == shadowPiece?.color);
      const selectedSquare = false;//(!!ctrl.selected && ctrl.selected[0] === color && ctrl.selected[1] === role && ctrl.shogiground.state.movable.color == color);
      //if (activeColor) {
        //if (dropped === role) nb--;
        //if (captured && captured.role === role) nb++;
      //}
      return m(
        'div.pocket-c1',
        m(
          'div.pocket-c2',
          {
            class: sp ? 'shadow-piece' : ''
          },
          m('piece.' + role + '.' + color, {
            class: selectedSquare ? 'selected-square': '',
            'data-role': role,
            'data-color': color,
            'data-nb': nb,
            cursor: 'pointer'
          })
        )
      );
    })
  );
}
