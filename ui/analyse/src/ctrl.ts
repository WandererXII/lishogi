import { Api as ShogigroundApi } from 'shogiground/api';
import { DrawShape } from 'shogiground/draw';
import * as cg from 'shogiground/types';
import { Config as ShogigroundConfig } from 'shogiground/config';
import { build as makeTree, path as treePath, ops as treeOps, TreeWrapper } from 'tree';
import * as keyboard from './keyboard';
import { Ctrl as ActionMenuCtrl } from './actionMenu';
import { Autoplay, AutoplayDelay } from './autoplay';
import * as promotion from './promotion';
import * as util from './util';
import { defined, pretendItsUsi, prop, Prop } from 'common';
import throttle from 'common/throttle';
import { storedProp, StoredBooleanProp } from 'common/storage';
import { make as makeSocket, Socket } from './socket';
import { ForecastCtrl } from './forecast/interfaces';
import { make as makeForecast } from './forecast/forecastCtrl';
import { ctrl as cevalCtrl, isEvalBetter, CevalCtrl, Work as CevalWork, CevalOpts } from 'ceval';
import explorerCtrl from './explorer/explorerCtrl';
import { ExplorerCtrl } from './explorer/interfaces';
import * as game from 'game';
import makeStudy from './study/studyCtrl';
import { StudyCtrl } from './study/interfaces';
import { StudyPracticeCtrl } from './study/practice/interfaces';
import { make as makeFork, ForkCtrl } from './fork';
import { make as makeRetro, RetroCtrl } from './retrospect/retroCtrl';
import { make as makePractice, PracticeCtrl } from './practice/practiceCtrl';
import { make as makeEvalCache, EvalCache } from './evalCache';
import { compute as computeAutoShapes } from './autoShape';
import { nextGlyphSymbol } from './nodeFinder';
import * as speech from './speech';
import { AnalyseOpts, AnalyseData, ServerEvalData, Key, JustCaptured, NvuiPlugin, Redraw } from './interfaces';
import GamebookPlayCtrl from './study/gamebook/gamebookPlayCtrl';
import { ctrl as treeViewCtrl, TreeView } from './treeView/treeView';
import { cancelDropMode } from 'shogiground/drop';
import { lishogiVariantRules, shogigroundDests, shogigroundDropDests } from 'shogiops/compat';
import { makeSquare, opposite, parseUsi, roleToString } from 'shogiops/util';
import { Outcome, isNormal } from 'shogiops/types';
import { parseSfen } from 'shogiops/sfen';
import { Position, PositionError } from 'shogiops/shogi';
import { Result } from '@badrap/result';
import { setupPosition } from 'shogiops/variant';
import { makeNotation, Notation } from 'common/notation';

const li = window.lishogi;

export default class AnalyseCtrl {
  data: AnalyseData;
  element: HTMLElement;

  tree: TreeWrapper;
  socket: Socket;
  shogiground: ShogigroundApi;
  trans: Trans;
  ceval: CevalCtrl;
  evalCache: EvalCache;

  // current tree state, cursor, and denormalized node lists
  path: Tree.Path;
  node: Tree.Node;
  nodeList: Tree.Node[];
  mainline: Tree.Node[];

  // sub controllers
  actionMenu: ActionMenuCtrl;
  autoplay: Autoplay;
  explorer: ExplorerCtrl;
  forecast?: ForecastCtrl;
  retro?: RetroCtrl;
  fork: ForkCtrl;
  practice?: PracticeCtrl;
  study?: StudyCtrl;
  studyPractice?: StudyPracticeCtrl;

  // state flags
  justPlayedUsi?: string;
  justCaptured?: JustCaptured;
  autoScrollRequested: boolean = false;
  redirecting: boolean = false;
  onMainline: boolean = true;
  synthetic: boolean; // false if coming from a real game
  imported: boolean; // true if coming from kif or csa
  ongoing: boolean; // true if real game is ongoing

