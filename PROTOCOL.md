# Lumora Pair protocol (phone ⇄ TV)

Transport: an [ntfy](https://ntfy.sh) relay (same one the TV app already uses for the QR "Add source" flow:
`CONFIG.RELAY = 'https://ntfy.sh/'`). The relay can be changed in the phone app's Settings (e.g. a self-hosted ntfy).

| Direction  | How                                            | Topic            |
|------------|------------------------------------------------|------------------|
| phone → TV | `POST <relay><topic>` with the JSON as body    | `lumora-<code>`  |
| TV → phone | TV publishes with `POST`; phone listens SSE   | `lumora-<code>-tv` (`GET <relay><topic>-tv/sse?since=<unix>`) |

`<code>` is the pair code shown on the TV (6+ chars, `a-z0-9`; the current TV QR already uses 12 random base36 chars).
The phone accepts: the bare code, `lumora-<code>`, or any URL containing `?t=lumora-<code>` (so scanning/pasting the
existing QR URL works).

Every message body is JSON: `{"lumora":1,"op":"<op>","id":"<unique>","ts":<ms>, ...}`.
Ignore anything without `"lumora":1` (the legacy "add source" message has no such field).

## Phone → TV

| op      | fields | meaning |
|---------|--------|---------|
| `hello` | `device`, `name` | Sent when pairing and by `ping`. TV should answer with a `status` (or `hello_ack`). |
| `bye`   | – | Phone un-paired. |
| `cast`  | `url`, `title`, `kind` (`live`\|`vod`\|`series`\|`ep`), `logo`, optional `start` (ms), `ref` | Play this stream now. `url` is already resolved by the phone and normally works as-is. |
| `key`   | `key` | Remote key. Values: `up down left right ok back info stop playpause ff rw chup chdown red green yellow blue` and digits `0`-`9`. Map them to your existing key handler (e.g. `ok`→13, `back`→10009, `playpause`→10252, `ff`→417, `rw`→412, `chup`→427, `chdown`→428, colors→403-406, `info`→457). |

`ref` (optional, for sources whose URLs are session/MAC bound, e.g. Stalker, or if the TV wants to re-resolve):
```json
{"src":{"type":"m3u|xtream|stalker","url":"","user":"","pass":"","mac":"","format":""},
 "item":{"id":"","kind":"","name":"","group":"","ext":""}}
```

Legacy message (unchanged, sent by the "send to TV" button on a source): a bare JSON
`{"type":"xtream","name":"..","url":"..","user":"..","pass":".."}` → handled by the TV's existing `applyRemote()`.

## TV → phone (topic `lumora-<code>-tv`)

| op           | fields | meaning |
|--------------|--------|---------|
| `status`     | `state` (`idle\|playing\|paused\|buffering\|error`), `title`, optional `pos`, `dur` (ms), `kind` | Publish on every change and every ~15–20 s while the app is open. The phone shows **TV online** while the last message is < 45 s old. |
| `hello_ack`  | – | Optional reply to `hello`. |

## TV-side sketch (Tizen app, ES5)

```js
var topic = 'lumora-' + code;                       // show `code` (and a QR of CONNECT_URL + '?t=' + topic) on screen
var es = new EventSource(CFG.RELAY + topic + '/sse?since=' + (Math.floor(Date.now()/1000) - 2));
es.onmessage = function (ev) {
    var o; try { o = JSON.parse(ev.data); } catch (e) { return; }
    if (!o || o.event !== 'message') return;
    var m; try { m = JSON.parse(o.message); } catch (e) { return; }
    if (!m || m.lumora !== 1) return;
    if (m.op === 'hello') sendStatus();
    else if (m.op === 'key')  fireKey(m.key);               // dispatch a synthetic keydown to your onKey()
    else if (m.op === 'cast') castPlay(m.url, m.title, m.kind, m.start); // Player.play(m.url, {live: m.kind==='live', start: m.start})
};
function sendStatus() {
    var x = new XMLHttpRequest(); x.open('POST', CFG.RELAY + topic + '-tv', true);
    x.send(JSON.stringify({lumora:1, op:'status', state:'playing', title:'...', ts:Date.now()}));
}
```

Notes
* ntfy.sh rate-limits anonymous clients (burst of ~60 requests, then ~1 per 5 s). Fine for normal use, but for heavy
  remote-control use self-host ntfy and put its URL in the phone's Settings → Relay server (and `CONFIG.RELAY` on the TV).
* Anyone who knows the code can control the TV: use ≥ 8 random chars and regenerate it when pairing again.
