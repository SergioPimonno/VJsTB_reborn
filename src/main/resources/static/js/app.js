'use strict';

const API = '/api';

const SIG_COLORS = ['var(--sig1)', 'var(--sig2)', 'var(--sig3)', 'var(--sig4)',
                     'var(--sig5)', 'var(--sig6)', 'var(--sig7)', 'var(--sig8)'];

const state = {
  projects: [],
  currentProjectId: null,
  currentProjectDetail: null,
  currentSceneId: null,
  currentScreenId: null,
  currentScreen: null,
  cabinetTypes: [],
  mode: 'power',
  activePhase: 1,
  editingCabinetTypeId: null,
  chainBuilding: false,
  activeChainCabIds: [],
  undoStack: [],       // снимки состояния активного экрана для «отменить»
  hoveredCabId: null,  // кабинет под курсором (для шортката Del)
};

const UNDO_LIMIT = 50;

// ── UNDO: снимки редактируемого состояния активного экрана ────────────
function snapshotScreen(scr) {
  return {
    name: scr.name,
    posXMm: scr.posXMm,
    posYMm: scr.posYMm,
    cabinets: scr.cabinets.map(c => ({ id: c.id, phase: c.phase, hidden: c.hidden })),
    powerChains: scr.powerChains.map(c => ({ phase: c.phase, cabinetInstanceIds: c.cabinetInstanceIds.slice() })),
    signalChains: scr.signalChains.map(c => ({ portNumber: c.portNumber, backup: c.backup, cabinetInstanceIds: c.cabinetInstanceIds.slice() })),
  };
}

/** Запомнить текущее состояние экрана перед изменением. */
function pushUndo() {
  if (!state.currentScreen) return;
  state.undoStack.push(snapshotScreen(state.currentScreen));
  if (state.undoStack.length > UNDO_LIMIT) state.undoStack.shift();
  updateUndoBtn();
}

function clearUndo() {
  state.undoStack = [];
  updateUndoBtn();
}

function updateUndoBtn() {
  const btn = document.getElementById('btn-undo');
  if (!btn) return;
  const n = state.undoStack.length;
  btn.disabled = n === 0 || !state.currentScreen;
  btn.textContent = n > 0 ? `↶ Отменить (${n})` : '↶ Отменить';
}

async function undo() {
  if (!state.currentScreen || state.undoStack.length === 0) return;
  const snap = state.undoStack.pop();
  try {
    state.currentScreen = await api('PUT', `/screens/${state.currentScreen.id}/restore`, snap);
    resetChainBuilding();
    populateScreenParams();
    renderCanvas();
    renderStats();
    renderChainList();
    state.currentProjectDetail = await api('GET', `/projects/${state.currentProjectId}`);
    renderScreenList();
    setStatus('Действие отменено');
  } catch (e) {
    // восстановление не удалось — вернём снимок в стек, чтобы не потерять
    state.undoStack.push(snap);
    alert('Не удалось отменить: ' + e.message);
  }
  updateUndoBtn();
}

// ── low-level API helper ──────────────────────────────────────────────
async function api(method, url, body) {
  const res = await fetch(API + url, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : {},
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    let message = res.statusText;
    try {
      const err = await res.json();
      if (err && err.message) message = err.message;
    } catch (e) { /* ignore */ }
    throw new Error(message);
  }
  if (res.status === 204) return null;
  return res.json();
}

function setStatus(text) {
  document.getElementById('toolbar-status').textContent = text;
}

// ── CABINET LIBRARY ───────────────────────────────────────────────────
async function loadCabinetTypes() {
  state.cabinetTypes = await api('GET', '/cabinet-types');
  renderLibList();
  renderCabTypeSelects();
}

function renderLibList() {
  const wrap = document.getElementById('lib-list');
  wrap.innerHTML = '';
  if (state.cabinetTypes.length === 0) {
    wrap.innerHTML = '<div class="hint-text">Библиотека пуста</div>';
    return;
  }
  for (const ct of state.cabinetTypes) {
    const card = document.createElement('div');
    card.className = 'list-card';
    card.innerHTML = `
      <div class="list-card-info">
        <div>${escapeHtml(ct.name)}</div>
        <div class="list-card-meta">${ct.widthMm}×${ct.heightMm}мм · ${ct.resolutionWidth}×${ct.resolutionHeight}px · ${ct.powerConsumptionW}Вт · ${ct.weightKg}кг</div>
      </div>
      <div class="list-card-btns">
        <button class="list-card-btn" data-action="edit">✎</button>
        <button class="list-card-btn" data-action="del">✕</button>
      </div>`;
    card.querySelector('[data-action="edit"]').onclick = (e) => { e.stopPropagation(); editCabinetType(ct); };
    card.querySelector('[data-action="del"]').onclick = (e) => { e.stopPropagation(); deleteCabinetType(ct.id); };
    wrap.appendChild(card);
  }
}