  // display flags
  flipped: boolean = false;
  embed: boolean;
  showComments: boolean = true; // whether to display comments in the move tree
  showAutoShapes: StoredBooleanProp = storedProp('show-auto-shapes', true);
  showGauge: StoredBooleanProp = storedProp('show-gauge', true);
  showMoveAnnotation: StoredBooleanProp = storedProp('show-move-annotation', true);
  showComputer: StoredBooleanProp = storedProp('show-computer', true);
  keyboardHelp: boolean = location.hash === '#keyboard';
  threatMode: Prop<boolean> = prop(false);
  treeView: TreeView;
  cgVersion = {
    js: 1, // increment to recreate shogiground
    dom: 1,
  };

  // underboard inputs
  sfenInput?: string;
  kifInput?: string;
  csaInput?: string;

  // other paths
  initialPath: Tree.Path;
  contextMenuPath?: Tree.Path;
  gamePath?: Tree.Path;

  dropmodeActive: boolean = false;
  // misc
  cgConfig: any; // latest shogiground config (useful for revert)
  music?: any;
  nvui?: NvuiPlugin;

  constructor(readonly opts: AnalyseOpts, readonly redraw: Redraw) {
    this.data = opts.data;
    this.element = opts.element;
    this.embed = opts.embed;
    this.trans = opts.trans;
    this.treeView = treeViewCtrl(opts.embed ? 'inline' : 'column');

    if (this.data.forecast) this.forecast = makeForecast(this.data.forecast, this.data, redraw);

    if (li.AnalyseNVUI) this.nvui = li.AnalyseNVUI(redraw) as NvuiPlugin;

    this.instanciateEvalCache();

    this.initialize(this.data, false);

    this.instanciateCeval();

    this.initialPath = treePath.root;

    if (opts.initialPly) {
      const loc = window.location,
        intHash = loc.hash === '#last' ? this.tree.lastPly() : parseInt(loc.hash.substr(1)),
        plyStr = opts.initialPly === 'url' ? intHash || '' : opts.initialPly;
      // remove location hash - http://stackoverflow.com/questions/1397329/how-to-remove-the-hash-from-window-location-with-javascript-without-page-refresh/5298684#5298684
      if (intHash) window.history.pushState('', document.title, loc.pathname + loc.search);
      const mainline = treeOps.mainlineNodeList(this.tree.root);
      if (plyStr === 'last') this.initialPath = treePath.fromNodeList(mainline);
      else {
        const ply = parseInt(plyStr as string);
        if (ply) this.initialPath = treeOps.takePathWhile(mainline, n => n.ply <= ply);
      }
    }

    this.setPath(this.initialPath);

    this.showGround();
    this.onToggleComputer();
    this.startCeval();
    this.explorer.setNode();
    this.study = opts.study
      ? makeStudy(opts.study, this, (opts.tagTypes || '').split(','), opts.practice, opts.relay)
      : undefined;
    this.studyPractice = this.study ? this.study.practice : undefined;

    if (location.hash === '#practice' || (this.study && this.study.data.chapter.practice)) this.togglePractice();
    else if (location.hash === '#menu') li.requestIdleCallback(this.actionMenu.toggle);

    keyboard.bind(this);

    li.pubsub.on('jump', (ply: any) => {
      this.jumpToMain(parseInt(ply));
      this.redraw();
    });

    li.pubsub.on('sound_set', (set: string) => {
      if (!this.music && set === 'music')
        li.loadScript('javascripts/music/replay.js').then(() => {
          this.music = window.lishogiReplayMusic();
        });
      if (this.music && set !== 'music') this.music = null;
    });

    li.pubsub.on('analysis.change.trigger', this.onChange);
    li.pubsub.on('analysis.chart.click', index => {
      this.jumpToIndex(index);
      this.redraw();
    });

    li.sound && speech.setup();
  }

