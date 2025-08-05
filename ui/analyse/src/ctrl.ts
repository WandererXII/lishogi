import type { Result } from '@badrap/result';
import cevalCtrl from 'ceval/ctrl';
import { cevalRestarter } from 'ceval/restarter';
import type { CevalCtrl, EvalMeta, NodeEvals } from 'ceval/types';
import { isEvalBetter } from 'ceval/util';
import {
  loadChushogiPieceSprite,
  loadKyotoshogiPieceSprite,
  loadLishogiScript,
} from 'common/assets';
import { type Prop, defined, prop, requestIdleCallbackWithFallback } from 'common/common';
import { analysis } from 'common/links';
import { getPerfIcon } from 'common/perf-icons';
import { type StoredBooleanProp, storedProp } from 'common/storage';
import throttle from 'common/throttle';
import { debounce } from 'common/timings';
import * as game from 'game';
import { i18n, i18nPluralSame } from 'i18n';
import { i18nVariant } from 'i18n/variant';
import { flipMetaPlayers, plyColor, usiToRole } from 'shogi/common';
import { isImpasse as impasse } from 'shogi/impasse';
import { makeNotation } from 'shogi/notation';
import { Shogiground } from 'shogiground';
import type { Api as ShogigroundApi } from 'shogiground/api';
import type { Config as ShogigroundConfig } from 'shogiground/config';
import type { DrawShape } from 'shogiground/draw';
import type * as sg from 'shogiground/types';
import { eagleLionAttacks, falconLionAttacks } from 'shogiops/attacks';
import {
  shogigroundDropDests,
  shogigroundMoveDests,
  shogigroundSecondLionStep,
  usiToSquareNames,
} from 'shogiops/compat';
import { isHandicap as sgIsHandicap } from 'shogiops/handicaps';
import { parseSfen } from 'shogiops/sfen';
import type { NormalMove, Outcome, Piece } from 'shogiops/types';
import { makeSquareName, makeUsi, opposite, parseSquareName, squareDist } from 'shogiops/util';
import type { Chushogi } from 'shogiops/variant/chushogi';
import type { Position, PositionError } from 'shogiops/variant/position';
import { promotableOnDrop, promote } from 'shogiops/variant/util';
import { type TreeWrapper, build as makeTree, ops as treeOps, path as treePath } from 'tree';
import { Ctrl as ActionMenuCtrl } from './action-menu';
import { compute as computeAutoShapes } from './auto-shape';
import { Autoplay, type AutoplayDelay } from './autoplay';
import { type EvalCache, make as makeEvalCache } from './eval-cache';
import { make as makeForecast } from './forecast/forecast-ctrl';
import type { ForecastCtrl } from './forecast/interfaces';
import { type ForkCtrl, make as makeFork } from './fork';
import { makeConfig } from './ground';
import type { AnalyseData, AnalyseOpts, NvuiPlugin, ServerEvalData } from './interfaces';
import * as keyboard from './keyboard';
import { nextGlyphSymbol } from './node-finder';
import { type PracticeCtrl, make as makePractice } from './practice/practice-ctrl';
import { type RetroCtrl, make as makeRetro } from './retrospect/retro-ctrl';
import { type Socket, make as makeSocket } from './socket';
import * as speech from './speech';
import type GamebookPlayCtrl from './study/gamebook/gamebook-play-ctrl';
import type { StudyCtrl } from './study/interfaces';
import type { StudyPracticeCtrl } from './study/practice/interfaces';
import makeStudy from './study/study-ctrl';
import { type TreeView, ctrl as treeViewCtrl } from './tree-view/tree-view';
import * as util from './util';

const li = window.lishogi;

export default class AnalyseCtrl {
  data: AnalyseData;

  tree: TreeWrapper;
  socket: Socket;
  shogiground: ShogigroundApi;
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
  forecast?: ForecastCtrl;
  retro?: RetroCtrl;
  fork: ForkCtrl;
  practice?: PracticeCtrl;
  study?: StudyCtrl;
  studyPractice?: StudyPracticeCtrl;

  // state flags
  justPlayedUsi?: string;
  autoScrollRequested = false;
  redirecting = false;
  onMainline = true;
  synthetic: boolean; // false if coming from a real game
  imported: boolean; // true if coming from kif or csa
  ongoing: boolean; // true if real game is ongoing
  lionFirstMove: NormalMove | undefined;

