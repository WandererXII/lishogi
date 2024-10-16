import m from 'mithril';

export default function (send, ctrl) {
  this.send = send;

  var handlers = {
    reload: function (data) {
      ctrl.reload(data);
      m.redraw();
    },
    aborted: function () {
      lishogi.reload();
    },
    hostGame: function (gameId) {
      ctrl.data.host.gameId = gameId;
      m.redraw();
    },
  };

  this.receive = function (type, data) {
    if (handlers[type]) {
      handlers[type](data);
      return true;
    }
    return false;
  }.bind(this);
};