function renderCabTypeSelects() {
  for (const selId of ['new-screen-cabtype', 'scr-cabtype']) {
    const sel = document.getElementById(selId);
    const prev = sel.value;
    sel.innerHTML = state.cabinetTypes.map(ct => `<option value="${ct.id}">${escapeHtml(ct.name)}</option>`).join('');
    if (prev) sel.value = prev;
  }
}

function editCabinetType(ct) {
  state.editingCabinetTypeId = ct.id;
  document.getElementById('lib-name').value = ct.name;
  document.getElementById('lib-w').value = ct.widthMm;
  document.getElementById('lib-h').value = ct.heightMm;
  document.getElementById('lib-d').value = ct.depthMm ?? '';
  document.getElementById('lib-rw').value = ct.resolutionWidth;
  document.getElementById('lib-rh').value = ct.resolutionHeight;
  document.getElementById('lib-power').value = ct.powerConsumptionW;
  document.getElementById('lib-weight').value = ct.weightKg;
  document.getElementById('btn-cancel-edit-cabtype').style.display = 'block';
}

function resetCabTypeForm() {
  state.editingCabinetTypeId = null;
  document.getElementById('lib-name').value = '';
  document.getElementById('lib-w').value = 500;
  document.getElementById('lib-h').value = 500;
  document.getElementById('lib-d').value = '';
  document.getElementById('lib-rw').value = 128;
  document.getElementById('lib-rh').value = 128;
  document.getElementById('lib-power').value = 150;
  document.getElementById('lib-weight').value = 12;
  document.getElementById('btn-cancel-edit-cabtype').style.display = 'none';
}

async function saveCabinetType() {
  const payload = {
    name: document.getElementById('lib-name').value.trim(),
    widthMm: Number(document.getElementById('lib-w').value),
    heightMm: Number(document.getElementById('lib-h').value),
    depthMm: document.getElementById('lib-d').value ? Number(document.getElementById('lib-d').value) : null,
    resolutionWidth: Number(document.getElementById('lib-rw').value),
    resolutionHeight: Number(document.getElementById('lib-rh').value),
    powerConsumptionW: Number(document.getElementById('lib-power').value),
    weightKg: Number(document.getElementById('lib-weight').value),
  };
  if (!payload.name) { alert('Укажите название кабинета'); return; }
  try {
    if (state.editingCabinetTypeId) {
      await api('PUT', `/cabinet-types/${state.editingCabinetTypeId}`, payload);
    } else {
      await api('POST', '/cabinet-types', payload);
    }
    resetCabTypeForm();
    await loadCabinetTypes();
  } catch (e) {
    alert('Ошибка сохранения: ' + e.message);
  }
}

async function deleteCabinetType(id) {
  if (!confirm('Удалить кабинет из библиотеки?')) return;
  try {
    await api('DELETE', `/cabinet-types/${id}`);
    await loadCabinetTypes();
  } catch (e) {
    alert('Ошибка удаления: ' + e.message);
  }
}

async function exportLib() {
  const data = await api('GET', '/cabinet-types/export');
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'led-cabinet-library.json';
  a.click();
  URL.revokeObjectURL(a.href);
}

async function importLibFile(file) {
  const text = await file.text();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch (e) {
    alert('Некорректный JSON');
    return;
  }
  const list = Array.isArray(parsed) ? parsed : parsed.cabinetTypes;
  if (!Array.isArray(list)) { alert('Ожидался массив кабинетов'); return; }
  const cabinetTypes = list.map(ct => ({
    name: ct.name,
    widthMm: ct.widthMm,
    heightMm: ct.heightMm,
    depthMm: ct.depthMm ?? null,
    resolutionWidth: ct.resolutionWidth,
    resolutionHeight: ct.resolutionHeight,
    powerConsumptionW: ct.powerConsumptionW,
    weightKg: ct.weightKg,
  }));
  try {
    await api('POST', '/cabinet-types/import', { cabinetTypes });
    await loadCabinetTypes();
    alert(`Импортировано кабинетов: ${cabinetTypes.length}`);
  } catch (e) {
    alert('Ошибка импорта: ' + e.message);
  }
}

