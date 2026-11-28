// Parte 3 (US-13/14) con el SDK CLIENTE: eventos en matchFixtures/{m}/events/{id} y su historial en
// revisions/{version}. Replica FirestoreMatchEventStore (batch evento + revisión, listeners):
// quién lee y escribe, tipos del catálogo del equipo, obligatorios, Sale ≠ Entra, convocados, plazos
// (desde el inicio hasta +24 h; corregir/anular hasta +7 días), historial con valor anterior,
// nombres, tiempo real, sin conexión y la cascada al eliminar el equipo (D2).
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Requiere el emulador arrancado con firebase.local.json. Sale con código 1 si algo falla.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, setDoc, getDoc, getDocs, getDocFromServer, updateDoc, deleteDoc,
  onSnapshot, writeBatch, serverTimestamp, deleteField, disableNetwork, enableNetwork, Timestamp } = require('firebase/firestore');

const project = 'demo-sportpro';
const root = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents`;
const authUrl = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const password = 'SportProLocal2026!';
const run = Date.now().toString(36);
const J = { 'Content-Type': 'application/json' };
// "Bearer owner" se salta las reglas: solo para preparar datos (partidos pasados, catálogo).
const raw = async (url, method = 'GET', body) => (await fetch(url, { method, headers: { ...J, Authorization: 'Bearer owner' }, ...(body ? { body: JSON.stringify(body) } : {}) })).json();
const val = (v) => v instanceof Date ? { timestampValue: v.toISOString() } : Array.isArray(v) ? { arrayValue: { values: v.map(val) } }
  : typeof v === 'boolean' ? { booleanValue: v } : typeof v === 'number' ? { integerValue: String(v) }
  : typeof v === 'object' && v !== null ? { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, val(x)])) } } : { stringValue: v };
const seed = (path, data) => raw(`${root}/${path}`, 'PATCH', { fields: Object.fromEntries(Object.entries(data).filter(([, v]) => v !== undefined).map(([k, v]) => [k, val(v)])) });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function user(name, roles, verified = true) {
  const email = `${name}-${run}@sportpro.test`;
  const r = await fetch(`${authUrl}/accounts:signUp?key=local`, { method: 'POST', headers: J, body: JSON.stringify({ email, password, returnSecureToken: true }) }).then((x) => x.json());
  if (verified) await fetch(`${authUrl}/accounts:update`, { method: 'POST', headers: { ...J, Authorization: 'Bearer owner' }, body: JSON.stringify({ localId: r.localId, emailVerified: true }) });
  await seed(`users/${r.localId}`, { roles });
  const app = initializeApp({ projectId: project, apiKey: 'local' }, `${name}-${run}`);
  const auth = getAuth(app); connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  const db = getFirestore(app); connectFirestoreEmulator(db, '127.0.0.1', 8080);
  await signInWithEmailAndPassword(auth, email, password);
  return { uid: r.localId, email, name, db };
}

// ---------- Datos ----------
const coach = await user('entrenadora', ['ENTRENADOR']);
const pending = await user('entrenadora-pendiente', ['ENTRENADOR']);
const otherCoach = await user('otra-entrenadora', ['ENTRENADOR']);
const p1 = await user('paula', ['JUGADOR']);
const p2 = await user('bea', ['JUGADOR']);
const p3 = await user('retirada', ['JUGADOR']);
const notCalled = await user('no-convocada', ['JUGADOR']);
const otherCat = await user('otra-categoria', ['JUGADOR']);
const guardian = await user('tutora', ['PADRE / TUTOR']);
const otherGuardian = await user('tutora-no-vinculada', ['PADRE / TUTOR']);
const admin = await user('admin', []);
for (const c of [coach, otherCoach]) await seed(`coachApprovals/${c.uid}`, { approved: true });
await seed(`admins/${admin.uid}`, { since: new Date() });
const team = `eq-${run}`, team2 = `eq2-${run}`;
await seed(`teams/${team}`, { createdBy: coach.uid, name: 'Equipo Valido', type: 'EQUIPO', categories: ['SUB-12', 'SUB-14'] });
await seed(`teams/${team2}`, { createdBy: pending.uid, name: 'Equipo Pendiente', type: 'EQUIPO', categories: ['SUB-12'] });
const names = { [p1.uid]: 'Paula', [p2.uid]: 'Bea', [p3.uid]: 'Retirada', [notCalled.uid]: 'No convocada' };
for (const p of [p1, p2, p3, notCalled]) await seed(`teamMemberships/${team}_${p.uid}`, { teamId: team, userId: p.uid, playerName: names[p.uid], category: 'SUB-12' });
await seed(`teamMemberships/${team}_${otherCat.uid}`, { teamId: team, userId: otherCat.uid, playerName: 'Otra', category: 'SUB-14' });
await seed(`guardianLinks/${p1.uid}_${guardian.uid}`, { playerId: p1.uid, guardianId: guardian.uid, inviteId: 'x', status: 'ACTIVE' });

// Catálogo: generales (uno inactivo, otro desactivado en el equipo) y propios del equipo.
const type = (name, extra = {}) => ({ name, description: '', requiredCore: [], extraKeys: [], extraFields: [], requiredExtra: [], active: true, base: false, order: 1000, ...extra });
const catalog = {
  gol: type('Gol', { base: true, order: 10 }),
  tarjeta: type('Tarjeta', { base: true, extraKeys: ['color'], requiredExtra: ['color'], extraFields: [{ key: 'color', label: 'Color', kind: 'choice', options: ['Amarilla', 'Roja'], required: true }] }),
  cambio: type('Cambio', { base: true, extraKeys: ['sale', 'entra'], extraFields: [{ key: 'sale', label: 'Sale', kind: 'player' }, { key: 'entra', label: 'Entra', kind: 'player' }] }),
  saque_lateral: type('Saque lateral', { base: true }),
  fuera_de_juego: type('Fuera de juego', { base: true, active: false }),
};
for (const [id, data] of Object.entries(catalog)) await seed(`eventCatalog/${id}`, data);
await seed(`teams/${team}/eventTypes/saque_lateral`, { kind: 'OVERRIDE', active: false, updatedBy: coach.uid });
await seed(`teams/${team}/eventTypes/eq_penal_ejecutado`, { kind: 'CUSTOM', ...type('Penal ejecutado', { requiredCore: ['minute', 'playerId'] }) });
await seed(`teams/${team}/eventTypes/eq_inactivo`, { kind: 'CUSTOM', ...type('Inactivo', { active: false }) });

// Partidos (sembrados porque algunos ya empezaron): en juego, en 3 min, de hace 30 h y de hace 8 días.
const now = Date.now();
const fixtures = { live: now - 3600e3, soon: now + 180e3, old: now - 30 * 3600e3, veryOld: now - 8 * 24 * 3600e3 };
const ids = {};
for (const [key, kickoff] of Object.entries(fixtures)) {
  ids[key] = `fx-${key}-${run}`;
  await seed(`matchFixtures/${ids[key]}`, { teamId: team, ownerId: coach.uid, category: 'SUB-12', opponent: 'Rival FC',
    kickoff: new Date(kickoff), responseDeadline: new Date(kickoff - 60e3), updatedAt: new Date() });
  for (const p of [p1, p2]) await seed(`matchCallupPlayers/${ids[key]}_${p.uid}`, { matchId: ids[key], teamId: team, ownerId: coach.uid, playerId: p.uid, selected: true });
  await seed(`matchCallupPlayers/${ids[key]}_${p3.uid}`, { matchId: ids[key], teamId: team, ownerId: coach.uid, playerId: p3.uid, selected: false });
}
const m = ids.live;
const pendingMatch = `fx-pend-${run}`;
await seed(`matchFixtures/${pendingMatch}`, { teamId: team2, ownerId: pending.uid, category: 'SUB-12', opponent: 'R', kickoff: new Date(now - 3600e3), responseDeadline: new Date(now - 7200e3), updatedAt: new Date() });

let count = 0, failures = 0;
const out = (label, ok, detail = '') => { count++; if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${count}. ${label}${detail ? ': ' + detail : ''}`); };
const code = (p) => p.then(() => 'OK', (e) => e.code ?? e.message ?? String(e));
const expectOk = async (label, p) => { const c = await code(p); out(label, c === 'OK', c); };
const expectDenied = async (label, p) => { const c = await code(p); out(label, c === 'permission-denied', c); };
const once = (ref) => new Promise((resolve) => { const stop = onSnapshot(ref, (s) => { stop(); resolve(s.docs ? `${s.size} docs` : s.exists() ? 'existe' : 'no existe'); }, (e) => resolve(e.code)); });
const check = async (label, ref, want) => { const r = await once(ref); out(`${label} → ${want}`, r === want, r); };

