// US-10 con el SDK CLIENTE (como la app): convocar -> responder jugador -> cambiar -> responder tutor
// -> entrenadora lee -> retirar/reconvocar -> cierre -> cancelar. Incluye lecturas de documentos que
// AÚN NO EXISTEN (listener de respuesta, tx.get de respuestas al retirar o reconvocar).
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Requiere el emulador arrancado con firebase.local.json. Sale con código 1 si algo falla.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, runTransaction, setDoc, getDoc, getDocs, query, where,
  onSnapshot, writeBatch, deleteDoc, serverTimestamp, Timestamp } = require('firebase/firestore');

const project = 'demo-sportpro';
const root = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents`;
const authUrl = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const password = 'SportProLocal2026!';
const run = Date.now().toString(36);
const J = { 'Content-Type': 'application/json' };
// "Bearer owner" se salta las reglas: solo para preparar datos.
const raw = async (url, method = 'GET', body) => (await fetch(url, { method, headers: { ...J, Authorization: 'Bearer owner' }, ...(body ? { body: JSON.stringify(body) } : {}) })).json();
const val = (v) => v instanceof Date ? { timestampValue: v.toISOString() } : Array.isArray(v) ? { arrayValue: { values: v.map(val) } }
  : typeof v === 'boolean' ? { booleanValue: v } : typeof v === 'number' ? { integerValue: String(v) } : { stringValue: v };
const seed = (path, data) => raw(`${root}/${path}`, 'PATCH', { fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, val(v)])) });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

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
const coach = await user('entrenadora', ['ENTRENADOR']);
const otherCoach = await user('otra-entrenadora', ['ENTRENADOR']);
const player = await user('jugador', ['JUGADOR']);
const teammate = await user('companero', ['JUGADOR']);
const silent = await user('sin-responder', ['JUGADOR']);
const notCalled = await user('no-convocado', ['JUGADOR']);
const guardian = await user('tutor', ['PADRE / TUTOR']);
const otherGuardian = await user('tutor-no-vinculado', ['PADRE / TUTOR']);
const revokedGuardian = await user('tutor-revocado', ['PADRE / TUTOR']);
const unverifiedGuardian = await user('tutor-sin-verificar', ['PADRE / TUTOR'], false);
const admin = await user('admin', []);
for (const c of [coach, otherCoach]) await seed(`coachApprovals/${c.uid}`, { approved: true });
await seed(`admins/${admin.uid}`, { since: new Date() });
const team = `eq-${run}`;
await seed(`teams/${team}`, { createdBy: coach.uid, name: 'Equipo Valido', type: 'EQUIPO', categories: ['SUB-12'] });
const member = (p) => seed(`teamMemberships/${team}_${p.uid}`, { teamId: team, userId: p.uid, playerName: p.name, category: 'SUB-12' });
for (const p of [player, teammate, silent, notCalled]) await member(p);
const link = (g, status) => seed(`guardianLinks/${player.uid}_${g.uid}`, { playerId: player.uid, guardianId: g.uid, inviteId: 'x', status });
await link(guardian, 'ACTIVE'); await link(revokedGuardian, 'REVOKED'); await link(unverifiedGuardian, 'ACTIVE');

let count = 0, failures = 0;
const out = (label, ok, detail = '') => { count++; if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${count}. ${label}${detail ? ': ' + detail : ''}`); };
const code = (p) => p.then(() => 'OK', (e) => e.code ?? String(e));
const expectOk = async (label, p) => { const c = await code(p); out(label, c === 'OK', c); };
const expectDenied = async (label, p) => { const c = await code(p); out(label, c === 'permission-denied', c); };
const existsLabel = (p) => p.then((d) => (d.exists() ? `existe:${d.get('status')}` : 'no existe'), (e) => e.code);

