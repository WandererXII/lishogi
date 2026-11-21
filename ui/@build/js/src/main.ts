import fs from 'node:fs/promises';
import path from 'node:path';
import { glob } from '@build/helpers/glob';
import type { Changes } from '@build/helpers/watcher';
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
    onChange: async (changes: Changes) => {
      // packages that need a fresh build context (non-ts change or file added/removed)
      const toClear = new Set<Project>();
      const toBuild = new Set<Project>();
      // packages whose old output files should be deleted
      const toDelete = new Set<Project>();

      for (const [filepath, event] of changes) {
        const pkg = getPackageFromPath(pkgs, filepath);
        if (!pkg) continue;
        console.log(`Change detected in: ${filepath}`);

        const impacted = findImpactedPackages(pkg, pkgs);
        impacted.forEach(i => {
          toBuild.add(i);
        });
        if (!filepath.endsWith('.ts') || event === 'rename') {
          impacted.forEach(i => {
            toClear.add(i);
          });
          toDelete.add(pkg);
        }
      }

      await Promise.all([...toClear].map(i => clearOne(i)));
      for (const pkg of toDelete) {
        const filesToDelete = await glob(path.join(outdir, `lishogi.${pkg.manifest.name}.*`));
        await Promise.all(filesToDelete.map(file => fs.rm(file, { force: true })));
      }
      await Promise.all([...toBuild].map(i => buildOne(i)));
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