// ---------- Réplica de FirestoreMatchEventStore ----------
const E = (u, match, id) => doc(u.db, 'matchFixtures', match, 'events', id);
const EVENTS = (u, match) => collection(u.db, 'matchFixtures', match, 'events');
const R = (u, match, id, v) => doc(u.db, 'matchFixtures', match, 'events', id, 'revisions', String(v));
let seq = 0;
const newId = () => `ev-${run}-${++seq}`;
// MatchEvents.editableSnapshot: los opcionales vacíos no se guardan.
function editable(e) {
  const s = { typeId: e.typeId, typeName: e.typeName, extras: e.extras ?? {}, extraNames: e.extraNames ?? {}, voided: e.voided ?? false };
  for (const k of ['minute', 'team', 'playerId', 'playerName', 'notes']) if (e[k] !== undefined && e[k] !== null && e[k] !== '') s[k] = e[k];
  return s;
}
function add(u, match, e, { revision = true, extra = {}, version = 1 } = {}) {
  const id = e.id ?? newId();
  const b = writeBatch(u.db);
  b.set(E(u, match, id), { ...editable(e), teamId: team, category: 'SUB-12', version, createdBy: u.uid, createdAt: serverTimestamp(),
    clientCreatedAt: Timestamp.now(), updatedBy: u.uid, updatedAt: serverTimestamp(), ...extra });
  if (revision) b.set(R(u, match, id, 1), { action: 'CREATE', previous: {}, by: u.uid, at: serverTimestamp() });
  return b.commit().then(() => id);
}
async function current(u, match, id) { const d = await getDocFromServer(E(u, match, id)); return d.data(); }
function correct(u, match, id, before, after, { version = before.version + 1, previous = editable(before), revision = true } = {}) {
  const next = editable(after);
  const removed = Object.fromEntries(['minute', 'team', 'playerId', 'playerName', 'notes'].filter((k) => !(k in next)).map((k) => [k, deleteField()]));
  const b = writeBatch(u.db);
  b.update(E(u, match, id), { ...next, ...removed, version, updatedBy: u.uid, updatedAt: serverTimestamp() });
  if (revision) b.set(R(u, match, id, version), { action: 'CORRECTION', previous, by: u.uid, at: serverTimestamp() });
  return b.commit();
}
function voidEvent(u, match, id, before) {
  const version = before.version + 1;
  const b = writeBatch(u.db);
  b.update(E(u, match, id), { voided: true, voidedBy: u.uid, voidedAt: serverTimestamp(), version, updatedBy: u.uid, updatedAt: serverTimestamp() });
  b.set(R(u, match, id, version), { action: 'VOID', previous: editable(before), by: u.uid, at: serverTimestamp() });
  return b.commit();
}
const goal = (extra = {}) => ({ typeId: 'gol', typeName: 'Gol', minute: 10, team: 'OWN', playerId: p1.uid, playerName: 'Paula', ...extra });
// MatchEvents.visibleNames (D3, como la alineación)
function visibleNames(role, events, roster = {}, childId = '', childName = '') {
  const snaps = {};
  for (const e of events) {
    if (e.playerId && e.playerName) snaps[e.playerId] = e.playerName;
    for (const [k, n] of Object.entries(e.extraNames ?? {})) if (e.extras?.[k]) snaps[e.extras[k]] = n;
  }
  if (role === 'OWNER') return [{ ...snaps, ...roster }, 'Jugador'];
  if (role === 'PLAYER') return [snaps, 'Jugador'];
  if (role === 'GUARDIAN') return [{ [childId]: snaps[childId] ?? childName }, 'Compañero'];
  return [{}, 'Jugador'];
}

