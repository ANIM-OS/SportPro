// Parte 2 (partidos unificados) con el SDK CLIENTE: cada lectura que hacen la pestaña Partidos y el
// detalle del partido (Info · Convocatoria · Alineación · LIVE) para cada rol, con listeners y con
// documentos que AÚN NO EXISTEN. No hay reglas nuevas: se comprueba que las pantallas nuevas solo
// usan lecturas que las reglas ya permiten (lección de US-09) y que las ajenas siguen denegadas.
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Requiere el emulador arrancado con firebase.local.json. Sale con código 1 si algo falla.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, runTransaction, setDoc, getDoc, getDocs, getDocsFromServer, query, where,
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

const coach = await user('entrenadora', ['ENTRENADOR']);
const otherCoach = await user('otra-entrenadora', ['ENTRENADOR']);
const p1 = await user('titular', ['JUGADOR']);
const p2 = await user('suplente', ['JUGADOR']);
const p3 = await user('retirado', ['JUGADOR']);
const notCalled = await user('no-convocado', ['JUGADOR']);
const otherCat = await user('otra-categoria', ['JUGADOR']);
const guardian = await user('tutora', ['PADRE / TUTOR']);
const admin = await user('admin', []);
for (const c of [coach, otherCoach]) await seed(`coachApprovals/${c.uid}`, { approved: true });
await seed(`admins/${admin.uid}`, { since: new Date() });
const team = `eq-${run}`;
const teamName = 'Equipo Valido';
await seed(`teams/${team}`, { createdBy: coach.uid, name: teamName, type: 'EQUIPO', categories: ['SUB-12', 'SUB-14'] });
for (const p of [p1, p2, p3, notCalled]) await seed(`teamMemberships/${team}_${p.uid}`, { teamId: team, userId: p.uid, playerName: p.name, category: 'SUB-12' });
await seed(`teamMemberships/${team}_${otherCat.uid}`, { teamId: team, userId: otherCat.uid, playerName: otherCat.name, category: 'SUB-14' });
await seed(`guardianLinks/${p1.uid}_${guardian.uid}`, { playerId: p1.uid, guardianId: guardian.uid, inviteId: 'x', status: 'ACTIVE' });

