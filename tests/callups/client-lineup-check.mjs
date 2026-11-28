// US-11 con el SDK CLIENTE (como la app): convocar -> crear alineación -> titulares -> suplentes
// -> cambiar posición -> cambiar formación -> retirar -> jugador/tutor leen -> inicio -> cancelar.
// Replica MatchLineupRepository (observeFormation, observePlayers, assign, remove, formation),
// CallupRepository.saveSelections y MatchCleanup.deleteMatch, incluidas las lecturas de
// documentos que AÚN NO EXISTEN (padre de la alineación, jugador, casilla, convocatoria).
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Requiere el emulador arrancado con firebase.local.json. Sale con código 1 si algo falla.
// - "[P1]…[P7]" = casos de las correcciones (resumen-us11-fix.md); "[observado]" = solo informa.
// - Tutor (P4b): lee la alineación tras crear matchLineups/{m}/guardians/{su uid} con su jugador.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, runTransaction, setDoc, getDoc, getDocs, query, where,
  onSnapshot, getDocsFromServer, writeBatch, deleteDoc, updateDoc, serverTimestamp, Timestamp } = require('firebase/firestore');

const project = 'demo-sportpro';
const root = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents`;
const authUrl = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const password = 'SportProLocal2026!';
const run = Date.now().toString(36);
const J = { 'Content-Type': 'application/json' };
const raw = async (url, method = 'GET', body) => (await fetch(url, { method, headers: { ...J, Authorization: 'Bearer owner' }, ...(body ? { body: JSON.stringify(body) } : {}) })).json();
const val = (v) => v instanceof Date ? { timestampValue: v.toISOString() } : Array.isArray(v) ? { arrayValue: { values: v.map(val) } }
  : typeof v === 'boolean' ? { booleanValue: v } : typeof v === 'number' ? { integerValue: String(v) }
  : typeof v === 'object' ? { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, val(x)])) } } : { stringValue: v };
const seed = (path, data) => raw(`${root}/${path}`, 'PATCH', { fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, val(v)])) });

async function account(name, roles, verified = true) {
  const email = `${name}-${run}@sportpro.test`;
  const r = await fetch(`${authUrl}/accounts:signUp?key=local`, { method: 'POST', headers: J, body: JSON.stringify({ email, password, returnSecureToken: true }) }).then((x) => x.json());
  if (verified) await fetch(`${authUrl}/accounts:update`, { method: 'POST', headers: { ...J, Authorization: 'Bearer owner' }, body: JSON.stringify({ localId: r.localId, emailVerified: true }) });
  await seed(`users/${r.localId}`, { roles });
  return { uid: r.localId, email, name };
}
async function user(name, roles, verified = true) {
  const a = await account(name, roles, verified);
  const app = initializeApp({ projectId: project, apiKey: 'local' }, `${name}-${run}`);
  const auth = getAuth(app); connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  const db = getFirestore(app); connectFirestoreEmulator(db, '127.0.0.1', 8080);
  await signInWithEmailAndPassword(auth, a.email, password);
  return { ...a, db };
}

// ---------- Datos ----------
const coach = await user('entrenadora', ['ENTRENADOR']);
const pendingCoach = await user('entrenadora-pendiente', ['ENTRENADOR']);
const otherCoach = await user('otra-entrenadora', ['ENTRENADOR']);
const guardian = await user('tutor', ['PADRE / TUTOR']);
const guardian3 = await user('tutor-de-j3', ['PADRE / TUTOR']);
const guardianNC = await user('tutor-de-no-convocado', ['PADRE / TUTOR']);
const otherGuardian = await user('tutor-no-vinculado', ['PADRE / TUTOR']);
const unverifiedGuardian = await user('tutor-sin-verificar', ['PADRE / TUTOR'], false);
const admin = await user('admin', []);
const P = [];
for (let i = 1; i <= 13; i++) P.push(await user(`j${i}`, ['JUGADOR']));
const unavailable = await user('no-disponible', ['JUGADOR']);
const notCalled = await user('no-convocado', ['JUGADOR']);
const otherCat = await user('otra-categoria', ['JUGADOR']);
for (const c of [coach, otherCoach]) await seed(`coachApprovals/${c.uid}`, { approved: true });
await seed(`admins/${admin.uid}`, { since: new Date() });
const team = `eq-${run}`;
await seed(`teams/${team}`, { createdBy: coach.uid, name: 'Equipo Valido', type: 'EQUIPO', categories: ['SUB-12', 'SUB-14'] });
const member = (p, category = 'SUB-12') => seed(`teamMemberships/${team}_${p.uid}`, { teamId: team, userId: p.uid, playerName: p.name, category });
for (const p of [...P, unavailable, notCalled]) await member(p);
await member(otherCat, 'SUB-14');
const link = (p, g, status = 'ACTIVE') => seed(`guardianLinks/${p.uid}_${g.uid}`, { playerId: p.uid, guardianId: g.uid, inviteId: 'x', status });
await link(P[0], guardian); await link(P[2], guardian3); await link(notCalled, guardianNC); await link(P[0], unverifiedGuardian);
// Alineación general (US-03) que no debe cambiar.
await seed(`teamLineups/${team}_SUB-12`, { teamId: team, category: 'SUB-12', formation: '3-5-2', positions: { P0: P[5].uid } });

let count = 0, failures = 0;
const out = (label, ok, detail = '') => { count++; if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${count}. ${label}${detail ? ': ' + detail : ''}`); };
const info = (label, detail) => console.log(`INFO [observado] ${label}: ${detail}`);
const code = (p) => p.then(() => 'OK', (e) => e.code ?? e.message ?? String(e));
const expectOk = async (label, p) => { const c = await code(p); out(label, c === 'OK', c); };
const expectDenied = async (label, p) => { const c = await code(p); out(label, c === 'permission-denied', c); };
const expectMsg = async (label, p, text) => { const c = await code(p); out(label, String(c).includes(text), c); };
const existsLabel = (p) => p.then((d) => (d.exists() ? 'existe' : 'no existe'), (e) => e.code);

