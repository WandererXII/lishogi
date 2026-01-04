import { makeChat } from 'chat';
import { wsConnect, wsOnOpen, wsSend } from 'common/ws';

function main(data: any) {
  wsConnect('/chatroom/socket', data.socketVersion, {
    options: { reloadOnResume: true },
  });
  data.chat.withColorTags = true;
  data.chat && makeChat(data.chat);

  wsOnOpen(() => {
    setTimeout(() => {
      wsSend('version_check');
    }, 2000);
  });
}

window.lishogi.registerModule(__bundlename__, main);