// ── PROJECTS ───────────────────────────────────────────────────────────
async function loadProjects() {
  state.projects = await api('GET', '/projects');
  renderProjectList();
}

function renderProjectList() {
  const wrap = document.getElementById('project-list');
  wrap.innerHTML = '';
  if (state.projects.length === 0) {
    wrap.innerHTML = '<div class="hint-text">Нет проектов</div>';
    return;
  }
  for (const p of state.projects) {
    const card = document.createElement('div');
    card.className = 'list-card' + (p.id === state.currentProjectId ? ' active' : '');
    card.innerHTML = `
      <div class="list-card-info">
        <div>${escapeHtml(p.name)}</div>
        <div class="list-card-meta">${p.sceneCount} сцен</div>
      </div>
      <div class="list-card-btns"><button class="list-card-btn" data-action="del">✕</button></div>`;
    card.onclick = () => selectProject(p.id);
    card.querySelector('[data-action="del"]').onclick = (e) => { e.stopPropagation(); deleteProject(p.id); };
    wrap.appendChild(card);
  }
}

async function addProject() {
  const input = document.getElementById('new-project-name');
  const name = input.value.trim();
  if (!name) return;
  await api('POST', '/projects', { name, description: null });
  input.value = '';
  await loadProjects();
}

async function deleteProject(id) {
  if (!confirm('Удалить проект вместе со всеми сценами и экранами?')) return;
  await api('DELETE', `/projects/${id}`);
  if (state.currentProjectId === id) {
    state.currentProjectId = null;
    state.currentProjectDetail = null;
    selectScene(null);
    document.getElementById('scenes-section').style.display = 'none';
  }
  await loadProjects();
}

async function selectProject(id) {
  state.currentProjectId = id;
  state.currentProjectDetail = await api('GET', `/projects/${id}`);
  renderProjectList();
  document.getElementById('scenes-section').style.display = 'block';
  renderSceneList();
  selectScreen(null);
  document.getElementById('screens-section').style.display = 'none';
  setStatus(`Проект «${state.currentProjectDetail.name}» — выберите сцену`);
}

// ── SCENES ─────────────────────────────────────────────────────────────
function renderSceneList() {
  const wrap = document.getElementById('scene-list');
  wrap.innerHTML = '';
  const scenes = state.currentProjectDetail.scenes;
  if (scenes.length === 0) {
    wrap.innerHTML = '<div class="hint-text">Нет сцен</div>';
    return;
  }
  for (const s of scenes) {
    const card = document.createElement('div');
    card.className = 'list-card' + (s.id === state.currentSceneId ? ' active' : '');
    card.innerHTML = `
      <div class="list-card-info">
        <div>${escapeHtml(s.name)}</div>
        <div class="list-card-meta">${s.screens.length} экранов</div>
      </div>
      <div class="list-card-btns"><button class="list-card-btn" data-action="del">✕</button></div>`;
    card.onclick = () => selectScene(s.id);
    card.querySelector('[data-action="del"]').onclick = (e) => { e.stopPropagation(); deleteScene(s.id); };
    wrap.appendChild(card);
  }
}

async function addScene() {
  const input = document.getElementById('new-scene-name');
  const name = input.value.trim();
  if (!name || !state.currentProjectId) return;
  await api('POST', `/projects/${state.currentProjectId}/scenes`, { name });
  input.value = '';
  state.currentProjectDetail = await api('GET', `/projects/${state.currentProjectId}`);
  renderSceneList();
}

async function deleteScene(id) {
  if (!confirm('Удалить сцену вместе со всеми экранами?')) return;
  await api('DELETE', `/scenes/${id}`);
  if (state.currentSceneId === id) selectScene(null);
  state.currentProjectDetail = await api('GET', `/projects/${state.currentProjectId}`);
  renderSceneList();
}