// ---------- Réplicas de la app ----------
const selId = (p, m) => `${m}_${p.uid}`;
function saveSelections(db, matchRef, additions, withdrawals, category = 'SUB-12') {
  return runTransaction(db, async (tx) => {
    const enc = await tx.get(matchRef);
    const t = await tx.get(doc(db, 'teams', team));
    const current = new Map();
    for (const p of [...additions, ...withdrawals]) current.set(p.uid, await tx.get(doc(db, 'matchCallupPlayers', selId(p, matchRef.id))));
    const memberships = new Map();
    for (const p of additions) memberships.set(p.uid, await tx.get(doc(db, 'teamMemberships', `${team}_${p.uid}`)));
    const assignments = new Map();
    for (const p of withdrawals) assignments.set(p.uid, await tx.get(doc(db, 'matchLineups', matchRef.id, 'players', p.uid)));
    const responses = new Map();
    for (const p of [...withdrawals, ...additions.filter((a) => current.get(a.uid).exists())])
      responses.set(p.uid, await tx.get(doc(db, 'matchAvailability', selId(p, matchRef.id))));
    for (const p of additions) if (memberships.get(p.uid).get('category') !== category) throw new Error('Un jugador ya no pertenece a esta categoría');
    const details = { opponent: enc.get('opponent'), category, teamName: t.get('name'), kickoff: enc.get('kickoff'), responseDeadline: enc.get('responseDeadline') ?? enc.get('kickoff') };
    for (const p of [...additions, ...withdrawals]) {
      const a = assignments.get(p.uid);
      if (a?.exists()) {
        if (a.get('kind') === 'STARTER') tx.delete(doc(db, 'matchLineups', matchRef.id, 'slots', a.get('position')));
        tx.delete(a.ref);
      }
      const r = responses.get(p.uid);
      if (r?.exists()) tx.delete(r.ref);
      tx.set(doc(db, 'matchCallupPlayers', selId(p, matchRef.id)), { matchId: matchRef.id, teamId: team, ownerId: coach.uid, playerId: p.uid, selected: additions.includes(p), encounter: details, updatedAt: serverTimestamp() });
    }
  });
}
const L = (db, m) => doc(db, 'matchLineups', m);
// MatchLineupRepository.formation
const setFormation = (db, m, formation) => setDoc(L(db, m), { teamId: team, formation, updatedAt: serverTimestamp() });
// MatchLineupRepository.assign (mismo orden de lecturas y escrituras)
function assign(db, m, formation, playerId, kind, position) {
  return runTransaction(db, async (tx) => {
    const ref = L(db, m);
    const parent = await tx.get(ref);
    if (parent.exists() && parent.get('formation') !== formation) throw new Error('La formación cambió. Revisa la cancha antes de guardar.');
    const playerRef = doc(ref, 'players', playerId);
    const previous = await tx.get(playerRef);
    const target = kind === 'STARTER' ? doc(ref, 'slots', position) : null;
    const targetDoc = target ? await tx.get(target) : null;
    if (targetDoc && targetDoc.exists() && targetDoc.get('playerId') !== playerId) throw new Error('Esta posición ya está ocupada. Retira o mueve primero al titular.');
    const callup = await tx.get(doc(db, 'matchCallupPlayers', `${m}_${playerId}`));
    if (callup.get('selected') !== true) throw new Error('El jugador ya no está convocado');
    if (!parent.exists()) tx.set(ref, { teamId: team, formation, updatedAt: serverTimestamp() });
    const oldPosition = previous.get('position');
    if (previous.get('kind') === 'STARTER' && (kind !== 'STARTER' || oldPosition !== position)) tx.delete(doc(ref, 'slots', oldPosition));
    if (target) tx.set(target, { playerId, updatedAt: serverTimestamp() });
    tx.set(playerRef, { playerId, kind, position, updatedAt: serverTimestamp() });
  });
}
// MatchLineupRepository.remove (P1: si ya no está, termina sin escribir)
function remove(db, m, playerId) {
  return runTransaction(db, async (tx) => {
    const ref = doc(L(db, m), 'players', playerId);
    const old = await tx.get(ref);
    if (!old.exists()) return;
    if (old.get('kind') === 'STARTER') tx.delete(doc(L(db, m), 'slots', old.get('position')));
    tx.delete(ref);
  });
}
// Escritura "a mano" (sin las comprobaciones de la app) para probar solo las reglas.
function rawAssign(db, m, playerId, kind, position, { slot = kind === 'STARTER', parent = null } = {}) {
  const b = writeBatch(db);
  if (parent) b.set(L(db, m), { teamId: team, formation: parent, updatedAt: serverTimestamp() });
  if (slot) b.set(doc(L(db, m), 'slots', position), { playerId, updatedAt: serverTimestamp() });
  b.set(doc(L(db, m), 'players', playerId), { playerId, kind, position, updatedAt: serverTimestamp() });
  return b.commit();
}
// MatchLineupRepository.openAsGuardian
const openAsGuardian = (u, m, playerId) => setDoc(doc(L(u.db, m), 'guardians', u.uid), { playerId, updatedAt: serverTimestamp() });
const once = (ref) => new Promise((resolve) => { const stop = onSnapshot(ref, (s) => { stop(); resolve(s.docs ? `${s.size} docs` : s.exists() ? 'existe' : 'no existe'); }, (e) => resolve(e.code)); });
const players = (db, m) => collection(L(db, m), 'players');
const slotsOf = async (m) => (await getDocs(collection(L(coach.db, m), 'slots'))).docs.map((d) => `${d.id}=${P.findIndex((p) => p.uid === d.get('playerId')) + 1}`).sort().join(',');