let count = 0, failures = 0;
const out = (label, ok, detail = '') => { count++; if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${count}. ${label}${detail ? ': ' + detail : ''}`); };
const code = (p) => p.then(() => 'OK', (e) => e.code ?? e.message ?? String(e));
const expectOk = async (label, p) => { const c = await code(p); out(label, c === 'OK', c); };
const once = (ref) => new Promise((resolve) => { const stop = onSnapshot(ref, (s) => { stop(); resolve(s.docs ? `${s.size} docs` : s.exists() ? 'existe' : 'no existe'); }, (e) => resolve(e.code)); });
const check = async (label, ref, want) => { const r = await once(ref); out(`${label} → ${want}`, want instanceof RegExp ? want.test(r) : r === want, r); };
const DOCS = /^\d+ docs$/;

// ---------- Réplicas de la app ----------
const fx = doc(collection(coach.db, 'matchFixtures'));
const m = fx.id;
const sel = (p) => `${m}_${p.uid}`;
function saveSelections(additions, withdrawals) { // CallupRepository.saveSelections
  return runTransaction(coach.db, async (tx) => {
    const enc = await tx.get(doc(coach.db, 'matchFixtures', m));
    const t = await tx.get(doc(coach.db, 'teams', team));
    for (const p of [...additions, ...withdrawals]) await tx.get(doc(coach.db, 'matchCallupPlayers', sel(p)));
    for (const p of additions) await tx.get(doc(coach.db, 'teamMemberships', `${team}_${p.uid}`));
    const assignments = new Map();
    for (const p of withdrawals) assignments.set(p.uid, await tx.get(doc(coach.db, 'matchLineups', m, 'players', p.uid)));
    const responses = new Map();
    for (const p of withdrawals) responses.set(p.uid, await tx.get(doc(coach.db, 'matchAvailability', sel(p))));
    const details = { opponent: enc.get('opponent'), category: 'SUB-12', teamName: t.get('name'), kickoff: enc.get('kickoff'), responseDeadline: enc.get('responseDeadline') };
    for (const p of [...additions, ...withdrawals]) {
      const a = assignments.get(p.uid);
      if (a?.exists()) { if (a.get('kind') === 'STARTER') tx.delete(doc(coach.db, 'matchLineups', m, 'slots', a.get('position'))); tx.delete(a.ref); }
      const r = responses.get(p.uid); if (r?.exists()) tx.delete(r.ref);
      tx.set(doc(coach.db, 'matchCallupPlayers', sel(p)), { matchId: m, teamId: team, ownerId: coach.uid, playerId: p.uid, selected: additions.includes(p), encounter: details, updatedAt: serverTimestamp() });
    }
  });
}
function assign(playerId, kind, position) { // MatchLineupRepository.assign
  return runTransaction(coach.db, async (tx) => {
    const ref = doc(coach.db, 'matchLineups', m);
    const parent = await tx.get(ref);
    const playerRef = doc(ref, 'players', playerId);
    await tx.get(playerRef);
    const target = kind === 'STARTER' ? doc(ref, 'slots', position) : null;
    if (target) await tx.get(target);
    await tx.get(doc(coach.db, 'matchCallupPlayers', `${m}_${playerId}`));
    if (!parent.exists()) tx.set(ref, { teamId: team, formation: '4-3-3', updatedAt: serverTimestamp() });
    if (target) tx.set(target, { playerId, updatedAt: serverTimestamp() });
    tx.set(playerRef, { playerId, kind, position, updatedAt: serverTimestamp() });
  });
}

// ---------- Preparación (lo que hace la entrenadora desde la pestaña Partidos) ----------
console.log('--- Preparación: registrar partido (MatchEditor), convocar, alinear y responder ---');
await expectOk('la dueña registra el partido desde Partidos (MatchEditor → saveMatch)', setDoc(fx, { teamId: team, ownerId: coach.uid, category: 'SUB-12', opponent: 'Rival FC',
  kickoff: Timestamp.fromMillis(Date.now() + 48 * 3600e3), responseDeadline: Timestamp.fromMillis(Date.now() + 24 * 3600e3), updatedAt: serverTimestamp() }));

// ---------- 1. Detalle con convocatoria y alineación AÚN INEXISTENTES ----------
console.log('\n--- 1. Pestañas con documentos AÚN INEXISTENTES ---');
await check('dueña · Convocatoria: coachSelections (ownerId+matchId) sin convocados', query(collection(coach.db, 'matchCallupPlayers'), where('ownerId', '==', coach.uid), where('matchId', '==', m)), '0 docs');
await check('dueña · Convocatoria: coachResponses sin respuestas', query(collection(coach.db, 'matchAvailability'), where('ownerId', '==', coach.uid), where('matchId', '==', m), where('teamId', '==', team)), '0 docs');
await check('dueña · Alineación: documento aún inexistente', doc(coach.db, 'matchLineups', m), 'no existe');
await check('admin · Convocatoria: matchSelections (solo matchId) sin convocados', query(collection(admin.db, 'matchCallupPlayers'), where('matchId', '==', m)), '0 docs');
await check('admin · Convocatoria: matchResponses (solo matchId) sin respuestas', query(collection(admin.db, 'matchAvailability'), where('matchId', '==', m)), '0 docs');
await check('admin · Alineación: players/ aún vacío', collection(admin.db, 'matchLineups', m, 'players'), '0 docs');
await check('dueña · LIVE: catálogo del equipo aún vacío (listenActive)', collection(coach.db, 'teams', team, 'eventTypes'), '0 docs');

await expectOk('convocar titular, suplente y el que luego se retira', saveSelections([p1, p2, p3], []));
await expectOk('alinear: titular de portero (P10) y suplente', assign(p1.uid, 'STARTER', 'P10').then(() => assign(p2.uid, 'BENCH', 'DEF')));
await expectOk('el titular responde Disponible', setDoc(doc(p1.db, 'matchAvailability', sel(p1)), { matchId: m, teamId: team, playerId: p1.uid, ownerId: coach.uid, status: 'AVAILABLE', respondedBy: p1.uid, updatedAt: serverTimestamp() }));
await expectOk('retirar al tercero de la convocatoria', saveSelections([], [p3]));

// ---------- 2. Lista de Partidos por rol ----------
console.log('\n--- 2. Pestaña Partidos: listas por rol ---');
await check('dueña: sus equipos (teams where createdBy)', query(collection(coach.db, 'teams'), where('createdBy', '==', coach.uid)), DOCS);
await check('dueña: partidos del equipo (matchFixtures where teamId)', query(collection(coach.db, 'matchFixtures'), where('teamId', '==', team)), '1 docs');
await check('jugador: sus convocatorias (where playerId)', query(collection(p1.db, 'matchCallupPlayers'), where('playerId', '==', p1.uid)), '1 docs');
await check('tutora: sus vínculos (guardianLinks where guardianId)', query(collection(guardian.db, 'guardianLinks'), where('guardianId', '==', guardian.uid)), '1 docs');
await check('tutora: convocatorias de su jugador (where playerId)', query(collection(guardian.db, 'matchCallupPlayers'), where('playerId', '==', p1.uid)), '1 docs');
out('admin: es admin (admins/{uid}, checkAdmin)', (await getDoc(doc(admin.db, 'admins', admin.uid))).exists());
const allAdmin = await getDocsFromServer(collection(admin.db, 'matchFixtures')).then((s) => s.size, (e) => e.code);
out('admin: TODOS los partidos (allMatches, solo lectura)', typeof allAdmin === 'number' && allAdmin >= 1, String(allAdmin));
for (const u of [coach, otherCoach, p1, guardian])
  await check(`${u.name}: NO puede listar todos los partidos`, collection(u.db, 'matchFixtures'), 'permission-denied');

// ---------- 3. Info ----------
console.log('\n--- 3. Pestaña Info (listener del partido) ---');
for (const [u, want] of [[coach, 'existe'], [p1, 'existe'], [p3, 'existe'], [admin, 'existe'], [guardian, 'permission-denied'],
  [otherCoach, 'permission-denied'], [otherCat, 'permission-denied']])
  await check(`${u.name} lee el partido`, doc(u.db, 'matchFixtures', m), want);
const enc = (await getDoc(doc(guardian.db, 'matchCallupPlayers', sel(p1)))).get('encounter');
out('tutora: Info sale de la copia encounter de la convocatoria (con nombre del equipo)', enc?.teamName === teamName && enc?.opponent === 'Rival FC');
const adminEnc = (await getDocs(query(collection(admin.db, 'matchCallupPlayers'), where('matchId', '==', m)))).docs.map((d) => d.get('encounter')?.teamName).find(Boolean);
out('admin: el nombre del equipo sale de una convocatoria (no lee teams)', adminEnc === teamName, adminEnc);
await check('admin: NO lee el equipo (US-22: sin cambio de reglas)', doc(admin.db, 'teams', team), 'permission-denied');

// ---------- 4. Convocatoria ----------
console.log('\n--- 4. Pestaña Convocatoria ---');
await check('dueña: plantilla con nombres (memberships where teamId)', query(collection(coach.db, 'teamMemberships'), where('teamId', '==', team)), '5 docs');
await check('dueña: coachSelections', query(collection(coach.db, 'matchCallupPlayers'), where('ownerId', '==', coach.uid), where('matchId', '==', m)), '3 docs');
await check('dueña: coachResponses', query(collection(coach.db, 'matchAvailability'), where('ownerId', '==', coach.uid), where('matchId', '==', m), where('teamId', '==', team)), '1 docs');
await check('jugador: su respuesta (PlayerMatchCard)', doc(p1.db, 'matchAvailability', sel(p1)), 'existe');
await check('suplente: su respuesta aún inexistente', doc(p2.db, 'matchAvailability', sel(p2)), 'no existe');
await check('tutora: la respuesta de su jugador (GuardianCallupCard)', doc(guardian.db, 'matchAvailability', sel(p1)), 'existe');
await check('admin: convocatorias del partido (conteos)', query(collection(admin.db, 'matchCallupPlayers'), where('matchId', '==', m)), '3 docs');
await check('admin: respuestas del partido (conteos)', query(collection(admin.db, 'matchAvailability'), where('matchId', '==', m)), '1 docs');
await check('admin: NO lee la plantilla (nombres de menores)', query(collection(admin.db, 'teamMemberships'), where('teamId', '==', team)), 'permission-denied');
for (const u of [otherCoach, p1, notCalled, guardian])
  await check(`${u.name}: NO lee las convocatorias ajenas por partido (matchSelections)`, query(collection(u.db, 'matchCallupPlayers'), where('matchId', '==', m)), 'permission-denied');
await check('otra entrenadora: NO lee respuestas ajenas', query(collection(otherCoach.db, 'matchAvailability'), where('matchId', '==', m)), 'permission-denied');

// ---------- 5. Alineación ----------
console.log('\n--- 5. Pestaña Alineación ---');
await check('dueña: players/', collection(coach.db, 'matchLineups', m, 'players'), '2 docs');
await check('jugador convocado: players/', collection(p1.db, 'matchLineups', m, 'players'), '2 docs');
await check('jugador convocado: plantilla para nombres', query(collection(p1.db, 'teamMemberships'), where('teamId', '==', team)), '5 docs');
await check('admin (modo anónimo): documento y players/', collection(admin.db, 'matchLineups', m, 'players'), '2 docs');
await check('tutora: antes de guardar su acceso → denegado', collection(guardian.db, 'matchLineups', m, 'players'), 'permission-denied');
await expectOk('tutora: la pestaña guarda su acceso (openAsGuardian)', setDoc(doc(guardian.db, 'matchLineups', m, 'guardians', guardian.uid), { playerId: p1.uid, updatedAt: serverTimestamp() }));
await check('tutora: después lee players/', collection(guardian.db, 'matchLineups', m, 'players'), '2 docs');
await check('tutora: NO lee la plantilla (ve "Compañero")', query(collection(guardian.db, 'teamMemberships'), where('teamId', '==', team)), 'permission-denied');
await check('otra entrenadora: NO lee la alineación', collection(otherCoach.db, 'matchLineups', m, 'players'), 'permission-denied');

// ---------- 6. LIVE ----------
console.log('\n--- 6. Pestaña LIVE (catálogo del equipo y convocados para elegir jugador) ---');
await seed(`eventCatalog/gol`, { name: 'Gol', description: '', requiredCore: [], extraKeys: [], extraFields: [], requiredExtra: [], active: true, base: true, order: 10 });
await expectOk('la dueña guarda un override de un general en su equipo', setDoc(doc(coach.db, 'teams', team, 'eventTypes', 'gol'), { kind: 'OVERRIDE', active: true, updatedBy: coach.uid, updatedAt: serverTimestamp() }));
await check('dueña: catálogo del equipo', collection(coach.db, 'teams', team, 'eventTypes'), '1 docs');
await check('jugador miembro: catálogo del equipo', collection(p1.db, 'teams', team, 'eventTypes'), '1 docs');
await check('admin: catálogo del equipo', collection(admin.db, 'teams', team, 'eventTypes'), '1 docs');
await check('tutora: catálogo del equipo denegado (la app usa el global; no muestra registro)', collection(guardian.db, 'teams', team, 'eventTypes'), 'permission-denied');
await check('todos: catálogo global', collection(guardian.db, 'eventCatalog'), DOCS);
const options = (await getDocs(query(collection(coach.db, 'matchCallupPlayers'), where('ownerId', '==', coach.uid), where('matchId', '==', m)))).docs
  .filter((d) => d.get('selected')).map((d) => d.get('playerId'));
out('dueña: los convocados para elegir jugador excluyen al retirado', options.length === 2 && !options.includes(p3.uid), `${options.length} convocados`);

// ---------- 7. Partido cancelado ----------
console.log('\n--- 7. Partido cancelado (MatchCleanup) ---');
await expectOk('la dueña cancela (alineación, accesos, respuestas, convocatorias y partido)', (async () => {
  // Igual que MatchCleanup: cada jugador junto con su casilla (las reglas lo exigen), luego casillas
  // sueltas, accesos de tutores y el documento de la alineación.
  const lp = await getDocs(collection(coach.db, 'matchLineups', m, 'players'));
  if (!lp.empty) {
    const b0 = writeBatch(coach.db);
    lp.docs.forEach((d) => { if (d.get('kind') === 'STARTER') b0.delete(doc(coach.db, 'matchLineups', m, 'slots', d.get('position'))); b0.delete(d.ref); });
    await b0.commit();
  }
  for (const sub of ['slots', 'guardians']) for (const d of (await getDocs(collection(coach.db, 'matchLineups', m, sub))).docs) await deleteDoc(d.ref);
  await deleteDoc(doc(coach.db, 'matchLineups', m));
  const sels = await getDocs(query(collection(coach.db, 'matchCallupPlayers'), where('ownerId', '==', coach.uid), where('matchId', '==', m)));
  const b = writeBatch(coach.db);
  for (const s of sels.docs) { const r = await getDoc(doc(coach.db, 'matchAvailability', s.id)); if (r.exists()) b.delete(r.ref); b.delete(s.ref); }
  await b.commit();
  await deleteDoc(fx);
})());
for (const u of [p1, guardian, admin, coach]) await check(`${u.name}: el partido ya no existe (Info → "Partido cancelado")`, doc(u.db, 'matchFixtures', m), 'no existe');

console.log(failures === 0 ? `\nParte 2 client reads verified in demo-sportpro only: ${count} de ${count} OK.` : `\n${failures} de ${count} comprobaciones fallidas.`);
process.exit(failures === 0 ? 0 : 1);