  initialize(data: AnalyseData, merge: boolean): void {
    this.data = data;
    this.synthetic = data.game.id === 'synthetic';
    this.imported = data.game.source === 'import';
    this.ongoing = !this.synthetic && game.playable(data);

    const prevTree = merge && this.tree.root;
    this.tree = makeTree(treeOps.reconstruct(this.data.treeParts));
    if (prevTree) this.tree.merge(prevTree);

    this.initNotation(data.pref.pieceNotation, data.game.variant.key);

    this.actionMenu = new ActionMenuCtrl();
    this.autoplay = new Autoplay(this);
    if (this.socket) this.socket.clearCache();
    else this.socket = makeSocket(this.opts.socketSend, this);
    this.explorer = explorerCtrl(this, this.opts.explorer, this.explorer ? this.explorer.allowed() : !this.embed);
    this.gamePath =
      this.synthetic || this.ongoing ? undefined : treePath.fromNodeList(treeOps.mainlineNodeList(this.tree.root));
    this.fork = makeFork(this);
  }

  private setPath = (path: Tree.Path): void => {
    this.path = path;
    this.nodeList = this.tree.getNodeList(path);
    this.node = treeOps.last(this.nodeList) as Tree.Node;
    this.mainline = treeOps.mainlineNodeList(this.tree.root);
    this.onMainline = this.tree.pathIsMainline(path);
    this.sfenInput = undefined;
    this.kifInput = undefined;
    this.csaInput = undefined;
  };

  flip = () => {
    this.flipped = !this.flipped;
    this.shogiground.set({
      orientation: this.bottomColor(),
    });
    if (this.practice) this.restartPractice();
    this.redraw();
  };

  topColor(): Color {
    return opposite(this.bottomColor());
  }

  bottomColor(): Color {
    return this.flipped ? opposite(this.data.orientation) : this.data.orientation;
  }

  bottomIsSente = () => this.bottomColor() === 'sente';

  getOrientation(): Color {
    // required by ui/ceval
    return this.bottomColor();
  }
  getNode(): Tree.Node {
    // required by ui/ceval
    return this.node;
  }

  turnColor(): Color {
    return util.plyColor(this.node.ply);
  }

  togglePlay(delay: AutoplayDelay): void {
    this.autoplay.toggle(delay);
    this.actionMenu.open = false;
  }

  private usiToLastMove(usi?: Usi): Key[] | undefined {
    if (!usi) return;
    if (usi[1] === '*') return [usi.substr(2, 2), usi.substr(2, 2)] as Key[];
    return [usi.substr(0, 2), usi.substr(2, 2)] as Key[];
  }

  private showGround(): void {
    this.onChange();
    this.withCg(cg => {
      cg.set(this.makeCgOpts());
      this.setAutoShapes();
      if (this.node.shapes) cg.setShapes(this.node.shapes as DrawShape[]);
    });
  }

  private getMoveDests(): cg.Dests {
    if (this.embed) return new Map();
    else
      return this.position(this.node).unwrap(
        pos => shogigroundDests(pos),
        _ => new Map()
      );
  }

  private getDropDests(): cg.DropDests {
    if (this.embed) return new Map();
    return this.position(this.node).unwrap(
      pos => shogigroundDropDests(pos),
      _ => new Map()
    );
  }

  makeCgOpts(): ShogigroundConfig {
    const node = this.node,
      color = this.turnColor(),
      dests = this.getMoveDests(),
      drops = this.getDropDests(),
      movableColor =
        this.practice || this.gamebookPlay()
          ? this.bottomColor()
          : !this.embed && (dests.size > 0 || drops.size > 0)
          ? color
          : undefined,
      config: ShogigroundConfig = {
        sfen: node.sfen,
        turnColor: color,
        movable: this.embed
          ? {
              color: undefined,
              dests: new Map(),
            }
          : {
              color: movableColor,
              dests: (movableColor === color && dests) || new Map(),
            },
        dropmode: this.embed
          ? {
              dropDests: undefined,
            }
          : {
              dropDests: (movableColor === color && drops) || new Map(),
            },
        check: !!node.check,
        lastMove: this.usiToLastMove(node.usi),
      };
    //if (!dests && !node.check) {
    //  // premove while dests are loading from server
    //  // can't use when in check because it highlights the wrong king
    //  config.turnColor = opposite(color);
    //  config.movable!.color = color;
    //}

    config.premovable = {
      enabled: config.movable!.color && config.turnColor !== config.movable!.color,
    };
    config.predroppable = {
      enabled: config.movable!.color && config.turnColor !== config.movable!.color,
    };
    this.cgConfig = config;
    return config;
  }