// ---------- 1. Convocar ----------
console.log('--- 1. Convocar ---');
const fx = doc(collection(coach.db, 'matchFixtures'));
await expectOk('entrenadora registra el partido', setDoc(fx, { teamId: team, ownerId: coach.uid, category: 'SUB-12', opponent: 'Rival', kickoff: Timestamp.fromMillis(Date.now() + 48 * 3600e3), responseDeadline: Timestamp.fromMillis(Date.now() + 24 * 3600e3), updatedAt: serverTimestamp() }));
const m = fx.id;
await expectOk('convoca a 14 jugadores en UN guardado (13 + uno que dirá No disponible)', saveSelections(coach.db, fx, [...P, unavailable], []));
await expectMsg('no se puede convocar a uno de otra categoría (la app lo frena)', saveSelections(coach.db, fx, [otherCat], []), 'categoría');
await expectDenied('ni saltándose la app (reglas: membresía de la categoría)', setDoc(doc(coach.db, 'matchCallupPlayers', selId(otherCat, m)), { matchId: m, teamId: team, ownerId: coach.uid, playerId: otherCat.uid, selected: true, updatedAt: serverTimestamp() }));
await expectOk('el jugador No disponible responde', setDoc(doc(unavailable.db, 'matchAvailability', selId(unavailable, m)), { matchId: m, teamId: team, playerId: unavailable.uid, ownerId: coach.uid, status: 'UNAVAILABLE', respondedBy: unavailable.uid, updatedAt: serverTimestamp() }));

