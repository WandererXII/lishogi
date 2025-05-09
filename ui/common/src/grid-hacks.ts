import throttle from './throttle';

export const runner = (hacks: () => void, throttleMs = 100): void => {
  let timeout: number | undefined;

  const runHacks = throttle(throttleMs, () =>
    requestAnimationFrame(() => {
      hacks();
      schedule();
    }),
  );

  function schedule() {
    timeout && clearTimeout(timeout);
    timeout = setTimeout(runHacks, 500);
  }

  runHacks();
};

let boundShogigroundResize = false;

export const bindShogigroundResizeOnce = (f: () => void): void => {
  if (!boundShogigroundResize) {
    boundShogigroundResize = true;
    document.body.addEventListener('shogiground.resize', f);
  }
};
