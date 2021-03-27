var util = require('../og/main').util;
var drag = require('../og/main').drag;
var ground = require("../ground");
var ogDrag = require("../og/drag");

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
};

exports.shadowDrop = function(ctrl, color, e) {
  console.log("shadowDrop");
};