// ---------- 2. Lecturas ANTES de que exista la alineación ----------
console.log('\n--- 2. Lecturas de la alineación AÚN INEXISTENTE (observeFormation / observePlayers) ---');
for (const [label, u, want] of [
  ['entrenadora dueña aprobada', coach, 'no existe'], ['jugador convocado', P[0], 'no existe'],
  ['jugador NO convocado de la misma categoría', notCalled, 'no existe'],
  ['jugador de OTRA categoría', otherCat, 'permission-denied'], ['tutor vinculado SIN acceso guardado', guardian, 'permission-denied'],
  ['[P4a] admin', admin, 'no existe'], ['OTRA entrenadora', otherCoach, 'permission-denied'],
  ['entrenadora PENDIENTE (no dueña)', pendingCoach, 'permission-denied']]) {
  const r = await once(L(u.db, m));
  out(`${label}: onSnapshot del documento de alineación → ${want}`, r === want, r);
}
for (const [label, u, want] of [['entrenadora', coach, '0 docs'], ['jugador convocado', P[0], '0 docs'], ['tutor vinculado SIN acceso', guardian, 'permission-denied'], ['[P4a] admin', admin, '0 docs']]) {
  const r = await once(players(u.db, m));
  out(`${label}: onSnapshot de players/ vacío → ${want}`, r === want, r);
}

console.log('\n--- [P4b] Acceso del tutor (guardians/{uid}) con la alineación AÚN INEXISTENTE ---');
await expectOk('[P4b] tutor verificado de j1 (convocado) guarda su acceso', openAsGuardian(guardian, m, P[0].uid));
await expectOk('[P4b] volver a abrir (set sobre el acceso existente) también funciona', openAsGuardian(guardian, m, P[0].uid));
for (const [label, ref, want] of [['documento de alineación', (u) => L(u.db, m), 'no existe'], ['players/', (u) => players(u.db, m), '0 docs'],
  ['slots/', (u) => collection(L(u.db, m), 'slots'), '0 docs']]) {
  const r = await once(ref(guardian));
  out(`[P4b] tutor con acceso: onSnapshot de ${label} inexistente → ${want}`, r === want, r);
}
await expectDenied('[P4b] tutor NO vinculado no puede guardar acceso a j1', openAsGuardian(otherGuardian, m, P[0].uid));
await expectDenied('[P4b] tutor sin verificar el correo no puede guardar acceso', openAsGuardian(unverifiedGuardian, m, P[0].uid));
await expectDenied('[P4b] tutor de un jugador NO convocado no puede guardar acceso', openAsGuardian(guardianNC, m, notCalled.uid));
await expectDenied('[P4b] tutor con playerId de otro jugador (j2) no puede', openAsGuardian(guardian, m, P[1].uid));
await expectDenied('[P4b] jugador j2 no puede crear un acceso de tutor (no tiene el rol)', openAsGuardian(P[1], m, P[1].uid));
await expectDenied('[P4b] tutor no puede escribir el acceso de OTRO usuario', setDoc(doc(L(guardian.db, m), 'guardians', otherGuardian.uid), { playerId: P[0].uid, updatedAt: serverTimestamp() }));
await expectDenied('[P4b] tutor no puede inventar campos en su acceso', setDoc(doc(L(guardian.db, m), 'guardians', guardian.uid), { playerId: P[0].uid, updatedAt: serverTimestamp(), teamId: team }));
out('[P4b] tutor de un no convocado: sigue sin leer la alineación', (await once(players(guardianNC.db, m))) === 'permission-denied');
await expectOk('[P4b] tutor de j3 guarda su acceso (j3 convocado)', openAsGuardian(guardian3, m, P[2].uid));
const seenG = [];
const stopG = onSnapshot(players(guardian.db, m), (s) => seenG.push(s.size), (e) => seenG.push(e.code));
// Listener del jugador abierto durante todo el flujo (como la pantalla).
const seen = [];
const stopP1 = onSnapshot(players(P[0].db, m), (s) => seen.push(s.size), (e) => seen.push(e.code));

