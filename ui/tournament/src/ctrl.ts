import type { Challenge, ChallengeData } from 'challenge/interfaces';
import { randomNumber } from 'common/common';
import { type StoredJsonProp, type StoredProp, storedJsonProp, storedProp } from 'common/storage';
import { ids } from 'game/status';
import type {
  Arrangement,
  NewArrangement,
  NewArrangementSettings,
  Pages,
  PlayerInfo,
  Points,
  Standing,
  TeamInfo,
  TournamentDataBase,
  TournamentDataFull,
  TournamentOpts,
  TourPlayer,
} from './interfaces';
import { myPage, players } from './pagination';
import makeSocket, { type TournamentSocket } from './socket';
import * as sound from './sound';
import type { Tab } from './view/arrangement/tabs';
import xhr from './xhr';

interface CtrlTeamInfo {
  requested?: string;
  loaded?: TeamInfo;
}

export default class TournamentController {
  opts: TournamentOpts;
  data: TournamentDataFull;
  challengeData: ChallengeData;
  socket: TournamentSocket;
  page: number;
  pages: Pages = {};
  lastPageDisplayed: number | undefined;
  joinSpinner = false;
  tourRedirect = false;
  playerInfo: PlayerInfo = {};
  teamInfo: CtrlTeamInfo = {};
  arrangement: Arrangement | undefined;
  defaultArrangementPoints: Points = { w: 3, d: 2, l: 1 };
  playerManagement = false;
  newArrangement: NewArrangement | undefined;
  activeTab: StoredProp<Tab> | undefined;
  newArrangementSettings: StoredJsonProp<NewArrangementSettings>;
  shadedCandidates: string[] = [];
  disableClicks = true;
  searching = false;
  joinWithTeamSelector = false;
  dateToFinish: Date | undefined;
  reloading: string | undefined;
  lastReloaded: number | undefined = Date.now();
  redraw: () => void;
  nbWatchers = 0;

  private lastStorage = window.lishogi.storage.make('last-redirect');

  constructor(opts: TournamentOpts, redraw: () => void) {
    this.opts = opts;
    this.data = opts.data;
    this.challengeData = opts.challenges || {
      in: [],
      out: [],
    };
    this.redraw = redraw;
    this.socket = makeSocket(opts.socketSend, this);
    this.page = this.data.standing.page || 1;
    setTimeout(() => {
      this.disableClicks = false;
    }, 1500);
    this.loadPage(this.data.standing);
    this.scrollToMe();
    sound.end(this.data);
    sound.countDown(this.data);
    this.recountTeams();

    if (this.isArena()) this.redirectToMyGame();

    if (this.data.secondsToFinish)
      this.dateToFinish = new Date(Date.now() + this.data.secondsToFinish * 1000);

    this.newArrangementSettings = storedJsonProp(
      `arrangement.newArrangementSettings.${this.opts.userId}`,
      () => {
        return {};
      },
    );

    if (this.isRobin()) this.activeTab = storedProp('tournament.tab.robin', 'games');
    else if (this.isOrganized()) this.activeTab = storedProp('tournament.tab.org', 'games');

    if (this.activeTab?.() === 'challenges' && !this.data.isStarted) this.activeTab('games');

    this.updateCreatorButtons();

    this.hashChange();
    window.addEventListener('hashchange', () => {
      this.hashChange(true);
    });
    // reload on reconnection
    window.lishogi.pubsub.on('socket.open', () => {
      if (this.lastReloaded && Date.now() - this.lastReloaded > 10000) {
        this.lastReloaded = Date.now();
        this.askReload();
      }
    });
    window.lishogi.pubsub.on('socket.in.crowd', data => {
      this.nbWatchers = data.nb;
    });
    window.lishogi.pubsub.on('socket.in.challenges', (data: ChallengeData) => {
      const filter: (c: Challenge) => boolean = (c: Challenge) => {
        return !!c.tourInfo && c.tourInfo.tourId === this.data.id;
      };
      this.challengeData = {
        in: data.in.filter(filter),
        out: data.out.filter(filter),
      };
      this.redraw();
    });
  }

  hashChange = (redraw = false): void => {
    const hash = window.location.hash.slice(1);
    const userIds = hash.split(';');
    if (this.data.system === 'robin') {
      const id = userIds.length === 2 ? this.makeRobinId(userIds as [string, string]) : undefined;
      if (id) this.arrangement = this.findOrCreateArrangement(id);
    } else if (hash.length === 8) this.arrangement = this.findArrangement(hash);
    if (redraw) this.redraw();
  };

  askReload = (): void => {
    if (this.joinSpinner || this.isCreator()) xhr.reloadNow(this);
    else setTimeout(() => xhr.reloadSoon(this), randomNumber(5000));
  };

  askFullReload = (): void => {
    setTimeout(() => xhr.reloadSoon(this, false), randomNumber(5000));
  };

