import { type MaybeVNodes, bind } from 'common/snabbdom';
import { type VNode, h } from 'snabbdom';
import type TournamentController from './ctrl';

export function searchOr(ctrl: TournamentController, nodes: MaybeVNodes): MaybeVNodes {
  return [button(ctrl), ...(ctrl.searching ? [input(ctrl)] : nodes)];
}

function button(ctrl: TournamentController): VNode {
  return h('button.fbt', {
    class: { active: ctrl.searching },
    attrs: {
      'data-icon': ctrl.searching ? 'L' : 'y',
      title: 'Search tournament players',
    },
    hook: bind('mousedown', ctrl.toggleSearch, ctrl.redraw),
  });
}

function input(ctrl: TournamentController): VNode {
  return h(
    'div.search',
    h('input', {
      hook: {
        insert(vnode) {
          requestAnimationFrame(() => {
            const el = vnode.elm as HTMLInputElement;
            window.lishogi.userAutocomplete($(el), {
              tag: 'span',
              tour: ctrl.data.id,
              focus: true,
              minLength: 3,
              onSelect(v: any) {
                ctrl.jumpToPageOf(v.id || v);
                $(el).typeahead('close');
                el.value = '';
                ctrl.redraw();
              },
            });
          });
        },
      },
    }),
  );
}
