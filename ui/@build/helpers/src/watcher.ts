import {
  existsSync,
  type FSWatcher,
  watch as fswatch,
  statSync,
  type WatchEventType,
} from 'node:fs';
import path from 'node:path';

export type Changes = ReadonlyMap<string, WatchEventType>;

export function watchers(
  paths: string[],
  f: (changes: Changes) => Promise<void>,
  delay = 100,
): FSWatcher[] {
  let pending = new Map<string, WatchEventType>();
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  let running = false;

  const record = (event: WatchEventType, filepath: string) => {
    if (pending.get(filepath) !== 'rename') pending.set(filepath, event);
    schedule();
  };

  const schedule = () => {
    clearTimeout(timeoutId);
    timeoutId = setTimeout(flush, delay);
  };

  const flush = async () => {
    if (running || pending.size === 0) return;
    running = true;
    const batch = pending;
    pending = new Map();
    try {
      await f(batch);
    } catch (error) {
      console.error(error);
    } finally {
      running = false;
      if (pending.size) schedule();
    }
  };

  const fswatchers: FSWatcher[] = [];

  const dirs: string[] = [];
  const files = new Map<string, Set<string>>();
  for (const p of new Set(paths)) {
    if (!existsSync(p)) continue;
    if (statSync(p).isDirectory()) dirs.push(p);
    else {
      const dir = path.dirname(p);
      if (!files.has(dir)) files.set(dir, new Set());
      files.get(dir)!.add(path.basename(p));
    }
  }

  for (const dir of dirs) {
    fswatchers.push(
      fswatch(dir, { recursive: true }, (event, filename) =>
        record(filename ? event : 'rename', filename ? path.join(dir, filename) : dir),
      ),
    );
  }

  // Watch the parent folder rather than the file itself, so atomic saves
  // (write temp + rename over) don't detach the watcher.
  for (const [dir, names] of files) {
    fswatchers.push(
      fswatch(dir, { recursive: false }, (event, filename) => {
        if (filename && names.has(filename)) record(event, path.join(dir, filename));
      }),
    );
  }

  return fswatchers;
}