  reload = (data: TournamentDataBase): void => {
    // we joined a private tournament! Reload the page to load the chat
    if (!this.data.me && data.me) {
      if (this.data.private) window.lishogi.reload();
      else if (this.isOrganized()) this.activeTab!('players');
    }

    this.lastReloaded = Date.now();
    this.data = { ...this.data, ...data };
    // could be undefined so we need to overwrite manually
    this.data.me = data.me;
    this.data.isCandidate = data.isCandidate;
    this.data.isDenied = data.isDenied;
    this.data.isClosed = data.isClosed;
    this.data.isFull = data.isFull;
    this.data.candidatesOnly = data.candidatesOnly;
    this.data.candidatesFull = data.candidatesFull;

    if (data.playerInfo && data.playerInfo.player.id === this.playerInfo.id)
      this.playerInfo.data = data.playerInfo;

    if (this.data.secondsToFinish)
      this.dateToFinish = new Date(Date.now() + this.data.secondsToFinish * 1000);

    this.loadPage(data.standing);

    if (this.activeTab?.() === 'challenges' && !!data.isFinished) this.activeTab('games');

    sound.end(this.data);
    sound.countDown(this.data);

    this.shadedCandidates = [];
    this.joinSpinner = false;
    this.reloading = undefined;

    this.recountTeams();

    this.updateCreatorButtons();

    if (this.isArena()) this.redirectToMyGame();
  };

  isRobin = (): boolean => this.data.system === 'robin';
  isOrganized = (): boolean => this.data.system === 'organized';
  isArena = (): boolean => this.data.system === 'arena';

  isCreator = (): boolean => this.data.createdBy === this.opts.userId;

  isCorres = (): boolean => 'days' in this.data.clock;

  myGameId = (): string | undefined => {
    if (this.isCorres()) return;
    else if (this.isArena()) return this.data.me?.gameId;
    else
      return this.data.standing.arrangements.find(
        a => this.arrangementHasMe(a) && a.status === ids.started,
      )?.gameId;
  };

  private recountTeams() {
    if (this.data.teamBattle)
      this.data.teamBattle.hasMoreThanTenTeams =
        Object.keys(this.data.teamBattle.teams).length > 10;
  }

  private redirectToMyGame() {
    const gameId = this.myGameId();
    if (gameId) this.redirectFirst(gameId);
  }

  redirectFirst = (gameId: string, rightNow?: boolean): void => {
    const delay = rightNow || document.hasFocus() ? 10 : 1000 + Math.random() * 500;
    setTimeout(() => {
      if (this.lastStorage.get() !== gameId) {
        this.lastStorage.set(gameId);
        this.tourRedirect = true;
        window.lishogi.redirect(`/${gameId}`);
      }
    }, delay);
  };

  loadPage = (data: Standing): void => {
    if (this.isArena()) {
      if (!data.failed || !this.pages[data.page]) this.pages[data.page] = data.players;
    }
  };

  setPage = (page: number): void => {
    this.page = page;
    if (this.isArena()) xhr.loadPage(this, page);
    else this.redraw();
  };

  jumpToPageOf = (name: string): void => {
    const userId = name.toLowerCase();
    if (this.isArena()) {
      xhr.loadPageOf(this, userId).then(data => {
        this.loadPage(data);
        this.page = data.page;
        this.searching = false;
        this.pages[this.page]
          .filter(p => p.name.toLowerCase() == userId)
          .forEach(this.showPlayerInfo);
      });
    }
    this.redraw();
  };

  userSetPage = (page: number): void => {
    this.setPage(page);
  };

  userNextPage = (): void => this.userSetPage(this.page + 1);
  userPrevPage = (): void => this.userSetPage(this.page - 1);
  userLastPage = (): void => this.userSetPage(players(this).nbPages);

  withdraw = (): void => {
    xhr.withdraw(this);
    this.joinSpinner = true;
  };

  join = (password?: string, team?: string): void => {
    this.joinWithTeamSelector = false;
    if (!this.data.verdicts.accepted)
      this.data.verdicts.list.forEach((v: any) => {
        if (v.verdict !== 'ok') alert(v.verdict);
      });
    else if (this.data.teamBattle && !team && !this.data.me) {
      this.joinWithTeamSelector = true;
    } else {
      xhr.join(this, password, team);
      this.joinSpinner = true;
    }
  };

  processCandidate(userId: string, accept: boolean): void {
    this.shadedCandidates.push(userId);
    this.socket.send('process-candidate', { u: userId, v: accept });
    this.redraw();
  }

  playerKick(userId: string): void {
    this.socket.send('player-kick', { v: userId });
  }

  closeJoining(v: boolean): void {
    this.socket.send('close-joining', { v });
  }

  annulGame(arrId: string, gameId: string | undefined): void {
    if (gameId) {
      this.socket.send('arrangement-annul', {
        id: arrId,
        gid: gameId,
      });
      this.showArrangement(undefined);
    }
  }

