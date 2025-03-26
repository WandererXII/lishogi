import { type MaybeVNodes, bind } from 'common/snabbdom';
import { type VNode, h } from 'snabbdom';
import type TournamentController from './ctrl';
import type { PageData } from './interfaces';
import { searchOr } from './search';

export const maxPerPage = 10;

function button(
  text: string,
  icon: string,
  click: () => void,
  enable: boolean,
  ctrl: TournamentController,
): VNode {
  return h('button.fbt.is', {
    attrs: {
      'data-icon': icon,
      disabled: !enable,
      title: text,
    },
    hook: bind('mousedown', click, ctrl.redraw),
  });
}

function scrollToMeButton(ctrl: TournamentController): VNode | undefined {
  if (ctrl.data.me)
    return h(`button.fbt${ctrl.focusOnMe ? '.active' : ''}`, {
      attrs: {
        'data-icon': '7',
        title: 'Scroll to your player',
      },
      hook: bind('mousedown', ctrl.toggleFocusOnMe, ctrl.redraw),
    });
  else return;
}

export function renderPager(ctrl: TournamentController, pag: PageData): MaybeVNodes {
  const enabled = !!pag.currentPageResults;
  const page = ctrl.page;
  return pag.nbPages > -1
    ? searchOr(ctrl, [
        button('First', 'W', () => ctrl.userSetPage(1), enabled && page > 1, ctrl),
        button('Prev', 'Y', ctrl.userPrevPage, enabled && page > 1, ctrl),
        h('span.page', `${pag.nbResults ? pag.from + 1 : 0}-${pag.to} / ${pag.nbResults}`),
        button('Next', 'X', ctrl.userNextPage, enabled && page < pag.nbPages, ctrl),
        button('Last', 'V', ctrl.userLastPage, enabled && page < pag.nbPages, ctrl),
        scrollToMeButton(ctrl),
      ])
    : [];
}

export function players(ctrl: TournamentController): PageData {
  const page = ctrl.page;
  const nbResults = ctrl.data.nbPlayers;
  const from = (page - 1) * maxPerPage;
  const to = Math.min(nbResults, page * maxPerPage);
  return {
    currentPage: page,
    maxPerPage,
    from,
    to,
    currentPageResults: ctrl.pages[page],
    nbResults,
    nbPages: Math.ceil(nbResults / maxPerPage),
  };
}

export function myPage(ctrl: TournamentController): number | undefined {
  if (ctrl.data.me) return Math.floor((ctrl.data.me.rank - 1) / maxPerPage) + 1;
  else return;
}
