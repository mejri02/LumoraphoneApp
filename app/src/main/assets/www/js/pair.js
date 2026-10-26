/* Lumora phone <-> TV pairing over an ntfy relay. See PROTOCOL.md.
 *   phone -> TV : POST  <relay><topic>
 *   TV -> phone : SSE   <relay><topic>-tv
 * Every message body is a JSON string: {"lumora":1,"op":"...","id":"...","ts":...}
 */
var Pair = (function () {
    'use strict';

    var KEY = 'lumora.pair';
    var st = { topic: '', relay: '' };
    var es = null;
    var seen = 0;          // last time we heard from the TV (ms)
    var tv = null;         // last status object received from the TV
    var subs = [];
    var seq = 0;
    var sseUp = false;
    var tick = null;

    function load() {
        try { var o = JSON.parse(localStorage.getItem(KEY) || '{}'); st.topic = o.topic || ''; st.relay = o.relay || ''; } catch (e) { }
    }
    function save() { try { localStorage.setItem(KEY, JSON.stringify(st)); } catch (e) { } }

    function relay() {
        var r = String(st.relay || (window.CONFIG && CONFIG.RELAY) || 'https://ntfy.sh/').trim();
        return r.replace(/\/*$/, '/');
    }

    // Accepts: "ab12cd34", "lumora-ab12cd34", "https://host/?t=lumora-ab12cd34", "lumora://pair?t=...".
    function parse(input) {
        var s = String(input || '').trim();
        if (!s) return '';
        var m = /[?&#]t=([A-Za-z0-9_-]+)/.exec(s);
        if (m) s = m[1];
        else if (/:\/\//.test(s)) return '';
        s = s.replace(/^\s*lumora[-_ ]?/i, '').replace(/[^A-Za-z0-9]/g, '').toLowerCase();
        return s.length >= 6 ? 'lumora-' + s : '';
    }

    function emit() { subs.forEach(function (f) { try { f(info()); } catch (e) { } }); }
    function on(f) { subs.push(f); }

    function info() {
        return {
            paired: !!st.topic,
            code: st.topic ? st.topic.replace(/^lumora-/, '') : '',
            online: !!st.topic && Date.now() - seen < 45000,
            listening: sseUp,
            tv: tv
        };
    }

    function send(op, data) {
        if (!st.topic) return Promise.reject(new Error('Not paired'));
        var msg = { lumora: 1, op: op, id: Date.now().toString(36) + (++seq), ts: Date.now() };
        if (data) Object.keys(data).forEach(function (k) { msg[k] = data[k]; });
        return fetch(relay() + st.topic, { method: 'POST', body: JSON.stringify(msg) }).then(function (r) {
            if (!r.ok) throw new Error('Relay error (HTTP ' + r.status + ')');
            return msg;
        });
    }

    function handle(text) {
        var m = null;
        try { m = JSON.parse(text); } catch (e) { return; }
        if (!m || m.lumora !== 1) return;
        seen = Date.now();
        if (m.op === 'status' || m.op === 'hello_ack') tv = Object.assign({}, tv || {}, m);
        emit();
    }

    function stopSse() {
        if (es) { try { es.close(); } catch (e) { } es = null; }
        sseUp = false;
    }

    function listen() {
        stopSse();
        if (!st.topic || typeof EventSource === 'undefined') return;
        var since = Math.floor(Date.now() / 1000) - 3;
        try {
            es = new EventSource(relay() + st.topic + '-tv/sse?since=' + since);
        } catch (e) { return; }
        es.onopen = function () { sseUp = true; emit(); };
        es.onerror = function () { sseUp = false; emit(); };
        es.onmessage = function (ev) {
            var o = null;
            try { o = JSON.parse(ev.data); } catch (e) { return; }
            if (o && o.event === 'message' && o.message) handle(o.message);
        };
    }

    function connect(input) {
        var t = parse(input);
        if (!t) return false;
        st.topic = t;
        save();
        tv = null; seen = 0;
        listen();
        send('hello', { device: 'phone', name: 'Lumora Phone' }).catch(function () { });
        emit();
        return true;
    }

    function disconnect() {
        send('bye').catch(function () { });
        stopSse();
        st.topic = '';
        tv = null; seen = 0;
        save();
        emit();
    }

    function setRelay(url) {
        st.relay = String(url || '').trim();
        save();
        if (st.topic) listen();
        emit();
    }

    function ping() { if (st.topic) send('hello', { device: 'phone' }).catch(function () { }); }

    function init() {
        load();
        if (st.topic) listen();
        clearInterval(tick);
        tick = setInterval(emit, 5000); // refresh "online" indicator as status messages age out
    }

    return { init: init, on: on, info: info, parse: parse, connect: connect, disconnect: disconnect, send: send, ping: ping, setRelay: setRelay, relay: relay };
})();
