/* MiniBank internet banking: satu halaman, tanpa library.
   Semua data diambil dari API MiniBank di origin yang sama. */
(() => {
  'use strict';

  /* ---------- utilitas ---------- */
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const app = $('#app');
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const store = {
    get(k) { try { return sessionStorage.getItem(k); } catch { return null; } },
    set(k, v) { try { v == null ? sessionStorage.removeItem(k) : sessionStorage.setItem(k, v); } catch { /* mode privat */ } },
  };
  const uuid = () => (crypto.randomUUID ? crypto.randomUUID()
    : 'k' + Date.now().toString(36) + Math.random().toString(36).slice(2, 12));
  const nf = new Intl.NumberFormat('id-ID', { maximumFractionDigits: 0 });
  const TZ = 'Asia/Jakarta';

  const S = {
    token: store.get('mb.token'),
    me: null,
    accounts: [],
    acct: Number(store.get('mb.acct') || 0),
    hide: store.get('mb.hide') === '1',
    flow: null,
  };

  const LIMITS = { min: 10000, maxTx: 50000000, daily: 100000000 };

  /* ---------- ikon (garis, 24px) ---------- */
  const P = {
    up: '<path d="M12 19V5M5 12l7-7 7 7"/>',
    down: '<path d="M12 5v14M19 12l-7 7-7-7"/>',
    eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    eyeOff: '<path d="M3 3l18 18M10.6 10.6a3 3 0 0 0 4.2 4.2M9.9 5.1A10.8 10.8 0 0 1 12 5c6.5 0 10 7 10 7a17.6 17.6 0 0 1-3.2 4.2M6.6 6.6C3.8 8.4 2 12 2 12s3.5 7 10 7a10.6 10.6 0 0 0 5.4-1.4"/>',
    copy: '<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/>',
    out: '<path d="M10 17l5-5-5-5M15 12H3M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5"/>',
    check: '<path d="M20 6L9 17l-5-5"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    home: '<path d="M3 11l9-8 9 8v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
    send: '<path d="M7 17L17 7M8 7h9v9"/>',
    list: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
    wallet: '<rect x="3" y="6" width="18" height="14" rx="2"/><path d="M3 10h18M16 15h2"/>',
    desk: '<path d="M3 10h18M5 10V6a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v4M6 10v10M18 10v10M9 14h6"/>',
    refresh: '<path d="M21 12a9 9 0 1 1-3-6.7L21 8M21 3v5h-5"/>',
    x: '<path d="M18 6L6 18M6 6l12 12"/>',
    lock: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
  };
  const icon = (n, label) => `<svg class="i" viewBox="0 0 24 24" ${label ? `role="img" aria-label="${esc(label)}"` : 'aria-hidden="true"'}>${P[n]}</svg>`;

  /* ---------- format ---------- */
  function money(v, { hide = false, sign = '' } = {}) {
    const n = Math.abs(Number(v) || 0);
    const int = Math.trunc(n);
    const cents = String(Math.round((n - int) * 100)).padStart(2, '0');
    const val = hide ? '••••••' : nf.format(int);
    return `<span class="money${hide ? ' is-hidden' : ''}">${sign ? `<span class="sign">${sign}</span>` : ''}<span class="cur">Rp</span><span class="val">${val}</span>${hide ? '' : `<span class="cents">,${cents}</span>`}</span>`;
  }
  const rp = (v) => 'Rp' + nf.format(Math.trunc(Number(v) || 0));
  const fmtDate = (iso, withTime = true) => new Date(iso).toLocaleString('id-ID', {
    timeZone: TZ, day: 'numeric', month: 'short', year: withTime ? undefined : 'numeric',
    ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}),
  });
  const fmtNo = (no, hide) => hide ? `${no.slice(0, 4)} ···· ${no.slice(-3)}` : `${no.slice(0, 3)} ${no.slice(3, 7)} ${no.slice(7)}`;
  const initials = (name) => (name || '?').split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('');
  const todayISO = (offsetDays = 0) => {
    const d = new Date(Date.now() + offsetDays * 864e5);
    return d.toLocaleDateString('en-CA', { timeZone: TZ });
  };
  const digits = (s) => String(s || '').replace(/\D/g, '');

  /* ---------- API ---------- */
  class ApiError extends Error {
    constructor(status, code, message, fields) { super(message); this.status = status; this.code = code; this.fields = fields || {}; }
  }
  const DEFAULT_MSG = {
    0: 'Tidak bisa terhubung ke server. Periksa koneksi lalu coba lagi.',
    401: 'Sesi berakhir. Silakan masuk lagi.',
    403: 'Akun ini tidak punya akses ke fitur tersebut.',
    500: 'Terjadi kesalahan pada sistem. Coba lagi sebentar lagi.',
  };

  async function api(path, { method = 'GET', body, key } = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (S.token) headers.Authorization = 'Bearer ' + S.token;
    if (key) headers['Idempotency-Key'] = key;
    let res;
    try {
      res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch {
      throw new ApiError(0, 'NETWORK', DEFAULT_MSG[0]);
    }
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = null; }
    if (!res.ok) {
      if (res.status === 401 && S.token && !path.startsWith('/api/auth/')) {
        logout(DEFAULT_MSG[401]);
      }
      throw new ApiError(res.status, data?.code || 'HTTP_' + res.status,
        data?.detail || DEFAULT_MSG[res.status] || DEFAULT_MSG[500], data?.fields);
    }
    return { data, headers: res.headers, status: res.status };
  }

  /* ---------- toast ---------- */
  function toast(msg, type = 'ok') {
    const el = document.createElement('div');
    el.className = 'toast ' + type;
    el.innerHTML = `<span class="dot" aria-hidden="true"></span><div>${esc(msg)}</div>`;
    $('#toasts').append(el);
    setTimeout(() => { el.style.opacity = '0'; el.style.transition = 'opacity .3s'; setTimeout(() => el.remove(), 320); }, type === 'err' ? 6000 : 3500);
  }

  async function copy(text, label = 'Nomor rekening') {
    try { await navigator.clipboard.writeText(text); }
    catch {
      const t = Object.assign(document.createElement('textarea'), { value: text });
      document.body.append(t); t.select(); document.execCommand('copy'); t.remove();
    }
    toast(`${label} disalin`);
  }

  function busy(btn, on, text) {
    if (!btn) return;
    if (on) { btn.dataset.label = btn.innerHTML; btn.disabled = true; btn.innerHTML = `<span class="spin" aria-hidden="true"></span>${esc(text || 'Memproses')}`; }
    else { btn.disabled = false; if (btn.dataset.label) btn.innerHTML = btn.dataset.label; }
  }

  function showFieldErrors(form, fields) {
    $$('.err', form).forEach((e) => { e.textContent = ''; });
    $$('[aria-invalid]', form).forEach((i) => i.removeAttribute('aria-invalid'));
    Object.entries(fields || {}).forEach(([name, msg]) => {
      const input = form.elements[name];
      const err = $(`[data-err="${name}"]`, form);
      if (input) input.setAttribute('aria-invalid', 'true');
      if (err) err.textContent = msg.charAt(0).toUpperCase() + msg.slice(1);
    });
  }

  /* ---------- sesi ---------- */
  function setToken(t) { S.token = t; store.set('mb.token', t); }
  function logout(msg) {
    setToken(null); S.me = null; S.accounts = []; S.flow = null;
    if (msg) toast(msg, 'err');
    location.hash = '#/';
    render();
  }
  async function loadMe() {
    S.me = (await api('/api/me')).data;
    if (S.me.role === 'CUSTOMER') await loadAccounts();
  }
  async function loadAccounts() {
    S.accounts = (await api('/api/accounts')).data || [];
    if (S.acct >= S.accounts.length) S.acct = 0;
  }
  const currentAcct = () => S.accounts[S.acct];
  function selectAcct(i) { S.acct = i; store.set('mb.acct', String(i)); }

  /* ---------- router ---------- */
  const CUSTOMER_NAV = [
    ['/', 'Beranda', 'home'], ['/transfer', 'Transfer', 'send'], ['/mutasi', 'Mutasi', 'list'], ['/rekening', 'Rekening', 'wallet'],
  ];
  const ADMIN_NAV = [['/teller', 'Meja teller', 'desk']];
  const VIEWS = { '/': viewHome, '/transfer': viewTransfer, '/mutasi': viewMutasi, '/rekening': viewRekening, '/teller': viewTeller };
  const route = () => (location.hash.replace(/^#/, '').split('?')[0] || '/');

  async function render() {
    if (!S.token) return viewAuth();
    if (!S.me) {
      app.innerHTML = '<div class="boot"><div class="sk" style="width:180px;height:18px"></div></div>';
      try { await loadMe(); } catch (e) { if (S.token) { toast(e.message, 'err'); logout(); } return; }
    }
    const admin = S.me.role === 'ADMIN';
    let r = route();
    if (admin && r !== '/teller') { location.replace('#/teller'); r = '/teller'; }
    if (!admin && (r === '/teller' || !VIEWS[r])) { location.replace('#/'); r = '/'; }
    shell(r, admin ? ADMIN_NAV : CUSTOMER_NAV);
    const main = $('#main');
    main.focus({ preventScroll: true });
    window.scrollTo(0, 0);
    await VIEWS[r](main);
  }
  addEventListener('hashchange', render);

  function shell(r, nav) {
    const links = nav.map(([href, label]) => `<a href="#${href}" ${href === r ? 'aria-current="page"' : ''}>${label}</a>`).join('');
    const mobile = nav.map(([href, label, ic]) => `<a href="#${href}" ${href === r ? 'aria-current="page"' : ''}>${icon(ic)}<span>${label}</span></a>`).join('');
    app.innerHTML = `
      <header class="top">
        <a class="wordmark" href="#/" aria-label="MiniBank, ke beranda"><span class="dot" aria-hidden="true"></span>MiniBank</a>
        <nav class="nav" aria-label="Menu utama">${links}</nav>
        <div class="who">
          <div class="name"><b>${esc(S.me.fullName)}</b><span>${S.me.role === 'ADMIN' ? 'Teller' : 'Nasabah'}</span></div>
          <span class="avatar" aria-hidden="true">${esc(initials(S.me.fullName))}</span>
          <button class="icon-btn" id="logout" title="Keluar">${icon('out', 'Keluar')}</button>
        </div>
      </header>
      <main class="page" id="main" tabindex="-1"></main>
      ${nav.length > 1 ? `<nav class="bottom-nav" aria-label="Menu utama">${mobile}</nav>` : ''}`;
    $('#logout').onclick = () => logout();
  }

  /* =====================================================================
     Masuk & daftar
     ===================================================================== */
  function viewAuth(mode = 'login') {
    app.innerHTML = `
      <div class="auth">
        <section class="auth-poster">
          <a class="wordmark" href="#/"><span class="dot" aria-hidden="true"></span>MiniBank</a>
          <h1 class="headline h-xl">Uang kamu,<span class="sub">dicatat dua kali.</span></h1>
          <div class="dots4" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
          <div class="auth-notes">
            <div><b>Pembukuan berpasangan</b><span>Tiap rupiah yang keluar tercatat masuk di rekening lain.</span></div>
            <div><b>Anti transfer ganda</b><span>Tombol kirim terpencet dua kali tetap jadi satu transaksi.</span></div>
            <div><b>PIN terkunci otomatis</b><span>Salah tiga kali, PIN dikunci 15 menit.</span></div>
          </div>
        </section>
        <section class="auth-panel" aria-labelledby="auth-title">
          <div class="seg" role="tablist" aria-label="Pilih masuk atau daftar">
            <button role="tab" id="tab-login" aria-selected="${mode === 'login'}">Masuk</button>
            <button role="tab" id="tab-reg" aria-selected="${mode === 'register'}">Buka rekening</button>
          </div>
          <div id="auth-body"></div>
        </section>
      </div>`;
    $('#tab-login').onclick = () => viewAuth('login');
    $('#tab-reg').onclick = () => viewAuth('register');
    mode === 'login' ? loginForm() : registerForm();
  }

  function loginForm() {
    $('#auth-body').innerHTML = `
      <h2 id="auth-title">Masuk</h2>
      <p>Pakai email dan password yang kamu daftarkan.</p>
      <form class="form" id="f" novalidate>
        <div class="field"><label for="email">Email</label>
          <input class="input" id="email" name="email" type="email" autocomplete="email" required>
          <span class="err" data-err="email"></span></div>
        <div class="field"><label for="password">Password</label>
          <input class="input" id="password" name="password" type="password" autocomplete="current-password" required>
          <span class="err" data-err="password"></span></div>
        <button class="btn block" type="submit">Masuk</button>
      </form>
      <p class="demo">Mau mencoba sebagai teller di komputer sendiri? <button type="button" id="demo">Isi akun teller</button></p>`;
    const f = $('#f');
    $('#demo').onclick = () => { f.email.value = 'teller@minibank.local'; f.password.value = 'Teller12345'; f.email.focus(); };
    f.onsubmit = async (e) => {
      e.preventDefault();
      const btn = $('button[type=submit]', f);
      busy(btn, true, 'Masuk');
      try {
        const { data } = await api('/api/auth/login', { method: 'POST', body: { email: f.email.value.trim(), password: f.password.value } });
        setToken(data.accessToken);
        location.hash = '#/';
        await render();
      } catch (err) {
        busy(btn, false);
        showFieldErrors(f, err.fields);
        if (!Object.keys(err.fields).length) toast(err.message, 'err');
      }
    };
    f.email.focus();
  }

  function registerForm() {
    $('#auth-body').innerHTML = `
      <h2 id="auth-title">Buka rekening</h2>
      <p>Rekening tabungan langsung aktif setelah daftar.</p>
      <form class="form" id="f" novalidate>
        <div class="field"><label for="fullName">Nama lengkap</label>
          <input class="input" id="fullName" name="fullName" autocomplete="name" required>
          <span class="err" data-err="fullName"></span></div>
        <div class="field"><label for="email">Email</label>
          <input class="input" id="email" name="email" type="email" autocomplete="email" required>
          <span class="err" data-err="email"></span></div>
        <div class="field"><label for="phone">Nomor HP</label>
          <input class="input" id="phone" name="phone" inputmode="tel" autocomplete="tel" placeholder="081234567890" required>
          <span class="err" data-err="phone"></span></div>
        <div class="field"><label for="password">Password</label>
          <input class="input" id="password" name="password" type="password" autocomplete="new-password" required>
          <span class="hint">Minimal 8 karakter, berisi huruf dan angka.</span>
          <span class="err" data-err="password"></span></div>
        <div class="field"><label for="pin">PIN transaksi</label>
          <input class="input" id="pin" name="pin" type="password" inputmode="numeric" maxlength="6" autocomplete="off" required>
          <span class="hint">6 angka. Hindari 123456 atau angka kembar.</span>
          <span class="err" data-err="pin"></span></div>
        <button class="btn block" type="submit">Buka rekening</button>
      </form>`;
    const f = $('#f');
    f.pin.oninput = () => { f.pin.value = digits(f.pin.value).slice(0, 6); };
    f.onsubmit = async (e) => {
      e.preventDefault();
      const btn = $('button[type=submit]', f);
      busy(btn, true, 'Membuka rekening');
      const body = Object.fromEntries(['fullName', 'email', 'phone', 'password', 'pin'].map((k) => [k, f[k].value.trim()]));
      try {
        const { data } = await api('/api/auth/register', { method: 'POST', body });
        setToken(data.token.accessToken);
        toast(`Rekening ${data.account.accountNumber} sudah aktif`);
        location.hash = '#/';
        await render();
      } catch (err) {
        busy(btn, false);
        if (err.code === 'WEAK_PIN') err.fields = { pin: err.message };
        if (err.code === 'EMAIL_TAKEN') err.fields = { email: err.message };
        showFieldErrors(f, err.fields);
        if (!Object.keys(err.fields).length) toast(err.message, 'err');
      }
    };
    f.fullName.focus();
  }

  /* =====================================================================
     Kartu rekening (dipakai di beranda dan halaman rekening)
     ===================================================================== */
  function accountCard(a, { actions = true } = {}) {
    const frozen = a.status !== 'ACTIVE';
    return `
      <article class="acard${frozen ? ' frozen' : ''}" aria-label="Rekening ${esc(a.accountNumber)}">
        <div class="acard-top">
          <span><b>Tabungan</b> <span class="light">MiniBank</span></span>
          ${frozen ? '<span class="badge red">Dibekukan</span>' : '<span class="toggle" aria-hidden="true"><i></i><i></i></span>'}
        </div>
        <div class="reds" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div>
        <div class="acard-bottom">
          <span class="no">${esc(fmtNo(a.accountNumber, S.hide))}</span>
          ${actions ? `<span class="actions-row">
            <button class="icon-btn" data-act="hide" title="${S.hide ? 'Tampilkan saldo' : 'Sembunyikan saldo'}">${icon(S.hide ? 'eye' : 'eyeOff', S.hide ? 'Tampilkan saldo' : 'Sembunyikan saldo')}</button>
            <button class="icon-btn" data-act="copy" data-no="${esc(a.accountNumber)}" title="Salin nomor rekening">${icon('copy', 'Salin nomor rekening')}</button>
          </span>` : ''}
        </div>
        <div aria-label="Saldo">${money(a.balance, { hide: S.hide })}</div>
      </article>`;
  }
  function bindCard(root, rerender) {
    $$('[data-act="hide"]', root).forEach((b) => { b.onclick = () => { S.hide = !S.hide; store.set('mb.hide', S.hide ? '1' : '0'); rerender(); }; });
    $$('[data-act="copy"]', root).forEach((b) => { b.onclick = () => copy(b.dataset.no); });
  }

  function acctTabs(onPick) {
    if (S.accounts.length < 2) return '';
    return `<div class="acct-tabs" role="group" aria-label="Pilih rekening">${S.accounts.map((a, i) =>
      `<button class="chip" data-i="${i}" aria-pressed="${i === S.acct}">${esc(a.accountNumber.slice(-4))}</button>`).join('')}</div>`;
  }
  function bindTabs(root, onPick) {
    $$('.acct-tabs .chip', root).forEach((b) => { b.onclick = () => { selectAcct(Number(b.dataset.i)); onPick(); }; });
  }

  /* statement lengkap untuk satu rentang (beberapa halaman sekaligus) */
  async function fetchStatement(no, from, to, maxPages = 5) {
    const items = [];
    let page = 0, totalPages = 1, current = 0;
    while (page < totalPages && page < maxPages) {
      const { data } = await api(`/api/accounts/${no}/statement?from=${from}&to=${to}&page=${page}&size=100`);
      items.push(...data.items);
      totalPages = data.totalPages;
      current = data.currentBalance;
      page++;
    }
    return { items, currentBalance: current };
  }

  function describe(it) {
    const out = it.direction === 'DEBIT';
    if (it.type === 'DEPOSIT') return { title: 'Setor tunai', out };
    if (it.type === 'WITHDRAWAL') return { title: 'Tarik tunai', out };
    return { title: out ? `Transfer ke ${it.counterparty}` : `Transfer dari ${it.counterparty}`, out };
  }

  /* =====================================================================
     Beranda
     ===================================================================== */
  async function viewHome(main) {
    const a = currentAcct();
    if (!a) {
      main.innerHTML = `<div class="empty"><b>Belum ada rekening</b>Buka rekening baru dari menu Rekening.</div>`;
      return;
    }
    const first = esc(S.me.fullName.split(/\s+/)[0]);
    main.innerHTML = `
      <div class="home">
        <section class="home-left" aria-labelledby="act-title">
          <div class="activity-head">
            <h1 class="headline h-lg" id="act-title">Halo, ${first}<span class="sub" id="act-sub"><span class="sk" style="display:inline-block;width:220px;height:.8em"></span></span></h1>
          </div>
          <div class="legend"><span><i class="out"></i>Uang keluar</span><span><i class="in"></i>Uang masuk</span><span>12 minggu terakhir</span></div>
          <div class="chart" id="chart" role="img" aria-label="Grafik aktivitas 12 minggu">${'<div class="bar"><div class="cols"><span class="sk" style="width:30px;height:40%"></span></div><span class="lbl">&nbsp;</span></div>'.repeat(12)}</div>
          <p class="muted" id="chart-note" hidden></p>
        </section>
        <section class="home-right">
          ${acctTabs()}
          <div id="card">${accountCard(a)}</div>
          <div class="card">
            <div class="card-head"><h2>Transaksi terakhir</h2><a href="#/mutasi" class="code">Lihat semua</a></div>
            <div class="list" id="recent">${'<div class="row"><span class="ic sk"></span><span class="sk" style="height:14px"></span><span class="sk" style="width:80px;height:14px"></span></div>'.repeat(3)}</div>
          </div>
        </section>
      </div>
      <nav class="dock" aria-label="Aksi cepat">
        <a href="#/transfer"><span class="round">${icon('up')}</span>Transfer</a>
        <a href="#/mutasi" class="only" title="Mutasi" aria-label="Mutasi"><span class="round"><span class="grid4" aria-hidden="true"><i></i><i></i><i></i><i></i></span></span></a>
        <button id="receive"><span class="round">${icon('down')}</span>Terima</button>
      </nav>`;
    const rerender = () => viewHome(main);
    bindCard(main, rerender);
    bindTabs(main, rerender);
    $('#receive').onclick = () => receiveDialog(a);

    try {
      const { items } = await fetchStatement(a.accountNumber, todayISO(-83), todayISO(0));
      drawChart(items);
      drawRecent(items.slice(0, 5));
    } catch (e) {
      toast(e.message, 'err');
    }
  }

  function drawChart(items) {
    const now = new Date();
    const weeks = Array.from({ length: 12 }, (_, i) => {
      const start = new Date(now.getTime() - (11 - i) * 7 * 864e5 - 6 * 864e5);
      return { start, out: 0, in: 0 };
    });
    const t0 = weeks[0].start.getTime();
    for (const it of items) {
      const idx = Math.floor((new Date(it.date).getTime() - t0) / (7 * 864e5));
      if (idx < 0 || idx > 11) continue;
      if (it.direction === 'DEBIT') weeks[idx].out += Number(it.amount); else weeks[idx].in += Number(it.amount);
    }
    const max = Math.max(1, ...weeks.map((w) => Math.max(w.out, w.in)));
    const totalOut = weeks.reduce((s, w) => s + w.out, 0);
    const totalIn = weeks.reduce((s, w) => s + w.in, 0);
    $('#act-sub').innerHTML = `${rp(totalOut)} keluar`;
    $('#chart').setAttribute('aria-label', `Grafik 12 minggu: total keluar ${rp(totalOut)}, total masuk ${rp(totalIn)}`);
    $('#chart').innerHTML = weeks.map((w, i) => {
      const label = w.start.toLocaleDateString('id-ID', { timeZone: TZ, day: 'numeric', month: 'short' });
      return `<div class="bar">
        <div class="cols">
          <span class="o" style="height:${(w.out / max) * 100}%;animation-delay:${i * 40}ms"></span>
          <span class="n" style="height:${(w.in / max) * 100}%;animation-delay:${i * 40 + 20}ms"></span>
        </div>
        <span class="lbl">${esc(label)}</span>
        <button aria-label="Minggu mulai ${esc(label)}: keluar ${rp(w.out)}, masuk ${rp(w.in)}"></button>
        <span class="tip" aria-hidden="true">Mulai ${esc(label)}<br>Keluar <b>${rp(w.out)}</b><br>Masuk <b>${rp(w.in)}</b></span>
      </div>`;
    }).join('');
    const note = $('#chart-note');
    if (!items.length) { note.hidden = false; note.textContent = 'Belum ada transaksi dalam 12 minggu terakhir. Minta teller menyetor dana untuk memulai.'; }
  }

  function txRow(it) {
    const d = describe(it);
    const note = it.description ? esc(it.description) + ' · ' : '';
    return `<div class="row">
      <span class="ic ${d.out ? 'out' : ''}">${icon(d.out ? 'up' : 'down')}</span>
      <div class="t"><b>${esc(d.title)}</b><span>${note}${esc(fmtDate(it.date))}</span></div>
      <div class="amt ${d.out ? 'out' : ''}">${d.out ? '−' : '+'}${rp(it.amount)}<small>Saldo ${rp(it.balanceAfter)}</small></div>
    </div>`;
  }
  function drawRecent(items) {
    $('#recent').innerHTML = items.length ? items.map(txRow).join('')
      : `<div class="empty" style="padding:24px 0"><b>Belum ada transaksi</b>Transfer pertamamu akan muncul di sini.</div>`;
  }

  function receiveDialog(a) {
    const dlg = document.createElement('dialog');
    dlg.setAttribute('aria-labelledby', 'rcv-title');
    dlg.innerHTML = `<div class="sheet">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <h2 class="h-md" id="rcv-title">Terima uang</h2>
        <button class="icon-btn" data-close>${icon('x', 'Tutup')}</button>
      </div>
      <p class="muted">Bagikan nomor rekening ini ke pengirim. Pengirim akan melihat nama ${esc(maskName(S.me.fullName))}.</p>
      <div class="big-no">${esc(fmtNo(a.accountNumber, false))}</div>
      <button class="btn block" data-copy>${icon('copy')}Salin nomor rekening</button>
    </div>`;
    document.body.append(dlg);
    dlg.showModal();
    $('[data-close]', dlg).onclick = () => dlg.close();
    $('[data-copy]', dlg).onclick = () => copy(a.accountNumber);
    dlg.addEventListener('close', () => dlg.remove());
  }
  const maskName = (n) => n.trim().split(/\s+/).map((w) => w.length <= 2 ? w : w.slice(0, 2) + '*'.repeat(w.length - 2)).join(' ');

  /* =====================================================================
     Transfer: 1 tujuan, 2 nominal, 3 PIN, 4 struk
     ===================================================================== */
  function newFlow() { return { step: 1, to: '', recipient: null, amount: 0, note: '', key: null, result: null, replayed: false }; }

  async function viewTransfer(main) {
    if (!S.flow) S.flow = newFlow();
    const a = currentAcct();
    if (!a) { main.innerHTML = '<div class="empty"><b>Belum ada rekening</b></div>'; return; }
    const F = S.flow;
    const steps = ['Tujuan', 'Nominal', 'Konfirmasi', 'Bukti'];
    main.innerHTML = `
      <div class="page-head">
        <h1 class="headline h-lg">Transfer<span class="sub">ke sesama MiniBank</span></h1>
      </div>
      <div class="flow">
        <section class="card" aria-labelledby="step-title">
          <ol class="steps" aria-label="Langkah transfer">${steps.map((s, i) => {
            const n = i + 1;
            return `<li class="${n < F.step ? 'done' : ''}" ${n === F.step ? 'aria-current="step"' : ''}><i></i><span>${n}. ${s}</span></li>`;
          }).join('')}</ol>
          <div id="step"></div>
        </section>
        <aside class="side-info" aria-label="Info rekening dan batas transfer">
          ${acctTabs()}
          ${accountCard(a)}
          <div><h3>Batas transfer</h3><p>Minimal ${rp(LIMITS.min)}, maksimal ${rp(LIMITS.maxTx)} per transaksi, dan ${rp(LIMITS.daily)} per hari.</p></div>
          <div><h3>PIN</h3><p>Salah PIN tiga kali membuat PIN terkunci 15 menit.</p></div>
          <div><h3>Kirim ulang aman</h3><p>Kalau koneksi putus saat mengirim, tekan kirim lagi. Transaksi yang sama tidak akan terpotong dua kali.</p></div>
        </aside>
      </div>`;
    const rerender = () => viewTransfer(main);
    bindCard(main, rerender);
    bindTabs(main, () => { if (F.step < 4) { F.recipient = null; F.step = 1; F.key = null; } rerender(); });
    const box = $('#step');
    if (F.step === 1) stepTujuan(box, a, rerender);
    if (F.step === 2) stepNominal(box, a, rerender);
    if (F.step === 3) stepKonfirmasi(box, a, rerender);
    if (F.step === 4) stepBukti(box, rerender);
  }

  function stepTujuan(box, a, rerender) {
    const F = S.flow;
    const others = S.accounts.filter((x) => x.accountNumber !== a.accountNumber);
    box.innerHTML = `
      <h2 class="h-md" id="step-title" style="margin-bottom:18px">Ke rekening mana?</h2>
      <form class="form" id="f" novalidate>
        <div class="field"><label for="to">Nomor rekening tujuan</label>
          <input class="input" id="to" name="to" inputmode="numeric" maxlength="13" autocomplete="off" placeholder="10 digit" value="${esc(F.to)}">
          <span class="err" data-err="to"></span></div>
        ${others.length ? `<div class="field"><span class="hint">Rekening sendiri</span><div class="chips">${others.map((o) =>
          `<button type="button" class="chip" data-no="${esc(o.accountNumber)}">${esc(fmtNo(o.accountNumber, false))}</button>`).join('')}</div></div>` : ''}
        <div id="rcp">${F.recipient ? recipientBox(F.recipient) : ''}</div>
        <div class="btn-row">
          <span></span>
          <button class="btn" type="submit">${F.recipient ? 'Lanjut' : 'Cek rekening'}</button>
        </div>
      </form>`;
    const f = $('#f');
    f.to.oninput = () => {
      const d = digits(f.to.value).slice(0, 10);
      f.to.value = d;
      if (F.recipient && d !== F.recipient.accountNumber) { F.recipient = null; $('#rcp').innerHTML = ''; $('button[type=submit]', f).textContent = 'Cek rekening'; }
    };
    $$('[data-no]', f).forEach((b) => { b.onclick = () => { f.to.value = b.dataset.no; f.to.dispatchEvent(new Event('input')); f.requestSubmit(); }; });
    f.onsubmit = async (e) => {
      e.preventDefault();
      const no = digits(f.to.value);
      showFieldErrors(f, {});
      if (F.recipient && F.recipient.accountNumber === no) { F.to = no; F.step = 2; return rerender(); }
      if (no.length !== 10) return showFieldErrors(f, { to: 'Nomor rekening harus 10 digit' });
      if (no === a.accountNumber) return showFieldErrors(f, { to: 'Tidak bisa transfer ke rekening yang sama' });
      const btn = $('button[type=submit]', f);
      busy(btn, true, 'Mengecek');
      try {
        const { data } = await api(`/api/accounts/${no}/inquiry`);
        F.recipient = data; F.to = no;
        $('#rcp').innerHTML = recipientBox(data);
        busy(btn, false); btn.textContent = 'Lanjut'; btn.focus();
      } catch (err) {
        busy(btn, false);
        showFieldErrors(f, { to: err.message });
      }
    };
    if (!F.recipient) f.to.focus();
  }
  const recipientBox = (r) => `<div class="recipient"><span class="avatar" aria-hidden="true">${esc(initials(r.holderName.replace(/\*/g, '')))}</span>
    <div><b>${esc(r.holderName)}</b><span>${esc(fmtNo(r.accountNumber, false))} · MiniBank</span></div></div>`;

  function stepNominal(box, a, rerender) {
    const F = S.flow;
    const presets = [50000, 100000, 250000, 500000];
    box.innerHTML = `
      <h2 class="h-md" id="step-title" style="margin-bottom:6px">Berapa yang dikirim?</h2>
      <p class="muted" style="margin-bottom:18px">Ke ${esc(F.recipient.holderName)} · ${esc(fmtNo(F.recipient.accountNumber, false))}</p>
      <form class="form" id="f" novalidate>
        <div class="field"><label for="amount">Nominal</label>
          <div class="prefix"><span>Rp</span><input class="input big" id="amount" name="amount" inputmode="numeric" autocomplete="off" value="${F.amount ? nf.format(F.amount) : ''}" placeholder="0"></div>
          <span class="hint" id="amt-hint">Saldo tersedia ${rp(a.balance)}</span>
          <span class="err" data-err="amount"></span></div>
        <div class="chips" role="group" aria-label="Nominal cepat">${presets.map((p) => `<button type="button" class="chip" data-v="${p}">${nf.format(p)}</button>`).join('')}</div>
        <div class="field"><label for="note">Catatan <span class="muted">(boleh kosong)</span></label>
          <input class="input" id="note" name="note" maxlength="140" placeholder="Misalnya: bayar kos November" value="${esc(F.note)}"></div>
        <div class="btn-row">
          <button class="btn ghost" type="button" id="back">Kembali</button>
          <button class="btn" type="submit">Lanjut</button>
        </div>
      </form>`;
    const f = $('#f');
    const setAmt = (v) => { f.amount.value = v ? nf.format(v) : ''; };
    f.amount.oninput = () => { const v = Number(digits(f.amount.value).slice(0, 12)); setAmt(v); };
    $$('[data-v]', f).forEach((b) => { b.onclick = () => { setAmt(Number(b.dataset.v)); f.amount.focus(); }; });
    $('#back').onclick = () => { F.step = 1; rerender(); };
    f.onsubmit = (e) => {
      e.preventDefault();
      const v = Number(digits(f.amount.value));
      let msg = '';
      if (!v) msg = 'Isi nominal transfer';
      else if (v < LIMITS.min) msg = `Minimal transfer ${rp(LIMITS.min)}`;
      else if (v > LIMITS.maxTx) msg = `Maksimal ${rp(LIMITS.maxTx)} per transaksi`;
      else if (v > Number(a.balance)) msg = `Saldo tidak cukup. Saldo tersedia ${rp(a.balance)}`;
      if (msg) return showFieldErrors(f, { amount: msg });
      if (v !== F.amount || f.note.value.trim() !== F.note) F.key = null; // isi berubah: kunci baru
      F.amount = v; F.note = f.note.value.trim(); F.step = 3;
      rerender();
    };
    f.amount.focus();
  }

  function stepKonfirmasi(box, a, rerender) {
    const F = S.flow;
    if (!F.key) F.key = uuid(); // satu kunci untuk satu niat transfer, dipakai ulang saat kirim ulang
    box.innerHTML = `
      <h2 class="h-md" id="step-title" style="margin-bottom:12px">Periksa lalu masukkan PIN</h2>
      <dl class="summary">
        <div><dt>Dari</dt><dd>${esc(fmtNo(a.accountNumber, false))}</dd></div>
        <div><dt>Ke</dt><dd>${esc(F.recipient.holderName)}<br><span class="muted" style="font-weight:400">${esc(fmtNo(F.recipient.accountNumber, false))}</span></dd></div>
        ${F.note ? `<div><dt>Catatan</dt><dd>${esc(F.note)}</dd></div>` : ''}
        <div><dt>Biaya</dt><dd>Rp0</dd></div>
        <div class="total"><dt>Total</dt><dd>${rp(F.amount)}</dd></div>
      </dl>
      <form class="form" id="f" novalidate style="margin-top:22px">
        <div class="field" style="justify-items:center;text-align:center">
          <label for="pin">PIN transaksi</label>
          <input class="input pin" id="pin" name="pin" type="password" inputmode="numeric" maxlength="6" autocomplete="off" aria-describedby="pin-err">
          <span class="err" id="pin-err" data-err="pin"></span>
        </div>
        <div class="btn-row">
          <button class="btn ghost" type="button" id="back">Ubah nominal</button>
          <button class="btn red" type="submit">${icon('lock')}Kirim ${rp(F.amount)}</button>
        </div>
      </form>`;
    const f = $('#f');
    f.pin.oninput = () => { f.pin.value = digits(f.pin.value).slice(0, 6); };
    $('#back').onclick = () => { F.step = 2; rerender(); };
    f.onsubmit = async (e) => {
      e.preventDefault();
      if (f.pin.value.length !== 6) return showFieldErrors(f, { pin: 'PIN terdiri dari 6 angka' });
      const btn = $('button[type=submit]', f);
      busy(btn, true, 'Mengirim');
      try {
        const res = await api('/api/transfers', {
          method: 'POST', key: F.key,
          body: { fromAccount: a.accountNumber, toAccount: F.recipient.accountNumber, amount: F.amount, description: F.note || null, pin: f.pin.value },
        });
        F.result = res.data;
        F.replayed = res.headers.get('Idempotent-Replayed') === 'true';
        F.step = 4;
        await loadAccounts();
        rerender();
      } catch (err) {
        busy(btn, false);
        f.pin.value = '';
        if (err.code === 'INVALID_PIN' || err.code === 'PIN_LOCKED') { showFieldErrors(f, { pin: err.message }); f.pin.focus(); return; }
        if (err.code === 'NETWORK') { toast(err.message + ' Transfer ini aman dikirim ulang.', 'err'); return; }
        // ditolak aturan bank (saldo, limit, rekening dibekukan): kunci baru untuk percobaan berikutnya
        F.key = null;
        toast(err.message, 'err');
      }
    };
    f.pin.focus();
  }

  function stepBukti(box, rerender) {
    const F = S.flow;
    const t = F.result;
    box.innerHTML = `
      <h2 class="sr-only" id="step-title">Bukti transfer</h2>
      <div class="receipt-wrap">
        <div class="receipt" role="document" aria-label="Struk transfer">
          <div class="stamp"><i aria-hidden="true"></i>Transfer berhasil</div>
          ${money(t.amount)}
          <div class="muted">${esc(fmtDate(t.createdAt))} WIB</div>
          <hr>
          <dl>
            <dt>Ke</dt><dd>${esc(F.recipient.holderName)}</dd>
            <dt>Rekening tujuan</dt><dd>${esc(fmtNo(t.destinationAccount, false))}</dd>
            <dt>Dari</dt><dd>${esc(fmtNo(t.sourceAccount, false))}</dd>
            ${t.description ? `<dt>Catatan</dt><dd>${esc(t.description)}</dd>` : ''}
            <dt>Biaya</dt><dd>Rp0</dd>
          </dl>
          <hr>
          <dl><dt>No. referensi</dt><dd>${esc(t.reference)}</dd></dl>
          <p class="foot">${F.replayed ? 'Permintaan ini sudah pernah diproses sebelumnya, jadi saldo tidak dipotong lagi.' : 'Simpan nomor referensi sebagai bukti transfer.'}</p>
        </div>
        <div class="btn-row" style="justify-content:center">
          <button class="btn ghost" id="copyref">${icon('copy')}Salin referensi</button>
          <a class="btn ghost" href="#/mutasi">Lihat mutasi</a>
          <button class="btn" id="again">Transfer lagi</button>
        </div>
      </div>`;
    $('#copyref').onclick = () => copy(t.reference, 'Nomor referensi');
    $('#again').onclick = () => { S.flow = newFlow(); rerender(); };
  }

  /* =====================================================================
     Mutasi
     ===================================================================== */
  async function viewMutasi(main) {
    const a = currentAcct();
    if (!a) { main.innerHTML = '<div class="empty"><b>Belum ada rekening</b></div>'; return; }
    const params = new URLSearchParams(location.hash.split('?')[1] || '');
    const from = params.get('from') || todayISO(-29);
    const to = params.get('to') || todayISO(0);
    main.innerHTML = `
      <div class="page-head">
        <h1 class="headline h-lg">Mutasi<span class="sub">${esc(fmtNo(a.accountNumber, false))}</span></h1>
        ${acctTabs()}
      </div>
      <section class="card">
        <form class="filters" id="filt" style="margin-bottom:22px">
          <div class="field"><label for="from">Dari tanggal</label><input class="input" type="date" id="from" name="from" value="${from}" max="${todayISO(0)}"></div>
          <div class="field"><label for="to">Sampai tanggal</label><input class="input" type="date" id="to" name="to" value="${to}" max="${todayISO(0)}"></div>
          <button class="btn" type="submit">Tampilkan</button>
          <span class="hint muted" style="font-size:13px">Rentang maksimal 90 hari.</span>
        </form>
        <div id="mut"><div class="sk" style="height:180px"></div></div>
      </section>`;
    bindTabs(main, () => viewMutasi(main));
    $('#filt').onsubmit = (e) => {
      e.preventDefault();
      const f = e.target;
      location.hash = `#/mutasi?from=${f.from.value}&to=${f.to.value}`;
    };
    try {
      const { items } = await fetchStatement(a.accountNumber, from, to, 10);
      renderLedger(items, 0);
    } catch (err) {
      $('#mut').innerHTML = `<div class="notice">${esc(err.message)}</div>`;
    }
  }

  function renderLedger(items, page) {
    const box = $('#mut');
    if (!items.length) {
      box.innerHTML = '<div class="empty"><b>Tidak ada transaksi</b>Coba pilih rentang tanggal yang lain.</div>';
      return;
    }
    const sumIn = items.filter((i) => i.direction === 'CREDIT').reduce((s, i) => s + Number(i.amount), 0);
    const sumOut = items.filter((i) => i.direction === 'DEBIT').reduce((s, i) => s + Number(i.amount), 0);
    const per = 20, pages = Math.ceil(items.length / per);
    const slice = items.slice(page * per, page * per + per);
    box.innerHTML = `
      <div class="sum-strip">
        <div><span>Uang masuk</span><b>+${rp(sumIn)}</b></div>
        <div><span>Uang keluar</span><b style="color:var(--red-ink)">−${rp(sumOut)}</b></div>
        <div><span>Jumlah transaksi</span><b>${items.length}</b></div>
      </div>
      <div class="table-wrap">
        <table class="ledger">
          <caption class="sr-only">Mutasi rekening</caption>
          <thead><tr><th scope="col">Tanggal</th><th scope="col">Keterangan</th><th scope="col" class="num">Mutasi</th><th scope="col" class="num hide-sm">Saldo</th></tr></thead>
          <tbody>${slice.map((it) => {
            const d = describe(it);
            return `<tr>
              <td class="date">${esc(fmtDate(it.date))}</td>
              <td class="desc"><b>${esc(d.title)}</b><span>${it.description ? esc(it.description) + ' · ' : ''}${esc(it.reference)}</span></td>
              <td class="num ${d.out ? 'out' : 'in'}">${d.out ? '−' : '+'}${rp(it.amount)}</td>
              <td class="num hide-sm">${rp(it.balanceAfter)}</td>
            </tr>`;
          }).join('')}</tbody>
        </table>
      </div>
      ${pages > 1 ? `<div class="pager"><span>Halaman ${page + 1} dari ${pages}</span><div>
        <button class="btn soft" id="prev" ${page === 0 ? 'disabled' : ''}>Sebelumnya</button>
        <button class="btn soft" id="next" ${page >= pages - 1 ? 'disabled' : ''}>Berikutnya</button></div></div>` : ''}`;
    if (pages > 1) {
      $('#prev').onclick = () => renderLedger(items, page - 1);
      $('#next').onclick = () => renderLedger(items, page + 1);
    }
  }

  /* =====================================================================
     Rekening & profil
     ===================================================================== */
  async function viewRekening(main) {
    const max = 3;
    main.innerHTML = `
      <div class="page-head">
        <h1 class="headline h-lg">Rekening<span class="sub">${S.accounts.length} dari ${max} rekening</span></h1>
        <button class="btn" id="open" ${S.accounts.length >= max ? 'disabled' : ''}>${icon('plus')}Buka rekening baru</button>
      </div>
      <div class="accts">${S.accounts.map((a, i) => `
        <div class="stack" style="display:grid;gap:10px">
          ${accountCard(a)}
          <div class="chips">
            <button class="chip" data-mut="${i}">Mutasi</button>
            <button class="chip" data-tf="${i}" ${a.status !== 'ACTIVE' ? 'disabled' : ''}>Transfer dari sini</button>
            <span class="code" style="align-self:center">Dibuka ${esc(fmtDate(a.openedAt, false))}</span>
          </div>
        </div>`).join('')}
      </div>
      <section class="card" style="margin-top:28px;max-width:640px">
        <div class="card-head"><h2>Profil</h2></div>
        <dl class="kv">
          <dt>Nama</dt><dd>${esc(S.me.fullName)}</dd>
          <dt>Email</dt><dd>${esc(S.me.email)}</dd>
          <dt>Nomor HP</dt><dd>${esc(S.me.phone)}</dd>
          <dt>Nasabah sejak</dt><dd>${esc(fmtDate(S.me.createdAt, false))}</dd>
        </dl>
      </section>`;
    bindCard(main, () => viewRekening(main));
    $$('[data-mut]', main).forEach((b) => { b.onclick = () => { selectAcct(Number(b.dataset.mut)); location.hash = '#/mutasi'; }; });
    $$('[data-tf]', main).forEach((b) => { b.onclick = () => { selectAcct(Number(b.dataset.tf)); S.flow = null; location.hash = '#/transfer'; }; });
    $('#open').onclick = async (e) => {
      busy(e.currentTarget, true, 'Membuka');
      try {
        const { data } = await api('/api/accounts', { method: 'POST' });
        await loadAccounts();
        toast(`Rekening ${data.accountNumber} sudah aktif`);
        viewRekening(main);
      } catch (err) { busy(e.currentTarget, false); toast(err.message, 'err'); }
    };
  }

  /* =====================================================================
     Meja teller (admin)
     ===================================================================== */
  const ACTION_LABEL = {
    REGISTER: 'Daftar', LOGIN: 'Masuk', LOGIN_FAILED: 'Gagal masuk', PIN_FAILED: 'PIN salah', PIN_LOCKED: 'PIN terkunci',
    TRANSFER: 'Transfer', CASH_DEPOSIT: 'Setor tunai', CASH_WITHDRAWAL: 'Tarik tunai',
    ACCOUNT_FROZEN: 'Rekening dibekukan', ACCOUNT_UNFROZEN: 'Rekening diaktifkan', ADMIN_SEEDED: 'Akun teller dibuat',
  };
  let tellerTab = 'deposit';

  async function viewTeller(main) {
    main.innerHTML = `
      <div class="page-head">
        <h1 class="headline h-lg">Meja teller<span class="sub">Setor, tarik, dan jaga pembukuan</span></h1>
      </div>
      <section class="card" style="margin-bottom:24px" aria-live="polite">
        <div class="verify" id="verify"><span class="lamp sk" aria-hidden="true"></span><div><b>Memeriksa pembukuan</b><span>Mencocokkan buku besar dengan saldo rekening.</span></div></div>
      </section>
      <div class="seg" role="tablist" aria-label="Fitur teller" style="margin-bottom:20px">
        ${[['deposit', 'Setor tunai'], ['withdraw', 'Tarik tunai'], ['account', 'Rekening'], ['audit', 'Jejak audit']].map(([k, l]) =>
          `<button role="tab" data-tab="${k}" aria-selected="${tellerTab === k}">${l}</button>`).join('')}
      </div>
      <div id="tpanel"></div>`;
    $$('[data-tab]', main).forEach((b) => { b.onclick = () => { tellerTab = b.dataset.tab; viewTeller(main); }; });
    verifyLedger();
    const panel = $('#tpanel');
    if (tellerTab === 'deposit' || tellerTab === 'withdraw') cashForm(panel, tellerTab);
    if (tellerTab === 'account') accountAdmin(panel);
    if (tellerTab === 'audit') auditLog(panel);
  }

  async function verifyLedger() {
    const box = $('#verify');
    try {
      const { data } = await api('/api/admin/ledger/verify');
      box.className = 'verify' + (data.balanced ? '' : ' bad');
      box.innerHTML = `<span class="lamp" aria-hidden="true"></span>
        <div style="flex:1"><b>${data.balanced ? 'Pembukuan seimbang' : 'Ada selisih pembukuan'}</b>
        <span>Total debit ${rp(data.totalDebit)} · total kredit ${rp(data.totalCredit)}${data.accountMismatches.length ? ` · ${data.accountMismatches.length} rekening tidak cocok` : ''}</span></div>
        <button class="icon-btn" id="reverify" title="Periksa ulang">${icon('refresh', 'Periksa ulang')}</button>`;
      $('#reverify').onclick = verifyLedger;
    } catch (err) {
      box.innerHTML = `<div class="notice">${esc(err.message)}</div>`;
    }
  }

  function cashForm(panel, kind) {
    const isDep = kind === 'deposit';
    let key = uuid();
    let found = null;
    panel.innerHTML = `
      <div class="teller-grid">
        <section class="card">
          <h2 class="h-md" style="margin-bottom:18px">${isDep ? 'Setor tunai ke rekening' : 'Tarik tunai dari rekening'}</h2>
          <form class="form" id="f" novalidate>
            <div class="field"><label for="acc">Nomor rekening nasabah</label>
              <input class="input" id="acc" name="acc" inputmode="numeric" maxlength="10" autocomplete="off" placeholder="10 digit">
              <span class="err" data-err="acc"></span></div>
            <div id="who"></div>
            <div class="field"><label for="amount">Nominal</label>
              <div class="prefix"><span>Rp</span><input class="input big" id="amount" name="amount" inputmode="numeric" autocomplete="off" placeholder="0"></div>
              <span class="err" data-err="amount"></span></div>
            <div class="chips">${[100000, 500000, 1000000, 5000000].map((v) => `<button type="button" class="chip" data-v="${v}">${nf.format(v)}</button>`).join('')}</div>
            <div class="field"><label for="desc">Keterangan <span class="muted">(boleh kosong)</span></label>
              <input class="input" id="desc" name="desc" maxlength="140"></div>
            <button class="btn ${isDep ? '' : 'red'}" type="submit">${isDep ? 'Setor' : 'Tarik'} dana</button>
          </form>
        </section>
        <section class="card"><div class="card-head"><h2>Hasil terakhir</h2></div><div id="last" class="muted">Belum ada transaksi di sesi ini.</div></section>
      </div>`;
    const f = $('#f');
    const lookup = async () => {
      const no = digits(f.acc.value);
      found = null; $('#who').innerHTML = '';
      if (no.length !== 10) return;
      try {
        const { data } = await api(`/api/admin/accounts/${no}`);
        found = data;
        $('#who').innerHTML = `<div class="recipient"><span class="avatar" aria-hidden="true">${esc(initials(data.ownerName))}</span>
          <div><b>${esc(data.ownerName)}</b><span>Saldo ${rp(data.account.balance)} · ${data.account.status === 'ACTIVE' ? 'Aktif' : 'Dibekukan'}</span></div></div>`;
      } catch (err) { showFieldErrors(f, { acc: err.message }); }
    };
    f.acc.oninput = () => { f.acc.value = digits(f.acc.value).slice(0, 10); showFieldErrors(f, {}); key = uuid(); if (f.acc.value.length === 10) lookup(); else $('#who').innerHTML = ''; };
    f.amount.oninput = () => { const v = Number(digits(f.amount.value).slice(0, 13)); f.amount.value = v ? nf.format(v) : ''; key = uuid(); };
    $$('[data-v]', f).forEach((b) => { b.onclick = () => { f.amount.value = nf.format(Number(b.dataset.v)); key = uuid(); }; });
    f.onsubmit = async (e) => {
      e.preventDefault();
      const no = digits(f.acc.value), amount = Number(digits(f.amount.value));
      const errs = {};
      if (no.length !== 10) errs.acc = 'Nomor rekening harus 10 digit';
      if (!amount) errs.amount = 'Isi nominal';
      if (Object.keys(errs).length) return showFieldErrors(f, errs);
      const btn = $('button[type=submit]', f);
      busy(btn, true, 'Memproses');
      try {
        const { data } = await api(isDep ? '/api/admin/deposits' : '/api/admin/withdrawals', {
          method: 'POST', key, body: { accountNumber: no, amount, description: f.desc.value.trim() || null },
        });
        toast(`${isDep ? 'Setor' : 'Tarik'} ${rp(amount)} berhasil`);
        $('#last').innerHTML = `<dl class="kv">
          <dt>Jenis</dt><dd>${isDep ? 'Setor tunai' : 'Tarik tunai'}</dd>
          <dt>Rekening</dt><dd>${esc(fmtNo(no, false))}${found ? ' · ' + esc(found.ownerName) : ''}</dd>
          <dt>Nominal</dt><dd>${rp(data.amount)}</dd>
          <dt>Referensi</dt><dd>${esc(data.reference)}</dd>
          <dt>Waktu</dt><dd>${esc(fmtDate(data.createdAt))}</dd></dl>`;
        f.amount.value = ''; f.desc.value = ''; key = uuid();
        lookup(); verifyLedger();
      } catch (err) {
        if (err.code !== 'NETWORK') key = uuid();
        toast(err.message, 'err');
      } finally { busy(btn, false); }
    };
    f.acc.focus();
  }

  function accountAdmin(panel) {
    panel.innerHTML = `
      <div class="teller-grid">
        <section class="card">
          <h2 class="h-md" style="margin-bottom:18px">Cari rekening</h2>
          <form class="form" id="f" novalidate>
            <div class="field"><label for="acc">Nomor rekening</label>
              <input class="input" id="acc" name="acc" inputmode="numeric" maxlength="10" autocomplete="off">
              <span class="err" data-err="acc"></span></div>
            <button class="btn" type="submit">Cari</button>
          </form>
        </section>
        <section class="card" id="detail"><p class="muted">Detail rekening akan muncul di sini.</p></section>
      </div>`;
    const f = $('#f');
    f.acc.oninput = () => { f.acc.value = digits(f.acc.value).slice(0, 10); };
    const show = async (no) => {
      try {
        const { data } = await api(`/api/admin/accounts/${no}`);
        const active = data.account.status === 'ACTIVE';
        $('#detail').innerHTML = `
          <div class="card-head"><h2>${esc(data.ownerName)}</h2><span class="badge ${active ? '' : 'red'}">${active ? 'Aktif' : 'Dibekukan'}</span></div>
          <dl class="kv" style="margin-bottom:20px">
            <dt>Rekening</dt><dd>${esc(fmtNo(data.account.accountNumber, false))}</dd>
            <dt>Saldo</dt><dd>${rp(data.account.balance)}</dd>
            <dt>Email</dt><dd>${esc(data.ownerEmail)}</dd>
            <dt>Nomor HP</dt><dd>${esc(data.ownerPhone)}</dd>
            <dt>Dibuka</dt><dd>${esc(fmtDate(data.account.openedAt, false))}</dd>
          </dl>
          <form class="form" id="fz" novalidate>
            <div class="field"><label for="reason">Alasan ${active ? 'pembekuan' : 'pengaktifan'}</label>
              <input class="input" id="reason" name="reason" maxlength="200" placeholder="${active ? 'Misalnya: laporan penipuan dari nasabah' : 'Misalnya: identitas sudah diverifikasi'}">
              <span class="err" data-err="reason"></span></div>
            <button class="btn ${active ? 'red' : ''}" type="submit">${active ? 'Bekukan rekening' : 'Aktifkan rekening'}</button>
          </form>`;
        $('#fz').onsubmit = async (e) => {
          e.preventDefault();
          const reason = e.target.reason.value.trim();
          if (!reason) return showFieldErrors(e.target, { reason: 'Tulis alasannya untuk jejak audit' });
          const btn = $('button[type=submit]', e.target);
          busy(btn, true);
          try {
            await api(`/api/admin/accounts/${no}/${active ? 'freeze' : 'unfreeze'}`, { method: 'POST', body: { reason } });
            toast(active ? 'Rekening dibekukan' : 'Rekening diaktifkan');
            show(no);
          } catch (err) { busy(btn, false); toast(err.message, 'err'); }
        };
      } catch (err) { showFieldErrors(f, { acc: err.message }); }
    };
    f.onsubmit = (e) => { e.preventDefault(); showFieldErrors(f, {}); const no = digits(f.acc.value); if (no.length === 10) show(no); else showFieldErrors(f, { acc: 'Nomor rekening harus 10 digit' }); };
    f.acc.focus();
  }

  async function auditLog(panel) {
    panel.innerHTML = '<section class="card"><div class="sk" style="height:200px"></div></section>';
    try {
      const { data } = await api('/api/admin/audit-logs?size=50');
      panel.innerHTML = `<section class="card">
        <div class="card-head"><h2>Jejak audit <span class="light">50 terbaru</span></h2></div>
        <div class="table-wrap"><table class="ledger">
          <caption class="sr-only">Jejak audit</caption>
          <thead><tr><th scope="col">Waktu</th><th scope="col">Kejadian</th><th scope="col">Target</th><th scope="col" class="hide-sm">Detail</th><th scope="col" class="hide-sm">IP</th></tr></thead>
          <tbody>${data.items.map((l) => `<tr>
            <td class="date">${esc(fmtDate(l.createdAt))}</td>
            <td><span class="tag">${esc(ACTION_LABEL[l.action] || l.action)}</span></td>
            <td>${esc(l.target || '-')}</td>
            <td class="hide-sm muted">${esc(l.details || '')}</td>
            <td class="hide-sm muted">${esc(l.ipAddress || '')}</td></tr>`).join('')}</tbody>
        </table></div></section>`;
    } catch (err) { panel.innerHTML = `<div class="notice">${esc(err.message)}</div>`; }
  }

  render();
})();
