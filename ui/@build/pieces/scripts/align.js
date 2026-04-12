import * as fs from 'fs';
import * as path from 'path';
import { JSDOM } from 'jsdom';
import { svgPathBbox } from 'svg-path-bbox';

// --- CONFIGURATION ---
const INPUT_DIR = './assets/standard/ryoko_1kanji';
const OUTPUT_DIR = INPUT_DIR + '/out';
const MARGIN = 7;
// ---------------------

async function processSvgs() {
  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR);
  }

  const files = fs.readdirSync(INPUT_DIR).filter((f) => f.endsWith('.svg'));

  for (const filename of files) {
    const filePath = path.join(INPUT_DIR, filename);
    const svgContent = fs.readFileSync(filePath, 'utf8');

    // Create a virtual DOM to parse the SVG
    const dom = new JSDOM(svgContent, { contentType: 'image/svg+xml' });
    const document = dom.window.document;
    const svgElement = document.querySelector('svg');

    if (!svgElement) continue;

    // Get canvas height (prefer viewBox, fallback to height attribute)
    const viewBox = svgElement.getAttribute('viewBox')?.split(/\s+|,/).map(Number);
    const canvasHeight = viewBox
      ? viewBox[3]
      : parseFloat(svgElement.getAttribute('height') || '0');

    if (!canvasHeight) {
      console.error(`Could not determine height for ${filename}`);
      continue;
    }

    // Calculate the "ink" bounding box by checking all path elements
    let minY = Infinity;
    let maxY = -Infinity;

    const paths = svgElement.querySelectorAll('path');
    paths.forEach((p) => {
      const d = p.getAttribute('d');
      if (d) {
        const [x0, y0, x1, y1] = svgPathBbox(d);
        if (y0 < minY) minY = y0;
        if (y1 > maxY) maxY = y1;
      }
    });

    // If no paths found, skip
    if (minY === Infinity) continue;

    let dy = 0;

    // Logic: 0 = Bottom align, 1 = Top align
    if (filename.startsWith('0')) {
      // Target: Bottom of piece at (canvasHeight - MARGIN)
      const targetBottom = canvasHeight - MARGIN;
      dy = targetBottom - maxY;
    } else if (filename.startsWith('1')) {
      // Target: Top of piece at (0 + MARGIN)
      const targetTop = MARGIN;
      dy = targetTop - minY;
    }

    // Apply translation to a wrapper group to move everything at once
    const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    g.setAttribute('transform', `translate(0, ${dy})`);

    // Move all children of the SVG into the group
    while (svgElement.firstChild) {
      g.appendChild(svgElement.firstChild);
    }
    svgElement.appendChild(g);

    // Save the file
    const outputPath = path.join(OUTPUT_DIR, filename);
    fs.writeFileSync(outputPath, svgElement.outerHTML);

    console.log(`Processed ${filename}: Shifted by ${dy.toFixed(2)}px`);
  }
}

processSvgs().catch(console.error);
