(function () {
    'use strict';

    var CFG = window.CONFIG;
    var S = Settings;
    var MODS = { m3u: M3U, xtream: Xtream, stalker: Stalker };
    var TYPE_NAME = { m3u: 'M3U Playlist', xtream: 'Xtream Codes', stalker: 'Stalker Portal' };
    var KINDS = ['live', 'vod', 'series', 'favs'];
    var KIND_NAME = { live: 'Live TV', vod: 'Movies', series: 'Series', favs: 'Favorites' };
    var SRC_KEY = 'lumora.sources';
    var FAV_KEY = 'lumora.phone.favs';
    var PAGE = 70;

    /* ---------- i18n (phone strings; engine strings come from core/i18n.js) ---------- */
    var AR = {
        'Sources': 'المصادر', 'Library': 'المكتبة', 'Remote': 'الريموت', 'Settings': 'الإعدادات',
        'Live TV': 'البث المباشر', 'Movies': 'أفلام', 'Series': 'مسلسلات', 'Favorites': 'المفضلة',
        'Your sources': 'مصادرك', 'Add source': 'إضافة مصدر', 'Edit source': 'تعديل المصدر', 'Name': 'الاسم',
        'Playlist URL': 'رابط القائمة', 'Server URL': 'رابط السيرفر', 'Username': 'اسم المستخدم', 'Password': 'كلمة المرور',
        'Portal URL': 'رابط البوابة', 'MAC address': 'عنوان MAC', 'Format': 'الصيغة', 'Save': 'حفظ', 'Cancel': 'إلغاء',
        'Delete': 'حذف', 'Test': 'اختبار', 'Connection OK': 'الاتصال ناجح', 'Saved': 'تم الحفظ', 'Type': 'النوع',
        'No sources yet. Add an M3U playlist, an Xtream Codes account or a Stalker portal.': 'لا توجد مصادر بعد. أضف قائمة M3U أو حساب Xtream Codes أو بوابة Stalker.',
        'Search': 'بحث', 'All': 'الكل', 'Loading…': 'جارٍ التحميل…', 'Nothing found': 'لا توجد نتائج', 'Choose a source first': 'اختر مصدراً أولاً',
        'Play on phone': 'تشغيل على الهاتف', 'Play on TV': 'تشغيل على التلفاز', 'Send to TV': 'إرسال للتلفاز', 'Sent to TV': 'تم الإرسال للتلفاز',
        'Pair with TV first': 'اربط التلفاز أولاً', 'Episodes': 'الحلقات', 'Back': 'رجوع', 'Season {n}': 'الموسم {n}',
        'Pair with your TV': 'الربط مع التلفاز', 'Not paired': 'غير مربوط', 'Paired': 'مربوط', 'TV online': 'التلفاز متصل', 'Waiting for TV…': 'بانتظار التلفاز…',
        'Enter the code shown on the TV': 'أدخل الرمز الظاهر على التلفاز', 'Pair code': 'رمز الربط', 'Pair': 'ربط', 'Unpair': 'إلغاء الربط',
        'Invalid code': 'رمز غير صالح', 'Now playing on TV': 'يعمل الآن على التلفاز', 'Nothing playing': 'لا شيء يعمل',
        'Send source to TV': 'إرسال المصدر للتلفاز', 'Source sent to TV': 'تم إرسال المصدر للتلفاز', 'Language': 'اللغة', 'Relay server': 'سيرفر الترحيل',
        'Player engine': 'مشغّل الفيديو', 'Automatic': 'تلقائي', 'Clear cache': 'مسح الذاكرة المؤقتة', 'Cache cleared': 'تم مسح الذاكرة المؤقتة',
        'About': 'حول', 'Could not load this content.': 'تعذّر تحميل المحتوى.', 'Playing on phone': 'يعمل على الهاتف',
        'Tip: the TV app must be open and paired.': 'ملاحظة: يجب أن يكون تطبيق التلفاز مفتوحاً ومربوطاً.', 'Channels': 'القنوات',
        'Added to favorites': 'أُضيف إلى المفضلة', 'Removed from favorites': 'أُزيل من المفضلة', 'Pair help': 'افتح الربط في تطبيق التلفاز ثم أدخل الرمز هنا.',
        'Could not reach the relay': 'تعذّر الوصول إلى سيرفر الترحيل', 'Stop': 'إيقاف', 'Fit': 'ملاءمة', 'Fill': 'تمديد', 'Zoom': 'تكبير',
        'items': 'عنصر', 'Load more': 'عرض المزيد', 'Send': 'إرسال', 'Close': 'إغلاق'
    };
    function lang() { return S.get('lang') === 'ar' ? 'ar' : 'en'; }
    function L(s, v) {
        var r = s;
        if (lang() === 'ar' && AR[s] !== undefined) r = AR[s];
        else if (lang() === 'ar') r = I18n.t(s);
        if (v) r = r.replace(/\{(\w+)\}/g, function (x, k) { return v[k] !== undefined ? v[k] : x; });
        return r;
    }

    /* ---------- helpers ---------- */
    function $(id) { return document.getElementById(id); }
    function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
    function rj(k, d) { try { var v = JSON.parse(localStorage.getItem(k)); return v == null ? d : v; } catch (e) { return d; } }
    function wj(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { } }
    function fmt(ms) {
        var s = Math.max(0, Math.floor(ms / 1000)), h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
        return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (x < 10 ? '0' : '') + x;
    }
    var toastT = null;
    function toast(msg, bad) {
        var t = $('toast');
        t.textContent = msg;
        t.className = bad ? 'err' : '';
        clearTimeout(toastT);
        toastT = setTimeout(function () { t.className = 'hidden'; }, 2600);
    }
    function vibe(n) { try { if (navigator.vibrate) navigator.vibrate(n || 12); } catch (e) { } }

    /* ---------- state ---------- */
    var state = {
        tab: 'sources', sources: rj(SRC_KEY, []), favs: rj(FAV_KEY, {}),
        src: null, kind: 'live', cat: '', q: '', items: [], view: [], cats: [], shown: PAGE,
        loading: false, error: '', series: null, form: null, playing: null, tvInfo: Pair.info()
    };
    var searchT = null;

    function srcById(id) { for (var i = 0; i < state.sources.length; i++) if (state.sources[i].id === id) return state.sources[i]; return null; }
    function saveSources() { wj(SRC_KEY, state.sources); }
    function favKey(src, it) { return src.id + ':' + (it.kind || '') + ':' + it.id; }

    /* ---------- chrome ---------- */
    var ICONS = {
        sources: '<svg viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h10"/></svg>',
        library: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="13" rx="3"/><path d="M9 21h6M12 17v4M10 8.5l5 2.5-5 2.5z"/></svg>',
        remote: '<svg viewBox="0 0 24 24"><rect x="7" y="2" width="10" height="20" rx="4"/><circle cx="12" cy="8" r="2"/><path d="M10 14h4M10 18h4"/></svg>',
        settings: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>'
    };
    function renderNav() {
        $('nav').innerHTML = ['sources', 'library', 'remote', 'settings'].map(function (t) {
            return '<button data-act="tab" data-v="' + t + '" class="' + (state.tab === t ? 'on' : '') + '">' + ICONS[t] + '<span>' + esc(L(t === 'sources' ? 'Sources' : t === 'library' ? 'Library' : t === 'remote' ? 'Remote' : 'Settings')) + '</span></button>';
        }).join('');
    }
    function renderChip() {
        var i = state.tvInfo, c = $('chip');
        c.className = 'chip' + (i.paired ? (i.online ? ' on' : ' wait') : '');
        $('chip-t').textContent = !i.paired ? L('Not paired') : i.online ? L('TV online') : L('Waiting for TV…');
    }
    function setTab(t) {
        state.tab = t;
        if (t !== 'library') state.series = null;
        render();
        if (t === 'library') {
            var key = state.src + '|' + state.kind;
            if (state.loadedKey !== key) { state.loadedKey = key; loadCatalog(); }
        }
    }
    function render() {
        renderNav();
        renderChip();
        var m = $('main');
        var scroll = 0;
        if (state.tab === 'sources') m.innerHTML = state.form ? viewForm() : viewSources();
        else if (state.tab === 'library') { m.innerHTML = viewLibrary(); renderList(); }
        else if (state.tab === 'remote') m.innerHTML = viewRemote();
        else m.innerHTML = viewSettings();
        m.scrollTop = scroll;
        renderMini();
    }

    /* ---------- sources ---------- */
    function viewSources() {
        var h = '<h2>' + esc(L('Your sources')) + '</h2>';
        if (!state.sources.length) h += '<div class="empty">' + esc(L('No sources yet. Add an M3U playlist, an Xtream Codes account or a Stalker portal.')) + '</div>';
        else h += '<div class="list">' + state.sources.map(function (s) {
            return '<div class="src"><div class="ic">' + esc(s.type === 'm3u' ? 'M3U' : s.type === 'xtream' ? 'XC' : 'ST') + '</div>' +
                '<div class="tx" data-act="open-src" data-id="' + esc(s.id) + '"><div class="nm">' + esc(s.name || s.url) + '</div><div class="sb">' + esc(TYPE_NAME[s.type]) + ' · ' + esc(s.url) + '</div></div>' +
                '<button class="ib sm" data-act="send-src" data-id="' + esc(s.id) + '" title="' + esc(L('Send source to TV')) + '">&#128250;</button>' +
                '<button class="ib sm" data-act="edit-src" data-id="' + esc(s.id) + '">&#9998;</button></div>';
        }).join('') + '</div>';
        h += '<div style="margin-top:16px"><button class="btn pri full" data-act="add-src">+ ' + esc(L('Add source')) + '</button></div>';
        return h;
    }

    function fld(key, label, val, type, ph) {
        return '<div class="fld"><label>' + esc(L(label)) + '</label><input data-f="' + key + '" type="' + (type || 'text') + '" value="' + esc(val || '') + '" placeholder="' + esc(ph || '') + '" autocapitalize="off" autocorrect="off" spellcheck="false"></div>';
    }
    function viewForm() {
        var f = state.form, v = f.v;
        var h = '<h2>' + esc(L(f.isNew ? 'Add source' : 'Edit source')) + '</h2><div class="card">';
        h += '<div class="fld"><label>' + esc(L('Type')) + '</label><select data-f="type">' + ['m3u', 'xtream', 'stalker'].map(function (t) { return '<option value="' + t + '"' + (v.type === t ? ' selected' : '') + '>' + TYPE_NAME[t] + '</option>'; }).join('') + '</select></div>';
        h += fld('name', 'Name', v.name, 'text', 'My playlist');
        if (v.type === 'm3u') h += fld('url', 'Playlist URL', v.url, 'url', 'http://host/playlist.m3u');
        if (v.type === 'xtream') {
            h += fld('url', 'Server URL', v.url, 'url', 'http://host:8080') + fld('user', 'Username', v.user) + fld('pass', 'Password', v.pass);
            h += '<div class="fld"><label>' + esc(L('Format')) + '</label><select data-f="format"><option value="m3u8"' + (v.format !== 'ts' ? ' selected' : '') + '>HLS (m3u8)</option><option value="ts"' + (v.format === 'ts' ? ' selected' : '') + '>MPEG-TS</option></select></div>';
        }
        if (v.type === 'stalker') h += fld('url', 'Portal URL', v.url, 'url', 'http://host/c/') + fld('mac', 'MAC address', v.mac, 'text', '00:1A:79:xx:xx:xx');
        h += '</div><div class="row" style="margin-top:14px"><button class="btn" data-act="form-cancel">' + esc(L('Cancel')) + '</button><button class="btn" data-act="form-test">' + esc(L('Test')) + '</button><button class="btn pri" data-act="form-save">' + esc(L('Save')) + '</button></div>';
        if (!f.isNew) h += '<div style="margin-top:12px"><button class="btn bad full" data-act="form-del">' + esc(L('Delete')) + '</button></div>';
        return h;
    }
    function openForm(src) {
        var isNew = !src;
        state.form = {
            isNew: isNew, id: src ? src.id : 's' + Date.now().toString(36),
            v: src ? Object.assign({}, src) : { type: 'm3u', name: '', url: '', user: '', pass: '', format: 'm3u8', mac: '' }
        };
        state.tab = 'sources';
        render();
    }
    function readForm() {
        var f = state.form, v = f.v;
        document.querySelectorAll('[data-f]').forEach(function (el) { v[el.getAttribute('data-f')] = el.value.trim(); });
        if (v.type === 'xtream' && v.url && !v.user) {
            var px = Validate.parseXtream(v.url);
            if (px) { v.url = px.url; v.user = px.user; v.pass = px.pass; }
        }
        if (v.type === 'stalker' && !v.mac) v.mac = Stalker.makeMac();
        v.id = f.id;
        if (!v.name) v.name = v.url.replace(/^https?:\/\//i, '').split(/[/?#]/)[0] || TYPE_NAME[v.type];
        return v;
    }
    function testSource(src) {
        if (src.type === 'm3u') return M3U.test(src);
        if (src.type === 'xtream') return Xtream.login(src);
        return Stalker.login(src);
    }

    /* ---------- library ---------- */
    function viewLibrary() {
        var src = state.src && srcById(state.src);
        if (!src && state.kind !== 'favs') {
            if (state.sources.length === 1) { state.src = state.sources[0].id; src = state.sources[0]; }
            else return '<div class="empty">' + esc(state.sources.length ? L('Choose a source first') : L('No sources yet. Add an M3U playlist, an Xtream Codes account or a Stalker portal.')) + '</div>' +
                (state.sources.length ? '<div class="list">' + state.sources.map(function (s) { return '<div class="src" data-act="open-src" data-id="' + esc(s.id) + '"><div class="ic">' + esc(s.type === 'm3u' ? 'M3U' : s.type === 'xtream' ? 'XC' : 'ST') + '</div><div class="tx"><div class="nm">' + esc(s.name) + '</div></div></div>'; }).join('') + '</div>' : '');
        }
        if (state.series) return viewSeries();
        var h = '';
        if (state.sources.length > 1 && src) {
            h += '<div class="fld" style="margin:6px 0 0"><select data-act-change="pick-src">' + state.sources.map(function (s) { return '<option value="' + esc(s.id) + '"' + (s.id === state.src ? ' selected' : '') + '>' + esc(s.name) + '</option>'; }).join('') + '</select></div>';
        }
        h += '<div class="tabs">' + KINDS.map(function (k) { return '<button data-act="kind" data-v="' + k + '" class="' + (state.kind === k ? 'on' : '') + '">' + esc(L(KIND_NAME[k])) + '</button>'; }).join('') + '</div>';
        h += '<input class="q" id="q" type="search" placeholder="' + esc(L('Search')) + '" value="' + esc(state.q) + '" autocomplete="off">';
        h += '<div style="height:10px"></div><div class="cats" id="cats"></div><div id="list" class="list"></div>';
        return h;
    }

    function loadCatalog() {
        var src = srcById(state.src);
        state.loadedKey = state.src + '|' + state.kind;
        state.items = []; state.cats = []; state.error = '';
        if (state.kind === 'favs') {
            state.items = Object.keys(state.favs).map(function (k) { return state.favs[k]; });
            state.loading = false;
            return Promise.resolve(computeView());
        }
        if (!src) return Promise.resolve();
        state.loading = true;
        var tok = state.tok = (state.tok || 0) + 1;
        renderList();
        return MODS[src.type].catalog(src, state.kind).then(function (items) {
            if (tok !== state.tok) return;
            state.items = items || [];
            state.loading = false;
            computeView();
        }).catch(function (e) {
            if (tok !== state.tok) return;
            state.loading = false;
            state.error = (e && e.message) ? L(e.message) : L('Could not load this content.');
            computeView();
        });
    }

    function computeView() {
        var q = state.q.toLowerCase();
        var seen = {}, cats = [];
        state.items.forEach(function (it) {
            var g = it.group || '—';
            if (!seen[g]) { seen[g] = 0; cats.push(g); }
            seen[g]++;
        });
        state.cats = cats.map(function (g) { return { g: g, n: seen[g] }; });
        if (state.cat && !seen[state.cat]) state.cat = '';
        state.view = state.items.filter(function (it) {
            if (state.cat && (it.group || '—') !== state.cat) return false;
            return !q || String(it.name || '').toLowerCase().indexOf(q) >= 0;
        });
        state.shown = PAGE;
        renderList();
    }

    function renderList() {
        var box = $('list');
        if (!box) return;
        var cats = $('cats');
        if (cats) {
            cats.innerHTML = state.cats.length > 1 ? '<button data-act="cat" data-v="" class="' + (!state.cat ? 'on' : '') + '">' + esc(L('All')) + '</button>' +
                state.cats.slice(0, 400).map(function (c, i) { return '<button data-act="cat" data-i="' + i + '" class="' + (state.cat === c.g ? 'on' : '') + '">' + esc(c.g) + ' · ' + c.n + '</button>'; }).join('') : '';
        }
        if (state.loading) { box.innerHTML = '<div class="spin"></div><div class="empty">' + esc(L('Loading…')) + '</div>'; return; }
        if (state.error) { box.innerHTML = '<div class="empty">' + esc(state.error) + '</div>'; return; }
        if (!state.view.length) { box.innerHTML = '<div class="empty">' + esc(L('Nothing found')) + '</div>'; return; }
        var poster = state.kind === 'vod' || state.kind === 'series';
        var src = srcById(state.src);
        var out = '';
        var n = Math.min(state.shown, state.view.length);
        for (var i = 0; i < n; i++) {
            var it = state.view[i];
            var isSeries = it.kind === 'series' || (state.kind === 'series' && it.kind !== 'ep');
            var fav = !!(src && state.favs[favKey(src, it)]) || state.kind === 'favs';
            out += '<div class="it' + (poster ? ' poster' : '') + '" data-i="' + i + '">' +
                '<img loading="lazy" src="' + esc(it.logo || 'img/ph-logo.png') + '" onerror="this.onerror=null;this.src=\'img/ph-logo.png\'" alt="">' +
                '<div class="tx" data-act="' + (isSeries ? 'series-open' : 'play') + '" data-i="' + i + '"><div class="nm">' + esc(it.name) + '</div><div class="sb">' + esc(it.group || '') + '</div></div>' +
                '<div class="acts"><button class="ib sm fav' + (fav ? ' on' : '') + '" data-act="fav" data-i="' + i + '">&#9733;</button>' +
                (isSeries ? '<button class="ib sm" data-act="series-open" data-i="' + i + '">&#8250;</button>' :
                    '<button class="ib sm" data-act="play" data-i="' + i + '">&#9654;</button><button class="ib sm" data-act="cast" data-i="' + i + '">&#128250;</button>') + '</div></div>';
        }
        if (n < state.view.length) out += '<div class="more" id="more">' + esc(L('Load more')) + ' (' + (state.view.length - n) + ')</div>';
        box.innerHTML = out;
        var more = $('more');
        if (more && 'IntersectionObserver' in window) {
            var io = new IntersectionObserver(function (es) {
                if (es[0].isIntersecting) { io.disconnect(); state.shown += PAGE; renderList(); }
            }, { root: $('main'), rootMargin: '400px' });
            io.observe(more);
        }
        if (more) more.onclick = function () { state.shown += PAGE; renderList(); };
    }

    function viewSeries() {
        var s = state.series;
        var h = '<div class="row" style="margin:8px 0"><button class="ib" data-act="series-back">' + (lang() === 'ar' ? '&#8594;' : '&#8592;') + '</button><div style="font-weight:700;font-size:17px">' + esc(s.item.name) + '</div></div>';
        if (s.loading) return h + '<div class="spin"></div>';
        if (s.error) return h + '<div class="empty">' + esc(s.error) + '</div>';
        var cur = null;
        s.eps.forEach(function (e, i) {
            if (e.season !== cur) { cur = e.season; h += '<div class="sec">' + esc(L('Season {n}', { n: cur })) + '</div>'; }
            h += '<div class="it" style="margin-bottom:8px"><div class="tx" data-act="ep-play" data-i="' + i + '"><div class="nm">' + esc((e.num ? e.num + '. ' : '') + e.name) + '</div></div>' +
                '<div class="acts"><button class="ib sm" data-act="ep-play" data-i="' + i + '">&#9654;</button><button class="ib sm" data-act="ep-cast" data-i="' + i + '">&#128250;</button></div></div>';
        });
        return h;
    }
    function openSeries(it) {
        var src = srcById(it._src || state.src);
        if (!src) return;
        state.series = { item: it, eps: [], loading: true, error: '' };
        render();
        MODS[src.type].seriesInfo(src, it).then(function (eps) {
            state.series.eps = eps || []; state.series.loading = false;
            if (!state.series.eps.length) state.series.error = L('Nothing found');
            if (state.tab === 'library') render();
        }).catch(function (e) {
            state.series.loading = false; state.series.error = (e && e.message) ? L(e.message) : L('Could not load this content.');
            if (state.tab === 'library') render();
        });
    }

    /* ---------- playback / cast ---------- */
    function resolveItem(it) {
        var src = srcById(it._src || state.src);
        if (!src) return Promise.reject(new Error('Choose a source first'));
        return MODS[src.type].resolve(src, it).then(function (url) { return { src: src, url: url }; });
    }
    function itemKind(it) { return it.kind === 'ep' ? 'ep' : (state.kind === 'favs' ? (it.kind || 'live') : state.kind); }

    function playHere(it) {
        resolveItem(it).then(function (r) {
            var kind = itemKind(it);
            startPlayer({ url: r.url, name: it.name, kind: kind, item: it, src: r.src });
        }).catch(function (e) { toast(L(e.message || 'Could not load this content.'), true); });
    }
    function castTo(it) {
        if (!Pair.info().paired) { toast(L('Pair with TV first'), true); setTab('remote'); return; }
        resolveItem(it).then(function (r) { return sendCast({ url: r.url, name: it.name, kind: itemKind(it), item: it, src: r.src }); })
            .then(function () { toast(L('Sent to TV')); vibe(); })
            .catch(function (e) { toast(L(e.message || 'Could not reach the relay'), true); });
    }
    function sendCast(p) {
        var s = p.src || {};
        var ref = {
            src: { type: s.type, url: s.url, user: s.user || '', pass: s.pass || '', mac: s.mac || '', format: s.format || '' },
            item: { id: p.item.id, kind: p.item.kind || '', name: p.item.name, group: p.item.group || '', ext: p.item.ext || '' }
        };
        var data = { url: p.url, title: p.name, kind: p.kind, logo: p.item.logo || '', ref: ref };
        if (p.start) data.start = p.start;
        return Pair.send('cast', data);
    }

    var plTimer = null, idleT = null;
    function startPlayer(p) {
        state.playing = p;
        $('pl').classList.remove('hidden', 'idle');
        $('pl-name').textContent = p.name;
        $('pl-err').classList.add('hidden');
        $('pl-spin').classList.remove('hidden');
        var live = p.kind === 'live';
        $('pl-seek').classList.toggle('hidden', live);
        try { Player.setEngine(S.get('engine')); Player.setAspect(S.get('aspect')); } catch (e) { }
        Player.play(p.url, { live: live });
        wakeUI();
        clearInterval(plTimer);
        plTimer = setInterval(plTick, 500);
        renderMini();
    }
    function plTick() {
        var v = $('html-video');
        $('pl-pp').innerHTML = v.paused ? '&#9654;' : '&#10074;&#10074;';
        if (state.playing && state.playing.kind !== 'live') {
            var d = Player.duration(), pos = Player.position();
            $('pl-pos').textContent = fmt(pos); $('pl-dur').textContent = fmt(d);
            if (!state.seeking && d) $('pl-range').value = Math.round(pos / d * 1000);
        }
    }
    function wakeUI() {
        var pl = $('pl');
        pl.classList.remove('idle');
        clearTimeout(idleT);
        idleT = setTimeout(function () { if (!state.sheet) pl.classList.add('idle'); }, 4000);
    }
    function closePlayer(keepMini) {
        clearInterval(plTimer);
        $('pl').classList.add('hidden');
        if (!keepMini) { try { Player.stop(); } catch (e) { } state.playing = null; }
        renderMini();
    }
    function renderMini() {
        var m = $('mini');
        if (!state.playing || !$('pl').classList.contains('hidden')) { m.classList.add('hidden'); return; }
        m.classList.remove('hidden');
        m.innerHTML = '<div class="tx" data-act="mini-open"><div class="nm">' + esc(state.playing.name) + '</div><div class="sb">' + esc(L('Playing on phone')) + '</div></div>' +
            '<button class="ib sm" data-act="pl-cast">&#128250;</button><button class="ib sm" data-act="mini-stop">&#9632;</button>';
    }
    Player.init({
        buffering: function (on) { $('pl-spin').classList.toggle('hidden', !on); },
        error: function (e) { $('pl-spin').classList.add('hidden'); var b = $('pl-err'); b.textContent = (e && e.message) ? L(e.message) : L('Could not load this content.'); b.classList.remove('hidden'); },
        ended: function () { },
        stall: function () { },
        progress: function () { }
    });
    $('html-video').addEventListener('playing', function () { $('pl-spin').classList.add('hidden'); $('pl-err').classList.add('hidden'); });

    /* ---------- remote ---------- */
    function viewRemote() {
        var i = state.tvInfo;
        var h = '<h2>' + esc(L('Pair with your TV')) + '</h2><div class="card">';
        if (!i.paired) {
            h += '<div class="np" style="margin:0 0 12px">' + esc(L('Pair help')) + '</div>' +
                '<div class="fld"><label>' + esc(L('Enter the code shown on the TV')) + '</label><input id="pcode" placeholder="' + esc(L('Pair code')) + '" autocapitalize="off" autocomplete="off" spellcheck="false"></div>' +
                '<button class="btn pri full" data-act="pair">' + esc(L('Pair')) + '</button>';
        } else {
            h += '<div class="tvcard"><div class="ic">&#128250;</div><div style="flex:1"><div style="font-weight:700">' + esc(i.online ? L('TV online') : L('Waiting for TV…')) + '</div><div class="sb" style="color:var(--mut);font-size:12px">' + esc(L('Pair code')) + ': ' + esc(i.code) + '</div></div><button class="btn bad" data-act="unpair">' + esc(L('Unpair')) + '</button></div>';
            var t = i.tv || {};
            h += '<div class="np">' + esc(L('Now playing on TV')) + ': <b>' + esc(t.title || L('Nothing playing')) + '</b>' + (t.state ? ' · ' + esc(t.state) : '') + '</div>';
        }
        h += '</div>';
        if (i.paired) {
            h += '<div class="pad"><button class="u" data-act="key" data-k="up">&#9650;</button><button class="l" data-act="key" data-k="left">&#9664;</button><button class="ok" data-act="key" data-k="ok">OK</button><button class="r" data-act="key" data-k="right">&#9654;</button><button class="d" data-act="key" data-k="down">&#9660;</button></div>';
            h += '<div class="keys"><button data-act="key" data-k="back">&#8617;</button><button data-act="key" data-k="info">i</button><button data-act="key" data-k="stop">&#9632;</button><button data-act="key" data-k="playpause">&#9199;</button>' +
                '<button data-act="key" data-k="rw">&#9194;</button><button data-act="key" data-k="ff">&#9193;</button><button data-act="key" data-k="chdown">CH-</button><button data-act="key" data-k="chup">CH+</button>' +
                '<button class="r" data-act="key" data-k="red">&#9679;</button><button class="g" data-act="key" data-k="green">&#9679;</button><button class="y" data-act="key" data-k="yellow">&#9679;</button><button class="b" data-act="key" data-k="blue">&#9679;</button></div>';
            h += '<div class="num">' + [1, 2, 3, 4, 5, 6, 7, 8, 9, '', 0, ''].map(function (d) { return d === '' ? '<span></span>' : '<button data-act="key" data-k="' + d + '">' + d + '</button>'; }).join('') + '</div>';
            h += '<div class="np" style="text-align:center;margin-top:14px">' + esc(L('Tip: the TV app must be open and paired.')) + '</div>';
        }
        return h;
    }
    Pair.on(function (info) {
        state.tvInfo = info;
        renderChip();
        if (state.tab === 'remote' && !(document.activeElement && document.activeElement.id === 'pcode')) {
            var m = $('main'), top = m.scrollTop; m.innerHTML = viewRemote(); m.scrollTop = top;
        }
    });

    /* ---------- settings ---------- */
    function viewSettings() {
        var h = '<h2>' + esc(L('Settings')) + '</h2><div class="card">';
        h += '<div class="sw"><div>' + esc(L('Language')) + '</div><select data-act-change="lang"><option value="en"' + (lang() === 'en' ? ' selected' : '') + '>English</option><option value="ar"' + (lang() === 'ar' ? ' selected' : '') + '>العربية</option></select></div>';
        h += '<div class="sw"><div>' + esc(L('Player engine')) + '</div><select data-act-change="engine">' + [['auto', L('Automatic')], ['html5', 'HTML5 / Hls.js']].map(function (o) { return '<option value="' + o[0] + '"' + (S.get('engine') === o[0] ? ' selected' : '') + '>' + esc(o[1]) + '</option>'; }).join('') + '</select></div>';
        h += '<div class="fld" style="margin:14px 0 0"><label>' + esc(L('Relay server')) + '</label><input id="relay" value="' + esc(Pair.relay()) + '" autocapitalize="off" spellcheck="false"></div>';
        h += '</div><div style="margin-top:14px"><button class="btn full" data-act="clear">' + esc(L('Clear cache')) + '</button></div>';
        h += '<div class="np" style="text-align:center;margin-top:18px">Lumora Phone · v1.0.0' + '</div>';
        return h;
    }

    /* ---------- sheet ---------- */
    function openSheet(html) { var s = $('sheet'); s.innerHTML = '<div class="in">' + html + '</div>'; s.classList.remove('hidden'); state.sheet = true; }
    function closeSheet() { $('sheet').classList.add('hidden'); state.sheet = false; }

    /* ---------- events ---------- */
    document.addEventListener('click', function (e) {
        if (e.target === $('sheet')) { closeSheet(); return; }
        var el = e.target.closest('[data-act]');
        if (!el) { if (!$('pl').classList.contains('hidden')) wakeUI(); return; }
        var a = el.getAttribute('data-act'), v = el.getAttribute('data-v'), id = el.getAttribute('data-id');
        var i = el.getAttribute('data-i'); i = i == null ? -1 : +i;
        switch (a) {
            case 'tab': setTab(v); break;
            case 'add-src': openForm(null); break;
            case 'edit-src': openForm(srcById(id)); break;
            case 'open-src': state.src = id; state.tab = 'library'; if (state.kind === 'favs') state.kind = 'live'; state.cat = ''; state.q = ''; state.series = null; render(); loadCatalog(); break;
            case 'send-src': sendSource(srcById(id)); break;
            case 'form-cancel': state.form = null; render(); break;
            case 'form-save': saveForm(); break;
            case 'form-test': testForm(); break;
            case 'form-del': if (confirm(L('Delete') + '?')) { state.sources = state.sources.filter(function (s) { return s.id !== state.form.id; }); saveSources(); if (state.src === state.form.id) state.src = null; Catalog.forget && Catalog.forget(state.form.id); state.form = null; render(); } break;
            case 'kind': state.kind = v; state.cat = ''; state.series = null; render(); loadCatalog(); break;
            case 'cat': state.cat = v === '' ? '' : state.cats[i].g; computeView(); break;
            case 'play': playHere(state.view[i]); break;
            case 'cast': castTo(state.view[i]); break;
            case 'fav': toggleFav(state.view[i]); break;
            case 'series-open': openSeries(state.view[i]); break;
            case 'series-back': state.series = null; render(); break;
            case 'ep-play': playHere(Object.assign({}, state.series.eps[i], { kind: 'ep' })); break;
            case 'ep-cast': castTo(Object.assign({}, state.series.eps[i], { kind: 'ep' })); break;
            case 'pair': doPair(); break;
            case 'unpair': Pair.disconnect(); break;
            case 'key': Pair.send('key', { key: el.getAttribute('data-k') }).then(function () { vibe(8); }).catch(function () { toast(L('Could not reach the relay'), true); }); break;
            case 'clear': try { Net.clear(); Store.clear && Store.clear(); } catch (x) { } toast(L('Cache cleared')); break;
            case 'pl-close': closePlayer(true); break;
            case 'pl-pp': try { Player.toggle(); } catch (x) { } wakeUI(); break;
            case 'pl-back10': Player.seek(Player.position() - 10000); wakeUI(); break;
            case 'pl-fwd10': Player.seek(Player.position() + 10000); wakeUI(); break;
            case 'pl-aspect': cycleAspect(); wakeUI(); break;
            case 'pl-cast': castPlaying(); break;
            case 'mini-open': startPlayer(state.playing); break;
            case 'mini-stop': closePlayer(false); break;
        }
    });
    document.addEventListener('change', function (e) {
        var a = e.target.getAttribute && e.target.getAttribute('data-act-change');
        if (a === 'pick-src') { state.src = e.target.value; state.cat = ''; state.series = null; loadCatalog(); }
        else if (a === 'lang') { S.set('lang', e.target.value); I18n.setLang(e.target.value); render(); }
        else if (a === 'engine') { S.set('engine', e.target.value); }
        else if (e.target.id === 'relay') { Pair.setRelay(e.target.value); toast(L('Saved')); }
        else if (e.target.getAttribute && e.target.getAttribute('data-f') === 'type' && state.form) { readForm(); state.form.v.type = e.target.value; render(); }
    });
    document.addEventListener('input', function (e) {
        if (e.target.id === 'q') { state.q = e.target.value; clearTimeout(searchT); searchT = setTimeout(computeView, 150); }
        else if (e.target.id === 'pl-range') { state.seeking = true; $('pl-pos').textContent = fmt(+e.target.value / 1000 * Player.duration()); wakeUI(); }
    });
    $('pl-range').addEventListener('change', function (e) { Player.seek(+e.target.value / 1000 * Player.duration()); state.seeking = false; });
    $('pl').addEventListener('touchstart', wakeUI, { passive: true });

    function cycleAspect() {
        var o = ['fit', 'fill', 'zoom'], n = o[(o.indexOf(S.get('aspect')) + 1) % 3];
        S.set('aspect', n); Player.setAspect(n); toast(L(n === 'fit' ? 'Fit' : n === 'fill' ? 'Fill' : 'Zoom'));
    }
    function castPlaying() {
        var p = state.playing;
        if (!p) return;
        if (!Pair.info().paired) { toast(L('Pair with TV first'), true); closePlayer(true); setTab('remote'); return; }
        var start = p.kind === 'live' ? 0 : Math.floor(Player.position());
        sendCast(Object.assign({}, p, { start: start })).then(function () { toast(L('Sent to TV')); vibe(); }).catch(function () { toast(L('Could not reach the relay'), true); });
    }
    function toggleFav(it) {
        var src = srcById(state.src);
        if (state.kind === 'favs') {
            var key = Object.keys(state.favs).filter(function (k) { return state.favs[k] === it; })[0];
            if (key) delete state.favs[key];
            wj(FAV_KEY, state.favs); loadCatalog(); toast(L('Removed from favorites')); return;
        }
        if (!src) return;
        var k = favKey(src, it);
        if (state.favs[k]) { delete state.favs[k]; toast(L('Removed from favorites')); }
        else { state.favs[k] = Object.assign({}, it, { kind: it.kind || state.kind, _src: src.id }); toast(L('Added to favorites')); }
        wj(FAV_KEY, state.favs);
        renderList();
    }
    function saveForm() {
        var v = readForm();
        if (!v.url) { toast(L('Could not load this content.'), true); return; }
        if (state.form.isNew) state.sources.push(v);
        else state.sources = state.sources.map(function (s) { return s.id === v.id ? v : s; });
        saveSources();
        try { MODS[v.type].forget(v.id); } catch (e) { }
        state.form = null; toast(L('Saved')); render();
    }
    function testForm() {
        var v = readForm();
        toast(L('Loading…'));
        testSource(v).then(function () { toast(L('Connection OK')); }).catch(function (e) { toast(L(e && e.message || 'Could not load this content.'), true); });
    }
    function sendSource(s) {
        if (!s) return;
        if (!Pair.info().paired) { toast(L('Pair with TV first'), true); setTab('remote'); return; }
        // Legacy format understood by the TV's "Add source" form (see applyRemote in the TV app).
        var m = { type: s.type, name: s.name, url: s.url };
        ['user', 'pass', 'mac', 'format'].forEach(function (k) { if (s[k]) m[k] = s[k]; });
        fetch(Pair.relay() + Pair.info().code.replace(/^/, 'lumora-'), { method: 'POST', body: JSON.stringify(m) })
            .then(function (r) { if (!r.ok) throw new Error(); toast(L('Source sent to TV')); })
            .catch(function () { toast(L('Could not reach the relay'), true); });
    }
    function doPair() {
        var v = $('pcode').value;
        if (!Pair.connect(v)) { toast(L('Invalid code'), true); return; }
        vibe(); render();
    }
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && e.target.id === 'pcode') doPair();
    });

    /* Android back button (called from the native shell) */
    window.LumoraBack = function () {
        if (state.sheet) { closeSheet(); return true; }
        if (!$('pl').classList.contains('hidden')) { closePlayer(true); return true; }
        if (state.form) { state.form = null; render(); return true; }
        if (state.series) { state.series = null; render(); return true; }
        if (state.tab !== 'sources') { setTab('sources'); return true; }
        return false;
    };
    window.addEventListener('beforeunload', function () { try { Player.stop(); } catch (e) { } });

    /* ---------- boot ---------- */
    I18n.setLang(lang());
    Pair.init();
    state.tvInfo = Pair.info();
    if (state.sources.length) { state.src = state.sources[0].id; }
    render();
})();
