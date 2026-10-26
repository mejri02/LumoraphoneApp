var Settings = (function () {
    'use strict';

    var KEY = 'lumora.settings';

    function detectLang() {
        try {
            var l = String(navigator.language || navigator.userLanguage || (navigator.languages && navigator.languages[0]) || 'en').toLowerCase();
            if (l.indexOf('ar') === 0) return 'ar';
        } catch (e) { }
        return 'en';
    }

    var DEFAULTS = {
        lang: detectLang(),
        theme: 'violet',
        clock: '24',
        animations: false,
        aspect: 'fit',
        resume: true,
        autoNext: true,
        seekStep: 10,
        infoTimeout: 6,
        retryLimit: 5,
        buffer: 'stable',
        engine: 'auto',
        parental: false,
        lockCats: true,
        keepAwake: true,
        diag: false,
        sort: 'default',
        autoOpenLast: true,
        resumeAsk: true,
        bgRefresh: true,
        cacheHours: 12,
        tmdbKey: ''
    };
    var data = {};

    function load() {
        var saved = {};
        try { saved = JSON.parse(localStorage.getItem(KEY) || '{}') || {}; } catch (e) { saved = {}; }
        data = {};
        Object.keys(DEFAULTS).forEach(function (k) {
            data[k] = saved[k] !== undefined ? saved[k] : DEFAULTS[k];
        });
    }

    function save() {
        try { localStorage.setItem(KEY, JSON.stringify(data)); } catch (e) { }
    }

    function get(k) { return data[k]; }

    function set(k, v) {
        data[k] = v;
        save();
    }

    function reset() {
        data = {};
        Object.keys(DEFAULTS).forEach(function (k) { data[k] = DEFAULTS[k]; });
        save();
    }

    load();
    return { get: get, set: set, reset: reset };
})();