function selectScene(id) {
  state.currentSceneId = id;
  selectScreen(null);
  if (id == null) {
    document.getElementById('screens-section').style.display = 'none';
    return;
  }
  renderSceneList();
  document.getElementById('screens-section').style.display = 'block';
  renderScreenList();
  setStatus('Выберите экран или создайте новый');
}

// ── SCREENS ────────────────────────────────────────────────────────────
function currentSceneSummary() {
  return state.currentProjectDetail.scenes.find(s => s.id === state.currentSceneId);
}

function renderScreenList() {
  const wrap = document.getElementById('screen-list');
  wrap.innerHTML = '';
  const scene = currentSceneSummary();
  if (!scene || scene.screens.length === 0) {
    wrap.innerHTML = '<div class="hint-text">Нет экранов</div>';
    return;
  }
  for (const scr of scene.screens) {
    const card = document.createElement('div');
    card.className = 'list-card' + (scr.id === state.currentScreenId ? ' active' : '');
    card.innerHTML = `
      <div class="list-card-info">
        <div>${escapeHtml(scr.name)}</div>
        <div class="list-card-meta">${scr.cols}×${scr.rows} · ${escapeHtml(scr.cabinetTypeName)}</div>
      </div>`;
    card.onclick = () => selectScreen(scr.id);
    wrap.appendChild(card);
  }
}

async function addScreen() {
  if (!state.currentSceneId) return;
  if (state.cabinetTypes.length === 0) { alert('Сначала добавьте кабинет в библиотеку'); return; }
  const payload = {
    name: document.getElementById('new-screen-name').value.trim() || 'Экран',
    cabinetTypeId: Number(document.getElementById('new-screen-cabtype').value),
    cols: Number(document.getElementById('new-screen-cols').value),
    rows: Number(document.getElementById('new-screen-rows').value),
    posXMm: Number(document.getElementById('new-screen-x').value),
    posYMm: Number(document.getElementById('new-screen-y').value),
  };
  try {
    const created = await api('POST', `/scenes/${state.currentSceneId}/screens`, payload);
    document.getElementById('new-screen-name').value = '';
    state.currentProjectDetail = await api('GET', `/projects/${state.currentProjectId}`);
    renderScreenList();
    selectScreen(created.id);
  } catch (e) {
    alert('Ошибка создания экрана: ' + e.message);
  }
}

async function selectScreen(id) {
  resetChainBuilding();
  clearUndo(); // стек «отменить» относится к конкретному экрану
  state.hoveredCabId = null;
  state.currentScreenId = id;
  if (id == null) {
    state.currentScreen = null;
    document.getElementById('screen-params-section').style.display = 'none';
    document.getElementById('mode-section').style.display = 'none';
    document.getElementById('stats-section').style.display = 'none';
    renderCanvas();
    updateUndoBtn();
    return;
  }
  state.currentScreen = await api('GET', `/screens/${id}`);
  renderScreenList();
  document.getElementById('screen-params-section').style.display = 'block';
  document.getElementById('mode-section').style.display = 'block';
  document.getElementById('stats-section').style.display = 'block';
  populateScreenParams();
  renderCanvas();
  renderStats();
  renderChainList();
  updateUndoBtn();
  setStatus(`Экран «${state.currentScreen.name}»`);
}

function populateScreenParams() {
  const scr = state.currentScreen;
  document.getElementById('scr-name').value = scr.name;
  document.getElementById('scr-cabtype').value = scr.cabinetTypeId;
  document.getElementById('scr-cols').value = scr.cols;
  document.getElementById('scr-rows').value = scr.rows;
  document.getElementById('scr-x').value = scr.posXMm;
  document.getElementById('scr-y').value = scr.posYMm;
}

async function applyGrid() {
  const payload = {
    name: document.getElementById('scr-name').value.trim() || state.currentScreen.name,
    cabinetTypeId: Number(document.getElementById('scr-cabtype').value),
    cols: Number(document.getElementById('scr-cols').value),
    rows: Number(document.getElementById('scr-rows').value),
  };
  try {
    state.currentScreen = await api('PUT', `/screens/${state.currentScreen.id}`, payload);
    state.currentProjectDetail = await api('GET', `/projects/${state.currentProjectId}`);
    renderScreenList();
    resetChainBuilding();
    renderCanvas();
    renderStats();
    renderChainList();
    // сетка перестроена — id кабинетов изменились, прежние снимки недействительны
    clearUndo();
  } catch (e) {
    alert('Ошибка обновления экрана: ' + e.message);
  }
}

