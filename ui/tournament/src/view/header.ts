import { assetUrl } from 'common/assets';
import { type MaybeVNode, dataIcon } from 'common/snabbdom';
import { i18n } from 'i18n';
import { type VNode, h } from 'snabbdom';
import type TournamentController from '../ctrl';

function startClock(time: number) {
  return {
    insert: (vnode: VNode) => $(vnode.elm as HTMLElement).clock({ time: time }),
  };
}

const oneDayInSeconds = 60 * 60 * 24;

function hasFreq(freq: string, d: any) {
  return d.schedule && d.schedule.freq === freq;
}

function clock(d: any): MaybeVNode {
  if (d.isFinished) return;
  if (d.secondsToFinish) {
    if (d.secondsToFinish > oneDayInSeconds)
      return h('div.clock.clock-title', [
        h('span.shy', `${i18n('ending')} `),
        new Date(Date.now() + d.secondsToFinish * 1000).toLocaleString(),
      ]);
    else
      return h(
        'div.clock.clock-title',
        {
          hook: startClock(d.secondsToFinish),
        },
        [h('span.shy', i18n('ending')), h('div.time')],
      );
  }
  if (d.secondsToStart) {
    if (d.secondsToStart > oneDayInSeconds)
      return h(
        'div.clock',
        h('time.timeago.shy', {
          attrs: {
            title: new Date(d.startsAt).toLocaleString(),
            datetime: Date.now() + d.secondsToStart * 1000,
          },
          hook: {
            insert(vnode) {
              (vnode.elm as HTMLElement).setAttribute(
                'datetime',
                `${Date.now() + d.secondsToStart * 1000}`,
              );
            },
          },
        }),
      );
    else
      return h(
        'div.clock.clock-title',
        {
          hook: startClock(d.secondsToStart),
        },
        [h('span.shy', i18n('starting')), h('span.time.text')],
      );
  } else return;
}

function image(d: any): VNode | undefined {
  if (d.isFinished) return;
  if (hasFreq('shield', d) || hasFreq('marathon', d)) return;
  const s = d.spotlight;
  if (s?.iconImg)
    return h('img.img', {
      attrs: { src: assetUrl(`images/${s.iconImg}`) },
    });
  return h('i.img', {
    attrs: dataIcon(s?.iconFont || 'g'),
  });
}

function title(ctrl: TournamentController) {
  const d = ctrl.data;
  if (hasFreq('marathon', d)) return h('h1', [h('i.fire-trophy', '\\'), d.fullName]);
  if (hasFreq('shield', d))
    return h('h1', [
      h(
        'a.shield-trophy',
        {
          attrs: { href: '/tournament/shields' },
        },
        d.perf.icon,
      ),
      d.fullName,
    ]);
  return h(
    'h1',
    (d.animal
      ? [
          h(
            'a',
            {
              attrs: {
                href: d.animal.url,
                target: '_blank',
              },
            },
            d.animal.name,
          ),
          ' Arena',
        ]
      : [d.fullName]
    ).concat(d.private ? [' ', h('span', { attrs: dataIcon('a') })] : []),
  );
}

export default function (ctrl: TournamentController): VNode {
  return h('div.tour__main__header', [image(ctrl.data), title(ctrl), clock(ctrl.data)]);
}
