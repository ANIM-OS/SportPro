// US-15/US-16 con el SDK CLIENTE: finalizar el partido (endedAt/endedBy), eventos después de finalizar,
// "Cuenta como gol" (countsAsGoal) en el catálogo y en los eventos, lecturas por rol que usa Estadísticas
// y que collectionGroup('events') sigue cerrado. Replica CallupRepository.finishMatch,
// FirestoreMatchEventStore (add/correct/void) y MatchStatsRepository.
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Requiere el emulador arrancado con firebase.local.json. Sale con código 1 si algo falla.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, collectionGroup, query, where, setDoc, getDocs, getDocsFromServer,
  getDocFromServer, updateDoc, onSnapshot, writeBatch, serverTimestamp, deleteField, Timestamp } = require('firebase/firestore');

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
const guardian = await user('tutora', ['PADRE / TUTOR']);
const otherGuardian = await user('tutora-no-vinculada', ['PADRE / TUTOR']);
const admin = await user('admin', []);
const outsider = await user('ajena', ['JUGADOR']);
for (const c of [coach, otherCoach]) await seed(`coachApprovals/${c.uid}`, { approved: true });
await seed(`admins/${admin.uid}`, { since: new Date() });
const team = `eq-${run}`, team2 = `eq2-${run}`;
await seed(`teams/${team}`, { createdBy: coach.uid, name: 'Halcones', type: 'EQUIPO', categories: ['SUB-12'] });
await seed(`teams/${team2}`, { createdBy: pending.uid, name: 'Pendiente', type: 'EQUIPO', categories: ['SUB-12'] });
const names = { [p1.uid]: 'Paula', [p2.uid]: 'Bea', [p3.uid]: 'Retirada', [notCalled.uid]: 'No convocada' };
for (const p of [p1, p2, p3, notCalled]) await seed(`teamMemberships/${team}_${p.uid}`, { teamId: team, userId: p.uid, playerName: names[p.uid], category: 'SUB-12' });
await seed(`guardianLinks/${p1.uid}_${guardian.uid}`, { playerId: p1.uid, guardianId: guardian.uid, inviteId: 'x', status: 'ACTIVE' });

// Catálogo: generales (gol, penal con resultado) y propios (uno con "Cuenta como gol", otro sin).
const type = (name, extra = {}) => ({ name, description: '', requiredCore: [], extraKeys: [], extraFields: [], requiredExtra: [], active: true, base: false, order: 1000, ...extra });
await seed('eventCatalog/gol', type('Gol', { base: true, order: 10 }));
await seed('eventCatalog/penal', type('Penal', { base: true, order: 30, extraKeys: ['resultado'],
  extraFields: [{ key: 'resultado', label: 'Resultado', kind: 'choice', options: ['Gol', 'Fallado', 'Atajado'], required: false }] }));
await seed('eventCatalog/falta', type('Falta', { base: true, order: 20 }));
await seed(`teams/${team}/eventTypes/eq_olimpico`, { kind: 'CUSTOM', countsAsGoal: true, ...type('Gol olímpico') });
await seed(`teams/${team}/eventTypes/eq_palo`, { kind: 'CUSTOM', ...type('Tiro al palo') });

// Partidos sembrados: en juego (A: se finaliza; B: sigue en juego), en 3 min, de hace 30 h y uno de hace 8 días ya finalizado.
const now = Date.now();
const kickoffs = { a: now - 3600e3, b: now - 1800e3, soon: now + 180e3, old: now - 30 * 3600e3, veryOld: now - 8 * 24 * 3600e3 };
const ids = {};
for (const [key, kickoff] of Object.entries(kickoffs)) {
  ids[key] = `fx-${key}-${run}`;
  await seed(`matchFixtures/${ids[key]}`, { teamId: team, ownerId: coach.uid, category: 'SUB-12', opponent: 'Rival FC',
    kickoff: new Date(kickoff), responseDeadline: new Date(kickoff - 60e3), updatedAt: new Date(),
    ...(key === 'veryOld' ? { endedAt: new Date(kickoff + 7200e3), endedBy: coach.uid } : {}) });
  for (const p of [p1, p2]) await seed(`matchCallupPlayers/${ids[key]}_${p.uid}`, { matchId: ids[key], teamId: team, ownerId: coach.uid, playerId: p.uid, selected: true });
  await seed(`matchCallupPlayers/${ids[key]}_${p3.uid}`, { matchId: ids[key], teamId: team, ownerId: coach.uid, playerId: p3.uid, selected: false });
}
const pendingMatch = `fx-pend-${run}`;
await seed(`matchFixtures/${pendingMatch}`, { teamId: team2, ownerId: pending.uid, category: 'SUB-12', opponent: 'R', kickoff: new Date(now - 3600e3), responseDeadline: new Date(now - 7200e3), updatedAt: new Date() });
// Evento antiguo del partido de hace 8 días (para "corregir tras 7 días").
await seed(`matchFixtures/${ids.veryOld}/events/old-ev`, { typeId: 'gol', typeName: 'Gol', team: 'OWN', extras: {}, extraNames: {}, teamId: team,
  category: 'SUB-12', voided: false, version: 1, createdBy: coach.uid, createdAt: new Date(kickoffs.veryOld + 60e3),
  clientCreatedAt: new Date(kickoffs.veryOld + 60e3), updatedBy: coach.uid, updatedAt: new Date(kickoffs.veryOld + 60e3) });