async function applyPos() {
  const payload = {
    posXMm: Number(document.getElementById('scr-x').value),
    posYMm: Number(document.getElementById('scr-y').value),
  };
  pushUndo();
  try {
    state.currentScreen = await api('PUT', `/screens/${state.currentScreen.id}/position`, payload);
    state.currentProjectDetail = await api('GET', `/projects/${state.currentProjectId}`);
    renderScreenList();
    updateUndoBtn();
  } catch (e) {
    state.undoStack.pop();
    updateUndoBtn();
    alert('Ошибка обновления позиции: ' + e.message);
  }
}

async function deleteScreenCurrent() {
  if (!confirm('Удалить экран?')) return;
  await api('DELETE', `/screens/${state.currentScreen.id}`);
  state.currentProjectDetail = await api('GET', `/projects/${state.currentProjectId}`);
  renderScreenList();
  selectScreen(null);
}

// ── MODE / CHAINS ─────────────────────────────────────────────────────
function setMode(mode) {
  resetChainBuilding();
  state.mode = mode;
  document.getElementById('tab-power').classList.toggle('active', mode === 'power');
  document.getElementById('tab-signal').classList.toggle('active', mode === 'signal');
  document.getElementById('power-controls').style.display = mode === 'power' ? 'block' : 'none';
  document.getElementById('signal-controls').style.display = mode === 'signal' ? 'block' : 'none';
  document.getElementById('toolbar-mode-badge').textContent = mode === 'power' ? '⚡ Питание' : '📡 Сигнал';
  renderCanvas();
  renderChainList();
}

function selectPhase(n) {
  state.activePhase = n;
  document.querySelectorAll('.phase-btn').forEach(b => b.classList.toggle('active', Number(b.dataset.phase) === n));
}

function resetChainBuilding() {
  state.chainBuilding = false;
  state.activeChainCabIds = [];
  document.getElementById('btn-start-chain').disabled = false;
  document.getElementById('btn-finish-chain').disabled = true;
  document.getElementById('btn-cancel-chain').disabled = true;
  document.getElementById('chain-hint').textContent = '';
}

function startChain() {
  if (!state.currentScreen) return;
  state.chainBuilding = true;
  state.activeChainCabIds = [];
  document.getElementById('btn-start-chain').disabled = true;
  document.getElementById('btn-finish-chain').disabled = false;
  document.getElementById('btn-cancel-chain').disabled = false;
  document.getElementById('chain-hint').textContent = 'Кликайте по кабинетам на схеме в порядке цепочки.';
  renderCanvas();
}

function cancelChain() {
  resetChainBuilding();
  renderCanvas();
}

async function finishChain() {
  if (state.activeChainCabIds.length === 0) { alert('Добавьте хотя бы один кабинет в цепочку'); return; }
  pushUndo();
  try {
    if (state.mode === 'power') {
      for (const cabId of state.activeChainCabIds) {
        await api('PATCH', `/screens/${state.currentScreen.id}/cabinets/${cabId}`, { phase: state.activePhase, hidden: null });
      }
      const fresh = await api('GET', `/screens/${state.currentScreen.id}`);
      const chains = fresh.powerChains.map(c => ({ phase: c.phase, cabinetInstanceIds: c.cabinetInstanceIds }));
      chains.push({ phase: state.activePhase, cabinetInstanceIds: state.activeChainCabIds.slice() });
      state.currentScreen = await api('PUT', `/screens/${state.currentScreen.id}/power-chains`, { chains });
    } else {
      const port = document.getElementById('sig-port').value ? Number(document.getElementById('sig-port').value) : null;
      const backup = document.getElementById('sig-backup').checked;
      const chains = state.currentScreen.signalChains.map(c => ({ portNumber: c.portNumber, backup: c.backup, cabinetInstanceIds: c.cabinetInstanceIds }));
      chains.push({ portNumber: port, backup, cabinetInstanceIds: state.activeChainCabIds.slice() });
      state.currentScreen = await api('PUT', `/screens/${state.currentScreen.id}/signal-chains`, { chains });
    }
    resetChainBuilding();
    renderCanvas();
    renderStats();
    renderChainList();
    updateUndoBtn();
  } catch (e) {
    state.undoStack.pop(); // изменение не применилось — снимок не нужен
    updateUndoBtn();
    alert('Ошибка сохранения цепочки: ' + e.message);
  }
}

