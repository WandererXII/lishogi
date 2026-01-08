import type { FSWatcher, WatchEventType } from 'node:fs';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { getOutputDirectory } from './util.js';
import { watchers } from './watcher.js';
import { getPackages, getRootDir, type Project } from './workspace-packages.js';

interface WorkspaceBuilder {
  all: () => Promise<void>;
  onChange: (event: WatchEventType, filepath: string) => Promise<void>;
  stop: () => Promise<void>;
  watchPaths: string[];
}

export async function workspaceBuildConductor(
  key: string,
  workspaceBuilder: (rootDir: string, pkgs: Project[], outdir: string) => Promise<WorkspaceBuilder>,
): Promise<void> {
  try {
    const rootDir = await getRootDir();
    const pkgs = await getPackages(rootDir);

    const isWatch = process.argv.includes('--watch');
    const fswatchers: FSWatcher[] = [];

    const outdir = path.join(rootDir, getOutputDirectory(), key);

    await fs.rm(outdir, { recursive: true, force: true });
    await fs.mkdir(outdir, { recursive: true });

    const builder = await workspaceBuilder(rootDir, pkgs, outdir);

    const signals = ['SIGINT', 'SIGTERM'];
    signals.forEach(sig => {
      process.on(sig, async () => {
        await builder.stop();
        fswatchers.forEach(fsw => {
          fsw.close();
        });
        process.exit(0);
      });
    });

    await builder.all();

    if (isWatch) {
      const onChange = async (event: WatchEventType, filepath: string) => {
        try {
          await builder.onChange(event, filepath);
        } catch (error) {
          console.error(error);
        }
      };

      const packageWatch = builder.watchPaths.filter(wp => !path.isAbsolute(wp));
      for (const pkg of pkgs) {
        fswatchers.push(
          ...watchers(
            ['package.json', 'tsconfig.json', ...packageWatch].map(wp =>
              path.join(pkg.rootDirRealPath, wp),
            ),
            onChange,
          ),
        );
      }

      const absoluteWatch = builder.watchPaths.filter(wp => path.isAbsolute(wp));
      fswatchers.push(...watchers(absoluteWatch, onChange));

      console.log('Watching for changes...');
      process.stdin.resume();
    } else {
      await builder.stop();
      process.exit(0);
    }
  } catch (error) {
    console.error(error);
    process.exit(1);
  }
}
