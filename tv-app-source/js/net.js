var Net = (function () {
    'use strict';

    var inflight = {};
    var memo = {};
    var MEMO_MAX = 40;
    var active = 0;
    var queue = [];
    var scopes = {};

    function cfg(k, d) {
        var c = window.CONFIG || {};
        return c[k] !== undefined ? c[k] : d;
    }

    var FORBIDDEN_RE = /^(cookie2?|host|origin|referer|accept-charset|accept-encoding|connection|content-length|date|expect|keep-alive|te|trailer|transfer-encoding|upgrade|via|proxy-.*|sec-.*)$/i;

    function blockedHeaders(headers) {
        var out = [];
        if (!headers) return out;
        Object.keys(headers).forEach(function (k) {
            if (headers[k] && FORBIDDEN_RE.test(k)) out.push(k);
        });
        return out;
    }

    function fail(msg, retryable, aborted, params, code) {
        var e = new Error(msg);
        e.retryable = !!retryable;
        e.aborted = !!aborted;
        if (params) e.params = params;
        if (code) e.code = code;
        return e;
    }

    function httpFail(status) {
        if (status === 401 || status === 403) return fail('Access denied (HTTP {n})', false, false, { n: status }, status);
        if (status === 404) return fail('Not found (HTTP {n})', false, false, { n: status }, status);
        if (status === 429) return fail('Too many requests, try again later (HTTP {n})', true, false, { n: status }, status);
        if (status >= 500) return fail('Server error (HTTP {n})', true, false, { n: status }, status);
        if (status === 408) return fail('Request timed out', true, false, null, status);
        return fail('Unexpected server response (HTTP {n})', false, false, { n: status }, status);
    }

    function pump() {
        var max = cfg('NET_CONCURRENCY', 6);
        while (active < max && queue.length) {
            var j = queue.shift();
            if (!j.dead) j.run();
        }
    }

    function register(scope, job) {
        if (!scope) return;
        (scopes[scope] = scopes[scope] || []).push(job);
    }

    function unregister(scope, job) {
        var list = scopes[scope];
        if (!list) return;
        var i = list.indexOf(job);
        if (i >= 0) list.splice(i, 1);
        if (!list.length) delete scopes[scope];
    }

    function once(url, headers, timeout, scope, prio) {
        return new Promise(function (resolve, reject) {
            var job = { dead: false, started: false, xhr: null, idle: null };
            var idleMs = cfg('IDLE_TIMEOUT_MS', 25000);

            function release() {
                clearTimeout(job.idle);
                unregister(scope, job);
                if (job.started) { job.started = false; active--; pump(); }
            }

            function settle(ok, val) {
                if (job.dead) return;
                job.dead = true;
                release();
                if (!ok && val && job.blocked && job.blocked.length) val.blocked = job.blocked;
                if (ok) resolve(val); else reject(val);
            }

            function kick() {
                clearTimeout(job.idle);
                job.idle = setTimeout(function () {
                    if (job.dead) return;
                    try { job.xhr.abort(); } catch (e) { }
                    settle(false, fail('Request timed out', true));
                }, idleMs);
            }

            job.abort = function () {
                if (job.dead) return;
                try { if (job.xhr) job.xhr.abort(); } catch (e) { }
                settle(false, fail('Cancelled', false, true));
            };

            job.run = function () {
                job.started = true;
                active++;
                var xhr = new XMLHttpRequest();
                job.xhr = xhr;
                xhr.open('GET', url, true);
                xhr.timeout = timeout;
                job.blocked = blockedHeaders(headers);
                if (headers) {
                    Object.keys(headers).forEach(function (k) {
                        if (!headers[k]) return;
                        try { xhr.setRequestHeader(k, headers[k]); } catch (e) { if (job.blocked.indexOf(k) < 0) job.blocked.push(k); }
                    });
                }
                xhr.onprogress = kick;
                xhr.onload = function () {
                    var s = xhr.status;
                    if (s >= 200 && s < 300) settle(true, xhr.responseText);
                    else settle(false, httpFail(s));
                };
                xhr.onerror = function () { settle(false, fail('Network error', true)); };
                xhr.ontimeout = function () { settle(false, fail('Request timed out', true)); };
                xhr.onabort = function () { settle(false, fail('Cancelled', false, true)); };
                kick();
                try { xhr.send(); } catch (e) { settle(false, fail('Network error', true)); }
            };

            register(scope, job);
            if (active < cfg('NET_CONCURRENCY', 6)) job.run();
            else if (prio) queue.unshift(job);
            else queue.push(job);
        });
    }

    function backoff(n) {
        var base = Math.min(8000, 400 * Math.pow(2, n - 1));
        return Math.round(base * (0.8 + Math.random() * 0.4));
    }

    function request(url, headers, opts) {
        opts = opts || {};

        if (!opts.raw) {
            var chk = window.Validate ? Validate.httpUrl(url) : { ok: true, value: url };
            if (!chk.ok) return Promise.reject(fail(chk.error, false));
            url = chk.value;
        }

        var key = url + '\n' + (headers ? JSON.stringify(headers) : '');
        var now = Date.now();

        var hit = memo[key];
        if (hit && hit.until > now) return Promise.resolve(hit.value);
        if (inflight[key]) return inflight[key];

        var pkey = (opts.persist && opts.ttl > 0 && window.Store) ? 'net:' + key : '';

        function network() {
            if (typeof navigator !== 'undefined' && navigator.onLine === false) {
                return Promise.reject(fail('No connection', true));
            }
            var retries = opts.retries !== undefined ? opts.retries : cfg('NET_RETRIES', 2);
            var timeout = opts.timeout || cfg('FETCH_TIMEOUT_MS', 30000);
            var scope = opts.scope || '';
            var attempt = 0;
            return new Promise(function (resolve, reject) {
                (function go() {
                    once(url, headers, timeout, scope, opts.prio).then(resolve, function (err) {
                        if (attempt < retries && err.retryable && !err.aborted) {
                            attempt++;
                            setTimeout(go, backoff(attempt));
                        } else {
                            reject(err);
                        }
                    });
                })();
            });
        }

        var p = pkey
            ? Store.getTTL(pkey).then(function (cachedText) {
                if (typeof cachedText === 'string') return cachedText;
                return network().then(function (text) {
                    if (typeof text === 'string' && text.length < 3000000) Store.setTTL(pkey, text, opts.ttl);
                    return text;
                });
            })
            : network();

        inflight[key] = p;
        function done() { if (inflight[key] === p) delete inflight[key]; }
        p.then(function (text) {
            done();
            if (opts.ttl > 0) remember(key, text, opts.ttl);
        }, done);
        return p;
    }

    function remember(key, value, ttl) {
        var keys = Object.keys(memo);
        if (keys.length >= MEMO_MAX) {
            keys.sort(function (a, b) { return memo[a].until - memo[b].until; });
            for (var i = 0; i < keys.length - MEMO_MAX + 8; i++) delete memo[keys[i]];
        }
        memo[key] = { value: value, until: Date.now() + ttl };
    }

    function json(url, headers, opts) {
        return request(url, headers, opts).then(function (text) {
            var head = String(text || '').replace(/^\uFEFF/, '').trim();
            if (!head) throw fail('Empty response from server', true);
            var c = head.charAt(0);
            if (c !== '{' && c !== '[') throw fail('Invalid response from server', false);
            try { return JSON.parse(head); } catch (e) { throw fail('Invalid response from server', false); }
        });
    }

    function clear() {
        memo = {};
        if (window.Store) Store.delPrefix('t:net:');
    }

    function probe(url, timeoutMs) {
        return new Promise(function (resolve) {
            var done = false;
            var xhr;
            function fin(v) {
                if (done) return;
                done = true;
                try { if (xhr) xhr.abort(); } catch (e) { }
                resolve(v);
            }
            try {
                xhr = new XMLHttpRequest();
                xhr.open('GET', url, true);
                xhr.timeout = timeoutMs || cfg('PROBE_TIMEOUT_MS', 4000);
                xhr.onreadystatechange = function () {
                    if (xhr.readyState >= 2) {
                        var type = '';
                        var len = 0;
                        try { type = (xhr.getResponseHeader('Content-Type') || '').toLowerCase(); } catch (e) { }
                        try { len = parseInt(xhr.getResponseHeader('Content-Length'), 10) || 0; } catch (e2) { }
                        fin({ status: xhr.status, type: type, length: len });
                    }
                };
                xhr.onerror = xhr.ontimeout = function () { fin(null); };
                xhr.send();
            } catch (e) { fin(null); }
        });
    }

    var CORE = function () {
        var EP_RE = /^(.*?)[\s._-]*S(\d{1,2})\s*[._ -]?E(\d{1,3})/i;
        var ATTR_RE = /([\w-]+)="([^"]*)"/g;
        var KIND_RE = /\/(series|movie)\//i;
        var FILE_RE = /\.(mp4|mkv|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|3gp|ogv)(\?|$)/i;
        var URL_OK = /^(https?|rtmps?|rtsp|rtp|udp|mmsh?):\/\/\S+$/i;
        var GRP_SERIES = /\bseries\b|\bserie\b|\bshows?\b|\u0645\u0633\u0644\u0633\u0644/i;
        var GRP_VOD = /\bmovies?\b|\bfilms?\b|\bvod\b|\bcinema\b|\u0641\u064a\u0644\u0645|\u0627\u0641\u0644\u0627\u0645|\u0623\u0641\u0644\u0627\u0645/i;

        function m3u(text) {
            var pos = 0;
            var len = text.length;
            var pending = null;
            var count = 0;
            var live = [], vod = [], series = [];
            var smap = Object.create(null);
            var optUa = '';
            var optRef = '';
            var epgUrl = '';

            function info(line) {
                var attrs = {};
                var m;
                ATTR_RE.lastIndex = 0;
                while ((m = ATTR_RE.exec(line))) attrs[m[1].toLowerCase()] = m[2];
                var start = line.lastIndexOf('"') + 1;
                var comma = line.indexOf(',', start);
                var name = comma >= 0 ? line.slice(comma + 1).trim() : '';
                return {
                    name: name || attrs['tvg-name'] || 'Unknown',
                    group: attrs['group-title'] || 'Uncategorized',
                    logo: attrs['tvg-logo'] || '',
                    tvgId: attrs['tvg-id'] || ''
                };
            }

            function header(line) {
                var attrs = {};
                var m;
                ATTR_RE.lastIndex = 0;
                while ((m = ATTR_RE.exec(line))) attrs[m[1].toLowerCase()] = m[2];
                var u = (attrs['url-tvg'] || attrs['x-tvg-url'] || attrs['tvg-url'] || '').split(',')[0].trim();
                if (/^https?:\/\//i.test(u)) epgUrl = u;
            }

            function vlcOpt(line) {
                var o = line.slice(11);
                var eq = o.indexOf('=');
                if (eq < 1) return;
                var k = o.slice(0, eq).trim().toLowerCase();
                var v = o.slice(eq + 1).replace(/[\u0000-\u001F\u007F]/g, '').trim().slice(0, 300);
                if (k === 'http-user-agent') optUa = v;
                else if (k === 'http-referrer' || k === 'http-referer') optRef = v;
            }

            function kindOf(u, group) {
                var m = KIND_RE.exec(u);
                if (m) return m[1].toLowerCase() === 'series' ? 's' : 'v';
                if (FILE_RE.test(u)) return 'v';
                if (group) {
                    if (GRP_SERIES.test(group)) return 's';
                    if (GRP_VOD.test(group)) return 'v';
                }
                return 'l';
            }

            function extra(o, it) {
                if (it.ua) o.ua = it.ua;
                if (it.referer) o.referer = it.referer;
                return o;
            }

            function add(it, url) {
                var k = kindOf(url, it.group);
                if (k === 'l') {
                    var lo = { id: url, name: it.name, group: it.group, logo: it.logo, kind: 'live', url: url };
                    if (it.tvgId) lo.tvgId = it.tvgId;
                    live.push(extra(lo, it));
                } else if (k === 'v') {
                    vod.push(extra({ id: url, name: it.name, group: it.group, logo: it.logo, kind: 'vod', url: url }, it));
                } else {
                    var m = it.name.match(EP_RE);
                    var title = m ? (m[1].trim() || it.name) : it.name;
                    var key = title.toLowerCase();
                    var s = smap[key];
                    if (!s) {
                        s = smap[key] = { id: key, name: title, group: it.group, logo: it.logo, kind: 'series', eps: [] };
                        series.push(s);
                    }
                    var sn = m ? parseInt(m[2], 10) : 1;
                    var en = m ? parseInt(m[3], 10) : s.eps.length + 1;
                    s.eps.push(extra({ id: url, name: it.name, group: 'Season ' + sn, season: sn, num: en, logo: it.logo, url: url, kind: 'ep' }, it));
                }
            }

            return {
                step: function (budget) {
                    var t0 = Date.now();
                    while (pos < len) {
                        var nl = text.indexOf('\n', pos);
                        if (nl === -1) nl = len;
                        var line = text.slice(pos, nl).trim();
                        pos = nl + 1;
                        if (line) {
                            var c = line.charCodeAt(0);
                            if (c === 35) {
                                if (line.indexOf('#EXTINF') === 0) pending = info(line);
                                else if (line.indexOf('#EXTVLCOPT:') === 0) vlcOpt(line);
                                else if (line.indexOf('#EXTM3U') === 0) header(line);
                            } else {
                                if (URL_OK.test(line)) {
                                    var base = pending || { name: line, group: 'Uncategorized', logo: '' };
                                    if (optUa) base.ua = optUa;
                                    if (optRef) base.referer = optRef;
                                    add(base, line);
                                }
                                pending = null;
                                optUa = '';
                                optRef = '';
                            }
                        }
                        if ((++count & 511) === 0 && Date.now() - t0 > budget) return false;
                    }
                    return true;
                },
                result: function () { return { live: live, vod: vod, series: series, epg: epgUrl ? [epgUrl] : [] }; }
            };
        }

        function xt(text, kind, cats) {
            var arr = null;
            var i = 0;
            var out = [];

            function conv(s) {
                var group = cats[s.category_id] || 'Uncategorized';
                if (kind === 'live') {
                    return { id: String(s.stream_id), name: s.name || 'Unknown', group: group, logo: s.stream_icon || '', kind: 'live' };
                }
                if (kind === 'vod') {
                    return { id: String(s.stream_id), name: s.name || 'Unknown', group: group, logo: s.stream_icon || '', kind: 'vod', ext: s.container_extension || 'mp4', rating: s.rating ? String(s.rating) : '', year: '' };
                }
                return { id: String(s.series_id), name: s.name || 'Unknown', group: group, logo: s.cover || '', kind: 'series', plot: s.plot || '', genre: s.genre || '', cast: s.cast || '', rating: s.rating ? String(s.rating) : '', year: (s.releaseDate || '').slice(0, 4) };
            }

            return {
                step: function (budget) {
                    var t0 = Date.now();
                    if (arr === null) {
                        try { arr = JSON.parse(text); } catch (e) { throw new Error('Invalid response from server'); }
                        text = null;
                        if (!Array.isArray(arr)) arr = [];
                        return false;
                    }
                    var n = arr.length;
                    while (i < n) {
                        out.push(conv(arr[i]));
                        i++;
                        if ((i & 255) === 0 && Date.now() - t0 > budget) return false;
                    }
                    return true;
                },
                result: function () { return { items: out }; }
            };
        }

        return { m3u: m3u, xt: xt };
    };

    var worker = null;
    var workerOff = false;
    var wjobs = {};
    var wseq = 0;
    var localCore = null;
    var CHUNK = 3000;

    function workerSource() {
        return 'var core=(' + CORE.toString() + ')();var CH=' + CHUNK + ';' +
            'onmessage=function(e){var m=e.data;try{var task=core[m.t].apply(null,m.a);while(!task.step(1e9)){}' +
            'var r=task.result();Object.keys(r).forEach(function(k){var a=r[k];' +
            'if(!a.length){postMessage({id:m.id,k:k,p:[]});return;}' +
            'for(var i=0;i<a.length;i+=CH)postMessage({id:m.id,k:k,p:a.slice(i,i+CH)});});' +
            'postMessage({id:m.id,done:1});}catch(err){postMessage({id:m.id,err:String(err&&err.message||err)});}};';
    }

    function spawn() {
        if (worker || workerOff) return worker;
        try {
            var blob = new Blob([workerSource()], { type: 'application/javascript' });
            var url = (window.URL || window.webkitURL).createObjectURL(blob);
            worker = new Worker(url);
            worker.onmessage = function (e) {
                var m = e.data;
                var j = wjobs[m.id];
                if (!j) return;
                if (m.err) { delete wjobs[m.id]; j.reject(new Error(m.err)); return; }
                if (m.done) { delete wjobs[m.id]; j.resolve(j.res); return; }
                var dst = j.res[m.k] || (j.res[m.k] = []);
                var p = m.p;
                for (var i = 0; i < p.length; i++) dst.push(p[i]);
            };
            worker.onerror = function () {
                workerOff = true;
                try { worker.terminate(); } catch (e) { }
                worker = null;
                var pending = wjobs;
                wjobs = {};
                Object.keys(pending).forEach(function (id) {
                    var j = pending[id];
                    j.res = {};
                    runLocal(j);
                });
            };
        } catch (e) {
            workerOff = true;
            worker = null;
        }
        return worker;
    }

    function runLocal(j) {
        if (!localCore) localCore = CORE();
        var task;
        try { task = localCore[j.t].apply(null, j.a); } catch (e) { j.reject(e); return; }
        (function loop() {
            if (j.dead) return;
            var done;
            try { done = task.step(12); } catch (e) { j.reject(e); return; }
            if (done) j.resolve(task.result());
            else setTimeout(loop, 0);
        })();
    }

    function work(type, args, scope) {
        return new Promise(function (resolve, reject) {
            var j = { id: ++wseq, t: type, a: args, scope: scope || '', res: {}, dead: false, resolve: resolve, reject: reject };
            var w = spawn();
            if (w) {
                wjobs[j.id] = j;
                try { w.postMessage({ id: j.id, t: type, a: args }); } catch (e) { delete wjobs[j.id]; runLocal(j); }
            } else {
                runLocal(j);
            }
        });
    }

    function cancel(scope) {
        var list = scopes[scope];
        if (list) list.slice().forEach(function (j) { j.abort(); });
        var killed = false;
        Object.keys(wjobs).forEach(function (id) {
            var j = wjobs[id];
            if (j.scope === scope) {
                j.dead = true;
                delete wjobs[id];
                killed = true;
                j.reject(fail('Cancelled', false, true));
            }
        });
        if (killed && worker) {
            try { worker.terminate(); } catch (e) { }
            worker = null;
            var rest = wjobs;
            wjobs = {};
            Object.keys(rest).forEach(function (id) {
                var j = rest[id];
                j.res = {};
                var w = spawn();
                if (w) { wjobs[j.id] = j; w.postMessage({ id: j.id, t: j.t, a: j.a }); }
                else runLocal(j);
            });
        }
        queue = queue.filter(function (j) { return !j.dead; });
    }

    function each(arr, fn, budget) {
        budget = budget || 12;
        return new Promise(function (resolve, reject) {
            var i = 0;
            var n = arr.length;
            function step() {
                var t0 = Date.now();
                try {
                    while (i < n) {
                        fn(arr[i], i);
                        i++;
                        if ((i & 127) === 0 && Date.now() - t0 > budget) {
                            setTimeout(step, 0);
                            return;
                        }
                    }
                } catch (e) { reject(e); return; }
                resolve();
            }
            step();
        });
    }

    function map(arr, fn, budget) {
        var out = new Array(arr.length);
        return each(arr, function (x, i) { out[i] = fn(x, i); }, budget).then(function () { return out; });
    }

    return { text: request, json: json, clear: clear, cancel: cancel, work: work, each: each, map: map, probe: probe, blockedHeaders: blockedHeaders };
})();