  private sound = li.sound
    ? {
        move: throttle(50, li.sound.move),
        capture: throttle(50, li.sound.capture),
        check: throttle(50, li.sound.check),
      }
    : {
        move: $.noop,
        capture: $.noop,
        check: $.noop,
      };

  private onChange: () => void = throttle(300, () => {
    li.pubsub.emit('analysis.change', this.node.sfen, this.path, this.onMainline ? this.node.ply : false);
  });

  private updateHref: () => void = li.debounce(() => {
    if (!this.opts.study) window.history.replaceState(null, '', '#' + this.node.ply);
  }, 750);

  autoScroll(): void {
    this.autoScrollRequested = true;
  }

  playedLastMoveMyself = () => !!this.justPlayedUsi && !!this.node.usi && this.node.usi.startsWith(this.justPlayedUsi);

  jump(path: Tree.Path): void {
    const pathChanged = path !== this.path,
      isForwardStep = pathChanged && path.length == this.path.length + 2;
    this.setPath(path);
    this.showGround();
    if (pathChanged) {
      const playedMyself = this.playedLastMoveMyself();
      if (this.study) this.study.setPath(path, this.node, playedMyself);
      if (isForwardStep) {
        if (!this.node.usi) this.sound.move();
        // initial position
        else if (!playedMyself) {
          //if (this.node.san!.includes('x')) this.sound.capture();
          this.sound.move();
        }
        if (this.node.check) this.sound.check();
      }
      this.threatMode(false);
      this.ceval.stop();
      this.startCeval();
      speech.node(this.node);
    }
    this.justPlayedUsi = this.justCaptured = undefined;
    this.explorer.setNode();
    this.updateHref();
    this.autoScroll();
    promotion.cancel(this);
    if (pathChanged) {
      if (this.retro) this.retro.onJump();
      if (this.practice) this.practice.onJump();
      if (this.study) this.study.onJump();
    }
    if (this.music) this.music.jump(this.node);
    li.pubsub.emit('ply', this.node.ply);
  }

  userJump = (path: Tree.Path): void => {
    this.autoplay.stop();
    this.withCg(cg => cg.selectSquare(null));
    if (this.practice) {
      const prev = this.path;
      this.practice.preUserJump(prev, path);
      this.jump(path);
      this.practice.postUserJump(prev, this.path);
    } else this.jump(path);
  };

  private canJumpTo(path: Tree.Path): boolean {
    return !this.study || this.study.canJumpTo(path);
  }

  userJumpIfCan(path: Tree.Path): void {
    if (this.canJumpTo(path)) this.userJump(path);
  }

  mainlinePathToPly(ply: Ply): Tree.Path {
    return treeOps.takePathWhile(this.mainline, n => n.ply <= ply);
  }

  jumpToMain = (ply: Ply): void => {
    this.userJump(this.mainlinePathToPly(ply));
  };

  jumpToIndex = (index: number): void => {
    this.jumpToMain(index + 1 + this.tree.root.ply);
  };

  jumpToGlyphSymbol(color: Color, symbol: string): void {
    const node = nextGlyphSymbol(color, symbol, this.mainline, this.node.ply);
    if (node) this.jumpToMain(node.ply);
    this.redraw();
  }

  reloadData(data: AnalyseData, merge: boolean): void {
    this.initialize(data, merge);
    this.redirecting = false;
    this.setPath(treePath.root);
    this.instanciateCeval();
    this.instanciateEvalCache();
    this.cgVersion.js++;
  }

