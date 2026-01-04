declare global {
  namespace Socket {
    type Sri = string;
    type Tpe = string;
    type Payload = any;
    type Version = number;
    interface MsgBase {
      t: Tpe;
      d?: Payload;
    }
    interface MsgIn extends MsgBase {
      v?: Version;
    }
    interface MsgOut extends MsgBase {}
    interface MsgAck extends MsgOut {
      at: number;
    }
    type Send = (t: Tpe, d?: Payload, o?: any, noRetry?: boolean) => void;

    interface Options {
      idle: boolean;
      pongTimeout: number; // time to wait for pong before reseting the connection
      pingDelay: number; // time between pong and ping
      autoReconnectDelay: number;
      protocol: string;
      isAuth: boolean;
      debug?: boolean;
      reloadOnResume?: boolean;
    }

    interface Params extends Record<string, any> {
      sri?: Sri;
      flag?: string;
    }

    interface Settings {
      receive?: (t: Tpe, d: Payload) => void;
      events: {
        [tpe: string]: (d: Payload | null, msg: MsgIn) => any;
      };
      params?: Params;
      options?: Partial<Options>;
    }
  }
}

export {};
