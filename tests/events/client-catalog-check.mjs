// US-12 (parte 1) con el SDK CLIENTE (como la app): catálogo global eventCatalog (solo admin) y del
// equipo teams/{teamId}/eventTypes (entrenadora dueña aprobada). Replica
// EventCatalogFirestoreRepository: listeners (también con el catálogo AÚN INEXISTENTE), siembra de
// los 9 tipos base, tipos propios, desactivar en el equipo un tipo global, cambios en vivo, modo sin
// conexión (caché y respaldo) y la cascada al eliminar el equipo (US-03).
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Requiere el emulador arrancado con firebase.local.json. Sale con código 1 si algo falla.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, setDoc, getDoc, getDocs, getDocsFromServer, getDocsFromCache,
  updateDoc, deleteDoc, onSnapshot, writeBatch, serverTimestamp, disableNetwork, enableNetwork, Timestamp } = require('firebase/firestore');

const project = 'demo-sportpro';
const root = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents`;
const authUrl = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const password = 'SportProLocal2026!';
const run = Date.now().toString(36);
const J = { 'Content-Type': 'application/json' };
// "Bearer owner" se salta las reglas: solo para preparar datos y revisar huérfanos.
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
function client(name) {
  const app = initializeApp({ projectId: project, apiKey: 'local' }, `${name}-${run}`);
  const auth = getAuth(app); connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  const db = getFirestore(app); connectFirestoreEmulator(db, '127.0.0.1', 8080);
  return { auth, db };
}
async function user(name, roles, verified = true) {
  const a = await account(name, roles, verified);
  const c = client(name);
  await signInWithEmailAndPassword(c.auth, a.email, password);
  return { ...a, db: c.db };
}

// El catálogo global es compartido en el emulador: se vacía al empezar (solo eventCatalog) para
// probar de verdad "AÚN INEXISTENTE" aunque el script se haya ejecutado antes.
for (const d of (await raw(`${root}/eventCatalog?pageSize=300`)).documents ?? [])
  await fetch(`http://127.0.0.1:8080/v1/${d.name}`, { method: 'DELETE', headers: { Authorization: 'Bearer owner' } });

const admin = await user('admin', []);
const coach = await user('entrenadora', ['ENTRENADOR']);
const pending = await user('entrenadora-pendiente', ['ENTRENADOR']);
const otherCoach = await user('otra-entrenadora', ['ENTRENADOR']);
const player = await user('jugador', ['JUGADOR']);
const outsider = await user('jugador-ajeno', ['JUGADOR']);
const guardian = await user('tutor', ['PADRE / TUTOR']);
const anon = { db: client('sin-sesion').db, name: 'sin sesión' };
await seed(`admins/${admin.uid}`, { since: new Date() });
for (const c of [coach, otherCoach]) await seed(`coachApprovals/${c.uid}`, { approved: true });
const team = `eq-${run}`, team2 = `eq2-${run}`, academy = `ac-${run}`;
await seed(`teams/${team}`, { createdBy: coach.uid, name: 'Equipo Valido', type: 'EQUIPO', categories: ['SUB-12'] });
await seed(`teams/${team2}`, { createdBy: pending.uid, name: 'Equipo Pendiente', type: 'EQUIPO', categories: ['SUB-12'] });
await seed(`teams/${academy}`, { createdBy: coach.uid, name: 'Academia', type: 'ACADEMIA', categories: ['SUB-12'] });
await seed(`teamMemberships/${team}_${player.uid}`, { teamId: team, userId: player.uid, playerName: 'Jugador', category: 'SUB-12' });
await seed(`guardianLinks/${player.uid}_${guardian.uid}`, { playerId: player.uid, guardianId: guardian.uid, inviteId: 'x', status: 'ACTIVE' });

