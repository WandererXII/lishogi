import { assetUrl } from './assets';
import * as data from './data';
import { spinnerHtml } from './spinner';

const destroyKey = '__icon_destroy';

export function createIconSelector(
  el: HTMLElement,
  current: string,
  categ: string,
  onSelect: (key: string) => void,
): {
  selected: () => string;
  toggle: () => void;
  close: () => void;
  destroy: () => void;
} {
  document.querySelectorAll('.icon-selector-modal').forEach(i => {
    const destroyer = data.get(i, destroyKey);
    destroyer?.();
  });

  let selected: string = current;

  const modal = document.createElement('div');
  modal.className = 'icon-selector-modal';
  data.set(modal, destroyKey, destroy);

  const handleBackdropClick = (e: MouseEvent) => {
    e.stopPropagation();
    close();
  };
  const overlay = document.createElement('div');
  overlay.className = 'icon-selector-overlay';
  overlay.addEventListener('click', handleBackdropClick);
  modal.appendChild(overlay);

  const handleIconClick = (e: MouseEvent) => {
    e.stopPropagation();
    const target = e.target as HTMLElement;
    const key = target.dataset.key;
    if (key) {
      selected = key;
      onSelect(key);
      close();
    }
  };
  const grid = document.createElement('div');
  grid.className = 'icon-selector-grid';
  grid.addEventListener('click', handleIconClick);
  modal.appendChild(grid);

  const spinner = document.createElement('div');
  spinner.classList = 'spinner';
  spinner.innerHTML = spinnerHtml;
  grid.appendChild(spinner);

  el.appendChild(modal);

  window.lishogi.xhr
    .text('GET', assetUrl(`icons/${categ}.txt`, { sameDomain: true }))
    .then(txt => txt.split('\n').filter(l => !!l))
    .then(list => {
      list.forEach(icon => {
        const iconEl = document.createElement('div');
        iconEl.dataset.key = icon;
        iconEl.className = 'icon-selector-img';

        iconEl.innerHTML = svgSprite(categ, icon);

        spinner.classList.add('none');
        grid.appendChild(iconEl);
      });
    });

  function close() {
    modal.classList.add('none');
  }
  function destroy() {
    overlay.removeEventListener('click', handleBackdropClick);
    grid.removeEventListener('click', handleIconClick);
    modal.remove();
  }

  return {
    selected: () => selected,
    toggle: () => {
      modal.classList.toggle('none');
    },
    close,
    destroy,
  };
}

export function svgSprite(categ: string, key: string): string {
  return `<svg class="extra-icon"><use href="${assetUrl(`icons/${categ}.svg`, { sameDomain: true })}#${key}"></use></svg>`;
}