  changeNotation(notation: string): void {
    this.redirecting = true;
    $.ajax({
      url: '/analysis/notation',
      method: 'post',
      data: { notation },
      success: (data: AnalyseData) => {
        this.reloadData(data, false);
        this.userJump(this.mainlinePathToPly(this.tree.lastPly()));
        this.redraw();
      },
      error: error => {
        alert(error.responseText);
        this.redirecting = false;
        this.redraw();
      },
    });
  }

  changeSfen(sfen: Sfen): void {
    this.redirecting = true;
    window.location.href =
      '/analysis/' +
      this.data.game.variant.key +
      '/' +
      encodeURIComponent(sfen).replace(/%20/g, '_').replace(/%2F/g, '/');
  }

  userNewPiece = (piece: cg.Piece, key: Key): void => {
    const usi = roleToString(piece.role).toUpperCase() + '*' + key;
    this.justPlayedUsi = usi;
    this.justCaptured = undefined;
    this.sound.move();
    const drop = {
      usi: usi,
      variant: this.data.game.variant.key,
      sfen: this.node.sfen,
      path: this.path,
    };
    this.socket.sendAnaUsi(drop);
    this.preparePremoving();
    cancelDropMode(this.shogiground.state);
    this.dropmodeActive = false;
    this.redraw();
  };

  userMove = (orig: Key, dest: Key, capture?: JustCaptured): void => {
    this.justPlayedUsi = orig + dest;
    const isCapture = capture;
    this.sound[isCapture ? 'capture' : 'move']();
    if (!promotion.start(this, orig, dest, capture, this.sendMove)) this.sendMove(orig, dest, capture);
  };

  sendMove = (orig: Key, dest: Key, capture?: JustCaptured, prom?: Boolean): void => {
    const move: any = {
      usi: orig + dest + (prom ? '+' : ''),
      variant: this.data.game.variant.key,
      sfen: this.node.sfen,
      path: this.path,
    };
    if (capture) this.justCaptured = capture;
    if (this.practice) this.practice.onUserMove();
    this.socket.sendAnaUsi(move);
    this.preparePremoving();
    this.redraw();
  };

  private preparePremoving(): void {
    this.shogiground.set({
      turnColor: this.shogiground.state.movable.color as cg.Color,
      movable: {
        color: opposite(this.shogiground.state.movable.color as cg.Color),
      },
      premovable: {
        enabled: true,
      },
    });
  }

  onPremoveSet = () => {
    if (this.study) this.study.onPremoveSet();
  };

  addNode(node: Tree.Node, path: Tree.Path) {
    const newPath = this.tree.addNode(node, path);
    if (!newPath) return this.redraw();
    const parent = this.tree.nodeAtPath(path);
    if (node.usi)
      node.notation = makeNotation(
        this.data.pref.pieceNotation,
        parent.sfen,
        this.data.game.variant.key,
        node.usi,
        parent.usi
      );
    this.jump(newPath);
    this.redraw();
    this.shogiground.playPremove();
  }

  deleteNode(path: Tree.Path): void {
    const node = this.tree.nodeAtPath(path);
    if (!node) return;
    const count = treeOps.countChildrenAndComments(node);
    if (
      (count.nodes >= 10 || count.comments > 0) &&
      !confirm(
        'Delete ' +
          util.plural('move', count.nodes) +
          (count.comments ? ' and ' + util.plural('comment', count.comments) : '') +
          '?'
      )
    )
      return;
    this.tree.deleteNodeAt(path);
    if (treePath.contains(this.path, path)) this.userJump(treePath.init(path));
    else this.jump(this.path);
    if (this.study) this.study.deleteNode(path);
  }

  promote(path: Tree.Path, toMainline: boolean): void {
    this.tree.promoteAt(path, toMainline);
    this.jump(path);
    if (this.study) this.study.promote(path, toMainline);
  }