  // display flags
  flipped = false;
  embed: boolean;
  showComments = true; // whether to display comments in the move tree
  showAutoShapes: StoredBooleanProp = storedProp('show-auto-shapes', true);
  showGauge: StoredBooleanProp = storedProp('show-gauge', true);
  showMoveAnnotation: StoredBooleanProp = storedProp('show-move-annotation', true);
  showComputer: StoredBooleanProp = storedProp('show-computer', true);
  keyboardHelp: boolean = location.hash === '#keyboard';
  continueWith: boolean = location.hash === '#continue-with';
  studyModal: Prop<boolean> = prop(false);
  threatMode: Prop<boolean> = prop(false);
  treeView: TreeView;

  // underboard inputs
  sfenInput?: string;
  kifInput?: string;
  csaInput?: string;
  urlInput?: string;

  // other paths
  initialPath: Tree.Path;
  contextMenuPath?: Tree.Path;
  gamePath?: Tree.Path;

  // misc
  music?: any;
  nvui?: NvuiPlugin;

  pvUsiQueue: Usi[] = [];

  constructor(
    readonly opts: AnalyseOpts,
    readonly redraw: Redraw,
  ) {
    this.data = opts.data;
    this.embed = opts.embed;
    this.treeView = treeViewCtrl(opts.embed ? 'inline' : 'column');

    if (this.data.forecast) this.forecast = makeForecast(this.data.forecast, this.data, redraw);

    if (li.modules.analyseNvui) this.nvui = li.modules.analyseNvui(redraw);

    this.instanciateEvalCache();

    this.initialize(this.data, false);

    this.instanciateCeval();

    this.initialPath = treePath.root;

    const params = new URLSearchParams(window.location.search);
    if (opts.initialPly) {
      const loc = window.location;
      const intHash =
        loc.hash === '#last' ? this.tree.lastPly() : Number.parseInt(loc.hash.slice(1));
      const plyStr = opts.initialPly === 'url' ? intHash || '' : opts.initialPly;
      // remove location hash - http://stackoverflow.com/questions/1397329/how-to-remove-the-hash-from-window-location-with-javascript-without-page-refresh/5298684#5298684
      // and remove moves query param
      if (intHash || params.has('moves')) {
        params.delete('moves');
        window.history.replaceState(
          '',
          document.title,
          loc.pathname + (params.size ? '?' : '') + params.toString(),
        );
      }
      const mainline = treeOps.mainlineNodeList(this.tree.root);
      if (plyStr === 'last') this.initialPath = treePath.fromNodeList(mainline);
      else {
        const ply = Number.parseInt(plyStr as string);
        if (ply) this.initialPath = treeOps.takePathWhile(mainline, n => n.ply <= ply);
      }
    }

    this.setPath(this.initialPath);

    this.shogiground = Shogiground();
    this.onToggleComputer();
    this.startCeval();
    cevalRestarter();
    const evalParam = params.get('evaluation') || params.get('eval');
    if (defined(evalParam)) {
      requestIdleCallbackWithFallback(() => this.ensureCevalState(evalParam === '1'));
    }

    this.study = opts.study
      ? makeStudy(opts.study, this, (opts.tagTypes || '').split(','), opts.practice)
      : undefined;
    this.setOrientation();
    this.studyPractice = this.study ? this.study.practice : undefined;

    this.shogiground.set(makeConfig(this), true);
    this.showGround(true);

    if (location.hash === '#practice' || this.study?.data.chapter.practice) this.togglePractice();
    else if (location.hash === '#menu') requestIdleCallbackWithFallback(this.actionMenu.toggle);

    keyboard.bind(this);

    li.pubsub.on('jump', (ply: any) => {
      this.jumpToMain(Number.parseInt(ply));
      this.redraw();
    });

    li.pubsub.on('sound_set', (set: string) => {
      if (!this.music && set === 'music')
        loadLishogiScript('misc.music').then(() => {
          this.music = window.lishogi.modules.miscMusic();
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
    this.imported = game.imported(data);
    this.ongoing = !this.synthetic && game.playable(data);

    if (this.data.game.variant.key === 'chushogi') loadChushogiPieceSprite();
    else if (this.data.game.variant.key == 'kyotoshogi') loadKyotoshogiPieceSprite();

    const prevTree = merge && this.tree.root;
    this.tree = makeTree(treeOps.reconstruct(this.data.treeParts));
    if (prevTree) this.tree.merge(prevTree);
    this.initNotation();

    this.actionMenu = new ActionMenuCtrl();
    this.autoplay = new Autoplay(this);
    if (!this.socket) this.socket = makeSocket(this.opts.socketSend, this);
    this.gamePath =
      this.synthetic || this.ongoing
        ? undefined
        : treePath.fromNodeList(treeOps.mainlineNodeList(this.tree.root));
    this.fork = makeFork(this);

    window.lishogi.sound.preloadGameSounds();
  }

  setOrientation = (): void => {
    const userId = document.body.dataset.user;
    const players = this.study?.data.postGameStudy?.players;
    const userOrientation =
      players &&
      (players.sente.userId === userId
        ? 'sente'
        : players.gote.userId === userId
          ? 'gote'
          : undefined);
    this.flipped = !!(userOrientation && this.data.orientation !== userOrientation);
    flipMetaPlayers(userOrientation || this.data.orientation);
  };

  private setPath = (path: Tree.Path): void => {
    this.path = path;
    this.nodeList = this.tree.getNodeList(path);
    this.node = treeOps.last(this.nodeList) as Tree.Node;
    this.mainline = treeOps.mainlineNodeList(this.tree.root);
    this.onMainline = this.tree.pathIsMainline(path);
    this.lionFirstMove = undefined;
    this.sfenInput = undefined;
    this.kifInput = undefined;
    this.csaInput = undefined;
    this.urlInput = undefined;
  };

  flip = (): void => {
    this.flipped = !this.flipped;
    this.shogiground.set({
      orientation: this.bottomColor(),
    });
    if (this.practice) this.restartPractice();
    flipMetaPlayers(this.bottomColor());
    this.redraw();
  };

  topColor(): Color {
    return opposite(this.bottomColor());
  }

  bottomColor(): Color {
    return this.flipped ? opposite(this.data.orientation) : this.data.orientation;
  }

  bottomIsSente = (): boolean => this.bottomColor() === 'sente';

  getOrientation(): Color {
    // required by ui/ceval
    return this.bottomColor();
  }
  getNode(): Tree.Node {
    // required by ui/ceval
    return this.node;
  }

  turnColor(): Color {
    return plyColor(this.node.ply);
  }

  togglePlay(delay: AutoplayDelay): void {
    this.autoplay.toggle(delay);
    this.actionMenu.open = false;
  }

  private showGround(skip?: boolean): void {
    this.onChange();
    if (!skip) this.shogiground.set(this.makeSgOpts());
    this.setAutoShapes();
    this.setShapes(this.node.shapes as DrawShape[] | undefined);
  }

  // allows moving after game end - use pos.isEnd, if needed
  private getMoveDests(posRes: Result<Position>): sg.MoveDests {
    if (this.embed) return new Map();
    else
      return posRes.unwrap(
        pos => shogigroundMoveDests(pos),
        _ => new Map(),
      );
  }

  private getDropDests(posRes: Result<Position>): sg.DropDests {
    if (this.embed) return new Map();
    return posRes.unwrap(
      pos => shogigroundDropDests(pos),
      _ => new Map(),
    );
  }

  makeSgOpts(): ShogigroundConfig {
    const node = this.node;
    const color = this.turnColor();
    const posRes = this.position(this.node);
    const dests = this.getMoveDests(posRes);
    const drops = this.getDropDests(posRes);
    const movableColor =
      this.practice || this.gamebookPlay()
        ? this.bottomColor()
        : !this.embed && (dests.size > 0 || drops.size > 0)
          ? color
          : undefined;
    const splitSfen = node.sfen.split(' ');
    const lastRole = node.usi ? usiToRole(node.usi) : undefined;
    const config: ShogigroundConfig = {
      sfen: {
        board: splitSfen[0],
        hands: splitSfen[2],
      },
      turnColor: color,
      activeColor: this.embed ? undefined : movableColor,
      movable: {
        dests: this.embed || movableColor !== color ? new Map() : dests,
      },
      droppable: {
        dests: this.embed || movableColor !== color ? new Map() : drops,
      },
      checks: node.check,
      lastDests: node.usi ? usiToSquareNames(node.usi) : [],
      lastPiece: lastRole ? { color: opposite(color), role: lastRole } : undefined,
      drawable: {
        squares: [],
      },
    };
    config.premovable = {
      enabled: config.activeColor && config.turnColor !== config.activeColor,
    };
    config.predroppable = {
      enabled: config.activeColor && config.turnColor !== config.activeColor,
    };
    return config;
  }

  private sound = li.sound
    ? {
        move: li.sound.throttlePlay('move', 50),
        capture: li.sound.throttlePlay('capture', 50),
        check: li.sound.throttlePlay('check', 50),
      }
    : {
        move: () => {},
        capture: () => {},
        check: () => {},
      };

  private captureRegex = /[a-z]/gi;

  private onChange: () => void = throttle(300, () => {
    li.pubsub.emit(
      'analysis.change',
      this.node.sfen,
      this.path,
      this.onMainline ? this.node.ply : false,
    );
  });

  private updateHref: () => void = debounce(() => {
    if (!this.opts.study && !this.synthetic)
      window.history.replaceState(null, '', `#${this.node.ply}`);
  }, 500);

  autoScroll(): void {
    this.autoScrollRequested = true;
  }

  playedLastMoveMyself = (): boolean =>
    !!this.justPlayedUsi && this.node.usi === this.justPlayedUsi;

  jump(path: Tree.Path): void {
    const pathChanged = path !== this.path;
    const isForwardStep = pathChanged && path.length == this.path.length + 2;
    this.setPath(path);
    this.showGround();

    if (pathChanged) {
      if (this.study) this.study.setPath(path, this.node);
      if (isForwardStep) {
        // initial position
        if (!this.node.usi) this.sound.move();
        else if (!this.playedLastMoveMyself()) {
          if (this.node.capture) this.sound.capture();
          else this.sound.move();
        }
        if (this.node.check && this.data.game.variant.key !== 'chushogi') this.sound.check();
      }
      this.threatMode(false);
      this.ceval.stop();
      this.startCeval();
      speech.node(this.node);
    }
    this.justPlayedUsi = undefined;
    this.updateHref();
    this.autoScroll();
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
    this.shogiground.selectSquare(null);
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
    this.shogiground.set(makeConfig(this), true);
  }

  changeNotation(notation: string): void {
    this.redirecting = true;
    window.lishogi.xhr
      .json('POST', '/analysis/notation', { formData: { notation } })
      .then((data: AnalyseData) => {
        this.reloadData(data, false);
        const $selSpan = $('.mselect__label span');
        const icon = getPerfIcon(data.game.variant.key);
        $selSpan.attr('data-icon', icon);
        $selSpan.text(i18nVariant(data.game.variant.key));
        $('nav.mselect__list a').each(function (this: HTMLElement) {
          $(this).toggleClass('current', $(this).data('icon') === icon);
        });
        this.userJump(this.mainlinePathToPly(this.tree.lastPly()));
        this.redraw();
      })
      .catch(error => {
        try {
          const res = error as Response;
          res.text().then(t => {
            alert(t);
          });
        } catch {
          alert(`Cannot import notation - ${error}`);
        }
        this.redirecting = false;
        this.redraw();
      });
  }

  changeSfen(sfen: Sfen): void {
    this.redirecting = true;
    window.location.href = analysis(this.data.game.variant.key, sfen);
  }

  userDrop = (piece: Piece, key: Key, prom: boolean): void => {
    let role = piece.role;
    if (prom && promotableOnDrop(this.data.game.variant.key)(piece))
      role = promote(this.data.game.variant.key)(role) || role;
    const usi = makeUsi({ role: role, to: parseSquareName(key) });
    this.justPlayedUsi = usi;
    this.sound.move();
    this.sendUsi(usi);
  };

  userMove = (orig: Key, dest: Key, prom: boolean, capture?: sg.Piece): void => {
    if (this.data.game.variant.key === 'chushogi') {
      this.chushogiUserMove(orig, dest, prom, capture);
      return;
    }

    const usi = orig + dest + (prom ? '+' : '');
    this.justPlayedUsi = usi;
    this.sound[capture ? 'capture' : 'move']();
    this.sendUsi(usi);
  };

  sendUsi = (usi: string): void => {
    const socUsi: any = {
      usi: usi,
      variant: this.data.game.variant.key,
      sfen: this.node.sfen,
      path: this.path,
    };
    if (this.practice) this.practice.onUserMove();
    this.socket.sendAnaUsi(socUsi);
    this.preparePreMD();
    this.redraw();
  };

  private chushogiUserMove = (orig: Key, dest: Key, prom: boolean, capture?: sg.Piece): void => {
    this.sound[capture ? 'capture' : 'move']();

    const posRes = this.position(this.node);
    const piece = posRes.isOk && posRes.value.board.get(parseSquareName(orig))!;
    if (
      piece &&
      this.lionFirstMove === undefined &&
      squareDist(parseSquareName(orig), parseSquareName(dest)) === 1 &&
      (['lion', 'lionpromoted'].includes(piece.role) ||
        (piece.role === 'eagle' &&
          eagleLionAttacks(parseSquareName(orig), piece.color).has(parseSquareName(dest))) ||
        (piece.role === 'falcon' &&
          falconLionAttacks(parseSquareName(orig), piece.color).has(parseSquareName(dest))))
    ) {
      const pos = posRes.value as Chushogi;
      this.shogiground.set({
        activeColor: pos.turn,
        turnColor: pos.turn,
        movable: {
          dests: shogigroundSecondLionStep(pos, orig, dest),
        },
        drawable: {
          squares: [{ key: dest, className: 'force-selected' }],
        },
      });
      this.shogiground.selectSquare(dest, false, true);
      this.lionFirstMove = {
        from: parseSquareName(orig),
        to: parseSquareName(dest),
      };
    } else {
      const hadMid =
        this.lionFirstMove !== undefined && makeSquareName(this.lionFirstMove.to) !== dest;
      const move: NormalMove = {
        from: hadMid ? this.lionFirstMove!.from : parseSquareName(orig),
        to: parseSquareName(dest),
        promotion: prom,
        midStep: hadMid ? this.lionFirstMove!.to : undefined,
      };
      this.lionFirstMove = undefined;
      const usi = makeUsi(move);
      this.justPlayedUsi = usi;
      this.sendUsi(usi);
    }
  };

  private preparePreMD(): void {
    this.shogiground.set({
      turnColor: this.shogiground.state.activeColor as Color,
      activeColor: opposite(this.shogiground.state.activeColor as Color),
      premovable: {
        enabled: true,
      },
      predroppable: {
        enabled: true,
      },
    });
  }

  onPremoveSet = (): void => {
    if (this.study) this.study.onPremoveSet();
  };

  addNode(node: Tree.Node, path: Tree.Path): void {
    const newPath = this.tree.addNode(node, path);
    if (!newPath) {
      this.redraw();
      return;
    }
    const parent = this.tree.nodeAtPath(path);
    if (node.usi) {
      node.notation = makeNotation(parent.sfen, this.data.game.variant.key, node.usi, parent.usi);
      node.capture =
        (parent.sfen.split(' ')[0].match(this.captureRegex) || []).length >
        (node.sfen.split(' ')[0].match(this.captureRegex) || []).length;
    }
    this.jump(newPath);
    this.redraw();
    const queuedUsi = this.pvUsiQueue.shift();
    if (queuedUsi) this.playUsi(queuedUsi, this.pvUsiQueue);
    else this.shogiground.playPremove();
  }

  deleteNode(path: Tree.Path): void {
    const node = this.tree.nodeAtPath(path);
    if (!node) return;
    const count = treeOps.countChildrenAndComments(node);
    if (
      (count.nodes >= 10 || count.comments > 0) &&
      !confirm(
        `${i18n('delete').toUpperCase()}: ${i18nPluralSame('nbMoves', count.nodes)}${count.comments ? ` and ${util.plural('comment', count.comments)}` : ''}?`,
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
    this.redraw();
  }

  currentEvals(): NodeEvals {
    return {
      server: this.node.eval,
      client: this.node.ceval,
    };
  }

  nextNodeBest(): Usi | undefined {
    return treeOps.withMainlineChild(this.node, (n: Tree.Node) =>
      n.eval ? n.eval.best : undefined,
    );
  }

  setAutoShapes = (): void => {
    this.shogiground.setAutoShapes(computeAutoShapes(this));
  };

  setShapes = (shapes?: DrawShape[]): void => {
    if (shapes) this.shogiground.setShapes(shapes);
  };

  private initNotation = (): void => {
    const variant = this.data.game.variant.key;
    const captureRegex = this.captureRegex;
    function update(node: Tree.Node, prev?: Tree.Node) {
      if (prev && node.usi && !node.notation) {
        node.notation = makeNotation(prev.sfen, variant, node.usi, prev.usi);
        node.capture =
          (prev.sfen.split(' ')[0].match(captureRegex) || []).length >
          (node.sfen.split(' ')[0].match(captureRegex) || []).length;
      }
      node.children.forEach(c => update(c, node));
    }
    update(this.tree.root);
  };

  private onNewCeval = (ev: Tree.ClientEval, path: Tree.Path, isThreat: boolean): void => {
    this.tree.updateAt(path, (node: Tree.Node) => {
      if (node.sfen !== ev.sfen && !isThreat) return;
      if (isThreat) {
        const threat = ev as Tree.LocalEval;
        if (
          !node.threat ||
          isEvalBetter(threat, node.threat) ||
          node.threat.maxDepth < threat.maxDepth
        )
          node.threat = threat;
      } else if (!node.ceval || isEvalBetter(ev, node.ceval)) node.ceval = ev;
      else if (!ev.cloud) {
        if (node.ceval.cloud && ev.maxDepth > node.ceval.depth) node.ceval = ev;
        else if (ev.maxDepth > node.ceval.maxDepth!) node.ceval.maxDepth = ev.maxDepth;
      }

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
    this.ceval = cevalCtrl({
      variant: this.data.game.variant,
      initialSfen: this.data.game.initialSfen,
      possible: !this.embed && (this.synthetic || !game.playable(this.data)),
      emit: (ev: Tree.ClientEval, work: EvalMeta) => {
        this.onNewCeval(ev, work.path, work.threatMode);
      },
      setAutoShapes: this.setAutoShapes,
      redraw: this.redraw,
      ...(this.opts.study && this.opts.practice
        ? {
            storageKeyPrefix: 'practice',
            multiPvDefault: 1,
          }
        : {}),
    });
  }

  getCeval(): CevalCtrl {
    return this.ceval;
  }

  outcome(node?: Tree.Node): Outcome | undefined {
    return this.position(node || this.node).unwrap(
      pos => pos.outcome(),
      _ => undefined,
    );
  }

  isImpasse(node?: Tree.Node): boolean {
    node = node || this.node;
    return impasse(this.data.game.variant.key, node.sfen, this.data.game.initialSfen);
  }

  isHandicap(): boolean {
    return sgIsHandicap({
      rules: this.data.game.variant.key,
      sfen: this.data.game.initialSfen,
    });
  }

  position(node: Tree.Node): Result<Position, PositionError> {
    return parseSfen(this.data.game.variant.key, node.sfen, false);
  }

  canUseCeval(): boolean {
    return !this.node.fourfold && !this.outcome();
  }

  startCeval: () => void = throttle(800, () => {
    if (this.ceval.enabled()) {
      if (this.canUseCeval()) {
        this.ceval.start(this.path, this.nodeList, this.threatMode());
        this.evalCache.fetch(this.path, Number.parseInt(this.ceval.multiPv()));
      } else this.ceval.stop();
    }
  });

  toggleCeval = (): void => {
    if (!this.showComputer() || this.ceval.technology === 'none') return;
    this.ceval.toggle();
    this.setAutoShapes();
    this.startCeval();
    if (!this.ceval.enabled()) {
      this.threatMode(false);
      if (this.practice) this.togglePractice();
    }
    this.redraw();
  };

  toggleThreatMode = (): void => {
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
    this.ceval.setThreads(v);
    this.cevalReset();
  };

  cevalSetHashSize = (v: number): void => {
    this.ceval.setHashSize(v);
    this.cevalReset();
  };

  cevalSetInfinite = (v: boolean): void => {
    this.ceval.infinite(v);
    this.cevalReset();
  };

  cevalSetEnableNnue = (v: boolean): void => {
    this.ceval.enableNnue(v);
    this.ceval.stop();
    alert('Reload the window to see changes.');
  };

  cevalSetEnteringKingRule = (v: boolean): void => {
    this.ceval.enteringKingRule(v);
    this.tree.removeCeval();
    this.cevalReset();
  };

  showEvalGauge(): boolean {
    return (
      this.hasAnyComputerAnalysis() && this.showGauge() && !this.outcome() && this.showComputer()
    );
  }

  hasAnyComputerAnalysis(): boolean {
    return this.data.analysis ? true : this.ceval.enabled();
  }

  hasFullComputerAnalysis = (): boolean => {
    return Object.keys(this.mainline[0].eval || {}).length > 0;
  };

  private resetAutoShapes() {
    if (this.showAutoShapes() || this.showMoveAnnotation()) this.setAutoShapes();
    else this.shogiground?.setAutoShapes([]);
  }

  toggleAutoShapes = (v: boolean): void => {
    this.showAutoShapes(v);
    this.resetAutoShapes();
  };

  toggleGauge = (): void => {
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
      this.shogiground.setAutoShapes([]);
    } else this.resetAutoShapes();
  }

  toggleComputer = (): void => {
    if (this.ceval.enabled()) this.toggleCeval();
    const value = !this.showComputer();
    this.showComputer(value);
    if (!value && this.practice) this.togglePractice();
    this.onToggleComputer();
    li.pubsub.emit('analysis.comp.toggle', value);
  };

  ensureCevalState(shouldBeRunning: boolean): void {
    if (!this.showComputer()) this.toggleComputer();
    if (this.ceval.enabled() !== shouldBeRunning) this.toggleCeval();
    if (this.threatMode() && shouldBeRunning) this.toggleThreatMode();
  }

  mergeAnalysisData(data: ServerEvalData): void {
    if (this.study && this.study.data.chapter.id !== data.ch) return;
    this.tree.merge(data.tree);
    this.initNotation();
    if (!this.showComputer()) this.tree.removeComputerVariations();
    this.data.analysis = data.analysis;
    if (data.analysis)
      data.analysis.partial = !!treeOps.findInMainline(data.tree, this.partialAnalysisCallback);
    if (data.division) this.data.game.division = data.division;
    if (this.retro) this.retro.onMergeAnalysisData();
    li.pubsub.emit('analysis.server.progress', this.data);
    this.redraw();
  }

  partialAnalysisCallback(n: Tree.Node): boolean {
    return !n.eval && !!n.children.length && n.ply <= 200 && n.ply > 0;
  }

  playUsi(usi: Usi, usiQueue?: Usi[]): void {
    this.pvUsiQueue = usiQueue ?? [];
    this.sendUsi(usi);
  }

  playUsiList(usiList: Usi[]): void {
    this.pvUsiQueue = usiList;
    const firstUsi = this.pvUsiQueue.shift();
    if (firstUsi) this.playUsi(firstUsi, this.pvUsiQueue);
  }

  playBestMove(): void {
    const usi = this.nextNodeBest() || this.node.ceval?.pvs[0].moves[0];
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

  instanciateEvalCache(): void {
    this.evalCache = makeEvalCache({
      variant: this.data.game.variant.key,
      canGet: () => this.canEvalGet(),
      canPut: () =>
        this.ceval?.cachable &&
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
    }
    this.setAutoShapes();
  };

  togglePractice = (): void => {
    if (this.practice || !this.ceval.possible) this.practice = undefined;
    else {
      if (this.retro) this.toggleRetro();
      this.practice = makePractice(this, () => {
        // push to 20 to store AI moves in the cloud
        // lower to 18 after task completion (or failure)
        return this.studyPractice && this.studyPractice.success() === null ? 20 : 18;
      });
    }
    this.setAutoShapes();
  };

  restartPractice(): void {
    this.practice = undefined;
    this.togglePractice();
  }

  gamebookPlay = (): GamebookPlayCtrl | undefined => {
    return this.study?.gamebookPlay();
  };

  isGamebook = (): boolean => !!this.study?.data.chapter.gamebook;

  // plies respect color - it is even if it's sente turn and vice versa
  // but move number is a separate thing
  // so instead of sending both
  // let's just count the offset
  plyOffset = (): number => {
    return game.plyOffset(this.data);
  };

  // Ideally we would just use node.clock
  // but we store remaining times for lishogi games as node.clock
  // for imports we store movetime as node.clock, because
  // that's what's provided next to each move
  getMovetime = (node: Tree.Node): number | undefined => {
    const offset = this.mainline[0].ply;
    if (defined(node.clock) && !this.study) {
      if (defined(this.data.game.moveCentis))
        return this.data.game.moveCentis[node.ply - 1 - offset];
      if (this.imported) return node.clock;
    }
    return;
  };
}
