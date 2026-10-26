window.CONFIG = {
    DEFAULT_PLAYLIST_URL: '',
    TMDB_API_KEY: '',
    CONNECT_URL: 'https://mejri02.github.io/lumora-connect/',
    RELAY: 'https://ntfy.sh/',
    STALKER_PROXY: 'https://lumora-proxy.soso1236u.workers.dev/',
    DEFAULT_UA: 'VLC/3.0.20 LibVLC/3.0.20',
    FETCH_TIMEOUT_MS: 30000,
    IDLE_TIMEOUT_MS: 25000,
    NET_RETRIES: 2,
    NET_CONCURRENCY: 6,
    DIGIT_TIMEOUT_MS: 1200,
    RETRY_DELAY_MS: 3000,
    FAST_RETRY_MS: 250,
    RETRY_BASE_MS: 500,
    RETRY_MAX_MS: 20000,
    RETRY_JITTER: 0.3,
    SOFT_RETRIES: 3,
    STALL_LIVE_MS: 9000,
    STALL_VOD_MS: 60000,
    CONNECT_TIMEOUT_MS: 15000,
    PROBE_TIMEOUT_MS: 4000,
    LIB_TIMEOUT_MS: 10000,
    TEST_TIMEOUT_MS: 25000,
    LOADING_SLOW_MS: 8000,
    ZAP_DELAY_MS: 220,
    SEARCH_DELAY_MS: 70,
    SAVE_POS_MS: 10000,
    SEEK_LONG_MS: 60000,
    CACHE_FRESH_MS: 43200000,
    CACHE_MAX_STALE_MS: 604800000,
    DETAIL_TTL_MS: 86400000,
    SERIES_TTL_MS: 21600000,
    EPG_PERSIST_TTL_MS: 300000,
    HIST_MAX: 60,
    URL_MAX_LEN: 2048,
    NAME_MAX_LEN: 60,
    CRED_MAX_LEN: 128,
    LIBS: {
        hls: ['js/lib/hls.min.js'],
        mpegts: ['js/lib/mpegts.js']
    },
    EPG_MAX_BYTES: 15000000,
    EPG_TTL_MS: 21600000,
    PIN_LEN: 4,
    VERSION: '3.8.0'
};

window.Validate = (function () {
    'use strict';

    var CTRL_RE = /[\u0000-\u001F\u007F-\u009F\u200B-\u200F\u202A-\u202E\u2066-\u2069\uFEFF]/g;
    var URL_RE = /^(https?):\/\/(?:([^\s\/?#@]*)@)?(\[[0-9a-f:.]+\]|[^\s\/?#:@]+)(?::(\d*))?([\/?#]\S*)?$/i;
    var HOST_RE = /^[a-z0-9\u00a1-\uffff]([a-z0-9\u00a1-\uffff._-]*[a-z0-9\u00a1-\uffff])?$/i;
    var IPV4_RE = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/;
    var STREAM_SCHEMES = /^(https?|rtmps?|rtsp|rtp|udp|mmsh?):\/\//i;

    function cfg(k, d) { return (window.CONFIG && window.CONFIG[k] !== undefined) ? window.CONFIG[k] : d; }

    function clean(s) {
        return String(s === undefined || s === null ? '' : s).replace(CTRL_RE, '').trim();
    }

    function res(ok, value, error) {
        return { ok: ok, value: value, error: ok ? '' : error };
    }

    function validHost(host) {
        if (host.charAt(0) === '[') return /^\[[0-9a-f:.]+\]$/i.test(host);
        var m = IPV4_RE.exec(host);
        if (m) {
            for (var i = 1; i <= 4; i++) if (parseInt(m[i], 10) > 255) return false;
            return true;
        }
        if (/^[\d.]+$/.test(host)) return false;
        return host.length <= 253 && HOST_RE.test(host) && host.indexOf('..') === -1;
    }

    function httpUrl(input) {
        var s = clean(input).replace(/ /g, '%20');
        if (!s) return res(false, '', 'Enter the URL');
        if (s.length > cfg('URL_MAX_LEN', 2048)) return res(false, s, 'URL is too long');
        if (!/^https?:\/\//i.test(s)) return res(false, s, 'URL must start with http:// or https://');
        var m = URL_RE.exec(s);
        if (!m) return res(false, s, 'Invalid URL');
        if (!validHost(m[3])) return res(false, s, 'Invalid server address');
        if (m[4] !== undefined) {
            var port = parseInt(m[4], 10);
            if (!m[4] || !(port >= 1 && port <= 65535)) return res(false, s, 'Invalid port number');
        }
        return res(true, s.replace(/^HTTP/, 'http').replace(/^https?:\/\//i, function (x) { return x.toLowerCase(); }), '');
    }

    function streamUrl(input) {
        var s = clean(input);
        if (!s) return res(false, '', 'Enter the URL');
        if (s.length > 8192) return res(false, s, 'URL is too long');
        if (!STREAM_SCHEMES.test(s)) return res(false, s, 'Unsupported stream address');
        return res(true, s, '');
    }

    function server(input) {
        var r = httpUrl(input);
        if (!r.ok) return r;
        var v = r.value.replace(/[?#].*$/, '').replace(/\/(player_api|get|panel_api|xmltv)\.php$/i, '').replace(/\/+$/, '');
        return res(true, v, '');
    }

    function parseXtream(input) {
        var s = clean(input);
        var q = /[?&]username=([^&#]*)/i.exec(s);
        var p = /[?&]password=([^&#]*)/i.exec(s);
        if (!q || !p || !/\/(get|player_api|panel_api)\.php/i.test(s)) return null;
        var base = server(s);
        if (!base.ok) return null;
        try { return { url: base.value, user: decodeURIComponent(q[1]), pass: decodeURIComponent(p[1]) }; } catch (e) { return null; }
    }

    function mac(input) {
        var s = clean(input).toUpperCase().replace(/-/g, ':');
        if (/^[0-9A-F]{12}$/.test(s)) s = s.replace(/(..)(?=.)/g, '$1:');
        if (!/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/.test(s)) return res(false, s, 'MAC must look like 00:1A:79:XX:XX:XX');
        return res(true, s, '');
    }

    function user(input) {
        var s = clean(input);
        if (!s) return res(false, '', 'Username and password are required');
        if (s.length > cfg('CRED_MAX_LEN', 128)) return res(false, s, 'Username or password is too long');
        return res(true, s, '');
    }

    function pass(input) {
        var s = String(input === undefined || input === null ? '' : input).replace(CTRL_RE, '').trim();
        if (!s) return res(false, '', 'Username and password are required');
        if (s.length > cfg('CRED_MAX_LEN', 128)) return res(false, s, 'Username or password is too long');
        return res(true, s, '');
    }

    function name(input) {
        return clean(input).replace(/\s+/g, ' ').slice(0, cfg('NAME_MAX_LEN', 60));
    }

    function playlistText(text) {
        var head = String(text || '').slice(0, 2048).replace(/^\uFEFF/, '').trim();
        if (!head) return res(false, '', 'Empty response from server');
        if (/^<(!doctype|html|\?xml|head|body)/i.test(head)) return res(false, '', 'The address returned a web page, not a playlist');
        if (head.charAt(0) === '{' || head.charAt(0) === '[') return res(false, '', 'The address returned data, not a playlist');
        return res(true, '', '');
    }

    return {
        clean: clean, httpUrl: httpUrl, streamUrl: streamUrl, server: server, parseXtream: parseXtream,
        mac: mac, user: user, pass: pass, name: name, playlistText: playlistText
    };
})();
