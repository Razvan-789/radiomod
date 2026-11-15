(() => {
  'use strict';
  const base = new URL('./', document.currentScript.src);
  const panel = document.createElement('section');
  panel.id = 'radio-map-panel';
  panel.innerHTML = `
    <div class="radio-map-title">Radio transmitters <button id="radio-map-collapse" type="button" aria-label="Collapse radio panel">−</button></div>
    <div id="radio-map-body">
      <div class="radio-map-row"><label>Country <select id="radio-country"><option value="">All countries</option></select></label>
      <label>Mode <select id="radio-mode"><option value="">All modes</option><option>FM</option><option>DAB</option><option>DAB+</option><option>DRM</option></select></label></div>
      <div class="radio-map-row"><label>Frequency MHz <input id="radio-frequency" inputmode="decimal" placeholder="e.g. 101.7"></label>
      <label>PI / SID <input id="radio-code" placeholder="Code"></label></div>
      <div class="radio-map-row"><label>Min ERP kW <input id="radio-power" type="number" min="0" step="0.001" placeholder="0"></label>
      <label>Station <input id="radio-name" placeholder="Search name"></label></div>
      <div class="radio-map-summary" id="radio-count">Select a transmitter on the map</div>
      <div class="radio-map-network" id="radio-network" hidden>
        <span id="radio-network-label"></span>
        <button type="button" data-network-mode="FM">FM coverage</button>
        <button type="button" data-network-mode="DAB">DAB coverage</button>
        <button type="button" id="radio-network-clear" aria-label="Clear network coverage">×</button>
      </div>
      <div class="radio-map-head"><span>Freq</span><span>Station</span><span>PI / SID</span><span>ERP kW</span><span>Network</span><span>Details</span></div>
      <div id="radio-rows" role="list"></div>
      <div class="radio-map-hint">Click a transmitter icon to view its stations. Select an ERP for one transmitter or Network for all transmitters with the same PI/SID.</div>
    </div>`;
  const dialog = document.createElement('div');
  dialog.id = 'radio-dab-dialog';
  dialog.hidden = true;
  dialog.innerHTML = `<div class="radio-dab-window" role="dialog" aria-modal="true" aria-labelledby="radio-dab-title">
    <div class="radio-dab-title"><strong id="radio-dab-title"></strong><button id="radio-dab-close" type="button" aria-label="Close DAB services">×</button></div>
    <div class="radio-dab-subtitle" id="radio-dab-subtitle"></div>
    <div class="radio-dab-scroll"><table><thead><tr><th>Status</th><th>Program label</th><th>PTY</th><th>Coding</th><th>Bitrate</th><th>Protection</th><th>SVCID</th><th>Remarks</th><th>Play</th></tr></thead>
    <tbody id="radio-dab-services"></tbody></table></div></div>`;
  (document.body || document.documentElement).append(panel, dialog);
  const field = id => panel.querySelector('#' + id);
  const fields = ['radio-country', 'radio-mode', 'radio-frequency', 'radio-code', 'radio-power', 'radio-name'];
  let worlds = [], chosen = null, selectedCoverage = '', selectedNetwork = null, visibleIds = new Set();
  let displayedRows = [], dialogRows = [];
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[c]));
  const MHz = khz => (khz / 1000).toFixed(3);
  const kW = watts => (watts / 1000).toFixed(3);
  const isDab = p => p.type === 'DAB' || p.type === 'DAB+';
  const sites = () => worlds.flatMap(w => w.sites);
  const radioIcons = () => [...document.querySelectorAll('img[src*="radio_tx_"], img[data-radio-key]')];
  function currentWorlds() {
    const world = new URLSearchParams(location.search).get('world');
    return world ? worlds.filter(w => w.dimension === world || w.dimension.split(':').pop() === world) : worlds;
  }
  function applyMarkerVisibility() {
    for (const img of radioIcons()) {
      const key = img.dataset.radioKey || (img.getAttribute('src') || '').match(/radio_tx_(?!sel_)[a-z0-9_]+(?=\.png|$)/i)?.[0];
      if (!key) continue;
      img.dataset.radioKey = key;
      const show = visibleIds.has(key);
      if (img.style.display !== (show ? '' : 'none')) img.style.display = show ? '' : 'none';
      const site = sites().find(s => s.icon === key);
      if (!site) continue;
      if (chosen === site.id && show && !img.dataset.radioSelected) {
        img.dataset.radioOriginalSrc = img.getAttribute('src');
        img.setAttribute('src', new URL(`tx-${site.power < 500 ? 'lo' : site.power < 5000 ? 'med' : 'hi'}-sel.png`, base));
        img.dataset.radioSelected = '1';
      } else if (chosen !== site.id && img.dataset.radioSelected) {
        img.setAttribute('src', img.dataset.radioOriginalSrc);
        delete img.dataset.radioSelected;
      }
    }
  }
  function networkLabels() {
    if (!selectedNetwork) return new Set();
    const labels = new Set();
    for (const world of currentWorlds()) for (const site of world.sites) for (const p of site.programmes) {
      if (p.code?.toUpperCase() === selectedNetwork.code && p.coverage &&
          (selectedNetwork.mode === 'DAB' ? isDab(p) : p.type === 'FM')) labels.add(p.coverage);
    }
    return labels;
  }
  function syncCoverage() {
    // Squaremap owns the Leaflet overlay controls; activate matching layers from our panel.
    const wanted = selectedNetwork ? networkLabels() : new Set(selectedCoverage ? [selectedCoverage] : []);
    for (const label of document.querySelectorAll('.leaflet-control-layers-overlays label')) {
      const name = label.textContent.trim();
      if (!name.startsWith('Radio coverage ')) continue;
      label.style.display = 'none';
      const input = label.querySelector('input[type="checkbox"]');
      if (input && input.checked !== wanted.has(name)) input.click();
    }
  }
  function renderNetwork() {
    const controls = field('radio-network');
    controls.hidden = !selectedNetwork;
    if (!selectedNetwork) return;
    field('radio-network-label').textContent = `Network ${selectedNetwork.code}`;
    const programmes = currentWorlds().flatMap(w => w.sites.flatMap(s => s.programmes));
    for (const button of controls.querySelectorAll('[data-network-mode]')) {
      const mode = button.dataset.networkMode;
      button.classList.toggle('active', selectedNetwork.mode === mode);
      button.disabled = !programmes.some(p => p.code?.toUpperCase() === selectedNetwork.code &&
        (mode === 'DAB' ? isDab(p) : p.type === 'FM'));
    }
  }
  function render() {
    const country = field('radio-country').value, mode = field('radio-mode').value;
    const frequency = Number.parseFloat(field('radio-frequency').value);
    const code = field('radio-code').value.trim().toLowerCase();
    const power = Number.parseFloat(field('radio-power').value);
    const name = field('radio-name').value.trim().toLowerCase();
    const matched = [];
    visibleIds = new Set();
    for (const world of currentWorlds()) for (const site of world.sites) {
      const rows = site.programmes.filter(p =>
        (!country || p.country === country) && (!mode || p.type === mode) &&
        (!Number.isFinite(frequency) || Math.abs(p.frequency / 1000 - frequency) < .0005) &&
        (!code || p.code.toLowerCase().includes(code)) &&
        (!Number.isFinite(power) || p.power / 1000 >= power) &&
        (!name || p.name.toLowerCase().includes(name)));
      if (rows.length) { matched.push({world, site, rows}); visibleIds.add(site.icon); }
    }
    const selected = matched.find(item => item.site.id === chosen);
    const count = field('radio-count');
    const list = field('radio-rows');
    displayedRows = selected?.rows || [];
    if (!chosen) {
      count.textContent = `${matched.length} locations match · select a transmitter on the map`;
      list.innerHTML = '';
    } else if (!selected) {
      count.textContent = 'Selected transmitter has no stations matching these filters';
      list.innerHTML = '';
    } else {
      const {world, site, rows} = selected;
      count.textContent = `${site.name || 'Unnamed transmitter site'} · ${world.dimension} · ${site.x}, ${site.y}, ${site.z} · ${rows.length} stations`;
      list.innerHTML = rows.map((p, i) => `<div class="radio-map-station${isDab(p) ? ' radio-map-dab' : ''}" role="listitem">
        <button class="radio-map-filter" type="button" data-filter="frequency" data-row="${i}" title="Filter transmitters to ${MHz(p.frequency)} MHz">${MHz(p.frequency)}</button>
        <span class="radio-map-programme">${p.logo ? `<img class="radio-map-logo" src="${escape(p.logo)}" alt="">` : ''}<span>${escape(p.name)} <small>${escape(p.type)}</small></span></span>
        <button class="radio-map-filter" type="button" data-filter="code" data-row="${i}" title="Filter transmitters to this PI/SID">${escape(p.code || '—')}</button>
        ${p.coverage ? `<button class="radio-map-erp${selectedCoverage === p.coverage && !selectedNetwork ? ' active' : ''}" type="button" data-coverage="${escape(p.coverage)}" aria-label="Show coverage for ${escape(p.name)}" aria-pressed="${selectedCoverage === p.coverage && !selectedNetwork}">${kW(p.power)}</button>` : `<span>${kW(p.power)}</span>`}
        ${p.coverage && p.code ? `<button class="radio-map-action${selectedNetwork?.code === p.code.toUpperCase() ? ' active' : ''}" type="button" data-network="${i}" title="Show this PI/SID network coverage">Network</button>` : '<span>—</span>'}
        ${isDab(p) ? `<button class="radio-map-action" type="button" data-info="${i}" title="Open DAB ensemble services">Services</button>` : '<span>—</span>'}
      </div>`).join('');
    }
    renderNetwork();
    applyMarkerVisibility();
    syncCoverage();
  }
  function openDabServices(p) {
    const selected = sites().find(s => s.id === chosen);
    if (!selected) return;
    dialogRows = selected.programmes.filter(service => isDab(service) &&
      service.coverage === p.coverage && service.mux === p.mux);
    dialog.querySelector('#radio-dab-title').textContent = `${p.type} ${p.mux || 'ensemble'}`;
    dialog.querySelector('#radio-dab-subtitle').textContent =
      `${MHz(p.frequency)} MHz · ${dialogRows.length} services · ${p.country}`;
    dialog.querySelector('#radio-dab-services').innerHTML = dialogRows.map((service, i) => `<tr>
      <td>${service.hasAudio ? 'On air' : 'Off air'}</td>
      <td><span class="radio-dab-programme">${service.logo ? `<img src="${escape(service.logo)}" alt="">` : ''}${escape(service.name)}</span></td>
      <td>${escape(service.pty || '—')}</td><td>${escape(service.coding || service.type)}</td>
      <td>${Number(service.bitrate) || '—'}${service.bitrate ? ' kbps' : ''}</td>
      <td>${escape(service.protection || '—')}</td><td>${escape(service.code)}</td>
      <td>${escape(service.remarks || '—')}</td>
      <td><button type="button" data-play="${i}" ${service.stream ? '' : 'disabled'} aria-label="Open ${escape(service.name)} stream in a new tab">▶</button></td>
    </tr>`).join('');
    dialog.hidden = false;
    dialog.querySelector('#radio-dab-close').focus();
  }
  function closeDabServices() { dialog.hidden = true; dialogRows = []; }
  // Capture icon clicks before Squaremap opens its own marker popup.
  document.addEventListener('click', event => {
    const img = event.target.closest?.('img');
    if (!img) return;
    const key = img.dataset.radioKey || (img.getAttribute('src') || '').match(/radio_tx_(?!sel_)[a-z0-9_]+(?=\.png|$)/i)?.[0];
    const site = key && sites().find(s => s.icon === key);
    if (!site) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    chosen = site.id;
    selectedCoverage = '';
    selectedNetwork = null;
    render();
  }, true);
  field('radio-rows').addEventListener('click', event => {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.dataset.filter) {
      const p = displayedRows[Number(button.dataset.row)];
      if (!p) return;
      field(button.dataset.filter === 'frequency' ? 'radio-frequency' : 'radio-code').value =
        button.dataset.filter === 'frequency' ? MHz(p.frequency) : p.code;
      render();
    } else if (button.dataset.coverage) {
      selectedNetwork = null;
      selectedCoverage = selectedCoverage === button.dataset.coverage ? '' : button.dataset.coverage;
      render();
    } else if (button.dataset.network != null) {
      const p = displayedRows[Number(button.dataset.network)];
      if (!p?.code) return;
      const mode = isDab(p) ? 'DAB' : 'FM';
      selectedNetwork = selectedNetwork?.code === p.code.toUpperCase() && selectedNetwork.mode === mode
        ? null : {code: p.code.toUpperCase(), mode};
      selectedCoverage = '';
      render();
    } else if (button.dataset.info != null) {
      const p = displayedRows[Number(button.dataset.info)];
      if (p) openDabServices(p);
    }
  });
  field('radio-network').addEventListener('click', event => {
    const mode = event.target.closest('button[data-network-mode]')?.dataset.networkMode;
    if (mode && selectedNetwork) { selectedNetwork.mode = mode; render(); }
    if (event.target.closest('#radio-network-clear')) { selectedNetwork = null; render(); }
  });
  dialog.addEventListener('click', event => {
    if (event.target === dialog || event.target.closest('#radio-dab-close')) { closeDabServices(); return; }
    const play = event.target.closest('button[data-play]');
    if (!play) return;
    const url = dialogRows[Number(play.dataset.play)]?.stream;
    try {
      const link = new URL(url);
      if (link.protocol === 'http:' || link.protocol === 'https:') window.open(link.href, '_blank', 'noopener,noreferrer');
    } catch (_) { /* Uploaded audio has no public browser URL. */ }
  });
  document.addEventListener('keydown', event => { if (event.key === 'Escape' && !dialog.hidden) closeDabServices(); });
  field('radio-map-collapse').addEventListener('click', () => {
    const closed = panel.classList.toggle('radio-map-closed');
    field('radio-map-collapse').textContent = closed ? '+' : '−';
  });
  for (const id of fields) field(id).addEventListener('input', render);
  async function refresh() {
    try {
      const response = await fetch(new URL('data.json?ts=' + Date.now(), base), {cache: 'no-store'});
      if (!response.ok) throw new Error(response.status);
      worlds = (await response.json()).worlds || [];
      if (chosen && !sites().some(s => s.id === chosen)) { chosen = null; selectedCoverage = ''; }
      const current = field('radio-country').value;
      const countries = [...new Set(sites().flatMap(s => s.programmes.map(p => p.country)))].sort();
      field('radio-country').innerHTML = '<option value="">All countries</option>' + countries.map(c => `<option value="${escape(c)}">${escape(c)}</option>`).join('');
      field('radio-country').value = current;
      render();
    } catch (_) { field('radio-count').textContent = 'Radio data is not available yet'; }
  }
  new MutationObserver(() => { applyMarkerVisibility(); syncCoverage(); }).observe(document.getElementById('map') || document.body,
    {childList: true, subtree: true});
  refresh();
  setInterval(refresh, 20000);
  window.addEventListener('popstate', render);
})();
