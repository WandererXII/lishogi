# [Lishogi](https://lishogi.org)

[![Build server](https://github.com/WandererXII/lishogi/workflows/Build%20server/badge.svg)](https://github.com/WandererXII/lishogi/actions?query=workflow%3A%22Build+server%22)
[![Build assets](https://github.com/WandererXII/lishogi/workflows/Build%20assets/badge.svg)](https://github.com/WandererXII/lishogi/actions?query=workflow%3A%22Build+assets%22)
[![Crowdin](https://badges.crowdin.net/lishogi/localized.svg)](https://crowdin.com/project/lishogi)
[![Twitter](https://img.shields.io/badge/Twitter-%40lishogi-blue.svg?style=flat)](https://twitter.com/lishogi)
[![Discord](https://dcbadge.vercel.app/api/server/YFtpMGg3rR?style=flat)](https://discord.gg/YFtpMGg3rR)

![Lishogi homepage-Lishogi comes with light and dark theme, this screenshot shows both.](public/images/preview.png)

Lila (li[shogi in sca]la) is a dynamic online shogi game server focused on providing a real-time gaming experience with user-friendly features.

**Key Features:**
- [Server Analysis](https://lishogi.org/B8fAS7aW/gote) powered by [shoginet](https://github.com/WandererXII/shoginet)
- [Local Analysis](https://lishogi.org/analysis)
- [Tournaments](https://lishogi.org/tournament)
- [Simuls](https://lishogi.org/simul)
- [Forums](https://lishogi.org/forum)
- [Teams](https://lishogi.org/team)
- [Puzzles](https://lishogi.org/training)
- [Search Engine](https://lishogi.org/games/search)
- [Shared Analysis Board](https://lishogi.org/study)

Lishogi is essentially a shogi adaptation of [Lichess](https://lichess.org), written in [Scala 2.13](https://www.scala-lang.org/), and built on the [Play](https://www.playframework.com/) framework. The project utilizes [scalatags](https://com-lihaoyi.github.io/scalatags/) for templating. Pure shogi logic is encapsulated in the [shogi](modules/shogi) submodule. The server operates fully asynchronously, leveraging Scala Futures and [Akka streams](http://akka.io). WebSocket connections are managed by a [dedicated server](https://github.com/WandererXII/lila-ws) communicating through [redis](https://redis.io/). Game data is stored in [MongoDB](https://mongodb.org). Proxying of HTTP requests and WebSocket connections can be achieved through [nginx](http://nginx.org). The web client is developed using [TypeScript](https://www.typescriptlang.org/) and [snabbdom](https://github.com/snabbdom/snabbdom), with [Sass](https://sass-lang.com/) for CSS generation. The [blog](https://lishogi.org/blog) follows a free open content plan from [prismic.io](https://prismic.io).

[Join us on Discord](https://discord.gg/YFtpMGg3rR) for discussions and more information. Report bugs and suggest features using [GitHub issues](https://github.com/WandererXII/lishogi/issues).

## Contributors

Thanks to all the wonderful people who have contributed to Lishogi! If you'd like to contribute, please check out our [Contributing Guidelines](CONTRIBUTING.md).

<!-- Contributors List -->
<!-- CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- CONTRIBUTORS-LIST:END -->

## Credits

This project owes its existence to [ornicar](https://github.com/ornicar) and the entire [Lichess project](https://github.com/lichess-org/lila).

## Supported browsers

| Browser           | Version | Notes                                       |
| ----------------- | ------- | ------------------------------------------- |
| Chromium / Chrome | Latest  | Full support, fastest local analysis        |
| Firefox           | 67+     | Full support, second fastest local analysis |
| Edge              | 91+     | Full support (reasonable support for 17+)   |
| Opera             | 55+     | Reasonable support                          |
| Safari            | 11.1+   | Reasonable support                          |

Older browsers, including any version of Internet Explorer, are unlikely to work. For security and performance reasons, please upgrade.

## License

Li[shogi in scala]la is licensed under the GNU Affero General Public License 3 or any later version at your choice, with an exception for Highcharts. See [LICENSE](/LICENSE) and [COPYING.md](/COPYING.md) for details (Work in Progress).