// ---------- 1. Eventos AÚN INEXISTENTES ----------
console.log('--- 1. Listeners con eventos AÚN INEXISTENTES ---');
await expectOk('tutora vinculada guarda su acceso (el mismo de la Alineación, US-11)', setDoc(doc(guardian.db, 'matchLineups', m, 'guardians', guardian.uid), { playerId: p1.uid, updatedAt: serverTimestamp() }));
for (const [u, want] of [[coach, '0 docs'], [p1, '0 docs'], [p2, '0 docs'], [guardian, '0 docs'], [admin, '0 docs'],
  [notCalled, 'permission-denied'], [p3, 'permission-denied'], [otherCat, 'permission-denied'], [otherCoach, 'permission-denied'],
  [otherGuardian, 'permission-denied'], [pending, 'permission-denied']])
  await check(`events/ de ${u.name}`, EVENTS(u, m), want);
await check('dueña: un evento aún inexistente', E(coach, m, 'no-existe'), 'no existe');
await check('jugadora: revisions/ de un evento inexistente', collection(p1.db, 'matchFixtures', m, 'events', 'no-existe', 'revisions'), '0 docs');
await check('tutora SIN acceso guardado (otra tutora de la misma jugadora no vinculada)', EVENTS(otherGuardian, m), 'permission-denied');

