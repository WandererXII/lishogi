import { StatusId } from 'game';

interface Untyped {
  [key: string]: any;
}

export interface StandingPlayer extends Untyped {}

export interface Standing {
  failed?: boolean;
  page: number;
  players: StandingPlayer[];
  arrangements: Arrangement[];
}

export interface TournamentOpts extends Untyped {
  element: HTMLElement;
  socketSend: SocketSend;
}

export interface TournamentData extends Untyped {
  teamBattle?: TeamBattle;
  teamStanding?: RankedTeam[];
  myTeam?: RankedTeam;
  standing: Standing;
}

export interface TeamBattle {
  teams: {
    [id: string]: string;
  };
  joinWith: string[];
  hasMoreThanTenTeams?: boolean;
}

export interface RankedTeam {
  id: string;
  rank: number;
  score: number;
  players: TeamPlayer[];
}

export interface TeamPlayer {
  user: {
    name: string;
  };
  score: number;
}

export type Page = StandingPlayer[];

export interface Pages {
  [n: number]: Page;
}

export interface PlayerInfo {
  id?: string;
  player?: any;
  data?: any;
}
export interface TeamInfo {
  id: string;
  nbPlayers: number;
  rating: number;
  perf: number;
  score: number;
  topPlayers: TeamPlayer[];
}

export interface TeamPlayer {
  name: string;
  rating: number;
  score: number;
  fire: boolean;
  title?: string;
}

export interface Duel {
  id: string;
  p: [DuelPlayer, DuelPlayer];
}

export interface DuelPlayer {
  n: string; // name
  r: number; // rating
  k: number; // rank
  t?: string; // title
}

export interface DuelTeams {
  [userId: string]: string;
}

export interface Arrangement {
  user1: ArrangementUser;
  user2: ArrangementUser;
  order?: number;
  name?: string;
  color?: Color;
  gameId?: string;
  status?: StatusId;
  winner?: Color;
  plies?: number;
  scheduledAt?: number;
  history?: string[];
}

export interface ArrangementUser {
  id: string;
  readyAt?: number;
  scheduledAt?: number;
}
