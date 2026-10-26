var Tmdb = (function () {
    'use strict';

    var BASE = 'https://api.themoviedb.org/3';
    var TTL = 30 * 86400000;

    function key() {
        var k = '';
        try { k = String(Settings.get('tmdbKey') || ''); } catch (e) { }
        if (!k && window.CONFIG) k = String(CONFIG.TMDB_API_KEY || '');
        return k.replace(/[^A-Za-z0-9._-]/g, '');
    }

    function enabled() { return key().length >= 20; }

    function lang() {
        var l = 'en';
        try { l = Settings.get('lang'); } catch (e) { }
        return l === 'ar' ? 'ar' : 'en-US';
    }

    function call(path, params) {
        var k = key();
        var bearer = k.length > 40 || k.indexOf('.') >= 0;
        var q = [];
        Object.keys(params).forEach(function (n) {
            if (params[n] !== '' && params[n] !== undefined && params[n] !== null) q.push(n + '=' + encodeURIComponent(params[n]));
        });
        if (!bearer) q.push('api_key=' + encodeURIComponent(k));
        var url = BASE + path + '?' + q.join('&');
        return Net.json(url, bearer ? { Authorization: 'Bearer ' + k } : null, { timeout: 10000, retries: 1, ttl: TTL, persist: true, scope: 'detail' });
    }

    function parse(name) {
        var s = String(name || '');
        s = s.replace(/\b(?:4K|UHD|FHD|HD|2160p|1080p|720p|480p|BluRay|BRRip|WEB-?DL|WEBRip|HDRip|DVDRip|x26[45]|H\.?26[45]|HEVC|HDR)\b/gi, ' ');
        var year = '';
        var ym = /[\(\[]\s*((?:19|20)\d{2})\s*[\)\]]/.exec(s) || /(?:^|[\s._-])((?:19|20)\d{2})\s*$/.exec(s);
        if (ym) year = ym[1];
        s = s.replace(/[\(\[][^\)\]]*[\)\]]/g, ' ');
        s = s.replace(/^\s*\|\s*[A-Za-z]{2,3}\s*\|\s*/, '');
        var stripped = s.replace(/^\s*[A-Z]{2,3}\s*[-:|]\s+/, '');
        if (/[^\s\d\-:|]/.test(stripped)) s = stripped;
        s = s.replace(/[._]+/g, ' ');
        s = s.replace(/\bS\d{1,2}(?:\s*E\d{1,3})?\b/gi, ' ').replace(/\bSeason\s*\d+\b/gi, ' ');
        s = s.replace(/\s+(?:19|20)\d{2}\s*$/, '');
        s = s.replace(/\s+/g, ' ').replace(/^[\s\-:|]+|[\s\-:|]+$/g, '');
        return { title: s, year: year };
    }

    function plain(html) {
        return String(html || '').replace(/<[^>]*>/g, ' ').replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#0?39;|&apos;/g, "'").replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim();
    }

    function norm(s) { return String(s || '').toLowerCase().replace(/[^a-z0-9]/g, ''); }

    function getFree(url) {
        return Net.json(url, null, { timeout: 10000, retries: 1, ttl: TTL, persist: true, scope: 'detail' });
    }

    function hasAr(s) { return /[\u0600-\u06FF]/.test(s); }

    function wiki(title, kind) {
        var lg = hasAr(title) ? 'ar' : 'en';
        var q = lg === 'ar' ? title + (kind === 'series' ? ' مسلسل' : ' فيلم') : title + (kind === 'series' ? ' TV series' : ' film');
        var url = 'https://' + lg + '.wikipedia.org/w/api.php?action=query&format=json&origin=*&generator=search&gsrlimit=3&prop=extracts&exintro=1&explaintext=1&exlimit=3&exchars=600&gsrsearch=' + encodeURIComponent(q);
        return getFree(url).then(function (r) {
            var pages = r && r.query && r.query.pages;
            if (!pages) return {};
            var list = Object.keys(pages).map(function (k) { return pages[k]; }).sort(function (a, b) { return (a.index || 0) - (b.index || 0); });
            var want = norm(title);
            var hit = null;
            for (var i = 0; i < list.length; i++) {
                var n = norm(String(list[i].title || '').replace(/\(.*\)/g, ''));
                if (want && n && (n.indexOf(want) >= 0 || want.indexOf(n) >= 0) && list[i].extract) { hit = list[i]; break; }
            }
            if (!hit) return {};
            return { plot: String(hit.extract).replace(/\s+/g, ' ').trim(), year: '', rating: '', genre: '', cast: '' };
        });
    }

    function lookupFree(it) {
        var p = parse(it.name);
        var year = p.year || (it.year ? String(it.year) : '');
        if (!p.title) return Promise.resolve({});
        var swallow = function (e) {
            if (e && e.aborted) throw e;
            return {};
        };
        var viaWiki = function (d) {
            if (d && d.plot) return d;
            return wiki(p.title, it.kind).then(function (w) {
                if (w && w.plot) { w.year = (d && d.year) || w.year; w.rating = (d && d.rating) || ''; w.genre = (d && d.genre) || ''; return w; }
                return d || {};
            }, swallow);
        };
        if (it.kind === 'series') {
            return getFree('https://api.tvmaze.com/search/shows?q=' + encodeURIComponent(p.title)).then(function (list) {
                var want = norm(p.title);
                var hit = null;
                list = list || [];
                for (var i = 0; i < list.length; i++) {
                    var sh = list[i].show || {};
                    if (norm(sh.name) === want) { hit = sh; break; }
                }
                if (!hit && list[0] && list[0].show && want && norm(list[0].show.name).indexOf(want) >= 0) hit = list[0].show;
                if (!hit) return {};
                return {
                    plot: plain(hit.summary),
                    year: String(hit.premiered || '').slice(0, 4),
                    rating: hit.rating && hit.rating.average ? String(hit.rating.average) : '',
                    genre: (hit.genres || []).slice(0, 3).join(', '),
                    cast: ''
                };
            }, swallow).then(viaWiki);
        }
        var want = norm(p.title);
        if (!want) return wiki(p.title, it.kind).then(null, swallow);
        return getFree('https://itunes.apple.com/search?media=movie&entity=movie&limit=10&term=' + encodeURIComponent(p.title)).then(function (r) {
            var list = (r && r.results) || [];
            var hit = null;
            for (var i = 0; i < list.length; i++) {
                if (norm(list[i].trackName) !== want) continue;
                var y = String(list[i].releaseDate || '').slice(0, 4);
                if (!year || y === year) { hit = list[i]; break; }
                if (!hit) hit = list[i];
            }
            if (!hit) return {};
            return {
                plot: String(hit.longDescription || hit.shortDescription || '').replace(/\s+/g, ' ').trim(),
                year: String(hit.releaseDate || '').slice(0, 4),
                rating: '',
                genre: hit.primaryGenreName || '',
                cast: ''
            };
        }, swallow).then(viaWiki);
    }

    function lookupKey(it) {
        var isTv = it.kind === 'series';
        var type = isTv ? 'tv' : 'movie';
        var p = parse(it.name);
        var year = p.year || (it.year ? String(it.year) : '');
        if (!p.title) return Promise.resolve({});
        var L = lang();

        function search(withYear) {
            var params = { query: p.title, language: L, include_adult: 'false' };
            if (withYear && year) params[isTv ? 'first_air_date_year' : 'year'] = year;
            return call('/search/' + type, params).then(function (r) {
                return (r && r.results && r.results[0]) || null;
            });
        }

        return search(true).then(function (hit) {
            return hit || (year ? search(false) : null);
        }).then(function (hit) {
            if (!hit) return null;
            return call('/' + type + '/' + hit.id, { language: L, append_to_response: 'credits' }).then(function (d) {
                if ((d && d.overview) || L === 'en-US') return d;
                return call('/' + type + '/' + hit.id, { language: 'en-US' }).then(function (en) {
                    d = d || {};
                    d.overview = (en && en.overview) || '';
                    return d;
                }, function (e) {
                    if (e && e.aborted) throw e;
                    return d;
                });
            });
        }).then(function (d) {
            if (!d) return {};
            var date = String(d.release_date || d.first_air_date || '');
            var cast = ((d.credits && d.credits.cast) || []).slice(0, 5).map(function (c) { return c.name; }).join(', ');
            var genre = (d.genres || []).slice(0, 3).map(function (g) { return g.name; }).join(', ');
            return {
                plot: d.overview || '',
                year: date.slice(0, 4),
                rating: d.vote_average ? (Math.round(d.vote_average * 10) / 10).toFixed(1) : '',
                genre: genre,
                cast: cast
            };
        });
    }

    function lookup(it) {
        if (!enabled()) return lookupFree(it);
        return lookupKey(it).then(function (d) {
            return (d && d.plot) ? d : lookupFree(it);
        });
    }

    return { enabled: enabled, lookup: lookup, parse: parse };
})();
