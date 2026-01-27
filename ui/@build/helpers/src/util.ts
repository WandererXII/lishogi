import { promises as fs } from 'node:fs';

export async function writeIfChanged(filePath: string, data: string): Promise<boolean> {
  try {
    const current = await fs.readFile(filePath, 'utf8');
    if (current === data) return false;
  } catch (err: any) {
    if (err.code !== 'ENOENT') throw err;
  }

  await fs.writeFile(filePath, data, 'utf8');
  return true;
}

// todo - build prod somewhere else, don't overwrite dev
export function getOutputDirectory(): string {
  const isProd = process.argv.includes('--prod');
  if (isProd) return 'public';
  else return 'public';
}
