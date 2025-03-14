function buildStorage(storage: Storage): LishogiStorageHelper {
  const api: LishogiStorageHelper = {
    get: k => storage.getItem(k),
    set: (k, v) => storage.setItem(k, v),
    fire: (k, v) =>
      storage.setItem(
        k,
        JSON.stringify({
          sri: window.lishogi.sri,
          nonce: Math.random(), // ensure item changes
          value: v,
        }),
      ),
    remove: k => storage.removeItem(k),
    make: k => ({
      get: () => api.get(k),
      set: v => api.set(k, v),
      fire: v => api.fire(k, v),
      remove: () => api.remove(k),
      listen: f =>
        window.addEventListener('storage', e => {
          if (e.key !== k || e.storageArea !== storage || e.newValue === null) return;
          let parsed: any;
          try {
            parsed = JSON.parse(e.newValue);
          } catch (_) {
            return;
          }
          // check sri, because Safari fires events also in the original
          // document when there are multiple tabs
          if (parsed.sri && parsed.sri !== window.lishogi.sri) f(parsed);
        }),
    }),
    makeBoolean: k => ({
      get: () => api.get(k) == '1',
      set: (v: boolean): void => api.set(k, v ? '1' : '0'),
      toggle: () => api.set(k, api.get(k) == '1' ? '0' : '1'),
    }),
  };
  return api;
}

export const storage: LishogiStorageHelper = buildStorage(window.localStorage);
export const tempStorage: LishogiStorageHelper = buildStorage(window.sessionStorage);
