# Login as a lichess user

## Login

Returns an authentication cookie and a `user` object.

```sh
http --form POST l.org/login username=thibault password=xxxxxxxx 'Accept:application/vnd.lichess.v1+json'
```

Response: `200 OK`

```
Set-Cookie: lila2="3b5cc8c80f0af258a31dc4fd1b5381cabe7388c7-sessionId=80q7V5stkKIu"; Expires=Tue, 21 Jul 2015 20:31:43 GMT; Path=/; Domain=.l.org; HTTPOnly
```

```javascript
{
  "username": "thibault",
  "online": true,
  "language": "en",
  "profile": {
    "bio": "Developer of lichess",
    "country": "FR",
    "firstName": "Thibault",
    "lastName": "Duplessis",
    "location": "Paris"
  },
  "nowPlaying": [
    {
      "id": "abcdefgh1234",
      "variant": "standard",
      "speed": "blitz",
      "perf": "standard",
      "rated": true,
      "opponent": {
        "id": "supercopter",
        "username": "SuperCopter",
        "rating": 2399
    },
  ],
  "perfs": {                                // user performances in different games
    "bullet": {
      "games": 35,                          // number of rated games played
      "rating": 1624,                       // Glicko2 rating
      "rd": 80                              // Glicko2 rating deviation
    },
    "chess960": {
      "games": 1,
      "rating": 1739,
      "rd": 277
    },
    "classical": {
      "games": 331,
      "rating": 1603,
      "rd": 65
    },
    "kingOfTheHill": {
      "games": 3,
      "rating": 1622,
      "rd": 223
    },
    "puzzle": {
      "games": 9,
      "rating": 1902,
      "rd": 117
    },
    "standard": {
      "games": 736,
      "rating": 1576,
      "rd": 79
    },
    "threeCheck": {
      "games": 1,
      "rating": 1662,
      "rd": 290
    }
  }
}
```

## Account info

Requires authentication.

```sh
http GET l.org/account/info
```

Response: `200` OK
See `user` object in `Login` section, above.

## Logout

```sh
http GET l.org/logout
```

Response: `200` OK
