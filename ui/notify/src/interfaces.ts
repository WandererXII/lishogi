import type { VNode } from 'snabbdom';

export interface NotifyOpts {
  data?: NotifyData;
  incoming: boolean;
  isVisible(): boolean;
  setCount(nb: number): void;
  show(): void;
  setNotified(): void;
  pulse(): void;
}

export interface NotifyData {
  pager: Paginator<Notification>;
  unread: number;
}

export interface Notification {
  content: any;
  type: string;
  read: boolean;
  date: number;
}

export interface Ctrl {
  redraw: Redraw;
  update(data: NotifyData, incoming: boolean): void;
  data(): NotifyData | undefined;
  initiating(): boolean;
  scrolling(): boolean;
  nextPage(): void;
  previousPage(): void;
  loadPage(page: number): void;
  setVisible(): void;
  setMsgRead(user: string): void;
  clear(): void;
}

export interface Renderers {
  [key: string]: Renderer;
}

interface Renderer {
  html(n: Notification): VNode;
  text(n: Notification): string;
}