  forceVariation(path: Tree.Path, force: boolean): void {
    this.tree.forceVariationAt(path, force);
    this.jump(path);
    if (this.study) this.study.forceVariation(path, force);
  }

  reset(): void {
    this.showGround();
    this.justPlayedUsi = undefined;
    this.justCaptured = undefined;
    this.redraw();
  }

  encodeNodeSfen(): Sfen {
    return this.node.sfen.replace(/\s/g, '_').replace(/\+/g, '%2B');
  }

  currentEvals() {
    return {
      server: this.node.eval,
      client: this.node.ceval,
    };
  }

  nextNodeBest() {
    return treeOps.withMainlineChild(this.node, (n: Tree.Node) => (n.eval ? n.eval.best : undefined));
  }

  setAutoShapes = (): void => {
    this.withCg(cg => cg.setAutoShapes(computeAutoShapes(this)));
  };

  private initNotation = (notation: Notation, variant: VariantKey): void => {
    function update(node: Tree.Node, prev?: Tree.Node) {
      if (prev && node.usi && !node.notation)
        node.notation = makeNotation(notation, prev.sfen, variant, node.usi, prev.usi);
      node.children.forEach(c => update(c, node));
    }
    update(this.tree.root);
  };

  private onNewCeval = (ev: Tree.ClientEval, path: Tree.Path, isThreat: boolean): void => {
    this.tree.updateAt(path, (node: Tree.Node) => {
      if (node.sfen !== ev.sfen && !isThreat) return;
      if (isThreat) {
        if (!node.threat || isEvalBetter(ev, node.threat) || node.threat.maxDepth < ev.maxDepth) node.threat = ev;
      } else if (isEvalBetter(ev, node.ceval)) node.ceval = ev;
      else if (node.ceval && ev.maxDepth > node.ceval.maxDepth) node.ceval.maxDepth = ev.maxDepth;

      if (path === this.path) {
        this.setAutoShapes();
        if (!isThreat) {
          if (this.retro) this.retro.onCeval();
          if (this.practice) this.practice.onCeval();
          if (this.studyPractice) this.studyPractice.onCeval();
          this.evalCache.onCeval();
          if (ev.cloud && ev.depth >= this.ceval.effectiveMaxDepth()) this.ceval.stop();
        }
        this.redraw();
      }
    });
  };

  private instanciateCeval(): void {
    if (this.ceval) this.ceval.destroy();
    const cfg: CevalOpts = {
      variant: this.data.game.variant,
      possible: !this.embed && (this.synthetic || !game.playable(this.data)),
      emit: (ev: Tree.ClientEval, work: CevalWork) => {
        this.onNewCeval(ev, work.path, work.threatMode);
      },
      setAutoShapes: this.setAutoShapes,
      redraw: this.redraw,
    };
    if (this.opts.study && this.opts.practice) {
      cfg.storageKeyPrefix = 'practice';
      cfg.multiPvDefault = 1;
    }
    this.ceval = cevalCtrl(cfg);
  }

  getCeval() {
    return this.ceval;
  }

  outcome(node?: Tree.Node): Outcome | undefined {
    return this.position(node || this.node).unwrap(
      pos => pos.outcome(),
      _ => undefined
    );
  }

  position(node: Tree.Node): Result<Position, PositionError> {
    return parseSfen(node.sfen).chain(setup =>
      setupPosition(lishogiVariantRules(this.data.game.variant.key), setup, false)
    );
  }

  canUseCeval(): boolean {
    return !this.node.fourfold && !this.outcome();
  }

  startCeval = throttle(800, () => {
    if (this.ceval.enabled()) {
      if (this.canUseCeval()) {
        this.ceval.start(this.path, this.nodeList, this.threatMode(), false);
        this.evalCache.fetch(this.path, parseInt(this.ceval.multiPv()));
      } else this.ceval.stop();
    }
  });

