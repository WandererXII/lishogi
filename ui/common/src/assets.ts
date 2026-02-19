interface AssetUrlOpts {
  sameDomain?: boolean;
  noVersion?: boolean;
  version?: string;
}

export const assetUrl = (path: string, opts?: AssetUrlOpts): string => {
  opts = opts || {};
  const baseUrl = opts.sameDomain ? '' : document.body.getAttribute('data-asset-url');
  const version = document.body.getAttribute('data-asset-version');
  return `${baseUrl}/assets${opts.noVersion ? '' : `/_${version}`}/${path}`;
};

export const loadCss = (href: string): Promise<void> => {
  return new Promise(resolve => {
    if (document.head.querySelector(`link[href="${href}"]`)) return resolve();

    const el = document.createElement('link');
    el.rel = 'stylesheet';
    el.href = href;
    el.onload = () => resolve();
    document.head.append(el);
  });
};

export const removeCssPath = (key: string): void => {
  const isDev = !!document.body.dataset.dev;
  const url = assetUrl(`css/${key}.${isDev ? 'dev' : 'min'}.css`);
  document.querySelectorAll<HTMLLinkElement>('link[rel="stylesheet"]').forEach(link => {
    if (link.href.endsWith(url)) link.remove();
  });
};

export const loadCssPath = (key: string): Promise<void> => {
  const isDev = !!document.body.dataset.dev;
  return loadCss(assetUrl(`css/${key}.${isDev ? 'dev' : 'min'}.css`));
};

const loadPieceSprite = (id: string, variant: VariantKey, defaultSet: string): void => {
  if (!document.getElementById(id)) {
    const cps = document.body.dataset[id] || defaultSet;
    const link = document.createElement('link');
    link.id = id;
    link.rel = 'stylesheet';
    link.type = 'text/css';
    link.href = assetUrl(`piece-css/${variant}/${cps}.css`);
    document.head.appendChild(link);
  }
};

const loadChushogiPieceSprite = () =>
  loadPieceSprite(
    'chu-piece-sprite',
    'chushogi',
    document.body.dataset.chuPieceSet || 'ryoko_1kanji',
  );
const loadKyotoshogiPieceSprite = () =>
  loadPieceSprite(
    'kyo-piece-sprite',
    'kyotoshogi',
    document.body.dataset.kyoPieceSet || 'ryoko_1kanji',
  );
const loadDobutsuPieceSprite = () =>
  loadPieceSprite(
    'dobutsu-piece-sprite',
    'dobutsu',
    document.body.dataset.dobutsuPieceSet || 'dobutsu',
  );

export const loadPieceSpriteByVariant: (variant: VariantKey) => void = (variant: VariantKey) => {
  if (variant === 'chushogi') loadChushogiPieceSprite();
  else if (variant === 'kyotoshogi') loadKyotoshogiPieceSprite();
  else if (variant === 'dobutsu') loadDobutsuPieceSprite();
};

export const loadPieceSpriteByVariants: (variants: VariantKey[]) => void = (
  variants: VariantKey[],
) => {
  if (variants.includes('chushogi')) loadChushogiPieceSprite();
  if (variants.includes('kyotoshogi')) loadKyotoshogiPieceSprite();
  if (variants.includes('dobutsu')) loadDobutsuPieceSprite();
};

export const loadScript = (src: string): Promise<void> =>
  new Promise((resolve, reject) => {
    if (document.head.querySelector(`script[scr="${src}"]`)) return resolve();

    const nonce = document.body.getAttribute('data-nonce');
    const el = document.createElement('script');
    if (nonce) el.setAttribute('nonce', nonce);
    el.onload = resolve as () => void;
    el.onerror = reject;
    el.src = src;
    document.head.append(el);
  });

export const lishogiScriptPath = (name: string): string => {
  const isDev = !!document.body.dataset.dev;
  return `js/lishogi.${name}${isDev ? '' : '.min'}.js`;
};

export const loadLishogiScript = (name: string): Promise<void> => {
  const src = assetUrl(lishogiScriptPath(name));
  return loadScript(src);
};

export const loadVendorScript = (
  packageName: string,
  file: string,
  opts?: AssetUrlOpts,
): Promise<void> => {
  const src = assetUrl(`vendors/${packageName}/${file}`, opts);
  return loadScript(src);
};

export const spectrum = (): Promise<void> => {
  loadCss(assetUrl('vendors/spectrum-vanilla/spectrum.min.css'));
  return loadScript(assetUrl('vendors/spectrum-vanilla/spectrum.min.js'));
};

export const flatpickr = (): Promise<void> => {
  return loadLishogiScript('misc.flatpickr');
};

export const flagUrl = (countryCode: string): string => {
  return assetUrl(`images/flags/${countryCode.toUpperCase()}.svg`);
};