async function deleteChain(chainId, isPower) {
  if (!confirm('Удалить цепочку?')) return;
  const scr = state.currentScreen;
  pushUndo();
  try {
    if (isPower) {
      const chains = scr.powerChains.filter(c => c.id !== chainId).map(c => ({ phase: c.phase, cabinetInstanceIds: c.cabinetInstanceIds }));
      state.currentScreen = await api('PUT', `/screens/${scr.id}/power-chains`, { chains });
    } else {
      const chains = scr.signalChains.filter(c => c.id !== chainId).map(c => ({ portNumber: c.portNumber, backup: c.backup, cabinetInstanceIds: c.cabinetInstanceIds }));
      state.currentScreen = await api('PUT', `/screens/${scr.id}/signal-chains`, { chains });
    }
    renderCanvas();
    renderStats();
    renderChainList();
    updateUndoBtn();
  } catch (e) {
    state.undoStack.pop();
    updateUndoBtn();
    alert('Ошибка удаления цепочки: ' + e.message);
  }
}

async function clearChains() {
  if (!confirm('Очистить все цепочки текущего режима на этом экране?')) return;
  const scr = state.currentScreen;
  pushUndo();
  try {
    if (state.mode === 'power') {
      state.currentScreen = await api('PUT', `/screens/${scr.id}/power-chains`, { chains: [] });
    } else {
      state.currentScreen = await api('PUT', `/screens/${scr.id}/signal-chains`, { chains: [] });
    }
    renderCanvas();
    renderStats();
    renderChainList();
    updateUndoBtn();
  } catch (e) {
    state.undoStack.pop();
    updateUndoBtn();
    alert('Ошибка очистки цепочек: ' + e.message);
  }
}

function renderChainList() {
  const wrap = document.getElementById('chain-list');
  wrap.innerHTML = '';
  if (!state.currentScreen) return;
  const chains = state.mode === 'power' ? state.currentScreen.powerChains : state.currentScreen.signalChains;
  if (chains.length === 0) {
    wrap.innerHTML = '<div class="hint-text">Цепочек ещё нет</div>';
    return;
  }
  chains.forEach((c, idx) => {
    const label = state.mode === 'power'
      ? `L${c.phase} · ${c.cabinetInstanceIds.length} каб.`
      : `${c.portNumber != null ? 'Порт ' + c.portNumber : 'Без порта'}${c.backup ? ' (резерв)' : ''} · ${c.cabinetInstanceIds.length} каб.`;
    const color = state.mode === 'power' ? phaseColor(c.phase) : SIG_COLORS[idx % SIG_COLORS.length];
    const card = document.createElement('div');
    card.className = 'list-card';
    card.innerHTML = `
      <span class="phase-dot" style="background:${color};"></span>
      <div class="list-card-info">${label}</div>
      <div class="list-card-btns"><button class="list-card-btn" data-action="del">✕</button></div>`;
    card.querySelector('[data-action="del"]').onclick = () => deleteChain(c.id, state.mode === 'power');
    wrap.appendChild(card);
  });
}

function phaseColor(phase) {
  return phase === 1 ? 'var(--phase1)' : phase === 2 ? 'var(--phase2)' : phase === 3 ? 'var(--phase3)' : 'var(--phase-none)';
}

// ── CANVAS / RENDER ───────────────────────────────────────────────────
function cabinetPxSize(ct) {
  const BASE = 90;
  const ratio = ct.widthMm / ct.heightMm;
  let w, h;
  if (ratio >= 1) { w = BASE; h = Math.round(BASE / ratio); } else { h = BASE; w = Math.round(BASE * ratio); }
  return { w: Math.max(w, 26), h: Math.max(h, 26) };
}

function onCabinetClick(cab) {
  if (!state.chainBuilding) return;
  if (state.activeChainCabIds.includes(cab.id)) return;
  state.activeChainCabIds.push(cab.id);
  renderCanvas();
}

async function toggleHideCabinet(cabId) {
  if (!state.currentScreen) return;
  const cab = state.currentScreen.cabinets.find(c => c.id === cabId);
  if (!cab) return;
  pushUndo();
  try {
    state.currentScreen = await api('PATCH', `/screens/${state.currentScreen.id}/cabinets/${cabId}`,
      { phase: null, hidden: !cab.hidden });
    renderCanvas();
    renderStats();
    updateUndoBtn();
  } catch (e) {
    state.undoStack.pop();
    updateUndoBtn();
    alert('Ошибка изменения кабинета: ' + e.message);
  }
}