  scrollToMe = (): void => {
    if (this.isArena()) {
      const page = myPage(this);
      if (page && page !== this.page) this.setPage(page);
    }
  };

  findArrangement = (id: string): Arrangement | undefined => {
    return this.data.standing.arrangements.find(a => a.id === id);
  };

  findOrCreateArrangement = (id: string): Arrangement | undefined => {
    const existing = this.findArrangement(id);
    if (existing) return existing;
    else if (this.isRobin()) {
      const parsed = this.parseRobinId(id);
      if (parsed) return this.createRobinArrangement(parsed.user1Id, parsed.user2Id);
      else return;
    } else return;
  };

  private createRobinArrangement(user1Id: string, user2Id: string): Arrangement {
    return {
      id: `${this.data.id}/${user1Id};${user2Id}`,
      user1: {
        id: user1Id,
      },
      user2: {
        id: user2Id,
      },
    };
  }

  private parseRobinId(s: string): { user1Id: string; user2Id: string } | undefined {
    const [tourId, usersPart] = s.split('/', 2);
    if (!usersPart) return;

    const users = usersPart
      .split(';')
      .map(u => u.toLowerCase())
      .sort();

    if (users.length !== 2 || tourId !== this.data.id) return;

    const [user1Id, user2Id] = users;
    if (this.data.standing.players.filter(p => p.id === user1Id || p.id === user2Id).length !== 2)
      return;
    else return { user1Id, user2Id };
  }

  makeRobinId(users: [string, string]): string | undefined {
    const sortedUsers = users.map(u => u.toLowerCase()).sort();
    if (sortedUsers[0] === sortedUsers[1]) return;
    else return `${this.data.id}/${sortedUsers[0]};${sortedUsers[1]}`;
  }

  showArrangement = (arrangement: Arrangement | undefined): void => {
    this.arrangement = arrangement;
    if (arrangement) {
      const anchor = this.isOrganized()
        ? `#${arrangement.id}`
        : `#${arrangement.user1!.id};${arrangement.user2!.id}`;
      window.history.replaceState(null, '', anchor);
    } else history.replaceState(null, '', window.location.pathname + window.location.search);
    this.redraw();
  };

  arrangementHasMe = (arrangement: Arrangement): boolean => {
    return this.opts.userId === arrangement.user1?.id || this.opts.userId === arrangement.user2?.id;
  };

  arrangementTime = (arrangement: Arrangement, date: Date | undefined): void => {
    const data: Socket.Payload = {
      id: arrangement.id,
    };
    if (date) data.t = date.getTime();
    this.socket.send('arrangement-time', data);
  };

  showOrganizerArrangement(arr: NewArrangement | undefined): void {
    if (arr)
      this.newArrangement = {
        ...arr,
        user1: arr.user1
          ? {
              id: arr.user1.id,
            }
          : undefined,
        user2: arr.user2
          ? {
              id: arr.user2.id,
            }
          : undefined,
        origin: arr,
      };
    else this.newArrangement = undefined;
    this.redraw();
    window.scrollTo(window.scrollX, 0);
  }

  showPlayerInfo = (player: TourPlayer): void => {
    if (this.data.secondsToStart) return;
    const userId = player.name.toLowerCase();
    this.teamInfo.requested = undefined;
    this.playerInfo = {
      id: this.playerInfo.id === userId ? undefined : userId,
      player: player,
      data: null,
    };
    if (this.playerInfo.id) xhr.playerInfo(this, this.playerInfo.id);
  };

  unshowPlayerInfo = (): void => {
    if (this.playerInfo) {
      this.playerInfo.id = undefined;
      this.redraw();
    }
  };

  setPlayerInfoData = (data: PlayerInfo): void => {
    if (data.player.id === this.playerInfo.id) this.playerInfo.data = data;
  };

  showTeamInfo = (teamId: string): void => {
    this.playerInfo.id = undefined;
    this.teamInfo = {
      requested: this.teamInfo.requested === teamId ? undefined : teamId,
      loaded: undefined,
    };
    if (this.teamInfo.requested) xhr.teamInfo(this, this.teamInfo.requested);
  };

  unshowTeamInfo = (): void => {
    this.teamInfo = {
      requested: undefined,
      loaded: undefined,
    };
    this.redraw();
  };

  setTeamInfo = (teamInfo: TeamInfo): void => {
    if (teamInfo.id === this.teamInfo.requested) this.teamInfo.loaded = teamInfo;
  };

  toggleSearch = (): void => {
    this.searching = !this.searching;
  };

  updateCreatorButtons = (): void => {
    const pmb = this.opts.playerManagementButton;
    if (pmb) {
      if (this.data.isFinished) pmb.classList.add('disabled');

      const candidates = this.data.candidates;
      if (candidates?.length) pmb.classList.add('data-count');
      pmb.setAttribute('data-count', `${candidates?.length || 0}`);
    }

    const teb = this.opts.teamEditButton;
    if (teb) {
      if (this.data.isFinished) teb.classList.add('disabled');
    }
  };
}
