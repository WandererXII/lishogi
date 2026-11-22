import { existsSync } from 'node:fs';
import path from 'node:path';
import { SassGraph } from 'sass-graph-ts';

interface Graph {
  reinit: () => void;
  update: (path: string) => void;
  impacted: (path: string) => string[];
}

export function createGraph(rootDir: string): Graph {
  let graph: SassGraph = initGraph(rootDir);

  function recImports(path: string, set: Set<string>): Set<string> {
    if (set.has(path)) return set;
    set.add(path);

    if (graph.index[path]) {
      const paths = graph.index[path].importedBy;
      paths.forEach(p => {
        recImports(p, set);
      });
    }

    return set;
  }

  return {
    reinit: () => {
      graph = initGraph(rootDir);
    },
    update: (path: string): void => {
      if (!graph.index[path]) graph = initGraph(rootDir);
      else {
        const fileImports = SassGraph.parseFile(path, {
          extensions: ['scss'],
          resolver: localPkgSchemeResolver(rootDir),
        }).index[path].imports;

        for (const dep of graph.index[path].imports) {
          if (!fileImports.includes(dep)) {
            graph.index[dep].importedBy = graph.index[dep].importedBy.filter(d => d !== path);
          }
        }
        for (const dep of fileImports) {
          const depImportedBy = graph.index[dep].importedBy;
          if (!depImportedBy.includes(path)) {
            graph.index[dep].importedBy.push(path);
          }
        }

        graph.index[path].imports = fileImports;
      }
    },
    impacted(path) {
      return Array.from(recImports(path, new Set<string>()));
    },
  };
}

function initGraph(rootDir: string): SassGraph {
  return SassGraph.parseDir(`${rootDir}/ui/`, {
    extensions: ['scss'],
    resolver: localPkgSchemeResolver(rootDir),
  })!;
}

function localPkgSchemeResolver(rootDir: string): (importPath: string) => string | false {
  return (importPath: string) => {
    if (importPath.startsWith('ui/'))
      importPath = importPath.replace(/^ui\/([^/]+)\/style\/(.+)$/, 'ui/$1/$2');
    const match = importPath.match(/^(?:pkg:|ui\/)([^/]+)\/(.+)$/);

    if (match) {
      const packageName = match[1];
      let restOfPath = match[2];
      const basePath = path.join(rootDir, 'ui', packageName, 'style');

      if (restOfPath === 'theme') restOfPath = 'theme/generated/theme';

      const firstPath = path.join(
        basePath,
        path.dirname(restOfPath),
        `_${path.basename(restOfPath)}.scss`,
      );
      if (existsSync(firstPath)) return firstPath;

      const fallbackPath = path.join(basePath, `${restOfPath}.scss`);
      if (existsSync(fallbackPath)) return fallbackPath;
    }

    return false;
  };
}
