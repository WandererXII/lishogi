import { isOnline } from 'common/common';
import { browserTaskQueueMonitor, idleTimer } from 'common/timings';
import { i18n } from 'i18n';
import { reload } from './navigation';
import { pubsub } from './pubsub';
import { storage as makeStorage } from './storage';
import { text, urlWithParams } from './xhr';

export class StrongSocket implements IStrongSocket {
  private ws: WebSocket | undefined;

  private settings: Socket.Settings;
  private options: Socket.Options;

  private version: number | false;
  private lastVersionTime: number = performance.now();

  private pingSchedule: Timeout;
  private connectSchedule: Timeout;
  private lastPingTime: number = performance.now();
  private averageLag = 0;
  private pongCount = 0;
  private ready = false;
  private wasInitiated = false;

  private ackable: Ackable = new Ackable((t, d, o) => this.send(t, d, o));

  private storage: LishogiStorage = makeStorage.make('surl8');
  private heartbeat = browserTaskQueueMonitor(1000);
  private resendWhenOpen: [string, any, any][] = [];

  private baseUrls = document.body.dataset.socketDomains!.split(',');
  private tryOtherUrl = false;

  private static resolveInitiated: () => void;
  static initiated: Promise<void> = new Promise<void>(r => {
    StrongSocket.resolveInitiated = r;
  });

  constructor(
    readonly url: string,
    version: number | false,
    settings: Partial<Socket.Settings> = {},
  ) {
    this.settings = {
      receive: settings.receive,
      events: settings.events || {},
      params: {
        sri: window.lishogi.sri,
        ...(settings.params || {}),
      },
    };
    this.options = {
      idle: false,
      pongTimeout: 10000, // time to wait for pong before reseting the connection
      pingDelay: 2500, // time between pong and ping
      autoReconnectDelay: 3500,
      protocol: location.protocol === 'https:' ? 'wss:' : 'ws:',
      isAuth: document.body.hasAttribute('data-user'),
      ...(settings.options || {}),
    };
    this.version = version;
    pubsub.on('socket.send', this.send);
    this.connect();
  }

  connect = (): void => {
    this.destroy();

    const fullUrl = urlWithParams(`${this.options.protocol}//${this.baseUrl()}${this.url}`, {
      ...this.settings.params,
      v: this.version === false ? undefined : this.version.toString(),
    });

    this.debug(`connection attempt to ${fullUrl}`);
    try {
      this.ws = new WebSocket(fullUrl);
      const ws = this.ws;
      ws.onerror = this.onError;
      ws.onclose = this.onClose;
      ws.onopen = this.onOpen;
      ws.onmessage = e => {
        if (e.data == 0) return this.pong();
        const m = JSON.parse(e.data);
        if (m.t === 'n') this.pong();
        this.handle(m);
      };
    } catch (e) {
      this.onError(e);
    }
    this.scheduleConnect(this.options.pongTimeout);
  };

  send = (t: string, d: any, o: any = {}, noRetry = false): void => {
    const msg: Partial<Socket.MsgOut> = { t };
    if (d !== undefined) {
      if (o.withLag) d.l = Math.round(this.averageLag);
      if (o.millis >= 0) d.s = Math.round(o.millis * 0.1).toString(36);
      msg.d = d;
    }
    if (o.ackable) {
      msg.d = msg.d || {}; // can't ack message without data
      this.ackable.register(t, msg.d); // adds d.a, the ack ID we expect to get back
    }

    const message = JSON.stringify(msg);

    this.debug(`send ${message}`);
    if (!this.ws || this.ws.readyState === WebSocket.CONNECTING) {
      if (!noRetry) this.resendWhenOpen.push([t, msg.d, o]);
    } else this.ws.send(message);
  };

  private scheduleConnect = (delay: number): void => {
    if (this.options.idle) delay = 10 * 1000 + Math.random() * 10 * 1000;

    clearTimeout(this.pingSchedule);
    clearTimeout(this.connectSchedule);

    this.connectSchedule = setTimeout(() => {
      updateNetworkStatusElement('offline');
      this.tryOtherUrl = true;
      this.connect();
    }, delay);
  };

  private schedulePing = (delay: number): void => {
    clearTimeout(this.pingSchedule);
    this.pingSchedule = setTimeout(this.pingNow, delay);
  };

  private pingNow = (): void => {
    clearTimeout(this.pingSchedule);
    clearTimeout(this.connectSchedule);

    const pingData =
      this.options.isAuth && this.pongCount % 10 === 2
        ? JSON.stringify({
            t: 'p',
            l: Math.round(0.1 * this.averageLag),
          })
        : 'null';
    try {
      this.ws!.send(pingData);
      this.lastPingTime = performance.now();
    } catch (e) {
      this.debug(e, true);
    }

    this.scheduleConnect(this.options.pongTimeout);
  };

  private computePingDelay = (): number => this.options.pingDelay + (this.options.idle ? 1000 : 0);

  private pong = (): void => {
    clearTimeout(this.connectSchedule);

    this.schedulePing(this.computePingDelay());
    const currentLag = Math.min(performance.now() - this.lastPingTime, 10000);
    this.pongCount++;

    // Average first 4 pings, then switch to decaying average.
    const mix = this.pongCount > 4 ? 0.1 : 1 / this.pongCount;
    this.averageLag += mix * (currentLag - this.averageLag);

    pubsub.emit('socket.lag', this.averageLag);
  };

