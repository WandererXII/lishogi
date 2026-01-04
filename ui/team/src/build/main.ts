import { makeChat } from 'chat';
import { wsConnect } from 'common/ws';
import type { TeamData } from '../interface';

function main(data: TeamData) {
  wsConnect(`/team/${data.id}`, data.socketVersion, {
    options: { reloadOnResume: true },
  });
  data.chat && makeChat(data.chat);
  $('#team-subscribe').on('change', function (this: HTMLInputElement) {
    $(this)
      .parents('form')
      .each(function (this: HTMLFormElement) {
        window.lishogi.xhr.formToXhr(this);
      });
  });
}

window.lishogi.registerModule(__bundlename__, main);
