# [lishogi.org](https://lishogi.org)

[![lishogi.org](https://img.shields.io/badge/☗_lishogi.org-Play_shogi-black)](https://lishogi.org)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/WandererXII/lishogi/server.yml?label=server)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/WandererXII/lishogi/ui.yml?label=ui)
[![X (formerly Twitter) Follow](https://img.shields.io/twitter/follow/lishogi)](https://twitter.com/lishogi)
[![Discord](https://img.shields.io/discord/778633701541806081?style=social&logo=discord&label=Discord)](https://discord.gg/YFtpMGg3rR)

![Lishogi comes with light and dark theme, this screenshot shows both.](preview.png)

Lila (li[shogi in sca]la) is a free online shogi game server.

## Features

- Real-time games against other users
- Correspondence games against other users
- Playing against shogi engines - YaneuraOu, Fairy-Stockfish and more
- [Puzzles](https://lishogi.org/training)
- [Studies](https://lishogi.org/study)
- [Server analysis](https://lishogi.org/B8fAS7aW/gote) distributed with [shoginet](https://github.com/WandererXII/shoginet)
- [Local analysis](https://lishogi.org/analysis)
- [Tournaments](https://lishogi.org/tournament)
- [Simuls](https://lishogi.org/simul)
- [Forums](https://lishogi.org/forum)
- [Teams](https://lishogi.org/team)
- [Search engine](https://lishogi.org/games/search)
- [Shogi variants](https://lishogi.org/variants)
- And much more!

## Code

Lishogi is [Lichess](https://lichess.org) rewritten for shogi.
Lishogi is written in [Scala 2.13](https://www.scala-lang.org/),
and relies on the [Play](https://www.playframework.com/) framework.
[scalatags](https://com-lihaoyi.github.io/scalatags/) is used for templating.
Pure shogi logic is contained in [scalashogi](https://github.com/WandererXII/scalashogi).
The server is fully asynchronous, making heavy use of Scala Futures and [Akka streams](http://akka.io).
WebSocket connections are handled by a [separate server](https://github.com/WandererXII/lila-ws) that communicates using [redis](https://redis.io/).
It uses [MongoDB](https://mongodb.org) to store games.
HTTP requests and WebSocket connections can be proxied by [nginx](http://nginx.org).
The web client is written in [TypeScript](https://www.typescriptlang.org/) and [snabbdom](https://github.com/snabbdom/snabbdom), using [Sass](https://sass-lang.com/) to generate CSS.
The [blog](https://lishogi.org/blog) uses a free open content plan from [prismic.io](https://prismic.io).

Use [GitHub issues](https://github.com/WandererXII/lishogi/issues) for bug reports and feature requests.

## Credits

This code exists because of [ornicar](https://github.com/ornicar), and the whole [Lichess project](https://github.com/lichess-org/lila). And obviously thanks to the Lishogi community for supporting the project financially, reporting issues, contributing code and using the site!

## Supported browsers

| Name         | Version | Notes                                       |
| ------------ | ------- | ------------------------------------------- |
| Chrome based | last 10 | Full support, fastest local analysis        |
| Firefox      | last 10 | Full support, second fastest local analysis |
| Safari       | 12+     | Reasonable support                          |

Older browsers (including any version of Internet Explorer) will probably not work.
For your own sake, please upgrade. Security and performance, think about it!

## License

Li[shogi in scala]la is licensed under the GNU Affero General Public License 3 or any later version at your choice. See [LICENSE](/LICENSE) and
[COPYING.md](/COPYING.md) for details. WIP