function renderCanvas() {
  const grid = document.getElementById('grid');
  const svg = document.getElementById('connections-svg');
  grid.innerHTML = '';
  svg.innerHTML = '';

  const scr = state.currentScreen;
  if (!scr) {
    grid.innerHTML = '<div class="empty-hint">Выберите или создайте экран, чтобы увидеть схему.</div>';
    svg.setAttribute('width', 0);
    svg.setAttribute('height', 0);
    return;
  }

  const ct = state.cabinetTypes.find(c => c.id === scr.cabinetTypeId) || { widthMm: 500, heightMm: 500 };
  const { w: cw, h: ch } = cabinetPxSize(ct);
  const totalW = scr.cols * cw;
  const totalH = scr.rows * ch;
  grid.style.width = totalW + 'px';
  grid.style.height = totalH + 'px';
  svg.setAttribute('width', totalW);
  svg.setAttribute('height', totalH);

  const byId = new Map(scr.cabinets.map(c => [c.id, c]));

  for (const cab of scr.cabinets) {
    const el = document.createElement('div');
    el.className = 'cabinet' + (cab.hidden ? ' hidden-cab' : '') + (state.activeChainCabIds.includes(cab.id) ? ' in-active-chain' : '');
    el.style.left = (cab.colIndex * cw) + 'px';
    el.style.top = (cab.rowIndex * ch) + 'px';
    el.style.width = cw + 'px';
    el.style.height = ch + 'px';
    if (state.mode === 'power') {
      el.style.background = phaseColor(cab.phase);
    }
    el.textContent = `${cab.rowIndex},${cab.colIndex}`;
    el.onclick = () => onCabinetClick(cab);
    el.onmouseenter = () => { state.hoveredCabId = cab.id; };
    el.onmouseleave = () => { if (state.hoveredCabId === cab.id) state.hoveredCabId = null; };
    grid.appendChild(el);
  }

  function center(cabId) {
    const cab = byId.get(cabId);
    if (!cab) return null;
    return { x: cab.colIndex * cw + cw / 2, y: cab.rowIndex * ch + ch / 2 };
  }

  function drawChain(ids, color, dashed) {
    for (let i = 0; i < ids.length - 1; i++) {
      const p1 = center(ids[i]);
      const p2 = center(ids[i + 1]);
      if (!p1 || !p2) continue;
      const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
      line.setAttribute('x1', p1.x); line.setAttribute('y1', p1.y);
      line.setAttribute('x2', p2.x); line.setAttribute('y2', p2.y);
      line.setAttribute('stroke', color);
      line.setAttribute('stroke-width', 2.5);
      if (dashed) line.setAttribute('stroke-dasharray', '5,4');
      svg.appendChild(line);
    }
  }

  if (state.mode === 'power') {
    scr.powerChains.forEach(c => drawChain(c.cabinetInstanceIds, phaseColor(c.phase), false));
    if (state.chainBuilding) drawChain(state.activeChainCabIds, phaseColor(state.activePhase), true);
  } else {
    scr.signalChains.forEach((c, idx) => drawChain(c.cabinetInstanceIds, SIG_COLORS[idx % SIG_COLORS.length], false));
    if (state.chainBuilding) drawChain(state.activeChainCabIds, '#ffffff', true);
  }
}