  private handle = (m: Socket.MsgIn): any => {
    if (m.v && this.version !== false) {
      if (m.v <= this.version) {
        this.debug(`already has event ${m.v}`);
        return;
      }
      // shouldn't happen, but it happens nonetheless
      if (m.v > this.version + 1) return reload();
      this.version = m.v;
      this.lastVersionTime = performance.now();
    }
    switch (m.t || false) {
      case false:
        break;
      case 'resync':
        reload();
        break;
      case 'ack':
        this.ackable.onServerAck(m.d);
        break;
      case 'versionCheck':
        this.lastVersionTime = performance.now();
        if (this.version !== false && m.d > this.version) {
          text('POST', '/jsmon/socketVersion', {
            url: { v: `${window.location.pathname}:${m.d}:${this.version}` },
          });
          this.debug('socket version mismatch');
          reload();
        }
        break;
      default:
        // return true in a receive handler to prevent pubsub and events
        if (!this.settings.receive?.(m.t, m.d)) {
          pubsub.emit(`socket.in.${m.t}`, m.d, m);
          if (this.settings?.events?.[m.t]) this.settings.events[m.t](m.d || null, m);
        }
    }
  };

  private debug = (msg: string, always = false): void => {
    if (always || this.options.debug) console.debug(msg);
  };

  destroy = (): void => {
    this.debug('Destroy');

    clearTimeout(this.pingSchedule);
    clearTimeout(this.connectSchedule);

    this.disconnect();
    this.ws = undefined;
  };

  disconnect = (): void => {
    const ws = this.ws;
    if (ws) {
      this.debug('Disconnect');
      ws.onerror = ws.onclose = ws.onopen = ws.onmessage = () => {};
      ws.close();
    }
  };

  private onError = (e: Event): void => {
    this.ready = false;

    if (this.heartbeat.wasSuspended) return;
    this.options.debug = true;
    this.debug(`error: ${JSON.stringify(e)}`);
  };

  private onClose = (e: CloseEvent): void => {
    this.ready = false;

    this.debug('WS closed');

    pubsub.emit('socket.close');

    if (this.heartbeat.wasSuspended) {
      this.onSuspended();
      return;
    }

    if (this.ws) {
      this.debug(`Will autoreconnect in ${this.options.autoReconnectDelay}`);
      this.scheduleConnect(this.options.autoReconnectDelay);
    }
    if (e.wasClean && e.code < 1002) return;

    if (isOnline()) this.tryOtherUrl = true;
    clearTimeout(this.pingSchedule);
  };

  private onOpen = () => {
    this.debug('WS opened');

    updateNetworkStatusElement(this.wasInitiated ? 'reconnected' : 'online');

    this.pingNow();

    this.resendWhenOpen.forEach(([t, d, o]) => {
      this.send(t, d, o);
    });
    this.resendWhenOpen = [];

    this.ackable.resend();

    pubsub.emit('socket.open');
    this.ready = true;

    if (this.wasInitiated) return;
    this.wasInitiated = true;
    StrongSocket.resolveInitiated();

    let disconnectTimeout: Timeout | undefined;
    idleTimer(
      10 * 60 * 1000,
      () => {
        this.options.idle = true;
        disconnectTimeout = setTimeout(this.destroy, 2 * 60 * 60 * 1000);
      },
      () => {
        this.options.idle = false;
        if (this.ws) clearTimeout(disconnectTimeout);
        else if (this.options.reloadOnResume) location.reload();
      },
    );
  };

  private onSuspended(): void {
    this.heartbeat.reset(); // not a networking error, just get our connection back

    clearTimeout(this.pingSchedule);
    clearTimeout(this.connectSchedule);

    this.connect();
  }

  private baseUrl = (): string => {
    let url = this.storage.get();

    if (!url || !this.baseUrls.includes(url)) {
      url = this.baseUrls[Math.floor(Math.random() * this.baseUrls.length)];
      this.storage.set(url);
    } else if (this.tryOtherUrl && this.baseUrls.length > 1) {
      const i = this.baseUrls.indexOf(url);
      url = this.baseUrls[(i + 1) % this.baseUrls.length];
      this.storage.set(url);
    }

    this.tryOtherUrl = false;
    return url;
  };

  isReady = (): boolean => this.ready;

  getVersion = (): number | false => this.version;
  getLastVersionTime = (): number => this.lastVersionTime;
  getAverageLag = (): number => this.averageLag;
  getPingInterval = (): number => this.computePingDelay() + this.averageLag;
}

class Ackable {
  currentId = 1; // increment with each ackable message sent
  messages: Socket.MsgAck[] = [];

  constructor(readonly send: Socket.Send) {
    setInterval(this.resend, 1200);
  }

  resend = (): void => {
    const resendCutoff = performance.now() - 2500;
    this.messages.forEach(m => {
      if (m.at < resendCutoff) this.send(m.t, m.d);
    });
  };

  register = (t: Socket.Tpe, d: Socket.Payload): void => {
    d.a = this.currentId++;
    this.messages.push({
      t: t,
      d: d,
      at: performance.now(),
    });
  };

  onServerAck = (id: number): void => {
    this.messages = this.messages.filter(m => m.d.a !== id);
  };
}

function updateNetworkStatusElement(status: 'online' | 'reconnected' | 'offline'): void {
  const onlineish = status === 'online' || status === 'reconnected';
  const cls = document.body.classList;
  cls.toggle('online', onlineish);
  cls.toggle('offline', !onlineish);
  if (status === 'reconnected') cls.add('reconnected');

  const el = document.getElementById('reconnecting');
  if (el) {
    const statusText = onlineish
      ? i18n('online')
      : isOnline()
        ? i18n('reconnecting')
        : i18n('offline');
    el.textContent = statusText;
  }
}