// ---------- 3. Titulares ----------
console.log('\n--- 3. Titulares (assign con padre, jugador y casilla inexistentes) ---');
await expectOk('primer titular: j1 portero (P10) — crea el padre en la misma transacción', assign(coach.db, m, '4-3-3', P[0].uid, 'STARTER', 'P10'));
let ok = true;
for (let i = 1; i <= 10; i++) ok = (await code(assign(coach.db, m, '4-3-3', P[i].uid, 'STARTER', `P${i - 1}`))) === 'OK' && ok;
out('j2..j11 titulares en P0..P9 (11/11)', ok, await slotsOf(m));
await expectMsg('12.º titular en casilla ocupada: la app lo frena', assign(coach.db, m, '4-3-3', P[11].uid, 'STARTER', 'P0'), 'ocupada');
await expectDenied('12.º titular saltándose la app (pisa la casilla P0)', rawAssign(coach.db, m, P[11].uid, 'STARTER', 'P0'));
await expectDenied('12.º titular en una casilla inventada P11', rawAssign(coach.db, m, P[11].uid, 'STARTER', 'P11'));
await expectDenied('titular sin casilla (solo el documento del jugador)', rawAssign(coach.db, m, P[11].uid, 'STARTER', 'P3', { slot: false }));
await expectDenied('formación fuera de lista (5-4-1)', setFormation(coach.db, m, '5-4-1'));

// ---------- 4. Suplentes ----------
console.log('\n--- 4. Suplentes ---');
await expectOk('j12 suplente DEF', assign(coach.db, m, '4-3-3', P[11].uid, 'BENCH', 'DEF'));
await expectOk('j13 suplente MED', assign(coach.db, m, '4-3-3', P[12].uid, 'BENCH', 'MED'));
await expectOk('[observado] jugador que respondió "No disponible" como suplente POR (se permite)', assign(coach.db, m, '4-3-3', unavailable.uid, 'BENCH', 'POR'));
await expectDenied('posición de suplente inválida ("LAT")', rawAssign(coach.db, m, P[11].uid, 'BENCH', 'LAT'));
await expectMsg('no convocado: la app lo frena (lee una convocatoria INEXISTENTE)', assign(coach.db, m, '4-3-3', notCalled.uid, 'BENCH', 'DEF'), 'ya no está convocado');
await expectDenied('no convocado saltándose la app', rawAssign(coach.db, m, notCalled.uid, 'BENCH', 'DEF'));
await expectDenied('otra categoría saltándose la app', rawAssign(coach.db, m, otherCat.uid, 'BENCH', 'DEF'));
await expectDenied('titular y suplente a la vez: j1 BENCH sin liberar su casilla P10', rawAssign(coach.db, m, P[0].uid, 'BENCH', 'DEF', { slot: false }));
await expectDenied('mismo jugador en dos casillas: j1 también en P3 con otro id de documento', (async () => {
  const b = writeBatch(coach.db);
  b.set(doc(L(coach.db, m), 'players', 'duplicado'), { playerId: P[0].uid, kind: 'BENCH', position: 'DEF', updatedAt: serverTimestamp() });
  await b.commit();
})());

// ---------- 5. Cambiar posición ----------
console.log('\n--- 5. Cambiar posición ---');
await expectOk('j11 (P9) pasa a suplente DEL (libera P9)', assign(coach.db, m, '4-3-3', P[10].uid, 'BENCH', 'DEL'));
await expectOk('j1 se mueve de P10 a P9', assign(coach.db, m, '4-3-3', P[0].uid, 'STARTER', 'P9'));
await expectOk('j12 de suplente a titular en P10', assign(coach.db, m, '4-3-3', P[11].uid, 'STARTER', 'P10'));
let sl = await slotsOf(m);
out('casillas coherentes (P9=j1, P10=j12, 11 casillas)', sl.includes('P9=1') && sl.includes('P10=12') && sl.split(',').length === 11, sl);
let pl = (await getDocs(players(coach.db, m))).docs;
out('un documento por jugador: 11 titulares + 3 suplentes', pl.filter((d) => d.get('kind') === 'STARTER').length === 11 && pl.filter((d) => d.get('kind') === 'BENCH').length === 3, `${pl.length} docs`);

// ---------- 6. Cambiar formación ----------
console.log('\n--- 6. Cambiar formación ---');
await expectOk('formación 4-3-3 → 4-4-2 (set del padre)', setFormation(coach.db, m, '4-4-2'));
await expectMsg('asignar con la formación vieja en pantalla: la app lo frena', assign(coach.db, m, '4-3-3', P[12].uid, 'BENCH', 'DEF'), 'formación cambió');
sl = await slotsOf(m);
out('[observado] las casillas P0..P10 se conservan tras el cambio (P2 pasa de DEL a MED)', sl.split(',').length === 11, sl);
await expectOk('asignar con la formación nueva', assign(coach.db, m, '4-4-2', P[12].uid, 'BENCH', 'DEF'));

