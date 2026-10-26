(function () {
    'use strict';

    var CFG = window.CONFIG;
    var S = Settings;
    var t = I18n.t;
    var ROWS = 9;
    var SOURCES_KEY = 'lumora.sources';
    var LAST_KEY = 'lumora.last';
    var FAV_KEY = 'lumora.favs';
    var HID_KEY = 'lumora.hidden';
    var POS_KEY = 'lumora.pos';
    var HIST_KEY = 'lumora.hist';
    var PIN_KEY = 'lumora.pin';
    var LOCK_KEY = 'lumora.locked';
    var COUNT_KEY = 'lumora.counts.';
    var CACHE_KEY = 'lumora.c.';
    var EPG_TTL = 300000;
    var MIRROR = {};
    MIRROR[SOURCES_KEY] = 1; MIRROR[FAV_KEY] = 1; MIRROR[POS_KEY] = 1; MIRROR[HIST_KEY] = 1; MIRROR[PIN_KEY] = 1; MIRROR[LOCK_KEY] = 1;

    var KEY = {
        LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40,
        ENTER: 13, BACK: 10009, BACK_ALT: 8, EXIT: 10182,
        STOP: 413, PLAY: 415, PAUSE: 19, PLAYPAUSE: 10252,
        FF: 417, RW: 412, CH_UP: 427, CH_DOWN: 428,
        RED: 403, GREEN: 404, YELLOW: 405, BLUE: 406, INFO: 457,
        IME_DONE: 65376, IME_CANCEL: 65385
    };

    var TYPE_ORDER = ['m3u', 'xtream', 'stalker'];
    var TYPE_LABEL = { m3u: 'M3U', xtream: 'XTREAM', stalker: 'STALKER' };
    var TYPE_NAME = { m3u: 'M3U Playlist', xtream: 'Xtream Codes', stalker: 'Stalker Portal' };
    var KINDS = ['live', 'vod', 'series'];
    var HUB = ['live', 'vod', 'series', 'favs', 'cont'];
    var KIND_META = {
        live: { title: 'Live TV', icon: 'i-tv', noun: 'channels', art: 'images/tile-live.png' },
        vod: { title: 'Movies', icon: 'i-film', noun: 'movies', art: 'images/tile-vod.png' },
        series: { title: 'Series', icon: 'i-series', noun: 'series', art: 'images/tile-series.png' },
        favs: { title: 'Favorites', icon: 'i-star', noun: 'favorites', art: 'images/tile-favs.png' },
        cont: { title: 'Continue', icon: 'i-film', noun: 'items', art: 'images/tile-vod.png' }
    };
    var FAV_KIND = { live: 'Live TV', vod: 'Movies', series: 'Series', ep: 'Series' };
    var PH_LOGO = 'images/ph-logo.png';
    var ASPECTS = ['fit', 'fill', 'zoom'];
    var ASPECT_LABEL = { fit: 'Fit', fill: 'Stretch', zoom: 'Zoom' };
    var SLEEP_STEPS = [0, 15, 30, 60, 90, 120];

    var FIELDS = {
        m3u: [
            { key: 'name', label: 'Name', kind: 'text', placeholder: 'My playlist' },
            { key: 'url', label: 'Playlist URL', kind: 'text', placeholder: 'http://host/playlist.m3u' }
        ],
        xtream: [
            { key: 'name', label: 'Name', kind: 'text', placeholder: 'My IPTV' },
            { key: 'url', label: 'Server URL', kind: 'text', placeholder: 'http://server.example:8080' },
            { key: 'user', label: 'Username', kind: 'text' },
            { key: 'pass', label: 'Password', kind: 'text' },
            { key: 'format', label: 'Live stream format', kind: 'cycle', options: ['m3u8', 'ts'] }
        ],
        stalker: [
            { key: 'name', label: 'Name', kind: 'text', placeholder: 'My portal' },
            { key: 'url', label: 'Portal URL', kind: 'text', placeholder: 'http://portal.example/c/' },
            { key: 'mac', label: 'MAC address', kind: 'text' },
            { key: 'proxy', label: 'Cookie proxy URL (optional)', kind: 'text', placeholder: 'https://my-proxy.workers.dev/' }
        ]
    };

    var HINTS = {
        home: [['▲▼◀▶', 'Navigate'], ['OK', 'Open'], ['g', 'Edit'], ['y', 'Settings'], ['b', 'Remove'], ['↩', 'Exit']],
        form: [['▲▼', 'Move'], ['◀▶', 'Change'], ['OK', 'Edit / Confirm'], ['y', 'Show password'], ['r', 'Phone'], ['↩', 'Cancel']],
        hub: [['◀▶', 'Choose'], ['OK', 'Open'], ['▼', 'Sources menu'], ['y', 'Settings'], ['r', 'Clear library'], ['↩', 'Sources']],
        browse: [['▲▼', 'Browse'], ['◀▶', 'Column'], ['OK', 'Play / Open'], ['y', 'Favorite'], ['b', 'Search'], ['g', 'Hide / Move'], ['r', 'Refresh'], ['INFO', 'Lock category'], ['0', 'Main menu'], ['↩', 'Back']],
        player: [['▶', 'Program guide'], ['b', 'Audio / Subtitles'], ['g', 'Picture size'], ['r', 'Sleep timer'], ['y', 'Favorite'], ['0', 'Last channel']],
        settings: [['▲▼', 'Select'], ['◀▶', 'Change'], ['OK', 'Toggle / Run'], ['↩', 'Back']],
        cont: [['▲▼', 'Browse'], ['◀▶', 'Column'], ['OK', 'Play / Open'], ['y', 'Favorite'], ['b', 'Search'], ['g', 'Hide / Move'], ['r', 'Remove from history'], ['↩', 'Back']]
    };

    var PROVIDERS = { m3u: M3U, xtream: Xtream, stalker: Stalker };

    var state = {
        screen: 'home', prev: 'home',
        sources: [], si: 0, confirmDelete: null,
        source: null, hi: 0,
        catCache: {}, epgCache: {}, favorites: {}, pos: {}, hist: {}, hidden: {}, moving: null,
        saveGen: {}, bg: {}, pend: {}, modal: null, raf: 0, fast: false, lastNav: 0, imgTimer: null,
        lastKey: 0, lastKeyAt: 0, rep: 0, step: 1, suspended: false,
        ctx: null, stack: [],
        form: null, fi: 0, fa: 0,
        set: { i: 0, confirm: null },
        playCtx: null, list: [], idx: 0, retries: 0,
        retryTimer: null, zapTimer: null, queryTimer: null, streamToken: 0,
        resumeAt: 0, healthy: false, quiet: false,
        infoTimer: null, toastTimer: null,
        digits: '', digitTimer: null, prevIdx: -1,
        pin: '', locked: {}, unlocked: {}, showPass: false, backup: null,
        tickTimer: null, tickCount: 0,
        sleepMin: 0, sleepAt: 0, clockText: ''
    };

    function $(id) { return document.getElementById(id); }
    function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
    function pad(n) { return n < 10 ? '0' + n : '' + n; }

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    function readJSON(key, fallback) {
        try {
            var v = localStorage.getItem(key);
            return v ? JSON.parse(v) : fallback;
        } catch (e) { return fallback; }
    }

    function purgeLegacy() {
        try {
            var rm = [];
            for (var i = 0; i < localStorage.length; i++) {
                var k = localStorage.key(i);
                if (k && k.indexOf(CACHE_KEY) === 0) rm.push(k);
            }
            rm.forEach(function (k) { localStorage.removeItem(k); });
        } catch (e) { }
    }

    function writeJSON(key, value) {
        var str;
        try { str = JSON.stringify(value); } catch (e) { return false; }
        try { localStorage.setItem(key, str); } catch (e) {
            purgeLegacy();
            try { localStorage.setItem(key, str); } catch (e2) { }
        }
        if (MIRROR[key]) Store.set('j:' + key, value);
        return true;
    }

    function restoreMirrors() {
        Object.keys(MIRROR).forEach(function (key) {
            var has = null;
            try { has = localStorage.getItem(key); } catch (e) { }
            if (has !== null) return;
            Store.get('j:' + key).then(function (v) {
                if (v === null || typeof v !== 'object') return;
                try { localStorage.setItem(key, JSON.stringify(v)); } catch (e) { }
                if (key === SOURCES_KEY && Array.isArray(v) && !state.sources.length) {
                    state.sources = v;
                    if (state.screen === 'home') renderHome();
                } else if (key === FAV_KEY && !Array.isArray(v)) state.favorites = v;
                else if (key === POS_KEY && !Array.isArray(v)) state.pos = v;
                else if (key === HIST_KEY && !Array.isArray(v)) state.hist = v;
                else if (key === PIN_KEY && v && v.h) state.pin = v.h;
                else if (key === LOCK_KEY && !Array.isArray(v)) state.locked = v;
            });
        });
    }
    var DIACRITICS = /[\u0300-\u036f\u0610-\u061a\u064b-\u065f\u0670\u06d6-\u06ed\u0640]/g;
    var AR_DIGITS = '٠١٢٣٤٥٦٧٨٩';

    function norm(s) {
        s = String(s == null ? '' : s).toLowerCase();
        if (s.normalize) { try { s = s.normalize('NFD'); } catch (e) { } }
        s = s.replace(DIACRITICS, '')
            .replace(/[أإآٱ]/g, 'ا').replace(/ى/g, 'ي').replace(/ة/g, 'ه')
            .replace(/[٠-٩]/g, function (c) { return String(AR_DIGITS.indexOf(c)); })
            .replace(/[_\-.|:\/\\\[\]()]+/g, ' ')
            .replace(/\s+/g, ' ');
        return s.trim();
    }

    function safeLogo(url) { return url && /^https?:/i.test(url) ? url : ''; }
    function hostOf(s) { return (s.url || '').replace(/^https?:\/\//i, '').split('/')[0]; }
    function initial(name) { return (name || '?').trim().charAt(0).toUpperCase() || '?'; }
    function isVod(it) { return !!it && (it.kind === 'vod' || it.kind === 'ep'); }
    function keyOf(src, it) { return src.id + ':' + it.kind + ':' + it.id; }
    function prov() { return PROVIDERS[state.source.type]; }
    function errText(err) { return t((err && err.message) || 'Could not load this content.', err && err.params); }

    function fmt(ms) {
        var s = Math.floor((ms || 0) / 1000);
        var h = Math.floor(s / 3600);
        var m = Math.floor((s % 3600) / 60);
        var sec = s % 60;
        return h ? h + ':' + pad(m) + ':' + pad(sec) : m + ':' + pad(sec);
    }

    function forgetAll(id) {
        Object.keys(PROVIDERS).forEach(function (k) { PROVIDERS[k].forget(id); });
    }

    function countLabel(kind, n) {
        return t('{n} ' + KIND_META[kind].noun, { n: n });
    }

    function toast(msg, isErr) {
        var el = $('toast');
        el.textContent = msg;
        el.classList.toggle('err', !!isErr);
        el.classList.remove('hidden');
        clearTimeout(state.toastTimer);
        state.toastTimer = setTimeout(function () { el.classList.add('hidden'); }, 2800);
    }

    function showLoading(on, text) {
        var sub = $('loading-sub');
        if (text) $('loading-text').textContent = t(text);
        clearInterval(state.loadTick);
        state.loadTick = null;
        if (on) {
            if (!state.loadingOn) state.loadSince = Date.now();
            state.loadingOn = true;
            sub.textContent = '';
            sub.classList.add('hidden');
            state.loadTick = setInterval(function () {
                var ms = Date.now() - state.loadSince;
                if (ms >= CFG.LOADING_SLOW_MS) {
                    sub.textContent = t('Still working… press BACK to cancel') + '  (' + Math.floor(ms / 1000) + ')';
                    sub.classList.remove('hidden');
                }
            }, 1000);
        } else {
            state.loadingOn = false;
            sub.classList.add('hidden');
        }
        $('loading').classList.toggle('hidden', !on);
    }

    function exitApp() {
        try { tizen.application.getCurrentApplication().exit(); } catch (e) { window.close(); }
    }

    function screenSaver(on) {
        try {
            var ac = window.webapis && window.webapis.appcommon;
            if (!ac || !ac.setScreenSaver) return;
            ac.setScreenSaver(on ? ac.AppCommonScreenSaverState.SCREEN_SAVER_ON : ac.AppCommonScreenSaverState.SCREEN_SAVER_OFF, function () { }, function () { });
        } catch (e) { }
    }

    function reveal(box, el, top) {
        if (!box || !el) return;
        if (top) { box.scrollTop = 0; return; }
        var a = el.offsetTop;
        var b = a + el.offsetHeight;
        var pad2 = 28;
        if (a - pad2 < box.scrollTop) box.scrollTop = Math.max(0, a - pad2);
        else if (b + pad2 > box.scrollTop + box.clientHeight) box.scrollTop = b + pad2 - box.clientHeight;
    }

    function setCursor(listEl, idx) {
        var kids = listEl.children;
        for (var i = 0; i < kids.length; i++) kids[i].classList.toggle('cursor', i === idx);
    }
    function renderHints() {
        var footers = document.querySelectorAll('.hints');
        for (var i = 0; i < footers.length; i++) {
            var list = HINTS[footers[i].getAttribute('data-h')] || [];
            var html = '';
            list.forEach(function (h) {
                var chip = h[0];
                var c = (chip === 'r' || chip === 'g' || chip === 'y' || chip === 'b')
                    ? '<span class="k c ' + chip + '"></span>'
                    : '<span class="k">' + chip + '</span>';
                html += '<span class="h">' + c + '<span>' + esc(t(h[1])) + '</span></span>';
            });
            footers[i].innerHTML = html;
        }
    }

    function showScreen(name) {
        try { if (document.activeElement && document.activeElement.tagName === 'INPUT') document.activeElement.blur(); } catch (e) { }
        hideModal();
        if (name === 'home') state.unlocked = {};
        if (name === 'settings' && state.screen !== 'settings') state.prev = state.screen;
        state.screen = name;
        ['home', 'form', 'hub', 'browse', 'settings'].forEach(function (id) {
            var el = $(id);
            var on = id === name;
            el.classList.toggle('hidden', !on);
            if (on) {
                el.classList.remove('enter');
                void el.offsetWidth;
                el.classList.add('enter');
            }
        });
        $('player-ui').classList.toggle('hidden', name !== 'player');
        renderBg();
        document.documentElement.classList.toggle('playing', name === 'player');
        if (name !== 'player') { hideInfo(); closeChanList(); }
        if (name !== 'form') Net.cancel('test');
        showLoading(false);
    }

    function clockString() {
        var mode = S.get('clock');
        if (mode === 'off') return '';
        var d = new Date();
        if (mode === '12') {
            var h = d.getHours();
            var ap = h >= 12 ? (I18n.isRtl() ? 'م' : 'PM') : (I18n.isRtl() ? 'ص' : 'AM');
            h = h % 12 || 12;
            return h + ':' + pad(d.getMinutes()) + ' ' + ap;
        }
        return pad(d.getHours()) + ':' + pad(d.getMinutes());
    }

    function renderClock() {
        var text = clockString();
        state.clockText = text;
        var els = document.querySelectorAll('.clock');
        for (var i = 0; i < els.length; i++) {
            els[i].textContent = text;
            els[i].classList.toggle('hidden', !text);
        }
    }

    function applyAppearance() {
        var root = document.documentElement;
        root.setAttribute('data-theme', S.get('theme'));
        root.classList.toggle('noanim', !S.get('animations'));
        I18n.setLang(S.get('lang'));
        I18n.apply();
        renderHints();
        renderClock();
    }

    function registerKeys() {
        var tv = window.tizen && window.tizen.tvinputdevice;
        if (!tv) return;
        ['MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaStop', 'MediaRewind', 'MediaFastForward',
         'ChannelUp', 'ChannelDown', 'ColorF0Red', 'ColorF1Green',
         'ColorF2Yellow', 'ColorF3Blue', 'Info', 'MediaTrackPrevious', 'MediaTrackNext',
         '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'].forEach(function (k) {
            try { tv.registerKey(k); } catch (e) { }
        });
    }
    function clearCache(id) {
        KINDS.forEach(function (k) { delete state.catCache[id + ':' + k]; });
        Catalog.forget(id);
        try { localStorage.removeItem(COUNT_KEY + id); } catch (e) { }
    }

    function remember(src, kind, items) {
        state.catCache[src.id + ':' + kind] = items;
        var counts = readJSON(COUNT_KEY + src.id, {});
        counts[kind] = items.length;
        writeJSON(COUNT_KEY + src.id, counts);
    }

    Catalog.listen(remember);

    function staleCheck(src, kind, at) {
        if (!S.get('bgRefresh') || !at) return;
        if (Date.now() - at < S.get('cacheHours') * 3600000) return;
        var key = src.id + ':' + kind;
        if (state.bg[key]) return;
        state.bg[key] = setTimeout(function () {
            PROVIDERS[src.type].catalog(src, kind, { force: true }).then(function () {
                delete state.bg[key];
                if (state.screen === 'hub' && state.source && state.source.id === src.id) renderHub();
            }, function () { delete state.bg[key]; });
        }, 5000);
    }

    function getCatalog(src, kind, force) {
        var key = src.id + ':' + kind;
        if (!force && state.catCache[key]) return Promise.resolve(state.catCache[key]);
        if (!force && state.pend[key]) return state.pend[key];
        var p = PROVIDERS[src.type].catalog(src, kind, { force: !!force }).then(function (items) {
            state.catCache[key] = items;
            staleCheck(src, kind, Catalog.age(src, kind));
            return items;
        });
        state.pend[key] = p;
        function done() { if (state.pend[key] === p) delete state.pend[key]; }
        p.then(done, done);
        return p;
    }

    function warm(src) {
        setTimeout(function () {
            if (state.source !== src) return;
            var key = src.id + ':live';
            if (state.catCache[key] || state.pend[key]) return;
            Catalog.peek(src, 'live').then(function (items) {
                if (!items || state.catCache[key]) return;
                state.catCache[key] = items;
                staleCheck(src, 'live', Catalog.age(src, 'live'));
            });
        }, 600);
    }
    function homeTotal() { return state.sources.length + 4; }

    function renderHome() {
        var list = state.sources;
        var html = '';
        list.forEach(function (s) {
            var warn = state.confirmDelete === s.id ? '<span class="warn">' + esc(t('Press BLUE again to remove')) + '</span>' : '';
            html += '<li class="src hg g24"><span class="av">' + esc(initial(s.name)) + '</span>' +
                '<div class="src-main"><div class="src-name">' + esc(s.name) + '</div>' +
                '<div class="src-host">' + esc(hostOf(s)) + '</div>' + warn + '</div>' +
                '<span class="badge">' + TYPE_LABEL[s.type] + '</span></li>';
        });
        html += '<li class="src tool hg g14"><svg><use href="#i-plus"/></svg><span>' + esc(t('Add source')) + '</span></li>';
        html += '<li class="src tool hg g14"><span style="font-size:34px">☰</span><span>' + esc(t('Sources menu')) + '</span></li>';
        html += '<li class="src tool hg g14"><svg><use href="#i-gear"/></svg><span>' + esc(t('Settings')) + '</span></li>';
        html += '<li class="src tool hg g14"><svg><use href="#i-tv"/></svg><span>' + esc(t('Link phone')) + '</span></li>';
        $('source-list').innerHTML = html;
        $('home-empty').classList.toggle('hidden', list.length > 0);
        state.si = clamp(state.si, 0, homeTotal() - 1);
        setCursor($('source-list'), state.si);
        revealHome();
    }

    function revealHome() {
        var el = $('source-list').children[state.si];
        reveal($('home-box'), el, state.si < 2);
    }

    function goHome() {
        stopBg();
        try { Player.release(); } catch (e) { }
        leaveCtx(state.ctx);
        state.ctx = null;
        state.stack = [];
        state.source = null;
        showScreen('home');
        renderHome();
    }

    function moveHome(dx, dy) {
        var total = homeTotal();
        var ni = clamp(state.si + dx + dy * 2, 0, total - 1);
        if (state.confirmDelete) { state.confirmDelete = null; state.si = ni; renderHome(); return; }
        state.si = ni;
        setCursor($('source-list'), state.si);
        revealHome();
    }

    function onHomeKey(k) {
        var n = state.sources.length;
        switch (k) {
            case KEY.LEFT: moveHome(-1, 0); break;
            case KEY.RIGHT: moveHome(1, 0); break;
            case KEY.UP: moveHome(0, -1); break;
            case KEY.DOWN: moveHome(0, 1); break;
            case KEY.ENTER:
                if (state.si < n) openSource(state.sources[state.si]);
                else if (state.si === n) openForm(null);
                else if (state.si === n + 1) openManage(state.sources[0] || null);
                else if (state.si === n + 2) openSettings();
                else openLink();
                break;
            case KEY.GREEN: if (state.si < n) openForm(state.sources[state.si]); break;
            case KEY.YELLOW: openSettings(); break;
            case KEY.BLUE:
                if (state.si >= n) break;
                var s = state.sources[state.si];
                if (state.confirmDelete === s.id) deleteSource(s);
                else { state.confirmDelete = s.id; renderHome(); }
                break;
            case KEY.BACK: case KEY.BACK_ALT:
                showModal({
                    title: 'Exit Lumora?',
                    name: '',
                    opts: [['Exit', ''], ['Cancel', '']],
                    onPick: function (i) { if (i === 0) exitApp(); }
                });
                break;
            case KEY.EXIT: exitApp(); break;
        }
    }

    function deleteSource(s) {
        state.sources = state.sources.filter(function (x) { return x.id !== s.id; });
        writeJSON(SOURCES_KEY, state.sources);
        clearCache(s.id);
        forgetAll(s.id);
        state.confirmDelete = null;
        renderHome();
        toast(t('Source removed'));
    }
    function openForm(src) {
        var values = src
            ? { name: src.name, url: src.url, user: src.user, pass: src.pass, format: src.format, mac: src.mac, proxy: src.proxy || '' }
            : { name: '', url: '', user: '', pass: '', format: 'm3u8', mac: Stalker.makeMac(), proxy: '' };
        state.showPass = false;
        state.form = { id: src ? src.id : null, isNew: !src, type: src ? src.type : 'm3u', values: values };
        state.fi = 0;
        state.fa = 0;
        showScreen('form');
        renderForm();
    }

    function formActions() {
        return state.form && !state.form.isNew
            ? [['save', 'Replace'], ['new', 'Add as new'], ['phone', 'Phone'], ['test', 'Test connection'], ['cancel', 'Cancel']]
            : [['save', 'Save'], ['phone', 'Phone'], ['test', 'Test connection'], ['cancel', 'Cancel']];
    }

    function formRows() {
        var f = state.form;
        var rows = [{ kind: 'type' }];
        FIELDS[f.type].forEach(function (fd) {
            rows.push({ kind: fd.kind, key: fd.key, label: fd.label, options: fd.options, placeholder: fd.placeholder });
        });
        rows.push({ kind: 'actions' });
        return rows;
    }

    function renderForm() {
        var f = state.form;
        var rows = formRows();
        state.fi = clamp(state.fi, 0, rows.length - 1);
        var html = '';
        rows.forEach(function (r, i) {
            var focus = i === state.fi;
            var cls = 'row' + (focus ? ' cursor' : '');
            var body;
            if (r.kind === 'type') {
                body = '<label>' + esc(t('Source type')) + '</label><div class="value">' + esc(t(TYPE_NAME[f.type])) + ' <span class="hint">◀ ▶</span></div>';
            } else if (r.kind === 'text') {
                body = '<label>' + esc(t(r.label)) + '</label>' +
                    '<input type="' + (r.key === 'pass' && !state.showPass ? 'password' : 'text') + '" autocomplete="off" spellcheck="false" data-key="' + r.key + '"' +
                    ' placeholder="' + esc(r.placeholder ? t(r.placeholder) : '') + '" value="' + esc(f.values[r.key] || '') + '">';
            } else if (r.kind === 'cycle') {
                body = '<label>' + esc(t(r.label)) + '</label><div class="value">' + esc(f.values[r.key]) + ' <span class="hint">OK</span></div>';
            } else {
                cls = 'row actions hg g16';
                var names = formActions().map(function (a) { return a[1]; });
                body = '';
                names.forEach(function (n, j) {
                    body += '<span class="btn' + (j === 0 ? ' primary' : '') + (focus && state.fa === j ? ' sel' : '') + '">' + esc(t(n)) + '</span>';
                });
            }
            html += '<div class="' + cls + '">' + body + '</div>';
        });
        $('form-rows').innerHTML = html;
        $('form-title').textContent = t(f.isNew ? 'Add source' : 'Edit source');
        var inputs = $('form-rows').querySelectorAll('input');
        for (var j = 0; j < inputs.length; j++) {
            (function (inp) {
                inp.addEventListener('input', function () {
                    f.values[inp.getAttribute('data-key')] = inp.value;
                });
            })(inputs[j]);
        }
        reveal($('form-box'), $('form-rows').children[state.fi], state.fi === 0);
    }

    function formMove(delta, focusText) {
        state.fi = clamp(state.fi + delta, 0, formRows().length - 1);
        renderForm();
        if (focusText) focusInput();
    }

    function focusInput() {
        var el = document.querySelector('#form-rows .row:nth-child(' + (state.fi + 1) + ') input');
        if (el) el.focus();
    }

    function cycleType(delta) {
        var f = state.form;
        var idx = TYPE_ORDER.indexOf(f.type);
        f.type = TYPE_ORDER[(idx + delta + TYPE_ORDER.length) % TYPE_ORDER.length];
        if (f.type === 'stalker' && !f.values.mac) f.values.mac = Stalker.makeMac();
        renderForm();
    }

    function cycleOption(row, delta) {
        var f = state.form;
        var field = FIELDS[f.type].filter(function (fd) { return fd.key === row.key; })[0];
        if (!field || !field.options) return;
        var idx = field.options.indexOf(f.values[row.key]);
        f.values[row.key] = field.options[(idx + delta + field.options.length) % field.options.length];
        renderForm();
    }

    function readForm() {
        var f = state.form;
        var v = f.values;
        var V = Validate;

        v.name = V.name(v.name);
        v.url = V.clean(v.url);
        v.user = V.clean(v.user);

        if (f.type === 'xtream') {
            var px = V.parseXtream(v.url);
            if (px) {
                v.url = px.url;
                if (!v.user) v.user = px.user;
                if (!v.pass) v.pass = px.pass;
            }
        }

        var u = f.type === 'xtream' ? V.server(v.url) : V.httpUrl(v.url);
        if (!u.ok) { toast(t(u.error), true); return null; }
        v.url = u.value;

        if (f.type === 'xtream') {
            var ru = V.user(v.user);
            if (!ru.ok) { toast(t(ru.error), true); return null; }
            var rp = V.pass(v.pass);
            if (!rp.ok) { toast(t(rp.error), true); return null; }
            v.user = ru.value;
            v.pass = rp.value;
        }

        if (f.type === 'stalker') {
            var rm = V.mac(v.mac);
            if (!rm.ok) { toast(t(rm.error), true); return null; }
            v.mac = rm.value;
            v.proxy = V.clean(v.proxy);
            if (v.proxy) {
                var rx = V.httpUrl(v.proxy);
                if (!rx.ok) { toast(t(rx.error), true); return null; }
                v.proxy = rx.value;
            }
        }

        return {
            id: f.id || ('s' + Date.now().toString(36)),
            type: f.type,
            name: v.name || hostOf({ url: v.url }),
            url: v.url,
            user: f.type === 'xtream' ? v.user : '',
            pass: f.type === 'xtream' ? (v.pass || '') : '',
            format: v.format || 'm3u8',
            mac: f.type === 'stalker' ? v.mac : '',
            proxy: f.type === 'stalker' ? (v.proxy || '') : ''
        };
    }

    function saveForm(asNew) {
        var src = readForm();
        if (!src) return;
        if (asNew) {
            src.id = 's' + Date.now().toString(36);
            var taken = state.sources.map(function (x) { return x.name; });
            var base = src.name, n = 2;
            while (taken.indexOf(src.name) >= 0) src.name = base + ' (' + (n++) + ')';
        }
        forgetAll(src.id);
        clearCache(src.id);
        var idx = state.sources.findIndex(function (s) { return s.id === src.id; });
        if (idx >= 0) state.sources[idx] = src;
        else state.sources.push(src);
        writeJSON(SOURCES_KEY, state.sources);
        state.si = idx >= 0 ? idx : state.sources.length - 1;
        goHome();
        toast(t('Source saved'));
    }

    function pad2date(sec) {
        var d = new Date(parseInt(sec, 10) * 1000);
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
    }

    function testForm() {
        var src = readForm();
        if (!src) return;
        src.id = '__test';
        var tok = state.testTok = (state.testTok || 0) + 1;
        showLoading(true, 'Testing connection…');
        var p;
        if (src.type === 'xtream') {
            p = Xtream.login(src).then(function (info) {
                var ui = (info && info.user_info) || {};
                var extra = /^\d+$/.test(String(ui.exp_date || '')) ? '  ·  ' + t('Expires') + ' ' + pad2date(ui.exp_date) : '';
                return t('Connection successful') + extra;
            });
        } else if (src.type === 'stalker') {
            p = Stalker.login(src).then(function () {
                Stalker.forget('__test');
                return t('Connection successful');
            });
        } else {
            p = M3U.test(src).then(function (n) {
                return t('Connection successful') + '  ·  ' + n + ' ' + t('items');
            });
        }
        var guard = new Promise(function (resolve, reject) {
            setTimeout(function () { reject(new Error('Request timed out')); }, CFG.TEST_TIMEOUT_MS);
        });
        Promise.race([p, guard]).then(function (msg) {
            if (state.screen !== 'form' || tok !== state.testTok) return;
            showLoading(false);
            toast(msg);
        }).catch(function (err) {
            if (state.screen !== 'form' || tok !== state.testTok) return;
            if (err && err.aborted) return;
            showLoading(false);
            toast(errText(err), true);
        });
    }

    function onFormKey(k) {
        if (state.loadingOn) {
            if (k === KEY.BACK || k === KEY.BACK_ALT) {
                state.testTok = (state.testTok || 0) + 1;
                Net.cancel('test');
                showLoading(false);
                toast(t('Cancelled'));
            }
            return;
        }
        var row = formRows()[state.fi];
        switch (k) {
            case KEY.UP: formMove(-1, false); break;
            case KEY.DOWN: formMove(1, false); break;
            case KEY.LEFT:
                if (row.kind === 'type') cycleType(-1);
                else if (row.kind === 'cycle') cycleOption(row, -1);
                else if (row.kind === 'actions') { state.fa = clamp(state.fa - 1, 0, formActions().length - 1); renderForm(); }
                break;
            case KEY.RIGHT:
                if (row.kind === 'type') cycleType(1);
                else if (row.kind === 'cycle') cycleOption(row, 1);
                else if (row.kind === 'actions') { state.fa = clamp(state.fa + 1, 0, formActions().length - 1); renderForm(); }
                break;
            case KEY.ENTER:
                if (row.kind === 'type') cycleType(1);
                else if (row.kind === 'cycle') cycleOption(row, 1);
                else if (row.kind === 'actions') {
                    var act = (formActions()[state.fa] || ['cancel'])[0];
                    if (act === 'save') saveForm(false);
                    else if (act === 'new') saveForm(true);
                    else if (act === 'phone') openQr();
                    else if (act === 'test') testForm();
                    else goHome();
                } else focusInput();
                break;
            case KEY.YELLOW:
                state.showPass = !state.showPass;
                toast(t(state.showPass ? 'Show password' : 'Hide password'));
                renderForm();
                break;
            case KEY.RED: openQr(); break;
            case KEY.BACK: case KEY.BACK_ALT: goHome(); break;
        }
    }

    function closeQr() {
        clearTimeout(state.qrTimer);
        state.qrTok = (state.qrTok || 0) + 1;
        state.qr = false;
        $('qr').classList.add('hidden');
    }

    function applyRemote(msg) {
        var f = state.form;
        if (!f) return false;
        var d = null;
        try { d = JSON.parse(msg); } catch (e) { }
        if (!d || typeof d !== 'object') {
            var s = String(msg || '').trim();
            var px = Validate.parseXtream(s);
            if (px) d = { type: 'xtream', url: px.url, user: px.user, pass: px.pass };
            else if (/^https?:\/\//i.test(s)) d = { type: 'm3u', url: s };
            else return false;
        }
        if (TYPE_ORDER.indexOf(d.type) < 0) return false;
        f.type = d.type;
        ['name', 'url', 'user', 'pass', 'mac', 'format', 'proxy'].forEach(function (k) {
            if (typeof d[k] === 'string' && d[k]) f.values[k] = d[k].slice(0, 2048);
        });
        if (f.type === 'stalker' && !f.values.mac) f.values.mac = Stalker.makeMac();
        if (f.type === 'xtream' && ['m3u8', 'ts'].indexOf(f.values.format) < 0) f.values.format = 'm3u8';
        renderForm();
        return true;
    }

    function openQr() {
        var rnd = '';
        try {
            var a = new Uint8Array(12);
            (window.crypto || window.msCrypto).getRandomValues(a);
            for (var i = 0; i < a.length; i++) rnd += (a[i] % 36).toString(36);
        } catch (e) { rnd = Math.random().toString(36).slice(2, 14) + Math.random().toString(36).slice(2, 8); }
        var topic = 'lumora-' + rnd;
        var url = CFG.CONNECT_URL + '?t=' + topic;
        try { QR.draw($('qr-canvas'), url); } catch (e2) { toast(t('Could not load this content.'), true); return; }
        $('qr-url').textContent = url;
        $('qr').classList.remove('hidden');
        state.qr = true;
        var tok = state.qrTok = (state.qrTok || 0) + 1;
        var since = Math.floor(Date.now() / 1000) - 5;
        function poll() {
            if (state.qrTok !== tok) return;
            var x = new XMLHttpRequest();
            x.open('GET', CFG.RELAY + topic + '/json?poll=1&since=' + since, true);
            x.timeout = 10000;
            function next() { if (state.qrTok === tok) state.qrTimer = setTimeout(poll, 3000); }
            x.onload = function () {
                if (state.qrTok !== tok) return;
                if (x.status >= 200 && x.status < 300) {
                    var lines = String(x.responseText || '').split('\n');
                    for (var j = 0; j < lines.length; j++) {
                        if (!lines[j]) continue;
                        var o = null;
                        try { o = JSON.parse(lines[j]); } catch (e3) { }
                        if (o && o.event === 'message' && o.message && applyRemote(o.message)) {
                            closeQr();
                            toast(t('Received from phone'));
                            if (state.form && !state.form.isNew) {
                                showModal({
                                    title: 'Received from phone',
                                    name: state.form.values.name || state.form.values.url || '',
                                    opts: [['Add as new', ''], ['Replace', ''], ['Edit first', '']],
                                    onPick: function (i) {
                                        if (i === 0) saveForm(true);
                                        else if (i === 1) saveForm(false);
                                    }
                                });
                            }
                            return;
                        }
                    }
                }
                next();
            };
            x.onerror = next;
            x.ontimeout = next;
            try { x.send(); } catch (e4) { next(); }
        }
        poll();
    }

    function secOpts() { return [[3, '{n} sec'], [6, '{n} sec'], [10, '{n} sec'], [15, '{n} sec']]; }

    function settingDefs() {
        return [
            { h: 'Phone' },
            { a: 'pair', n: 'Pair with code' },
            { h: 'Appearance' },
            { k: 'lang', n: 'Language', t: 'cycle', raw: true, o: [['en', 'English'], ['ar', 'العربية']] },
            { k: 'theme', n: 'Accent color', t: 'cycle', o: [['violet', 'Violet'], ['ocean', 'Ocean'], ['emerald', 'Emerald'], ['sunset', 'Sunset'], ['rose', 'Rose']] },
            { k: 'clock', n: 'Clock', t: 'cycle', o: [['24', '24-hour'], ['12', '12-hour'], ['off', 'Hidden']] },
            { k: 'animations', n: 'Animations', t: 'toggle' },
            { h: 'Playback' },
            { k: 'aspect', n: 'Picture size', t: 'cycle', o: [['fit', 'Fit'], ['fill', 'Stretch'], ['zoom', 'Zoom']] },
            { k: 'engine', n: 'Player engine', t: 'cycle', o: [['auto', 'Automatic'], ['avplay', 'Samsung AVPlay'], ['html5', 'HTML5 / Hls.js']] },
            { k: 'buffer', n: 'Buffer size', t: 'cycle', o: [['fast', 'Fast start'], ['balanced', 'Balanced'], ['stable', 'Stable']] },
            { k: 'resume', n: 'Resume movies where you left off', t: 'toggle' },
            { k: 'resumeAsk', n: 'Ask before resuming', t: 'toggle' },
            { k: 'autoNext', n: 'Auto-play next episode', t: 'toggle' },
            { k: 'seekStep', n: 'Skip step', t: 'cycle', o: [[10, '{n} sec'], [20, '{n} sec'], [30, '{n} sec']] },
            { k: 'infoTimeout', n: 'Info bar duration', t: 'cycle', o: secOpts() },
            { k: 'retryLimit', n: 'Reconnect attempts', t: 'cycle', o: [[1, '{n}'], [3, '{n}'], [5, '{n}'], [10, '{n}']] },
            { k: 'keepAwake', n: 'Keep screen awake while playing', t: 'toggle' },
            { k: 'diag', n: 'Show playback diagnostics', t: 'toggle' },
            { k: '_sleep', n: 'Sleep timer', t: 'cycle', o: [[0, 'Off'], [15, '{n} min'], [30, '{n} min'], [60, '{n} min'], [90, '{n} min'], [120, '{n} min']] },
            { h: 'Library' },
            { k: 'sort', n: 'Sort items', t: 'cycle', o: [['default', 'Provider order'], ['az', 'A – Z'], ['za', 'Z – A']] },
            { k: 'autoOpenLast', n: 'Open last source on launch', t: 'toggle' },
            { k: 'bgRefresh', n: 'Refresh library in background', t: 'toggle' },
            { k: 'cacheHours', n: 'Library cache lifetime', t: 'cycle', o: [[1, '{n} h'], [6, '{n} h'], [12, '{n} h'], [24, '{n} h'], [72, '{n} h']] },
            { a: 'tmdb', n: 'TMDB API key (descriptions)' },
            { h: 'Security' },
            { k: 'parental', n: 'Parental lock', t: 'toggle' },
            { k: 'lockCats', n: 'Locked categories', t: 'toggle' },
            { a: 'setpin', n: 'Set PIN' },
            { h: 'Backup' },
            { a: 'export', n: 'Export backup' },
            { a: 'import', n: 'Import backup' },
            { h: 'Data' },
            { a: 'cache', n: 'Clear cached libraries' },
            { a: 'favs', n: 'Clear favorites' },
            { a: 'pos', n: 'Clear watch progress' },
            { a: 'reset', n: 'Reset settings' },
            { a: 'wipe', n: 'Erase all data', danger: true },
            { h: 'About' },
            { i: true, n: 'Version', v: 'Lumora ' + CFG.VERSION },
            { i: true, n: 'Saved sources', v: String(state.sources.length) },
            { i: true, n: 'Remote colors', v: t('Shown at the bottom of every screen') }
        ];
    }

    function settingValue(d) {
        return d.k === '_sleep' ? state.sleepMin : S.get(d.k);
    }

    function settingFocusable(defs) {
        var list = [];
        defs.forEach(function (d, i) { if (d.k || d.a) list.push(i); });
        return list;
    }

    function arrows() {
        return I18n.isRtl() ? ['▶', '◀'] : ['◀', '▶'];
    }

    function renderSettings() {
        var defs = settingDefs();
        var foc = settingFocusable(defs);
        state.set.i = clamp(state.set.i, 0, foc.length - 1);
        var cur = foc[state.set.i];
        var ar = arrows();
        var html = '';
        var firstHeader = true;
        defs.forEach(function (d, i) {
            if (d.h) {
                html += '<div class="shead' + (firstHeader ? ' first' : '') + '">' + esc(t(d.h)) + '</div>';
                firstHeader = false;
                return;
            }
            var cls = 'srow' + (i === cur ? ' cursor' : '') + (d.danger ? ' danger' : '') + (d.i ? ' info' : '');
            var val = '';
            if (d.i) {
                val = '<span class="s-val">' + esc(d.v) + '</span>';
            } else if (d.a === 'pair') {
                val = '<span class="s-val">' + (link.phoneAt ? '<span style="color:#4ade80">● </span>' : '') + esc(link.id) + '</span>';
            } else if (d.a === 'tmdb') {
                val = '<span class="s-val">' + esc(t(Tmdb.enabled() ? 'Set' : 'Not set')) + '</span>';
            } else if (d.a) {
                val = state.set.confirm === d.a
                    ? '<span class="confirm">' + esc(t('Press OK again to confirm')) + '</span>'
                    : '<span class="s-val">OK</span>';
            } else if (d.t === 'toggle') {
                val = '<span class="sw' + (settingValue(d) ? ' on' : '') + '"><span class="knob"></span></span>';
            } else {
                var cv = settingValue(d);
                var label = '';
                d.o.forEach(function (o) {
                    if (o[0] === cv) label = d.raw ? o[1] : t(o[1], { n: o[0] });
                });
                val = '<span class="s-val"><span class="arr">' + ar[0] + '</span>' + esc(label) + '<span class="arr">' + ar[1] + '</span></span>';
            }
            html += '<div class="' + cls + '"><span class="s-name">' + esc(t(d.n)) + '</span>' + val + '</div>';
        });
        $('settings-list').innerHTML = html;
        var curEl = $('settings-list').querySelector('.srow.cursor');
        reveal($('settings-box'), curEl, state.set.i === 0);
    }

    function openSettings() {
        state.set.i = 0;
        state.set.confirm = null;
        showScreen('settings');
        renderSettings();
    }

    function closeSettings() {
        state.set.confirm = null;
        var back = state.prev === 'hub' && state.source ? 'hub' : 'home';
        if (back === 'hub') { showScreen('hub'); renderHub(); }
        else { showScreen('home'); renderHome(); }
    }

    function applySetting(k, v) {
        if (k === '_sleep') { setSleep(v); return; }
        S.set(k, v);
        if (k === 'lang' || k === 'theme' || k === 'animations') applyAppearance();
        if (k === 'clock') renderClock();
        if (k === 'aspect') Player.setAspect(v);
        if (k === 'buffer') Player.setProfile(v);
        if (k === 'engine') Player.setEngine(v);
    }

    function changeSetting(d, delta) {
        if ((d.k === 'parental' || d.k === 'lockCats') && S.get('parental') && state.pin) {
            verifyPin(function () { applyChange(d, delta); });
            return;
        }
        if (d.k === 'parental' && !S.get('parental') && !state.pin) {
            setPinFlow(function () { applySetting('parental', true); renderSettings(); });
            return;
        }
        applyChange(d, delta);
    }

    function applyChange(d, delta) {
        var cv = settingValue(d);
        if (d.t === 'toggle') {
            applySetting(d.k, !cv);
        } else {
            var idx = 0;
            d.o.forEach(function (o, i) { if (o[0] === cv) idx = i; });
            idx = (idx + delta + d.o.length) % d.o.length;
            applySetting(d.k, d.o[idx][0]);
        }
        if (d.k === '_sleep') toast(state.sleepMin ? t('Sleep timer: {n} min', { n: state.sleepMin }) : t('Sleep timer off'));
        renderSettings();
    }

    function eraseAll() {
        var keys = [];
        try {
            for (var i = 0; i < localStorage.length; i++) {
                var k = localStorage.key(i);
                if (k && k.indexOf('lumora.') === 0) keys.push(k);
            }
            keys.forEach(function (k) { localStorage.removeItem(k); });
        } catch (e) { }
        S.reset();
        Store.clear();
        state.hist = {};
        Object.keys(state.catCache).forEach(function (k) { delete state.catCache[k]; });
        state.sources.forEach(function (s) { forgetAll(s.id); });
        state.sources = [];
        state.favorites = {};
        state.pos = {};
        state.pin = '';
        state.locked = {};
        state.unlocked = {};
        state.si = 0;
        setSleep(0);
        Player.setAspect('fit');
        Player.setProfile(S.get('buffer'));
        Player.setEngine(S.get('engine'));
        applyAppearance();
    }

    function runAction(a) {
        switch (a) {
            case 'setpin':
                setPinFlow(function () { renderSettings(); });
                break;
            case 'export':
                guardPin(function () { showBackup('export'); });
                break;
            case 'import':
                guardPin(function () { showBackup('import'); });
                break;
            case 'tmdb':
                showBackup('tmdb');
                break;
            case 'pair':
                openLink();
                break;
            case 'cache':
                state.sources.forEach(function (s) { clearCache(s.id); forgetAll(s.id); });
                Net.clear();
                toast(t('Cache cleared'));
                break;
            case 'favs':
                state.favorites = {};
                writeJSON(FAV_KEY, {});
                toast(t('Favorites cleared'));
                break;
            case 'pos':
                state.pos = {};
                state.hist = {};
                writeJSON(POS_KEY, {});
                writeJSON(HIST_KEY, {});
                toast(t('Watch progress cleared'));
                break;
            case 'reset':
                S.reset();
                Player.setAspect(S.get('aspect'));
                Player.setProfile(S.get('buffer'));
                Player.setEngine(S.get('engine'));
                applyAppearance();
                toast(t('Settings reset'));
                break;
            case 'wipe':
                eraseAll();
                toast(t('All data erased'));
                break;
        }
    }

    function onSettingsKey(k) {
        var defs = settingDefs();
        var foc = settingFocusable(defs);
        var d = defs[foc[state.set.i]];
        switch (k) {
            case KEY.UP:
                state.set.confirm = null;
                state.set.i = clamp(state.set.i - 1, 0, foc.length - 1);
                renderSettings();
                break;
            case KEY.DOWN:
                state.set.confirm = null;
                state.set.i = clamp(state.set.i + 1, 0, foc.length - 1);
                renderSettings();
                break;
            case KEY.LEFT: if (d.k) changeSetting(d, -1); break;
            case KEY.RIGHT: if (d.k) changeSetting(d, 1); break;
            case KEY.ENTER:
                if (d.k) changeSetting(d, 1);
                else if (INSTANT[d.a]) runAction(d.a);
                else if (state.set.confirm === d.a) { state.set.confirm = null; runAction(d.a); renderSettings(); }
                else { state.set.confirm = d.a; renderSettings(); }
                break;
            case KEY.BACK: case KEY.BACK_ALT: closeSettings(); break;
            case KEY.EXIT: exitApp(); break;
        }
    }
    function setSleep(min) {
        state.sleepMin = min;
        state.sleepAt = min ? Date.now() + min * 60000 : 0;
    }

    function sleepLeft() {
        return state.sleepAt ? Math.max(1, Math.ceil((state.sleepAt - Date.now()) / 60000)) : 0;
    }

    function secondTick() {
        if (clockString() !== state.clockText) renderClock();
        if (state.sleepAt && Date.now() >= state.sleepAt) {
            setSleep(0);
            if (state.screen === 'player') closePlayer();
            exitApp();
        }
    }
    function openSource(src) {
        if (state.bg && state.bg.srcId !== src.id) stopBg();
        state.source = src;
        state.favorites = readJSON(FAV_KEY, {});
        state.pos = readJSON(POS_KEY, {});
        state.hist = readJSON(HIST_KEY, {});
        state.hidden = readJSON(HID_KEY, {}) || {};
        migrateFavs(src);
        state.ctx = null;
        state.stack = [];
        state.hi = 0;
        try { localStorage.setItem(LAST_KEY, src.id); } catch (e) { }
        showScreen('hub');
        renderHub();
        warm(src);
    }

    function renderHub() {
        var s = state.source;
        $('hub-title').textContent = s.name;
        $('hub-type').textContent = TYPE_LABEL[s.type];
        var counts = readJSON(COUNT_KEY + s.id, {});
        var html = '';
        HUB.forEach(function (k) {
            var m = KIND_META[k];
            var n = k === 'favs' ? favItems(s).length : k === 'cont' ? histItems(s).length : counts[k];
            html += '<li class="tile t-' + k + '"><img class="art" src="' + m.art + '" alt=""><div>' +
                '<div class="tname">' + esc(t(m.title)) + '</div>' +
                '<div class="tcount">' + esc(n === undefined ? t('Press OK to load') : countLabel(k, n)) + '</div></div></li>';
        });
        $('hub-list').innerHTML = html;
        setCursor($('hub-list'), state.hi);
        hubSwitchCursor();
    }

    function hubSwitchCursor() {
        var b = $('hub-switch');
        if (!b) return;
        b.textContent = '☰ ' + t('Sources menu');
        b.classList.toggle('sel', state.hi === -1);
        b.classList.toggle('primary', state.hi === -1);
    }

    function openManage(src) {
        if (!src && state.sources.length) src = state.sources[0];
        var has = !!src;
        var acts = [];
        if (state.sources.length > 1) acts.push(['Switch source', function () { openSwitcher(); }]);
        acts.push(['Add source', function () { openForm(null); }]);
        if (has) {
            acts.push(['Edit source', function () { openForm(src); }]);
            acts.push(['Replace via phone', function () { openForm(src); openQr(); }]);
        }
        acts.push(['Add via phone', function () { openForm(null); openQr(); }]);
        if (has) acts.push(['Remove source', function () {
            showModal({
                title: 'Remove this source?', name: src.name,
                opts: [['Remove', ''], ['Cancel', '']],
                onPick: function (i) { if (i === 0) { deleteSource(src); if (state.screen !== 'home') goHome(); } }
            });
        }]);
        showModal({
            title: 'Sources menu',
            name: has ? src.name : '',
            opts: acts.map(function (a) { return [a[0], '']; }),
            onPick: function (i) { acts[i][1](); }
        });
    }

    function openSwitcher() {
        var list = state.sources;
        if (list.length < 2) { toast(t('Add another source first')); return; }
        var cur = state.source ? state.source.id : null;
        var ci = 0;
        showModal({
            title: 'Switch source',
            name: '',
            opts: list.map(function (s, i) { if (s.id === cur) ci = i; return [(s.id === cur ? '✓ ' : '') + s.name, '']; }),
            onPick: function (i) {
                var s = list[i];
                if (s && (!state.source || s.id !== state.source.id)) openSource(s);
            }
        });
        if (cur && list[ci] && list[ci].id === cur) ci = (ci + 1) % list.length;
        state.modal.i = ci;
        renderModal();
    }

    function onHubKey(k) {
        switch (k) {
            case KEY.GREEN: openManage(state.source); break;
            case KEY.LEFT:
                if (state.hi >= 0) state.hi = clamp(state.hi - 1, 0, HUB.length - 1);
                setCursor($('hub-list'), state.hi); hubSwitchCursor(); break;
            case KEY.RIGHT:
                if (state.hi >= 0) state.hi = clamp(state.hi + 1, 0, HUB.length - 1);
                setCursor($('hub-list'), state.hi); hubSwitchCursor(); break;
            case KEY.DOWN:
                state.hi = state.hi < 0 ? 0 : HUB.length === 0 ? 0 : -1;
                setCursor($('hub-list'), state.hi); hubSwitchCursor(); break;
            case KEY.UP:
                state.hi = state.hi < 0 ? 0 : -1;
                setCursor($('hub-list'), state.hi); hubSwitchCursor(); break;
            case KEY.ENTER:
                if (state.hi < 0) openManage(state.source); else openKind(HUB[state.hi]);
                break;
            case KEY.YELLOW: openSettings(); break;
            case KEY.RED:
                clearCache(state.source.id);
                renderHub();
                toast(t('Library cleared'));
                break;
            case KEY.BACK: case KEY.BACK_ALT: goHome(); break;
            case KEY.EXIT: exitApp(); break;
        }
    }
    var pool = { items: [], groups: [], itemsOn: false, groupsOn: false };

    function makeCtx(kind, title, serie) {
        return {
            kind: kind, title: title, serie: serie || null,
            items: [], groups: [], base: null, gi: 0, ci: 0, ws: 0, gws: 0,
            focus: 'groups', query: '', vis: null, index: null, res: null,
            error: '', loading: true, playKey: ''
        };
    }

    function leaveCtx(ctx) {
        if (ctx && ctx.loading) Net.cancel('cat');
    }

    function openKind(kind) {
        leaveCtx(state.ctx);
        state.stack = [];
        state.ctx = makeCtx(kind, KIND_META[kind].title, null);
        showScreen('browse');
        renderBrowse();
        loadCtx(state.ctx, false);
    }

    function goHub() {
        stopBg();
        try { Player.release(); } catch (e) { }
        leaveCtx(state.ctx);
        state.ctx = null;
        state.stack = [];
        showScreen('hub');
        renderHub();
    }

    function sortItems(items) {
        var dec = new Array(items.length);
        for (var i = 0; i < items.length; i++) dec[i] = { n: String(items[i].name).toLowerCase(), it: items[i] };
        dec.sort(function (a, b) { return a.n < b.n ? -1 : (a.n > b.n ? 1 : 0); });
        return dec.map(function (d) { return d.it; });
    }

    function isLocalKind(kind) { return kind === 'ep' || kind === 'favs' || kind === 'cont'; }

    function loadCtx(ctx, force) {
        var src = state.source;
        if (force) Net.cancel('cat');
        ctx.loading = true;
        ctx.error = '';
        renderBrowse();
        var p = ctx.kind === 'favs' ? Promise.resolve(favItems(src))
            : ctx.kind === 'cont' ? Promise.resolve(histItems(src))
            : ctx.kind === 'ep' ? PROVIDERS[src.type].seriesInfo(src, ctx.serie)
            : getCatalog(src, ctx.kind, force);
        p.then(function (items) {
            if (state.ctx !== ctx) return;
            for (var i = 0; i < items.length; i++) {
                if (!items[i].key) items[i].key = keyOf(src, items[i]);
            }
            var local = isLocalKind(ctx.kind);
            if (!local) upgradeLegacy(items, ctx);
            if (!local && S.get('sort') === 'az') items = sortItems(items);
            else if (!local && S.get('sort') === 'za') items = sortItems(items).reverse();
            ctx.items = items;
            ctx.index = null;
            ctx.res = null;
            ctx.base = null;
            ctx.loading = false;
            ctx.error = items.length ? '' : 'Nothing to show here yet.';
            ctx.gi = 0; ctx.ci = 0; ctx.ws = 0; ctx.gws = 0; ctx.vis = null; ctx.focus = 'groups';
            buildGroups(ctx);
            if (ctx.playKey) {
                var at = -1;
                for (var j = 0; j < items.length; j++) { if (items[j].key === ctx.playKey) { at = j; break; } }
                ctx.playKey = '';
                if (at >= 0) {
                    ctx.focus = 'items';
                    ctx.ci = at;
                    renderBrowse();
                    openPlayer(ctx, items, at);
                    buildIndex(ctx);
                    return;
                }
            }
            renderBrowse();
            buildIndex(ctx);
            if (force && ctx.kind !== 'favs' && ctx.kind !== 'cont') toast(t('Updated: {n} {x}', { n: items.length, x: t(ctx.kind === 'ep' ? 'episodes' : KIND_META[ctx.kind].noun) }));
        }).catch(function (err) {
            if (state.ctx !== ctx) return;
            if (err && err.aborted) return;
            ctx.loading = false;
            ctx.error = errText(err);
            renderBrowse();
        });
    }

    function baseGroups(ctx) {
        var map = Object.create(null);
        var order = [];
        var items = ctx.items;
        for (var i = 0; i < items.length; i++) {
            var g = items[i].group || 'Uncategorized';
            var list = map[g];
            if (!list) { list = map[g] = []; order.push(g); }
            list.push(items[i]);
        }
        return order.map(function (n) { return { name: n, list: map[n] }; });
    }

    function serieIdsInHistory(src) {
        var prefix = src.id + ':ep:';
        var ids = Object.create(null);
        Object.keys(state.hist).forEach(function (key) {
            if (key.indexOf(prefix) !== 0) return;
            var r = state.hist[key];
            if (r && r.serie && r.serie.id !== undefined && state.pos[key]) ids[r.serie.id] = true;
        });
        return ids;
    }

    function buildGroups(ctx) {
        var groups = [];
        if (ctx.kind === 'favs' || ctx.kind === 'cont') {
            groups.push({ name: 'All', list: ctx.items });
            ['live', 'vod', 'series'].forEach(function (k) {
                var list = ctx.items.filter(function (it) { return (it.kind === 'ep' ? 'series' : it.kind) === k; });
                if (!list.length) return;
                groups.push({ name: KIND_META[k].title, list: list });
                if (ctx.kind !== 'favs') return;
                var cats = {};
                var order = [];
                list.forEach(function (it) {
                    var c = it.kind === 'ep' ? (it.serieName || '') : (it.group || '');
                    if (!c) return;
                    if (!cats[c]) { cats[c] = []; order.push(c); }
                    cats[c].push(it);
                });
                if (order.length < 2) return;
                order.sort(function (a, b) { return cats[b].length - cats[a].length || (a < b ? -1 : 1); });
                order.forEach(function (c) {
                    groups.push({ name: KIND_META[k].title + ' · ' + c, list: cats[c] });
                });
            });
            ctx.groups = groups;
            ctx.vis = null;
            return;
        }
        if (!ctx.base) ctx.base = baseGroups(ctx);
        if (ctx.kind !== 'ep') {
            var favs = ctx.items.filter(function (it) { return state.favorites[it.key]; });
            favs.sort(function (x, y) { return (state.favorites[y.key].at || 0) - (state.favorites[x.key].at || 0); });
            if (favs.length) groups.push({ name: '★ Favorites', list: favs });
            if (S.get('resume')) {
                var cont = null;
                if (ctx.kind === 'vod') {
                    cont = ctx.items.filter(function (it) { return state.pos[it.key]; });
                } else if (ctx.kind === 'series') {
                    var ids = serieIdsInHistory(state.source);
                    cont = ctx.items.filter(function (it) { return ids[it.id]; });
                }
                if (cont && cont.length) groups.push({ name: '▶ Continue watching', list: cont });
            }
            groups.push({ name: 'All', list: ctx.items });
        }
        var hid = hiddenMap(ctx);
        var hidN = Object.keys(hid).length;
        var base = hidN ? ctx.base.filter(function (g) { return !hid[g.name]; }) : ctx.base;
        ctx.groups = groups.concat(base);
        if (hidN) {
            var hl = Object.keys(hid).map(function (n) {
                return { id: '__h', key: '__h:' + n, name: n, kind: ctx.kind, group: '', logo: '', hiddenCat: n };
            });
            ctx.groups.push({ name: '⊘ Hidden categories', list: hl, restore: true });
        }
        ctx.vis = null;
    }

    function hiddenKey(ctx) { return (state.source ? state.source.id : '') + ':' + ctx.kind; }

    function hiddenMap(ctx) {
        if (KINDS.indexOf(ctx.kind) < 0) return {};
        return state.hidden[hiddenKey(ctx)] || {};
    }

    function trl(ar, en) { return I18n.isRtl() ? ar : en; }

    function toggleHideGroup() {
        var ctx = state.ctx;
        if (!ctx || KINDS.indexOf(ctx.kind) < 0) return;
        var g = ctx.groups[ctx.gi];
        if (!g) return;
        var old = ctx.gi;
        if (g.restore) {
            var back = Object.keys(state.hidden[hiddenKey(ctx)] || {});
            delete state.hidden[hiddenKey(ctx)];
            writeJSON(HID_KEY, state.hidden);
            rebuildGroups(ctx);
            var at = 0;
            ctx.groups.forEach(function (x, i) { if (!at && back.indexOf(x.name) >= 0) at = i; });
            ctx.gi = at;
            ctx.ci = 0; ctx.ws = 0; ctx.gws = 0; ctx.vis = null; ctx.focus = 'groups';
            toast(trl('تمت إعادة إظهار كل التصنيفات', 'All categories restored'));
            renderBrowse();
            return;
        } else if (ctx.base && ctx.base.indexOf(g) >= 0) {
            var m = state.hidden[hiddenKey(ctx)] || (state.hidden[hiddenKey(ctx)] = {});
            m[g.name] = true;
            toast(trl('تم إخفاء التصنيف — افتح "تصنيفات مخفية" واضغط OK على تصنيف لإرجاعه أو الأخضر لإرجاع الكل', 'Category hidden — open "Hidden categories": OK restores one, GREEN restores all'));
        } else {
            return;
        }
        writeJSON(HID_KEY, state.hidden);
        rebuildGroups(ctx);
        ctx.gi = clamp(old, 0, ctx.groups.length - 1);
        ctx.ci = 0; ctx.ws = 0; ctx.gws = 0; ctx.vis = null;
        renderBrowse();
    }

    function restoreCategory(name) {
        var ctx = state.ctx;
        var key = hiddenKey(ctx);
        var m = state.hidden[key];
        if (!m) return;
        delete m[name];
        if (!Object.keys(m).length) delete state.hidden[key];
        writeJSON(HID_KEY, state.hidden);
        var left = !!state.hidden[key];
        rebuildGroups(ctx);
        if (left) {
            ctx.gi = ctx.groups.length - 1;
            ctx.ci = clamp(ctx.ci, 0, Math.max(0, visibleList(ctx).length - 1));
            ctx.focus = 'items';
        } else {
            var at = 0;
            ctx.groups.forEach(function (g, i) { if (g.name === name) at = i; });
            ctx.gi = at;
            ctx.ci = 0;
            ctx.focus = 'groups';
        }
        ctx.ws = 0; ctx.gws = 0; ctx.vis = null;
        toast(trl('تمت إعادة إظهار التصنيف', 'Category restored'));
        renderBrowse();
    }

    function startMoveFav() {
        var ctx = state.ctx;
        var g = ctx.groups[ctx.gi];
        var it = visibleList(ctx)[ctx.ci];
        if (!it || !state.favorites[it.key] || ctx.query) return false;
        if (!(ctx.kind === 'favs' || (g && g.name === '★ Favorites'))) return false;
        state.moving = it.key;
        toast(trl('وضع النقل: ▲▼ لتحريك المفضلة، OK للتثبيت', 'Move mode: ▲▼ to move, OK to drop'));
        return true;
    }

    function moveFav(delta) {
        var ctx = state.ctx;
        var list = visibleList(ctx);
        var i = ctx.ci;
        var j = i + delta;
        if (j < 0 || j >= list.length) return;
        var a = state.favorites[list[i].key];
        var b = state.favorites[list[j].key];
        if (!a || !b) return;
        var ta = a.at || 0, tb = b.at || 0;
        if (ta === tb) { a.at = tb + (delta < 0 ? 1 : -1); }
        else { a.at = tb; b.at = ta; }
        writeJSON(FAV_KEY, state.favorites);
        var key = state.moving;
        rebuildGroups(ctx);
        var nl = visibleList(ctx);
        for (var k = 0; k < nl.length; k++) { if (nl[k].key === key) { ctx.ci = k; break; } }
        renderBrowse();
    }

    function rebuildGroups(ctx) {
        var cur = ctx.groups[ctx.gi] ? ctx.groups[ctx.gi].name : null;
        if (ctx.kind === 'favs' || ctx.kind === 'cont') {
            ctx.items = ctx.kind === 'favs' ? favItems(state.source) : histItems(state.source);
            ctx.index = null;
            ctx.res = null;
        }
        buildGroups(ctx);
        var found = -1;
        ctx.groups.forEach(function (g, i) { if (g.name === cur) found = i; });
        ctx.gi = found >= 0 ? found : 0;
        ctx.vis = null;
        ctx.ci = clamp(ctx.ci, 0, Math.max(0, visibleList(ctx).length - 1));
        if ((ctx.kind === 'favs' || ctx.kind === 'cont') && ctx.items.length) buildIndex(ctx);
    }
    function buildIndex(ctx) {
        var items = ctx.items;
        var n = items.length;
        var offs = new Uint32Array(n + 1);
        var parts = [];
        var pos = 0;
        var i = 0;
        function step() {
            if (ctx.items !== items) return;
            var t0 = Date.now();
            var buf = [];
            while (i < n) {
                var s = norm(items[i].name);
                offs[i] = pos;
                buf.push(s);
                pos += s.length + 1;
                i++;
                if ((i & 255) === 0 && Date.now() - t0 > 10) break;
            }
            if (buf.length) parts.push(buf.join('\n') + '\n');
            if (i < n) { setTimeout(step, 0); return; }
            offs[n] = pos;
            ctx.index = { text: parts.join(''), offs: offs };
            parts = null;
            if (ctx.query) {
                ctx.vis = null;
                if (state.ctx === ctx && state.screen === 'browse') scheduleRender();
            }
        }
        step();
    }

    function lineIndex(offs, n, p) {
        var lo = 0;
        var hi = n - 1;
        while (lo < hi) {
            var mid = (lo + hi + 1) >> 1;
            if (offs[mid] <= p) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    function fastSearch(ctx, toks, prev) {
        var ix = ctx.index;
        var text = ix.text;
        var offs = ix.offs;
        var n = ctx.items.length;
        var nt = toks.length;
        var t0 = toks[0];
        var b0 = [], b1 = [], b2 = [];
        var k, ok, f, line;
        if (prev) {
            for (var a = 0; a < prev.length; a++) {
                var i = prev[a];
                line = text.substring(offs[i], offs[i + 1] - 1);
                f = line.indexOf(t0);
                if (f < 0) continue;
                ok = true;
                for (k = 1; k < nt; k++) { if (line.indexOf(toks[k]) < 0) { ok = false; break; } }
                if (!ok) continue;
                if (f === 0) b0.push(i); else if (line.charCodeAt(f - 1) === 32) b1.push(i); else b2.push(i);
            }
        } else {
            var p = text.indexOf(t0);
            while (p !== -1) {
                var li = lineIndex(offs, n, p);
                var ls = offs[li];
                var le = offs[li + 1] - 1;
                ok = true;
                if (nt > 1) {
                    line = text.substring(ls, le);
                    for (k = 1; k < nt; k++) { if (line.indexOf(toks[k]) < 0) { ok = false; break; } }
                }
                if (ok) {
                    f = p - ls;
                    if (f === 0) b0.push(li); else if (text.charCodeAt(p - 1) === 32) b1.push(li); else b2.push(li);
                }
                p = text.indexOf(t0, le + 1);
            }
        }
        return b0.concat(b1, b2);
    }

    function slowSearch(ctx, toks) {
        var items = ctx.items;
        var b0 = [], b1 = [], b2 = [];
        var nt = toks.length;
        for (var i = 0; i < items.length; i++) {
            var n = norm(items[i].name);
            var first = n.indexOf(toks[0]);
            if (first < 0) continue;
            var ok = true;
            for (var k = 1; k < nt; k++) { if (n.indexOf(toks[k]) < 0) { ok = false; break; } }
            if (!ok) continue;
            if (first === 0) b0.push(i); else if (n.charAt(first - 1) === ' ') b1.push(i); else b2.push(i);
        }
        return b0.concat(b1, b2);
    }

    function searchItems(ctx, query) {
        var q = norm(query);
        var toks = q.split(' ').filter(Boolean);
        if (!toks.length) return null;
        var idx;
        if (ctx.index) {
            var prev = null;
            if (ctx.res && ctx.res.items === ctx.items && q.indexOf(ctx.res.q) === 0 && ctx.res.q) prev = ctx.res.idx;
            idx = fastSearch(ctx, toks, prev);
        } else {
            idx = slowSearch(ctx, toks);
        }
        ctx.res = { q: q, items: ctx.items, idx: idx };
        var items = ctx.items;
        var out = new Array(idx.length);
        for (var i = 0; i < idx.length; i++) out[i] = items[idx[i]];
        return out;
    }

    function visibleList(ctx) {
        if (ctx.vis) return ctx.vis;
        var list = null;
        if (ctx.query) list = searchItems(ctx, ctx.query);
        if (!list) {
            var g = ctx.groups[ctx.gi];
            list = g ? g.list : [];
        }
        ctx.vis = list;
        return list;
    }
    var COLS = 4;
    var GRID_ROWS = 2;
    function isPosterCtx(ctx) { return !!ctx && (ctx.kind === 'vod' || ctx.kind === 'series'); }
    function rowsNow() { return isPosterCtx(state.ctx) ? COLS * GRID_ROWS : 8; }

    function stableWindow(ws, idx, len) {
        if (isPosterCtx(state.ctx)) {
            var totalRows = Math.ceil(len / COLS);
            if (totalRows <= GRID_ROWS) return 0;
            var wr = Math.floor(ws / COLS);
            var row = Math.floor(idx / COLS);
            if (row < wr) wr = row;
            else if (row > wr + GRID_ROWS - 1) wr = row - GRID_ROWS + 1;
            return clamp(wr, 0, totalRows - GRID_ROWS) * COLS;
        }
        var R = ROWS;
        if (len <= R) return 0;
        if (idx < ws + 1) ws = idx - 1;
        else if (idx > ws + R - 2) ws = idx - R + 2;
        return clamp(ws, 0, len - R);
    }

    function showDetail(it) {
        var d = $('detail');
        if (!it) { d.classList.add('hidden'); return; }
        d.classList.remove('hidden');
        setText($('detail-title'), it.name);
        var m = [];
        if (it.year) m.push(it.year);
        if (it.rating && it.rating !== '0') m.push('★ ' + it.rating);
        if (it.genre) m.push(it.genre);
        setText($('detail-meta'), m.join('  ·  '));
        var canFetch = !it.detailed && (it.kind === 'vod' || it.kind === 'series');
        setText($('detail-plot'), it.plot || (canFetch ? t('Loading…') : t('No description available.')));
        setText($('detail-cast'), it.cast ? t('Cast') + ': ' + String(it.cast).slice(0, 140) : '');
        clearTimeout(state.detTimer);
        if (!it.plot && canFetch) {
            state.detTimer = setTimeout(function () {
                fetchDetail(it, function () {
                    var c = state.ctx;
                    if (state.screen === 'browse' && c && visibleList(c)[c.ci] === it) showDetail(it);
                });
            }, 350);
        }
    }

    function setText(el, v) {
        if (el._t !== v) { el._t = v; el.textContent = v; }
    }

    function setCls(el, v) {
        if (el._c !== v) { el._c = v; el.className = v; }
    }

    function makeRow() {
        var li = document.createElement('li');
        li.className = 'hg g20';
        li.innerHTML = '<span class="num"></span><span class="thumb"><span class="ini"></span><img alt=""></span><span class="name"></span><span class="meta"></span><span class="star hidden">★</span>';
        var c = li.children;
        li._num = c[0];
        li._thumb = c[1];
        li._ini = c[1].children[0];
        li._img = c[1].children[1];
        li._name = c[2];
        li._meta = c[3];
        li._star = c[4];
        li._img.decoding = 'async';
        li._img.style.visibility = 'hidden';
        li._img.onload = function () { this.style.visibility = 'visible'; };
        li._img.onerror = function () { this.style.visibility = 'hidden'; };
        li._on = true;
        return li;
    }

    function makeGroupRow() {
        var li = document.createElement('li');
        li.innerHTML = '<span class="name"></span><span class="count"></span>';
        li._name = li.children[0];
        li._count = li.children[1];
        li._on = true;
        return li;
    }

    function applyImg(li) {
        var img = li._img;
        var w = li._want || '';
        if (img._src === w) return;
        img._src = w;
        img.style.visibility = 'hidden';
        if (w) img.src = w; else img.removeAttribute('src');
    }

    function settleImages() {
        for (var i = 0; i < pool.items.length; i++) applyImg(pool.items[i]);
    }

    function metaOf(it) {
        var meta = '';
        if (it.kind === 'ep') meta = 'S' + pad(it.season || 0) + ' · E' + pad(it.num || 0);
        else if (it.rating && it.rating !== '0') meta = t('Rating') + ' ' + it.rating;
        var p = state.pos[it.key];
        if (isVod(it) && S.get('resume') && p) {
            var pct = it.dur ? ' (' + Math.min(99, Math.round(p / it.dur * 100)) + '%)' : '';
            meta = '▶ ' + fmt(p) + pct + (meta ? '  ·  ' + meta : '');
        }
        var cx = state.ctx;
        if (cx && (cx.kind === 'favs' || cx.kind === 'cont')) {
            var kl = it.kind === 'ep' && it.serieName ? it.serieName : t(FAV_KIND[it.kind] || '');
            meta = kl + (meta ? '  ·  ' + meta : '');
        }
        return meta;
    }

    function fillRow(li, it, i, cursor, fast) {
        setCls(li, 'hg g20' + (cursor ? ' cursor' : '') + (state.bg && state.bg.key === it.key ? ' now' : ''));
        setText(li._num, String(i + 1));
        setCls(li._thumb, 'thumb' + (it.kind !== 'live' ? ' poster' : ''));
        setText(li._ini, initial(it.name));
        setText(li._name, it.name);
        setText(li._meta, metaOf(it));
        var fav = !!state.favorites[it.key];
        if (li._fav !== fav) { li._fav = fav; li._star.classList.toggle('hidden', !fav); }
        li._want = safeLogo(it.logo);
        if (!fast) applyImg(li);
    }

    function ensureItemsPool() {
        if (pool.itemsOn) return;
        var list = $('channel-list');
        list.innerHTML = '';
        if (!pool.items.length) {
            for (var i = 0; i < ROWS; i++) pool.items.push(makeRow());
        }
        for (var j = 0; j < pool.items.length; j++) list.appendChild(pool.items[j]);
        pool.itemsOn = true;
    }

    function ensureGroupsPool() {
        if (pool.groupsOn) return;
        var list = $('group-list');
        list.innerHTML = '';
        if (!pool.groups.length) {
            for (var i = 0; i < ROWS; i++) pool.groups.push(makeGroupRow());
        }
        for (var j = 0; j < pool.groups.length; j++) list.appendChild(pool.groups[j]);
        pool.groupsOn = true;
    }

    function renderBrowse(fast) {
        if (state.screen !== 'browse' || !state.ctx) return;
        var ctx = state.ctx;
        var s = state.source;
        var isEp = ctx.kind === 'ep';
        setText($('src-title'), isEp ? ctx.serie.name : t(ctx.title));
        setText($('src-type'), TYPE_LABEL[s.type]);
        setText($('src-count'), ctx.loading ? '' : (isEp ? t('{n} episodes', { n: ctx.items.length }) : countLabel(ctx.kind, ctx.items.length)));
        var sub = isEp ? (ctx.serie.plot || '') : '';
        setText($('ctx-sub'), sub);
        $('ctx-sub').classList.toggle('hidden', !sub);
        if ($('search').value !== ctx.query) $('search').value = ctx.query;
        $('groups').classList.toggle('searching', !!ctx.query);
        var hf = $('browse-hints');
        var want = ctx.kind === 'cont' ? 'cont' : 'browse';
        if (hf.getAttribute('data-h') !== want) { hf.setAttribute('data-h', want); renderHints(); }
        renderGroups(ctx);
        renderItems(ctx, fast);
        clearTimeout(state.imgTimer);
        if (fast) state.imgTimer = setTimeout(settleImages, 150);
    }

    function renderGroups(ctx) {
        var n = ctx.groups.length;
        if (ctx.loading || !n) {
            pool.groupsOn = false;
            $('group-list').innerHTML = '';
            return;
        }
        ensureGroupsPool();
        ctx.gws = stableWindow(ctx.gws || 0, ctx.gi, n);
        for (var r = 0; r < ROWS; r++) {
            var li = pool.groups[r];
            var i = ctx.gws + r;
            if (i >= n) {
                if (li._on) { li.style.display = 'none'; li._on = false; }
                continue;
            }
            if (!li._on) { li.style.display = ''; li._on = true; }
            var g = ctx.groups[i];
            setCls(li, (i === ctx.gi ? 'active' : '') + (ctx.focus === 'groups' && i === ctx.gi ? ' cursor' : '') + (lockOn() && KINDS.indexOf(ctx.kind) >= 0 && state.locked[lockId({ kind: ctx.kind, group: g.name })] ? ' locked' : ''));
            setText(li._name, t(g.name));
            setText(li._count, String(g.list.length));
        }
    }

    function renderItems(ctx, fast) {
        var list = visibleList(ctx);
        $('channel-list').classList.toggle('posters', isPosterCtx(ctx));
        if (!isPosterCtx(ctx)) $('detail').classList.add('hidden');
        var g = ctx.groups[ctx.gi];
        var title = g ? t(g.name) : '';
        if (ctx.query) title = t('Search') + '  ·  ' + t('{n} found', { n: list.length });
        setText($('channels-title'), title);

        if (ctx.loading || !list.length) {
            pool.itemsOn = false;
            $('detail').classList.add('hidden');
            var html;
            if (ctx.loading) {
                html = '<li class="empty"><span class="spin"></span>' + esc(t('Loading…')) + '</li>';
            } else {
                var img = '';
                var msg;
                if (ctx.query) { img = 'images/no-results.png'; msg = t('No match for your search.'); }
                else if (ctx.kind === 'favs') { img = 'images/fav-empty.png'; msg = t('No favorites yet. Press YELLOW on a channel, movie or series to add it.'); }
                else if (ctx.kind === 'cont') { img = 'images/fav-empty.png'; msg = t('Nothing to continue yet. Movies and episodes you start will appear here.'); }
                else msg = t(ctx.error || 'Nothing here.');
                html = '<li class="empty big">' + (img ? '<img src="' + img + '" alt="">' : '') + '<div>' + esc(msg) + '</div></li>';
            }
            $('channel-list').innerHTML = html;
            return;
        }
        ensureItemsPool();
        ctx.ci = clamp(ctx.ci, 0, list.length - 1);
        ctx.ws = stableWindow(ctx.ws || 0, ctx.ci, list.length);
        var R = rowsNow();
        for (var r = 0; r < ROWS; r++) {
            var li = pool.items[r];
            var i = ctx.ws + r;
            if (i >= list.length || r >= R) {
                if (li._on) { li.style.display = 'none'; li._on = false; }
                continue;
            }
            if (!li._on) { li.style.display = ''; li._on = true; }
            fillRow(li, list[i], i, i === ctx.ci && ctx.focus === 'items', fast);
        }
        if (isPosterCtx(ctx)) showDetail(list[ctx.ci]); 
    }

    function scheduleRender() {
        if (state.raf) return;
        state.raf = requestAnimationFrame(function () {
            state.raf = 0;
            var fast = state.fast;
            state.fast = false;
            if (state.screen !== 'browse' || !state.ctx) return;
            var cx = state.ctx;
            if (fast && cx.focus === 'groups' && !cx.query && !cx.loading) {
                renderGroups(cx);
                clearTimeout(state.deferTimer);
                state.deferTimer = setTimeout(function () {
                    state.deferTimer = null;
                    if (state.screen === 'browse' && state.ctx === cx) renderBrowse(false);
                }, 90);
                return;
            }
            clearTimeout(state.deferTimer);
            state.deferTimer = null;
            renderBrowse(fast);
        });
    }

    function move(delta, horiz) {
        var ctx = state.ctx;
        var now = Date.now();
        state.fast = now - state.lastNav < 160;
        state.lastNav = now;
        if (ctx.focus === 'groups') {
            var gi = clamp(ctx.gi + delta, 0, ctx.groups.length - 1);
            if (gi !== ctx.gi) { ctx.gi = gi; ctx.ci = 0; ctx.ws = 0; ctx.vis = null; ctx.query = ''; ctx.res = null; }
        } else if (delta < 0 && !horiz && ctx.ci < (isPosterCtx(ctx) ? COLS : 1)) {
            $('search').focus();
            return;
        } else {
            ctx.ci = clamp(ctx.ci + delta, 0, Math.max(0, visibleList(ctx).length - 1));
        }
        scheduleRender();
    }

    function focusItems() {
        var ctx = state.ctx;
        if (!visibleList(ctx).length) return;
        var g = ctx.groups[ctx.gi];
        if (!ctx.query && g && KINDS.indexOf(ctx.kind) >= 0) {
            gate({ kind: ctx.kind, group: g.name }, function () {
                if (state.ctx !== ctx) return;
                ctx.focus = 'items';
                renderBrowse();
            });
            return;
        }
        ctx.focus = 'items';
        renderBrowse();
    }

    function focusGroups() {
        state.ctx.focus = 'groups';
        renderBrowse();
    }

    function setQuery(q) {
        var ctx = state.ctx;
        ctx.query = q;
        ctx.vis = null;
        ctx.ci = 0;
        ctx.ws = 0;
        if (!q) ctx.res = null;
        clearTimeout(state.queryTimer);
        if (!q) { renderBrowse(); return; }
        state.queryTimer = setTimeout(function () {
            if (state.ctx === ctx && state.screen === 'browse') renderBrowse(true);
            clearTimeout(state.imgTimer);
            state.imgTimer = setTimeout(settleImages, 200);
        }, CFG.SEARCH_DELAY_MS);
    }

    function pushCtx(next) {
        state.stack.push(state.ctx);
        state.ctx = next;
        renderBrowse();
        loadCtx(next, false);
    }

    function backBrowse() {
        var ctx = state.ctx;
        if (ctx.focus === 'items') { focusGroups(); return; }
        if (state.stack.length) {
            leaveCtx(ctx);
            state.ctx = state.stack.pop();
            if (state.ctx.kind === 'cont' || state.ctx.kind === 'favs') rebuildGroups(state.ctx);
            renderBrowse();
            return;
        }
        goHub();
    }

    function enterSelected() {
        var ctx = state.ctx;
        if (ctx.focus === 'groups') { focusItems(); return; }
        var list = visibleList(ctx);
        var it = list[ctx.ci];
        if (!it) return;
        if (it.hiddenCat) { restoreCategory(it.hiddenCat); return; }
        if (it.kind === 'series') {
            gate(it, function () { pushCtx(makeCtx('ep', it.name, it)); });
            return;
        }
        if (it.kind === 'ep' && ctx.kind === 'cont' && it.serie) {
            openFromHistory(it);
            return;
        }
        if (ctx.kind === 'ep') {
            var all = ctx.items;
            var at = all.indexOf(it);
            openPlayer(ctx, all, at < 0 ? 0 : at);
            return;
        }
        if (it.kind === 'live' && !(state.bg && state.bg.key === it.key)) {
            gate(it, function () { startPreview(it); });
            return;
        }
        openPlayer(ctx, list, ctx.ci);
    }

    function openFromHistory(it) {
        var src = state.source;
        var serie = it.serie;
        var p = src.type === 'm3u'
            ? getCatalog(src, 'series', false).then(function (list) {
                for (var i = 0; i < list.length; i++) { if (list[i].id === serie.id) return list[i]; }
                return serie;
            })
            : Promise.resolve(serie);
        p.then(function (full) {
            if (state.screen !== 'browse' || !state.ctx) return;
            var next = makeCtx('ep', full.name, full);
            next.playKey = it.key;
            pushCtx(next);
        }).catch(function (err) { toast(errText(err), true); });
    }

    function minSerie(s) {
        var o = { id: s.id, name: s.name, group: s.group || '', logo: s.logo || '', kind: 'series' };
        ['cmd', 'rating', 'year'].forEach(function (k) { if (s[k]) o[k] = s[k]; });
        if (s.plot) o.plot = String(s.plot).slice(0, 200);
        return o;
    }

    function snap(it, ctx) {
        var o = { id: it.id, name: it.name, group: it.group || '', logo: it.logo || '', kind: it.kind, at: Date.now() };
        ['ext', 'cmd', 'url', 'rating', 'year', 'season', 'num', 'tvgId', 'ua', 'referer'].forEach(function (k) {
            if (it[k] !== undefined && it[k] !== '' && it[k] !== null) o[k] = it[k];
        });
        if (it.plot) o.plot = String(it.plot).slice(0, 200);
        if (it.kind === 'ep') {
            var serie = (ctx && ctx.serie) || (state.playCtx && state.playCtx.serie);
            if (serie) { o.serieName = serie.name; o.serie = minSerie(serie); }
            else {
                if (it.serieName) o.serieName = it.serieName;
                if (it.serie) o.serie = it.serie;
            }
        }
        if (it.eps) {
            o.eps = it.eps.map(function (e) {
                return { id: e.id, name: e.name, group: e.group, season: e.season, num: e.num, url: e.url, kind: 'ep' };
            });
        }
        return o;
    }

    function recordsFor(src, store, order) {
        var prefix = src.id + ':';
        var out = [];
        Object.keys(store).forEach(function (key) {
            if (key.indexOf(prefix) !== 0) return;
            var v = store[key];
            if (!v || typeof v !== 'object') return;
            var it = {};
            Object.keys(v).forEach(function (k) { it[k] = v[k]; });
            it.key = key;
            out.push(it);
        });
        out.sort(function (a, b) { return (b[order] || 0) - (a[order] || 0); });
        return out;
    }

    function favItems(src) { return recordsFor(src, state.favorites, 'at'); }

    function histItems(src) {
        return recordsFor(src, state.hist, 'at').filter(function (it) { return state.pos[it.key]; });
    }

    function recordHist(it, pos, dur) {
        var rec = snap(it, state.playCtx);
        delete rec.eps;
        rec.pos = Math.floor(pos);
        rec.dur = Math.floor(dur);
        rec.at = Date.now();
        state.hist[it.key] = rec;
        var keys = Object.keys(state.hist);
        if (keys.length > CFG.HIST_MAX) {
            keys.sort(function (a, b) { return (state.hist[a].at || 0) - (state.hist[b].at || 0); });
            for (var i = 0; i < keys.length - CFG.HIST_MAX; i++) {
                delete state.hist[keys[i]];
                delete state.pos[keys[i]];
            }
        }
    }

    function dropHistory(it) {
        delete state.pos[it.key];
        delete state.hist[it.key];
        writeJSON(POS_KEY, state.pos);
        writeJSON(HIST_KEY, state.hist);
    }

    function upgradeLegacy(items, ctx) {
        var changed = false;
        for (var i = 0; i < items.length; i++) {
            if (state.favorites[items[i].key] === true) {
                state.favorites[items[i].key] = snap(items[i], ctx);
                changed = true;
            }
        }
        if (changed) writeJSON(FAV_KEY, state.favorites);
    }

    function migrateFavs(src) {
        var prefix = src.id + ':';
        var legacy = Object.keys(state.favorites).some(function (k) {
            return k.indexOf(prefix) === 0 && state.favorites[k] === true;
        });
        if (!legacy) return;
        KINDS.forEach(function (kind) {
            var items = state.catCache[src.id + ':' + kind];
            if (!items || !items.length) return;
            var changed = false;
            items.forEach(function (it) {
                var key = keyOf(src, it);
                if (state.favorites[key] === true) { state.favorites[key] = snap(it, null); changed = true; }
            });
            if (changed) writeJSON(FAV_KEY, state.favorites);
        });
    }

    function toggleFav(it) {
        if (!it || !it.key || it.hiddenCat) return;
        var ctx = state.screen === 'player' ? state.playCtx : state.ctx;
        if (state.favorites[it.key]) delete state.favorites[it.key];
        else state.favorites[it.key] = snap(it, ctx);
        writeJSON(FAV_KEY, state.favorites);
        if (state.screen === 'browse') {
            rebuildGroups(state.ctx);
            renderBrowse();
        } else {
            showInfo();
        }
        toast(t(state.favorites[it.key] ? 'Added to favorites' : 'Removed from favorites'));
    }

    function removeHistory() {
        var ctx = state.ctx;
        if (!ctx || ctx.kind !== 'cont') return;
        var it = visibleList(ctx)[ctx.ci];
        if (!it) return;
        dropHistory(it);
        rebuildGroups(ctx);
        renderBrowse();
        toast(t('Remove from history'));
    }

    function onBrowseKey(k) {
        if ((k === 48 || k === 96) && !state.moving) { goHub(); return; }
        if (state.moving) {
            if (k === KEY.UP) moveFav(-1);
            else if (k === KEY.DOWN) moveFav(1);
            else if (k === KEY.ENTER || k === KEY.BACK || k === KEY.BACK_ALT || k === KEY.GREEN) { state.moving = null; toast(trl('تم تثبيت الترتيب', 'Order saved')); }
            return;
        }
        var step = state.step || 1;
        var grid = isPosterCtx(state.ctx) && state.ctx.focus === 'items';
        var ci = state.ctx.ci;
        switch (k) {
            case KEY.UP: move(grid ? -step * COLS : -step); break;
            case KEY.DOWN: move(grid ? step * COLS : step); break;
            case KEY.CH_UP: case KEY.RW: move(grid ? -COLS * GRID_ROWS : -(rowsNow() - 1)); break;
            case KEY.CH_DOWN: case KEY.FF: move(grid ? COLS * GRID_ROWS : rowsNow() - 1); break;
            case KEY.LEFT:
                if (grid && ci % COLS !== 0) move(-1, true); else focusGroups();
                break;
            case KEY.RIGHT:
                if (grid) { if (ci % COLS !== COLS - 1 && ci < visibleList(state.ctx).length - 1) move(1, true); }
                else focusItems();
                break;
            case KEY.ENTER: enterSelected(); break;
            case KEY.BLUE: $('search').focus(); break;
            case KEY.GREEN:
                if (state.ctx.query) { setQuery(''); renderBrowse(); }
                else if (state.ctx.focus === 'groups' || (state.ctx.groups[state.ctx.gi] && state.ctx.groups[state.ctx.gi].restore)) toggleHideGroup();
                else startMoveFav();
                break;
            case KEY.YELLOW: toggleFav(visibleList(state.ctx)[state.ctx.ci]); break;
            case KEY.INFO: toggleLock(); break;
            case KEY.RED:
                if (state.ctx.kind === 'cont') removeHistory();
                else if (state.ctx.kind !== 'favs') loadCtx(state.ctx, true);
                break;
            case KEY.BACK: case KEY.BACK_ALT: backBrowse(); break;
            case KEY.EXIT: exitApp(); break;
        }
    }
    function showModal(m) {
        state.modal = m;
        m.i = 0;
        m.val = '';
        renderModal();
        $('modal').classList.remove('hidden');
    }

    function hideModal() {
        state.modal = null;
        $('modal').classList.add('hidden');
    }

    function renderModal() {
        var m = state.modal;
        if (!m) return;
        $('modal-title').textContent = t(m.title);
        var nm = $('modal-name');
        nm.classList.toggle('pin', !!m.pin);
        if (m.pin) {
            var dots = '';
            for (var d = 0; d < CFG.PIN_LEN; d++) dots += (d < m.val.length ? '●' : '○') + ' ';
            nm.textContent = dots;
        } else {
            nm.textContent = m.name || '';
        }
        var html = '';
        m.opts.forEach(function (o, i) {
            html += '<span class="btn' + (i === 0 ? ' primary' : '') + (i === m.i ? ' sel' : '') + '">' + esc(t(o[0], { x: o[1] })) + '</span>';
        });
        $('modal-btns').innerHTML = html;
    }


    var INSTANT = { setpin: 1, export: 1, import: 1, tmdb: 1, pair: 1 };

    function hashPin(p) {
        var h = 5381;
        var s = 'lumora:' + p;
        for (var i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) | 0;
        return String(h);
    }

    function askPin(title, cb) {
        showModal({ title: title, name: '', pin: true, opts: [['Cancel', '']], onPick: function () { }, onDone: cb });
    }

    function verifyPin(cb, title) {
        askPin(title || 'Enter PIN', function (p) {
            if (hashPin(p) === state.pin) cb();
            else toast(t('Wrong PIN'), true);
        });
    }

    function guardPin(cb) {
        if (S.get('parental') && state.pin) verifyPin(cb);
        else cb();
    }

    function setPinFlow(done) {
        function fresh() {
            askPin('Enter new PIN', function (a) {
                askPin('Confirm new PIN', function (b) {
                    if (a !== b) { toast(t('PINs do not match'), true); return; }
                    state.pin = hashPin(a);
                    writeJSON(PIN_KEY, { h: state.pin });
                    toast(t('PIN saved'));
                    if (done) done();
                });
            });
        }
        if (state.pin) verifyPin(fresh, 'Enter current PIN');
        else fresh();
    }

    function lockOn() { return !!(S.get('parental') && S.get('lockCats') && state.pin); }

    function lockId(it) {
        return (state.source ? state.source.id : '') + '|' + (it.kind || '') + '|' + (it.group || '');
    }

    function isGated(it) {
        if (!lockOn() || !it || it.kind === 'ep' || it.direct) return false;
        var id = lockId(it);
        return !!state.locked[id] && !state.unlocked[id];
    }

    function gate(it, go) {
        if (!isGated(it)) { go(); return; }
        var id = lockId(it);
        verifyPin(function () {
            state.unlocked[id] = true;
            go();
        });
    }

    function toggleLock() {
        var ctx = state.ctx;
        var g = ctx && ctx.groups[ctx.gi];
        if (!g || KINDS.indexOf(ctx.kind) < 0) { toast(t('Nothing to lock here')); return; }
        if (!S.get('parental') || !state.pin) { toast(t('Turn on Parental lock in Settings first'), true); return; }
        var id = lockId({ kind: ctx.kind, group: g.name });
        verifyPin(function () {
            if (state.locked[id]) delete state.locked[id];
            else state.locked[id] = 1;
            delete state.unlocked[id];
            writeJSON(LOCK_KEY, state.locked);
            toast(t(state.locked[id] ? 'Category locked' : 'Category unlocked'));
            if (state.screen === 'browse') renderBrowse();
        });
    }

    function lastChannel() {
        if (state.prevIdx < 0 || state.prevIdx >= state.list.length || state.prevIdx === state.idx) {
            toast(t('No previous channel'));
            return;
        }
        tune(state.prevIdx, true);
    }

    function trackName(tr, n) { return tr.lang || t('Track {n}', { n: n + 1 }); }

    function pickTrack(type) {
        var list = Player.tracks(type);
        if (!list.length) { toast(t(type === 'AUDIO' ? 'No audio tracks available' : 'No subtitles available')); return; }
        var opts = [];
        var map = [];
        var label = [];
        var sel = 0;
        if (type === 'TEXT') { opts.push(['Off', '']); map.push(-1); label.push(t('Off')); }
        list.forEach(function (tr, n) {
            if (tr.selected) sel = opts.length;
            opts.push(['{x}', (tr.selected ? '● ' : '') + trackName(tr, n)]);
            map.push(tr.index);
            label.push(trackName(tr, n));
        });
        showModal({
            title: type === 'AUDIO' ? 'Audio track' : 'Subtitles',
            name: '',
            opts: opts,
            onPick: function (i) {
                if (Player.selectTrack(type, map[i])) toast(t(type === 'AUDIO' ? 'Audio: {x}' : 'Subtitles: {x}', { x: label[i] }));
            }
        });
        state.modal.i = sel;
        renderModal();
    }

    function openTracks() {
        var a = Player.tracks('AUDIO').length;
        var s = Player.tracks('TEXT').length;
        if (!a && !s) { toast(t('No audio tracks available')); return; }
        if (a && s) {
            showModal({
                title: 'Audio / Subtitles',
                name: '',
                opts: [['Audio track', ''], ['Subtitles', '']],
                onPick: function (i) { pickTrack(i === 0 ? 'AUDIO' : 'TEXT'); }
            });
        } else {
            pickTrack(a ? 'AUDIO' : 'TEXT');
        }
    }

    function techText() {
        var si = Player.streamInfo();
        var parts = [];
        if (si.engine) parts.push(si.engine);
        if (si.w && si.h) parts.push(si.w + '\u00D7' + si.h);
        if (si.kbps) parts.push(si.kbps >= 1000 ? (si.kbps / 1000).toFixed(1) + ' Mbps' : si.kbps + ' kbps');
        var txt = parts.join('  \u00B7  ');
        if (S.get('diag')) txt += '\n' + Player.diag() + (state.lastErr ? '\n' + state.lastErr : '');
        return txt;
    }

    function plain(o) { return !!o && typeof o === 'object' && !Array.isArray(o); }

    function parseBackup(raw) {
        if (raw.length > 5000000) return { error: 'Backup contains invalid data' };
        var o;
        try { o = JSON.parse(raw); } catch (e) { return { error: 'Backup is not valid JSON' }; }
        if (!plain(o) || o.app !== 'lumora' || o.v !== 1) return { error: 'Backup was not made by Lumora' };
        if (!Array.isArray(o.sources) || o.sources.length > 200) return { error: 'Backup contains an invalid source' };
        var seen = {};
        var sources = [];
        for (var i = 0; i < o.sources.length; i++) {
            var s = o.sources[i];
            if (!plain(s) || TYPE_ORDER.indexOf(s.type) < 0 || typeof s.id !== 'string' || !/^[\w.-]{1,64}$/.test(s.id) || seen[s.id]) {
                return { error: 'Backup contains an invalid source' };
            }
            seen[s.id] = 1;
            var u = Validate.httpUrl(s.url);
            if (!u.ok) return { error: 'Backup contains an invalid source' };
            var mac = '';
            if (s.type === 'stalker') {
                var m = Validate.mac(s.mac);
                if (!m.ok) return { error: 'Backup contains an invalid source' };
                mac = m.value;
            }
            sources.push({
                id: s.id, type: s.type, name: Validate.name(s.name) || hostOf({ url: u.value }), url: u.value,
                user: s.type === 'xtream' ? String(s.user || '').slice(0, CFG.CRED_MAX_LEN) : '',
                pass: s.type === 'xtream' ? String(s.pass || '').slice(0, CFG.CRED_MAX_LEN) : '',
                format: /^[a-z0-9]{2,5}$/i.test(s.format || '') ? s.format : 'm3u8', mac: mac,
                proxy: s.type === 'stalker' && typeof s.proxy === 'string' && Validate.httpUrl(s.proxy).ok ? Validate.httpUrl(s.proxy).value : ''
            });
        }
        var out = { sources: sources, favorites: {}, pos: {}, hist: {} };
        var names = ['favorites', 'pos', 'hist'];
        for (var n = 0; n < names.length; n++) {
            var src = o[names[n]];
            if (src === undefined) continue;
            if (!plain(src)) return { error: 'Backup contains invalid data' };
            var keys = Object.keys(src);
            if (keys.length > 50000) return { error: 'Backup contains invalid data' };
            for (var j = 0; j < keys.length; j++) {
                var key = keys[j];
                if (key === '__proto__' || key === 'constructor' || key === 'prototype') continue;
                var v = src[key];
                if (names[n] === 'pos') {
                    if (typeof v !== 'number' || !isFinite(v) || v < 0) return { error: 'Backup contains invalid data' };
                } else if (!plain(v)) {
                    return { error: 'Backup contains invalid data' };
                }
                out[names[n]][key] = v;
            }
        }
        return { data: out };
    }

    function showBackup(mode) {
        var ta = $('backup-text');
        state.backup = { mode: mode };
        if (mode === 'tmdb') {
            $('backup-title').textContent = t('TMDB API key');
            $('backup-note').textContent = t('Free key from themoviedb.org → Settings → API. Used to load movie and series descriptions. This product uses the TMDB API but is not endorsed or certified by TMDB.');
            $('backup-hint').textContent = t('OK: type or paste · GREEN: save · BACK: close');
            ta.readOnly = false;
            ta.value = String(S.get('tmdbKey') || '');
            $('backup').classList.remove('hidden');
            ta.scrollTop = 0;
            return;
        }
        $('backup-title').textContent = t(mode === 'export' ? 'Export backup' : 'Import backup');
        $('backup-note').textContent = t('Backup contains your passwords. Keep it private.');
        $('backup-hint').textContent = t(mode === 'export' ? 'Scroll with ▲▼, press BACK to close' : 'OK: type or paste · GREEN: import · BACK: close');
        ta.readOnly = mode === 'export';
        ta.value = mode === 'export' ? JSON.stringify({ app: 'lumora', v: 1, at: Date.now(), sources: state.sources, favorites: state.favorites, pos: state.pos, hist: state.hist }) : '';
        $('backup').classList.remove('hidden');
        ta.scrollTop = 0;
    }

    function saveTmdbKey() {
        var v = $('backup-text').value.replace(/[^A-Za-z0-9._-]/g, '');
        S.set('tmdbKey', v);
        hideBackup();
        Net.clear();
        toast(t('TMDB key saved'));
        if (state.screen === 'settings') renderSettings();
    }

    function hideBackup() {
        state.backup = null;
        try { $('backup-text').blur(); } catch (e) { }
        $('backup').classList.add('hidden');
    }

    function importBackup() {
        var raw = $('backup-text').value.trim();
        if (!raw) { toast(t('Paste the backup text first'), true); return; }
        var r = parseBackup(raw);
        if (r.error) { toast(t(r.error), true); return; }
        showModal({
            title: 'Replace current data with this backup?',
            name: t('{n} sources', { n: r.data.sources.length }),
            opts: [['Replace', ''], ['Cancel', '']],
            onPick: function (i) { if (i === 0) applyBackup(r.data); }
        });
    }

    function applyBackup(d) {
        state.sources.forEach(function (s) { clearCache(s.id); forgetAll(s.id); });
        Object.keys(state.catCache).forEach(function (k) { delete state.catCache[k]; });
        state.sources = d.sources;
        state.favorites = d.favorites;
        state.pos = d.pos;
        state.hist = d.hist;
        writeJSON(SOURCES_KEY, state.sources);
        writeJSON(FAV_KEY, state.favorites);
        writeJSON(POS_KEY, state.pos);
        writeJSON(HIST_KEY, state.hist);
        state.si = 0;
        hideBackup();
        toast(t('Backup restored'));
        if (state.screen === 'settings') renderSettings();
    }

    function onBackupKey(k) {
        var ta = $('backup-text');
        switch (k) {
            case KEY.UP: ta.scrollTop = Math.max(0, ta.scrollTop - 140); break;
            case KEY.DOWN: ta.scrollTop += 140; break;
            case KEY.ENTER: if (state.backup.mode !== 'export') ta.focus(); break;
            case KEY.GREEN:
                if (state.backup.mode === 'import') importBackup();
                else if (state.backup.mode === 'tmdb') saveTmdbKey();
                break;
            case KEY.BACK: case KEY.BACK_ALT: hideBackup(); break;
            case KEY.EXIT: exitApp(); break;
        }
    }

    function onModalKey(k) {
        var m = state.modal;
        if (!m) return;
        var dg = (k >= 48 && k <= 57) ? k - 48 : ((k >= 96 && k <= 105) ? k - 96 : -1);
        if (m.pin && dg >= 0) {
            m.val += String(dg);
            if (m.val.length >= CFG.PIN_LEN) {
                var entered = m.val;
                var done = m.onDone;
                hideModal();
                done(entered);
                return;
            }
            renderModal();
            return;
        }
        switch (k) {
            case KEY.LEFT: case KEY.UP: m.i = clamp(m.i - 1, 0, m.opts.length - 1); renderModal(); break;
            case KEY.RIGHT: case KEY.DOWN: m.i = clamp(m.i + 1, 0, m.opts.length - 1); renderModal(); break;
            case KEY.ENTER:
                var pick = m.onPick;
                var i = m.i;
                hideModal();
                pick(i);
                break;
            case KEY.BACK: case KEY.BACK_ALT: hideModal(); break;
            case KEY.EXIT: exitApp(); break;
        }
    }
    function openPlayer(ctx, list, index) {
        var first = list[index];
        if (!first) return;
        gate(first, function () { openPlayerNow(ctx, list, index); });
    }

    function openPlayerNow(ctx, list, index) {
        var it = list[index];
        var saved = it && isVod(it) && S.get('resume') ? state.pos[it.key] : 0;
        if (saved > 5000 && S.get('resumeAsk')) {
            showModal({
                title: 'Resume playback?',
                name: it.name,
                opts: [['Resume from {x}', fmt(saved)], ['Start over', '']],
                onPick: function (i) {
                    if (i === 1) dropHistory(it);
                    launchPlayer(ctx, list, index);
                }
            });
            return;
        }
        launchPlayer(ctx, list, index);
    }

    function launchPlayer(ctx, list, index) {
        var same = !!(state.bg && list[index] && list[index].key === state.bg.key && state.source && state.source.id === state.bg.srcId);
        state.bg = null;
        clearTimeout(state.bgTimer);
        state.libSig = libSig();
        state.playCtx = ctx;
        state.list = list;
        state.tickCount = 0;
        state.suspended = false;
        state.idx = index;
        state.prevIdx = -1;
        showScreen('player');
        if (S.get('keepAwake')) screenSaver(false);
        if (same) {
            state.retries = 0; state.quiet = false; state.quietN = 0; state.healthy = true;
            setInfoState(t('Live'));
            showInfo();
        } else {
            tune(index, true);
        }
        startTick();
    }

    function curItem() { return state.list[state.idx]; }
    function tune(i, immediate) {
        var len = state.list.length;
        if (!len) return;
        var ni = ((i % len) + len) % len;
        if (ni !== state.idx) { state.prevIdx = state.idx; state.catchupUrl = null; }
        state.idx = ni;
        state.retries = 0;
        state.quietN = 0;
        state.resumeAt = 0;
        state.healthy = false;
        state.quiet = false;
        clearTimeout(state.retryTimer);
        clearTimeout(state.zapTimer);
        state.streamToken++;
        Net.cancel('resolve');
        var target = curItem();
        if (target && isGated(target)) {
            Player.stop();
            setInfoState(t('Enter PIN'));
            showInfo();
            var again = state.idx;
            gate(target, function () { if (state.screen === 'player' && state.idx === again) tune(again, true); });
            return;
        }
        setInfoState(t('Connecting…'));
        showInfo();
        if (immediate) {
            startStream();
        } else {
            Player.stop();
            state.zapTimer = setTimeout(startStream, CFG.ZAP_DELAY_MS);
        }
    }

    function startStream() {
        if (state.screen !== 'player') return;
        var token = ++state.streamToken;
        var it = curItem();
        var cu = !isVod(it) && state.catchupUrl;
        (cu ? Promise.resolve(cu) : (it.direct ? Promise.resolve(it.url) : prov().resolve(state.source, it))).then(function (url) {
            if (token !== state.streamToken || state.screen !== 'player') return;
            var start = 0;
            if (isVod(it) && state.retries < 2) start = state.resumeAt || (S.get('resume') ? Math.max(0, (state.pos[it.key] || 0) - 3000) : 0);
            if (it.direct) start = state.resumeAt || it.start || 0;
            state.paused = false;
            Player.play(url, { start: start, live: !isVod(it) && !cu, ua: it.ua, referer: it.referer });
        }).catch(function (err) {
            if (token !== state.streamToken) return;
            console.log('resolve error', err);
            handleError(err);
        });
    }

    function setInfoState(text) { $('info-state').textContent = text; }

    function showInfo() {
        var it = curItem();
        if (!it) return;
        var live = !isVod(it);
        $('info-name').textContent = it.name;
        var meta;
        if (live) {
            meta = t('CH') + ' ' + (state.idx + 1) + ' / ' + state.list.length + '   ·   ' + t(it.group || '');
        } else if (it.kind === 'ep') {
            var sname = (state.playCtx && state.playCtx.serie) ? state.playCtx.serie.name : (it.serieName || '');
            meta = sname + '   ·   S' + pad(it.season || 0) + ' E' + pad(it.num || 0);
        } else {
            meta = t(it.group || '') + (it.year ? '   ·   ' + it.year : '') +
                (it.rating && it.rating !== '0' ? '   ·   ' + t('Rating') + ' ' + it.rating : '');
        }
        $('info-meta').textContent = meta;
        $('info-plot').textContent = live ? '' : (it.plot || '');
        $('info-fav').textContent = state.favorites[it.key] ? t('★ Favorite') : '';
        $('info-sleep').textContent = state.sleepAt ? '☾ ' + t('{n} min', { n: sleepLeft() }) : '';
        $('info-tech').textContent = techText();
        var logo = safeLogo(it.logo);
        $('info-logo').style.visibility = 'visible';
        $('info-logo').src = logo || PH_LOGO;
        $('prog').classList.toggle('hidden', live);
        $('info-epg').textContent = '';
        if (live) loadEpg(it);
        else loadDetail(it);
        updateProgress();
        $('info').classList.remove('hidden');
        clearTimeout(state.infoTimer);
        state.infoTimer = setTimeout(hideInfo, S.get('infoTimeout') * 1000);
    }

    function hideInfo() {
        clearTimeout(state.infoTimer);
        $('info').classList.add('hidden');
    }

    function toggleInfo() {
        if ($('info').classList.contains('hidden')) showInfo();
        else hideInfo();
    }

    function mergeDetail(it, d) {
        if (!d) return;
        it.plot = d.plot || it.plot || '';
        it.year = d.year || it.year || '';
        it.rating = d.rating || it.rating || '';
        it.genre = d.genre || it.genre || '';
        it.cast = d.cast || it.cast || '';
    }

    function fetchDetail(it, cb) {
        if (it.detailed) { if (cb) cb(); return; }
        var p = prov();
        var srv = (p.detail && (it.kind === 'vod' || it.kind === 'series')) ? p.detail(state.source, it) : Promise.resolve(null);
        srv.then(function (d) { return d; }, function (e) {
            if (e && e.aborted) throw e;
            return null;
        }).then(function (d) {
            mergeDetail(it, d);
            if (!it.plot && (it.kind === 'vod' || it.kind === 'series')) {
                return Tmdb.lookup(it).then(function (d2) { mergeDetail(it, d2); }, function (e) {
                    if (e && e.aborted) throw e;
                    if (e && e.code === 401 && !state.tmdbWarned) { state.tmdbWarned = true; toast(t('TMDB key rejected'), true); }
                });
            }
        }).then(function () {
            it.detailed = true;
            if (cb) cb();
        }, function (e) {
            if (e && e.aborted) return;
            it.detailed = true;
            if (cb) cb();
        });
    }

    function loadDetail(it) {
        if (it.detailed) return;
        fetchDetail(it, function () {
            if (state.screen === 'player' && curItem() === it) showInfo();
        });
    }

    function loadEpg(ch) {
        renderEpg(null);
        if (ch.direct) return;
        var cached = state.epgCache[ch.key];
        if (cached && Date.now() - cached.at < EPG_TTL) {
            renderEpg(cached.data);
            return;
        }
        Net.cancel('epg');
        prov().epg(state.source, ch, state.catCache[state.source.id + ':live']).then(function (data) {
            state.epgCache[ch.key] = { at: Date.now(), data: data };
            if (state.screen === 'player' && curItem() === ch) renderEpg(data);
        }).catch(function () { });
    }

    function renderEpg(d) {
        var el = $('info-epg');
        if (!d || !d.now) { el.textContent = ''; return; }
        var text = t('NOW') + '  ' + (d.now.start ? d.now.start + '  ' : '') + d.now.title;
        if (d.next) text += '        ' + t('NEXT') + '  ' + (d.next.start ? d.next.start + '  ' : '') + d.next.title;
        el.textContent = text;
    }

    function updateProgress() {
        var it = curItem();
        if (!it || !isVod(it)) return;
        var pos = Player.position();
        var dur = Player.duration();
        $('prog-fill').style.width = dur ? Math.min(100, pos / dur * 100) + '%' : '0%';
        $('prog-time').textContent = fmt(pos) + ' / ' + (dur ? fmt(dur) : '--:--');
    }

    function startTick() {
        clearInterval(state.tickTimer);
        state.tickTimer = setInterval(function () {
            if (state.screen !== 'player') return;
            updateProgress();
            if (!$('info').classList.contains('hidden')) $('info-tech').textContent = techText();
            state.tickCount++;
            if (state.tickCount >= Math.max(1, Math.round(CFG.SAVE_POS_MS / 1000))) {
                state.tickCount = 0;
                savePos();
            }
        }, 1000);
    }

    function stopTick() {
        clearInterval(state.tickTimer);
        state.tickTimer = null;
    }

    function savePos() {
        var it = curItem();
        if (!it || it.direct || !isVod(it) || !S.get('resume')) return;
        var pos = Player.position();
        var dur = Player.duration();
        if (!pos || !dur) return;
        if (pos / dur > 0.95) {
            delete state.pos[it.key];
            delete state.hist[it.key];
        } else if (pos > 5000) {
            state.pos[it.key] = Math.floor(pos);
            recordHist(it, pos, dur);
        } else {
            return;
        }
        writeJSON(POS_KEY, state.pos);
        writeJSON(HIST_KEY, state.hist);
    }

    function clearPos(it) {
        if (!it) return;
        dropHistory(it);
    }

    function onEnded() {
        if (state.screen !== 'player') return;
        var it = curItem();
        if (!isVod(it) && state.catchupUrl) { state.catchupUrl = null; tune(state.idx, true); return; }
        if (!isVod(it)) { handleError('stream completed'); return; }
        clearPos(it);
        if (it.kind === 'ep' && S.get('autoNext') && state.idx + 1 < state.list.length) {
            var n = 5;
            var ar = I18n.isRtl();
            clearInterval(state.nextTimer);
            var show = function () { toast(ar ? 'الحلقة التالية بعد ' + n + ' ثوانٍ — أي زر للإلغاء' : 'Next episode in ' + n + 's — press any key to cancel'); };
            show();
            state.nextTimer = setInterval(function () {
                if (state.screen !== 'player') { clearInterval(state.nextTimer); state.nextTimer = null; return; }
                n--;
                if (n <= 0) { clearInterval(state.nextTimer); state.nextTimer = null; tune(state.idx + 1, true); }
                else show();
            }, 1000);
        } else closePlayer();
    }

    function togglePause() {
        var playing = Player.toggle();
        state.paused = !playing;
        linkSoon();
        if (!playing) savePos();
        setInfoState(t(playing ? 'Playing' : 'Paused'));
        showInfo();
    }

    function seekBy(delta) {
        var it = curItem();
        if (!isVod(it)) return;
        var pos = Player.position();
        var dur = Player.duration();
        var target = pos + delta;
        if (dur) target = Math.min(target, dur - 1000);
        Player.seek(Math.max(0, target));
        showInfo();
    }

    function zap(d) { tune(state.idx + d); }

    function digit(d) {
        state.digits = (state.digits + d).slice(-4);
        var el = $('digits');
        el.textContent = state.digits;
        el.classList.remove('hidden');
        clearTimeout(state.digitTimer);
        state.digitTimer = setTimeout(function () {
            var typed = parseInt(state.digits, 10) - 1;
            state.digits = '';
            el.classList.add('hidden');
            if (typed >= 0 && typed < state.list.length) tune(typed, true);
            else toast(t('No channel {n}', { n: typed + 1 }));
        }, CFG.DIGIT_TIMEOUT_MS);
    }

    function cycleAspect() {
        var next = ASPECTS[(ASPECTS.indexOf(S.get('aspect')) + 1) % ASPECTS.length];
        S.set('aspect', next);
        Player.setAspect(next);
        toast(t('Picture: {x}', { x: t(ASPECT_LABEL[next]) }));
    }

    function cycleSleep() {
        var next = SLEEP_STEPS[(SLEEP_STEPS.indexOf(state.sleepMin) + 1) % SLEEP_STEPS.length];
        setSleep(next);
        toast(next ? t('Sleep timer: {n} min', { n: next }) : t('Sleep timer off'));
        showInfo();
    }

    function closePlayer() {
        var cur0 = curItem();
        if (cur0 && cur0.direct) { closeCast(); return; }
        state.closedAt = Date.now();
        clearTimeout(state.zapTimer);
        clearTimeout(state.retryTimer);
        var it0 = curItem();
        var wasCatch = !!state.catchupUrl;
        state.catchupUrl = null;
        closeGuide();
        var keepBg = !!(it0 && !wasCatch && !isVod(it0) && state.healthy && state.source && !state.sleepAt);
        state.bg = keepBg ? { item: it0, key: it0.key, name: it0.name, srcId: state.source.id, tries: 0 } : null;
        state.streamToken++;
        try { savePos(); } catch (e1) { }
        try { stopTick(); } catch (e2) { }
        state.list = [];
        state.playCtx = null;
        showScreen('browse');
        try {
            if (state.ctx) {
                var cx = state.ctx;
                if (cx.kind === 'favs' || cx.kind === 'cont' || libSig() !== state.libSig) rebuildGroups(cx);
                renderBrowse();
            }
        } catch (e3) { }
        setTimeout(function () {
            if (state.screen === 'player') return;
            if (state.bg) { renderBg(); return; }
            try { Player.stop(); } catch (e4) { }
            screenSaver(true);
        }, 60);
    }

    function libSig() {
        return Object.keys(state.favorites || {}).length + ':' + Object.keys(state.hist || {}).length;
    }

    function miniRect() {
        return { x: I18n.isRtl() ? 64 : 1056, y: 140, w: 800, h: 450 };
    }

    function renderBg() {
        var el = $('bg-play');
        var on = !!state.bg && state.screen === 'browse';
        var root = document.documentElement;
        if (on) {
            var r = miniRect();
            root.style.setProperty('--mx', r.x + 'px');
            root.style.setProperty('--my', r.y + 'px');
            root.style.setProperty('--mw', r.w + 'px');
            root.style.setProperty('--mh', r.h + 'px');
        }
        root.classList.toggle('mini', on);
        if (on !== !!state.miniOn) {
            state.miniOn = on;
            try { Player.setRect(on ? miniRect() : null); } catch (e) { }
        }
        if (!el) return;
        el.classList.toggle('hidden', !on);
        if (on) {
            var html = '<b>' + esc(state.bg.name) + '</b><em>' + esc(state.bg.buf ? t('Buffering…') : '● ' + t('Live')) + '</em>' + epgHtml(state.bg.epg) + '<span>' + esc(diagTxt()) + '</span><span>' + esc(t('Press OK again for full screen')) + '</span>';
            if (el._h !== html) { el._h = html; el.innerHTML = html; }
        } else el._h = '';
    }

    function epgHtml(d) {
        if (!d || !d.now) return '';
        var s = '<i>' + esc(t('NOW') + '  ' + (d.now.start ? d.now.start + '  ' : '') + d.now.title) + '</i>';
        if (d.next) s += '<i class="nx">' + esc(t('NEXT') + '  ' + (d.next.start ? d.next.start + '  ' : '') + d.next.title) + '</i>';
        return s;
    }

    function loadBgEpg(b) {
        var ch = b.item;
        var cached = state.epgCache[ch.key];
        if (cached && Date.now() - cached.at < EPG_TTL) { b.epg = cached.data; return; }
        try {
            prov().epg(state.source, ch, state.catCache[state.source.id + ':live']).then(function (data) {
                state.epgCache[ch.key] = { at: Date.now(), data: data };
                if (state.bg === b) { b.epg = data; renderBg(); }
            }).catch(function () { });
        } catch (e) { }
    }

    function speedTxt() {
        var k = 0;
        try { k = Player.speed(); } catch (e) { }
        if (!k) return '';
        return '⇣ ' + (k >= 1000 ? (k / 1000).toFixed(1) + ' Mbps' : k + ' kbps');
    }

    function diagTxt() {
        try {
            var i = Player.streamInfo();
            var s = i.engine ? i.engine + (i.w ? ' · ' + i.w + '×' + i.h : '') : '';
            if (state.bg && state.bg.buf) { var sp = speedTxt(); if (sp) s += (s ? ' · ' : '') + sp; }
            return s;
        } catch (e) { return ''; }
    }

    function speedTick() {
        var chip = $('net-chip');
        if (!chip) return;
        if (state.screen === 'player') {
            var txt = !state.healthy ? speedTxt() : '';
            chip.textContent = txt;
            chip.classList.toggle('hidden', !txt);
        } else {
            chip.classList.add('hidden');
            if (state.screen === 'browse' && state.bg && state.bg.buf) renderBg();
        }
    }

    function startPreview(it) {
        var b = { item: it, key: it.key, name: it.name, srcId: state.source.id, tries: 0, buf: true };
        state.bg = b;
        clearTimeout(state.bgTimer);
        if (S.get('keepAwake')) screenSaver(false);
        loadBgEpg(b);
        renderBg();
        scheduleRender();
        setTimeout(function () { if (state.bg === b) renderBg(); }, 4500);
        prov().resolve(state.source, it).then(function (url) {
            if (state.bg !== b || state.screen !== 'browse') return;
            Player.play(url, { start: 0, live: true, preview: true, ua: it.ua, referer: it.referer });
        }).catch(function (err) {
            if (state.bg !== b) return;
            toast(errText(err), true);
            stopBg();
        });
    }

    function stopBg() {
        if (!state.bg) return;
        state.bg = null;
        clearTimeout(state.bgTimer);
        try { Player.stop(); } catch (e) { }
        screenSaver(true);
        renderBg();
        scheduleRender();
    }

    function bgRecover() {
        var b = state.bg;
        if (!b) return;
        if (++b.tries > 3 || !state.source || state.source.id !== b.srcId) { stopBg(); return; }
        clearTimeout(state.bgTimer);
        state.bgTimer = setTimeout(function () {
            if (state.bg !== b || state.screen === 'player') return;
            prov().resolve(state.source, b.item).then(function (url) {
                if (state.bg !== b || state.screen === 'player') return;
                Player.play(url, { start: 0, live: true, preview: true, ua: b.item.ua, referer: b.item.referer });
            }).catch(function () { bgRecover(); });
        }, 1500 * b.tries);
    }

    function handleBuffering(on) {
        if (state.screen !== 'player') {
            if (state.bg && state.bg.buf !== on) { state.bg.buf = on; renderBg(); }
            return;
        }
        var it = curItem();
        setInfoState(on ? t('Buffering…') : t(isVod(it) ? 'Playing' : 'Live'));
        state.healthy = !on;
        if (!on) { state.retries = 0; state.quiet = false; state.quietN = 0; }
    }

    function handleProgress(p) {
        if (state.screen !== 'player' || state.healthy) return;
        setInfoState(t('Buffering… {n}%', { n: p }));
    }

    function handleStall(err) {
        if (state.screen !== 'player') { if (state.bg) bgRecover(); return; }
        handleError(err || 'stalled');
    }

    function handleError(err) {
        if (state.screen !== 'player') { if (state.bg) bgRecover(); return; }
        console.log('playback error', err && (err.kind || err.message) || err);
        state.lastErr = String((err && (err.kind || err.message)) || err || '').slice(0, 80);
        var it = curItem();
        var msg = (err && err.message) ? errText(err) : t('Unavailable');
        try {
            var rw = err && err.raw;
            var rs = rw && typeof rw === 'object' ? (rw.name || rw.message || rw.details || rw.type || JSON.stringify(rw)) : rw;
            msg += ' [' + (Player.engine() || '-') + (rs ? ':' + String(rs).slice(0, 60) : '') + ']';
        } catch (ex) { }
        state.healthy = false;
        if (isVod(it)) {
            var at = Player.recentSeek() || Player.position();
            if (at > 0) state.resumeAt = at;
        }
        var limit = S.get('retryLimit');
        clearTimeout(state.retryTimer);
        if (err && err.fatal) {
            setInfoState(msg);
            showInfo();
            return;
        }
        if (state.retries < limit) {
            state.retries++;
            setInfoState(t('Reconnecting ({a}/{b})…', { a: state.retries, b: limit }));
            var delay = state.retries === 1 ? CFG.FAST_RETRY_MS : Player.backoffDelay(state.retries);
            state.retryTimer = setTimeout(startStream, delay);
        } else {
            if (!isVod(it)) {
                state.quiet = true;
                state.quietN = (state.quietN || 0) + 1;
                var wait = Math.max(12000, Math.min(60000, Player.backoffDelay(limit + state.quietN)));
                setInfoState(msg + '  ·  ' + t('Retrying in {n} sec', { n: Math.round(wait / 1000) }));
                state.retryTimer = setTimeout(function () { state.retries = 0; startStream(); }, wait);
            } else {
                setInfoState(msg);
            }
        }
        if (!state.quiet) showInfo();
    }

    function onOnline() {
        if (state.screen !== 'player' || state.healthy) return;
        clearTimeout(state.retryTimer);
        state.retries = 0;
        state.quiet = false;
        setInfoState(t('Connecting…'));
        showInfo();
        state.retryTimer = setTimeout(startStream, 400);
    }

    function onOffline() {
        if (state.screen !== 'player') return;
        setInfoState(t('No connection'));
        showInfo();
    }

    function renderChanList() {
        var n = state.list.length;
        var rows = 9;
        var start = Math.max(0, Math.min(state.chIdx - 4, n - rows));
        var end = Math.min(n, start + rows);
        var html = '';
        for (var i = start; i < end; i++) {
            html += '<li class="' + (i === state.chIdx ? 'cursor' : '') + (i === state.idx ? ' now' : '') + '"><span class="n">' + (i + 1) + '</span><span class="t">' + esc(state.list[i].name) + '</span></li>';
        }
        $('chlist-ul').innerHTML = html;
        var cur = state.list[state.idx];
        $('chlist-title').textContent = t((cur && cur.group) || '');
    }

    function armChanList() {
        clearTimeout(state.chTimer);
        state.chTimer = setTimeout(closeChanList, 8000);
    }

    function openChanList() {
        if (!state.list.length) return;
        hideInfo();
        state.chOpen = true;
        state.chIdx = state.idx;
        $('chlist').classList.remove('hidden');
        renderChanList();
        armChanList();
    }

    function closeChanList() {
        state.chOpen = false;
        clearTimeout(state.chTimer);
        $('chlist').classList.add('hidden');
    }

    function armGuide() {
        clearTimeout(state.gTimer);
        state.gTimer = setTimeout(closeGuide, 15000);
    }

    function closeGuide() {
        state.gOpen = false;
        state.gList = null;
        clearTimeout(state.gTimer);
        try { Net.cancel('guide'); } catch (e) { }
        var g = $('guide');
        if (g) g.classList.add('hidden');
    }

    function guideMsg(msg) {
        $('guide-ul').innerHTML = '<li><span class="t">' + esc(t(msg)) + '</span></li>';
    }

    function renderGuide() {
        var list = state.gList || [];
        var n = list.length;
        var rows = 9;
        var start = Math.max(0, Math.min(state.gIdx - 4, n - rows));
        var end = Math.min(n, start + rows);
        var now = Date.now() / 1000;
        var html = '';
        for (var i = start; i < end; i++) {
            var p = list[i];
            var cls = (i === state.gIdx ? 'cursor' : '') + (p.s <= now && p.e > now ? ' now' : '') + (p.e <= now ? ' past' : '');
            var mark = p.e <= now && p.arch ? '<span class="a">⏪</span>' : '';
            html += '<li class="' + cls + '"><span class="n">' + esc(p.time) + '</span><span class="t">' + esc(p.title) + '</span>' + mark + '</li>';
        }
        $('guide-ul').innerHTML = html;
    }

    function openGuide() {
        var it = curItem();
        if (!it || isVod(it) || !state.source || state.source.type !== 'xtream') { showInfo(); return; }
        hideInfo();
        state.gOpen = true;
        state.gList = null;
        state.gIdx = 0;
        $('guide-title').textContent = t('Program guide') + ' · ' + it.name;
        $('guide').classList.remove('hidden');
        guideMsg('Loading…');
        armGuide();
        Xtream.guide(state.source, it).then(function (list) {
            if (!state.gOpen || curItem() !== it) return;
            if (!list.length) { state.gList = []; guideMsg('No guide data'); return; }
            var now = Date.now() / 1000;
            var cur = 0;
            list.forEach(function (p, j) { if (p.s <= now && p.e > now) cur = j; });
            state.gList = list;
            state.gIdx = cur;
            renderGuide();
        }).catch(function () {
            if (state.gOpen) { state.gList = []; guideMsg('Could not load this content.'); }
        });
    }

    function playGuide(p) {
        var it = curItem();
        var now = Date.now() / 1000;
        if (p.s > now) { toast(t('Not started yet')); return; }
        if (p.e > now) {
            closeGuide();
            if (state.catchupUrl) { state.catchupUrl = null; tune(state.idx, true); } else showInfo();
            return;
        }
        if (!p.arch) { toast(t('Catch-up not available')); return; }
        var url = Xtream.catchupUrl(state.source, it, p);
        if (!url) { toast(t('Catch-up not available')); return; }
        closeGuide();
        state.catchupUrl = url;
        tune(state.idx, true);
        toast(t('Catch-up: {x}', { x: p.title }));
    }

    function onGuideKey(k) {
        var list = state.gList;
        armGuide();
        if (k === KEY.BACK || k === KEY.BACK_ALT || k === KEY.STOP || k === KEY.LEFT) { closeGuide(); return; }
        if (k === KEY.EXIT) { exitApp(); return; }
        if (!list || !list.length) return;
        var n = list.length;
        switch (k) {
            case KEY.UP: state.gIdx = Math.max(0, state.gIdx - 1); renderGuide(); break;
            case KEY.DOWN: state.gIdx = Math.min(n - 1, state.gIdx + 1); renderGuide(); break;
            case KEY.CH_UP: state.gIdx = Math.max(0, state.gIdx - 8); renderGuide(); break;
            case KEY.CH_DOWN: state.gIdx = Math.min(n - 1, state.gIdx + 8); renderGuide(); break;
            case KEY.ENTER: playGuide(list[state.gIdx]); break;
        }
    }

    function onChanListKey(k) {
        var n = state.list.length;
        if (!n) { closeChanList(); return; }
        armChanList();
        switch (k) {
            case KEY.UP: state.chIdx = (state.chIdx - 1 + n) % n; renderChanList(); break;
            case KEY.DOWN: state.chIdx = (state.chIdx + 1) % n; renderChanList(); break;
            case KEY.CH_UP: state.chIdx = Math.max(0, state.chIdx - 9); renderChanList(); break;
            case KEY.CH_DOWN: state.chIdx = Math.min(n - 1, state.chIdx + 9); renderChanList(); break;
            case KEY.ENTER:
                var i = state.chIdx;
                closeChanList();
                if (i !== state.idx) tune(i, true); else showInfo();
                break;
            case KEY.BACK: case KEY.BACK_ALT: closeChanList(); closePlayer(); break;
            case KEY.STOP: closeChanList(); break;
            case KEY.EXIT: exitApp(); break;
        }
    }

    function onPlayerKey(k) {
        if (state.nextTimer) {
            clearInterval(state.nextTimer);
            state.nextTimer = null;
            toast(I18n.isRtl() ? 'تم إلغاء التشغيل التلقائي' : 'Autoplay cancelled');
            return;
        }
        if (state.chOpen) { onChanListKey(k); return; }
        if (state.gOpen) { onGuideKey(k); return; }
        var live = !isVod(curItem());
        if (k >= 48 && k <= 57) {
            if (live && k === 48 && !state.digits) lastChannel();
            else if (live) digit(k - 48);
            return;
        }
        var step = S.get('seekStep') * 1000;
        switch (k) {
            case KEY.CH_UP: if (live) zap(1); else tune(state.idx + 1); break;
            case KEY.CH_DOWN: if (live) zap(-1); else tune(state.idx - 1); break;
            case KEY.UP: if (live) zap(1); else showInfo(); break;
            case KEY.DOWN: if (live) zap(-1); else showInfo(); break;
            case KEY.LEFT: if (live) showInfo(); else seekBy(-step); break;
            case KEY.RIGHT: if (live) openGuide(); else seekBy(step); break;
            case KEY.RW: seekBy(-CFG.SEEK_LONG_MS); break;
            case KEY.FF: seekBy(CFG.SEEK_LONG_MS); break;
            case KEY.ENTER: if (live) openChanList(); else if ($('info').classList.contains('hidden')) showInfo(); else togglePause(); break;
            case KEY.INFO: toggleInfo(); break;
            case KEY.PLAY: case KEY.PAUSE: case KEY.PLAYPAUSE: if (!live) togglePause(); break;
            case KEY.YELLOW: toggleFav(curItem()); break;
            case KEY.GREEN: cycleAspect(); break;
            case KEY.BLUE: openTracks(); break;
            case KEY.RED: cycleSleep(); break;
            case KEY.STOP: case KEY.BACK: case KEY.BACK_ALT: closePlayer(); break;
            case KEY.EXIT: exitApp(); break;
        }
    }
    function onInputKey(k, el) {
        var done = k === KEY.IME_DONE;
        var cancel = k === KEY.IME_CANCEL;
        if (state.screen === 'form') {
            if (k === KEY.ENTER || k === KEY.DOWN) { el.blur(); formMove(1, true); }
            else if (k === KEY.UP) { el.blur(); formMove(-1, true); }
            else if (k === KEY.YELLOW) {
                el.blur();
                state.showPass = !state.showPass;
                toast(t(state.showPass ? 'Show password' : 'Hide password'));
                renderForm();
            }
            else if (k === KEY.BACK || cancel || done) { el.blur(); }
        } else if (state.screen === 'browse' && state.ctx) {
            if (k === KEY.ENTER || k === KEY.DOWN || k === KEY.UP || done) {
                el.blur();
                if (visibleList(state.ctx).length) focusItems(); else focusGroups();
            } else if (k === KEY.BACK || cancel) {
                el.blur();
                if (state.ctx.query && k === KEY.BACK) setQuery('');
                focusGroups();
            }
        } else {
            el.blur();
        }
    }

    function accel(k) {
        var now = Date.now();
        if (k === state.lastKey && now - state.lastKeyAt < 220) state.rep++;
        else state.rep = 0;
        state.lastKey = k;
        state.lastKeyAt = now;
        return state.rep > 30 ? 8 : state.rep > 14 ? 3 : 1;
    }

    function digitFallback(el, k) {
        var ch = String(k >= 96 ? k - 96 : k - 48);
        var before = el.value;
        setTimeout(function () {
            if (document.activeElement !== el || el.value !== before) return;
            var max = el.maxLength > 0 ? el.maxLength : 100000;
            if (before.length >= max) return;
            var s = before.length;
            var e2 = before.length;
            try {
                if (typeof el.selectionStart === 'number') { s = el.selectionStart; e2 = el.selectionEnd; }
            } catch (x) { }
            el.value = before.slice(0, s) + ch + before.slice(e2);
            try { el.setSelectionRange(s + 1, s + 1); } catch (y) { }
            var ev = document.createEvent('Event');
            ev.initEvent('input', true, true);
            el.dispatchEvent(ev);
        }, 40);
    }

    var NO_REPEAT = {};
    [KEY.ENTER, KEY.BACK, KEY.BACK_ALT, KEY.YELLOW, KEY.GREEN, KEY.RED, KEY.BLUE, KEY.INFO, KEY.STOP, KEY.PLAY, KEY.PAUSE, KEY.PLAYPAUSE, KEY.EXIT].forEach(function (c) { NO_REPEAT[c] = true; });

    function onKey(e) {
        var k = e.keyCode;
        var el = document.activeElement;
        if (el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA')) {
            if (el.id === 'backup-text') {
                if (k === KEY.IME_DONE || k === KEY.IME_CANCEL || k === KEY.BACK || k === KEY.BACK_ALT) el.blur();
                return;
            }
            if ((k >= 48 && k <= 57) || (k >= 96 && k <= 105)) digitFallback(el, k);
            onInputKey(k, el);
            return;
        }
        e.preventDefault();
        if (e.repeat && NO_REPEAT[k]) return;
        if (link.open) {
            if (k === KEY.BACK || k === KEY.BACK_ALT || k === KEY.ENTER) closeLink();
            else if (k === KEY.RED) { linkReset(); }
            else if (k === KEY.EXIT) exitApp();
            return;
        }
        if (state.qr) { if (k === KEY.BACK || k === KEY.BACK_ALT || k === KEY.ENTER) closeQr(); else if (k === KEY.EXIT) exitApp(); return; }
        if ((k === KEY.BACK || k === KEY.BACK_ALT) && state.screen === 'browse' && Date.now() - (state.closedAt || 0) < 700) return;
        if (state.modal) { onModalKey(k); return; }
        if (state.backup) { onBackupKey(k); return; }
        state.step = accel(k);
        if (I18n.isRtl() && state.screen !== 'player') {
            if (k === KEY.LEFT) k = KEY.RIGHT;
            else if (k === KEY.RIGHT) k = KEY.LEFT;
        }
        switch (state.screen) {
            case 'home': onHomeKey(k); break;
            case 'form': onFormKey(k); break;
            case 'settings': onSettingsKey(k); break;
            case 'hub': onHubKey(k); break;
            case 'browse': onBrowseKey(k); break;
            case 'player': onPlayerKey(k); break;
        }
    }

    var speedIv = null;
    var secondIv = null;
    function startBgTimers() {
        if (!speedIv) speedIv = setInterval(speedTick, 1500);
        if (!secondIv) secondIv = setInterval(secondTick, 1000);
    }
    function stopBgTimers() {
        clearInterval(speedIv); speedIv = null;
        clearInterval(secondIv); secondIv = null;
    }

    function onVisibility() {
        if (document.hidden) {
            stopBgTimers();
            clearInterval(state.tickTimer);
            state.tickTimer = null;
            clearInterval(state.loadTick);
            state.loadTick = null;
            if (state.screen === 'player' && state.list.length) {
                var it = curItem();
                savePos();
                state.resumeAt = isVod(it) ? Player.position() : 0;
                state.suspended = true;
                clearTimeout(state.retryTimer);
                clearTimeout(state.zapTimer);
                state.streamToken++;
                Player.stop();
            }
        } else {
            startBgTimers();
            secondTick();
            if (state.screen === 'player' && !state.tickTimer) startTick();
            if (state.loadingOn && !state.loadTick) showLoading(true);
        }
        if (!document.hidden && state.suspended) {
            state.suspended = false;
            if (state.screen === 'player' && state.list.length) {
                setInfoState(t('Connecting…'));
                showInfo();
                startStream();
            }
        }
    }
    function loadSources() {
        var list = readJSON(SOURCES_KEY, []);
        if (!list.length && CFG.DEFAULT_PLAYLIST_URL) {
            list = [{ id: 'default', type: 'm3u', name: 'Default playlist', url: CFG.DEFAULT_PLAYLIST_URL, user: '', pass: '', format: 'm3u8', mac: '' }];
            writeJSON(SOURCES_KEY, list);
        }
        return list;
    }

    function hideSplash() {
        var sp = $('splash');
        sp.classList.add('out');
        setTimeout(function () { sp.parentNode && sp.parentNode.removeChild(sp); }, 600);
    }

    function init() {
        purgeLegacy();
        startBgTimers();
        state.sources = loadSources();
        state.hidden = readJSON(HID_KEY, {}) || {};
        state.favorites = readJSON(FAV_KEY, {});
        state.pos = readJSON(POS_KEY, {});
        state.hist = readJSON(HIST_KEY, {});
        state.pin = (readJSON(PIN_KEY, {}) || {}).h || '';
        state.locked = readJSON(LOCK_KEY, {}) || {};
        restoreMirrors();

        applyAppearance();
        Player.init({ buffering: handleBuffering, error: handleError, ended: onEnded, stall: handleStall, progress: handleProgress });
        Player.setAspect(S.get('aspect'));
        Player.setProfile(S.get('buffer'));
        Player.setEngine(S.get('engine'));
        Store.purgeExpired();
        window.addEventListener('online', onOnline);
        window.addEventListener('offline', onOffline);
        document.addEventListener('visibilitychange', onVisibility);
        $('info-logo').onerror = function () {
            if (this.getAttribute('src') !== PH_LOGO) this.setAttribute('src', PH_LOGO);
        };
        registerKeys();
        linkInit();

        $('search').addEventListener('input', function () {
            if (state.screen === 'browse' && state.ctx) setQuery(this.value);
        });
        document.addEventListener('keydown', onKey);

        var lastId = null;
        try { lastId = localStorage.getItem(LAST_KEY); } catch (e) { }
        var last = state.sources.filter(function (s) { return s.id === lastId; })[0];

        if (S.get('autoOpenLast') && last) {
            openSource(last);
        } else {
            showScreen('home');
            renderHome();
        }
        setTimeout(hideSplash, 900);
        if (link.fresh) setTimeout(function () { if (state.screen === 'home' && !state.modal) openLink(); }, 1600);
    }

    /* ---------- phone link ---------- */
    var LINK_KEY = 'lumora.tvid';
    var LINK_KEYS = {
        up: 38, down: 40, left: 37, right: 39, ok: 13, back: 10009, info: 457, stop: 413, playpause: 10252,
        rw: 412, ff: 417, chup: 427, chdown: 428, red: 403, green: 404, yellow: 405, blue: 406
    };
    var link = { id: '', topic: '', es: null, up: false, phoneAt: 0, phoneName: '', seen: {}, nSeen: 0, lastT: 0, hb: null, rt: null, pt: null, lastSend: 0, sendT: null, open: false, tok: 0, fresh: false };

    function newTvId() {
        var out = '';
        try {
            var a = new Uint8Array(8);
            (window.crypto || window.msCrypto).getRandomValues(a);
            for (var i = 0; i < a.length; i++) out += (a[i] < 16 ? '0' : '') + a[i].toString(16);
        } catch (e) {
            for (var j = 0; j < 16; j++) out += Math.floor(Math.random() * 16).toString(16);
        }
        return out.replace(/(.{4})(?=.)/g, '$1-');
    }

    function linkInit() {
        var id = '';
        try { id = localStorage.getItem(LINK_KEY) || ''; } catch (e) { }
        link.fresh = !/^[0-9a-f]{4}(-[0-9a-f]{4}){3}$/.test(id);
        if (link.fresh) {
            id = newTvId();
            try { localStorage.setItem(LINK_KEY, id); } catch (e2) { }
        }
        link.id = id;
        link.topic = 'lumora-' + id.replace(/-/g, '');
        linkListen();
        clearInterval(link.hb);
        link.hb = setInterval(function () {
            if (state.screen === 'player' || Date.now() - link.lastSend > 11000) linkSend();
            if (link.phoneAt && Date.now() - link.phoneAt > 90000) { link.phoneAt = 0; if (link.open) renderLink(); }
        }, 6000);
        setTimeout(function () { linkSend(); }, 1500);
    }

    function linkStop() {
        link.tok++;
        link.up = false;
        clearTimeout(link.rt);
        clearTimeout(link.pt);
        if (link.es) { try { link.es.close(); } catch (e) { } link.es = null; }
    }

    function linkListen() {
        linkStop();
        var tok = link.tok;
        var since = link.lastT || (Math.floor(Date.now() / 1000) - 60);
        if (typeof EventSource === 'undefined') { linkPoll(tok); return; }
        var es;
        try { es = new EventSource(CFG.RELAY + link.topic + '/sse?since=' + since); } catch (e) { linkPoll(tok); return; }
        link.es = es;
        es.onopen = function () { if (tok === link.tok) link.up = true; };
        es.onmessage = function (ev) {
            if (tok !== link.tok) return;
            var o = null;
            try { o = JSON.parse(ev.data); } catch (e) { return; }
            linkMsg(o);
        };
        es.onerror = function () {
            if (tok !== link.tok) return;
            link.up = false;
            try { es.close(); } catch (e) { }
            link.es = null;
            clearTimeout(link.rt);
            link.rt = setTimeout(function () { if (tok === link.tok) linkListen(); }, 5000);
        };
    }

    function linkPoll(tok) {
        if (tok !== link.tok) return;
        var x = new XMLHttpRequest();
        var since = link.lastT || (Math.floor(Date.now() / 1000) - 60);
        x.open('GET', CFG.RELAY + link.topic + '/json?poll=1&since=' + since, true);
        x.timeout = 10000;
        function next() { if (tok === link.tok) link.pt = setTimeout(function () { linkPoll(tok); }, 3000); }
        x.onload = function () {
            if (tok !== link.tok) return;
            if (x.status >= 200 && x.status < 300) {
                link.up = true;
                String(x.responseText || '').split('\n').forEach(function (ln) {
                    if (!ln) return;
                    var o = null;
                    try { o = JSON.parse(ln); } catch (e) { }
                    if (o) linkMsg(o);
                });
            }
            next();
        };
        x.onerror = function () { link.up = false; next(); };
        x.ontimeout = next;
        try { x.send(); } catch (e) { next(); }
    }

    function linkMsg(o) {
        if (!o || o.event !== 'message' || !o.message) return;
        if (o.id) {
            if (link.seen[o.id]) return;
            link.seen[o.id] = 1;
            if (++link.nSeen > 300) { link.seen = {}; link.nSeen = 0; }
        }
        if (o.time && o.time > link.lastT) link.lastT = o.time;
        var m = null;
        try { m = JSON.parse(o.message); } catch (e) { return; }
        if (!m || typeof m !== 'object') return;
        if (!m.lumora) {
            if (m.type && m.url) linkSource(m);
            return;
        }
        switch (m.op) {
            case 'hello':
                var fresh = !link.phoneAt || Date.now() - link.phoneAt > 60000;
                link.phoneAt = Date.now();
                link.phoneName = String(m.name || 'Phone').slice(0, 40);
                linkSend('hello_ack');
                if (link.open) renderLink();
                else if (fresh) toast(t('Phone connected'));
                if (!link.open && state.screen === 'settings') renderSettings();
                break;
            case 'bye':
                link.phoneAt = 0;
                if (link.open) renderLink();
                toast(t('Phone disconnected'));
                if (!link.open && state.screen === 'settings') renderSettings();
                break;
            case 'cast': link.phoneAt = Date.now(); castPlay(m); break;
            case 'key': link.phoneAt = Date.now(); linkKey(m.key); break;
            case 'pause': if (state.screen === 'player' && !state.paused) togglePause(); break;
            case 'resume': if (state.screen === 'player' && state.paused) togglePause(); break;
            case 'toggle': if (state.screen === 'player') togglePause(); break;
            case 'seek':
                if (state.screen === 'player' && isVod(curItem()) && typeof m.to === 'number') { Player.seek(Math.max(0, m.to)); showInfo(); }
                break;
            case 'stop': if (state.screen === 'player') closePlayer(); break;
            case 'source': if (m.source) linkSource(m.source); break;
        }
    }

    function linkKey(k) {
        var code = LINK_KEYS[k] || (/^[0-9]$/.test(String(k)) ? 48 + parseInt(k, 10) : 0);
        if (!code) return;
        onKey({ keyCode: code, repeat: false, preventDefault: function () { } });
        linkSoon();
    }

    function linkSource(d) {
        var type = String(d.type || '');
        var url = String(d.url || '').trim();
        if (TYPE_ORDER.indexOf(type) < 0 || !/^https?:\/\//i.test(url)) return;
        var dup = state.sources.some(function (s) { return s.type === type && s.url === url && (s.user || '') === (d.user || ''); });
        if (dup) { toast(t('Source saved')); return; }
        var src = { id: 's' + Date.now().toString(36), type: type, name: String(d.name || hostOf({ url: url })).slice(0, 60), url: url.slice(0, 2048), user: String(d.user || ''), pass: String(d.pass || ''), format: d.format === 'ts' ? 'ts' : 'm3u8', mac: String(d.mac || '') };
        if (type === 'stalker' && !src.mac) src.mac = Stalker.makeMac();
        state.sources.push(src);
        writeJSON(SOURCES_KEY, state.sources);
        if (state.screen === 'home') renderHome();
        toast(t('Source saved'));
    }

    function castPlay(m) {
        var url = String(m.url || '').trim();
        if (!/^https?:\/\//i.test(url)) return;
        var kind = m.kind === 'ep' ? 'ep' : (m.kind === 'vod' || m.kind === 'series') ? 'vod' : 'live';
        var h = 0;
        for (var i = 0; i < url.length; i++) h = (h * 31 + url.charCodeAt(i)) | 0;
        var name = String(m.title || '').trim() || hostOf({ url: url });
        var it = {
            key: 'cast:' + (h >>> 0).toString(36), id: 'cast', name: name.slice(0, 120), kind: kind, group: t('Playing from phone'),
            logo: typeof m.logo === 'string' && /^https?:\/\//i.test(m.logo) ? m.logo : '', url: url, direct: true,
            start: kind === 'live' ? 0 : Math.max(0, (parseInt(m.start, 10) || 0) * 1000), detailed: true, plot: ''
        };
        if (state.screen !== 'player') state.castBack = state.screen;
        else if (!curItem() || !curItem().direct) state.castBack = 'browse';
        stopBg();
        state.resumeAt = 0;
        launchPlayer({ cast: true }, [it], 0);
        linkSoon();
    }

    function closeCast() {
        clearTimeout(state.zapTimer);
        clearTimeout(state.retryTimer);
        state.streamToken++;
        try { stopTick(); } catch (e) { }
        try { Player.stop(); } catch (e2) { }
        state.list = [];
        state.playCtx = null;
        state.bg = null;
        state.closedAt = Date.now();
        var back = state.castBack || 'home';
        if (back === 'hub' && state.source) { showScreen('hub'); renderHub(); }
        else if (back === 'browse' && state.ctx) { showScreen('browse'); try { renderBrowse(); } catch (e3) { } }
        else { showScreen('home'); renderHome(); }
        try { screenSaver(true); } catch (e4) { }
        linkSoon();
    }

    function linkStatus() {
        var it = state.screen === 'player' ? curItem() : null;
        var o = { lumora: 1, op: 'status', id: link.id, name: 'Lumora TV', ver: CFG.VERSION, ts: Date.now(), state: 'idle' };
        if (it) {
            o.title = it.name;
            o.kind = it.kind;
            o.state = state.paused ? 'paused' : (state.healthy ? 'playing' : 'buffering');
            try { o.pos = Math.floor(Player.position()); o.dur = Math.floor(Player.duration()); } catch (e) { }
        }
        return o;
    }

    function linkSend(op) {
        var o = linkStatus();
        if (op) o.op = op;
        link.lastSend = Date.now();
        try {
            var x = new XMLHttpRequest();
            x.open('POST', CFG.RELAY + link.topic + '-tv', true);
            x.timeout = 8000;
            x.send(JSON.stringify(o));
        } catch (e) { }
    }

    function linkSoon() {
        var wait = Math.max(300, 2500 - (Date.now() - link.lastSend));
        clearTimeout(link.sendT);
        link.sendT = setTimeout(function () { linkSend(); }, wait);
    }

    function renderLink() {
        $('link-id').textContent = link.id;
        if (link.qrFor !== link.id) {
            try { QR.draw($('link-qr'), 'lumora://pair?t=lumora-' + link.id.replace(/-/g, '')); link.qrFor = link.id; } catch (e) { }
        }
        var ok = !!link.phoneAt;
        var st = $('link-state');
        st.textContent = ok ? t('Phone connected ✓') + (link.phoneName ? '  ·  ' + link.phoneName : '') : t('Waiting for phone…');
        st.className = ok ? 'on' : '';
    }

    function openLink() {
        hideModal();
        renderLink();
        $('link').classList.remove('hidden');
        link.open = true;
        linkSend();
    }

    function closeLink() {
        $('link').classList.add('hidden');
        link.open = false;
        if (state.screen === 'settings') renderSettings();
    }

    function linkReset() {
        try { localStorage.removeItem(LINK_KEY); } catch (e) { }
        link.lastT = 0; link.seen = {}; link.phoneAt = 0;
        linkInit();
        renderLink();
        toast(t('New code created'));
        if (state.screen === 'settings') renderSettings();
    }

    init();
})();