let count = 0, failures = 0;
const out = (label, ok, detail = '') => { count++; if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${count}. ${label}${detail ? ': ' + detail : ''}`); };
const code = (p) => p.then(() => 'OK', (e) => e.code ?? e.message ?? String(e));
const expectOk = async (label, p) => { const c = await code(p); out(label, c === 'OK', c); };
const expectDenied = async (label, p) => { const c = await code(p); out(label, c === 'permission-denied', c); };
const once = (ref) => new Promise((resolve) => { const stop = onSnapshot(ref, (s) => { stop(); resolve(s.docs ? `${s.size} docs` : s.exists() ? 'existe' : 'no existe'); }, (e) => resolve(e.code)); });
const check = async (label, ref, want) => { const r = await once(ref); out(`${label} → ${want}`, r === want, r); };
const readAll = (ref) => getDocsFromServer(ref).then((s) => `${s.size} docs`, (e) => e.code);

// ---------- Réplica de la app ----------
const F = (u, id) => doc(u.db, 'matchFixtures', id);
const E = (u, match, id) => doc(u.db, 'matchFixtures', match, 'events', id);
const EVENTS = (u, match) => collection(u.db, 'matchFixtures', match, 'events');
const R = (u, match, id, v) => doc(u.db, 'matchFixtures', match, 'events', id, 'revisions', String(v));
let seq = 0;
const newId = () => `ev-${run}-${++seq}`;
function editable(e) {
  const s = { typeId: e.typeId, typeName: e.typeName, extras: e.extras ?? {}, extraNames: e.extraNames ?? {}, voided: e.voided ?? false };
  for (const k of ['minute', 'team', 'playerId', 'playerName', 'notes']) if (e[k] !== undefined && e[k] !== null && e[k] !== '') s[k] = e[k];
  return s;
}
// FirestoreMatchEventStore.add: countsAsGoal solo si es true.
function add(u, match, e) {
  const id = e.id ?? newId();
  const b = writeBatch(u.db);
  b.set(E(u, match, id), { ...editable(e), teamId: team, category: 'SUB-12', version: 1, createdBy: u.uid, createdAt: serverTimestamp(),
    clientCreatedAt: Timestamp.now(), updatedBy: u.uid, updatedAt: serverTimestamp(), ...(e.countsAsGoal !== undefined ? { countsAsGoal: e.countsAsGoal } : {}) });
  b.set(R(u, match, id, 1), { action: 'CREATE', previous: {}, by: u.uid, at: serverTimestamp() });
  return b.commit().then(() => id);
}
async function current(u, match, id) { return (await getDocFromServer(E(u, match, id))).data(); }
// FirestoreMatchEventStore.correct: countsAsGoal true o se borra.
function correct(u, match, id, before, after, goal = after.countsAsGoal === true) {
  const next = editable(after);
  const removed = Object.fromEntries(['minute', 'team', 'playerId', 'playerName', 'notes'].filter((k) => !(k in next)).map((k) => [k, deleteField()]));
  const version = before.version + 1;
  const b = writeBatch(u.db);
  b.update(E(u, match, id), { ...next, ...removed, countsAsGoal: goal ? true : deleteField(), version, updatedBy: u.uid, updatedAt: serverTimestamp() });
  b.set(R(u, match, id, version), { action: 'CORRECTION', previous: editable(before), by: u.uid, at: serverTimestamp() });
  return b.commit();
}
function voidEvent(u, match, id, before) {
  const version = before.version + 1;
  const b = writeBatch(u.db);
  b.update(E(u, match, id), { voided: true, voidedBy: u.uid, voidedAt: serverTimestamp(), version, updatedBy: u.uid, updatedAt: serverTimestamp() });
  b.set(R(u, match, id, version), { action: 'VOID', previous: editable(before), by: u.uid, at: serverTimestamp() });
  return b.commit();
}
// CallupRepository.finishMatch
const finish = (u, id, extra = {}) => updateDoc(F(u, id), { endedAt: serverTimestamp(), endedBy: u.uid, updatedAt: serverTimestamp(), ...extra });
const goal = (extra = {}) => ({ typeId: 'gol', typeName: 'Gol', minute: 10, team: 'OWN', playerId: p1.uid, playerName: 'Paula', ...extra });

// ---------- 1. Finalizar el partido ----------
console.log('\n--- 1. Finalizar partido (US-15 decisión 1) ---');
const a = ids.a;
const beforeFinishId = await add(coach, a, goal());
const toVoidId = await add(coach, a, goal({ minute: 20 }));
out('antes de finalizar, la dueña registra eventos', !!beforeFinishId && !!toVoidId);
await expectDenied('jugadora convocada no puede finalizar', finish(p1, a));
await expectDenied('tutora no puede finalizar', finish(guardian, a));
await expectDenied('admin no puede finalizar (solo lectura)', finish(admin, a));
await expectDenied('otra entrenadora aprobada no puede finalizar un partido ajeno', finish(otherCoach, a));
await expectDenied('entrenadora NO aprobada no puede finalizar su propio partido', finish(pending, pendingMatch));
await expectDenied('antes del inicio no se puede finalizar', finish(coach, ids.soon));
await expectDenied('pasadas 24 h del inicio no se puede finalizar', finish(coach, ids.old));
await expectDenied('no se pueden cambiar otros campos a la vez (opponent)', finish(coach, a, { opponent: 'Otro' }));
await expectDenied('endedBy debe ser quien finaliza', finish(coach, a, { endedBy: p1.uid }));
await expectDenied('endedAt debe ser la hora del servidor (no la del celular)', finish(coach, a, { endedAt: Timestamp.now() }));
await expectDenied('sin updatedAt del servidor tampoco', finish(coach, a, { updatedAt: Timestamp.now() }));
// Tiempo real: la jugadora escucha el partido y recibe endedAt sin recargar (US-15 #1).
const seen = [];
const stop = onSnapshot(F(p1, a), (s) => seen.push(s.get('endedAt') ? 'finalizado' : 'en juego'), () => seen.push('error'));
await sleep(400);
await expectOk('la dueña aprobada finaliza el partido en juego', finish(coach, a));
await sleep(800); stop();
out('la jugadora recibe el fin del partido en tiempo real', seen[0] === 'en juego' && seen.at(-1) === 'finalizado', seen.join('→'));
const fin = (await getDocFromServer(F(coach, a))).data();
out('endedAt y endedBy guardados', fin.endedAt instanceof Timestamp && fin.endedBy === coach.uid);
await expectDenied('no se puede finalizar dos veces', finish(coach, a));
await expectDenied('no se puede "des-finalizar" (borrar endedAt)', updateDoc(F(coach, a), { endedAt: deleteField(), endedBy: deleteField(), updatedAt: serverTimestamp() }));
await expectDenied('tras finalizar, la edición normal del partido sigue cerrada', updateDoc(F(coach, a), { opponent: 'Nuevo', updatedAt: serverTimestamp() }));

// ---------- 2. Eventos después de finalizar ----------
console.log('\n--- 2. Eventos tras endedAt (decisión 4) ---');
await expectDenied('tras finalizar no se registran eventos nuevos', add(coach, a, goal({ minute: 80 })));
const e1 = await current(coach, a, beforeFinishId);
await expectOk('tras finalizar se corrige un evento (dentro de 7 días)', correct(coach, a, beforeFinishId, e1, goal({ minute: 11, playerId: p2.uid, playerName: 'Bea' })));
const e2 = await current(coach, a, toVoidId);
await expectOk('tras finalizar se anula un evento (dentro de 7 días)', voidEvent(coach, a, toVoidId, e2));
const old = await current(coach, ids.veryOld, 'old-ev');
await expectDenied('partido finalizado hace más de 7 días: corregir falla', correct(coach, ids.veryOld, 'old-ev', old, { typeId: 'gol', typeName: 'Gol', team: 'OPPONENT' }));
await expectDenied('partido finalizado hace más de 7 días: anular falla', voidEvent(coach, ids.veryOld, 'old-ev', old));
await expectOk('en otro partido en juego (sin endedAt) se sigue registrando', add(coach, ids.b, goal()));

// ---------- 3. Cuenta como gol ----------
console.log('\n--- 3. "Cuenta como gol" (decisión 2) ---');
const b = ids.b;
const olim = { typeId: 'eq_olimpico', typeName: 'Gol olímpico', team: 'OWN', minute: 30 };
const olimId = await add(coach, b, { ...olim, countsAsGoal: true }).catch((e) => e.code);
out('tipo propio con la casilla → evento con countsAsGoal: true', typeof olimId === 'string' && olimId.startsWith('ev-'), String(olimId));
await expectDenied('tipo propio con la casilla pero sin countsAsGoal en el evento', add(coach, b, olim));
await expectDenied('countsAsGoal: false no se guarda (solo true o ausente)', add(coach, b, { ...olim, countsAsGoal: false }));
await expectDenied('tipo general (gol) con countsAsGoal', add(coach, b, goal({ countsAsGoal: true })));
await expectDenied('tipo propio SIN la casilla no admite countsAsGoal', add(coach, b, { typeId: 'eq_palo', typeName: 'Tiro al palo', team: 'OWN', countsAsGoal: true }));
await expectOk('tipo propio sin la casilla y sin countsAsGoal', add(coach, b, { typeId: 'eq_palo', typeName: 'Tiro al palo', team: 'OWN' }));
await expectOk('penal con resultado "Gol" (cuenta en el marcador por la app)', add(coach, b, { typeId: 'penal', typeName: 'Penal', team: 'OPPONENT', extras: { resultado: 'Gol' } }));
const ol = await current(coach, b, olimId);
await expectDenied('corregir a un tipo sin la casilla manteniendo countsAsGoal', correct(coach, b, olimId, ol, { typeId: 'eq_palo', typeName: 'Tiro al palo', team: 'OWN' }, true));
await expectOk('corregir a un tipo sin la casilla borrando countsAsGoal', correct(coach, b, olimId, ol, { typeId: 'eq_palo', typeName: 'Tiro al palo', team: 'OWN' }));
const ol2 = await current(coach, b, olimId);
out('…el evento corregido ya no tiene countsAsGoal', !('countsAsGoal' in ol2) && ol2.typeId === 'eq_palo');
await expectDenied('anular no puede tocar countsAsGoal', (() => { const bb = writeBatch(coach.db);
  bb.update(E(coach, b, olimId), { voided: true, voidedBy: coach.uid, voidedAt: serverTimestamp(), version: ol2.version + 1, updatedBy: coach.uid, updatedAt: serverTimestamp(), countsAsGoal: true });
  bb.set(R(coach, b, olimId, ol2.version + 1), { action: 'VOID', previous: editable(ol2), by: coach.uid, at: serverTimestamp() }); return bb.commit(); })());
const customDoc = (extra) => ({ kind: 'CUSTOM', name: 'Gol de chilena', description: '', requiredCore: [], extraKeys: [], extraFields: [], requiredExtra: [],
  active: true, order: 1000, updatedBy: coach.uid, updatedAt: serverTimestamp(), ...extra });
await expectOk('catálogo: la dueña guarda un tipo propio con "Cuenta como gol"', setDoc(doc(coach.db, 'teams', team, 'eventTypes', 'eq_chilena'), customDoc({ countsAsGoal: true })));
await expectOk('catálogo: tipo propio con la casilla desmarcada (false)', setDoc(doc(coach.db, 'teams', team, 'eventTypes', 'eq_chilena'), customDoc({ countsAsGoal: false })));
await expectDenied('catálogo: countsAsGoal debe ser booleano', setDoc(doc(coach.db, 'teams', team, 'eventTypes', 'eq_chilena'), customDoc({ countsAsGoal: 'si' })));
await expectDenied('catálogo general: no admite countsAsGoal', setDoc(doc(admin.db, 'eventCatalog', `nuevo_${run}`), { name: 'Nuevo', description: '',
  requiredCore: [], extraKeys: [], extraFields: [], requiredExtra: [], active: true, base: false, order: 1000, updatedBy: admin.uid, updatedAt: serverTimestamp(), countsAsGoal: true }));
await expectDenied('catálogo: un OVERRIDE no admite countsAsGoal', setDoc(doc(coach.db, 'teams', team, 'eventTypes', 'falta'),
  { kind: 'OVERRIDE', active: true, updatedBy: coach.uid, updatedAt: serverTimestamp(), countsAsGoal: true }));

// ---------- 4. Lecturas por rol (Estadísticas, decisión 5) ----------
console.log('\n--- 4. Lecturas por rol ---');
await check('dueña: eventos del partido', EVENTS(coach, b), '4 docs');
await check('jugadora convocada: eventos', EVENTS(p1, b), '4 docs');
await check('jugadora con convocatoria retirada: eventos', EVENTS(p3, b), 'permission-denied');
await check('miembro NO convocada: eventos', EVENTS(notCalled, b), 'permission-denied');
await check('tutora sin acceso guardado: eventos', EVENTS(guardian, b), 'permission-denied');
await expectOk('tutora vinculada guarda su acceso (MatchStatsRepository.loadGuardian)', setDoc(doc(guardian.db, 'matchLineups', b, 'guardians', guardian.uid), { playerId: p1.uid, updatedAt: serverTimestamp() }));
await check('tutora con acceso: eventos', EVENTS(guardian, b), '4 docs');
await expectDenied('tutora NO vinculada no puede guardar acceso', setDoc(doc(otherGuardian.db, 'matchLineups', b, 'guardians', otherGuardian.uid), { playerId: p1.uid, updatedAt: serverTimestamp() }));
await check('tutora no vinculada: eventos', EVENTS(otherGuardian, b), 'permission-denied');
await check('admin: eventos', EVENTS(admin, b), '4 docs');
await check('usuaria ajena: eventos', EVENTS(outsider, b), 'permission-denied');
await check('dueña: el partido (endedAt incluido)', F(coach, a), 'existe');
await check('jugadora convocada: el partido', F(p1, a), 'existe');
await check('admin: el partido', F(admin, a), 'existe');
await check('usuaria ajena: el partido', F(outsider, a), 'permission-denied');
// Consultas del histórico (MatchStatsRepository).
out('dueña: partidos del equipo (where teamId)', (await readAll(query(collection(coach.db, 'matchFixtures'), where('teamId', '==', team)))) === '5 docs');
out('dueña: convocatoria del partido (where ownerId + matchId)', (await readAll(query(collection(coach.db, 'matchCallupPlayers'), where('ownerId', '==', coach.uid), where('matchId', '==', b)))) === '3 docs');
out('dueña: plantilla para los nombres (where teamId)', (await readAll(query(collection(coach.db, 'teamMemberships'), where('teamId', '==', team)))) === '4 docs');
out('jugadora: sus convocatorias (where playerId)', (await readAll(query(collection(p1.db, 'matchCallupPlayers'), where('playerId', '==', p1.uid)))) === '5 docs');
out('tutora: convocatorias de su jugadora (where playerId)', (await readAll(query(collection(guardian.db, 'matchCallupPlayers'), where('playerId', '==', p1.uid)))) === '5 docs');
out('admin: convocatoria del partido (where matchId)', (await readAll(query(collection(admin.db, 'matchCallupPlayers'), where('matchId', '==', b)))) === '3 docs');
const peers = await readAll(query(collection(p1.db, 'matchCallupPlayers'), where('matchId', '==', b)));
out('jugadora: NO lista la convocatoria de sus compañeras', peers === 'permission-denied', peers);
for (const u of [coach, p1, admin]) {
  const r = await readAll(collection(u.db, 'matchLineups', b, 'players'));
  out(`${u.name}: alineación del partido (titular/suplente)`, r === '0 docs', r);
}
// collectionGroup('events') sigue cerrado para todos (no se amplió eventReader).
for (const u of [coach, p1, guardian, admin]) {
  const r = await getDocs(collectionGroup(u.db, 'events')).then((s) => `${s.size} docs`, (e) => e.code);
  out(`${u.name}: collectionGroup('events') → permission-denied`, r === 'permission-denied', r);
}

console.log(failures === 0 ? `\nUS-15/16 status & stats client flow verified in demo-sportpro only: ${count} de ${count} OK.` : `\n${failures} de ${count} comprobaciones fallidas.`);
process.exit(failures === 0 ? 0 : 1);
