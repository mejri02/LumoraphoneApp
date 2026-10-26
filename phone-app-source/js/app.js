(function () {
    'use strict';

    var S = Settings;
    var MODS = { m3u: M3U, xtream: Xtream, stalker: Stalker };
    var TYPE_NAME = { m3u: 'M3U Playlist', xtream: 'Xtream Codes', stalker: 'Stalker Portal' };
    var SRC_KEY = 'lumora.sources', FAV_KEY = 'lumora.phone.favs', HIST_KEY = 'lumora.phone.hist', PREF_KEY = 'lumora.phone.prefs';
    var PAGE = 60, GPAGE = 120;
    var VERSION = '2.0.0';

    /* ---------- i18n ---------- */
    var AR = {
        'Home': 'الرئيسية', 'Catalog': 'الكتالوج', 'Favorites': 'المفضلة', 'Search': 'بحث', 'Channels': 'القنوات', 'Movies': 'أفلام', 'Series': 'مسلسلات',
        'Group name': 'اسم المجموعة', 'Search in group': 'ابحث داخل المجموعة', 'items': 'عنصر', 'See all': 'عرض الكل', 'Recently watched': 'شوهد مؤخراً',
        'Live channels': 'القنوات المباشرة', 'New releases (Movies)': 'أحدث الأفلام', 'New series': 'أحدث المسلسلات',
        'Lumora TV devices': 'أجهزة تلفاز Lumora', 'Link with TV code': 'ربط برمز التلفاز', 'Cancel': 'إلغاء', 'Online': 'متصل', 'Offline': 'غير متصل', 'TV ID': 'معرّف التلفاز',
        'Connected': 'متصل', 'Connecting…': 'جارٍ الاتصال…', 'Disconnect': 'قطع الاتصال', 'Remote control': 'جهاز التحكم', 'Link': 'ربط',
        'Enter the code shown on your TV': 'أدخل الرمز الظاهر على شاشة التلفاز', 'Name (optional)': 'الاسم (اختياري)',
        'Open Lumora on the TV, go to Link phone and type the code shown there.': 'افتح Lumora على التلفاز، ثم «ربط الهاتف» واكتب الرمز الظاهر هناك.',
        'TV is offline. Open Lumora on your TV.': 'التلفاز غير متصل. افتح تطبيق Lumora على التلفاز.', 'Connected to TV': 'تم الاتصال بالتلفاز',
        'Disconnected from TV': 'تم قطع الاتصال بالتلفاز', 'Playing on TV': 'يعمل على التلفاز', 'Paused': 'متوقف مؤقتاً', 'Nothing playing': 'لا شيء يعمل',
        'Now playing': 'يعمل الآن', 'Remove TV': 'حذف التلفاز', 'TV removed': 'تم حذف التلفاز', 'Invalid code': 'رمز غير صالح',
        'Add your first source': 'أضف مصدرك الأول', 'Add an M3U playlist, an Xtream Codes account or a Stalker portal to start watching.': 'أضف قائمة M3U أو حساب Xtream Codes أو بوابة Stalker لتبدأ المشاهدة.',
        'Sources': 'المصادر', 'Settings': 'الإعدادات', 'Your sources': 'مصادرك', 'Add source': 'إضافة مصدر', 'Edit source': 'تعديل المصدر', 'Name': 'الاسم',
        'Playlist URL': 'رابط القائمة', 'Server URL': 'رابط السيرفر', 'Username': 'اسم المستخدم', 'Password': 'كلمة المرور', 'Portal URL': 'رابط البوابة',
        'MAC address': 'عنوان MAC', 'Format': 'الصيغة', 'Save': 'حفظ', 'Delete': 'حذف', 'Test': 'اختبار', 'Connection OK': 'الاتصال ناجح', 'Saved': 'تم الحفظ', 'Type': 'النوع',
        'All': 'الكل', 'Loading…': 'جارٍ التحميل…', 'Nothing found': 'لا توجد نتائج', 'Choose a source': 'اختر مصدراً', 'Play on phone': 'تشغيل على الهاتف', 'Cast to TV': 'إرسال إلى التلفاز',
        'Sent to TV': 'تم الإرسال إلى التلفاز', 'Send source to TV': 'إرسال المصدر إلى التلفاز', 'Source sent to TV': 'تم إرسال المصدر إلى التلفاز', 'Season {n}': 'الموسم {n}', 'Episodes': 'الحلقات',
        'Language': 'اللغة', 'Relay server': 'سيرفر الترحيل', 'Player engine': 'مشغّل الفيديو', 'Automatic': 'تلقائي', 'Clear cache': 'مسح الذاكرة المؤقتة', 'Cache cleared': 'تم مسح الذاكرة المؤقتة',
        'Could not load this content.': 'تعذّر تحميل المحتوى.', 'Playing on phone': 'يعمل على الهاتف', 'Could not reach the relay': 'تعذّر الوصول إلى سيرفر الترحيل', 'Load more': 'عرض المزيد',
        'Added to favorites': 'أُضيف إلى المفضلة', 'Removed from favorites': 'أُزيل من المفضلة', 'No favorites yet': 'لا توجد مفضلة بعد', 'Tap the star on any channel, movie or series.': 'اضغط النجمة على أي قناة أو فيلم أو مسلسل.',
        'Search channels, movies and series': 'ابحث في القنوات والأفلام والمسلسلات', 'Type to search': 'اكتب للبحث', 'Auto-cast when connected': 'الإرسال التلقائي عند الاتصال',
        'Everything you open is sent to the TV while it is connected': 'كل ما تفتحه يُرسل إلى التلفاز طالما هو متصل', 'Saved TVs': 'التلفازات المحفوظة', 'About': 'حول',
        'TV is offline, playing on this phone': 'التلفاز غير متصل، سيعمل على الهاتف', 'Casting to': 'الإرسال إلى', 'Not connected': 'غير متصل', 'Tap the cast icon to link your TV': 'اضغط أيقونة الإرسال لربط التلفاز',
        'Play': 'تشغيل', 'Pause': 'إيقاف مؤقت', 'Stop': 'إيقاف', 'Fit': 'ملاءمة', 'Fill': 'تمديد', 'Zoom': 'تكبير', 'Back': 'رجوع', 'Close': 'إغلاق', 'Buffering…': 'جارٍ التحميل…',
        'Keys': 'الأزرار', 'Link a TV first': 'اربط تلفازاً أولاً', 'Delete?': 'حذف؟', 'Recent': 'الأخيرة', 'Clear history': 'مسح السجل', 'History cleared': 'تم مسح السجل',
        'Tip: keep the TV app open while casting.': 'تنبيه: أبقِ تطبيق التلفاز مفتوحاً أثناء الإرسال.', 'No sources yet': 'لا توجد مصادر بعد', 'Searching…': 'جارٍ البحث…', 'Schedule': 'جدول البرامج', 'No schedule available': 'لا يوجد جدول برامج', 'NOW': 'الآن', 'NEXT': 'التالي', 'Controls locked': 'تم قفل الأزرار', 'Not supported on this device': 'غير مدعوم على هذا الجهاز'
    };
    function lang() { return S.get('lang') === 'ar' ? 'ar' : 'en'; }
    function L(s, v) {
        var r = s;
        if (lang() === 'ar') r = AR[s] !== undefined ? AR[s] : I18n.t(s);
        if (v) r = String(r).replace(/\{(\w+)\}/g, function (x, k) { return v[k] !== undefined ? v[k] : x; });
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
    var prefs = rj(PREF_KEY, {});
    function pref(k, d) { return prefs[k] === undefined ? d : prefs[k]; }
    function setPref(k, v) { prefs[k] = v; wj(PREF_KEY, prefs); }
    var toastT = null;
    function toast(msg, bad) {
        var t = $('toast');
        t.textContent = msg;
        t.className = bad ? 'err' : '';
        clearTimeout(toastT);
        toastT = setTimeout(function () { t.className = 'hidden'; }, 2800);
    }
    function vibe(n) { try { if (navigator.vibrate) navigator.vibrate(n || 12); } catch (e) { } }

    var P = {
        cast: '<path d="M2 8V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6"/><path d="M2 12a9 9 0 0 1 8 8M2 16a5 5 0 0 1 4 4"/><path d="M2 20h.01" stroke-width="3"/>',
        download: '<path d="M12 3v12m0 0-4.5-4.5M12 15l4.5-4.5M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/>',
        remote: '<rect x="7" y="2" width="10" height="20" rx="5"/><circle cx="12" cy="8" r="2"/><path d="M10 14h4M10 18h4"/>',
        gear: '<path d="M12 2.8 20 7.4v9.2l-8 4.6-8-4.6V7.4z"/><circle cx="12" cy="12" r="3.2"/>',
        home: '<path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z"/>',
        catalog: '<rect x="3.5" y="5" width="17" height="13" rx="3.5"/>',
        plus: '<path d="M12 5v14M5 12h14"/>',
        star: '<path d="m12 3.5 2.6 5.4 5.9.8-4.3 4.2 1 5.9L12 17l-5.2 2.8 1-5.9L3.5 9.7l5.9-.8z"/>',
        search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.8-3.8"/>',
        folder: '<path d="M3.5 7.5a2 2 0 0 1 2-2H9l2 2.2h7.5a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z"/>',
        back: '<path d="M15 5l-7 7 7 7"/>', chev: '<path d="M9 5l7 7-7 7"/>', down: '<path d="M6 9l6 6 6-6"/>',
        play: '<path d="M8 5.5v13l11-6.5z" fill="currentColor"/>', pause: '<path d="M8 5v14M16 5v14" stroke-width="3"/>', stop: '<rect x="6.5" y="6.5" width="11" height="11" rx="2" fill="currentColor"/>',
        tv: '<rect x="3" y="5" width="18" height="12" rx="2.5"/><path d="M8 21h8M12 17v4"/>',
        edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13 7l4 4"/><path d="M4 12V6a2 2 0 0 1 2-2h6" opacity=".0"/>',
        link: '<rect x="3.5" y="4" width="14" height="14" rx="3"/><path d="M12 20l8.5-8.5-3-3L9 17v3z"/>',
        trash: '<path d="M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12"/>', phone: '<rect x="7" y="2.5" width="10" height="19" rx="2.5"/><path d="M11 18.5h2"/>',
        x: '<path d="M6 6l12 12M18 6 6 18"/>', pencil: '<path d="M4 20l1-4L16 5l3 3L8 19z"/>', cog: '<circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3"/>',
        rw: '<path d="M11 6v12L3 12zM21 6v12l-8-6z" fill="currentColor"/>', ff: '<path d="M13 6v12l8-6zM3 6v12l8-6z" fill="currentColor"/>'
    };
    function ic(n) { return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">' + P[n] + '</svg>'; }
    var PH = '<div class="ph"><svg viewBox="0 0 64 64"><rect x="6" y="12" width="52" height="34" rx="9"/><rect x="21" y="51" width="22" height="5" rx="2.5"/></svg></div>';

    /* ---------- state ---------- */
    var state = {
        tab: 'home', sources: rj(SRC_KEY, []), favs: rj(FAV_KEY, {}), hist: rj(HIST_KEY, []),
        src: null, cat: { kind: 'live', group: null, gq: '', q: '', shown: PAGE, loading: false, error: '' },
        series: null, form: null, playing: null, tvInfo: Pair.info(), sheet: null, favKind: 'all',
        home: { live: [], vod: [], series: [], loading: false, src: '' },
        search: { q: '', res: null, loading: false }, from: 'home'
    };
    var cache = {}, inflight = {}, groupMemo = {}, searchT = null;

    function srcById(id) { for (var i = 0; i < state.sources.length; i++) if (state.sources[i].id === id) return state.sources[i]; return null; }
    function curSrc() { return srcById(state.src) || state.sources[0] || null; }
    function saveSources() { wj(SRC_KEY, state.sources); }
    function favKey(it) { return (it._src || state.src) + ':' + (it.kind || '') + ':' + it.id; }
    function itemKind(it) { return it.kind === 'ep' ? 'ep' : (it.kind || 'live'); }
    function isSeriesItem(it) { return it.kind === 'series'; }
    function castMode() { var i = Pair.info(); return i.connected && pref('autoCast', true); }

    function loadKind(src, kind) {
        var key = src.id + '|' + kind;
        if (cache[key]) return Promise.resolve(cache[key]);
        if (inflight[key]) return inflight[key];
        inflight[key] = MODS[src.type].catalog(src, kind).then(function (items) {
            items = items || [];
            for (var i = 0; i < items.length; i++) { items[i]._src = src.id; if (!items[i].kind) items[i].kind = kind; }
            cache[key] = items;
            delete inflight[key];
            return items;
        }, function (e) { delete inflight[key]; throw e; });
        return inflight[key];
    }
    function dropCache(id) {
        Object.keys(cache).forEach(function (k) { if (k.indexOf(id + '|') === 0) delete cache[k]; });
        Object.keys(groupMemo).forEach(function (k) { if (k.indexOf(id + '|') === 0) delete groupMemo[k]; });
        if (state.home.src === id) state.home = { live: [], vod: [], series: [], loading: false, src: '' };
    }

    /* ---------- chrome ---------- */
    function renderTools() {
        var i = state.tvInfo;
        var cls = i.connected ? 'cast-on' : (i.paired ? 'cast-wait' : '');
        $('tools').innerHTML =
            '<button class="' + cls + '" data-act="cast-sheet" aria-label="Cast">' + ic('cast') + '</button>' +
            '<button data-act="tab" data-v="sources" aria-label="Sources">' + ic('download') + '</button>' +
            '<button data-act="tab" data-v="remote" aria-label="Remote">' + ic('remote') + '</button>' +
            '<button data-act="tab" data-v="settings" aria-label="Settings">' + ic('gear') + '</button>';
    }
    function renderNav() {
        var items = [['home', 'Home', 'home'], ['catalog', 'Catalog', 'catalog'], ['add', '', 'plus'], ['favs', 'Favorites', 'star'], ['search', 'Search', 'search']];
        $('nav').innerHTML = items.map(function (n) {
            if (n[0] === 'add') return '<button class="add" data-act="add-src" aria-label="' + esc(L('Add source')) + '"><span class="pill">' + ic('plus') + '</span></button>';
            return '<button data-act="tab" data-v="' + n[0] + '" class="' + (state.tab === n[0] ? 'on' : '') + '"><span class="pill">' + ic(n[2]) + '</span><span>' + esc(L(n[1])) + '</span></button>';
        }).join('');
    }
    function setTab(t) {
        if (state.tab !== t) state.from = state.tab;
        state.tab = t;
        if (t !== 'catalog') state.series = null;
        if (t !== 'sources') state.form = null;
        render();
        if (t === 'home') ensureHome();
        if (t === 'catalog') ensureCat();
        if (t === 'search') { var q = $('sq'); if (q && !state.search.q) setTimeout(function () { try { q.focus(); } catch (e) { } }, 150); }
        if (t === 'remote') Pair.ping(state.tvInfo.id);
    }
    function render(keep) {
        var m = $('main'), top = keep ? m.scrollTop : 0;
        renderTools(); renderNav();
        var h = '';
        switch (state.tab) {
            case 'home': h = viewHome(); break;
            case 'catalog': h = viewCatalog(); break;
            case 'favs': h = viewFavs(); break;
            case 'search': h = viewSearch(); break;
            case 'sources': h = state.form ? viewForm() : viewSources(); break;
            case 'settings': h = viewSettings(); break;
            case 'remote': h = viewRemote(); break;
        }
        m.innerHTML = h;
        m.scrollTop = top;
        if (state.tab === 'catalog') hookMore();
        renderMini();
    }

    /* ---------- generic builders ---------- */
    function tile(it, sec, i) {
        return '<button class="tile" data-act="home-it" data-sec="' + sec + '" data-i="' + i + '"><div class="im">' + PH + (it.logo ? '<img loading="lazy" src="' + esc(it.logo) + '" onerror="this.remove()" alt="">' : '') + '</div><div class="nm">' + esc(it.name) + '</div></button>';
    }
    function poster(it, act, i, extra) {
        return '<button class="poster" data-act="' + act + '" data-i="' + i + '"' + (extra || '') + '><div class="im">' + PH + (it.logo ? '<img loading="lazy" src="' + esc(it.logo) + '" onerror="this.remove()" alt="">' : '') + '</div><div class="nm">' + esc(it.name) + '</div></button>';
    }
    function row(it, act, i, favOn) {
        var series = isSeriesItem(it);
        return '<div class="it"><button class="lg" data-act="' + act + '" data-i="' + i + '">' + PH.replace('class="ph"', 'class="ph" style="width:34px;height:34px"') + (it.logo ? '<img loading="lazy" src="' + esc(it.logo) + '" onerror="this.remove()" alt="">' : '') + '</button>' +
            '<button class="tx" data-act="' + act + '" data-i="' + i + '"><div class="nm">' + esc(it.name) + '</div><div class="sb">' + esc(it.group || '') + '</div></button>' +
            '<div class="acts"><button class="ib sm fav' + (favOn ? ' on' : '') + '" data-act="fav" data-i="' + i + '" data-ctx="' + act + '">' + ic('star') + '</button>' +
            (series ? '' : '<button class="ib sm" data-act="alt" data-i="' + i + '" data-ctx="' + act + '">' + ic(castMode() ? 'phone' : 'cast') + '</button>') + '</div></div>';
    }
    function skel(n, poster) {
        var o = '';
        for (var i = 0; i < n; i++) o += '<div class="' + (poster ? 'poster' : 'tile') + ' skel"><div class="im"></div><div class="nm"></div></div>';
        return o;
    }
    function emptyBox(icn, title, text, btn) {
        return '<div class="empty"><div class="big">' + ic(icn) + '</div><h4>' + esc(title) + '</h4><div>' + esc(text || '') + '</div>' + (btn || '') + '</div>';
    }

    /* ---------- home ---------- */
    function homeSrc() { return curSrc(); }
    function newest(items) {
        var a = items.filter(function (i) { return i.logo; });
        if (!a.length) a = items.slice();
        var num = a.filter(function (i) { return /^\d+$/.test(String(i.id)); }).length > a.length * 0.8;
        a = num ? a.slice().sort(function (x, y) { return (+y.id) - (+x.id); }) : a.slice().reverse();
        return a.slice(0, 24);
    }
    function ensureHome() {
        var src = homeSrc();
        if (!src || state.home.loading) return;
        if (state.home.src === src.id && state.home.done) return;
        state.home = { live: [], vod: [], series: [], loading: true, src: src.id, done: false, err: false };
        var kinds = ['live', 'vod', 'series'], idx = 0;
        function next() {
            if (state.home.src !== src.id) return;
            if (idx >= kinds.length) { state.home.loading = false; state.home.done = true; refreshHome(); return; }
            var k = kinds[idx++];
            loadKind(src, k).then(function (items) {
                state.home[k] = k === 'live' ? (items.filter(function (i) { return i.logo; }).slice(0, 24).concat(items.slice(0, 24))).slice(0, 24) : newest(items);
                if (k === 'live') {
                    var seen = {}; state.home.live = state.home.live.filter(function (i) { if (seen[i.id]) return false; seen[i.id] = 1; return true; });
                }
                refreshHome(); next();
            }).catch(function () { if (k === 'live') state.home.err = true; refreshHome(); next(); });
        }
        refreshHome();
        next();
    }
    function refreshHome() { if (state.tab === 'home') render(true); }

    function viewHome() {
        var h = '<h1 class="page-t">' + esc(L('Home')) + '</h1>';
        var i = state.tvInfo;
        if (i.paired) {
            h += '<button class="banner" data-act="cast-sheet"><span class="dot' + (i.connected ? '' : ' w') + '"></span><div class="tx"><b>' + esc(i.name) + ' · ' + esc(L(i.connected ? 'Connected' : 'Offline')) + '</b><small>' + esc(i.connected ? L('Everything you open is sent to the TV while it is connected') : L('TV is offline. Open Lumora on your TV.')) + '</small></div>' + ic('cast') + '</button>';
        }
        if (!state.sources.length) {
            return h + emptyBox('download', L('Add your first source'), L('Add an M3U playlist, an Xtream Codes account or a Stalker portal to start watching.'), '<div><button class="btn pri" data-act="add-src">' + esc(L('Add source')) + '</button></div>') +
                (i.paired ? '' : '<div class="empty" style="padding-top:0"><button class="btn" data-act="cast-sheet">' + ic('cast').replace('<svg', '<svg width="20" height="20"') + esc(L('Link with TV code')) + '</button></div>');
        }
        var H = state.home;
        if (state.hist.length) {
            h += sec(L('Recently watched'), '') + '<div class="rail">' + state.hist.slice(0, 20).map(function (it, k) { return tile(it, 'hist', k); }).join('') + '</div>';
        }
        h += sec(L('Live channels'), 'live') + '<div class="rail">' + (H.live.length ? H.live.map(function (it, k) { return tile(it, 'live', k); }).join('') : (H.loading ? skel(4) : '<div class="empty" style="padding:20px">' + esc(H.err ? L('Could not load this content.') : L('Nothing found')) + '</div>')) + '</div>';
        h += sec(L('New releases (Movies)'), 'vod') + '<div class="rail">' + (H.vod.length ? H.vod.map(function (it, k) { return poster(it, 'home-it', k, ' data-sec="vod"'); }).join('') : (H.loading ? skel(4, true) : '')) + '</div>';
        if (H.series.length || H.loading) h += sec(L('New series'), 'series') + '<div class="rail">' + (H.series.length ? H.series.map(function (it, k) { return poster(it, 'home-it', k, ' data-sec="series"'); }).join('') : skel(4, true)) + '</div>';
        return h;
    }
    function sec(title, kind) {
        return '<div class="sec-h"><h3>' + esc(title) + '</h3>' + (kind ? '<button data-act="see-all" data-v="' + kind + '">' + esc(L('See all')) + '</button>' : '') + '</div>';
    }

    /* ---------- catalog ---------- */
    function ensureCat() {
        var src = curSrc();
        if (!src) return;
        state.src = src.id;
        var c = state.cat;
        c.loading = true; c.error = '';
        var kind = c.kind, id = src.id;
        refreshCatBody();
        loadKind(src, kind).then(function () {
            if (state.src === id && state.cat.kind === kind) { state.cat.loading = false; refreshCatBody(); }
        }).catch(function (e) {
            if (state.src === id && state.cat.kind === kind) { state.cat.loading = false; state.cat.error = (e && e.message) ? L(e.message) : L('Could not load this content.'); refreshCatBody(); }
        });
    }
    function catItems() { var s = curSrc(); return s ? (cache[s.id + '|' + state.cat.kind] || []) : []; }
    function groupsOf() {
        var s = curSrc(); if (!s) return [];
        var key = s.id + '|' + state.cat.kind;
        if (groupMemo[key]) return groupMemo[key];
        var items = cache[key]; if (!items) return [];
        var idx = {}, out = [];
        items.forEach(function (it) {
            var g = it.group || '—';
            if (idx[g] === undefined) { idx[g] = out.length; out.push({ g: g, n: 0 }); }
            out[idx[g]].n++;
        });
        groupMemo[key] = out;
        return out;
    }
    var KIND_LABEL = { live: 'Channels', vod: 'Movies', series: 'Series' };
    function viewCatalog() {
        if (state.series) return viewSeries();
        var c = state.cat;
        var h = '<h1 class="page-t">' + esc(L('Catalog')) + '</h1>';
        if (!state.sources.length) return h + emptyBox('download', L('Add your first source'), L('Add an M3U playlist, an Xtream Codes account or a Stalker portal to start watching.'), '<div><button class="btn pri" data-act="add-src">' + esc(L('Add source')) + '</button></div>');
        h += '<div class="utabs">' + ['live', 'vod', 'series'].map(function (k) { return '<button data-act="kind" data-v="' + k + '" class="' + (c.kind === k ? 'on' : '') + '">' + esc(L(KIND_LABEL[k])) + '</button>'; }).join('') + '</div>';
        if (state.sources.length > 1) h += '<button class="srcpill" data-act="pick-src">' + ic('tv').replace('<svg', '<svg width="16" height="16"') + '<b>' + esc((curSrc() || {}).name || '') + '</b>' + ic('down') + '</button>';
        h += '<div id="catbar">' + catBar() + '</div><div id="catbody">' + catBody() + '</div>';
        return h;
    }
    function catBar() {
        var c = state.cat, grouped = c.group === null;
        if (grouped) return '<label class="searchbar">' + ic('search') + '<input id="gq" type="search" placeholder="' + esc(L('Group name')) + '" value="' + esc(c.gq) + '" autocomplete="off"></label>';
        var n = filteredItems().length;
        return '<div class="ghead"><button class="ib sm" data-act="group-back"><span class="flip">' + ic('back') + '</span></button><div class="tx"><b>' + esc(c.group) + '</b><small>' + n + ' ' + esc(L('items')) + '</small></div></div>' +
            '<label class="searchbar">' + ic('search') + '<input id="iq" type="search" placeholder="' + esc(L('Search in group')) + '" value="' + esc(c.q) + '" autocomplete="off"></label>';
    }
    function filteredItems() {
        var c = state.cat, q = c.q.toLowerCase();
        return catItems().filter(function (it) {
            if (c.group !== null && (it.group || '—') !== c.group) return false;
            return !q || String(it.name || '').toLowerCase().indexOf(q) >= 0;
        });
    }
    function catBody() {
        var c = state.cat;
        if (c.loading) return '<div class="spin"></div>';
        if (c.error) return '<div class="empty">' + esc(c.error) + '<div><button class="btn" data-act="retry-cat" style="margin-top:14px">' + esc(L('Back')) + '</button></div></div>';
        var groups = groupsOf();
        if (c.group === null && groups.length === 1) { c.group = groups[0].g; return catBody(); }
        if (c.group === null) {
            var q = c.gq.toLowerCase();
            var list = groups.filter(function (g) { return !q || g.g.toLowerCase().indexOf(q) >= 0; });
            if (!list.length) return '<div class="empty">' + esc(L('Nothing found')) + '</div>';
            var n = Math.min(c.shown, list.length), out = '<div class="glist">';
            for (var i = 0; i < n; i++) out += '<button class="grow" data-act="group" data-g="' + esc(list[i].g) + '">' + ic('folder').replace('<svg', '<svg class="f"') + '<div class="tx"><div class="nm">' + esc(list[i].g) + '</div><div class="sb">' + list[i].n + ' ' + esc(L('items')) + '</div></div></button>';
            out += '</div>';
            if (n < list.length) out += '<div class="more" id="more">' + esc(L('Load more')) + ' (' + (list.length - n) + ')</div>';
            return out;
        }
        var items = filteredItems();
        if (!items.length) return '<div class="empty">' + esc(L('Nothing found')) + '</div>';
        var m = Math.min(c.shown, items.length), o = '';
        var posterMode = c.kind !== 'live';
        o += posterMode ? '<div class="grid">' : '<div>';
        for (var j = 0; j < m; j++) o += posterMode ? poster(items[j], 'item', j) : row(items[j], 'item', j, !!state.favs[favKey(items[j])]);
        o += '</div>';
        if (m < items.length) o += '<div class="more" id="more">' + esc(L('Load more')) + ' (' + (items.length - m) + ')</div>';
        return o;
    }
    function refreshCatBody() {
        if (state.tab !== 'catalog' || state.series) return;
        var b = $('catbody'); if (!b) return;
        var bar = $('catbar'); if (bar && !(document.activeElement && /^(gq|iq)$/.test(document.activeElement.id))) bar.innerHTML = catBar();
        b.innerHTML = catBody();
        hookMore();
    }
    var io = null;
    function hookMore() {
        var more = $('more');
        if (io) { try { io.disconnect(); } catch (e) { } io = null; }
        if (!more) return;
        var go = function () { state.cat.shown += state.cat.group === null ? GPAGE : PAGE; refreshCatBody(); };
        more.onclick = go;
        if ('IntersectionObserver' in window) {
            io = new IntersectionObserver(function (es) { if (es[0].isIntersecting) { io.disconnect(); go(); } }, { root: $('main'), rootMargin: '500px' });
            io.observe(more);
        }
    }
    function catView() { return state.cat.group === null ? null : filteredItems(); }

    /* ---------- series ---------- */
    function viewSeries() {
        var s = state.series;
        var h = '<div class="ghead" style="margin-top:14px"><button class="ib sm" data-act="series-back"><span class="flip">' + ic('back') + '</span></button><div class="tx"><b>' + esc(s.item.name) + '</b><small>' + esc(L('Episodes')) + '</small></div>' +
            '<button class="ib sm fav' + (state.favs[favKey(s.item)] ? ' on' : '') + '" data-act="series-fav">' + ic('star') + '</button></div>';
        if (s.loading) return h + '<div class="spin"></div>';
        if (s.error) return h + '<div class="empty">' + esc(s.error) + '</div>';
        var seasons = [];
        s.eps.forEach(function (e) { if (seasons.indexOf(e.season) < 0) seasons.push(e.season); });
        if (s.season == null) s.season = seasons[0];
        if (seasons.length > 1) h += '<div class="chips">' + seasons.map(function (n) { return '<button data-act="season" data-v="' + esc(n) + '" class="' + (String(n) === String(s.season) ? 'on' : '') + '">' + esc(L('Season {n}', { n: n })) + '</button>'; }).join('') + '</div>';
        s.eps.forEach(function (e, i) {
            if (String(e.season) !== String(s.season)) return;
            h += '<button class="ep" data-act="ep-play" data-i="' + i + '"><span class="n">' + esc(e.num || i + 1) + '</span><span class="nm">' + esc(e.name) + '</span>' + ic(castMode() ? 'cast' : 'play').replace('<svg', '<svg width="22" height="22" style="color:var(--mut)"') + '</button>';
        });
        return h;
    }
    function openSeries(it) {
        var src = srcById(it._src || state.src);
        if (!src) return;
        if (state.tab !== 'catalog') { state.from = state.tab; state.tab = 'catalog'; }
        state.series = { item: it, eps: [], loading: true, error: '', season: null };
        render();
        MODS[src.type].seriesInfo(src, it).then(function (eps) {
            if (!state.series || state.series.item !== it) return;
            state.series.eps = (eps || []).map(function (e) { e._src = src.id; return e; });
            state.series.loading = false;
            if (!state.series.eps.length) state.series.error = L('Nothing found');
            render(true);
        }).catch(function (e) {
            if (!state.series || state.series.item !== it) return;
            state.series.loading = false; state.series.error = (e && e.message) ? L(e.message) : L('Could not load this content.');
            render(true);
        });
    }
    function closeSeries() {
        state.series = null;
        if (state.from && state.from !== 'catalog' && state.from !== 'sources') { var f = state.from; state.tab = f; render(); if (f === 'home') ensureHome(); }
        else render();
    }

    /* ---------- favorites ---------- */
    function favList() {
        return Object.keys(state.favs).map(function (k) { return state.favs[k]; }).filter(function (it) { return state.favKind === 'all' || it.kind === state.favKind || (state.favKind === 'vod' && it.kind === 'ep'); });
    }
    function viewFavs() {
        var h = '<h1 class="page-t">' + esc(L('Favorites')) + '</h1>';
        h += '<div class="utabs">' + [['all', 'All'], ['live', 'Channels'], ['vod', 'Movies'], ['series', 'Series']].map(function (k) { return '<button data-act="favkind" data-v="' + k[0] + '" class="' + (state.favKind === k[0] ? 'on' : '') + '">' + esc(L(k[1])) + '</button>'; }).join('') + '</div>';
        var list = favList();
        if (!list.length) return h + emptyBox('star', L('No favorites yet'), L('Tap the star on any channel, movie or series.'));
        state.favView = list;
        h += '<div>' + list.map(function (it, i) { return row(it, 'fav-it', i, true); }).join('') + '</div>';
        return h;
    }

    /* ---------- search ---------- */
    function viewSearch() {
        var h = '<h1 class="page-t">' + esc(L('Search')) + '</h1><label class="searchbar">' + ic('search') + '<input id="sq" type="search" placeholder="' + esc(L('Search channels, movies and series')) + '" value="' + esc(state.search.q) + '" autocomplete="off"></label><div id="sbody">' + searchBody() + '</div>';
        return h;
    }
    function searchBody() {
        var s = state.search;
        if (!state.sources.length) return emptyBox('download', L('No sources yet'), L('Add an M3U playlist, an Xtream Codes account or a Stalker portal to start watching.'));
        if (!s.q) return '<div class="empty" style="padding-top:60px">' + esc(L('Type to search')) + '</div>';
        if (s.loading && !s.res) return '<div class="spin"></div>';
        if (!s.res) return '';
        var out = '', any = false;
        s.view = {};
        ['live', 'vod', 'series'].forEach(function (k) {
            var r = s.res[k] || [];
            if (!r.length) return;
            any = true; s.view[k] = r;
            out += sec(L(KIND_LABEL[k]), '');
            out += k === 'live' ? '<div>' + r.map(function (it, i) { return row(it, 'sr-it', k + ':' + i, !!state.favs[favKey(it)]); }).join('') + '</div>'
                : '<div class="rail">' + r.map(function (it, i) { return poster(it, 'sr-it', k + ':' + i); }).join('') + '</div>';
        });
        if (!any) return '<div class="empty">' + esc(s.loading ? L('Searching…') : L('Nothing found')) + '</div>';
        return out;
    }
    function runSearch() {
        var s = state.search, q = s.q.trim().toLowerCase(), src = curSrc();
        if (!q || !src) { s.res = null; s.loading = false; refreshSearch(); return; }
        s.loading = true;
        var token = s.token = (s.token || 0) + 1;
        Promise.all(['live', 'vod', 'series'].map(function (k) { return loadKind(src, k).catch(function () { return []; }); })).then(function (all) {
            if (token !== s.token) return;
            var res = {};
            ['live', 'vod', 'series'].forEach(function (k, i) {
                var out = [], items = all[i];
                for (var j = 0; j < items.length && out.length < 40; j++) if (String(items[j].name || '').toLowerCase().indexOf(q) >= 0) out.push(items[j]);
                res[k] = out;
            });
            s.res = res; s.loading = false; refreshSearch();
        });
    }
    function refreshSearch() { var b = $('sbody'); if (b && state.tab === 'search') b.innerHTML = searchBody(); }

    /* ---------- sources ---------- */
    function viewSources() {
        var h = '<h1 class="page-t">' + esc(L('Sources')) + '</h1>';
        if (!state.sources.length) h += emptyBox('download', L('No sources yet'), L('Add an M3U playlist, an Xtream Codes account or a Stalker portal to start watching.'));
        else h += state.sources.map(function (s) {
            return '<div class="src"><div class="ic">' + esc(s.type === 'm3u' ? 'M3U' : s.type === 'xtream' ? 'XC' : 'ST') + '</div>' +
                '<button class="tx" data-act="open-src" data-id="' + esc(s.id) + '"><div class="nm">' + esc(s.name || s.url) + '</div><div class="sb">' + esc(TYPE_NAME[s.type]) + ' · ' + esc(s.url) + '</div></button>' +
                '<button class="ib sm" data-act="send-src" data-id="' + esc(s.id) + '" title="' + esc(L('Send source to TV')) + '">' + ic('cast') + '</button>' +
                '<button class="ib sm" data-act="edit-src" data-id="' + esc(s.id) + '">' + ic('pencil') + '</button></div>';
        }).join('');
        h += '<div style="margin-top:16px"><button class="btn pri full" data-act="add-src">+ ' + esc(L('Add source')) + '</button></div>';
        return h;
    }
    function fld(key, label, val, type, ph) {
        return '<div class="fld"><label>' + esc(L(label)) + '</label><input data-f="' + key + '" type="' + (type || 'text') + '" value="' + esc(val || '') + '" placeholder="' + esc(ph || '') + '" autocapitalize="off" autocorrect="off" spellcheck="false"></div>';
    }
    function viewForm() {
        var f = state.form, v = f.v;
        var h = '<h1 class="page-t">' + esc(L(f.isNew ? 'Add source' : 'Edit source')) + '</h1><div class="card">';
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
        state.form = {
            isNew: !src, id: src ? src.id : 's' + Date.now().toString(36),
            v: src ? Object.assign({}, src) : { type: 'm3u', name: '', url: '', user: '', pass: '', format: 'm3u8', mac: '' }
        };
        state.series = null; state.tab = 'sources';
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
    function saveForm() {
        var v = readForm();
        if (!v.url) { toast(L('Could not load this content.'), true); return; }
        if (state.form.isNew) state.sources.push(v);
        else state.sources = state.sources.map(function (s) { return s.id === v.id ? v : s; });
        saveSources();
        try { MODS[v.type].forget(v.id); } catch (e) { }
        dropCache(v.id);
        if (!state.src) state.src = v.id;
        var wasNew = state.form.isNew;
        state.form = null; toast(L('Saved'));
        if (wasNew) { state.src = v.id; state.cat.group = null; setTab('home'); } else render();
    }

    /* ---------- playback / cast ---------- */
    function resolveItem(it) {
        var src = srcById(it._src || state.src);
        if (!src) return Promise.reject(new Error('Choose a source'));
        return MODS[src.type].resolve(src, it).then(function (url) { return { src: src, url: url }; });
    }
    function addHist(it) {
        var c = {};
        Object.keys(it).forEach(function (k) { var v = it[k]; if (v == null || typeof v === 'object' || typeof v === 'function') return; c[k] = v; });
        if (it.kind === 'ep') return;
        var key = favKey(it);
        state.hist = state.hist.filter(function (h) { return favKey(h) !== key; });
        state.hist.unshift(c);
        state.hist = state.hist.slice(0, 30);
        wj(HIST_KEY, state.hist);
    }
    function playItem(it) {
        if (!it) return;
        if (isSeriesItem(it)) { openSeries(it); return; }
        addHist(it);
        var info = Pair.info();
        if (castMode()) castTo(it);
        else {
            if (info.paired && !info.connected && pref('autoCast', true)) toast(L('TV is offline, playing on this phone'));
            playHere(it);
        }
    }
    function playHere(it) {
        resolveItem(it).then(function (r) {
            startPlayer({ url: r.url, name: it.name, kind: itemKind(it), item: it, src: r.src });
        }).catch(function (e) { toast(L(e.message || 'Could not load this content.'), true); });
    }
    function castTo(it) {
        if (!Pair.info().paired) { openCastSheet(); return; }
        toast(L('Connecting…'));
        resolveItem(it).then(function (r) { return sendCast({ url: r.url, name: it.name, kind: itemKind(it), item: it, src: r.src }); })
            .then(function () { toast(L('Sent to TV')); vibe(); setTimeout(function () { Pair.ping(state.tvInfo.id); }, 2500); })
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
    var PAUSE_SVG = '<svg viewBox="0 0 24 24"><path d="M8 5v14M16 5v14"/></svg>';
    var PLAY_SVG = '<svg viewBox="0 0 24 24"><path d="M8 5.5v13l11-6.5z" fill="currentColor" stroke="none"/></svg>';
    function hms(ms) {
        var s = Math.max(0, Math.floor(ms / 1000)), h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
        return (h < 10 ? '0' : '') + h + ':' + (m < 10 ? '0' : '') + m + ':' + (x < 10 ? '0' : '') + x;
    }
    function loadEpg(p) {
        var box = $('pl-epg');
        box.innerHTML = '';
        $('pl-sched').classList.toggle('hidden', p.kind !== 'live');
        $('pl-sched-t').textContent = L('Schedule');
        if (p.kind !== 'live') return;
        var m = p.src && MODS[p.src.type];
        if (!m || !m.epg) { box.innerHTML = '<div class="empty" style="padding:24px 0">' + esc(L('No schedule available')) + '</div>'; return; }
        var tok = p.item;
        var pr; try { pr = Promise.resolve(m.epg(p.src, p.item, [])); } catch (e) { pr = Promise.reject(e); }
        pr.then(function (d) {
            if (!state.playing || state.playing.item !== tok) return;
            if (!d || !d.now) { box.innerHTML = '<div class="empty" style="padding:24px 0">' + esc(L('No schedule available')) + '</div>'; return; }
            var o = '<div class="epg"><small>' + esc(L('NOW')) + '</small><b>' + esc(d.now.title) + '</b><span>' + esc(d.now.start || '') + '</span></div>';
            if (d.next) o += '<div class="epg"><small>' + esc(L('NEXT')) + '</small><b>' + esc(d.next.title) + '</b><span>' + esc(d.next.start || '') + '</span></div>';
            box.innerHTML = o;
        }).catch(function () { if (state.playing && state.playing.item === tok) box.innerHTML = '<div class="empty" style="padding:24px 0">' + esc(L('No schedule available')) + '</div>'; });
    }
    function startPlayer(p) {
        state.playing = p;
        state.playStart = Date.now();
        var it = p.item || {};
        var pl = $('pl');
        pl.classList.remove('hidden', 'idle', 'locked', 'fs');
        pl.scrollTop = 0;
        $('pl-name').textContent = p.name;
        var g = $('pl-group'); g.textContent = it.group || ''; g.style.display = it.group ? '' : 'none';
        var lg = $('pl-logo'); if (it.logo) { lg.src = it.logo; lg.style.display = ''; } else lg.style.display = 'none';
        $('pl-fav').classList.toggle('on', !!state.favs[favKey(it)]);
        $('pl-err').classList.add('hidden');
        $('pl-spin').classList.remove('hidden');
        var live = p.kind === 'live';
        $('pl-seek').classList.toggle('hidden', live);
        $('pl-time').style.display = live ? '' : 'none';
        $('pl-pp').innerHTML = PAUSE_SVG;
        try { Player.setEngine(S.get('engine')); Player.setAspect(S.get('aspect')); } catch (e) { }
        Player.play(p.url, { live: live });
        loadEpg(p);
        wakeUI();
        clearInterval(plTimer);
        plTimer = setInterval(plTick, 500);
        renderMini();
    }
    function plTick() {
        var v = $('html-video');
        $('pl-pp').innerHTML = v.paused ? PLAY_SVG : PAUSE_SVG;
        if (!state.playing) return;
        if (state.playing.kind !== 'live') {
            var d = Player.duration(), pos = Player.position();
            $('pl-pos').textContent = fmt(pos); $('pl-dur').textContent = fmt(d);
            if (!state.seeking && d) $('pl-range').value = Math.round(pos / d * 1000);
        } else $('pl-time').textContent = hms(Date.now() - state.playStart);
    }
    function setFs(on) {
        $('pl').classList.toggle('fs', on);
        try { if (on) { if (screen.orientation && screen.orientation.lock) screen.orientation.lock('landscape').catch(function () { }); } else if (screen.orientation && screen.orientation.unlock) screen.orientation.unlock(); } catch (e) { }
        wakeUI();
    }
    function wakeUI() {
        var pl = $('pl');
        pl.classList.remove('idle');
        clearTimeout(idleT);
        idleT = setTimeout(function () { if (!state.sheet) pl.classList.add('idle'); }, 4000);
    }
    function closePlayer(keepMini) {
        clearInterval(plTimer);
        setFs(false);
        $('pl').classList.add('hidden');
        if (!keepMini) { try { Player.stop(); } catch (e) { } state.playing = null; }
        renderMini();
    }
    function castLive() {
        var i = state.tvInfo;
        if (!i.connected || !i.tv || !i.tv.state || i.tv.state === 'idle') return null;
        return i.tv;
    }
    function renderMini() {
        var m = $('mini');
        if (state.playing && $('pl').classList.contains('hidden')) {
            m.classList.remove('hidden');
            m.innerHTML = '<button class="tx" data-act="mini-open"><div class="nm">' + esc(state.playing.name) + '</div><div class="sb">' + esc(L('Playing on phone')) + '</div></button>' +
                '<button class="ib" data-act="pl-cast">' + ic('cast') + '</button><button class="ib" data-act="mini-stop">' + ic('stop') + '</button>';
            return;
        }
        var tv = state.tab === 'remote' ? null : castLive();
        if (!tv) { m.classList.add('hidden'); return; }
        var pct = tv.dur ? Math.min(100, Math.max(0, (tv.pos + (tv.state === 'playing' ? Date.now() - tv._rx : 0)) / tv.dur * 100)) : 0;
        m.classList.remove('hidden');
        m.innerHTML = '<button class="tx" data-act="tab" data-v="remote"><div class="nm">' + esc(tv.title || '') + '</div><div class="sb">' + esc(tv.state === 'paused' ? L('Paused') : tv.state === 'buffering' ? L('Buffering…') : L('Playing on TV')) + ' · ' + esc(state.tvInfo.name) + '</div>' + (tv.dur ? '<div class="bar"><i style="width:' + pct + '%"></i></div>' : '') + '</button>' +
            '<button class="ib" data-act="tv-toggle">' + ic(tv.state === 'paused' ? 'play' : 'pause') + '</button><button class="ib" data-act="tv-stop">' + ic('stop') + '</button>';
    }
    Player.init({
        buffering: function (on) { $('pl-spin').classList.toggle('hidden', !on); },
        error: function (e) { $('pl-spin').classList.add('hidden'); var b = $('pl-err'); b.textContent = (e && e.message) ? L(e.message) : L('Could not load this content.'); b.classList.remove('hidden'); },
        ended: function () { }, stall: function () { }, progress: function () { }
    });
    $('html-video').addEventListener('playing', function () { $('pl-spin').classList.add('hidden'); $('pl-err').classList.add('hidden'); });

    function castPlaying() {
        var p = state.playing;
        if (!p) return;
        if (!Pair.info().paired || !Pair.info().connected) { closePlayer(true); openCastSheet(); return; }
        var start = p.kind === 'live' ? 0 : Math.floor(Player.position() / 1000);
        sendCast(Object.assign({}, p, { start: start })).then(function () { toast(L('Sent to TV')); vibe(); closePlayer(false); })
            .catch(function () { toast(L('Could not reach the relay'), true); });
    }

    /* ---------- cast sheet (TV devices) ---------- */
    function openSheet(html, mode) { var s = $('sheet'); s.innerHTML = '<div class="in">' + html + '</div>'; s.classList.remove('hidden'); state.sheet = mode || true; }
    function closeSheet() { $('sheet').classList.add('hidden'); state.sheet = null; }
    function castSheetHtml() {
        var tvs = Pair.list(), info = Pair.info();
        var h = '<div class="sh-card"><div class="sh-h">' + ic('cast') + '<span>' + esc(L('Lumora TV devices')) + '</span></div>';
        tvs.forEach(function (tv) {
            h += '<div class="sh-row" data-act="tv-tap" data-id="' + esc(tv.id) + '">' + ic('tv') + '<div class="tx"><div class="t1">' + esc(tv.name) + '</div><div class="t2"><span class="' + (tv.online ? 'on' : 'off') + '">' + esc(L(tv.online ? 'Online' : 'Offline')) + '</span> | ' + esc(L('TV ID')) + ': ' + esc(tv.label) + '</div></div>' +
                (tv.active ? '<span class="pill-c">' + esc(L(tv.online ? 'Connected' : 'Connecting…')) + '</span>' : '') +
                '<button class="ib sm" data-act="tv-del" data-id="' + esc(tv.id) + '" aria-label="' + esc(L('Remove TV')) + '">' + ic('trash') + '</button></div>';
        });
        h += '<div class="sh-sep"></div>';
        h += '<button class="sh-row" data-act="tv-link">' + ic('link') + '<div class="tx"><div class="t1">' + esc(L('Link with TV code')) + '</div></div></button>';
        if (info.paired) {
            h += '<button class="sh-row" data-act="tab" data-v="remote">' + ic('remote') + '<div class="tx"><div class="t1">' + esc(L('Remote control')) + '</div></div></button>';
            h += '<button class="sh-row" data-act="tv-off">' + ic('x') + '<div class="tx"><div class="t1">' + esc(L('Disconnect')) + '</div></div></button>';
        }
        h += '<div style="height:8px"></div></div><button class="sh-cancel" data-act="sheet-close">' + esc(L('Cancel')) + '</button>';
        return h;
    }
    function openCastSheet() { openSheet(castSheetHtml(), 'cast'); Pair.ping(); }
    function refreshCastSheet() { if (state.sheet === 'cast') { var in_ = $('sheet').firstChild; if (in_) in_.innerHTML = castSheetHtml(); } }
    function openLinkForm() {
        openSheet('<div class="sh-card"><div class="sh-h">' + ic('link') + '<span>' + esc(L('Link with TV code')) + '</span></div><div class="sh-form"><p>' + esc(L('Open Lumora on the TV, go to Link phone and type the code shown there.')) + '</p>' +
            '<input id="tvcode" inputmode="text" placeholder="xxxx-xxxx-xxxx-xxxx" autocomplete="off" autocapitalize="off" spellcheck="false" maxlength="40">' +
            '<input id="tvname" class="nm" placeholder="' + esc(L('Name (optional)')) + '" maxlength="30">' +
            '<div class="row" style="margin-top:12px"><button class="btn" data-act="sheet-close">' + esc(L('Cancel')) + '</button><button class="btn pri" data-act="tv-link-go">' + esc(L('Link')) + '</button></div></div></div>', 'link');
        setTimeout(function () { var i = $('tvcode'); if (i) try { i.focus(); } catch (e) { } }, 250);
    }
    function doLink() {
        var id = Pair.parse($('tvcode').value);
        if (!id) { toast(L('Invalid code'), true); return; }
        var nm = $('tvname').value;
        Pair.add(id, nm);
        closeSheet(); vibe();
        toast(L('Connecting…'));
        Pair.waitOnline(id, 9000).then(function (ok) {
            if (ok) { toast(L('Connected to TV')); vibe(30); render(true); }
            else toast(L('TV is offline. Open Lumora on your TV.'), true);
        });
    }
    function tvTap(id) {
        var tv = Pair.list().filter(function (t) { return t.id === id; })[0];
        if (!tv) return;
        if (tv.active && tv.online) { closeSheet(); setTab('remote'); return; }
        Pair.select(id);
        toast(L('Connecting…'));
        Pair.waitOnline(id, 9000).then(function (ok) {
            if (ok) { toast(L('Connected to TV')); vibe(30); } else toast(L('TV is offline. Open Lumora on your TV.'), true);
            refreshCastSheet();
        });
        refreshCastSheet();
    }

    /* ---------- remote ---------- */
    function viewRemote() {
        var i = state.tvInfo, h = '<h1 class="page-t">' + esc(L('Remote control')) + '</h1>';
        if (!i.paired) return h + emptyBox('cast', L('Not connected'), L('Tap the cast icon to link your TV'), '<div><button class="btn pri" data-act="tv-link">' + esc(L('Link with TV code')) + '</button></div>');
        var tv = i.tv || {}, st = i.connected ? (tv.state || 'idle') : 'off';
        var sub = st === 'off' ? L('Offline') : st === 'idle' ? L('Nothing playing') : st === 'paused' ? L('Paused') : st === 'buffering' ? L('Buffering…') : L('Playing on TV');
        h += '<div class="nowp"><div class="ic">' + ic('tv') + '</div><div class="tx"><small>' + esc(i.name) + ' · ' + esc(i.connected ? L('Online') : L('Offline')) + '</small><b>' + esc(st === 'idle' || st === 'off' ? L('Nothing playing') : tv.title || '') + '</b><small>' + esc(sub) + '</small></div></div>';
        if (st !== 'idle' && st !== 'off') {
            if (tv.dur > 0 && tv.kind !== 'live') h += '<div class="rprog"><span id="rpos">' + fmt(tv.pos || 0) + '</span><input id="rrange" type="range" min="0" max="1000" value="' + Math.round((tv.pos || 0) / tv.dur * 1000) + '"><span>' + fmt(tv.dur) + '</span></div>';
            h += '<div class="tport"><button class="ib" data-act="tv-seek" data-v="-30000">' + ic('rw') + '</button><button class="ib big" data-act="tv-toggle">' + ic(st === 'paused' ? 'play' : 'pause') + '</button><button class="ib" data-act="tv-seek" data-v="30000">' + ic('ff') + '</button><button class="ib" data-act="tv-stop">' + ic('stop') + '</button></div>';
        }
        h += '<div class="pad"><button class="u" data-act="key" data-k="up">&#9650;</button><button class="l" data-act="key" data-k="left">&#9664;</button><button class="ok" data-act="key" data-k="ok">OK</button><button class="r" data-act="key" data-k="right">&#9654;</button><button class="d" data-act="key" data-k="down">&#9660;</button></div>';
        h += '<div class="keys"><button data-act="key" data-k="back">&#8617;</button><button data-act="key" data-k="info">i</button><button data-act="key" data-k="chdown">CH-</button><button data-act="key" data-k="chup">CH+</button>' +
            '<button class="r" data-act="key" data-k="red">&#9679;</button><button class="g" data-act="key" data-k="green">&#9679;</button><button class="y" data-act="key" data-k="yellow">&#9679;</button><button class="b" data-act="key" data-k="blue">&#9679;</button></div>';
        h += '<div class="num">' + [1, 2, 3, 4, 5, 6, 7, 8, 9, '', 0, ''].map(function (d) { return d === '' ? '<span></span>' : '<button data-act="key" data-k="' + d + '">' + d + '</button>'; }).join('') + '</div>';
        h += '<div class="empty" style="padding:18px 0 0;font-size:12px">' + esc(L('Tip: keep the TV app open while casting.')) + '</div>';
        return h;
    }
    function tvCmd(op, data) {
        return Pair.send(op, data).then(function () { vibe(8); setTimeout(function () { Pair.ping(state.tvInfo.id); }, 1200); }).catch(function () { toast(L('Could not reach the relay'), true); });
    }
    var lastSig = '';
    Pair.on(function (info) {
        state.tvInfo = info;
        var tv = info.tv || {};
        var sig = [info.paired, info.connected, info.name, info.id, tv.state, tv.title, info.tvs.length].join('|');
        var changed = sig !== lastSig;
        lastSig = sig;
        renderTools();
        renderMini();
        if (changed) {
            if (state.tab === 'remote' && !state.rSeek) { var m = $('main'), top = m.scrollTop; m.innerHTML = viewRemote(); m.scrollTop = top; }
            else if (state.tab === 'home') render(true);
        }
        refreshCastSheet();
    });
    function useSrc(id) { state.src = id; try { localStorage.setItem('lumora.phone.src', id); } catch (e) { } }
    setInterval(function () {
        if (state.tab !== 'remote' || state.rSeek) return;
        var tv = state.tvInfo.tv;
        if (!tv || tv.state !== 'playing' || !tv.dur) return;
        var pos = (tv.pos || 0) + (Date.now() - tv._rx);
        var a = $('rpos'), r = $('rrange');
        if (a) a.textContent = fmt(pos);
        if (r) r.value = Math.round(Math.min(pos, tv.dur) / tv.dur * 1000);
    }, 1000);

    /* ---------- settings ---------- */
    function viewSettings() {
        var h = '<h1 class="page-t">' + esc(L('Settings')) + '</h1><div class="card">';
        h += '<div class="sw"><div>' + esc(L('Language')) + '</div><select data-act-change="lang"><option value="en"' + (lang() === 'en' ? ' selected' : '') + '>English</option><option value="ar"' + (lang() === 'ar' ? ' selected' : '') + '>العربية</option></select></div>';
        h += '<div class="sw"><div>' + esc(L('Auto-cast when connected')) + '<small>' + esc(L('Everything you open is sent to the TV while it is connected')) + '</small></div><button class="tgl' + (pref('autoCast', true) ? ' on' : '') + '" data-act="tgl-cast" aria-label="toggle"></button></div>';
        h += '<div class="sw"><div>' + esc(L('Player engine')) + '</div><select data-act-change="engine">' + [['auto', L('Automatic')], ['html5', 'HTML5 / Hls.js']].map(function (o) { return '<option value="' + o[0] + '"' + (S.get('engine') === o[0] ? ' selected' : '') + '>' + esc(o[1]) + '</option>'; }).join('') + '</select></div>';
        h += '<button class="sw" style="width:100%;text-align:start" data-act="cast-sheet"><div>' + esc(L('Saved TVs')) + '<small>' + Pair.list().length + '</small></div>' + ic('chev').replace('<svg', '<svg width="20" height="20" class="flip"') + '</button>';
        h += '<div class="fld" style="margin:16px 0 0"><label>' + esc(L('Relay server')) + '</label><input id="relay" value="' + esc(Pair.relay()) + '" autocapitalize="off" spellcheck="false"></div>';
        h += '</div><div style="margin-top:14px" class="row"><button class="btn" data-act="clear">' + esc(L('Clear cache')) + '</button><button class="btn" data-act="clear-hist">' + esc(L('Clear history')) + '</button></div>';
        h += '<div class="empty" style="padding:22px 0 0;font-size:13px">Lumora Phone · v' + VERSION + '</div>';
        return h;
    }

    /* ---------- events ---------- */
    document.addEventListener('click', function (e) {
        if (e.target === $('sheet')) { closeSheet(); return; }
        var el = e.target.closest('[data-act]');
        if (!el) { if (!$('pl').classList.contains('hidden')) wakeUI(); return; }
        var a = el.getAttribute('data-act'), v = el.getAttribute('data-v'), id = el.getAttribute('data-id');
        var iRaw = el.getAttribute('data-i'), i = iRaw == null ? -1 : +iRaw;
        switch (a) {
            case 'tab': closeSheet(); setTab(v); break;
            case 'cast-sheet': openCastSheet(); break;
            case 'sheet-close': closeSheet(); break;
            case 'tv-link': openLinkForm(); break;
            case 'tv-link-go': doLink(); break;
            case 'tv-tap': tvTap(id); break;
            case 'tv-del': e.stopPropagation(); if (confirm(L('Delete?'))) { Pair.remove(id); toast(L('TV removed')); refreshCastSheet(); } break;
            case 'tv-off': Pair.deselect(); closeSheet(); toast(L('Disconnected from TV')); render(true); break;
            case 'tv-toggle': tvCmd('toggle'); break;
            case 'tv-stop': tvCmd('stop'); break;
            case 'tv-seek': { var t = state.tvInfo.tv || {}; var to = Math.max(0, (t.pos || 0) + (t.state === 'playing' ? Date.now() - t._rx : 0) + (+v)); tvCmd('seek', { to: to }); break; }
            case 'key': Pair.send('key', { key: el.getAttribute('data-k') }).then(function () { vibe(8); }).catch(function () { toast(L('Could not reach the relay'), true); }); break;
            case 'add-src': openForm(null); break;
            case 'edit-src': openForm(srcById(id)); break;
            case 'open-src': useSrc(id); state.cat.group = null; state.cat.gq = ''; state.cat.q = ''; setTab('catalog'); break;
            case 'pick-src': openSheet('<div class="sh-card"><div class="sh-h">' + ic('tv') + '<span>' + esc(L('Choose a source')) + '</span></div><div class="sh-pick">' + state.sources.map(function (s) { return '<button class="pick' + (s.id === state.src ? ' on' : '') + '" data-act="set-src" data-id="' + esc(s.id) + '">' + esc(s.name) + '<small>' + esc(TYPE_NAME[s.type]) + '</small></button>'; }).join('') + '</div></div><button class="sh-cancel" data-act="sheet-close">' + esc(L('Cancel')) + '</button>', 'pick'); break;
            case 'set-src': closeSheet(); useSrc(id); state.cat.group = null; state.cat.gq = ''; state.cat.q = ''; state.home = { live: [], vod: [], series: [], loading: false, src: '' }; render(); ensureCat(); break;
            case 'send-src': sendSource(srcById(id)); break;
            case 'form-cancel': state.form = null; render(); break;
            case 'form-save': saveForm(); break;
            case 'form-test': testForm(); break;
            case 'form-del': if (confirm(L('Delete') + '?')) { var fid = state.form.id; state.sources = state.sources.filter(function (s) { return s.id !== fid; }); saveSources(); dropCache(fid); if (state.src === fid) state.src = null; state.form = null; render(); } break;
            case 'kind': state.cat.kind = v; state.cat.group = null; state.cat.gq = ''; state.cat.q = ''; state.cat.shown = GPAGE; state.series = null; render(); ensureCat(); break;
            case 'see-all': state.cat.kind = v; state.cat.group = null; state.cat.gq = ''; state.cat.q = ''; state.cat.shown = GPAGE; setTab('catalog'); break;
            case 'retry-cat': ensureCat(); break;
            case 'group': state.cat.group = el.getAttribute('data-g'); state.cat.q = ''; state.cat.shown = PAGE; refreshCatBody(); $('main').scrollTop = 0; break;
            case 'group-back': state.cat.group = null; state.cat.q = ''; state.cat.shown = GPAGE; refreshCatBody(); break;
            case 'item': playItem(filteredItems()[i]); break;
            case 'fav-it': playItem(state.favView[i]); break;
            case 'sr-it': { var p = iRaw.split(':'); playItem(state.search.view[p[0]][+p[1]]); break; }
            case 'home-it': { var sec_ = el.getAttribute('data-sec'); playItem(sec_ === 'hist' ? state.hist[i] : state.home[sec_][i]); break; }
            case 'alt': { var it2 = ctxItem(el.getAttribute('data-ctx'), iRaw); if (!it2) break; if (castMode()) { addHist(it2); playHere(it2); } else castTo(it2); break; }
            case 'fav': toggleFav(ctxItem(el.getAttribute('data-ctx'), iRaw), el); break;
            case 'favkind': state.favKind = v; render(); break;
            case 'series-back': closeSeries(); break;
            case 'series-fav': toggleFav(state.series.item); render(true); break;
            case 'season': state.series.season = v; render(true); break;
            case 'ep-play': { var ep = Object.assign({}, state.series.eps[i], { kind: 'ep' }); if (castMode()) castTo(ep); else playHere(ep); break; }
            case 'tgl-cast': setPref('autoCast', !pref('autoCast', true)); render(true); break;
            case 'clear': try { Net.clear(); Store.clear && Store.clear(); } catch (x) { } cache = {}; groupMemo = {}; state.home = { live: [], vod: [], series: [], loading: false, src: '' }; toast(L('Cache cleared')); break;
            case 'clear-hist': state.hist = []; wj(HIST_KEY, []); toast(L('History cleared')); break;
            case 'pl-close': closePlayer(true); break;
            case 'pl-pp': try { Player.toggle(); } catch (x2) { } wakeUI(); break;
            case 'pl-back10': Player.seek(Player.position() - 10000); wakeUI(); break;
            case 'pl-fwd10': Player.seek(Player.position() + 10000); wakeUI(); break;
            case 'pl-aspect': cycleAspect(); wakeUI(); break;
            case 'pl-fs': setFs(!$('pl').classList.contains('fs')); break;
            case 'pl-pip': { var vv = $('html-video'); if (document.pictureInPictureEnabled && vv.requestPictureInPicture) vv.requestPictureInPicture().catch(function () { toast(L('Not supported on this device'), true); }); else toast(L('Not supported on this device'), true); break; }
            case 'pl-fav': if (state.playing) { toggleFav(state.playing.item); $('pl-fav').classList.toggle('on', !!state.favs[favKey(state.playing.item)]); } break;
            case 'pl-lock': $('pl').classList.add('locked'); toast(L('Controls locked')); break;
            case 'pl-unlock': $('pl').classList.remove('locked'); wakeUI(); break;
            case 'pl-cast': castPlaying(); break;
            case 'mini-open': startPlayer(state.playing); break;
            case 'mini-stop': closePlayer(false); break;
        }
    });
    function ctxItem(ctx, iRaw) {
        if (ctx === 'item') return filteredItems()[+iRaw];
        if (ctx === 'fav-it') return state.favView[+iRaw];
        if (ctx === 'sr-it') { var p = String(iRaw).split(':'); return state.search.view[p[0]][+p[1]]; }
        return null;
    }
    document.addEventListener('change', function (e) {
        var a = e.target.getAttribute && e.target.getAttribute('data-act-change');
        if (a === 'lang') { S.set('lang', e.target.value); I18n.setLang(e.target.value); render(); }
        else if (a === 'engine') S.set('engine', e.target.value);
        else if (e.target.id === 'relay') { Pair.setRelay(e.target.value); toast(L('Saved')); }
        else if (e.target.getAttribute && e.target.getAttribute('data-f') === 'type' && state.form) { readForm(); state.form.v.type = e.target.value; render(); }
        else if (e.target.id === 'rrange') { var tv = state.tvInfo.tv || {}; tvCmd('seek', { to: Math.round(+e.target.value / 1000 * (tv.dur || 0)) }); state.rSeek = false; }
    });
    document.addEventListener('input', function (e) {
        var id = e.target.id;
        if (id === 'gq') { state.cat.gq = e.target.value; state.cat.shown = GPAGE; clearTimeout(searchT); searchT = setTimeout(function () { var b = $('catbody'); if (b) { b.innerHTML = catBody(); hookMore(); } }, 120); }
        else if (id === 'iq') { state.cat.q = e.target.value; state.cat.shown = PAGE; clearTimeout(searchT); searchT = setTimeout(function () { var b = $('catbody'); if (b) { b.innerHTML = catBody(); hookMore(); } }, 150); }
        else if (id === 'sq') { state.search.q = e.target.value; clearTimeout(searchT); searchT = setTimeout(runSearch, 300); state.search.res = null; refreshSearch(); }
        else if (id === 'tvcode') { var f = Pair.fmt(e.target.value.toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 32)); e.target.value = f; }
        else if (id === 'pl-range') { state.seeking = true; $('pl-pos').textContent = fmt(+e.target.value / 1000 * Player.duration()); wakeUI(); }
        else if (id === 'rrange') { state.rSeek = true; var t2 = state.tvInfo.tv || {}; var r = $('rpos'); if (r) r.textContent = fmt(+e.target.value / 1000 * (t2.dur || 0)); }
    });
    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Enter') return;
        if (e.target.id === 'tvcode' || e.target.id === 'tvname') doLink();
        else if (e.target.id === 'sq' || e.target.id === 'gq' || e.target.id === 'iq') e.target.blur();
    });
    $('pl-range').addEventListener('change', function (e) { Player.seek(+e.target.value / 1000 * Player.duration()); state.seeking = false; });
    $('pl').addEventListener('touchstart', wakeUI, { passive: true });

    function cycleAspect() {
        var o = ['fit', 'fill', 'zoom'], n = o[(o.indexOf(S.get('aspect')) + 1) % 3];
        S.set('aspect', n); Player.setAspect(n); toast(L(n === 'fit' ? 'Fit' : n === 'fill' ? 'Fill' : 'Zoom'));
    }
    function toggleFav(it, el) {
        if (!it) return;
        var k = favKey(it);
        if (state.favs[k]) { delete state.favs[k]; toast(L('Removed from favorites')); }
        else {
            var c = {};
            Object.keys(it).forEach(function (x) { var v = it[x]; if (v != null && typeof v !== 'object' && typeof v !== 'function') c[x] = v; });
            c._src = it._src || state.src; c.kind = it.kind || 'live';
            state.favs[k] = c; toast(L('Added to favorites'));
        }
        wj(FAV_KEY, state.favs);
        vibe(10);
        if (state.tab === 'favs') render(true);
        else if (el) el.classList.toggle('on');
    }
    function testForm() {
        var v = readForm();
        toast(L('Loading…'));
        testSource(v).then(function () { toast(L('Connection OK')); }).catch(function (e) { toast(L(e && e.message || 'Could not load this content.'), true); });
    }
    function sendSource(s) {
        if (!s) return;
        if (!Pair.info().paired) { toast(L('Link a TV first'), true); openCastSheet(); return; }
        var m = { type: s.type, name: s.name, url: s.url };
        ['user', 'pass', 'mac', 'format'].forEach(function (k) { if (s[k]) m[k] = s[k]; });
        Pair.send('source', { source: m }).then(function () { toast(L('Source sent to TV')); }).catch(function () { toast(L('Could not reach the relay'), true); });
    }

    /* Android back button (called from the native shell) */
    window.LumoraBack = function () {
        if (state.sheet) { closeSheet(); return true; }
        if (!$('pl').classList.contains('hidden')) { if ($('pl').classList.contains('fs')) setFs(false); else closePlayer(true); return true; }
        if (state.form) { state.form = null; render(); return true; }
        if (state.series) { closeSeries(); return true; }
        if (state.tab === 'catalog' && state.cat.group !== null && groupsOf().length > 1) { state.cat.group = null; state.cat.q = ''; refreshCatBody(); return true; }
        if (state.tab !== 'home') { setTab('home'); return true; }
        return false;
    };
    window.addEventListener('beforeunload', function () { try { Player.stop(); } catch (e) { } });

    /* ---------- boot ---------- */
    I18n.setLang(lang());
    Pair.init();
    state.tvInfo = Pair.info();
    var lastId = null; try { lastId = localStorage.getItem('lumora.phone.src'); } catch (e) { }
    state.src = (srcById(lastId) || state.sources[0] || {}).id || null;
    render();
    ensureHome();

    /* ---------- splash ---------- */
    (function () {
        var sp = $('splash'); if (!sp) return;
        var done = false;
        function hide() {
            if (done) return; done = true;
            sp.classList.add('out');
            setTimeout(function () { if (sp.parentNode) sp.parentNode.removeChild(sp); var c = document.getElementById('splash-css'); if (c && c.parentNode) c.parentNode.removeChild(c); }, 700);
        }
        (function wait() {
            var t = (window.performance && performance.now) ? performance.now() : 2000;
            var ready = !state.sources.length || !state.home.loading;
            if (t >= 2000 && (ready || t >= 3500)) hide(); else setTimeout(wait, 100);
        })();
    })();
})();