// ---------- 2. Quién registra ----------
console.log('\n--- 2. Quién registra ---');
const seenP1 = [];
const stopP1 = onSnapshot(EVENTS(p1, m), (s) => seenP1.push(s.docs.map((d) => `${d.get('minute')}:${d.get('voided')}`).join('|')), (e) => seenP1.push(e.code));
let g1;
await expectOk('dueña aprobada registra un gol de Paula (batch evento + revisión CREATE)', add(coach, m, goal()).then((id) => { g1 = id; }));
for (const u of [otherCoach, p1, guardian, admin]) await expectDenied(`${u.name} no puede registrar`, add(u, m, goal()));
await expectDenied('entrenadora PENDIENTE no puede, ni en su propio partido', add(pending, pendingMatch, goal({ playerId: undefined, playerName: undefined, team: undefined })));

// ---------- 3. Validaciones ----------
console.log('\n--- 3. Validaciones ---');
const okCases = [
  ['tipo propio activo con sus obligatorios (minuto y jugador)', { typeId: 'eq_penal_ejecutado', typeName: 'Penal ejecutado', minute: 30, team: 'OWN', playerId: p2.uid, playerName: 'Bea' }],
  ['tarjeta con su color obligatorio', { typeId: 'tarjeta', typeName: 'Tarjeta', minute: 40, team: 'OPPONENT', extras: { color: 'Amarilla' } }],
  ['cambio con Sale y Entra distintos (con nombres copiados)', { typeId: 'cambio', typeName: 'Cambio', minute: 60, team: 'OWN', extras: { sale: p1.uid, entra: p2.uid }, extraNames: { sale: 'Paula', entra: 'Bea' } }],
  ['gol del rival sin jugador', { typeId: 'gol', typeName: 'Gol', minute: 70, team: 'OPPONENT' }],
];
let cambioId;
for (const [label, e] of okCases) await expectOk(`acepta: ${label}`, add(coach, m, e).then((id) => { if (e.typeId === 'cambio') cambioId = id; }));
const bad = [
  ['tipo general desactivado por el admin (fuera_de_juego)', () => add(coach, m, { typeId: 'fuera_de_juego', typeName: 'Fuera de juego' })],
  ['tipo general desactivado en el equipo (saque_lateral)', () => add(coach, m, { typeId: 'saque_lateral', typeName: 'Saque lateral' })],
  ['tipo propio inactivo', () => add(coach, m, { typeId: 'eq_inactivo', typeName: 'Inactivo' })],
  ['tipo inexistente', () => add(coach, m, { typeId: 'inventado', typeName: 'X' })],
  ['propio sin sus obligatorios (sin minuto ni jugador)', () => add(coach, m, { typeId: 'eq_penal_ejecutado', typeName: 'Penal ejecutado', team: 'OWN' })],
  ['tarjeta sin color (obligatorio)', () => add(coach, m, { typeId: 'tarjeta', typeName: 'Tarjeta', minute: 41 })],
  ['minuto 131', () => add(coach, m, goal({ minute: 131 }))],
  ['minuto decimal', () => add(coach, m, goal({ minute: 10.5 }))],
  ['equipo "LOCAL"', () => add(coach, m, goal({ team: 'LOCAL' }))],
  ['jugador con equipo RIVAL', () => add(coach, m, goal({ team: 'OPPONENT' }))],
  ['jugador no convocado', () => add(coach, m, goal({ playerId: notCalled.uid, playerName: 'No convocada' }))],
  ['jugador retirado de la convocatoria', () => add(coach, m, goal({ playerId: p3.uid, playerName: 'Retirada' }))],
  ['nombre sin jugador', () => add(coach, m, goal({ playerId: undefined }))],
  ['cambio con el MISMO jugador en Sale y Entra', () => add(coach, m, { typeId: 'cambio', typeName: 'Cambio', extras: { sale: p1.uid, entra: p1.uid } })],
  ['cambio con Sale no convocado', () => add(coach, m, { typeId: 'cambio', typeName: 'Cambio', extras: { sale: notCalled.uid, entra: p1.uid } })],
  ['sin la revisión CREATE', () => add(coach, m, goal(), { revision: false })],
  ['campo de más', () => add(coach, m, goal(), { extra: { color: 'rojo' } })],
  ['createdBy de otra persona', () => add(coach, m, goal(), { extra: { createdBy: p1.uid } })],
  ['versión 2 al crear', () => add(coach, m, goal(), { version: 2 })],
  ['creado ya anulado', () => add(coach, m, goal({ voided: true }))],
  ['otro equipo en teamId', () => add(coach, m, goal(), { extra: { teamId: team2 } })],
  ['ANTES del inicio (partido en 3 minutos)', () => add(coach, ids.soon, goal())],
  ['más de 24 h después del inicio', () => add(coach, ids.old, goal())],
];
for (const [label, fn] of bad) await expectDenied(`rechaza: ${label}`, fn());

