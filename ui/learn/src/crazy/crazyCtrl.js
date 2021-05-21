var m = require('mithril');
var util = require('../og/main').util;
var drag = require('../og/main').drag;
var ground = require('../ground');
var ogDrag = require('../og/drag');
var ogDrop = require('../og/drop');
var setDropMode = ogDrop.setDropMode,
  cancelDropMode = ogDrop.cancelDropMode;

exports.drag = function(ctrl, color, e) {
  console.log("drag");
  console.log("ground.instance.data", ground.instance.data);
  if (e.button !== undefined && e.button !== 0) return; // only touch or left click
  if (ground.instance.data.movable.color !== color) return;
  var el = e.target,
    role = el.getAttribute("data-role"),
    number = el.getAttribute("data-nb");
  console.log(el, role, number);
  if (!role || !color || number === "0") return;
  e.stopPropagation();
  e.preventDefault();
  ogDrag.dragNewPiece(ground.instance.data, { color: color, role: role }, e);
};

exports.selectToDrop = function(ctrl, color, e) {
  console.log("selectToDrop");
  console.log("crazyCtrl.js selectToDrop data", ground.instance.data);
  if (e.button !== undefined && e.button !== 0) return; // only touch or left click
  if (ground.instance.data.movable.color !== color) return;
  var el = e.target,
    role = el.getAttribute("data-role"),
    number = el.getAttribute("data-nb");
  if (!role || !color || number === "0") return;
  var dropMode = ground.instance.data.dropmode,
    dropPiece = ground.instance.data.dropmode.piece;
  if (!dropMode.active || (dropPiece && (dropPiece.role !== role))) {
    setDropMode(ground.instance.data, { color: color, role: role });
    // ctrl.dropmodeActive = true;
  } else {
    cancelDropMode(ground.instance.data);
    // ctrl.dropmodeActive = false;
  }
  e.stopPropagation();
  e.preventDefault();
};

exports.shadowDrop = function(ctrl, color, e) {
  console.log("shadowDrop");
};
