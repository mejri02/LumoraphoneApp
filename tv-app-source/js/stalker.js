var Stalker = (function () {
    'use strict';

    var sessions = {};
    var MAX_PAGES = 400;

    function endpoint(src) {
        var u = src.url.trim();
        if (/\.php(\?|$)/i.test(u)) return u.split('?')[0];
        return u.replace(/\/+$/, '') + '/portal.php';
    }

    function origin(src) {
        var m = src.url.trim().match(/^(https?:\/\/[^\/]+)/i);
        return m ? m[1] : '';
    }

    function toList(x) {
        if (Array.isArray(x)) return x;
        if (x && typeof x === 'object') return Object.keys(x).map(function (k) { return x[k]; });
        return [];
    }

    function proxyOf(src) {
        return src.proxy || (window.CONFIG && window.CONFIG.STALKER_PROXY) || '';
    }

    function call(src, params, token, opts) {
        var q = Object.keys(params).map(function (k) {
            return k + '=' + encodeURIComponent(params[k]);
        }).join('&');
        var headers = {
            'Cookie': 'mac=' + src.mac + '; stb_lang=en; timezone=Europe/Paris',
            'Authorization': token ? 'Bearer ' + token : '',
            'X-User-Agent': 'Model: MAG250; Link: WiFi'
        };
        var url = endpoint(src) + '?' + q + '&JsHttpRequest=1-xml';
        var px = proxyOf(src);
        if (px) {
            url = px + (px.indexOf('?') < 0 ? '?' : '&') + 'u=' + encodeURIComponent(url) +
                '&c=' + encodeURIComponent(headers.Cookie) + '&a=' + encodeURIComponent(token || '');
            headers = null;
        }
        return Net.json(url, headers, opts).then(function (r) {
            if (!r || r.js === undefined) throw new Error('Portal did not return a valid answer');
            return r.js;
        });
    }

    function login(src) {
        if (sessions[src.id]) return Promise.resolve(sessions[src.id]);
        var vu = Validate.httpUrl(src.url);
        if (!vu.ok) return Promise.reject(new Error(vu.error));
        var vm = Validate.mac(src.mac);
        if (!vm.ok) return Promise.reject(new Error(vm.error));
        src.url = vu.value;
        src.mac = vm.value;
        var token = null;
        return call(src, { type: 'stb', action: 'handshake', token: '' }, null, { timeout: 12000, retries: 1 })
            .then(function (js) {
                token = js && js.token;
                if (!token) throw new Error('Handshake failed. Check the portal URL and MAC address.');
                return js;
            })
            .then(null, function (err) {
                var soft = err && (err.aborted || err.message === 'Request timed out' || err.message === 'Network error' || err.message === 'No connection');
                if (!soft && !proxyOf(src) && Net.blockedHeaders({ Cookie: 'x' }).length) {
                    var e = new Error('Stalker needs a cookie header the TV browser can\'t send. Set a Cookie proxy URL in the source.');
                    e.blocked = ['Cookie'];
                    throw e;
                }
                throw err;
            })
            .then(function () {
                return call(src, { type: 'stb', action: 'get_profile', hd: '1', stb_lang: 'en', timezone: 'Europe/Paris' }, token);
            })
            .then(function () {
                sessions[src.id] = { token: token };
                return sessions[src.id];
            });
    }

    function forget(id) {
        delete sessions[id];
    }

    function logoUrl(src, logo) {
        if (!logo) return '';
        if (/^https?:/i.test(logo)) return logo;
        return origin(src) + '/stalker_portal/misc/logos/320/' + logo;
    }

    function withSession(src, fn) {
        function attempt(again) {
            return login(src).then(fn).catch(function (err) {
                forget(src.id);
                if (again && !(err && err.aborted)) return attempt(false);
                throw err;
            });
        }
        return attempt(true);
    }

    function liveCatalog(src) {
        return withSession(src, function (s) {
            return Promise.all([
                call(src, { type: 'itv', action: 'get_genres' }, s.token, { scope: 'cat', prio: true }),
                call(src, { type: 'itv', action: 'get_all_channels' }, s.token, { timeout: 120000, retries: 2, scope: 'cat' })
            ]).then(function (res) {
                var genres = {};
                toList(res[0]).forEach(function (g) { genres[g.id] = g.title; });
                return toList((res[1] && res[1].data) || []).map(function (c) {
                    return {
                        id: String(c.id),
                        name: c.name || 'Unknown',
                        group: genres[c.tv_genre_id] || 'Uncategorized',
                        logo: logoUrl(src, c.logo),
                        kind: 'live',
                        cmd: c.cmd || ''
                    };
                });
            });
        });
    }

    function pagedCatalog(src, type, kind) {
        return withSession(src, function (s) {
            return call(src, { type: type, action: 'get_categories' }, s.token, { scope: 'cat', prio: true }).catch(function () { return []; }).then(function (cats) {
                var names = {};
                toList(cats).forEach(function (c) { names[c.id] = c.title; });

                function fetchPage(p) {
                    return call(src, { type: type, action: 'get_ordered_list', genre: '*', p: p, sortby: 'added' }, s.token, { timeout: 45000, retries: 2, scope: 'cat' });
                }

                function convert(r) {
                    return Net.map(toList(r && r.data), function (c) {
                        return {
                            id: String(c.id),
                            name: c.name || 'Unknown',
                            group: names[c.category_id] || 'Uncategorized',
                            logo: logoUrl(src, c.screenshot_uri || c.logo || ''),
                            kind: kind,
                            cmd: c.cmd || '',
                            rating: String(c.rating_imdb || c.rating_kinopoisk || ''),
                            year: String(c.year || ''),
                            plot: c.description || ''
                        };
                    });
                }

                return fetchPage(1).then(function (first) {
                    var firstData = toList(first && first.data);
                    var total = parseInt(first && first.total_items, 10) || 0;
                    var per = firstData.length;
                    var pages = (per && total) ? Math.min(MAX_PAGES, Math.ceil(total / per)) : 1;
                    var results = [first];
                    var next = 2;
                    var workers = [];

                    function worker() {
                        if (next > pages) return Promise.resolve();
                        var p = next++;
                        return fetchPage(p).then(function (r) {
                            results[p - 1] = r;
                            return worker();
                        });
                    }
                    for (var i = 0; i < 6 && i < pages - 1; i++) workers.push(worker());

                    return Promise.all(workers).then(function () {
                        var all = [];
                        function nextChunk(i) {
                            if (i >= results.length) return Promise.resolve(all);
                            return convert(results[i]).then(function (part) {
                                for (var j = 0; j < part.length; j++) all.push(part[j]);
                                return nextChunk(i + 1);
                            });
                        }
                        return nextChunk(0);
                    });
                });
            });
        });
    }

    function remote(src, kind) {
        if (kind === 'live') return liveCatalog(src);
        return pagedCatalog(src, kind === 'vod' ? 'vod' : 'series', kind);
    }

    function catalog(src, kind, opts) {
        return Catalog.get(src, kind, function () { return remote(src, kind); }, opts);
    }

    function seriesInfo(src, serie) {
        return withSession(src, function (s) {
            return call(src, { type: 'series', action: 'get_ordered_list', movie_id: serie.id, season_id: 0, episode_id: 0, p: 1 }, s.token, { scope: 'cat', ttl: 600000 });
        }).then(function (r) {
            var eps = toList(r && r.data).map(function (e, i) {
                var season = parseInt(e.season_num || e.season, 10) || 1;
                return {
                    id: String(e.id),
                    name: e.name || ('Episode ' + (i + 1)),
                    group: 'Season ' + season,
                    season: season,
                    num: parseInt(e.episode_num, 10) || (i + 1),
                    logo: logoUrl(src, e.screenshot_uri || '') || serie.logo || '',
                    kind: 'ep',
                    cmd: e.cmd || serie.cmd || '',
                    plot: e.description || ''
                };
            });
            eps.sort(function (a, b) { return a.season - b.season || a.num - b.num; });
            return eps;
        });
    }

    function resolve(src, item) {
        return withSession(src, function (s) {
            return call(src, {
                type: item.kind === 'live' ? 'itv' : 'vod',
                action: 'create_link',
                cmd: item.cmd,
                series: item.kind === 'ep' ? item.num : '',
                forced_storage: 'undefined',
                disable_ad: '0',
                download: '0'
            }, s.token, { timeout: 12000, retries: 1, scope: 'resolve', prio: true });
        }).then(function (js) {
            var cmd = js && js.cmd;
            if (!cmd) throw new Error('Portal did not return a stream link');
            return cmd.replace(/^(ffmpeg|ffrt\d*|auto)\s+/i, '').trim();
        });
    }

    function entry(e) {
        return {
            title: e.name || e.descr || '',
            start: (e.time || '').slice(11, 16),
            end: (e.time_to || '').slice(11, 16)
        };
    }

    function epg(src, ch) {
        return withSession(src, function (s) {
            return call(src, { type: 'itv', action: 'get_short_epg', ch_id: ch.id, size: 2 }, s.token, { timeout: 8000, retries: 0, scope: 'epg' });
        }).then(function (js) {
            var list = toList(js);
            if (!list.length) return null;
            return { now: entry(list[0]), next: list[1] ? entry(list[1]) : null };
        });
    }

    function makeMac() {
        var parts = ['00', '1A', '79'];
        for (var i = 0; i < 3; i++) {
            parts.push(('0' + Math.floor(Math.random() * 256).toString(16)).slice(-2).toUpperCase());
        }
        return parts.join(':');
    }

    return { login: login, catalog: catalog, seriesInfo: seriesInfo, resolve: resolve, epg: epg, forget: forget, makeMac: makeMac };
})();