// ---------- 4. Corregir, anular e historial ----------
console.log('\n--- 4. Corregir, anular e historial (US-14) ---');
let before = await current(coach, m, g1);
await expectDenied('corregir saltándose la versión', correct(coach, m, g1, before, goal({ minute: 12 }), { version: 3 }));
await expectDenied('corregir con un valor anterior falso', correct(coach, m, g1, before, goal({ minute: 12 }), { previous: { ...editable(before), minute: 99 } }));
await expectDenied('corregir sin revisión', correct(coach, m, g1, before, goal({ minute: 12 }), { revision: false }));
await expectDenied('otra entrenadora no puede corregir', correct(otherCoach, m, g1, before, goal({ minute: 12 })));
await expectDenied('corregir dejando a la jugadora retirada', correct(coach, m, g1, before, goal({ playerId: p3.uid, playerName: 'Retirada' })));
await expectOk('dueña corrige el minuto 10 → 12 (versión 2, valor anterior en revisions/2)', correct(coach, m, g1, before, goal({ minute: 12 })));
before = await current(coach, m, g1);
await expectOk('dueña corrige quitando el minuto (se borra el campo)', correct(coach, m, g1, before, goal({ minute: undefined })));
before = await current(coach, m, g1);
out('el evento queda en la versión 3 sin minuto', before.version === 3 && !('minute' in before), JSON.stringify({ v: before.version, minute: before.minute }));
await expectDenied('anular sin ser la dueña (admin)', voidEvent(admin, m, g1, before));
await expectOk('dueña ANULA (versión 4, voided, quién y cuándo)', voidEvent(coach, m, g1, before));
before = await current(coach, m, g1);
out('el anulado se conserva: voided, voidedBy y versión 4', before.voided === true && before.voidedBy === coach.uid && before.version === 4);
await expectDenied('corregir un evento anulado', correct(coach, m, g1, before, goal({ minute: 20 })));
await expectDenied('desanular', (async () => { const b = writeBatch(coach.db);
  b.update(E(coach, m, g1), { voided: false, version: 5, updatedBy: coach.uid, updatedAt: serverTimestamp() });
  b.set(R(coach, m, g1, 5), { action: 'CORRECTION', previous: editable(before), by: coach.uid, at: serverTimestamp() }); await b.commit(); })());
const revs = (await getDocs(collection(p1.db, 'matchFixtures', m, 'events', g1, 'revisions'))).docs.map((d) => `${d.id}:${d.get('action')}`).sort();
out('la jugadora lee el historial: CREATE, 2 CORRECTION y VOID', JSON.stringify(revs) === JSON.stringify(['1:CREATE', '2:CORRECTION', '3:CORRECTION', '4:VOID']), revs.join(','));
const rev2 = (await getDoc(R(p1, m, g1, 2))).data();
out('revisions/2 guarda el valor anterior (minuto 10) y quién', rev2.previous.minute === 10 && rev2.by === coach.uid);
await expectDenied('nadie modifica una revisión', setDoc(R(coach, m, g1, 2), { ...rev2, by: coach.uid }));
await check('no convocada: NO lee el historial', collection(notCalled.db, 'matchFixtures', m, 'events', g1, 'revisions'), 'permission-denied');
for (const u of [p1, admin, otherCoach]) await expectDenied(`${u.name} no puede borrar un evento`, deleteDoc(E(u, m, g1)));