  toggleCeval = () => {
    if (!this.showComputer()) return;
    this.ceval.toggle();
    this.setAutoShapes();
    this.startCeval();
    if (!this.ceval.enabled()) {
      this.threatMode(false);
      if (this.practice) this.togglePractice();
    }
    this.redraw();
  };

  toggleThreatMode = () => {
    if (this.node.check) return;
    if (!this.ceval.enabled()) this.ceval.toggle();
    if (!this.ceval.enabled()) return;
    this.threatMode(!this.threatMode());
    if (this.threatMode() && this.practice) this.togglePractice();
    this.setAutoShapes();
    this.startCeval();
    this.redraw();
  };

  disableThreatMode = (): boolean => {
    return !!this.practice;
  };

  mandatoryCeval = (): boolean => {
    return !!this.studyPractice;
  };

  private cevalReset(): void {
    this.ceval.stop();
    if (!this.ceval.enabled()) this.ceval.toggle();
    this.startCeval();
    this.redraw();
  }

  cevalSetMultiPv = (v: number): void => {
    this.ceval.multiPv(v);
    this.tree.removeCeval();
    this.cevalReset();
  };

  cevalSetThreads = (v: number): void => {
    if (!this.ceval.threads) return;
    this.ceval.threads(v);
    this.cevalReset();
  };

  cevalSetHashSize = (v: number): void => {
    if (!this.ceval.hashSize) return;
    this.ceval.hashSize(v);
    this.cevalReset();
  };

  cevalSetInfinite = (v: boolean): void => {
    this.ceval.infinite(v);
    this.cevalReset();
  };

  showEvalGauge(): boolean {
    return this.hasAnyComputerAnalysis() && this.showGauge() && !this.outcome() && this.showComputer();
  }

  hasAnyComputerAnalysis(): boolean {
    return this.data.analysis ? true : this.ceval.enabled();
  }

  hasFullComputerAnalysis = (): boolean => {
    return Object.keys(this.mainline[0].eval || {}).length > 0;
  };

  private resetAutoShapes() {
    if (this.showAutoShapes() || this.showMoveAnnotation()) this.setAutoShapes();
    else this.shogiground && this.shogiground.setAutoShapes([]);
  }

  toggleAutoShapes = (v: boolean): void => {
    this.showAutoShapes(v);
    this.resetAutoShapes();
  };

  toggleGauge = () => {
    this.showGauge(!this.showGauge());
  };

  toggleMoveAnnotation = (v: boolean): void => {
    this.showMoveAnnotation(v);
    this.resetAutoShapes();
  };

  private onToggleComputer() {
    if (!this.showComputer()) {
      this.tree.removeComputerVariations();
      if (this.ceval.enabled()) this.toggleCeval();
      this.shogiground && this.shogiground.setAutoShapes([]);
    } else this.resetAutoShapes();
  }

  toggleComputer = () => {
    if (this.ceval.enabled()) this.toggleCeval();
    const value = !this.showComputer();
    this.showComputer(value);
    if (!value && this.practice) this.togglePractice();
    this.onToggleComputer();
    li.pubsub.emit('analysis.comp.toggle', value);
  };

  mergeAnalysisData(data: ServerEvalData): void {
    if (this.study && this.study.data.chapter.id !== data.ch) return;
    this.tree.merge(data.tree);
    if (!this.showComputer()) this.tree.removeComputerVariations();
    this.data.analysis = data.analysis;
    if (data.analysis)
      data.analysis.partial = !!treeOps.findInMainline(data.tree, n => !n.eval && !!n.children.length && n.ply <= 200);
    if (data.division) this.data.game.division = data.division;
    if (this.retro) this.retro.onMergeAnalysisData();
    if (this.study) this.study.serverEval.onMergeAnalysisData();
    li.pubsub.emit('analysis.server.progress', this.data);
    this.redraw();
  }

