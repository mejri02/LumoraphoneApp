/* Lumora phone <-> TV link over an ntfy relay.
 *   phone -> TV : POST  <relay>lumora-<tvid>
 *   TV -> phone : SSE   <relay>lumora-<tvid>-tv   (several TVs share one SSE connection)
 * Every message body is JSON: {"lumora":1,"op":"...","id":"...","ts":...}
 */
var Pair = (function () {
    'use strict';

    var KEY = 'lumora.pair2', OLD = 'lumora.pair';
    var st = { tvs: [], active: '', relay: '' };
    var es = null, sseUp = false, tick = null, seq = 0;
    var seen = {}, status = {}, subs = [];

    function load() {
        try {
            var o = JSON.parse(localStorage.getItem(KEY) || 'null');
            if (o && Array.isArray(o.tvs)) { st.tvs = o.tvs; st.active = o.active || ''; st.relay = o.relay || ''; return; }
        } catch (e) { }
        try {
            var old = JSON.parse(localStorage.getItem(OLD) || 'null');
            if (old && old.topic) {
                var id = String(old.topic).replace(/^lumora-/, '');
                st.tvs = [{ id: id, name: 'TV' }];
                st.active = id;
                st.relay = old.relay || '';
                save();
            }
        } catch (e2) { }
    }
    function save() { try { localStorage.setItem(KEY, JSON.stringify(st)); } catch (e) { } }

    function relay() {
        var r = String(st.relay || (window.CONFIG && CONFIG.RELAY) || 'https://ntfy.sh/').trim();
        return r.replace(/\/*$/, '/');
    }
    function topic(id) { return 'lumora-' + id; }

    /* "a3cc-db44-0851-2719", "lumora-a3ccdb44...", "lumora://pair?t=lumora-..." -> "a3ccdb4408512719" */
    function parse(input) {
        var s = String(input || '').trim();
        if (!s) return '';
        var m = /[?&#]t=([A-Za-z0-9_-]+)/.exec(s);
        if (m) s = m[1];
        else if (/:\/\//.test(s)) return '';
        s = s.replace(/^\s*lumora[-_ ]?/i, '').replace(/[^A-Za-z0-9]/g, '').toLowerCase();
        return s.length >= 8 && s.length <= 40 ? s : '';
    }
    function fmt(id) { return String(id || '').replace(/(.{4})(?=.)/g, '$1-'); }

    function find(id) { for (var i = 0; i < st.tvs.length; i++) if (st.tvs[i].id === id) return st.tvs[i]; return null; }
    function isOnline(id) { return Date.now() - (seen[id] || 0) < 45000; }

    function list() {
        return st.tvs.map(function (t) {
            var s = status[t.id] || null;
            return { id: t.id, label: fmt(t.id), name: t.name || (s && s.name) || 'TV', online: isOnline(t.id), active: st.active === t.id, tv: s };
        });
    }
    function info() {
        var a = st.active ? find(st.active) : null;
        var on = !!a && isOnline(a.id);
        return {
            paired: !!a, connected: on, online: on,
            id: a ? a.id : '', code: a ? a.id : '', label: a ? fmt(a.id) : '',
            name: a ? (a.name || 'TV') : '', tv: a ? (status[a.id] || null) : null,
            tvs: list(), listening: sseUp
        };
    }

    function on(f) { subs.push(f); }
    function emit() { var i = info(); subs.forEach(function (f) { try { f(i); } catch (e) { } }); }

    function post(id, msg) {
        return fetch(relay() + topic(id), { method: 'POST', body: JSON.stringify(msg) }).then(function (r) {
            if (!r.ok) throw new Error('Relay error (HTTP ' + r.status + ')');
            return msg;
        });
    }
    function mk(op, data) {
        var msg = { lumora: 1, op: op, id: Date.now().toString(36) + (++seq), ts: Date.now() };
        if (data) Object.keys(data).forEach(function (k) { msg[k] = data[k]; });
        return msg;
    }
    function sendTo(id, op, data) { return post(id, mk(op, data)); }
    function send(op, data) {
        if (!st.active) return Promise.reject(new Error('Not paired'));
        return sendTo(st.active, op, data);
    }

    function handle(id, text) {
        var m = null;
        try { m = JSON.parse(text); } catch (e) { return; }
        if (!m || m.lumora !== 1) return;
        seen[id] = Date.now();
        if (m.op === 'status' || m.op === 'hello_ack') {
            var s = Object.assign({}, status[id] || {}, m);
            s._rx = Date.now();
            status[id] = s;
        }
        emit();
    }

    function stopSse() { if (es) { try { es.close(); } catch (e) { } es = null; } sseUp = false; }
    var retryT = null;
    function listen() {
        stopSse();
        clearTimeout(retryT);
        if (!st.tvs.length || typeof EventSource === 'undefined') return;
        var topics = st.tvs.map(function (t) { return topic(t.id) + '-tv'; }).join(',');
        var since = Math.floor(Date.now() / 1000) - 3;
        var me;
        try { me = es = new EventSource(relay() + topics + '/sse?since=' + since); } catch (e) { return; }
        me.onopen = function () { sseUp = true; emit(); };
        me.onerror = function () {
            sseUp = false; emit();
            try { me.close(); } catch (e) { }
            if (es === me) { es = null; retryT = setTimeout(listen, 4000); }
        };
        me.onmessage = function (ev) {
            var o = null;
            try { o = JSON.parse(ev.data); } catch (e) { return; }
            if (!o || o.event !== 'message' || !o.message) return;
            var t = String(o.topic || '').replace(/-tv$/, '').replace(/^lumora-/, '');
            if (!t && st.tvs.length === 1) t = st.tvs[0].id;
            if (find(t)) handle(t, o.message);
        };
    }

    function ping(id) {
        var ids = id ? [id] : st.tvs.map(function (t) { return t.id; });
        ids.forEach(function (x) { sendTo(x, 'hello', { device: 'phone', name: 'Lumora Phone' }).catch(function () { }); });
    }

    function add(input, name) {
        var id = parse(input);
        if (!id) return '';
        var t = find(id);
        if (!t) { t = { id: id, name: String(name || '').trim().slice(0, 30) || 'TV' }; st.tvs.push(t); }
        else if (name) t.name = String(name).trim().slice(0, 30);
        st.active = id;
        save();
        listen();
        ping(id);
        emit();
        return id;
    }
    function select(id) { if (!find(id)) return; st.active = id; save(); ping(id); emit(); }
    function deselect() { st.active = ''; save(); emit(); }
    function remove(id) {
        if (!find(id)) return;
        sendTo(id, 'bye').catch(function () { });
        st.tvs = st.tvs.filter(function (t) { return t.id !== id; });
        if (st.active === id) st.active = '';
        delete seen[id]; delete status[id];
        save(); listen(); emit();
    }
    function rename(id, name) { var t = find(id); if (t) { t.name = String(name || '').trim().slice(0, 30) || 'TV'; save(); emit(); } }

    function waitOnline(id, ms) {
        return new Promise(function (resolve) {
            var t0 = Date.now();
            (function chk() {
                if (isOnline(id) && seen[id] >= t0 - 1000) return resolve(true);
                if (Date.now() - t0 > ms) return resolve(false);
                setTimeout(chk, 250);
            })();
        });
    }

    function setRelay(url) { st.relay = String(url || '').trim(); save(); listen(); emit(); }

    function init() {
        load();
        listen();
        ping();
        clearInterval(tick);
        tick = setInterval(emit, 5000);
        document.addEventListener('visibilitychange', function () { if (!document.hidden) { if (!es) listen(); ping(); } });
    }

    return {
        init: init, on: on, info: info, list: list, parse: parse, fmt: fmt, add: add, select: select, deselect: deselect,
        remove: remove, rename: rename, send: send, sendTo: sendTo, ping: ping, waitOnline: waitOnline, setRelay: setRelay, relay: relay
    };
})();
