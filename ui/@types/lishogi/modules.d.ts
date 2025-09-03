export interface LishogiModules {
  challenge?: (opts: any) => { update: (d: any) => any };
  dasher?: (opts: { playing: boolean }) => Promise<any>;
  editor?: (opts: any) => any;
  keyboardMove?: (opts: any) => any;
  miscCli?: (opts: { $wrap: JQuery; toggle: () => void }) => any;
  miscExpandText?: () => void;
  miscConfetti: (canvas: HTMLCanvasElement) => void;
  notify?: (opts: any) => any;
  palantir: (opts: any) => undefined | { render: (h: any) => any };
  speech?: (opts: LishogiSpeech) => any;
  chartAcpl?: (
    el: HTMLCanvasElement,
    data: any,
    mainline: Tree.Node[],
    ply: number,
  ) => {
    updateData: (data: any, mainline: Tree.Node[]) => void;
    selectPly: (ply: number, isMainline: boolean) => void;
  };
  chartMovetime?: (
    el: HTMLCanvasElement,
    data: any,
    ply: number,
  ) => {
    selectPly: (ply: number, isMainline: boolean) => void;
  };
  analyseNvui?: LishogiNvui;
  roundNvui?: LishogiNvui;
}
