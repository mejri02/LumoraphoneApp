var Player = (function () {
    'use strict';

    var av = (window.webapis && window.webapis.avplay) ? window.webapis.avplay : null;
    var video = document.getElementById('html-video');
    var root = document.documentElement;
    var h = { buffering: function () {}, error: function () {}, ended: function () {}, stall: function () {}, progress: function () {} };

    var current = null;
    var currentLive = true;
    var aspect = 'fit';
    var profile = 'balanced';
    var pref = 'auto';
    var bump = 0;
    var stallTimer = null;
    var seekTimer = null;
    var softTimer = null;
    var seekTarget = null;
    var playing = false;
    var rebuf = [];
    var bufOn = false;
    var lastT = 0;
    var nativeTimer = null;

    var session = 0;
    var engineTok = 0;
    var engineName = null;
    var chain = [];
    var chainIdx = 0;
    var soft = 0;
    var mediaFix = 0;
    var pendingStart = 0;
    var startPos = 0;
    var curUa = '';
    var det = null;
    var hlsInst = null;
    var tsInst = null;
    var libs = {};
    var previewMode = false;
    var meas = { kbps: 0, at: 0, busy: false };
    var measX = null;
    var hlsBound = [];
    var tsBound = [];
    var vh = {};
    var vBound = false;
    var engMemo = {};
    var engMemoN = 0;
    function memoKey(u) { return String(u || '').split('#')[0].split('?')[0]; }
    function extKey(u) { var m = /\.([a-z0-9]{2,5})$/i.exec(memoKey(u)); return m ? 'ext:' + m[1].toLowerCase() : ''; }
    function memoGet(u) { return engMemo[memoKey(u)] || engMemo[extKey(u)] || null; }
    function memoSet(u, n) {
        var k = memoKey(u);
        if (!k || !n) return;
        var ek = extKey(u);
        if (ek && !currentLive) engMemo[ek] = n;
        if (!engMemo[k]) {
            if (engMemoN >= 300) { engMemo = {}; engMemoN = 0; }
            engMemoN++;
        }
        engMemo[k] = n;
    }

    function measure() {
        if (meas.busy || !current || !/^https?:/i.test(current)) return;
        if (Date.now() - meas.at < 15000) return;
        meas.busy = true;
        var t0 = Date.now();
        var loaded = 0;
        var fin = false;
        var x = new XMLHttpRequest();
        measX = x;
        function done() {
            if (fin) return;
            fin = true;
            meas.busy = false;
            if (measX === x) measX = null;
            meas.at = Date.now();
            var dt = (Date.now() - t0) / 1000;
            if (loaded > 30000 && dt > 0.05) meas.kbps = Math.round(loaded * 8 / 1000 / dt);
            try { x.abort(); } catch (e) { }
        }
        try {
            x.open('GET', current, true);
            x.setRequestHeader('Range', 'bytes=0-262143');
            x.responseType = 'arraybuffer';
            x.timeout = 6000;
            x.onprogress = function (e) { loaded = e.loaded || loaded; if (loaded >= 262144) done(); };
            x.onload = function () { loaded = (x.response && x.response.byteLength) || loaded; done(); };
            x.onerror = done;
            x.ontimeout = done;
            x.send();
        } catch (e2) { meas.busy = false; measX = null; }
    }

    function speed() {
        var kbps = 0;
        try {
            if (engineName === 'hls' && hlsInst && hlsInst.bandwidthEstimate) kbps = Math.round(hlsInst.bandwidthEstimate / 1000);
            else if (engineName === 'mpegts' && tsInst && tsInst.statisticsInfo && tsInst.statisticsInfo.speed) kbps = Math.round(tsInst.statisticsInfo.speed * 8);
        } catch (e) { }
        if (!kbps) {
            measure();
            if (Date.now() - meas.at < 60000) kbps = meas.kbps;
        }
        return kbps;
    }
    var checkTimer = null;

    var HAS_MSE = !!(window.MediaSource || window.WebKitMediaSource);
    var NATIVE_HLS = false;
    try { NATIVE_HLS = !!video.canPlayType('application/vnd.apple.mpegurl'); } catch (e) { }

    var AV_MODES = { fit: 'PLAYER_DISPLAY_MODE_LETTER_BOX', fill: 'PLAYER_DISPLAY_MODE_FULL_SCREEN', zoom: 'PLAYER_DISPLAY_MODE_CROPPED_FULL' };
    var CSS_FIT = { fit: 'contain', fill: 'fill', zoom: 'cover' };
    var ORDER = ['fast', 'balanced', 'stable'];

    var PROFILES = {
        fast: { live: [3, 8], vod: [3, 6], pre: 3000, start: 'LOWEST', sync: 3, tsMax: 8, tsMin: 1 },
        balanced: { live: [8, 20], vod: [5, 10], pre: 6000, start: 'AVERAGE', sync: 4, tsMax: 20, tsMin: 3 },
        stable: { live: [15, 35], vod: [8, 16], pre: 10000, start: 'HIGHEST', sync: 6, tsMax: 40, tsMin: 5 }
    };

    var EXT = {
        m3u8: 'hls', m3u: 'hls',
        ts: 'ts', mts: 'ts', m2ts: 'ts', mpegts: 'ts',
        flv: 'flv',
        mp4: 'mp4', m4v: 'mp4', mov: 'mp4', '3gp': 'mp4',
        webm: 'webm', mkv: 'mkv', avi: 'avi', wmv: 'wmv', mpg: 'mpeg', mpeg: 'mpeg', ogv: 'webm',
        mp3: 'audio', aac: 'audio', m4a: 'audio', ogg: 'audio', oga: 'audio', opus: 'audio', wav: 'audio', flac: 'audio', weba: 'audio',
        mpd: 'dash', ism: 'mss'
    };

    var MSG = {
        network: 'Stream connection failed',
        media: 'Media decoding error',
        unsupported: 'Unsupported stream format',
        timeout: 'Stream timed out'
    };

    function cfg(k, d) { return (window.CONFIG && window.CONFIG[k] !== undefined) ? window.CONFIG[k] : d; }

    function effName() {
        var i = Math.min(ORDER.length - 1, ORDER.indexOf(profile) + bump);
        return ORDER[Math.max(0, i)];
    }

    function effective() { return PROFILES[effName()]; }

    function backoffDelay(n) {
        var base = cfg('RETRY_BASE_MS', 500);
        var max = cfg('RETRY_MAX_MS', 20000);
        var j = cfg('RETRY_JITTER', 0.3);
        var d = Math.min(max, base * Math.pow(2, Math.max(0, n - 1)));
        return Math.round(d * (1 - j + Math.random() * 2 * j));
    }

    function mkErr(kind, raw) {
        var e = new Error(MSG[kind] || MSG.media);
        e.kind = kind;
        e.fatal = kind === 'unsupported';
        e.raw = raw;
        return e;
    }

    function detect(url) {
        var out = { proto: 'http', type: 'unknown' };
        var m = /^([a-z][a-z0-9+.-]*):\/\//i.exec(url || '');
        var scheme = m ? m[1].toLowerCase() : 'http';
        out.proto = (scheme === 'https') ? 'http' : scheme;
        if (out.proto !== 'http') {
            out.type = out.proto;
            return out;
        }
        var clean = String(url).split('#')[0];
        var qi = clean.indexOf('?');
        var path = qi >= 0 ? clean.slice(0, qi) : clean;
        var query = qi >= 0 ? clean.slice(qi + 1) : '';
        var em = /\.([a-z0-9]{2,5})$/i.exec(path.replace(/\/+$/, ''));
        if (em && EXT[em[1].toLowerCase()]) { out.type = EXT[em[1].toLowerCase()]; return out; }
        var qm = /(?:^|&)(?:extension|ext|format|output|container)=([a-z0-9]+)/i.exec(query);
        if (qm && EXT[qm[1].toLowerCase()]) { out.type = EXT[qm[1].toLowerCase()]; return out; }
        if (/\.m3u8(?:[\/?&]|$)/i.test(clean) || /\/hls\//i.test(path)) { out.type = 'hls'; return out; }
        if (/\.ism(?:l)?\/manifest/i.test(path)) { out.type = 'mss'; return out; }
        return out;
    }

    function typeFromMime(m) {
        m = String(m || '').toLowerCase();
        if (!m) return '';
        if (/mpegurl/.test(m)) return 'hls';
        if (/mp2t|mpeg-?ts/.test(m)) return 'ts';
        if (/x-flv/.test(m)) return 'flv';
        if (/dash\+xml/.test(m)) return 'dash';
        if (/vnd\.ms-sstr/.test(m)) return 'mss';
        if (/matroska/.test(m)) return 'mkv';
        if (/webm/.test(m)) return 'webm';
        if (/mp4/.test(m)) return 'mp4';
        if (/^audio\//.test(m)) return 'audio';
        return '';
    }

    function buildChain(d, live) {
        var A = av ? ['avplay'] : [];
        var H = [];
        if (d.proto !== 'http') return A;
        switch (d.type) {
            case 'hls':
                if (HAS_MSE) H.push('hls');
                H.push('native');
                break;
            case 'ts':
            case 'flv':
                if (HAS_MSE) H.push('mpegts');
                H.push('native');
                break;
            case 'dash':
            case 'mss':
                H.push('native');
                break;
            case 'unknown':
                if (live) {
                    if (HAS_MSE) { H.push('mpegts'); H.push('hls'); }
                    H.push('native');
                } else {
                    H.push('native');
                    if (HAS_MSE) H.push('hls');
                }
                break;
            default:
                H.push('native');
        }
        var all = (pref === 'html5' || (previewMode && H.length && HAS_MSE)) ? H.concat(A) : A.concat(H);
        var m = pref === 'auto' ? memoGet(current) : null;
        if (m && all.indexOf(m) > 0) all = [m].concat(all.filter(function (x) { return x !== m; }));
        return all.filter(function (x, i) { return all.indexOf(x) === i; });
    }

    function loadLib(name) {
        if (libs[name]) return libs[name];
        var g = name === 'hls' ? 'Hls' : 'mpegts';
        if (window[g]) return (libs[name] = Promise.resolve(window[g]));
        var urls = ((window.CONFIG && window.CONFIG.LIBS) || {})[name] || [];
        var p = new Promise(function (resolve, reject) {
            var i = 0;
            (function next() {
                if (i >= urls.length) { reject(new Error('Library unavailable')); return; }
                var s = document.createElement('script');
                var done = false;
                var timer = setTimeout(function () { fin(false); }, cfg('LIB_TIMEOUT_MS', 10000));
                function fin(ok) {
                    if (done) return;
                    done = true;
                    clearTimeout(timer);
                    if (ok && window[g]) { resolve(window[g]); return; }
                    try { if (s.parentNode) s.parentNode.removeChild(s); } catch (e) { }
                    i++;
                    next();
                }
                s.onload = function () { fin(true); };
                s.onerror = function () { fin(false); };
                s.async = true;
                s.src = urls[i];
                document.head.appendChild(s);
            })();
        });
        libs[name] = p;
        p.then(null, function () { if (libs[name] === p) delete libs[name]; });
        return p;
    }

    var rect = null;
    function rectArgs() {
        return rect ? [Math.round(rect.x), Math.round(rect.y), Math.round(rect.w), Math.round(rect.h)] : [0, 0, 1920, 1080];
    }
    function applyRect() {
        if (!av || engineName !== 'avplay') return;
        try {
            if (av.getState() === 'NONE') return;
            var a = rectArgs();
            av.setDisplayRect(a[0], a[1], a[2], a[3]);
            av.setDisplayMethod(AV_MODES[aspect]);
        } catch (e) { }
    }
    function setRect(r) {
        rect = r || null;
        applyRect();
    }

    function applyAspect() {
        if (av && engineName === 'avplay') {
            try { if (av.getState() !== 'NONE') av.setDisplayMethod(AV_MODES[aspect]); } catch (e) { }
        }
        video.style.objectFit = CSS_FIT[aspect];
    }

    function setAspect(mode) {
        aspect = AV_MODES[mode] ? mode : 'fit';
        applyAspect();
    }

    function setProfile(name) {
        profile = PROFILES[name] ? name : 'balanced';
        bump = 0;
        rebuf = [];
    }

    function setEngine(name) {
        pref = (name === 'avplay' || name === 'html5') ? name : 'auto';
    }

    function disarm() {
        clearTimeout(stallTimer);
        stallTimer = null;
    }

    function arm(ms) {
        disarm();
        var token = current;
        stallTimer = setTimeout(function () {
            if (!current || current !== token) return;
            if (engineName === 'avplay' && !playing && chainIdx + 1 < chain.length) { fail('unsupported', 'avplay timeout', engineTok); return; }
            h.stall(mkErr('timeout'));
        }, ms);
    }

    function stallMs() {
        return currentLive ? cfg('STALL_LIVE_MS', 9000) : cfg('STALL_VOD_MS', 20000);
    }

    function noteRebuffer() {
        if (!playing) return;
        var now = Date.now();
        rebuf.push(now);
        while (rebuf.length && now - rebuf[0] > 90000) rebuf.shift();
        if (rebuf.length >= 3 && bump < 2) {
            bump++;
            rebuf = [];
        }
    }

    function bufStart() { bufOn = true; noteRebuffer(); arm(stallMs()); h.buffering(true); }
    function scheduleCheck() {
        var nativeVod = engineName === 'native' && !currentLive && detect(current).type !== 'audio';
        if (!previewMode && !nativeVod) return;
        clearTimeout(checkTimer);
        var tok = engineTok;
        checkTimer = setTimeout(function () {
            if (tok !== engineTok || !current) return;
            var i = streamInfo();
            if (i.w > 0) return;
            if (chainIdx + 1 < chain.length) { teardown(); chainIdx++; startEngine(); }
        }, 3000);
    }

    function bufEnd() { bufOn = false; clearTimeout(nativeTimer); playing = true; if (current && engineName) memoSet(current, engineName); soft = 0; mediaFix = 0; disarm(); if (engineName === 'avplay') applyRect(); scheduleCheck(); h.buffering(false); }

    function classifyAv(e) {
        var s = String((e && (e.name || e.message)) || e || '');
        if (/NOT_SUPPORTED|INVALID_URI|UNSUPPORTED|FILE_TYPE|CODEC|DRM|NotSupported/i.test(s)) return 'unsupported';
        if (/CONNECTION|NETWORK|TIMEOUT|NO_SUCH_FILE|SERVER|HTTP/i.test(s)) return 'network';
        return 'media';
    }

    function hOn(inst, evt, fn) { inst.on(evt, fn); hlsBound.push([evt, fn]); }
    function tOn(inst, evt, fn) { inst.on(evt, fn); tsBound.push([evt, fn]); }

    function bindVideo() {
        if (vBound) return;
        vBound = true;
        Object.keys(vh).forEach(function (k) { video[k] = vh[k]; });
    }

    function unbindVideo() {
        vBound = false;
        Object.keys(vh).forEach(function (k) { video[k] = null; });
    }

    function abortMeasure() {
        if (measX) { try { measX.onprogress = measX.onload = measX.onerror = measX.ontimeout = null; measX.abort(); } catch (e) { } }
        measX = null;
        meas.busy = false;
    }

    function teardown() {
        clearTimeout(nativeTimer);
        bufOn = false;
        lastT = 0;
        clearTimeout(softTimer);
        softTimer = null;
        unbindVideo();
        if (hlsInst) {
            var hi = hlsInst;
            hlsInst = null;
            hlsBound.forEach(function (b) { try { hi.off(b[0], b[1]); } catch (e0) { } });
            hlsBound = [];
            try { hi.stopLoad(); } catch (e) { }
            try { hi.detachMedia(); } catch (e7) { }
            try { hi.destroy(); } catch (e8) { }
        }
        if (tsInst) {
            var ti = tsInst;
            tsInst = null;
            tsBound.forEach(function (b) { try { ti.off(b[0], b[1]); } catch (e0) { } });
            tsBound = [];
            try { ti.pause(); } catch (e1) { }
            try { ti.unload(); } catch (e2) { }
            try { ti.detachMediaElement(); } catch (e3) { }
            try { ti.destroy(); } catch (e4) { }
        }
        if (av) {
            try {
                var s = av.getState();
                if (s === 'PLAYING' || s === 'PAUSED') av.stop();
                if (s !== 'NONE') av.close();
            } catch (e5) { }
        }
        if (video.getAttribute('src') !== null || video.src) {
            try {
                video.pause();
                video.removeAttribute('src');
                video.load();
                video.currentTime = 0;
            } catch (e6) { }
        }
    }

    function fail(kind, raw, tok) {
        if (tok !== undefined && tok !== engineTok) return;
        if (!current) return;
        disarm();
        clearTimeout(softTimer);
        if (kind === 'unsupported' && chainIdx + 1 < chain.length) {
            teardown();
            chainIdx++;
            startEngine();
            return;
        }
        h.error(mkErr(kind, raw));
    }

    function soften(kind, raw, tok, retry) {
        if (tok !== engineTok || !current) return;
        if (soft >= cfg('SOFT_RETRIES', 3)) { fail(kind, raw, tok); return; }
        soft++;
        h.buffering(true);
        arm(stallMs() + backoffDelay(soft));
        clearTimeout(softTimer);
        softTimer = setTimeout(function () {
            if (tok !== engineTok || !current) return;
            try { retry(); } catch (e) { fail(kind, e, tok); }
        }, backoffDelay(soft));
    }

    function tuneBuffer() {
        var p = effective();
        var v = currentLive ? p.live : p.vod;
        try { av.setBufferingParam('PLAYER_BUFFER_FOR_PLAY', 'PLAYER_BUFFER_SIZE_IN_SECOND', v[0]); } catch (e) { }
        try { av.setBufferingParam('PLAYER_BUFFER_FOR_RESUME', 'PLAYER_BUFFER_SIZE_IN_SECOND', v[1]); } catch (e) { }
        if (det && det.type === 'hls') {
            try { av.setStreamingProperty('ADAPTIVE_INFO', 'STARTBITRATE=' + (previewMode ? 'LOWEST' : p.start)); } catch (e) { }
        }
    }

    function startAv(tok) {
        var url = current;
        var start = startPos;
        try {
            av.open(url);
            if (curUa) { try { av.setStreamingProperty('USER_AGENT', curUa); } catch (eu) { } }
            tuneBuffer();
            var ra = rectArgs();
            av.setDisplayRect(ra[0], ra[1], ra[2], ra[3]);
            av.setDisplayMethod(AV_MODES[aspect]);
            av.prepareAsync(function () {
                if (tok !== engineTok) return;
                disarm();
                applyRect();
                if (start > 0) {
                    av.seekTo(start, function () { av.play(); }, function () { av.play(); });
                } else {
                    av.play();
                }
                playing = true;
                setTimeout(function () { if (tok === engineTok) applyRect(); }, 350);
                setTimeout(function () { if (tok === engineTok) applyRect(); }, 1500);
            }, function (e) {
                fail(chainIdx + 1 < chain.length ? 'unsupported' : classifyAv(e), e, tok);
            });
        } catch (e) {
            fail(chainIdx + 1 < chain.length ? 'unsupported' : classifyAv(e), e, tok);
        }
    }

    function startNative(tok) {
        video.style.objectFit = CSS_FIT[aspect];
        video.src = current;
        clearTimeout(nativeTimer);
        if (!currentLive) {
            nativeTimer = setTimeout(function () {
                if (tok !== engineTok || !current || playing) return;
                if (video.readyState >= 2) return;
                fail('unsupported', 'native load timeout rs=' + video.readyState + ' ns=' + video.networkState, tok);
            }, 20000);
        }
        var p = video.play();
        if (p && p.catch) {
            p.catch(function (e) {
                if (tok !== engineTok || !e) return;
                if (e.name === 'NotSupportedError') fail('unsupported', e, tok);
                else if (e.name !== 'AbortError' && e.name !== 'NotAllowedError') fail('media', e, tok);
            });
        }
    }

    function startHls(tok) {
        loadLib('hls').then(function (Hls) {
            if (tok !== engineTok) return;
            if (!Hls || !Hls.isSupported()) { fail('unsupported', 'hls unsupported', tok); return; }
            var p = effective();
            var live = currentLive;
            var connect = cfg('CONNECT_TIMEOUT_MS', 15000);
            var inst = new Hls({
                enableWorker: true,
                lowLatencyMode: false,
                capLevelToPlayerSize: previewMode,
                startLevel: previewMode ? 0 : undefined,
                maxBufferSize: 40 * 1000 * 1000,
                maxBufferHole: 0.5,
                liveSyncDurationCount: p.sync,
                maxBufferLength: previewMode ? 8 : (live ? Math.max(10, p.live[1] * 2) : p.vod[1] * 2),
                maxMaxBufferLength: previewMode ? 15 : (live ? 60 : 120),
                backBufferLength: previewMode ? 5 : (live ? 15 : 30),
                manifestLoadingTimeOut: connect,
                manifestLoadingMaxRetry: 2,
                manifestLoadingRetryDelay: 500,
                manifestLoadingMaxRetryTimeout: 8000,
                levelLoadingTimeOut: connect,
                levelLoadingMaxRetry: 3,
                levelLoadingRetryDelay: 500,
                levelLoadingMaxRetryTimeout: 8000,
                fragLoadingTimeOut: 20000,
                fragLoadingMaxRetry: 4,
                fragLoadingRetryDelay: 500,
                fragLoadingMaxRetryTimeout: 8000
            });
            hlsInst = inst;
            hOn(inst, Hls.Events.MANIFEST_PARSED, function () {
                if (tok !== engineTok) return;
                var pr = video.play();
                if (pr && pr.catch) pr.catch(function () { });
            });
            hOn(inst, Hls.Events.ERROR, function (ev, data) {
                if (tok !== engineTok || !data || !data.fatal) return;
                var details = String(data.details || '');
                if (/manifestParsingError|manifestIncompatibleCodecsError|levelEmptyError/i.test(details)) {
                    fail('unsupported', data, tok);
                    return;
                }
                if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
                    var code = data.response && data.response.code;
                    if (code === 404 || code === 403 || code === 401) { fail('network', data, tok); return; }
                    soften('network', data, tok, function () { inst.startLoad(); });
                } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
                    mediaFix++;
                    if (mediaFix === 1) soften('media', data, tok, function () { inst.recoverMediaError(); });
                    else if (mediaFix === 2) soften('media', data, tok, function () { inst.swapAudioCodec(); inst.recoverMediaError(); });
                    else fail('media', data, tok);
                } else {
                    fail('media', data, tok);
                }
            });
            inst.attachMedia(video);
            inst.loadSource(current);
        }).then(null, function (e) {
            fail('unsupported', e, tok);
        });
    }

    function startTs(tok) {
        loadLib('mpegts').then(function (lib) {
            if (tok !== engineTok) return;
            var ok = false;
            try { ok = !!(lib && lib.getFeatureList && lib.getFeatureList().mseLivePlayback); } catch (e) { }
            if (!ok) { fail('unsupported', 'mpegts unsupported', tok); return; }
            var p = effective();
            var live = currentLive;
            var inst = lib.createPlayer({
                type: det && det.type === 'flv' ? 'flv' : 'mpegts',
                isLive: live,
                url: current
            }, {
                enableWorker: true,
                enableStashBuffer: true,
                autoCleanupSourceBuffer: true,
                liveBufferLatencyChasing: false,
                liveBufferLatencyMaxLatency: p.tsMax,
                liveBufferLatencyMinRemain: p.tsMin,
                lazyLoad: false
            });
            tsInst = inst;
            tOn(inst, lib.Events.ERROR, function (type, detail, info) {
                if (tok !== engineTok) return;
                var d = String(detail || '');
                if (type === lib.ErrorTypes.NETWORK_ERROR) {
                    var code = info && info.code;
                    if (code === 404 || code === 403 || code === 401) { fail('network', info, tok); return; }
                    soften('network', info, tok, function () { inst.unload(); inst.load(); inst.play(); });
                } else if (type === lib.ErrorTypes.MEDIA_ERROR) {
                    if (/Unsupported|Unrecommended/i.test(d)) { fail('unsupported', info, tok); return; }
                    mediaFix++;
                    if (mediaFix <= 2) soften('media', info, tok, function () { inst.unload(); inst.load(); inst.play(); });
                    else fail('media', info, tok);
                } else {
                    fail('media', info, tok);
                }
            });
            inst.attachMediaElement(video);
            inst.load();
            var pr = inst.play();
            if (pr && pr.catch) pr.catch(function () { });
        }).then(null, function (e) {
            fail('unsupported', e, tok);
        });
    }

    function startEngine() {
        var tok = ++engineTok;
        var name = chain[chainIdx];
        engineName = name;
        mediaFix = 0;
        soft = 0;
        playing = false;
        pendingStart = name === 'avplay' ? 0 : startPos;
        root.classList.toggle('vid', name !== 'avplay');
        if (name !== 'avplay') bindVideo();
        arm(currentLive ? cfg('CONNECT_TIMEOUT_MS', 15000) : Math.max(cfg('CONNECT_TIMEOUT_MS', 15000), 45000));
        if (name === 'avplay') startAv(tok);
        else if (name === 'native') startNative(tok);
        else if (name === 'hls') startHls(tok);
        else if (name === 'mpegts') startTs(tok);
        else fail('unsupported', 'no engine', tok);
    }

    function init(opts) {
        h.buffering = opts.buffering || h.buffering;
        h.error = opts.error || h.error;
        h.ended = opts.ended || h.ended;
        h.stall = opts.stall || h.stall;
        h.progress = opts.progress || h.progress;

        if (av) {
            av.setListener({
                onbufferingstart: function () { if (engineName === 'avplay') bufStart(); },
                onbufferingprogress: function (p) { if (engineName === 'avplay') h.progress(p); },
                onbufferingcomplete: function () { if (engineName === 'avplay') bufEnd(); },
                onerror: function (e) { if (engineName === 'avplay') fail(classifyAv(e), e, engineTok); },
                onstreamcompleted: function () { if (engineName === 'avplay') { disarm(); h.ended(); } }
            });
        }

        video.preload = 'auto';
        vh.onwaiting = function () { if (engineName !== 'avplay' && current) bufStart(); };
        vh.onstalled = function () { if (engineName !== 'avplay' && current && video.readyState < 3) bufStart(); };
        vh.onplaying = function () { if (engineName !== 'avplay' && current) bufEnd(); };
        vh.oncanplay = function () {
            if (engineName === 'avplay') return;
            disarm();
            if (bufOn && !video.paused && current) bufEnd();
        };
        vh.ontimeupdate = function () {
            if (engineName === 'avplay' || !current) return;
            var t = video.currentTime || 0;
            if (bufOn && !video.paused && t - lastT > 0.25 && video.readyState >= 3) bufEnd();
            lastT = t;
        };
        vh.onseeked = function () {
            if (engineName !== 'avplay' && current && bufOn && !video.paused && video.readyState >= 3) bufEnd();
        };
        vh.onloadedmetadata = function () {
            if (engineName !== 'avplay' && pendingStart > 0) {
                try { video.currentTime = pendingStart / 1000; } catch (e) { }
                pendingStart = 0;
            }
        };
        vh.onerror = function () {
            if (engineName !== 'native' || !current) return;
            var c = video.error && video.error.code;
            fail(c === 4 ? 'unsupported' : (c === 2 ? 'network' : 'media'), c, engineTok);
        };
        vh.onended = function () { if (engineName !== 'avplay' && current) { disarm(); h.ended(); } };
        vh.onprogress = function () {
            if (engineName === 'avplay') return;
            try {
                var b = video.buffered;
                if (b.length && video.duration && isFinite(video.duration)) h.progress(Math.min(100, Math.round(b.end(b.length - 1) / video.duration * 100)));
            } catch (e) { }
        };

        if (!av && HAS_MSE) setTimeout(function () { loadLib('hls').then(null, function () { }); }, 2000);
    }

    function stop() {
        current = null;
        session++;
        engineTok++;
        playing = false;
        seekTarget = null;
        pendingStart = 0;
        startPos = 0;
        clearTimeout(seekTimer);
        clearTimeout(checkTimer);
        clearTimeout(verifyTimer);
        lastSeek = null;
        meas.kbps = 0; meas.at = 0;
        abortMeasure();
        clearTimeout(softTimer);
        softTimer = null;
        rebuf = [];
        disarm();
        teardown();
        engineName = null;
        root.classList.remove('vid');
    }

    function play(url, opts) {
        opts = opts || {};
        stop();
        var sess = session;
        var chk = window.Validate ? Validate.streamUrl(url) : { ok: true, value: url };
        currentLive = opts.live !== false;
        previewMode = !!opts.preview;
        startPos = opts.start || 0;
        curUa = String(opts.ua || cfg('DEFAULT_UA', '')).replace(/[\u0000-\u001F\u007F]/g, '').slice(0, 300);
        if (!chk.ok) {
            current = String(url || '');
            setTimeout(function () { if (sess === session) { current = null; h.error(mkErr('unsupported', chk.error)); } }, 0);
            return;
        }
        current = chk.value;
        det = detect(current);
        chain = buildChain(det, currentLive);
        chainIdx = 0;
        soft = 0;
        mediaFix = 0;
        arm(currentLive ? cfg('CONNECT_TIMEOUT_MS', 15000) : Math.max(cfg('CONNECT_TIMEOUT_MS', 15000), 45000));

        if (!chain.length) {
            setTimeout(function () { if (sess === session) { current = null; h.error(mkErr('unsupported', det.proto)); } }, 0);
            return;
        }

        if (det.proto === 'http' && det.type === 'unknown' && chain[0] !== 'avplay' && window.Net && Net.probe) {
            Net.probe(current).then(function (info) {
                if (sess !== session) return;
                if (info) {
                    var t = typeFromMime(info.type);
                    if (t) { det.type = t; chain = buildChain(det, currentLive); }
                }
                startEngine();
            });
        } else {
            startEngine();
        }
    }

    function release() {
        stop();
        rect = null;
        bump = 0;
        det = null;
        chain = [];
        chainIdx = 0;
        curUa = '';
        if (av) { try { if (av.getState() !== 'NONE') av.close(); } catch (e) { } }
        try { video.style.objectFit = CSS_FIT[aspect]; } catch (e2) { }
    }

    function isAv() { return engineName === 'avplay' && av; }

    function toggle() {
        if (isAv()) {
            try {
                var s = av.getState();
                if (s === 'PLAYING') { disarm(); av.pause(); return false; }
                if (s === 'PAUSED') { av.play(); return true; }
            } catch (e) { }
            return false;
        }
        if (video.paused) {
            var p = video.play();
            if (p && p.catch) p.catch(function () { });
            return true;
        }
        disarm();
        video.pause();
        return false;
    }

    function seek(ms) {
        seekTarget = Math.max(0, Math.floor(ms));
        clearTimeout(seekTimer);
        seekTimer = setTimeout(function () {
            var target = seekTarget;
            if (target === null) return;
            lastSeek = { t: target, at: Date.now() };
            rawSeek(target, 0);
        }, 220);
    }

    var lastSeek = null;
    var verifyTimer = null;

    function verifySeek(target, tries) {
        clearTimeout(verifyTimer);
        var tok = engineTok;
        verifyTimer = setTimeout(function () {
            if (tok !== engineTok || !current || seekTarget !== null) return;
            var pos = position();
            if (Math.abs(pos - target) > 20000 && tries < 2) rawSeek(target, tries + 1);
        }, 3500);
    }

    function rawSeek(target, tries) {
        if (isAv()) {
            try {
                av.seekTo(target, function () {
                    if (seekTarget === target) seekTarget = null;
                    verifySeek(target, tries);
                }, function () {
                    if (tries < 2) {
                        setTimeout(function () { if (seekTarget === target || seekTarget === null) rawSeek(target, tries + 1); }, 700);
                    } else if (seekTarget === target) {
                        seekTarget = null;
                    }
                });
            } catch (e) { if (seekTarget === target) seekTarget = null; }
        } else {
            try { video.currentTime = target / 1000; } catch (e2) { }
            if (seekTarget === target) seekTarget = null;
            verifySeek(target, tries);
        }
    }

    function recentSeek() {
        return (lastSeek && Date.now() - lastSeek.at < 20000) ? lastSeek.t : null;
    }

    function position() {
        if (seekTarget !== null) return seekTarget;
        if (isAv()) {
            try { return av.getCurrentTime() || 0; } catch (e) { return 0; }
        }
        return (video.currentTime || 0) * 1000;
    }

    function duration() {
        if (isAv()) {
            try { return av.getDuration() || 0; } catch (e) { return 0; }
        }
        return isFinite(video.duration) ? video.duration * 1000 : 0;
    }

    function engine() { return engineName; }

    var LANGS = {
        en: 'English', eng: 'English', ar: 'Arabic', ara: 'Arabic', fr: 'French', fre: 'French', fra: 'French',
        de: 'German', ger: 'German', deu: 'German', es: 'Spanish', spa: 'Spanish', tr: 'Turkish', tur: 'Turkish',
        it: 'Italian', ita: 'Italian', ru: 'Russian', rus: 'Russian', fa: 'Persian', per: 'Persian', fas: 'Persian',
        hi: 'Hindi', hin: 'Hindi', ku: 'Kurdish', kur: 'Kurdish', pt: 'Portuguese', por: 'Portuguese'
    };

    function langOf(code) {
        var c = String(code || '').toLowerCase();
        if (!c || c === 'und' || c === 'unk') return '';
        return LANGS[c] || c.toUpperCase();
    }

    function avInfo(x) {
        var o = {};
        try { o = JSON.parse(x.extra_info || '{}') || {}; } catch (e) { }
        var low = {};
        Object.keys(o).forEach(function (k) { low[k.toLowerCase()] = o[k]; });
        return low;
    }

    function tracks(type) {
        var out = [];
        var i;
        try {
            if (engineName === 'avplay' && av) {
                var all = av.getTotalTrackInfo() || [];
                var cur = av.getCurrentStreamInfo() || [];
                for (i = 0; i < all.length; i++) {
                    if (all[i].type !== type) continue;
                    var info = avInfo(all[i]);
                    var sel = false;
                    for (var c = 0; c < cur.length; c++) {
                        if (cur[c].type === type && cur[c].index === all[i].index) sel = true;
                    }
                    out.push({ index: all[i].index, lang: langOf(info.language || info.track_lang), selected: sel });
                }
            } else if (engineName === 'hls' && hlsInst) {
                var hl = type === 'AUDIO' ? hlsInst.audioTracks : hlsInst.subtitleTracks;
                var hs = type === 'AUDIO' ? hlsInst.audioTrack : hlsInst.subtitleTrack;
                for (i = 0; i < (hl || []).length; i++) {
                    out.push({ index: i, lang: langOf(hl[i].lang) || hl[i].name || '', selected: i === hs });
                }
            } else if (type === 'AUDIO' && video.audioTracks) {
                for (i = 0; i < video.audioTracks.length; i++) {
                    var at = video.audioTracks[i];
                    out.push({ index: i, lang: langOf(at.language) || at.label || '', selected: !!at.enabled });
                }
            } else if (type === 'TEXT' && video.textTracks) {
                var n = 0;
                for (i = 0; i < video.textTracks.length; i++) {
                    var tt = video.textTracks[i];
                    if (tt.kind !== 'subtitles' && tt.kind !== 'captions') continue;
                    out.push({ index: i, lang: langOf(tt.language) || tt.label || '', selected: tt.mode === 'showing' });
                    n++;
                }
            }
        } catch (e) { }
        return out;
    }

    function selectTrack(type, index) {
        var i;
        try {
            if (engineName === 'avplay' && av) {
                if (type === 'TEXT') {
                    if (index < 0) { av.setSilentSubtitle(true); return true; }
                    av.setSilentSubtitle(false);
                }
                av.setSelectTrack(type, index);
                return true;
            }
            if (engineName === 'hls' && hlsInst) {
                if (type === 'AUDIO') hlsInst.audioTrack = index;
                else { hlsInst.subtitleDisplay = index >= 0; hlsInst.subtitleTrack = index; }
                return true;
            }
            if (type === 'AUDIO' && video.audioTracks) {
                for (i = 0; i < video.audioTracks.length; i++) video.audioTracks[i].enabled = (i === index);
                return true;
            }
            if (type === 'TEXT' && video.textTracks) {
                for (i = 0; i < video.textTracks.length; i++) {
                    var tt = video.textTracks[i];
                    if (tt.kind !== 'subtitles' && tt.kind !== 'captions') continue;
                    tt.mode = (i === index) ? 'showing' : 'disabled';
                }
                return true;
            }
        } catch (e) { }
        return false;
    }

    var ENGINE_NAME = { avplay: 'AVPlay', hls: 'Hls.js', mpegts: 'mpegts.js', native: 'HTML5' };

    function streamInfo() {
        var o = { engine: ENGINE_NAME[engineName] || '', w: 0, h: 0, kbps: 0 };
        try {
            if (engineName === 'avplay' && av) {
                var cur = av.getCurrentStreamInfo() || [];
                for (var i = 0; i < cur.length; i++) {
                    if (cur[i].type !== 'VIDEO') continue;
                    var x = avInfo(cur[i]);
                    o.w = parseInt(x.width, 10) || 0;
                    o.h = parseInt(x.height, 10) || 0;
                    var br = parseInt(x.bit_rate || x.bitrate, 10) || 0;
                    o.kbps = br > 100000 ? Math.round(br / 1000) : br;
                }
            } else {
                o.w = video.videoWidth || 0;
                o.h = video.videoHeight || 0;
                if (engineName === 'hls' && hlsInst && hlsInst.levels && hlsInst.currentLevel >= 0) {
                    var lv = hlsInst.levels[hlsInst.currentLevel];
                    if (lv) {
                        o.w = lv.width || o.w;
                        o.h = lv.height || o.h;
                        o.kbps = Math.round((lv.bitrate || 0) / 1000);
                    }
                }
            }
        } catch (e) { }
        return o;
    }

    function diag() {
        var o = [];
        try {
            if (engineName && engineName !== 'avplay') {
                var b = video.buffered;
                o.push('rs=' + video.readyState + ' ns=' + video.networkState + ' buf=' + (b.length ? b.end(b.length - 1).toFixed(0) + 's' : '0'));
                if (video.error) o.push('err=' + video.error.code);
            }
            o.push('chain=' + chain.join('>') + '#' + chainIdx);
            o.push('kbps=' + meas.kbps);
        } catch (e) { }
        return o.join('  ');
    }

    return {
        diag: diag, init: init, play: play, stop: stop, release: release, toggle: toggle, seek: seek,
        position: position, speed: speed, recentSeek: recentSeek, duration: duration, setAspect: setAspect, setRect: setRect, setProfile: setProfile,
        setEngine: setEngine, detect: detect, backoffDelay: backoffDelay, engine: engine,
        tracks: tracks, selectTrack: selectTrack, streamInfo: streamInfo
    };
})();
