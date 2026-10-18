import type { WatchEventType } from 'node:fs';
import path from 'node:path';
import { glob } from '@build/helpers/glob';
import { workspaceBuildConductor } from '@build/helpers/workspace-builder';
import type { Project } from '@build/helpers/workspace-packages';
import { createBuilder } from './builder.js';
import { createCssVariableBuilder } from './css-variables/builder.js';
import { createGraph } from './graph.js';

await workspaceBuildConductor('css', async (rootDir: string, pkgs: Project[], outdir: string) => {
  const graph = createGraph(rootDir);
  const builder = await createBuilder(rootDir, pkgs, outdir);
  const cssVariableBuilder = await createCssVariableBuilder(rootDir, pkgs);

  const buildFilesFilter = (filepath: string) =>
    path.basename(path.dirname(filepath)) === 'build' && !path.basename(filepath).startsWith('_');

  return {
    all: async () => {
      const buildFiles = (
        await glob(pkgs.map(p => path.join(p.rootDirRealPath, '/**/css/build/*.scss')))
      ).filter(buildFilesFilter);

      await cssVariableBuilder.build();

      await Promise.all(buildFiles.map(file => builder.build(file)));
      console.log(`Compiled ${buildFiles.length} scss files`);
    },
    onChange: async (event: WatchEventType, filepath: string | null) => {
      if (filepath === null || filepath.includes('/generated/')) return;

      console.log(`Change detected in: ${filepath}`);

      if (event === 'rename') graph.reinit();
      else graph.update(filepath);

      const rebuildVars = await cssVariableBuilder.update(filepath);
      if (rebuildVars) {
        const themeFiles = (await glob(path.join(rootDir, '/**/common/css/build/*.scss'))).filter(
          buildFilesFilter,
        );
        console.log('Rebuilding theme files');
        themeFiles.map(file => builder.build(file));
      }

      const impacted = graph.impacted(filepath).filter(buildFilesFilter);
      await Promise.all(impacted.map(file => builder.build(file)));
      console.log(`Recompiled ${impacted.length} scss files`);
    },
    stop: async () => {
      await builder.stop();
    },
    watchPaths: ['css'],
  };
});