// ---------- 7. Retirar ----------
console.log('\n--- 7. Retirar de la alineación y de la convocatoria ---');
await expectOk('remove(): retirar al suplente j13', remove(coach.db, m, P[12].uid));
await expectOk('remove(): retirar al titular j2 (borra jugador + casilla)', remove(coach.db, m, P[1].uid));
await expectOk('[P1] remove() de un jugador ya retirado (otro dispositivo): termina sin error', remove(coach.db, m, P[1].uid));
out('[P1] y no deja nada escrito (j2 sigue fuera de players/)', !(await getDoc(doc(players(coach.db, m), P[1].uid))).exists());
await expectDenied('quitar la convocatoria SIN borrar la alineación (j3 titular)', setDoc(doc(coach.db, 'matchCallupPlayers', selId(P[2], m)), { matchId: m, teamId: team, ownerId: coach.uid, playerId: P[2].uid, selected: false, updatedAt: serverTimestamp() }, { merge: false }));
for (const p of P.slice(2, 7)) await setDoc(doc(p.db, 'matchAvailability', selId(p, m)), { matchId: m, teamId: team, playerId: p.uid, ownerId: coach.uid, status: 'AVAILABLE', respondedBy: p.uid, updatedAt: serverTimestamp() });
out('[P4b] tutor de j3 lee la alineación mientras j3 está convocado', (await once(players(guardian3.db, m))).endsWith('docs'));
await expectOk('saveSelections retira a 5 titulares con respuesta (máximo de la app) en UNA transacción', saveSelections(coach.db, fx, [], P.slice(2, 7)));
out('[P4b] al retirar a j3, su tutor pierde el acceso aunque el documento siga', (await once(players(guardian3.db, m))) === 'permission-denied');
await expectDenied('[P4b] y no puede renovarlo', openAsGuardian(guardian3, m, P[2].uid));
pl = (await getDocs(players(coach.db, m))).docs.map((d) => d.id);
const avLeft = (await Promise.all(P.slice(2, 7).map((p) => getDoc(doc(coach.db, 'matchAvailability', selId(p, m)))))).filter((d) => d.exists()).length;
sl = await slotsOf(m);
out('los 5 retirados salen de players/, slots/ y matchAvailability', P.slice(2, 7).every((p) => !pl.includes(p.uid)) && avLeft === 0 && sl.split(',').length === 5, `casillas=${sl}`);
await expectMsg('retirado (j3) no se puede volver a alinear: la app lo frena', assign(coach.db, m, '4-4-2', P[2].uid, 'BENCH', 'DEF'), 'ya no está convocado');
await expectDenied('retirado saltándose la app', rawAssign(coach.db, m, P[2].uid, 'BENCH', 'DEF'));
info('retirado (j3) lee la alineación (es de la categoría)', await once(players(P[2].db, m)));

// ---------- 8. Quién modifica ----------
console.log('\n--- 8. Solo usuarios autorizados modifican ---');
for (const [label, u] of [['jugador convocado j1', P[0]], ['tutor vinculado', guardian], ['admin', admin], ['OTRA entrenadora aprobada', otherCoach], ['entrenadora PENDIENTE', pendingCoach]]) {
  await expectDenied(`${label}: cambiar formación`, setFormation(u.db, m, '3-5-2'));
  await expectDenied(`${label}: assign (titular)`, assign(u.db, m, '4-4-2', P[7].uid, 'BENCH', 'MED').catch((e) => { throw e.code ? e : Object.assign(e, { code: e.message }); }));
  await expectDenied(`${label}: remove de un suplente`, remove(u.db, m, P[10].uid));
}
// Entrenadora PENDIENTE dueña de un equipo (aprobación retirada): lee pero no modifica.
const team2 = `eq2-${run}`;
await seed(`teams/${team2}`, { createdBy: pendingCoach.uid, name: 'Equipo Pendiente', type: 'EQUIPO', categories: ['SUB-12'] });
await seed(`teamMemberships/${team2}_${P[0].uid}`, { teamId: team2, userId: P[0].uid, playerName: 'j1', category: 'SUB-12' });
const m2 = `fx2-${run}`;
await seed(`matchFixtures/${m2}`, { teamId: team2, ownerId: pendingCoach.uid, category: 'SUB-12', opponent: 'R2', kickoff: new Date(Date.now() + 48 * 3600e3), responseDeadline: new Date(Date.now() + 24 * 3600e3), updatedAt: new Date() });
await seed(`matchCallupPlayers/${m2}_${P[0].uid}`, { matchId: m2, teamId: team2, ownerId: pendingCoach.uid, playerId: P[0].uid, selected: true, updatedAt: new Date() });
out('entrenadora PENDIENTE dueña: lee su alineación (inexistente)', (await once(L(pendingCoach.db, m2))) === 'no existe');
await expectDenied('entrenadora PENDIENTE dueña: no puede crear la alineación', assign(pendingCoach.db, m2, '4-3-3', P[0].uid, 'STARTER', 'P10'));

