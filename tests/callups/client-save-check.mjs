// US-09: "Guardar convocatoria" con el SDK CLIENTE, igual que CallupRepository.saveSelections (runTransaction).
// Los otros checks escriben por REST y no hacen las lecturas previas de la transacción; este sí:
// lee partido, equipo, la convocatoria de cada jugador (aún inexistente) y su membresía, y luego escribe.
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Requiere el emulador arrancado con firebase.local.json. Sale con código 1 si algo falla.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, runTransaction, setDoc, getDoc, query, where, getDocs,
  serverTimestamp, Timestamp } = require('firebase/firestore');

const project = 'demo-sportpro';
const root = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents`;
const authUrl = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const password = 'SportProLocal2026!';
const run = Date.now().toString(36);
const raw = async (url, method = 'GET', body) => (await fetch(url, { method, headers: { 'Content-Type': 'application/json', Authorization: 'Bearer owner' }, ...(body ? { body: JSON.stringify(body) } : {}) })).json();
const val = (v) => v instanceof Date ? { timestampValue: v.toISOString() } : Array.isArray(v) ? { arrayValue: { values: v.map(val) } }
  : typeof v === 'boolean' ? { booleanValue: v } : typeof v === 'number' ? { integerValue: String(v) } : { stringValue: v };
// "Bearer owner" se salta las reglas: solo para preparar datos.
const seed = (path, data) => raw(`${root}/${path}`, 'PATCH', { fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, val(v)])) });
const signUp = async (email) => (await fetch(`${authUrl}/accounts:signUp?key=local`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password, returnSecureToken: true }) }).then((x) => x.json()));
const signIn = async (email) => (await fetch(`${authUrl}/accounts:signInWithPassword?key=local`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password, returnSecureToken: true }) }).then((x) => x.json()));

// Datos como los de producción: equipo y membresías creados antes (por QR y por invitación), sin convocatorias.
const coachEmail = `coach-${run}@sportpro.test`;
const coach = (await signUp(coachEmail)).localId;
const p1 = (await signUp(`qr-${run}@sportpro.test`)).localId, p2 = (await signUp(`mijhail-${run}@sportpro.test`)).localId;
await seed(`users/${coach}`, { roles: ['ENTRENADOR'] });
await seed(`coachApprovals/${coach}`, { approved: true });
const team = `equipo-valido-${run}`;
await seed(`teams/${team}`, { createdBy: coach, name: 'Equipo Valido', type: 'EQUIPO', categories: ['SUB-12'], playerCount: 0, createdAt: new Date('2026-11-01') });
await seed(`teamMemberships/${team}_${p1}`, { teamId: team, userId: p1, requestId: `${team}_${p1}`, playerName: 'Jugador QR', category: 'SUB-12', acceptedAt: new Date('2026-11-10') });
await seed(`teamMemberships/${team}_${p2}`, { teamId: team, userId: p2, inviteId: 'inv', playerName: 'Mijhail', category: 'SUB-12', acceptedAt: new Date('2026-11-10') });

const app = initializeApp({ projectId: project, apiKey: 'local' }, run);
const auth = getAuth(app); connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
const db = getFirestore(app); connectFirestoreEmulator(db, '127.0.0.1', 8080);
await signInWithEmailAndPassword(auth, coachEmail, password);

let failures = 0;
const out = (label, ok, detail = '') => { if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${label}${detail ? ': ' + detail : ''}`); };
const expectOk = async (label, fn) => { try { await fn(); out(label, true); } catch (e) { out(label, false, `${e.code ?? ''} ${e.message?.split('\n')[0] ?? e}`); } };

const fixtureRef = doc(collection(db, 'matchFixtures'));
const kickoff = Timestamp.fromMillis(Date.now() + 36 * 3600e3), deadline = Timestamp.fromMillis(Date.now() + 12 * 3600e3);
await expectOk('US09 client coach registers match (saveMatch)', () => setDoc(fixtureRef, { teamId: team, ownerId: coach, category: 'SUB-12', opponent: 'Prueba US09', kickoff, responseDeadline: deadline, updatedAt: serverTimestamp() }));
await expectOk('US09 client coachSelections listener query', () => getDocs(query(collection(db, 'matchCallupPlayers'), where('ownerId', '==', coach), where('matchId', '==', fixtureRef.id))));
const selRef = (p) => doc(db, 'matchCallupPlayers', `${fixtureRef.id}_${p}`);
const missing = await getDoc(selRef(p1)).then((d) => (d.exists() ? 'existe' : 'no existe'), (e) => e.code);
out('US09 client match owner reads not-yet-existing callup', missing === 'no existe', missing);

// Igual que saveSelections: lee partido, equipo, convocaciones actuales y membresías; luego escribe.
const save = () => runTransaction(db, async (tx) => {
  const encounter = await tx.get(fixtureRef);
  const t = await tx.get(doc(db, 'teams', team));
  for (const p of [p1, p2]) await tx.get(selRef(p));
  for (const p of [p1, p2]) await tx.get(doc(db, 'teamMemberships', `${team}_${p}`));
  const details = { opponent: encounter.get('opponent'), category: 'SUB-12', teamName: t.get('name') ?? '',
    kickoff: encounter.get('kickoff'), responseDeadline: encounter.get('responseDeadline') ?? encounter.get('kickoff') };
  for (const p of [p1, p2]) tx.set(selRef(p), { matchId: fixtureRef.id, teamId: team, ownerId: coach, playerId: p, selected: true, encounter: details, updatedAt: serverTimestamp() });
});
await expectOk('US09 client first "Guardar convocatoria" transaction (new players)', save);
await expectOk('US09 client second save when callups already exist', save);

// Nadie más puede comprobar si una convocatoria inexistente existe.
const ghost = `${fixtureRef.id}_nadie${run}`;
const other = (await signUp(`otra-${run}@sportpro.test`)).idToken;
const playerToken = (await signIn(`qr-${run}@sportpro.test`)).idToken;
for (const [label, token] of [['another user', other], ['called player', playerToken]]) {
  const r = await fetch(`${root}/matchCallupPlayers/${ghost}`, { headers: { Authorization: `Bearer ${token}` } });
  out(`US09 client ${label} cannot probe a missing callup`, r.status === 403, `HTTP ${r.status}`);
}

console.log(failures === 0 ? 'US09 client transaction verified in demo-sportpro only.' : `${failures} client check(s) failed.`);
process.exit(failures === 0 ? 0 : 1);
