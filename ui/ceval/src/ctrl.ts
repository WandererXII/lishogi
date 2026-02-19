import { prop } from 'common/common';
import { isAndroid, isIOS, isIPad } from 'common/mobile';
import { storedProp } from 'common/storage';
import throttle from 'common/throttle';
import { EngineCode, engineCode, engineNameFromCode } from 'shogi/engine-name';
import { isImpasse } from 'shogi/impasse';
import { initialSfen, parseSfen } from 'shogiops/sfen';
import { Cache } from './cache';
import type {
  CevalCtrl,
  CevalOpts,
  CevalTechnology,
  Hovering,
  PvBoard,
  Started,
  Step,
  Work,
} from './types';
import { invalidPosition, unsupportedVariants } from './util';
import { povChances } from './winning-chances';
import { type AbstractWorker, ThreadedWasmWorker } from './worker';

const sharedWasmMemory = (lo: number, hi = 32767): WebAssembly.Memory => {
  let shrink = 4; // 32767 -> 24576 -> 16384 -> 12288 -> 8192 -> 6144 -> etc
  while (true) {
    try {
      console.debug(`Creating memory, lo: ${lo}, hi: ${hi}`);
      return new WebAssembly.Memory({ shared: true, initial: lo, maximum: hi });
    } catch (e) {
      console.error(`Failed to create memory, lo: ${lo}, hi: ${hi}`);
      if (hi < lo || !(e instanceof RangeError)) throw e;
      hi = Math.max(lo, Math.ceil(hi - hi / shrink));
      shrink = shrink === 4 ? 3 : 4;
    }
  }
};

function sharedMemoryTest(): boolean {
  if (typeof Atomics !== 'object' || typeof SharedArrayBuffer !== 'function') return false;

  try {
    const mem = new WebAssembly.Memory({ shared: true, initial: 1, maximum: 2 });
    if (!(mem.buffer instanceof SharedArrayBuffer)) return false;
    window.postMessage(mem.buffer, '*');
    return true;
  } catch {
    return false;
  }
}

function defaultDepth(technology: CevalTechnology, threads: number, multiPv: number): number {
  const extraDepth = Math.min(Math.max(threads - multiPv, 0), 6);
  switch (technology) {
    case 'none':
      return 0;
    case 'hce':
      return 18 + extraDepth;
    case 'nnue':
      return 20 + extraDepth;
  }
}

const cevalDisabledSentinel = '1';

function enabledAfterDisable() {
  const enabledAfter = window.lishogi.tempStorage.get('ceval.enabled-after');
  const disable = window.lishogi.storage.get('ceval.disable') || cevalDisabledSentinel;
  return enabledAfter === disable;
}

function isAnalysable(variant: VariantKey, sfen: Sfen | undefined): boolean {
  const pos = parseSfen(variant, sfen || initialSfen(variant), false);
  return pos.isOk && !unsupportedVariants.includes(variant) && !invalidPosition(pos.value);
}

