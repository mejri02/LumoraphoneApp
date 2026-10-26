var Store = (function () {
    'use strict';

    var mem = {};
    var dbp = null;
    var TTL_PREFIX = 't:';
    var HOT_MAX = 80;
    var hot = {};
    var hotKeys = [];
    var flights = {};

    function open() {
        if (dbp) return dbp;
        dbp = new Promise(function (resolve) {
            var settled = false;
            function fin(db) { if (!settled) { settled = true; resolve(db); } }
            try {
                var rq = window.indexedDB.open('lumora', 1);
                rq.onupgradeneeded = function () {
                    if (!rq.result.objectStoreNames.contains('kv')) rq.result.createObjectStore('kv');
                };
                rq.onsuccess = function () { fin(rq.result); };
                rq.onerror = function () { fin(null); };
                rq.onblocked = function () { fin(null); };
                setTimeout(function () { fin(null); }, 5000);
            } catch (e) { fin(null); }
        });
        return dbp;
    }

    function run(mode, fn) {
        return open().then(function (db) {
            if (!db) return undefined;
            return new Promise(function (resolve) {
                try {
                    var tx = db.transaction('kv', mode);
                    var st = tx.objectStore('kv');
                    var out;
                    var rq = fn(st);
                    if (rq) rq.onsuccess = function () { out = rq.result; };
                    tx.oncomplete = function () { resolve(out); };
                    tx.onerror = tx.onabort = function () { resolve(undefined); };
                } catch (e) { resolve(undefined); }
            });
        });
    }

    function get(key) {
        return run('readonly', function (st) { return st.get(key); }).then(function (v) {
            return v !== undefined ? v : (mem[key] !== undefined ? mem[key] : null);
        });
    }

    function set(key, value) {
        mem[key] = value;
        return run('readwrite', function (st) { st.put(value, key); return null; }).then(function () {
            return open().then(function (db) { if (db) delete mem[key]; });
        });
    }

    function del(key) {
        delete mem[key];
        return run('readwrite', function (st) { st.delete(key); return null; });
    }

    function hotDrop(prefix) {
        Object.keys(hot).forEach(function (k) { if (!prefix || k.indexOf(prefix) === 0) delete hot[k]; });
        hotKeys = hotKeys.filter(function (k) { return hot[k] !== undefined; });
    }

    function delPrefix(prefix) {
        Object.keys(mem).forEach(function (k) { if (k.indexOf(prefix) === 0) delete mem[k]; });
        hotDrop(prefix.indexOf(TTL_PREFIX) === 0 ? prefix.slice(TTL_PREFIX.length) : '');
        return run('readwrite', function (st) {
            st.delete(window.IDBKeyRange.bound(prefix, prefix + '\uffff'));
            return null;
        });
    }

    function clear() {
        mem = {};
        hot = {};
        hotKeys = [];
        flights = {};
        return run('readwrite', function (st) { st.clear(); return null; });
    }

    function hotPut(key, env) {
        if (hot[key] === undefined) {
            hotKeys.push(key);
            if (hotKeys.length > HOT_MAX) delete hot[hotKeys.shift()];
        }
        hot[key] = env;
    }

    function valid(env) {
        return env && typeof env === 'object' && env.__t === 1 && typeof env.exp === 'number';
    }

    function entry(key) {
        var h = hot[key];
        if (h) return Promise.resolve(h);
        return get(TTL_PREFIX + key).then(function (env) {
            if (!valid(env)) return null;
            hotPut(key, env);
            return env;
        });
    }

    function getTTL(key, allowStale) {
        return entry(key).then(function (env) {
            if (!env) return null;
            if (env.exp > Date.now() || allowStale) return env.v;
            delete hot[key];
            del(TTL_PREFIX + key);
            return null;
        });
    }

    function ageOf(key) {
        return entry(key).then(function (env) { return env ? Date.now() - env.at : -1; });
    }

    function setTTL(key, value, ttl) {
        var now = Date.now();
        var env = { __t: 1, v: value, at: now, exp: now + Math.max(1000, ttl || 0) };
        hotPut(key, env);
        return set(TTL_PREFIX + key, env);
    }

    function cached(key, ttl, loader, opts) {
        opts = opts || {};
        if (flights[key]) return flights[key];
        var p = entry(key).then(function (env) {
            var now = Date.now();
            if (env && env.exp > now) return env.v;
            return Promise.resolve().then(loader).then(function (v) {
                if (v !== undefined && v !== null) setTTL(key, v, ttl);
                return v;
            }, function (err) {
                if (env && opts.staleOnError !== false) return env.v;
                throw err;
            });
        });
        flights[key] = p;
        function done() { if (flights[key] === p) delete flights[key]; }
        p.then(done, done);
        return p;
    }

    function purgeExpired() {
        return open().then(function (db) {
            if (!db) return 0;
            return new Promise(function (resolve) {
                var n = 0;
                try {
                    var tx = db.transaction('kv', 'readwrite');
                    var rq = tx.objectStore('kv').openCursor(window.IDBKeyRange.bound(TTL_PREFIX, TTL_PREFIX + '\uffff'));
                    var now = Date.now();
                    rq.onsuccess = function () {
                        var c = rq.result;
                        if (!c) return;
                        var env = c.value;
                        if (!valid(env) || env.exp <= now) { c.delete(); n++; }
                        c.continue();
                    };
                    tx.oncomplete = function () { resolve(n); };
                    tx.onerror = tx.onabort = function () { resolve(n); };
                } catch (e) { resolve(n); }
            });
        });
    }

    return {
        get: get, set: set, del: del, delPrefix: delPrefix, clear: clear,
        getTTL: getTTL, setTTL: setTTL, ageOf: ageOf, cached: cached, purgeExpired: purgeExpired
    };
})();

