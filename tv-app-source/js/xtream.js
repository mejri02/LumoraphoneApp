var Xtream = (function () {
    'use strict';

    function base(src) {
        var v = Validate.server(src.url);
        return v.ok ? v.value : String(src.url || '').trim().replace(/\/+$/, '');
    }

    function problem(src) {
        var r = Validate.server(src && src.url);
        if (!r.ok) return r.error;
        r = Validate.user(src.user);
        if (!r.ok) return r.error;
        r = Validate.pass(src.pass);
        if (!r.ok) return r.error;
        return '';
    }

    function api(src, action, extra) {
        var u = base(src) + '/player_api.php?username=' + encodeURIComponent(Validate.clean(src.user)) +
                '&password=' + encodeURIComponent(String(src.pass == null ? '' : src.pass).trim());
        if (action) u += '&action=' + action;
        if (extra) u += '&' + extra;
        return u;
    }

    function asArray(x) {
        return Array.isArray(x) ? x : [];
    }

    function login(src) {
        var bad = problem(src);
        if (bad) return Promise.reject(new Error(bad));
        return Net.json(api(src), null, { timeout: 12000, retries: 1, ttl: 120000, prio: true }).then(function (info) {
            if (!info || typeof info !== 'object' || Array.isArray(info)) throw new Error('Invalid response from server');
            var ui = info.user_info;
            if (!ui || typeof ui !== 'object') throw new Error('Invalid response from server');
            if (String(ui.auth) !== '1') throw new Error('Invalid username or password');
            if (ui.status && ui.status !== 'Active') throw new Error('Account status: ' + ui.status);
            var exp = parseInt(ui.exp_date, 10);
            if (exp > 0 && exp * 1000 < Date.now()) throw new Error('Account expired');
            return info;
        });
    }

    var CATS = {
        live: ['get_live_categories', 'get_live_streams'],
        vod: ['get_vod_categories', 'get_vod_streams'],
        series: ['get_series_categories', 'get_series']
    };

    function catalog(src, kind, opts) {
        var bad = problem(src);
        if (bad) return Promise.reject(new Error(bad));
        return Catalog.get(src, kind, function () { return remote(src, kind); }, opts);
    }

    function remote(src, kind) {
        var a = CATS[kind];
        var bad = problem(src);
        if (bad) return Promise.reject(new Error(bad));
        var auth = login(src);
        var cats = Net.json(api(src, a[0]), null, { timeout: 30000, retries: 2, scope: 'cat', prio: true }).catch(function (e) {
            if (e && e.aborted) throw e;
            return [];
        });
        var data = Net.text(api(src, a[1]), null, { timeout: 180000, retries: 2, scope: 'cat' });
        data.catch(function () { });
        cats.catch(function () { });
        return auth.then(function () { return Promise.all([cats, data]); }).then(function (res) {
            var map = {};
            asArray(res[0]).forEach(function (c) { map[c.category_id] = c.category_name; });
            return Net.work('xt', [res[1], kind, map], 'cat');
        }).then(function (r) {
            return r.items || [];
        });
    }

    function seriesInfo(src, serie) {
        return Net.json(api(src, 'get_series_info', 'series_id=' + serie.id), null, { timeout: 30000, retries: 2, ttl: (window.CONFIG && CONFIG.SERIES_TTL_MS) || 600000, persist: true, scope: 'cat' }).then(function (d) {
            var seasons = (d && d.episodes) || {};
            var isArr = Array.isArray(seasons);
            var eps = [];
            Object.keys(seasons).forEach(function (k, idx) {
                var sn = isArr ? idx + 1 : (parseInt(k, 10) || idx + 1);
                asArray(seasons[k]).forEach(function (e, j) {
                    var info = e.info || {};
                    eps.push({
                        id: String(e.id),
                        name: e.title || ('Episode ' + (parseInt(e.episode_num, 10) || (j + 1))),
                        group: 'Season ' + sn,
                        season: sn,
                        num: parseInt(e.episode_num, 10) || (j + 1),
                        logo: info.movie_image || serie.logo || '',
                        kind: 'ep',
                        ext: e.container_extension || 'mp4',
                        plot: info.plot || ''
                    });
                });
            });
            eps.sort(function (a, b) { return a.season - b.season || a.num - b.num; });
            return eps;
        });
    }

    function resolve(src, item) {
        var bad = problem(src);
        if (bad) return Promise.reject(new Error(bad));
        var b = base(src);
        var u = encodeURIComponent(Validate.clean(src.user));
        var p = encodeURIComponent(String(src.pass == null ? '' : src.pass).trim());
        if (item.kind === 'live') {
            return Promise.resolve(b + '/live/' + u + '/' + p + '/' + item.id + '.' + (src.format === 'ts' ? 'ts' : 'm3u8'));
        }
        var seg = item.kind === 'ep' ? 'series' : 'movie';
        return Promise.resolve(b + '/' + seg + '/' + u + '/' + p + '/' + item.id + '.' + (item.ext || 'mp4'));
    }

    function decodeB64(s) {
        try { return decodeURIComponent(escape(atob(s))); } catch (e) { return s || ''; }
    }

    function entry(e) {
        return {
            title: decodeB64(e.title),
            start: (e.start || '').slice(11, 16),
            end: (e.end || '').slice(11, 16)
        };
    }

    function epg(src, ch) {
        return Net.json(api(src, 'get_short_epg', 'stream_id=' + ch.id + '&limit=2'), null, { timeout: 8000, retries: 0, scope: 'epg' }).then(function (d) {
            var list = asArray(d && d.epg_listings);
            if (!list.length) return null;
            return { now: entry(list[0]), next: list[1] ? entry(list[1]) : null };
        });
    }

    function detail(src, item) {
        if (item.kind === 'series') {
            return Net.json(api(src, 'get_series_info', 'series_id=' + item.id), null, { timeout: 15000, retries: 1, ttl: (window.CONFIG && CONFIG.SERIES_TTL_MS) || 600000, persist: true, scope: 'detail' }).then(function (d) {
                var i = (d && d.info) || {};
                return {
                    plot: i.plot || i.description || '',
                    year: String(i.releaseDate || i.release_date || '').slice(0, 4),
                    rating: i.rating ? String(i.rating) : '',
                    genre: i.genre ? String(i.genre) : '',
                    cast: i.cast ? String(i.cast) : ''
                };
            });
        }
        return Net.json(api(src, 'get_vod_info', 'vod_id=' + item.id), null, { timeout: 10000, retries: 1, ttl: (window.CONFIG && CONFIG.DETAIL_TTL_MS) || 600000, persist: true, scope: 'detail' }).then(function (d) {
            var info = (d && d.info) || {};
            return {
                plot: info.plot || info.description || '',
                year: String(info.releasedate || '').slice(0, 4),
                rating: info.rating ? String(info.rating) : '',
                genre: info.genre ? String(info.genre) : '',
                cast: info.cast ? String(info.cast) : (info.actors ? String(info.actors) : '')
            };
        });
    }

    function forget(id) {
        Net.clear();
        if (id) Catalog.forget(id);
    }

    function stamp(sec) {
        var d = new Date(sec * 1000);
        function p(n) { return n < 10 ? '0' + n : '' + n; }
        return p(d.getHours()) + ':' + p(d.getMinutes());
    }

    function guide(src, ch) {
        return Net.json(api(src, 'get_simple_data_table', 'stream_id=' + ch.id), null, { timeout: 10000, retries: 0, scope: 'guide' }).then(function (d) {
            return asArray(d && d.epg_listings).map(function (e) {
                var s = parseInt(e.start_timestamp, 10) || 0;
                var en = parseInt(e.stop_timestamp, 10) || 0;
                return {
                    title: decodeB64(e.title),
                    time: s ? stamp(s) : (e.start || '').slice(11, 16),
                    s: s, e: en,
                    raw: String(e.start || ''),
                    mins: en > s ? Math.ceil((en - s) / 60) : 0,
                    arch: String(e.has_archive) === '1'
                };
            }).filter(function (p) { return p.s && p.e; }).sort(function (a, b) { return a.s - b.s; });
        });
    }

    function catchupUrl(src, ch, p) {
        var b = base(src);
        var u = encodeURIComponent(Validate.clean(src.user));
        var pw = encodeURIComponent(String(src.pass == null ? '' : src.pass).trim());
        var m = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}):(\d{2})/.exec(p.raw);
        if (!m) return '';
        return b + '/timeshift/' + u + '/' + pw + '/' + p.mins + '/' + m[1] + ':' + m[2] + '-' + m[3] + '/' + ch.id + '.m3u8';
    }

    return { guide: guide, catchupUrl: catchupUrl, login: login, catalog: catalog, seriesInfo: seriesInfo, resolve: resolve, epg: epg, detail: detail, forget: forget };
})();