// ---------- 9. Jugador / tutor leen ----------
console.log('\n--- 9. Jugador y tutor leen la alineación guardada ---');
const p1Players = await getDocs(players(P[0].db, m)).then((s) => s.size, (e) => e.code);
out('jugador convocado j1 lee players/', typeof p1Players === 'number', String(p1Players));
out('jugador convocado j1 lee slots/', (await once(collection(L(P[0].db, m), 'slots'))).endsWith('docs'));
out('jugador convocado j1 lee el documento (formación)', (await getDoc(L(P[0].db, m))).get('formation') === '4-4-2');
out(`listener de j1 recibió los cambios en vivo (${seen.length} instantáneas)`, seen.length > 5 && !seen.some((x) => typeof x === 'string'), seen.slice(-3).join('→'));
const gPlayers = await getDocs(players(guardian.db, m)).then((s) => s.size, (e) => e.code);
const cPlayers = (await getDocs(players(coach.db, m))).size;
out('[P4b] tutor con acceso lee players/ completo (igual que la entrenadora)', gPlayers === cPlayers, `${gPlayers} de ${cPlayers}`);
out('[P4b] tutor lee slots/ y la formación', (await once(collection(L(guardian.db, m), 'slots'))).endsWith('docs') && (await getDoc(L(guardian.db, m))).get('formation') === '4-4-2');
out(`[P4b] listener del tutor recibió los cambios en vivo (${seenG.length} instantáneas)`, seenG.length > 3 && !seenG.some((x) => typeof x === 'string'), seenG.slice(-3).join('→'));
out('[P4b] el tutor NO puede leer la plantilla (US-22: los demás se ven como "Compañero")', (await getDocs(query(collection(guardian.db, 'teamMemberships'), where('teamId', '==', team))).then(() => 'OK', (e) => e.code)) === 'permission-denied');
for (const [label, ref] of [['documento', L(admin.db, m)], ['players/', players(admin.db, m)], ['slots/', collection(L(admin.db, m), 'slots')]])
  out(`[P4a] admin lee ${label}`, !(String(await once(ref))).includes('denied'));
await expectDenied('[P4a] admin no puede crear un acceso de tutor', openAsGuardian(admin, m, P[0].uid));
info('jugador NO convocado de la misma categoría lee la alineación', await once(players(notCalled.db, m)));
const gen = await getDoc(doc(coach.db, 'teamLineups', `${team}_SUB-12`));
out('la alineación general (teamLineups) no cambió', gen.get('formation') === '3-5-2' && gen.get('positions').P0 === P[5].uid);

// ---------- 10. Cambio de categoría de un alineado ----------
console.log('\n--- 10. Cambio de categoría de un alineado (US-03) ---');
const catChange = await code(updateDoc(doc(coach.db, 'teamMemberships', `${team}_${P[7].uid}`), { playerName: 'j8', category: 'SUB-14' }));
info('entrenadora cambia la categoría de j8 (titular) directamente', catChange);

// ---------- 11. Partido empezado ----------
console.log('\n--- 11. Partido empezado ---');
const kick = Date.now() + 6000;
const fx3 = doc(collection(coach.db, 'matchFixtures'));
await expectOk('partido que empieza en 6 s', setDoc(fx3, { teamId: team, ownerId: coach.uid, category: 'SUB-12', opponent: 'Pronto', kickoff: Timestamp.fromMillis(kick), responseDeadline: Timestamp.fromMillis(kick - 1000), updatedAt: serverTimestamp() }));
await expectOk('convocar a j1 y j12', saveSelections(coach.db, fx3, [P[0], P[11]], []));
await expectOk('alinear a j1 (titular) y j12 (suplente) antes del inicio', assign(coach.db, fx3.id, '4-3-3', P[0].uid, 'STARTER', 'P10').then(() => assign(coach.db, fx3.id, '4-3-3', P[11].uid, 'BENCH', 'DEF')));
await new Promise((r) => setTimeout(r, Math.max(0, kick - Date.now()) + 1500));
await expectDenied('tras el inicio: assign', assign(coach.db, fx3.id, '4-3-3', P[11].uid, 'BENCH', 'MED'));
await expectDenied('tras el inicio: cambiar formación', setFormation(coach.db, fx3.id, '4-4-2'));
const lateRemove = await code(remove(coach.db, fx3.id, P[11].uid));
info('tras el inicio: la entrenadora borra a un alineado con el SDK (la app oculta el botón)', lateRemove);
out('jugador lee la alineación tras el inicio', (await once(players(P[0].db, fx3.id))).endsWith('docs'));
await expectOk('[P4b] tutor abre la alineación de un partido ya empezado (solo lectura)', openAsGuardian(guardian, fx3.id, P[0].uid));
out('[P4b] y la lee', (await once(players(guardian.db, fx3.id))).endsWith('docs'));
await expectDenied('[P4b] tutor no puede escribir la alineación', assign(guardian.db, fx3.id, '4-3-3', P[0].uid, 'BENCH', 'MED'));