var Catalog = (function () {
    'use strict';

    var CHUNK = 6000;
    var VERSION = 2;
    var mem = {};
    var at = {};
    var pend = {};
    var gens = {};
    var listeners = [];

    function maxStale() {
        return (window.CONFIG && window.CONFIG.CACHE_MAX_STALE_MS) || 604800000;
    }

    function base(src, kind) { return 'c:' + src.id + ':' + kind; }

    function tick() { return new Promise(function (r) { setTimeout(r, 0); }); }

    function listen(fn) { listeners.push(fn); }

    function emit(src, kind, items) {
        for (var i = 0; i < listeners.length; i++) {
            try { listeners[i](src, kind, items); } catch (e) { }
        }
    }

    function save(src, kind, items) {
        var b = base(src, kind);
        var n = Math.ceil(items.length / CHUNK);
        var gen = (gens[b] = (gens[b] || 0) + 1);
        var stamp = Date.now();
        mem[b] = items;
        at[b] = stamp;
        emit(src, kind, items);
        return Store.delPrefix(b + ':').then(function () {
            var i = 0;
            function next() {
                if (gens[b] !== gen) return Promise.resolve();
                if (i >= n) return Store.set(b + ':m', { v: VERSION, at: stamp, n: n, count: items.length });
                var idx = i++;
                return Store.set(b + ':' + idx, items.slice(idx * CHUNK, (idx + 1) * CHUNK)).then(tick).then(next);
            }
            return next();
        });
    }

    function load(src, kind) {
        var b = base(src, kind);
        return Store.get(b + ':m').then(function (m) {
            if (!m || !m.n) return null;
            var out = [];
            var i = 0;
            function next() {
                if (i >= m.n) return Promise.resolve({ at: m.at, items: out });
                return Store.get(b + ':' + (i++)).then(function (part) {
                    if (!part || !part.length) return null;
                    for (var j = 0; j < part.length; j++) out.push(part[j]);
                    return tick().then(next);
                });
            }
            return next();
        });
    }

    function fromDisk(src, kind) {
        var b = base(src, kind);
        return load(src, kind).then(function (rec) {
            if (!rec || !rec.items.length || Date.now() - rec.at >= maxStale()) return null;
            mem[b] = rec.items;
            at[b] = rec.at;
            return rec.items;
        });
    }

    function peek(src, kind) {
        var b = base(src, kind);
        if (mem[b]) return Promise.resolve(mem[b]);
        if (pend[b]) return Promise.resolve(null);
        return fromDisk(src, kind);
    }

    function get(src, kind, fetcher, opts) {
        var b = base(src, kind);
        var force = !!(opts && opts.force);
        if (!force) {
            if (mem[b]) return Promise.resolve(mem[b]);
            if (pend[b]) return pend[b];
        }
        var p = (force ? Promise.resolve(null) : fromDisk(src, kind)).then(function (hit) {
            if (hit) return hit;
            return Promise.resolve().then(fetcher).then(function (items) {
                items = items || [];
                save(src, kind, items);
                return items;
            });
        });
        pend[b] = p;
        function done() { if (pend[b] === p) delete pend[b]; }
        p.then(done, done);
        return p;
    }

    function put(src, kind, items) { return save(src, kind, items || []); }

    function pending(src, kind) { return !!pend[base(src, kind)]; }

    function age(src, kind) {
        var s = at[base(src, kind)];
        return s ? s : 0;
    }

    function forget(id) {
        var prefix = 'c:' + id + ':';
        [mem, at, pend].forEach(function (o) {
            Object.keys(o).forEach(function (k) { if (k.indexOf(prefix) === 0) delete o[k]; });
        });
        Object.keys(gens).forEach(function (k) { if (k.indexOf(prefix) === 0) gens[k]++; });
        return Store.delPrefix(prefix);
    }

    function release(id) {
        var prefix = 'c:' + id + ':';
        Object.keys(mem).forEach(function (k) { if (k.indexOf(prefix) === 0) delete mem[k]; });
    }

    return { get: get, put: put, peek: peek, age: age, pending: pending, forget: forget, release: release, listen: listen };
})();
