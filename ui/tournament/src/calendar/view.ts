import addDays from 'date-fns/addDays';
import areIntervalsOverlapping from 'date-fns/areIntervalsOverlapping';
import eachDayOfInterval from 'date-fns/eachDayOfInterval';
import format from 'date-fns/format';
import getHours from 'date-fns/getHours';
import getMinutes from 'date-fns/getMinutes';
import { type VNode, h } from 'snabbdom';
import type { Ctrl, Lanes, Tournament } from './interfaces';

function tournamentClass(tour: Tournament, day: Date) {
  const classes: Record<string, boolean> = {
    rated: tour.rated,
    casual: !tour.rated,
    'max-rating': tour.hasMaxRating,
    yesterday: tour.bounds.start < day,
  };
  if (tour.schedule) classes[tour.schedule.freq] = true;
  return classes;
}

function iconOf(tour: any, perfIcon: string): string {
  return tour.schedule && tour.schedule.freq === 'shield' ? '5' : perfIcon;
}

function renderTournament(tour: Tournament, day: Date) {
  let left = ((getHours(tour.bounds.start) + getMinutes(tour.bounds.start) / 60) / 24) * 100;
  if (tour.bounds.start < day) left -= 100;
  const width = (tour.minutes / 60 / 24) * 100;

  return h(
    'a.tournament',
    {
      class: tournamentClass(tour, day),
      attrs: {
        href: `/tournament/${tour.id}`,
        style: `width: ${width}%; left: ${left}%`,
        title: `${tour.fullName} - ${format(tour.bounds.start, 'EEEE, dd/MM/yyyy HH:mm')}`,
      },
    },
    [
      h(
        'span.icon',
        tour.perf
          ? {
              attrs: {
                'data-icon': iconOf(tour, tour.perf.icon),
              },
            }
          : {},
      ),
      h('span.body', [tour.fullName]),
    ],
  );
}

function renderLane(tours: Tournament[], day: Date) {
  return h(
    'lane',
    tours.map(t => renderTournament(t, day)),
  );
}

function fitLane(lane: Tournament[], tour2: Tournament) {
  return !lane.some(tour1 => {
    return areIntervalsOverlapping(tour1.bounds, tour2.bounds);
  });
}

function makeLanes(tours: Tournament[]): Lanes {
  const lanes: Lanes = [];
  tours.forEach(t => {
    const lane = lanes.find(l => fitLane(l, t));
    if (lane) lane.push(t);
    else lanes.push([t]);
  });
  return lanes;
}

function renderDay(ctrl: Ctrl) {
  return (day: Date): VNode => {
    const dayEnd = addDays(day, 1);
    const tours = ctrl.data.tournaments.filter(t => t.bounds.start < dayEnd && t.bounds.end > day);
    return h('day', [
      h(
        'date',
        {
          attrs: {
            title: format(day, 'EEEE, dd/MM/yyyy'),
          },
        },
        [format(day, 'dd/MM')],
      ),
      h(
        'lanes',
        makeLanes(tours).map(l => renderLane(l, day)),
      ),
    ]);
  };
}

function renderGroup(ctrl: Ctrl) {
  return (group: Date[]): VNode =>
    h('group', [renderTimeline(), h('days', group.map(renderDay(ctrl)))]);
}

function renderTimeline() {
  const hours: number[] = [];
  for (let i = 0; i < 24; i++) hours.push(i);
  return h(
    'div.timeline',
    hours.map(hour =>
      h(
        'div.timeheader',
        {
          attrs: { style: `left: ${(hour / 24) * 100}%` },
        },
        timeString(hour),
      ),
    ),
  );
}

// converts Date to "%H:%M" with leading zeros
function timeString(hour: number) {
  return `0${hour}`.slice(-2);
}

function makeGroups(days: Date[]): Date[][] {
  const groups: Date[][] = [];
  let i: number;
  let j: number;
  const chunk = 10;
  for (i = 0, j = days.length; i < j; i += chunk) groups.push(days.slice(i, i + chunk));
  return groups;
}

export default function (ctrl: any): VNode {
  const days = eachDayOfInterval({
    start: new Date(ctrl.data.since),
    end: new Date(ctrl.data.to),
  });
  const groups = makeGroups(days);
  return h('div#tournament-calendar', h('groups', groups.map(renderGroup(ctrl))));
}
