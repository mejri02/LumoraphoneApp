var QR = (function () {
    'use strict';
    var ECC = [-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26];
    var BLK = [-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5];

    function rawModules(v) {
        var r = (16 * v + 128) * v + 64;
        if (v >= 2) { var n = Math.floor(v / 7) + 2; r -= (25 * n - 10) * n - 55; if (v >= 7) r -= 36; }
        return r;
    }
    function dataCw(v) { return Math.floor(rawModules(v) / 8) - ECC[v] * BLK[v]; }

    function utf8(s) {
        var u = unescape(encodeURIComponent(s)), o = [];
        for (var i = 0; i < u.length; i++) o.push(u.charCodeAt(i));
        return o;
    }

    function gfMul(x, y) {
        var z = 0;
        for (var i = 7; i >= 0; i--) { z = (z << 1) ^ ((z >>> 7) * 0x11D); z ^= ((y >>> i) & 1) * x; }
        return z;
    }
    function rsDivisor(deg) {
        var r = [], i, j;
        for (i = 0; i < deg - 1; i++) r.push(0);
        r.push(1);
        var root = 1;
        for (i = 0; i < deg; i++) {
            for (j = 0; j < r.length; j++) { r[j] = gfMul(r[j], root); if (j + 1 < r.length) r[j] ^= r[j + 1]; }
            root = gfMul(root, 2);
        }
        return r;
    }
    function rsRemainder(data, div) {
        var r = div.map(function () { return 0; });
        data.forEach(function (b) {
            var f = b ^ r.shift();
            r.push(0);
            div.forEach(function (c, i) { r[i] ^= gfMul(c, f); });
        });
        return r;
    }

    function encode(text) {
        var bytes = utf8(text), ver, i;
        for (ver = 1; ver <= 10; ver++) {
            var cc = ver <= 9 ? 8 : 16;
            if (4 + cc + bytes.length * 8 <= dataCw(ver) * 8) break;
        }
        if (ver > 10) throw new Error('Text too long');
        var bits = [];
        function put(val, len) { for (var k = len - 1; k >= 0; k--) bits.push((val >>> k) & 1); }
        put(4, 4);
        put(bytes.length, ver <= 9 ? 8 : 16);
        bytes.forEach(function (b) { put(b, 8); });
        var cap = dataCw(ver) * 8;
        put(0, Math.min(4, cap - bits.length));
        while (bits.length % 8) bits.push(0);
        for (var pad = 0xEC; bits.length < cap; pad ^= 0xEC ^ 0x11) put(pad, 8);
        var data = [];
        for (i = 0; i < bits.length; i += 8) {
            var by = 0;
            for (var k = 0; k < 8; k++) by = (by << 1) | bits[i + k];
            data.push(by);
        }
        var nb = BLK[ver], eb = ECC[ver];
        var raw = Math.floor(rawModules(ver) / 8);
        var shortN = nb - raw % nb, shortLen = Math.floor(raw / nb) - eb;
        var blocks = [], div = rsDivisor(eb), pos = 0;
        for (i = 0; i < nb; i++) {
            var len = shortLen + (i < shortN ? 0 : 1);
            var d = data.slice(pos, pos + len);
            pos += len;
            var e = rsRemainder(d, div);
            if (i < shortN) d.push(0);
            blocks.push(d.concat(e));
        }
        var all = [];
        for (i = 0; i < blocks[0].length; i++) {
            blocks.forEach(function (b, j) { if (i !== shortLen || j >= shortN) all.push(b[i]); });
        }
        return build(ver, all);
    }

    function build(ver, cw) {
        var size = ver * 4 + 17, m = [], fn = [], r, c;
        for (r = 0; r < size; r++) { m.push(new Array(size).fill(false)); fn.push(new Array(size).fill(false)); }
        function set(x, y, d) { m[y][x] = d; fn[y][x] = true; }
        for (i = 0; i < size; i++) { set(6, i, i % 2 === 0); set(i, 6, i % 2 === 0); }
        function finder(cx, cy) {
            for (var dy = -4; dy <= 4; dy++) for (var dx = -4; dx <= 4; dx++) {
                var dist = Math.max(Math.abs(dx), Math.abs(dy)), x = cx + dx, y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) set(x, y, dist !== 2 && dist !== 4);
            }
        }
        var i;
        finder(3, 3); finder(size - 4, 3); finder(3, size - 4);
        var al = [];
        if (ver > 1) {
            var n = Math.floor(ver / 7) + 2;
            var step = ver === 32 ? 26 : Math.ceil((ver * 4 + 4) / (n * 2 - 2)) * 2;
            al = [6];
            for (var p = size - 7; al.length < n; p -= step) al.splice(1, 0, p);
        }
        al.forEach(function (ax, a) {
            al.forEach(function (ay, b) {
                if ((a === 0 && b === 0) || (a === 0 && b === al.length - 1) || (a === al.length - 1 && b === 0)) return;
                for (var dy = -2; dy <= 2; dy++) for (var dx = -2; dx <= 2; dx++) set(ax + dx, ay + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
            });
        });
        function formatBits(mask) {
            var d = (0 << 3) | mask, rem = d;
            for (var k = 0; k < 10; k++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
            var bits = ((d << 10) | rem) ^ 0x5412;
            function bit(k) { return ((bits >>> k) & 1) !== 0; }
            for (var k2 = 0; k2 <= 5; k2++) set(8, k2, bit(k2));
            set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8));
            for (var k3 = 9; k3 < 15; k3++) set(14 - k3, 8, bit(k3));
            for (var k4 = 0; k4 < 8; k4++) set(size - 1 - k4, 8, bit(k4));
            for (var k5 = 8; k5 < 15; k5++) set(8, size - 15 + k5, bit(k5));
            set(8, size - 8, true);
        }
        formatBits(0);
        if (ver >= 7) {
            var rem = ver;
            for (i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            var vb = (ver << 12) | rem;
            for (i = 0; i < 18; i++) {
                var bt = ((vb >>> i) & 1) !== 0, a = size - 11 + i % 3, b = Math.floor(i / 3);
                set(a, b, bt); set(b, a, bt);
            }
        }
        var k = 0;
        for (var right = size - 1; right >= 1; right -= 2) {
            if (right === 6) right = 5;
            for (var vert = 0; vert < size; vert++) {
                for (var j = 0; j < 2; j++) {
                    var x = right - j, up = ((right + 1) & 2) === 0, y = up ? size - 1 - vert : vert;
                    if (!fn[y][x] && k < cw.length * 8) { m[y][x] = ((cw[k >>> 3] >>> (7 - (k & 7))) & 1) !== 0; k++; }
                }
            }
        }
        function applyMask(mask) {
            for (var y = 0; y < size; y++) for (var x = 0; x < size; x++) {
                var inv;
                switch (mask) {
                    case 0: inv = (x + y) % 2 === 0; break;
                    case 1: inv = y % 2 === 0; break;
                    case 2: inv = x % 3 === 0; break;
                    case 3: inv = (x + y) % 3 === 0; break;
                    case 4: inv = (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0; break;
                    case 5: inv = x * y % 2 + x * y % 3 === 0; break;
                    case 6: inv = (x * y % 2 + x * y % 3) % 2 === 0; break;
                    default: inv = ((x + y) % 2 + x * y % 3) % 2 === 0;
                }
                if (!fn[y][x] && inv) m[y][x] = !m[y][x];
            }
        }
        function penalty() {
            var res = 0, y, x, run, col;
            for (y = 0; y < size; y++) {
                run = 1; col = m[y][0];
                for (x = 1; x < size; x++) { if (m[y][x] === col) { run++; if (run === 5) res += 3; else if (run > 5) res++; } else { col = m[y][x]; run = 1; } }
            }
            for (x = 0; x < size; x++) {
                run = 1; col = m[0][x];
                for (y = 1; y < size; y++) { if (m[y][x] === col) { run++; if (run === 5) res += 3; else if (run > 5) res++; } else { col = m[y][x]; run = 1; } }
            }
            for (y = 0; y < size - 1; y++) for (x = 0; x < size - 1; x++) {
                if (m[y][x] === m[y][x + 1] && m[y][x] === m[y + 1][x] && m[y][x] === m[y + 1][x + 1]) res += 3;
            }
            var dark = 0;
            for (y = 0; y < size; y++) for (x = 0; x < size; x++) if (m[y][x]) dark++;
            res += (Math.ceil(Math.abs(dark * 20 - size * size * 10) / (size * size)) - 1) * 10;
            return res;
        }
        var best = 0, bestP = 1e9;
        for (var mk = 0; mk < 8; mk++) {
            applyMask(mk); formatBits(mk);
            var pn = penalty();
            if (pn < bestP) { bestP = pn; best = mk; }
            applyMask(mk);
        }
        applyMask(best); formatBits(best);
        return { size: size, get: function (x, y) { return m[y][x]; } };
    }

    function draw(canvas, text) {
        var q = encode(text), quiet = 4, total = q.size + quiet * 2;
        var scale = Math.floor(canvas.width / total), off = Math.floor((canvas.width - scale * total) / 2);
        var g = canvas.getContext('2d');
        g.fillStyle = '#fff';
        g.fillRect(0, 0, canvas.width, canvas.height);
        g.fillStyle = '#000';
        for (var y = 0; y < q.size; y++) for (var x = 0; x < q.size; x++) {
            if (q.get(x, y)) g.fillRect(off + (x + quiet) * scale, off + (y + quiet) * scale, scale, scale);
        }
    }

    return { encode: encode, draw: draw };
})();
