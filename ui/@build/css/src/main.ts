import { existsSync } from 'node:fs';
import path from 'node:path';
import { glob } from '@build/helpers/glob';
import type { Changes } from '@build/helpers/watcher';
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
    onChange: async (changes: Changes) => {
      const files = [...changes].filter(([filepath]) => !filepath.includes('/generated/'));
      if (!files.length) return;

      console.log(`Change detected in: ${files.map(([filepath]) => filepath).join(', ')}`);

      if (files.some(([, event]) => event === 'rename')) graph.reinit();
      else for (const [filepath] of files) graph.update(filepath);

      const toBuild = new Set<string>();

      let rebuildVars = false;
      for (const [filepath] of files) {
        if (filepath.endsWith('.scss') && existsSync(filepath))
          rebuildVars = (await cssVariableBuilder.update(filepath)) || rebuildVars;
      }
      if (rebuildVars) {
        const themeFiles = (await glob(path.join(rootDir, '/**/common/css/build/*.scss'))).filter(
          buildFilesFilter,
        );
        console.log('Rebuilding theme files');
        themeFiles.forEach(file => {
          toBuild.add(file);
        });
      }

      for (const [filepath] of files) {
        graph
          .impacted(filepath)
          .filter(buildFilesFilter)
          .forEach(file => {
            toBuild.add(file);
          });
      }
      await Promise.all([...toBuild].map(file => builder.build(file)));
      console.log(`Recompiled ${toBuild.size} scss files`);
    },
    stop: async () => {
      await builder.stop();
    },
    watchPaths: ['css'],
  };
});
