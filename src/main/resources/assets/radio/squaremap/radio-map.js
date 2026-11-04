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
      <div class="radio-map-head"><span>Freq</span><span>Station</span><span>PI / SID</span><span>ERP kW</span></div>
      <div id="radio-rows" role="list"></div>
      <div class="radio-map-hint" id="radio-map-hint">Click a transmitter icon to see its stations, then click an ERP value to show coverage.</div>
    </div>`;
  (document.body || document.documentElement).appendChild(panel);
  const field = id => panel.querySelector('#' + id);
  const fields = ['radio-country', 'radio-mode', 'radio-frequency', 'radio-code', 'radio-power', 'radio-name'];
  let worlds = [], chosen = null, selectedCoverage = '', visibleIds = new Set();
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[c]));
  const MHz = khz => (khz / 1000).toFixed(3);
  const kW = watts => (watts / 1000).toFixed(3);
  const radioIcons = () => [...document.querySelectorAll('img[src*="radio_tx_"], img[data-radio-key]')];
  const sites = () => worlds.flatMap(w => w.sites);
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
  function currentWorlds() {
    const world = new URLSearchParams(location.search).get('world');
    return world ? worlds.filter(w => w.dimension === world || w.dimension.split(':').pop() === world) : worlds;
  }
  function syncCoverage() {
    // Squaremap uses Leaflet's overlay controls. Keep radio coverage hidden in that menu;
    // the ERP buttons below are the controls for these layers.
    for (const label of document.querySelectorAll('.leaflet-control-layers-overlays label')) {
      const name = label.textContent.trim();
      if (!name.startsWith('Radio coverage ')) continue;
      label.style.display = 'none';
      const input = label.querySelector('input[type="checkbox"]');
      const desired = name === selectedCoverage;
      if (input && input.checked !== desired) input.click();
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
    if (!chosen) {
      count.textContent = `${matched.length} locations match · select a transmitter on the map`;
      list.innerHTML = '';
    } else if (!selected) {
      count.textContent = 'Selected transmitter has no stations matching these filters';
      list.innerHTML = '';
    } else {
      const {world, site, rows} = selected;
      count.textContent = `${world.dimension} · ${site.x}, ${site.y}, ${site.z} · ${rows.length} stations`;
      list.innerHTML = rows.map(p => `<div class="radio-map-station" role="listitem"><span>${MHz(p.frequency)}</span><span>${escape(p.name)} <small>${escape(p.type)}</small></span><span>${escape(p.code)}</span>${p.coverage ? `<button class="radio-map-erp${selectedCoverage === p.coverage ? ' active' : ''}" type="button" data-coverage="${escape(p.coverage)}" aria-label="Show coverage for ${escape(p.name)}" aria-pressed="${selectedCoverage === p.coverage}">${kW(p.power)}</button>` : `<span>${kW(p.power)}</span>`}</div>`).join('');
    }
    applyMarkerVisibility();
    syncCoverage();
  }
  // Capture the icon click before Squaremap's own popup handler runs.
  document.addEventListener('click', event => {
    const img = event.target.closest('img');
    if (!img) return;
    const key = img.dataset.radioKey || (img.getAttribute('src') || '').match(/radio_tx_(?!sel_)[a-z0-9_]+(?=\.png|$)/i)?.[0];
    const site = key && sites().find(s => s.icon === key);
    if (!site) return;
    chosen = site.id;
    selectedCoverage = '';
    render();
  }, true);
  field('radio-rows').addEventListener('click', event => {
    const button = event.target.closest('button[data-coverage]');
    if (!button) return;
    selectedCoverage = selectedCoverage === button.dataset.coverage ? '' : button.dataset.coverage;
    render();
  });
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
