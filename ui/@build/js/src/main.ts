import type { WatchEventType } from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';
import { glob } from '@build/helpers/glob';
import { workspaceBuildConductor } from '@build/helpers/workspace-builder';
import { getPackageFromPath, type Project } from '@build/helpers/workspace-packages';
import type { BuildContext } from 'esbuild';
import { createBuildContexts } from './build-context.js';

await workspaceBuildConductor('js', async (_rootDir: string, pkgs: Project[], outdir: string) => {
  const contexts = new Map<string, BuildContext[]>();

  async function buildOne(pkg: Project): Promise<void> {
    let pkgCtxs = contexts.get(pkg.manifest.name);
    if (!pkgCtxs) {
      pkgCtxs = await createBuildContexts(pkg, outdir);
      contexts.set(pkg.manifest.name, pkgCtxs);
    }

    if (pkgCtxs.length) {
      await Promise.all(pkgCtxs.map(ctx => ctx.cancel()));
      await Promise.all(pkgCtxs.map(ctx => ctx.rebuild()));
      console.log(`Bundled ${pkg.manifest.name}`);
    }
  }

  async function clearOne(pkg: Project): Promise<void> {
    const ctxs = contexts.get(pkg.manifest.name);
    if (ctxs?.length) {
      await Promise.all(ctxs.map(ctx => ctx.cancel()));
      await Promise.all(ctxs.map(ctx => ctx.dispose()));
      contexts.delete(pkg.manifest.name);
    }
  }

  function all(f: (pkg: Project) => Promise<void>): () => Promise<void> {
    return async () => {
      await Promise.all(pkgs.map(f));
    };
  }

  return {
    all: all(buildOne),
    onChange: async (event: WatchEventType, filepath: string | null) => {
      if (filepath === null) return;

      const pkg = getPackageFromPath(pkgs, filepath)!;
      const impacted = findImpactedPackages(pkg, pkgs);

      console.log(`Change detected in: ${filepath}`);

      if (!filepath?.endsWith('.ts') || event === 'rename') {
        await Promise.all(impacted.map(i => clearOne(i)));
        const filesToDelete = await glob(path.join(outdir, `lishogi.${pkg.manifest.name}.*`));
        await Promise.all(filesToDelete.map(file => fs.rm(file)));
      }
      await Promise.all(impacted.map(i => buildOne(i)));
    },
    stop: all(clearOne),
    watchPaths: ['src'],
  };
});

function findImpactedPackages(pkg: Project, pkgs: Project[]): Project[] {
  const traverseRevDeps = (currentPkg: Project, visited: Set<string> = new Set()): Set<string> => {
    if (!visited.has(currentPkg.manifest.name)) {
      visited.add(currentPkg.manifest.name);
      const revDeps = pkgs.filter(p =>
        Object.keys(p.manifest.dependencies || {}).includes(currentPkg.manifest.name),
      );
      revDeps.forEach(dep => {
        traverseRevDeps(dep, visited);
      });
    }
    return visited;
  };

  const deps = traverseRevDeps(pkg);
  return pkgs.filter(p => deps.has(p.manifest.name));
}