// ---------- 12. Cancelar (MatchCleanup.deleteMatch) ----------
console.log('\n--- 12. Cancelar el partido (MatchCleanup) ---');
const stopFormation = [];
const formationEvents = new Promise((resolve) => { const s = onSnapshot(L(P[0].db, m), () => {}, (e) => resolve(e.code)); stopFormation.push(s); });
await expectOk('cancelar: borra players+slots (de 5 en 5), slots sueltos, padre, convocatorias, respuestas y partido', (async () => {
  for (;;) {
    const snap = await getDocs(query(players(coach.db, m)));
    if (snap.empty) break;
    const b = writeBatch(coach.db);
    snap.docs.slice(0, 5).forEach((d) => { if (d.get('kind') === 'STARTER') b.delete(doc(L(coach.db, m), 'slots', d.get('position'))); b.delete(d.ref); });
    await b.commit();
  }
  for (const d of (await getDocs(collection(L(coach.db, m), 'slots'))).docs) await deleteDoc(d.ref);
  for (const d of (await getDocs(collection(L(coach.db, m), 'guardians'))).docs) await deleteDoc(d.ref);
  if ((await getDoc(L(coach.db, m))).exists()) await deleteDoc(L(coach.db, m));
  const sel = await getDocs(query(collection(coach.db, 'matchCallupPlayers'), where('ownerId', '==', coach.uid), where('matchId', '==', m)));
  const b = writeBatch(coach.db);
  for (const s of sel.docs) { const r = await getDoc(doc(coach.db, 'matchAvailability', s.id)); if (r.exists()) b.delete(r.ref); b.delete(s.ref); }
  await b.commit();
  await deleteDoc(fx);
})());
const evt = await Promise.race([formationEvents, new Promise((r) => setTimeout(() => r('sin error'), 3000))]);
// El padre se borra antes que el partido: el listener ve "no existe" y no recibe error después.
// En la app, el aviso "El partido fue cancelado" lo da el listener del partido (callups.match).
info('listener del jugador sobre la alineación tras cancelar (ver probe-cancel-listener.mjs)', evt);
out('alineación del partido cancelado: lectura denegada (no queda nada huérfano visible)', (await once(players(coach.db, m))) === 'permission-denied');
const orphan = await raw(`${root}/matchLineups/${m}/players`);
out('no quedan documentos huérfanos en players/', !orphan.documents || orphan.documents.length === 0);
const orphanG = await raw(`${root}/matchLineups/${m}/guardians`);
out('[P4b] no quedan accesos de tutores huérfanos en guardians/', !orphanG.documents || orphanG.documents.length === 0);
await expectDenied('[P4b] tutor no puede abrir la alineación de un partido cancelado', openAsGuardian(guardian, m, P[0].uid));
// Lectura nueva: se cierra antes el listener del tutor, que el SDK reutilizaría (no se reevalúa sin cambios).
stopG(); await new Promise((r) => setTimeout(r, 500));
const gCancel = await getDocsFromServer(players(guardian.db, m)).then((x) => `${x.size} docs`, (e) => e.code);
out('[P4b] tutor: alineación del partido cancelado (servidor) → permission-denied', gCancel === 'permission-denied', gCancel);

stopP1();
console.log(failures === 0 ? `\nUS11 client flow verified in demo-sportpro only: ${count} de ${count} OK.` : `\n${failures} de ${count} comprobaciones fallidas.`);
process.exit(failures === 0 ? 0 : 1);
