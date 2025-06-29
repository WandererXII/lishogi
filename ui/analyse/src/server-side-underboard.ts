import { loadLishogiScript } from 'common/assets';
import { spinnerHtml } from 'common/spinner';
import { escapeHtml } from 'common/string';
import { i18n } from 'i18n';
import { engineName } from 'shogi/engine-name';
import type AnalyseCtrl from './ctrl';
import type { AnalyseData } from './interfaces';
import { baseUrl } from './util';

export default function (element: HTMLElement, ctrl: AnalyseCtrl): void {
  const li = window.lishogi;

  $(element).replaceWith(ctrl.opts.$underboard!);
  applyNotationPreferences();

  const data = ctrl.data;
  const $panels = $('.analyse__underboard__panels > div');
  const $menu = $('.analyse__underboard__menu');

  const inputSfen = document.querySelector('.analyse__underboard__sfen') as HTMLInputElement;
  inputSfen.value = ctrl.node.sfen;

  let lastInputHash: string = ctrl.node.sfen;
  let advChart: any;
  let timeChartLoaded = false;

  if (!li.modules.analyseNvui) {
    li.pubsub.on('analysis.comp.toggle', (v: boolean) => {
      setTimeout(() => {
        (v ? $menu.find('[data-panel="computer-analysis"]') : $menu.find('span:eq(1)')).trigger(
          'mousedown',
        );
      }, 50);
    });
    li.pubsub.on('analysis.change', (sfen: Sfen, _) => {
      const nextInputHash = sfen;
      if (sfen && nextInputHash !== lastInputHash) {
        inputSfen.value = sfen;
        lastInputHash = nextInputHash;
      }
    });
    li.pubsub.on('analysis.server.progress', (d: AnalyseData) => {
      if (!advChart) startAdvantageChart();
      else advChart.updateData(d, ctrl.mainline);
      if (d.analysis && !d.analysis.partial) $('#acpl-chart-loader').remove();
    });
  }

  function chartLoader() {
    const name = engineName(ctrl.data.game.variant.key, ctrl.data.game.initialSfen);
    return `<div id="acpl-chart-container-loader"><span>${name}<br>${i18n('serverAnalysis')}</span>${spinnerHtml}</div>`;
  }
  function startAdvantageChart() {
    if (advChart || li.modules.analyseNvui) return;
    const loading = !data.treeParts[0].eval || !Object.keys(data.treeParts[0].eval).length;
    const $panel = $panels.filter('.computer-analysis');
    if (!$('#acpl-chart-container').length)
      $panel.html(
        `<div id="acpl-chart-container"><canvas id="acpl-chart"></canvas></div>${loading ? chartLoader() : ''}`,
      );
    else if (loading && !$('#acpl-chart-container-loader').length) $panel.append(chartLoader());
    loadLishogiScript('chart').then(() => {
      loadLishogiScript('chart.acpl').then(() => {
        li.modules.chartAcpl!(
          $('#acpl-chart')[0] as HTMLCanvasElement,
          data,
          ctrl.mainline,
          ctrl.node.ply,
        );
      });
    });
  }

  const storage = li.storage.make('analysis.panel');
  const setPanel = (panel: string) => {
    $menu.children('.active').removeClass('active');
    $menu.find(`[data-panel="${panel}"]`).addClass('active');
    $panels.removeClass('active').filter(`.${panel}`).addClass('active');
    if (panel == 'move-times' && !timeChartLoaded)
      loadLishogiScript('chart').then(() => {
        loadLishogiScript('chart.movetime').then(() => {
          timeChartLoaded = true;
          li.modules.chartMovetime!(
            $('#movetimes-chart')[0] as HTMLCanvasElement,
            data,
            ctrl.node.ply,
          );
        });
      });
    if (panel == 'computer-analysis' && $('#acpl-chart-container').length)
      setTimeout(startAdvantageChart, 200);
  };
  $menu.on('mousedown', 'span', function (this: HTMLElement) {
    const panel = $(this).data('panel');
    storage.set(panel);
    setPanel(panel);
  });
  const stored = storage.get();
  const foundStored =
    stored &&
    $menu.children(`[data-panel="${stored}"]`).filter(function (this: HTMLElement) {
      const display = window.getComputedStyle(this).display;
      return !!display && display != 'none';
    }).length;
  if (foundStored) setPanel(stored);
  else {
    const $menuCt = $menu.children('[data-panel="game-export"]');
    ($menuCt.length ? $menuCt : $menu.children(':first-child')).trigger('mousedown');
  }
  if (!data.analysis) {
    $panels.find('form.future-game-analysis').on('submit', function (this: HTMLFormElement) {
      if ($(this).hasClass('must-login')) {
        if (confirm(i18n('youNeedAnAccountToDoThat'))) location.href = '/signup';
        return false;
      }
      window.lishogi.xhr.formToXhr(this).then(startAdvantageChart).catch(li.reload);
      return false;
    });
  }

  $panels.on('click', '.embed-howto', function (this: HTMLElement) {
    const url = `${baseUrl()}/embed/${data.game.id}${location.hash}`;
    const iframe = `<iframe src="${url}?theme=auto&bg=auto"\nwidth=600 height=371 frameborder=0></iframe>`;
    $.modal(
      $(
        `<strong style="font-size:1.5em">${$(this).html()}</strong><br /><br /><pre>${escapeHtml(iframe)}</pre><br />${iframe}<br /><br /><a class="text" data-icon="" href="/developers#embed-game">${i18n('study:readMoreAboutEmbedding')}</a>`,
      ),
    );
  });

  function updateNotationLinks(params: Record<string, boolean>): void {
    const links = document.querySelectorAll<HTMLAnchorElement>('.game-notation a');

    links.forEach(link => {
      const url = new URL(link.href);
      for (const [param, enabled] of Object.entries(params)) {
        url.searchParams.set(param, enabled ? '1' : '0');
      }
      link.href = url.toString();
    });
  }

  function applyNotationPreferences(): void {
    const checkboxes = document.querySelectorAll<HTMLInputElement>(
      'form.notation-options input[type="checkbox"]',
    );
    const params: Record<string, boolean> = {};

    checkboxes.forEach(checkbox => {
      const param = checkbox.value;
      const stored = localStorage.getItem(`game-export.notation-${param}`);
      const enabled = stored !== null ? stored === 'true' : param === 'clocks';

      checkbox.checked = enabled;
      params[param] = enabled;
    });

    updateNotationLinks(params);
  }

  document.querySelector('form.notation-options')?.addEventListener('change', e => {
    const target = e.target as HTMLInputElement;
    if (target.tagName === 'INPUT' && target.type === 'checkbox') {
      const param = target.value;
      const enabled = target.checked;

      window.lishogi.storage.set(`game-export.notation-${param}`, enabled.toString());
      updateNotationLinks({ [param]: enabled });
    }
  });
}