  playUsi(usi: Usi): void {
    const move = parseUsi(pretendItsUsi(usi))!;
    const to = makeSquare(move.to);
    if (isNormal(move)) {
      const piece = this.shogiground.state.pieces.get(makeSquare(move.from));
      const capture = this.shogiground.state.pieces.get(to);
      this.sendMove(
        makeSquare(move.from),
        to,
        capture && piece && capture.color !== piece.color ? capture : undefined,
        move.promotion
      );
    } else
      this.shogiground.newPiece(
        {
          color: this.shogiground.state.movable.color as Color,
          role: move.role,
        },
        to
      );
  }

  explorerMove(usi: Usi) {
    this.playUsi(usi);
    this.explorer.loading(true);
  }

  playBestMove() {
    const usi = this.nextNodeBest() || (this.node.ceval && this.node.ceval.pvs[0].moves[0]);
    if (usi) this.playUsi(usi);
  }

  canEvalGet(): boolean {
    if (this.node.ply >= 15 && !this.opts.study) return false;

    // cloud eval does not support fourfold repetition
    const sfens = new Set();
    for (let i = this.nodeList.length - 1; i >= 0; i--) {
      const node = this.nodeList[i];
      const sfen = node.sfen.split(' ').slice(0, 3).join(' ');
      if (sfens.has(sfen)) return false;
      sfens.add(sfen);
    }
    return true;
  }

  instanciateEvalCache() {
    this.evalCache = makeEvalCache({
      variant: this.data.game.variant.key,
      canGet: () => this.canEvalGet(),
      canPut: () =>
        this.data.evalPut &&
        this.canEvalGet() &&
        // if not in study, only put decent opening moves
        (this.opts.study || (!this.node.ceval!.mate && Math.abs(this.node.ceval!.cp!) < 99)),
      getNode: () => this.node,
      send: this.opts.socketSend,
      receive: this.onNewCeval,
    });
  }

  toggleRetro = (): void => {
    if (this.retro) this.retro = undefined;
    else {
      this.retro = makeRetro(this, this.bottomColor());
      if (this.practice) this.togglePractice();
      if (this.explorer.enabled()) this.toggleExplorer();
    }
    this.setAutoShapes();
  };

  toggleExplorer = (): void => {
    if (this.practice) this.togglePractice();
    if (this.explorer.enabled() || this.explorer.allowed()) this.explorer.toggle();
  };

  togglePractice = () => {
    if (this.practice || !this.ceval.possible) this.practice = undefined;
    else {
      if (this.retro) this.toggleRetro();
      if (this.explorer.enabled()) this.toggleExplorer();
      this.practice = makePractice(this, () => {
        // push to 20 to store AI moves in the cloud
        // lower to 18 after task completion (or failure)
        return this.studyPractice && this.studyPractice.success() === null ? 20 : 18;
      });
    }
    this.setAutoShapes();
  };

  restartPractice() {
    this.practice = undefined;
    this.togglePractice();
  }

  gamebookPlay = (): GamebookPlayCtrl | undefined => {
    return this.study && this.study.gamebookPlay();
  };

  isGamebook = (): boolean => !!(this.study && this.study.data.chapter.gamebook);

  // plies respect color - it is even if it's sente turn and vice versa
  // but move number is s separate thing
  // so instead of sending both
  // let's just count the offset
  plyOffset = (): number => {
    return this.data.game.startedAtPly - (this.data.game.startedAtMove - 1);
  };

  // Ideally we would just use node.clock
  // but we store remaining times for lishogi games as node.clock
  // for imports we store movetime as node.clock, because
  // that's what's provided next to each move
  getMovetime = (node: Tree.Node): number | undefined => {
    const offset = this.mainline[0].ply;
    if (defined(node.clock) && !this.study) {
      if (defined(this.data.game.moveCentis)) return this.data.game.moveCentis[node.ply - 1 - offset];
      if (this.imported) return node.clock;
    }
    return;
  };

  withCg<A>(f: (cg: ShogigroundApi) => A): A | undefined {
    if (this.shogiground && this.cgVersion.js === this.cgVersion.dom) return f(this.shogiground);
    return undefined;
  }
}