// Plazos de corrección y tipo desactivado después de registrar.
const seedEvent = async (match, id, extra = {}) => {
  await seed(`matchFixtures/${match}/events/${id}`, { typeId: 'gol', typeName: 'Gol', minute: 10, team: 'OWN', playerId: p1.uid, playerName: 'Paula',
    extras: {}, extraNames: {}, teamId: team, category: 'SUB-12', voided: false, version: 1, createdBy: coach.uid, createdAt: new Date(fixtures.old),
    clientCreatedAt: new Date(fixtures.old), updatedBy: coach.uid, updatedAt: new Date(fixtures.old), ...extra });
  await seed(`matchFixtures/${match}/events/${id}/revisions/1`, { action: 'CREATE', previous: {}, by: coach.uid, at: new Date(fixtures.old) });
};
await seedEvent(ids.old, 'viejo'); await seedEvent(ids.veryOld, 'muy-viejo');
await seedEvent(ids.old, 'offside', { typeId: 'fuera_de_juego', typeName: 'Fuera de juego', playerId: undefined, playerName: undefined });
let o = await current(coach, ids.old, 'viejo');
await expectOk('corregir 30 h después (dentro de los 7 días)', correct(coach, ids.old, 'viejo', o, goal({ minute: 11 })));
o = await current(coach, ids.veryOld, 'muy-viejo');
await expectDenied('corregir 8 días después', correct(coach, ids.veryOld, 'muy-viejo', o, goal({ minute: 11 })));
await expectDenied('anular 8 días después', voidEvent(coach, ids.veryOld, 'muy-viejo', o));
o = await current(coach, ids.old, 'offside');
const offside = { typeId: 'fuera_de_juego', typeName: 'Fuera de juego', team: 'OWN' };
await expectOk('corregir un evento cuyo tipo YA está desactivado, sin cambiar el tipo', correct(coach, ids.old, 'offside', o, { ...offside, minute: 15 }));
o = await current(coach, ids.old, 'offside');
await expectDenied('cambiar un evento a un tipo desactivado (saque_lateral)', correct(coach, ids.old, 'offside', o, { typeId: 'saque_lateral', typeName: 'Saque lateral', minute: 15 }));

// ---------- 5. Nombres por rol ----------
console.log('\n--- 5. Nombres (D3: copia en el evento, mostrada por rol como en la Alineación) ---');
const evs = (await getDocs(EVENTS(p1, m))).docs.map((d) => d.data());
const cambio = (await getDoc(E(guardian, m, cambioId))).data();
out('jugadora y tutora leen la copia del nombre (playerName y extraNames)', evs.some((e) => e.playerName === 'Bea') && cambio.extraNames.sale === 'Paula' && cambio.extraNames.entra === 'Bea');
const [gNames, gDefault] = visibleNames('GUARDIAN', evs, {}, p1.uid, 'Paula');
out('tutora: ve a SU jugadora por nombre y a Bea como "Compañero"', gNames[p1.uid] === 'Paula' && (gNames[p2.uid] ?? gDefault) === 'Compañero');
const [aNames, aDefault] = visibleNames('ADMIN', evs);
out('admin: "Jugador" sin nombre (D2/D3)', Object.keys(aNames).length === 0 && aDefault === 'Jugador');
const [pNames] = visibleNames('PLAYER', evs);
out('jugadora: ve los nombres de las copias (Paula y Bea)', pNames[p1.uid] === 'Paula' && pNames[p2.uid] === 'Bea');

// ---------- 6. Tiempo real ----------
console.log('\n--- 6. Tiempo real ---');
await sleep(800);
out(`el listener de la jugadora recibió alta, correcciones y anulación (${seenP1.length} instantáneas)`,
  seenP1.length >= 4 && !seenP1.some((x) => x === 'permission-denied') && seenP1.at(-1).includes(':true'), seenP1.at(-1));
