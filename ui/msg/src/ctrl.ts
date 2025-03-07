import notify from 'common/notification';
import throttle from 'common/throttle';
import type {
  Contact,
  Convo,
  LastMsg,
  Msg,
  MsgData,
  Pane,
  Search,
  SearchResult,
  Typing,
} from './interfaces';
import * as network from './network';
import { scroller } from './view/scroller';

export default class MsgCtrl {
  data: MsgData;
  search: Search = {
    input: '',
  };
  pane: Pane;
  loading = false;
  connected = () => true;
  msgsPerPage = 100;
  canGetMoreSince?: Date;
  typing?: Typing;
  textStore?: LishogiStorage;

  constructor(
    data: MsgData,
    readonly redraw: Redraw,
  ) {
    this.data = data;
    this.pane = data.convo ? 'convo' : 'side';
    this.connected = network.websocketHandler(this);
    if (this.data.convo) this.onLoadConvo(this.data.convo);
    window.addEventListener('focus', this.setRead);
    window.lishogi.pubsub.on('embed_loaded', () => scroller.auto());
  }

  openConvo = (userId: string): void => {
    if (this.data.convo?.user.id != userId) {
      this.data.convo = undefined;
      this.loading = true;
    }
    network.loadConvo(userId).then(data => {
      this.data = data;
      this.search.result = undefined;
      this.loading = false;
      if (data.convo) {
        history.replaceState({ contact: userId }, '', `/inbox/${data.convo.user.name}`);
        this.onLoadConvo(data.convo);
        this.redraw();
      } else this.showSide();
    });
    this.pane = 'convo';
    this.redraw();
  };

  showSide = (): void => {
    this.pane = 'side';
    this.redraw();
  };

  getMore = (): void => {
    if (this.data.convo && this.canGetMoreSince)
      network.getMore(this.data.convo.user.id, this.canGetMoreSince).then(data => {
        if (
          !this.data.convo ||
          !data.convo ||
          data.convo.user.id != this.data.convo.user.id ||
          !data.convo.msgs[0]
        )
          return;
        if (data.convo.msgs[0].date >= this.data.convo.msgs[this.data.convo.msgs.length - 1].date)
          return;
        this.data.convo.msgs = this.data.convo.msgs.concat(data.convo.msgs);
        this.onLoadMsgs(data.convo.msgs);
        this.redraw();
      });
    this.canGetMoreSince = undefined;
    this.redraw();
  };

  private onLoadConvo = (convo: Convo) => {
    this.textStore = window.lishogi.storage.make(`msg:area:${convo.user.id}`);
    this.onLoadMsgs(convo.msgs);
    if (this.typing) {
      clearTimeout(this.typing.timeout);
      this.typing = undefined;
    }
    setTimeout(this.setRead, 500);
  };
  private onLoadMsgs = (msgs: Msg[]) => {
    const oldFirstMsg = msgs[this.msgsPerPage - 1];
    this.canGetMoreSince = oldFirstMsg?.date;
  };

  post = (text: string): void => {
    if (this.data.convo) {
      network.post(this.data.convo.user.id, text);
      const msg: LastMsg = {
        text,
        user: this.data.me.id,
        date: new Date(),
        read: true,
      };
      this.data.convo.msgs.unshift(msg);
      const contact = this.currentContact();
      if (contact) this.addMsg(msg, contact);
      else
        setTimeout(
          () =>
            network.loadContacts().then(data => {
              this.data.contacts = data.contacts;
              this.redraw();
            }),
          1000,
        );
      scroller.enable(true);
      this.redraw();
    }
  };

  receive = (msg: LastMsg): void => {
    const contact = this.findContact(msg.user);
    this.addMsg(msg, contact);
    if (contact) {
      let redrawn = false;
      if (msg.user == this.data.convo?.user.id) {
        this.data.convo.msgs.unshift(msg);
        if (document.hasFocus()) redrawn = this.setRead();
        else this.notify(contact, msg);
        this.receiveTyping(msg.user, true);
      }
      if (!redrawn) this.redraw();
    } else
      network.loadContacts().then(data => {
        this.data.contacts = data.contacts;
        this.notify(this.findContact(msg.user)!, msg);
        this.redraw();
      });
  };

  private addMsg = (msg: LastMsg, contact?: Contact) => {
    if (contact) {
      contact.lastMsg = msg;
      this.data.contacts = [contact].concat(
        this.data.contacts.filter(c => c.user.id != contact.user.id),
      );
    }
  };

  private findContact = (userId: string): Contact | undefined =>
    this.data.contacts.find(c => c.user.id == userId);

  private currentContact = (): Contact | undefined =>
    this.data.convo && this.findContact(this.data.convo.user.id);

  private notify = (contact: Contact, msg: Msg) => {
    notify(() => `${contact.user.name}: ${msg.text}`);
  };

  searchInput = (q: string): void => {
    this.search.input = q;
    if (q[1])
      network.search(q).then((res: SearchResult) => {
        this.search.result = this.search.input[1] ? res : undefined;
        this.redraw();
      });
    else {
      this.search.result = undefined;
      this.redraw();
    }
  };

  setRead = (): boolean => {
    const msg = this.currentContact()?.lastMsg;
    if (msg && msg.user != this.data.me.id) {
      window.lishogi.notifyApp?.setMsgRead(msg.user);
      if (msg.read) return false;
      msg.read = true;
      network.setRead(msg.user);
      this.redraw();
      return true;
    }
    return false;
  };

  delete = (): void => {
    const userId = this.data.convo?.user.id;
    if (userId)
      network.del(userId).then(data => {
        this.data = data;
        this.redraw();
        history.replaceState({}, '', '/inbox');
      });
  };

  report = (): void => {
    const user = this.data.convo?.user;
    if (user) {
      const text = this.data.convo?.msgs.find(m => m.user != this.data.me.id)?.text.slice(0, 140);
      if (text) network.report(user.name, text).then(_ => alert('Your report has been sent.'));
    }
  };

  block = (): void => {
    const userId = this.data.convo?.user.id;
    if (userId) network.block(userId).then(() => this.openConvo(userId));
  };

  unblock = (): void => {
    const userId = this.data.convo?.user.id;
    if (userId) network.unblock(userId).then(() => this.openConvo(userId));
  };

  changeBlockBy = (userId: string): void => {
    if (userId == this.data.convo?.user.id) this.openConvo(userId);
  };

  sendTyping: (...args: any[]) => void = throttle(3000, (user: string) => {
    if (this.textStore?.get()) network.typing(user);
  });

  receiveTyping = (userId: string, cancel?: boolean): void => {
    if (this.typing) {
      clearTimeout(this.typing.timeout);
      this.typing = undefined;
    }
    if (!cancel && this.data.convo?.user.id == userId) {
      this.typing = {
        user: userId,
        timeout: setTimeout(() => {
          if (this.data.convo?.user.id == userId) this.typing = undefined;
          this.redraw();
        }, 3000),
      };
    }
    this.redraw();
  };

  onReconnect = (): void => {
    this.data.convo && this.openConvo(this.data.convo.user.id);
    this.redraw();
  };
}