export default function (opts: CevalOpts): CevalCtrl {
  const storageKey = (k: string) => {
    return opts.storageKeyPrefix ? `${opts.storageKeyPrefix}.${k}` : k;
  };
  const enableNnue = storedProp('ceval.enable-nnue', true);

  // check root position
  const analysable = isAnalysable(opts.variant.key, opts.initialSfen);

  // select nnue > hce > none
  const useYaneuraou =
    analysable && engineCode(opts.variant.key, opts.initialSfen) === EngineCode.YaneuraOu;
  const fairySupports = analysable && !useYaneuraou;
  let supportsNnue = false;
  let technology: CevalTechnology = 'none';
  if (
    (useYaneuraou || fairySupports) &&
    typeof WebAssembly === 'object' &&
    typeof WebAssembly.validate === 'function' &&
    WebAssembly.validate(Uint8Array.from([0, 97, 115, 109, 1, 0, 0, 0]))
  ) {
    const sharedMem = sharedMemoryTest();
    if (sharedMem) {
      technology = 'hce';

      const sourceWithSimd = Uint8Array.from([
        0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 96, 0, 1, 123, 3, 2, 1, 0, 7, 8, 1, 4, 116, 101, 115,
        116, 0, 0, 10, 15, 1, 13, 0, 65, 0, 253, 17, 65, 0, 253, 17, 253, 186, 1, 11,
      ]);
      supportsNnue = WebAssembly.validate(sourceWithSimd);
      if (useYaneuraou && enableNnue()) technology = 'nnue';
    }
  }

  const maxThreads = Math.max((navigator.hardwareConcurrency || 1) - 1, 1);
  const threads = () => {
    const stored = window.lishogi.storage.get(storageKey('ceval.threads'));
    return Math.min(
      maxThreads,
      stored ? Number.parseInt(stored, 10) : Math.ceil((navigator.hardwareConcurrency || 1) / 4),
    );
  };
  const pow2floor = (n: number) => {
    let pow2 = 1;
    while (pow2 * 2 <= n) pow2 *= 2;
    return pow2;
  };
  const maxWasmPages = (minPages: number): number => {
    let maxPages = 32768; // hopefully desktop browser, 2 GB max shared
    if (isAndroid())
      maxPages = 8192; // 512 MB max shared
    else if (isIPad())
      maxPages = 8192; // 512 MB max shared
    else if (isIOS()) maxPages = 4096; // 256 MB max shared
    return Math.max(minPages, maxPages);
  };
  const maxHashMB = (): number => {
    let maxHash = 256; // this is conservative but safe, mostly desktop firefox / mac safari users here
    if (navigator.deviceMemory) maxHash = pow2floor(navigator.deviceMemory * 128); // chrome/edge/opera

    if (isAndroid())
      maxHash = 64; // budget androids are easy to crash @ 128
    else if (isIPad())
      maxHash = 64; // ipados safari pretends to be desktop but acts more like iphone
    else if (isIOS()) maxHash = 32;

    return maxHash;
  };
  const maxHashSize = maxHashMB();

  const hashSize = () => {
    const stored = window.lishogi.storage.get(storageKey('ceval.hash-size'));
    return Math.min(maxHashSize, stored ? Number.parseInt(stored, 10) : 16);
  };
  const multiPv = storedProp(storageKey('ceval.multipv'), opts.multiPvDefault || 1);
  const enteringKingRule = storedProp(storageKey('ceval.enteringKingRule'), true);
  const infinite = storedProp('ceval.infinite', false);
  const fixedMemory = storedProp('ceval.fixedMemory', false);
  let curEval: Tree.LocalEval | null = null;
  const allowed = prop(true);
  const enabled = prop(
    technology !== 'none' && opts.possible && analysable && allowed() && enabledAfterDisable(),
  );
  const downloadProgress = prop(0);
  let running = false;
  let lastStarted: Started | false = false; // last started object (for going deeper even if stopped)
  const hovering = prop<Hovering | null>(null);
  const pvBoard = prop<PvBoard | null>(null);
  const isDeeper = prop(false);

  let worker: AbstractWorker<unknown> | undefined;

  let lastEmitSfen: string | null = null;
  const onEmit = throttle(200, (ev: Tree.LocalEval, work: Work) => {
    sortPvsInPlace(ev.pvs, work.ply % 2 === (work.threatMode ? 1 : 0) ? 'sente' : 'gote');
    curEval = ev;
    opts.emit(ev, work);
    if (ev.sfen !== lastEmitSfen && enabledAfterDisable()) {
      // amnesty while auto disable not processed
      lastEmitSfen = ev.sfen;
      window.lishogi.storage.fire('ceval.sfen', ev.sfen);
    }
  });

  const curDepth = () => (curEval ? curEval.depth : 0);

  const effectiveMaxDepth = () =>
    isDeeper() || infinite() ? 99 : defaultDepth(technology, threads(), Number.parseInt(multiPv()));

  const sortPvsInPlace = (pvs: Tree.PvData[], color: Color) =>
    pvs.sort((a, b) => povChances(color, b) - povChances(color, a));

  const start = (path: Tree.Path, steps: Step[], threatMode: boolean) => {
    const step = steps[steps.length - 1];

    if (!step) return;

    const impassePosition = isImpasse(opts.variant.key, step.sfen, steps[0].sfen);

    if (
      !enabled() ||
      !opts.possible ||
      !enabledAfterDisable() ||
      (impassePosition && enteringKingRule()) ||
      !isAnalysable(opts.variant.key, step.sfen)
    )
      return;

    const maxDepth = effectiveMaxDepth();

    const existing = threatMode ? step.threat : step.ceval;
    if (existing && existing.depth >= maxDepth) {
      lastStarted = {
        path,
        steps,
        threatMode,
      };
      return;
    }

    const work: Work = {
      variant: opts.variant.key,
      threads: threads(),
      hashSize: hashSize(),
      enteringKingRule: enteringKingRule(),
      stopRequested: false,
      initialSfen: steps[0].sfen,
      moves: [],
      currentSfen: step.sfen,
      path,
      ply: step.ply,
      maxDepth,
      multiPv: Number.parseInt(multiPv()),
      threatMode,
      emit(ev: Tree.LocalEval) {
        if (enabled()) onEmit(ev, work);
      },
    };

    if (threatMode) {
      const c = step.ply % 2 === 1 ? 'b' : 'w';
      const sfen = step.sfen.replace(/ (b|w) /, ` ${c} `);
      work.currentSfen = sfen;
      work.initialSfen = sfen;
    } else {
      for (let i = 1; i < steps.length; i++) {
        const s = steps[i];
        work.moves.push(s.usi!);
      }
    }

    // Notify all other tabs to disable ceval.
    window.lishogi.storage.fire('ceval.disable');
    window.lishogi.tempStorage.set(
      'ceval.enabled-after',
      window.lishogi.storage.get('ceval.disable')!,
    );

    if (!worker) {
      if (technology == 'nnue') {
        const minMemory = 2048;
        const maxMemory = maxWasmPages(minMemory);
        worker = new ThreadedWasmWorker({
          baseName: 'yaneuraou.k-p',
          baseUrl: 'vendors/yaneuraou.k-p/lib/',
          module: 'YaneuraOu_K_P',
          downloadProgress: throttle(200, mb => {
            downloadProgress(mb);
            opts.redraw();
          }),
          version: 'd49b90b2A',
          wasmMemory: sharedWasmMemory(fixedMemory() ? maxMemory : minMemory, maxMemory),
          cache: window.indexedDB && new Cache('ceval-wasm-cache'),
        });
      } else if (technology == 'hce') {
        const minMemory = 2048;
        const maxMemory = maxWasmPages(minMemory);
        worker = new ThreadedWasmWorker({
          baseName: 'stockfish',
          baseUrl: 'vendors/fairy-stockfish-nnue.wasm/',
          module: 'Stockfish',
          version: 'a410474a',
          wasmMemory: sharedWasmMemory(fixedMemory() ? maxMemory : minMemory, maxMemory),
        });
      }
    }

    if (worker) worker.start(work);

    running = true;
    lastStarted = {
      path,
      steps,
      threatMode,
    };
  };

  function goDeeper() {
    isDeeper(true);
    if (lastStarted) {
      if (infinite()) {
        if (curEval) opts.emit(curEval, lastStarted);
      } else {
        stop();
        start(lastStarted.path, lastStarted.steps, lastStarted.threatMode);
      }
    }
  }

  function stop() {
    if (!enabled() || !running) return;
    worker?.stop();
    running = false;
  }

  const showingCloud = (): boolean => {
    if (!lastStarted) return false;
    const curr = lastStarted.steps[lastStarted.steps.length - 1];
    return !!curr.ceval?.cloud;
  };

  return {
    technology,
    start: (path, steps, threadMode) => {
      isDeeper(false);
      start(path, steps, !!threadMode);
    },
    stop,
    allowed,
    possible: opts.possible,
    enabled,
    downloadProgress,
    multiPv,
    enteringKingRule,
    fixedMemory,
    threads,
    setThreads(threads: number) {
      window.lishogi.storage.set(storageKey('ceval.threads'), threads.toString());
    },
    maxThreads,
    hashSize,
    setHashSize(hash: number) {
      window.lishogi.storage.set(storageKey('ceval.hash-size'), hash.toString());
    },
    maxHashSize,
    infinite,
    supportsNnue,
    enableNnue,
    hovering,
    setHovering(sfen: Sfen, usi?: Usi) {
      hovering(
        usi
          ? {
              sfen,
              usi,
            }
          : null,
      );
      opts.setAutoShapes();
    },
    pvBoard,
    setPvBoard(_pvBoard: PvBoard | null) {
      pvBoard(_pvBoard);
      opts.redraw();
    },
    toggle() {
      if (!opts.possible || !allowed()) return;
      stop();
      if (!enabled() && !document.hidden) {
        const disable = window.lishogi.storage.get('ceval.disable') || cevalDisabledSentinel;
        if (disable) window.lishogi.tempStorage.set('ceval.enabled-after', disable);
        enabled(true);
      } else {
        window.lishogi.tempStorage.set('ceval.enabled-after', '');
        enabled(false);
      }
    },
    curDepth,
    effectiveMaxDepth,
    variant: opts.variant,
    isDeeper,
    goDeeper,
    canGoDeeper: () =>
      technology !== 'none' &&
      curDepth() < 99 &&
      !isDeeper() &&
      ((!infinite() && !worker?.isComputing()) || showingCloud()),
    isComputing: () => !!running && !!worker?.isComputing(),
    engineName:
      technology !== 'none'
        ? technology === 'hce'
          ? engineNameFromCode(EngineCode.Fairy)
          : engineNameFromCode(EngineCode.YaneuraOu)
        : undefined,
    destroy: () => worker?.destroy(),
    redraw: opts.redraw,
    shouldUseYaneuraou: useYaneuraou,
    cachable: technology === 'nnue' || (!useYaneuraou && technology === 'hce'),
    analysable: (sfen: Sfen) => isAnalysable(opts.variant.key, sfen),
  };
}