// ---------- Convocar: misma transacción que CallupRepository.saveSelections ----------
const selId = (p, m) => `${m}_${p.uid}`;
function saveSelections(db, matchRef, additions, withdrawals) {
  return runTransaction(db, async (tx) => {
    const enc = await tx.get(matchRef);
    const t = await tx.get(doc(db, 'teams', team));
    const current = new Map();
    for (const p of [...additions, ...withdrawals]) current.set(p.uid, await tx.get(doc(db, 'matchCallupPlayers', selId(p, matchRef.id))));
    for (const p of additions) await tx.get(doc(db, 'teamMemberships', `${team}_${p.uid}`));
    const assignments = new Map();
    for (const p of withdrawals) assignments.set(p.uid, await tx.get(doc(db, 'matchLineups', matchRef.id, 'players', p.uid)));
    // US-10: respuestas de retirados y de reconvocados (se leen aunque no existan; se borran solo si existen).
    const responses = new Map();
    for (const p of [...withdrawals, ...additions.filter((a) => current.get(a.uid).exists())])
      responses.set(p.uid, await tx.get(doc(db, 'matchAvailability', selId(p, matchRef.id))));
    const details = { opponent: enc.get('opponent'), category: 'SUB-12', teamName: t.get('name'), kickoff: enc.get('kickoff'), responseDeadline: enc.get('responseDeadline') ?? enc.get('kickoff') };
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
const newMatch = async (label, opponent, deadlineMs, kickoffMs = Date.now() + 48 * 3600e3) => {
  const ref = doc(collection(coach.db, 'matchFixtures'));
  await expectOk(label, setDoc(ref, { teamId: team, ownerId: coach.uid, category: 'SUB-12', opponent, kickoff: Timestamp.fromMillis(kickoffMs), responseDeadline: Timestamp.fromMillis(deadlineMs), updatedAt: serverTimestamp() }));
  return ref;
};

console.log('--- Convocar ---');
const fx = await newMatch('entrenadora registra partido (cierre en 24 h)', 'Rival', Date.now() + 24 * 3600e3);
await expectOk('entrenadora convoca a jugador, compañero y uno que no responderá', saveSelections(coach.db, fx, [player, teammate, silent], []));

// ---------- Lecturas de documentos que aún no existen ----------
console.log('\n--- Listener de respuesta ANTES de responder (documento inexistente) ---');
const avail = (db, p, m = fx.id) => doc(db, 'matchAvailability', selId(p, m));
for (const [label, u, want] of [['jugador convocado', player, 'no existe'], ['tutor vinculado', guardian, 'no existe'], ['entrenadora dueña', coach, 'no existe'],
  ['admin', admin, 'no existe'], ['compañero (respuesta ajena)', teammate, 'permission-denied'], ['jugador no convocado', notCalled, 'permission-denied'],
  ['tutor no vinculado', otherGuardian, 'permission-denied'], ['tutor revocado', revokedGuardian, 'permission-denied'],
  ['tutor sin verificar', unverifiedGuardian, 'permission-denied'], ['OTRA entrenadora', otherCoach, 'permission-denied']]) {
  const r = await existsLabel(getDoc(avail(u.db, player)));
  out(`${label} lee la respuesta aún inexistente → ${want}`, r === want, r);
}
const r0 = await existsLabel(getDoc(avail(notCalled.db, notCalled)));
out('jugador no convocado lee SU respuesta (sin convocatoria) → permission-denied', r0 === 'permission-denied', r0);
const listen = (u, p) => new Promise((resolve) => { const stop = onSnapshot(avail(u.db, p), { includeMetadataChanges: true },
  (s) => { stop(); resolve(s.exists() ? 'existe' : 'no existe'); }, (e) => resolve(e.code)); });
out('onSnapshot del jugador sobre su respuesta inexistente', (await listen(player, player)) === 'no existe');
out('onSnapshot del tutor sobre la respuesta inexistente', (await listen(guardian, player)) === 'no existe');

// ---------- Responder (AvailabilityRepository.respond = set sin merge) ----------
console.log('\n--- Responder y cambiar ---');
const respond = (u, p, status, m = fx.id) => setDoc(avail(u.db, p, m), { matchId: m, teamId: team, playerId: p.uid, ownerId: coach.uid, status, respondedBy: u.uid, updatedAt: serverTimestamp() });
await expectOk('[C1/C2] jugador responde Disponible (crea)', respond(player, player, 'AVAILABLE'));
await expectOk('[C5] jugador cambia a No disponible antes del cierre', respond(player, player, 'UNAVAILABLE'));
await expectDenied('[C2] estado inválido "QUIZAS"', respond(player, player, 'QUIZAS'));
await expectOk('[C1] tutor vinculado cambia a Disponible', respond(guardian, player, 'AVAILABLE'));
let s = await getDoc(avail(player.db, player));
out('se guarda quién (respondedBy) y cuándo (updatedAt)', s.get('respondedBy') === guardian.uid && s.get('updatedAt') != null, `respondedBy=${s.get('respondedBy') === guardian.uid ? 'tutor' : s.get('respondedBy')}`);
await expectDenied('[C4] compañero responde por el jugador', respond(teammate, player, 'AVAILABLE'));
await expectDenied('[C4] jugador no convocado responde por el jugador', respond(notCalled, player, 'AVAILABLE'));
await expectDenied('[C4] jugador no convocado responde por sí mismo', respond(notCalled, notCalled, 'AVAILABLE'));
await expectDenied('[C4] tutor no vinculado', respond(otherGuardian, player, 'AVAILABLE'));
await expectDenied('[C4] tutor revocado', respond(revokedGuardian, player, 'AVAILABLE'));
await expectDenied('[C4] tutor sin verificar el correo', respond(unverifiedGuardian, player, 'AVAILABLE'));
await expectDenied('[C4] entrenadora dueña responde por el jugador', respond(coach, player, 'AVAILABLE'));
await expectDenied('[C4] OTRA entrenadora', respond(otherCoach, player, 'AVAILABLE'));
await expectDenied('[C4] admin responde', respond(admin, player, 'AVAILABLE'));
await expectDenied('respondedBy suplantado (el jugador firma como tutor)', setDoc(avail(player.db, player), { matchId: fx.id, teamId: team, playerId: player.uid, ownerId: coach.uid, status: 'AVAILABLE', respondedBy: guardian.uid, updatedAt: serverTimestamp() }));
await expectDenied('campo extra en la respuesta (nota)', setDoc(avail(player.db, player), { matchId: fx.id, teamId: team, playerId: player.uid, ownerId: coach.uid, status: 'AVAILABLE', respondedBy: player.uid, note: 'x', updatedAt: serverTimestamp() }));

console.log('\n--- Jugador y tutor a la vez ---');
const both = await Promise.all([code(respond(player, player, 'UNAVAILABLE')), code(respond(guardian, player, 'AVAILABLE'))]);
s = await getDoc(avail(coach.db, player));
out('ambas escrituras se aceptan; queda la última', both.every((c) => c === 'OK') && ['AVAILABLE', 'UNAVAILABLE'].includes(s.get('status')), `${both.join('/')} → ${s.get('status')}`);

// ---------- Entrenadora y admin leen (CoachAvailability: ownerId + matchId + teamId) ----------
console.log('\n--- [C3] La entrenadora ve los estados; admin solo lectura ---');
const coachQuery = (u) => getDocs(query(collection(u.db, 'matchAvailability'), where('ownerId', '==', coach.uid), where('matchId', '==', fx.id), where('teamId', '==', team)));
const q = await coachQuery(coach).then((x) => x.size, (e) => e.code);
out('consulta de la entrenadora: 1 respuesta; compañero y silencioso quedan "Sin respuesta"', q === 1, String(q));
await expectDenied('OTRA entrenadora consulta las respuestas del partido', coachQuery(otherCoach));
const qa = await coachQuery(admin).then((x) => x.size, (e) => e.code);
out('[fix 5] admin consulta las respuestas (solo lectura)', qa === 1, String(qa));
const ga = await existsLabel(getDoc(avail(admin.db, player)));
out('[fix 5] admin lee una respuesta por ID', ga.startsWith('existe'), ga);
await expectDenied('[fix 5] admin borra una respuesta', deleteDoc(avail(admin.db, player)));
const live = await new Promise((resolve) => { let n = 0; const stop = onSnapshot(query(collection(coach.db, 'matchAvailability'), where('ownerId', '==', coach.uid), where('matchId', '==', fx.id), where('teamId', '==', team)),
  (snap) => { n++; if (n === 1) respond(teammate, teammate, 'AVAILABLE'); else { stop(); resolve(snap.size); } }, (e) => resolve(e.code)); });
out('tiempo real: el listener de la entrenadora recibe la respuesta del compañero', live === 2, `docs=${live}`);

// ---------- [fix 1] Retirar y reconvocar ----------
console.log('\n--- [fix 1] Retirado y reconvocado ---');
await expectOk('[fix 1] retirar a quien respondió (jugador) y a quien NO respondió (silencioso) en un guardado', saveSelections(coach.db, fx, [], [player, silent]));
let g = await existsLabel(getDoc(avail(coach.db, player)));
out('[fix 1] la respuesta del retirado se borró', g === 'no existe', g);
g = await existsLabel(getDoc(avail(player.db, player)));
out('[fix 1] el jugador retirado ve "sin respuesta" (no "Respuesta anterior")', g === 'no existe', g);
await expectDenied('[C1] jugador retirado no puede responder', respond(player, player, 'AVAILABLE'));
await expectDenied('[C1] su tutor tampoco', respond(guardian, player, 'AVAILABLE'));
await expectOk('[fix 1] reconvocar al jugador y al silencioso', saveSelections(coach.db, fx, [player, silent], []));
g = await existsLabel(getDoc(avail(coach.db, player)));
out('[fix 1] tras reconvocar, la entrenadora ve "Sin respuesta"', g === 'no existe', g);
await expectOk('[fix 1] el reconvocado puede responder de nuevo', respond(player, player, 'AVAILABLE'));

console.log('\n--- [fix 1] Respuesta antigua (anterior al arreglo) y reconvocatoria ---');
await expectOk('retirar al compañero (tiene respuesta: se borra)', saveSelections(coach.db, fx, [], [teammate]));
await seed(`matchAvailability/${selId(teammate, fx.id)}`, { matchId: fx.id, teamId: team, playerId: teammate.uid, ownerId: coach.uid, status: 'UNAVAILABLE', respondedBy: teammate.uid, updatedAt: new Date('2026-11-20') });
await expectOk('[fix 1] reconvocar a quien tenía una respuesta antigua sembrada', saveSelections(coach.db, fx, [teammate], []));
g = await existsLabel(getDoc(avail(coach.db, teammate)));
out('[fix 1] la respuesta antigua se limpió al reconvocar', g === 'no existe', g);

console.log('\n--- [fix 1] 5 retiros con respuesta (2 en la alineación) en un guardado ---');
const five = [];
for (let i = 0; i < 5; i++) { const p = await account(`cinco-${i}`, ['JUGADOR']); await member(p); five.push(p); }
const fx5 = await newMatch('partido para 5 retiros', 'Rival 5', Date.now() + 24 * 3600e3);
await expectOk('convocar a los 5', saveSelections(coach.db, fx5, five, []));
for (const p of five) await seed(`matchAvailability/${selId(p, fx5.id)}`, { matchId: fx5.id, teamId: team, playerId: p.uid, ownerId: coach.uid, status: 'AVAILABLE', respondedBy: p.uid, updatedAt: new Date() });
await seed(`matchLineups/${fx5.id}`, { teamId: team, formation: '4-3-3', updatedAt: new Date() });
await seed(`matchLineups/${fx5.id}/players/${five[0].uid}`, { playerId: five[0].uid, kind: 'STARTER', position: 'P0', updatedAt: new Date() });
await seed(`matchLineups/${fx5.id}/slots/P0`, { playerId: five[0].uid, updatedAt: new Date() });
await seed(`matchLineups/${fx5.id}/players/${five[1].uid}`, { playerId: five[1].uid, kind: 'BENCH', position: 'DEF', updatedAt: new Date() });
await expectOk('[fix 1] retirar a los 5 (5 respuestas + titular + suplente) en una transacción', saveSelections(coach.db, fx5, [], five));
const left5 = (await Promise.all(five.map((p) => getDoc(avail(coach.db, p, fx5.id))))).filter((d) => d.exists()).length;
out('[fix 1] no queda ninguna de las 5 respuestas', left5 === 0, `quedan ${left5}`);

console.log('\n--- [fix 1] Guardado grande: 15 RECONVOCATORIAS con respuesta antigua ---');
const fifteen = [];
for (let i = 0; i < 15; i++) { const p = await account(`quince-${i}`, ['JUGADOR']); await member(p); fifteen.push(p); }
const fx15 = await newMatch('partido para 15 reconvocatorias', 'Rival 15', Date.now() + 24 * 3600e3);
await expectOk('convocar a los 15 (15 altas en un guardado)', saveSelections(coach.db, fx15, fifteen, []));
let okW = true;
for (let i = 0; i < 15; i += 5) okW = (await code(saveSelections(coach.db, fx15, [], fifteen.slice(i, i + 5)))) === 'OK' && okW;
out('retirar a los 15 (3 guardados de 5, como limita la app)', okW);
for (const p of fifteen) await seed(`matchAvailability/${selId(p, fx15.id)}`, { matchId: fx15.id, teamId: team, playerId: p.uid, ownerId: coach.uid, status: 'UNAVAILABLE', respondedBy: p.uid, updatedAt: new Date('2026-11-20') });
await expectOk('[fix 1] reconvocar a los 15 en UNA transacción (lee y borra 15 respuestas antiguas)', saveSelections(coach.db, fx15, fifteen, []));
const left15 = (await Promise.all(fifteen.map((p) => getDoc(avail(coach.db, p, fx15.id))))).filter((d) => d.exists()).length;
const sel15 = (await Promise.all(fifteen.map((p) => getDoc(doc(coach.db, 'matchCallupPlayers', selId(p, fx15.id)))))).filter((d) => d.get('selected') === true).length;
out('[fix 1] 15 convocados de nuevo y 0 respuestas antiguas', left15 === 0 && sel15 === 15, `convocados=${sel15}, respuestas=${left15}`);

// ---------- Cierre (hora del servidor) ----------
console.log('\n--- [C5] Cierre de respuestas (hora del servidor) ---');
const closeAt = Date.now() + 8000;
const fx2 = await newMatch('partido con cierre en 8 s', 'Rival Cierre', closeAt, Date.now() + 3600e3);
await expectOk('convocar al jugador', saveSelections(coach.db, fx2, [player], []));
await expectOk('jugador responde antes del cierre', respond(player, player, 'AVAILABLE', fx2.id));
await sleep(Math.max(0, closeAt - Date.now()) + 1500);
await expectDenied('jugador cambia DESPUÉS del cierre (bloqueado por request.time)', respond(player, player, 'UNAVAILABLE', fx2.id));
await expectDenied('tutor cambia DESPUÉS del cierre', respond(guardian, player, 'UNAVAILABLE', fx2.id));
s = await getDoc(avail(coach.db, player, fx2.id));
out('la respuesta queda fija y la entrenadora la sigue leyendo', s.get('status') === 'AVAILABLE', s.get('status'));
await expectDenied('la entrenadora no puede reabrir el plazo (editar tras el cierre)', setDoc(fx2, { teamId: team, ownerId: coach.uid, category: 'SUB-12', opponent: 'Rival Cierre', kickoff: Timestamp.fromMillis(Date.now() + 3600e3), responseDeadline: Timestamp.fromMillis(Date.now() + 1800e3), updatedAt: serverTimestamp() }));

console.log('\n--- Cambio de cierre antes de que pase ---');
const k3 = Timestamp.fromMillis(Date.now() + 5 * 3600e3);
const fx3 = await newMatch('partido con cierre en 2 h', 'Rival 3', Date.now() + 2 * 3600e3, k3.toMillis());
await expectOk('convocar y responder', saveSelections(coach.db, fx3, [player], []).then(() => respond(player, player, 'AVAILABLE', fx3.id)));
const closeSoon = Date.now() + 6000;
const enc3 = { opponent: 'Rival 3', category: 'SUB-12', teamName: 'Equipo Valido', kickoff: k3, responseDeadline: Timestamp.fromMillis(closeSoon) };
await expectOk('entrenadora adelanta el cierre (partido + encounter en batch, como la app)', (async () => {
  const b = writeBatch(coach.db);
  b.set(fx3, { teamId: team, ownerId: coach.uid, category: 'SUB-12', opponent: 'Rival 3', kickoff: k3, responseDeadline: Timestamp.fromMillis(closeSoon), updatedAt: serverTimestamp() });
  b.update(doc(coach.db, 'matchCallupPlayers', selId(player, fx3.id)), { encounter: enc3, updatedAt: serverTimestamp() });
  await b.commit();
})());
await sleep(Math.max(0, closeSoon - Date.now()) + 1500);
await expectDenied('tras el nuevo cierre, la respuesta queda bloqueada', respond(player, player, 'UNAVAILABLE', fx3.id));
s = await getDoc(avail(coach.db, player, fx3.id));
out('la respuesta dada antes sigue guardada', s.get('status') === 'AVAILABLE', s.get('status'));

// ---------- Cancelar el partido ----------
console.log('\n--- Partido cancelado ---');
await expectOk('cancelar: borrar respuestas + convocatorias + partido (MatchCleanup)', (async () => {
  const b = writeBatch(coach.db);
  for (const p of [player, teammate, silent]) {
    const r = await getDoc(avail(coach.db, p));
    if (r.exists()) b.delete(r.ref);
    b.delete(doc(coach.db, 'matchCallupPlayers', selId(p, fx.id)));
  }
  await b.commit();
  await deleteDoc(fx);
})());
await expectDenied('responder a un partido cancelado', respond(player, player, 'AVAILABLE'));
const rc = await existsLabel(getDoc(avail(player.db, player)));
out('el listener del jugador sobre la respuesta borrada → permission-denied (la tarjeta desaparece)', rc === 'permission-denied', rc);

console.log(failures === 0 ? `\nUS10 client flow verified in demo-sportpro only: ${count} de ${count} OK.` : `\n${failures} de ${count} comprobaciones fallidas.`);
process.exit(failures === 0 ? 0 : 1);