let count = 0, failures = 0;
const out = (label, ok, detail = '') => { count++; if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${count}. ${label}${detail ? ': ' + detail : ''}`); };
const code = (p) => p.then(() => 'OK', (e) => e.code ?? e.message ?? String(e));
const expectOk = async (label, p) => { const c = await code(p); out(label, c === 'OK', c); };
const expectDenied = async (label, p) => { const c = await code(p); out(label, c === 'permission-denied', c); };
const once = (ref) => new Promise((resolve) => { const stop = onSnapshot(ref, (s) => { stop(); resolve(s.docs ? `${s.size} docs` : s.exists() ? 'existe' : 'no existe'); }, (e) => resolve(e.code)); });

// ---------- Réplica de la app (EventCatalogRules + EventCatalogFirestoreRepository) ----------
const BASE = [
  ['gol', 'Gol', 10, []], ['falta', 'Falta', 20, []],
  ['penal', 'Penal', 30, [{ key: 'resultado', label: 'Resultado', kind: 'choice', options: ['Gol', 'Fallado', 'Atajado'], required: false }]],
  ['tarjeta', 'Tarjeta', 40, [{ key: 'color', label: 'Color', kind: 'choice', options: ['Amarilla', 'Roja'], required: true }]],
  ['fuera_de_juego', 'Fuera de juego', 50, []],
  ['cambio', 'Cambio', 60, [{ key: 'sale', label: 'Sale', kind: 'text', options: [], required: false }, { key: 'entra', label: 'Entra', kind: 'text', options: [], required: false }]],
  ['saque_lateral', 'Saque lateral', 70, []], ['saque_de_meta', 'Saque de meta', 80, []], ['tiro_de_esquina', 'Tiro de esquina', 90, []],
].map(([id, name, order, extraFields]) => ({ id, name, order, extraFields, requiredCore: [], active: true, base: true }));
const BASE_IDS = BASE.map((t) => t.id);
const fields = (u, t) => ({
  name: t.name, description: t.description ?? '', requiredCore: t.requiredCore ?? [],
  extraKeys: (t.extraFields ?? []).map((f) => f.key), extraFields: t.extraFields ?? [],
  requiredExtra: (t.extraFields ?? []).filter((f) => f.required).map((f) => f.key),
  active: t.active ?? true, order: t.order ?? 1000, updatedBy: u.uid, updatedAt: serverTimestamp(),
});
const G = (u, id) => doc(u.db, 'eventCatalog', id);
const T = (u, teamId, id) => doc(u.db, 'teams', teamId, 'eventTypes', id);
const saveGlobal = (u, t, base = false) => setDoc(G(u, t.id), { ...fields(u, t), base });
const saveCustom = (u, teamId, t) => setDoc(T(u, teamId, t.id), { ...fields(u, t), kind: 'CUSTOM' });
const setTeamUsesGlobal = (u, teamId, id, active) => setDoc(T(u, teamId, id), { kind: 'OVERRIDE', active, updatedBy: u.uid, updatedAt: serverTimestamp() });
async function seedMissingBase(u) {
  const existing = new Set((await getDocsFromServer(collection(u.db, 'eventCatalog'))).docs.map((d) => d.id));
  const missing = BASE.filter((t) => !existing.has(t.id));
  if (missing.length === 0) return 0;
  const b = writeBatch(u.db);
  for (const t of missing) b.set(G(u, t.id), { ...fields(u, t), base: true });
  await b.commit();
  return missing.length;
}
// EventCatalogRules.activeTypes (respaldo: si no hay globales, los 9 base)
const activeTypes = (globals, overrides, customs) => [...(globals.length ? globals : BASE).filter((g) => g.active && overrides[g.id] !== false), ...customs.filter((c) => c.active)]
  .sort((a, b) => (a.order ?? 1000) - (b.order ?? 1000) || a.name.localeCompare(b.name)).map((t) => t.id);
async function readActive(u, teamId) {
  const globals = (await getDocs(collection(u.db, 'eventCatalog'))).docs.map((d) => ({ id: d.id, ...d.data() }));
  const teamDocs = teamId ? (await getDocs(collection(u.db, 'teams', teamId, 'eventTypes'))).docs : [];
  const overrides = Object.fromEntries(teamDocs.filter((d) => d.get('kind') === 'OVERRIDE').map((d) => [d.id, d.get('active')]));
  const customs = teamDocs.filter((d) => d.get('kind') === 'CUSTOM').map((d) => ({ id: d.id, ...d.data() }));
  return activeTypes(globals, overrides, customs);
}

// ---------- 1. Catálogo AÚN INEXISTENTE ----------
console.log('--- 1. Listeners con el catálogo AÚN INEXISTENTE ---');
for (const [u, want] of [[coach, '0 docs'], [player, '0 docs'], [guardian, '0 docs'], [admin, '0 docs'], [anon, 'permission-denied']]) {
  const r = await once(collection(u.db, 'eventCatalog'));
  out(`global: onSnapshot de ${u.name} → ${want}`, r === want, r);
}
for (const [u, want] of [[coach, '0 docs'], [player, '0 docs'], [admin, '0 docs'], [otherCoach, 'permission-denied'], [outsider, 'permission-denied'], [guardian, 'permission-denied']]) {
  const r = await once(collection(u.db, 'teams', team, 'eventTypes'));
  out(`equipo: onSnapshot de ${u.name} → ${want}`, r === want, r);
}
out('equipo: getDoc de un override inexistente (dueña) → no existe', (await getDoc(T(coach, team, 'gol')).then((d) => d.exists() ? 'existe' : 'no existe', (e) => e.code)) === 'no existe');
out('respaldo: sin catálogo global se ofrecen los 9 base del código', JSON.stringify(await readActive(coach, team)) === JSON.stringify(BASE_IDS));
await expectDenied('la dueña no puede desactivar un global que aún no existe (override sin global)', setTeamUsesGlobal(coach, team, 'gol', false));

// ---------- 2. Siembra ----------
console.log('\n--- 2. Siembra de los 9 tipos base ---');
await expectDenied('la entrenadora no puede sembrar', seedMissingBase(coach));
const seeded = await seedMissingBase(admin).catch((e) => e.code);
out('el admin crea los 9 tipos base (batch solo con los que faltan)', seeded === 9, String(seeded));
await expectOk('el admin cambia el nombre de "gol"', updateDoc(G(admin, 'gol'), { name: 'Gol anotado', updatedBy: admin.uid, updatedAt: serverTimestamp() }));
const again = await seedMissingBase(admin).catch((e) => e.code);
out('sembrar otra vez no crea nada ni pisa el cambio del admin', again === 0 && (await getDoc(G(admin, 'gol'))).get('name') === 'Gol anotado', `creados=${again}`);
const tarjeta = (await getDoc(G(player, 'tarjeta'))).data();
out('"tarjeta" exige el dato Color (requiredExtra = [color]); sale/entra y resultado no', JSON.stringify(tarjeta.requiredExtra) === '["color"]'
  && (await getDoc(G(player, 'cambio'))).get('requiredExtra').length === 0 && (await getDoc(G(player, 'penal'))).get('requiredExtra').length === 0);
await expectDenied('nadie convierte "gol" en tipo no base', saveGlobal(admin, { id: 'gol', name: 'Gol' }, false));
await expectDenied('un tipo nuevo no puede marcarse como base', saveGlobal(admin, { id: 'gol_olimpico', name: 'Gol olímpico' }, true));

// ---------- 3. Quién escribe ----------
console.log('\n--- 3. Quién crea, edita y desactiva ---');
await expectOk('global: el admin crea "Lesión"', saveGlobal(admin, { id: 'lesion', name: 'Lesión', requiredCore: ['minute', 'playerId'], order: 1000 }));
for (const u of [coach, pending, otherCoach, player, guardian]) {
  await expectDenied(`global: ${u.name} no puede crear`, saveGlobal(u, { id: `x_${u.name.replace(/-/g, '_')}`, name: 'X' }));
  await expectDenied(`global: ${u.name} no puede desactivar "gol"`, updateDoc(G(u, 'gol'), { active: false, updatedBy: u.uid, updatedAt: serverTimestamp() }));
}
await expectOk('equipo: la dueña aprobada crea un tipo propio "eq_penal_ejecutado"', saveCustom(coach, team, { id: 'eq_penal_ejecutado', name: 'Penal ejecutado',
  extraFields: [{ key: 'arquero', label: 'Arquero', kind: 'text', options: [], required: true }] }));
await expectOk('equipo: la dueña desactiva en su equipo el global "saque_lateral"', setTeamUsesGlobal(coach, team, 'saque_lateral', false));
await expectOk('equipo: la dueña edita su tipo propio', saveCustom(coach, team, { id: 'eq_penal_ejecutado', name: 'Penal ejecutado', description: 'Tras falta en el área',
  extraFields: [{ key: 'arquero', label: 'Arquero', kind: 'text', options: [], required: true }] }));
await expectDenied('equipo: la entrenadora PENDIENTE no puede, ni siendo dueña de su equipo', saveCustom(pending, team2, { id: 'eq_x', name: 'X' }));
await expectDenied('equipo: la entrenadora PENDIENTE no puede desactivar un global en su equipo', setTeamUsesGlobal(pending, team2, 'gol', false));
for (const u of [otherCoach, player, guardian, admin]) {
  await expectDenied(`equipo: ${u.name} no puede crear tipos propios`, saveCustom(u, team, { id: 'eq_intruso', name: 'Intruso' }));
  await expectDenied(`equipo: ${u.name} no puede desactivar un global`, setTeamUsesGlobal(u, team, 'falta', false));
}

// ---------- 4. Validaciones ----------
console.log('\n--- 4. Validaciones ---');
for (const [label, run] of [
  ['ID con mayúsculas', () => saveGlobal(admin, { id: 'Penalti', name: 'P' })],
  ['ID con espacios', () => saveGlobal(admin, { id: 'mi tipo', name: 'P' })],
  ['ID de más de 40 caracteres', () => saveGlobal(admin, { id: 'a'.repeat(41), name: 'P' })],
  ['global con prefijo eq_', () => saveGlobal(admin, { id: 'eq_global', name: 'P' })],
  ['propio sin prefijo eq_', () => saveCustom(coach, team, { id: 'penalti', name: 'P' })],
  ['campo de más', () => setDoc(G(admin, 'extra_campo'), { ...fields(admin, { name: 'P' }), base: false, color: 'rojo' })],
  ['requiredCore fuera de la lista ("arbitro")', () => saveGlobal(admin, { id: 'mal_core', name: 'P', requiredCore: ['minute', 'arbitro'] })],
  ['requiredExtra que no está en extraKeys', () => setDoc(G(admin, 'mal_req'), { ...fields(admin, { name: 'P' }), base: false, requiredExtra: ['color'] })],
  ['extraFields de distinto tamaño que extraKeys', () => setDoc(G(admin, 'mal_size'), { ...fields(admin, { name: 'P' }), base: false, extraKeys: ['color'] })],
  ['clave extra inválida ("Color")', () => saveGlobal(admin, { id: 'mal_key', name: 'P', extraFields: [{ key: 'Color', label: 'Color', kind: 'text', options: [], required: false }] })],
  ['claves extra repetidas', () => saveGlobal(admin, { id: 'mal_dup', name: 'P', extraFields: [{ key: 'dato', label: 'A', kind: 'text', options: [], required: false }, { key: 'dato', label: 'B', kind: 'text', options: [], required: false }] })],
  ['nombre vacío', () => saveGlobal(admin, { id: 'sin_nombre', name: '' })],
  ['updatedBy de otra persona', () => setDoc(G(admin, 'mal_actor'), { ...fields(admin, { name: 'P' }), base: false, updatedBy: coach.uid })],
  ['updatedAt del celular', () => setDoc(G(admin, 'mal_fecha'), { ...fields(admin, { name: 'P' }), base: false, updatedAt: Timestamp.now() })],
  ['borrar un tipo base', () => deleteDoc(G(admin, 'gol'))],
  ['borrar un global no base', () => deleteDoc(G(admin, 'lesion'))],
  ['override de un global inexistente', () => setTeamUsesGlobal(coach, team, 'no_existe', false)],
  ['tipo propio en una ACADEMIA', () => saveCustom(coach, academy, { id: 'eq_academia', name: 'P' })],
  ['convertir un tipo propio en override', () => setDoc(T(coach, team, 'eq_penal_ejecutado'), { kind: 'OVERRIDE', active: false, updatedBy: coach.uid, updatedAt: serverTimestamp() })],
]) await expectDenied(`rechaza: ${label}`, run());

// ---------- 5. Combinación ----------
console.log('\n--- 5. Tipos activos del equipo = globales activos + propios activos - desactivados por el equipo ---');
await expectOk('el admin desactiva "fuera_de_juego" para todos', updateDoc(G(admin, 'fuera_de_juego'), { active: false, updatedBy: admin.uid, updatedAt: serverTimestamp() }));
const expected = ['gol', 'falta', 'penal', 'tarjeta', 'cambio', 'saque_de_meta', 'tiro_de_esquina', 'lesion', 'eq_penal_ejecutado'];
let act = await readActive(player, team);
out('el jugador miembro calcula la misma lista que la entrenadora', JSON.stringify(act) === JSON.stringify(expected), act.join(','));
out('otro equipo sin overrides sigue viendo "saque_lateral" (los globales activos)', (await readActive(otherCoach, null)).includes('saque_lateral'));
await expectOk('la dueña reactiva "fuera_de_juego" en su equipo (override active:true)', setTeamUsesGlobal(coach, team, 'fuera_de_juego', true));
out('…pero no vuelve: el admin lo desactivó para todos', !(await readActive(coach, team)).includes('fuera_de_juego'));
await expectOk('la dueña vuelve a usar "saque_lateral"', setTeamUsesGlobal(coach, team, 'saque_lateral', true));
out('"saque_lateral" vuelve a ofrecerse en su equipo', (await readActive(coach, team)).includes('saque_lateral'));

// ---------- 6. Cambios en vivo ----------
console.log('\n--- 6. Cambios en vivo en otro celular ---');
const seenTeam = [], seenGlobal = [];
const stopT = onSnapshot(collection(player.db, 'teams', team, 'eventTypes'), (s) => seenTeam.push(s.docs.map((d) => `${d.id}:${d.get('active')}`).sort().join('|')), (e) => seenTeam.push(e.code));
const stopG = onSnapshot(collection(player.db, 'eventCatalog'), (s) => seenGlobal.push(s.docs.find((d) => d.id === 'lesion')?.get('active')), (e) => seenGlobal.push(e.code));
await sleep(800);
await saveCustom(coach, team, { id: 'eq_mano', name: 'Mano' });
await updateDoc(T(coach, team, 'eq_mano'), { active: false, updatedBy: coach.uid, updatedAt: serverTimestamp() });
await updateDoc(G(admin, 'lesion'), { active: false, updatedBy: admin.uid, updatedAt: serverTimestamp() });
await sleep(1500);
out('el jugador ve en vivo el tipo propio nuevo y su desactivación', seenTeam.some((x) => x.includes('eq_mano:true')) && seenTeam.at(-1).includes('eq_mano:false'), seenTeam.at(-1));
out('el jugador ve en vivo la desactivación global de "Lesión"', seenGlobal.at(-1) === false, String(seenGlobal.at(-1)));

// ---------- 7. Sin conexión ----------
console.log('\n--- 7. Sin conexión ---');
await disableNetwork(player.db);
const cached = await getDocsFromCache(collection(player.db, 'eventCatalog')).then((s) => s.size, (e) => e.code);
out('con el listener abierto, el catálogo global se lee desde la caché sin conexión', cached === 10, String(cached));
const cachedTeam = await getDocsFromCache(collection(player.db, 'teams', team, 'eventTypes')).then((s) => s.size, (e) => e.code);
out('y el del equipo también', typeof cachedTeam === 'number' && cachedTeam >= 3, String(cachedTeam));
await enableNetwork(player.db);
stopT(); stopG();
const fresh = await user('celular-nuevo', ['JUGADOR']);
await disableNetwork(fresh.db);
const empty = await getDocsFromCache(collection(fresh.db, 'eventCatalog')).then((s) => s.size, (e) => e.code);
out('celular nuevo sin caché ni conexión: no hay catálogo → la app usa los 9 base', (empty === 0 || typeof empty === 'string')
  && JSON.stringify(activeTypes([], {}, [])) === JSON.stringify(BASE_IDS), `caché=${empty}`);
await enableNetwork(fresh.db);
await disableNetwork(coach.db);
const states = [];
const stopW = onSnapshot(T(coach, team, 'eq_offline'), { includeMetadataChanges: true }, (s) => states.push(s.exists() ? (s.metadata.hasPendingWrites ? 'pendiente' : 'confirmado') : 'no existe'));
const write = saveCustom(coach, team, { id: 'eq_offline', name: 'Creado sin conexión' });
await sleep(800);
out('la dueña crea un tipo sin conexión: queda en la caché como pendiente', states.includes('pendiente'), states.join('→'));
await enableNetwork(coach.db);
await expectOk('al reconectar se envía y el servidor lo acepta', write);
await sleep(800); stopW();
out('el listener pasa a confirmado', states.at(-1) === 'confirmado', states.join('→'));
out('otro usuario lo ve en el servidor', (await getDocsFromServer(collection(player.db, 'teams', team, 'eventTypes'))).docs.some((d) => d.id === 'eq_offline'));

// ---------- 8. Cascada US-03 ----------
console.log('\n--- 8. Eliminar el equipo (US-03) ---');
await expectDenied('otra entrenadora no puede borrar el catálogo del equipo', deleteDoc(T(otherCoach, team, 'eq_mano')));
await expectOk('la dueña borra el catálogo (de 10 en 10) y después el equipo', (async () => {
  for (;;) {
    const s = await getDocsFromServer(collection(coach.db, 'teams', team, 'eventTypes'));
    if (s.empty) break;
    const b = writeBatch(coach.db); s.docs.slice(0, 10).forEach((d) => b.delete(d.ref)); await b.commit();
  }
  await deleteDoc(doc(coach.db, 'teams', team));
})());
const orphans = await raw(`${root}/teams/${team}/eventTypes`);
out('no quedan tipos huérfanos del equipo eliminado', !orphans.documents || orphans.documents.length === 0);
out('el catálogo global sigue intacto (10 tipos)', (await getDocsFromServer(collection(player.db, 'eventCatalog'))).size === 10);

console.log(failures === 0 ? `\nUS12 catalog client flow verified in demo-sportpro only: ${count} de ${count} OK.` : `\n${failures} de ${count} comprobaciones fallidas.`);
process.exit(failures === 0 ? 0 : 1);
