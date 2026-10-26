var M3U = (function () {
    'use strict';

    var loading = {};
    var epgUrls = {};
    var epgMem = {};
    var epgJobs = {};
    var EPG_KEEP = 3;

    function fetchAll(src, retries) {
        var vu = Validate.httpUrl(src.url);
        if (!vu.ok) return Promise.reject(new Error(vu.error));
        return Net.text(vu.value, null, { timeout: 120000, retries: retries, scope: 'cat' }).then(function (text) {
            var vt = Validate.playlistText(text);
            if (!vt.ok) throw new Error(vt.error);
            return Net.work('m3u', [text], 'cat');
        }).then(function (r) {
            var eu = (r.epg && r.epg[0]) || '';
            epgUrls[src.id] = eu;
            Store.set('m3u:epgurl:' + src.id, { u: eu });
            return { live: r.live || [], vod: r.vod || [], series: r.series || [] };
        });
    }

    function epgUrlFor(src) {
        if (epgUrls[src.id] !== undefined) return Promise.resolve(epgUrls[src.id]);
        return Store.get('m3u:epgurl:' + src.id).then(function (v) {
            epgUrls[src.id] = (v && v.u) || '';
            return epgUrls[src.id];
        });
    }

    function xmlText(s) {
        return String(s || '').replace(/<[^>]*>/g, '').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&').trim();
    }

    function xmlTime(s) {
        var m = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*([+-]\d{4})?/.exec(s || '');
        if (!m) return 0;
        var ms = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] || 0));
        if (m[7]) {
            var sign = m[7].charAt(0) === '-' ? -1 : 1;
            ms -= sign * (parseInt(m[7].slice(1, 3), 10) * 60 + parseInt(m[7].slice(3, 5), 10)) * 60000;
        }
        return ms;
    }

    function clock(ms) {
        var d = new Date(ms);
        return (d.getHours() < 10 ? '0' : '') + d.getHours() + ':' + (d.getMinutes() < 10 ? '0' : '') + d.getMinutes();
    }

    function parseEpg(text, wanted, done) {
        var map = {};
        var pos = 0;
        var now = Date.now();
        (function slice() {
            var t0 = Date.now();
            while (pos < text.length) {
                var a = text.indexOf('<programme', pos);
                if (a === -1) { pos = text.length; break; }
                var b = text.indexOf('</programme>', a);
                var head = text.indexOf('>', a);
                if (head === -1) { pos = text.length; break; }
                var end = b === -1 ? head + 1 : b + 12;
                pos = end;
                var tag = text.slice(a, head + 1);
                var cm = /\schannel="([^"]*)"/.exec(tag);
                if (!cm || !wanted[cm[1]]) continue;
                var stop = xmlTime((/\sstop="([^"]*)"/.exec(tag) || [])[1]);
                if (stop && stop <= now) continue;
                var start = xmlTime((/\sstart="([^"]*)"/.exec(tag) || [])[1]);
                if (!start) continue;
                var body = b === -1 ? '' : text.slice(head + 1, b);
                var tm = /<title[^>]*>([\s\S]*?)<\/title>/.exec(body);
                var title = xmlText(tm ? tm[1] : '');
                if (!title) continue;
                var list = map[cm[1]] || (map[cm[1]] = []);
                list.push({ s: start, e: stop || start + 3600000, t: title.slice(0, 120) });
                if (Date.now() - t0 > 12) break;
            }
            if (pos < text.length) { setTimeout(slice, 0); return; }
            Object.keys(map).forEach(function (k) {
                map[k].sort(function (x, y) { return x.s - y.s; });
                map[k] = map[k].slice(0, EPG_KEEP);
            });
            done(map);
        })();
    }

    function loadEpg(src, wanted) {
        if (epgJobs[src.id]) return epgJobs[src.id];
        var key = 'm3uepg:' + src.id;
        var p = Store.getTTL(key).then(function (hit) {
            if (hit) return hit;
            return epgUrlFor(src).then(function (u) {
                if (!u || !Validate.httpUrl(u).ok || /\.gz(\?|$)/i.test(u)) return {};
                var max = (window.CONFIG && CONFIG.EPG_MAX_BYTES) || 15000000;
                return Net.probe(u, 6000).then(function (info) {
                    if (info && (info.length > max || /gzip|x-gz/.test(info.type))) return {};
                    return Net.text(u, null, { timeout: 90000, retries: 1, scope: 'epgx' }).then(function (text) {
                        if (!text || text.length > max || text.charCodeAt(0) === 31) return {};
                        return new Promise(function (resolve) {
                            parseEpg(text, wanted, function (map) {
                                Store.setTTL(key, map, (window.CONFIG && CONFIG.EPG_TTL_MS) || 21600000);
                                resolve(map);
                            });
                        });
                    });
                });
            });
        }).then(function (map) {
            epgMem[src.id] = map || {};
            return epgMem[src.id];
        });
        epgJobs[src.id] = p;
        function done() { delete epgJobs[src.id]; }
        p.then(done, done);
        return p;
    }

    function shared(src, force) {
        if (!force && loading[src.id]) return loading[src.id];
        var p = fetchAll(src, 2);
        loading[src.id] = p;
        function done() { if (loading[src.id] === p) delete loading[src.id]; }
        p.then(done, done);
        return p;
    }

    function catalog(src, kind, opts) {
        var force = !!(opts && opts.force);
        return Catalog.get(src, kind, function () {
            return shared(src, force).then(function (b) {
                ['live', 'vod', 'series'].forEach(function (k) {
                    if (k !== kind && !Catalog.pending(src, k)) Catalog.put(src, k, b[k]);
                });
                return b[kind] || [];
            });
        }, opts);
    }

    function seriesInfo(src, serie) {
        var eps = (serie.eps || []).slice();
        eps.sort(function (a, b) { return a.season - b.season || a.num - b.num; });
        return Promise.resolve(eps);
    }

    function resolve(src, item) {
        return Promise.resolve(item.url);
    }

    function pick(list) {
        var now = Date.now();
        var cur = null;
        var next = null;
        for (var i = 0; i < list.length; i++) {
            if (list[i].e <= now) continue;
            if (list[i].s <= now) cur = cur || list[i];
            else { next = list[i]; break; }
        }
        if (!cur) return null;
        var out = { now: { start: clock(cur.s), title: cur.t } };
        if (next) out.next = { start: clock(next.s), title: next.t };
        return out;
    }

    function epg(src, ch, live) {
        if (!ch || !ch.tvgId) return Promise.resolve(null);
        var have = epgMem[src.id];
        if (have) return Promise.resolve(have[ch.tvgId] ? pick(have[ch.tvgId]) : null);
        var wanted = {};
        (live || []).forEach(function (it) { if (it.tvgId) wanted[it.tvgId] = 1; });
        wanted[ch.tvgId] = 1;
        return loadEpg(src, wanted).then(function (map) {
            return map[ch.tvgId] ? pick(map[ch.tvgId]) : null;
        });
    }

    function forget(id) {
        delete loading[id];
        Catalog.forget(id);
        delete epgUrls[id];
        delete epgMem[id];
        Store.delPrefix('t:m3uepg:' + id);
    }

    function test(src) {
        var vu = Validate.httpUrl(src.url);
        if (!vu.ok) return Promise.reject(new Error(vu.error));
        return Net.text(vu.value, null, { timeout: 60000, retries: 1, scope: 'test' }).then(function (text) {
            var vt = Validate.playlistText(text);
            if (!vt.ok) throw new Error(vt.error);
            var n = 0;
            var i = -1;
            while ((i = text.indexOf('#EXTINF', i + 1)) !== -1) n++;
            if (!n) {
                var lines = text.split('\n');
                for (var j = 0; j < lines.length; j++) {
                    var l = lines[j].trim();
                    if (l && l.charAt(0) !== '#') n++;
                }
            }
            if (!n) throw new Error('No channels found in this playlist');
            return n;
        });
    }

    return { catalog: catalog, seriesInfo: seriesInfo, resolve: resolve, epg: epg, forget: forget, test: test };
})();