const live = (await getDocs(EVENTS(p1, m))).docs.map((d) => d.data());
const score = { own: live.filter((e) => !e.voided && e.typeId === 'gol' && e.team === 'OWN').length, opp: live.filter((e) => !e.voided && e.typeId === 'gol' && e.team === 'OPPONENT').length };
out('marcador calculado con eventos válidos: 0 - 1 (el gol propio está anulado)', score.own === 0 && score.opp === 1, `${score.own} - ${score.opp}`);
stopP1();

// ---------- 7. Sin conexión ----------
console.log('\n--- 7. Sin conexión ---');
const states = [];
const offlineId = newId();
const stopW = onSnapshot(E(coach, m, offlineId), { includeMetadataChanges: true }, (s) => states.push(s.exists() ? (s.metadata.hasPendingWrites ? 'pendiente' : 'confirmado') : 'no existe'));
await disableNetwork(coach.db);
const offlineWrite = add(coach, m, goal({ id: offlineId, minute: 80 }));
await sleep(800);
out('sin conexión, el evento aparece al instante como pendiente en el celular de la entrenadora', states.includes('pendiente'), states.join('→'));
await enableNetwork(coach.db);
await expectOk('al reconectar se envía y el servidor lo acepta', offlineWrite);
await sleep(800); stopW();
out('pasa a confirmado y la jugadora lo ve', states.at(-1) === 'confirmado' && (await getDocFromServer(E(p1, m, offlineId))).exists(), states.join('→'));
// Rechazo al sincronizar: el equipo desactiva el tipo mientras la entrenadora no tiene conexión.
await disableNetwork(coach.db);
const rejectedId = newId();
const rejected = add(coach, m, { id: rejectedId, typeId: 'eq_penal_ejecutado', typeName: 'Penal ejecutado', minute: 85, team: 'OWN', playerId: p1.uid, playerName: 'Paula' });
await seed(`teams/${team}/eventTypes/eq_penal_ejecutado`, { kind: 'CUSTOM', ...type('Penal ejecutado', { requiredCore: ['minute', 'playerId'], active: false }) });
await enableNetwork(coach.db);
const rc = await code(rejected);
out('si el tipo se desactivó mientras tanto, el servidor lo rechaza al sincronizar', rc === 'permission-denied', rc);
out('…y el evento rechazado no queda guardado', !(await getDocFromServer(E(p1, m, rejectedId))).exists());
await seed(`teams/${team}/eventTypes/eq_penal_ejecutado`, { kind: 'CUSTOM', ...type('Penal ejecutado', { requiredCore: ['minute', 'playerId'] }) });

// ---------- 8. Cascada al eliminar el equipo (D2) ----------
console.log('\n--- 8. Eliminar el equipo (US-03, D2) ---');
await expectDenied('otra entrenadora no puede borrar las revisiones', deleteDoc(R(otherCoach, m, cambioId, 1)));
await expectOk('la dueña borra eventos y revisiones del partido (FirestoreMatchEventStore.deleteMatchEvents)', (async () => {
  for (;;) {
    const s = await getDocs(EVENTS(coach, m));
    if (s.empty) break;
    const b = writeBatch(coach.db);
    for (const e of s.docs.slice(0, 5)) {
      for (const r of (await getDocs(collection(coach.db, 'matchFixtures', m, 'events', e.id, 'revisions'))).docs) b.delete(r.ref);
      b.delete(e.ref);
    }
    await b.commit();
  }
})());
const orphan = await raw(`${root}/matchFixtures/${m}/events`);
out('no quedan eventos huérfanos', !orphan.documents || orphan.documents.length === 0);
// Como MatchCleanup: convocatorias y después el partido.
for (const p of [p1, p2, p3]) await raw(`${root}/matchCallupPlayers/${m}_${p.uid}`, 'DELETE');
await raw(`${root}/matchFixtures/${m}`, 'DELETE');
await check('tras la cascada (sin convocatoria ni partido): la jugadora ya no lee events/', EVENTS(p1, m), 'permission-denied');

console.log(failures === 0 ? `\nParte 3 events client flow verified in demo-sportpro only: ${count} de ${count} OK.` : `\n${failures} de ${count} comprobaciones fallidas.`);
process.exit(failures === 0 ? 0 : 1);