function renderStats() {
  const scr = state.currentScreen;
  if (!scr) return;
  document.getElementById('stat-res').textContent = `${scr.resolutionWidthPx} × ${scr.resolutionHeightPx} px`;
  document.getElementById('stat-size').textContent = `${scr.physicalWidthMm} × ${scr.physicalHeightMm} мм`;
  document.getElementById('stat-count').textContent = scr.activeCabinetCount;
  document.getElementById('stat-power').textContent = `${scr.totalPowerW.toFixed(0)} Вт`;
  document.getElementById('stat-weight').textContent = `${scr.totalWeightKg.toFixed(1)} кг`;

  const counts = { 1: 0, 2: 0, 3: 0 };
  scr.cabinets.forEach(c => { if (c.phase >= 1 && c.phase <= 3 && !c.hidden) counts[c.phase]++; });
  const ct = state.cabinetTypes.find(c => c.id === scr.cabinetTypeId);
  const perCabW = ct ? ct.powerConsumptionW : 0;
  const wrap = document.getElementById('phase-stats');
  wrap.innerHTML = [1, 2, 3].map(p => `
    <div class="phase-row">
      <span class="phase-dot" style="background:${phaseColor(p)};"></span>
      L${p}: ${counts[p]} каб. · ${(counts[p] * perCabW).toFixed(0)} Вт
    </div>`).join('');
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

// ── WIRING ─────────────────────────────────────────────────────────────
function wireEvents() {
  document.getElementById('btn-add-project').onclick = addProject;
  document.getElementById('new-project-name').addEventListener('keydown', e => { if (e.key === 'Enter') addProject(); });

  document.getElementById('btn-add-scene').onclick = addScene;
  document.getElementById('new-scene-name').addEventListener('keydown', e => { if (e.key === 'Enter') addScene(); });

  document.getElementById('btn-add-screen').onclick = addScreen;

  document.getElementById('btn-apply-grid').onclick = applyGrid;
  document.getElementById('btn-apply-pos').onclick = applyPos;
  document.getElementById('btn-delete-screen').onclick = deleteScreenCurrent;

  document.getElementById('tab-power').onclick = () => setMode('power');
  document.getElementById('tab-signal').onclick = () => setMode('signal');
  document.querySelectorAll('.phase-btn').forEach(b => b.onclick = () => selectPhase(Number(b.dataset.phase)));

  document.getElementById('btn-start-chain').onclick = startChain;
  document.getElementById('btn-finish-chain').onclick = finishChain;
  document.getElementById('btn-cancel-chain').onclick = cancelChain;
  document.getElementById('btn-clear-chains').onclick = clearChains;

  document.getElementById('btn-save-cabtype').onclick = saveCabinetType;
  document.getElementById('btn-cancel-edit-cabtype').onclick = resetCabTypeForm;
  document.getElementById('btn-export-lib').onclick = exportLib;
  document.getElementById('import-lib-file').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) importLibFile(file);
    e.target.value = '';
  });

  document.getElementById('btn-undo').onclick = undo;
  wireShortcutsPopover();
  document.addEventListener('keydown', onGlobalKeydown);

  selectPhase(1);
  updateUndoBtn();
}

function wireShortcutsPopover() {
  const btn = document.getElementById('btn-shortcuts');
  const pop = document.getElementById('shortcuts-popover');
  btn.onclick = (e) => {
    e.stopPropagation();
    pop.style.display = pop.style.display === 'none' ? 'block' : 'none';
  };
  document.addEventListener('click', (e) => {
    if (pop.style.display !== 'none' && !pop.contains(e.target) && e.target !== btn) {
      pop.style.display = 'none';
    }
  });
}

function isTypingTarget(el) {
  const tag = (el.tagName || '').toLowerCase();
  return tag === 'input' || tag === 'textarea' || tag === 'select' || el.isContentEditable;
}

function onGlobalKeydown(e) {
  // Ctrl/Cmd+Z — отменить (работает даже из полей ввода)
  if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key.toLowerCase() === 'z') {
    e.preventDefault();
    undo();
    return;
  }
  if (isTypingTarget(e.target)) return;
  if (!state.currentScreen) return;

  switch (e.key.toLowerCase()) {
    case 'p': setMode('power'); break;
    case 's': setMode('signal'); break;
    case 'n': if (!state.chainBuilding) startChain(); break;
    case 'enter': if (state.chainBuilding) finishChain(); break;
    case 'escape': if (state.chainBuilding) cancelChain(); break;
    case '1': if (state.mode === 'power') selectPhase(1); break;
    case '2': if (state.mode === 'power') selectPhase(2); break;
    case '3': if (state.mode === 'power') selectPhase(3); break;
    case 'delete':
    case 'backspace':
      if (state.hoveredCabId != null && !state.chainBuilding) {
        e.preventDefault();
        toggleHideCabinet(state.hoveredCabId);
      }
      break;
    default: return;
  }
}

async function init() {
  wireEvents();
  await loadCabinetTypes();
  await loadProjects();
  renderCanvas();
}

document.addEventListener('DOMContentLoaded', init);
