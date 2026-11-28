// US-20 con el SDK CLIENTE: avisos de pruebas o convocatorias abiertas (tryoutNotices). Replica
// TryoutRepository: crear con el nombre del perfil, leer vigentes (status == ACTIVE) y "Mis avisos"
// (authorId == uid), y cancelar (autor, admin o moderador) guardando cancelledBy.
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Límite de 10 get()/exists(): crear usa users y coachApprovals (2); leer/cancelar, admins y moderators (2).
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, query, where, addDoc, getDoc, getDocs,
  updateDoc, deleteDoc, serverTimestamp, Timestamp, deleteField } = require('firebase/firestore');

const project = 'demo-sportpro';
const root = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents`;
const authUrl = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const password = 'SportProLocal2026!';
const run = Date.now().toString(36);
const J = { 'Content-Type': 'application/json' };
const raw = async (url, method = 'GET', body) => (await fetch(url, { method, headers: { ...J, Authorization: 'Bearer owner' }, ...(body ? { body: JSON.stringify(body) } : {}) })).json();
const val = (v) => v instanceof Date ? { timestampValue: v.toISOString() } : Array.isArray(v) ? { arrayValue: { values: v.map(val) } }
  : typeof v === 'boolean' ? { booleanValue: v } : typeof v === 'number' ? { integerValue: String(v) }
  : typeof v === 'object' && v !== null ? { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, val(x)])) } } : { stringValue: v };
const seed = (path, data) => raw(`${root}/${path}`, 'PATCH', { fields: Object.fromEntries(Object.entries(data).filter(([, v]) => v !== undefined).map(([k, v]) => [k, val(v)])) });

async function user(name, roles, { first, last } = {}) {
  const email = `${name}-${run}@sportpro.test`;
  const r = await fetch(`${authUrl}/accounts:signUp?key=local`, { method: 'POST', headers: J, body: JSON.stringify({ email, password, returnSecureToken: true }) }).then((x) => x.json());
  await fetch(`${authUrl}/accounts:update`, { method: 'POST', headers: { ...J, Authorization: 'Bearer owner' }, body: JSON.stringify({ localId: r.localId, emailVerified: true }) });
  await seed(`users/${r.localId}`, { roles, firstName: first, lastName: last });
  const app = initializeApp({ projectId: project, apiKey: 'local' }, `${name}-${run}`);
  const auth = getAuth(app); connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  const db = getFirestore(app); connectFirestoreEmulator(db, '127.0.0.1', 8080);
  await signInWithEmailAndPassword(auth, email, password);
  return { uid: r.localId, email, name, db, display: first ? [first, last].filter(Boolean).join(' ') : 'Usuario SportPro' };
}

// ---------- Datos ----------
const coach = await user('entrenadora', ['ENTRENADOR'], { first: 'Carla', last: 'Ruiz' });
const coach2 = await user('entrenadora-sin-nombre', ['ENTRENADOR']);
const pending = await user('entrenadora-pendiente', ['ENTRENADOR'], { first: 'Pia' });
const player = await user('jugadora', ['JUGADOR'], { first: 'Paula' });
const guardian = await user('tutora', ['PADRE / TUTOR'], { first: 'Tita' });
const admin = await user('admin', []);
const moderator = await user('moderadora', ['JUGADOR'], { first: 'Mod' });
for (const c of [coach, coach2]) await seed(`coachApprovals/${c.uid}`, { approved: true });
await seed(`coachApprovals/${pending.uid}`, { approved: false });
await seed(`admins/${admin.uid}`, { since: new Date() });
await seed(`moderators/${moderator.uid}`, { since: new Date() });

let count = 0, failures = 0;
const out = (label, ok, detail = '') => { count++; if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${count}. ${label}${detail ? ': ' + detail : ''}`); };
const code = (p) => p.then(() => 'OK', (e) => e.code ?? e.message ?? String(e));
const expectOk = async (label, p) => { const c = await code(p); out(label, c === 'OK', c); };
const expectDenied = async (label, p) => { const c = await code(p); out(label, c === 'permission-denied', c); };

// ---------- Réplica de TryoutRepository ----------
const DAY = 24 * 3600e3;
// Fechas como la app: inicio del día en Lima (UTC-5) y 23:59:59 en Lima.
const limaStart = (offsetDays) => { const d = new Date(Date.now() - 5 * 3600e3 + offsetDays * DAY); return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate(), 5)); };
const limaEnd = (offsetDays) => new Date(limaStart(offsetDays).getTime() + DAY - 1000);
const N = (u) => collection(u.db, 'tryoutNotices');
const notice = (u, extra = {}) => ({ authorId: u.uid, authorName: u.display, title: 'Prueba Sub-15', description: 'Trae chimpunes',
  status: 'ACTIVE', createdAt: serverTimestamp(), ...extra });
const create = (u, extra = {}) => addDoc(N(u), notice(u, extra));
const cancel = (u, id, extra = {}) => updateDoc(doc(u.db, 'tryoutNotices', id), { status: 'CANCELLED', cancelledAt: serverTimestamp(), cancelledBy: u.uid, ...extra });
const active = (u) => getDocs(query(N(u), where('status', '==', 'ACTIVE'))).then((s) => s.size, (e) => e.code);
const mine = (u) => getDocs(query(N(u), where('authorId', '==', u.uid))).then((s) => s.size, (e) => e.code);

// ---------- 1. Crear ----------
console.log('\n--- 1. Crear ---');
let full, minimal, forAdmin, forModerator, forOther;
await expectOk('entrenadora aprobada crea un aviso completo', create(coach, { category: 'SUB-15', position: 'Portero', location: 'Estadio Municipal',
  tryoutDate: Timestamp.fromDate(limaStart(3)), deadline: Timestamp.fromDate(limaEnd(2)) }).then((r) => { full = r.id; }));
await expectOk('solo con título y descripción', create(coach).then((r) => { minimal = r.id; }));
await expectOk('prueba hoy y fecha límite hoy (23:59:59 Lima)', create(coach, { tryoutDate: Timestamp.fromDate(limaStart(0)), deadline: Timestamp.fromDate(limaEnd(0)) }).then((r) => { forAdmin = r.id; }));
await expectOk('fecha límite el mismo día de la prueba', create(coach, { tryoutDate: Timestamp.fromDate(limaStart(5)), deadline: Timestamp.fromDate(limaEnd(5)) }).then((r) => { forModerator = r.id; }));
await expectOk('entrenadora aprobada sin nombre en el perfil publica como "Usuario SportPro"', create(coach2).then((r) => { forOther = r.id; }));
await expectDenied('entrenadora NO aprobada', create(pending));
await expectDenied('jugadora', create(player));
await expectDenied('tutora', create(guardian));
await expectDenied('sin título', addDoc(N(coach), (({ title, ...r }) => r)(notice(coach))));
await expectDenied('sin descripción', addDoc(N(coach), (({ description, ...r }) => r)(notice(coach))));
await expectDenied('título vacío', create(coach, { title: '' }));
await expectDenied('título de 101 caracteres', create(coach, { title: 'x'.repeat(101) }));
await expectDenied('descripción de 2001 caracteres', create(coach, { description: 'x'.repeat(2001) }));
await expectDenied('ubicación de 121 caracteres', create(coach, { location: 'x'.repeat(121) }));
await expectDenied('categoría fuera de lista', create(coach, { category: 'SUB-99' }));
await expectDenied('posición fuera de lista', create(coach, { position: 'Arquero volador' }));
await expectDenied('campo extra (telefono)', create(coach, { telefono: '999999999' }));
await expectDenied('authorName de otra persona', create(coach, { authorName: 'Paula' }));
await expectDenied('authorName sacado del correo', create(coach, { authorName: coach.email.split('@')[0] }));
await expectDenied('prueba en el pasado (hace 3 días)', create(coach, { tryoutDate: Timestamp.fromDate(limaStart(-3)) }));
await expectDenied('fecha límite pasada', create(coach, { deadline: Timestamp.fromDate(new Date(Date.now() - 3600e3)) }));
await expectDenied('fecha límite después de la prueba (2 días)', create(coach, { tryoutDate: Timestamp.fromDate(limaStart(3)), deadline: Timestamp.fromDate(limaEnd(5)) }));
await expectDenied('nace cancelado', create(coach, { status: 'CANCELLED' }));
await expectDenied('createdAt del celular', create(coach, { createdAt: Timestamp.now() }));

// ---------- 2. Leer ----------
console.log('\n--- 2. Leer ---');
for (const u of [player, guardian, pending, admin]) {
  const r = await active(u);
  out(`${u.name}: lee los vigentes (status == ACTIVE)`, typeof r === 'number' && r >= 5, String(r));
}
await expectOk('jugadora abre un aviso vigente por ID', getDoc(doc(player.db, 'tryoutNotices', full)));
const r0 = await mine(coach);
out('"Mis avisos" de la autora (authorId == uid)', r0 === 4, String(r0));
const unfiltered = await getDocs(N(player)).then((s) => s.size, (e) => e.code);
out('consulta sin filtro de estado → permission-denied', unfiltered === 'permission-denied', String(unfiltered));

// ---------- 3. Cancelar ----------
console.log('\n--- 3. Cancelar ---');
await expectDenied('otra entrenadora no cancela un aviso ajeno', cancel(coach2, full));
await expectDenied('jugadora no cancela', cancel(player, full));
await expectDenied('cancelledBy de otra persona', cancel(coach, full, { cancelledBy: admin.uid }));
await expectDenied('sin cancelledBy', updateDoc(doc(coach.db, 'tryoutNotices', full), { status: 'CANCELLED', cancelledAt: serverTimestamp() }));
await expectDenied('cancelledAt del celular', cancel(coach, full, { cancelledAt: Timestamp.now() }));
await expectDenied('cancelar y editar el título a la vez', cancel(coach, full, { title: 'Otro' }));
await expectOk('la autora cancela su aviso', cancel(coach, full));
await expectOk('el admin cancela un aviso ajeno', cancel(admin, forAdmin));
await expectOk('la moderadora cancela un aviso ajeno', cancel(moderator, forModerator));
const byMod = (await getDoc(doc(coach.db, 'tryoutNotices', forModerator))).data();
out('queda cancelledBy = moderadora ("Cancelado por moderación" en la app)', byMod.cancelledBy === moderator.uid && byMod.status === 'CANCELLED');
await expectDenied('volver a ACTIVE (autora)', updateDoc(doc(coach.db, 'tryoutNotices', full), { status: 'ACTIVE', cancelledAt: deleteField(), cancelledBy: deleteField() }));
await expectDenied('volver a ACTIVE (admin)', updateDoc(doc(admin.db, 'tryoutNotices', full), { status: 'ACTIVE' }));
await expectDenied('cancelar dos veces', cancel(admin, full));
await expectDenied('editar un aviso vigente (autora)', updateDoc(doc(coach.db, 'tryoutNotices', minimal), { title: 'Nuevo' }));
await expectDenied('editar (admin)', updateDoc(doc(admin.db, 'tryoutNotices', minimal), { description: 'x' }));
await expectDenied('borrar (autora)', deleteDoc(doc(coach.db, 'tryoutNotices', minimal)));
await expectDenied('borrar (admin)', deleteDoc(doc(admin.db, 'tryoutNotices', minimal)));

// ---------- 4. Leer cancelados ----------
console.log('\n--- 4. Leer cancelados ---');
await expectOk('la autora lee su aviso cancelado', getDoc(doc(coach.db, 'tryoutNotices', full)));
await expectOk('el admin lee un cancelado', getDoc(doc(admin.db, 'tryoutNotices', full)));
await expectOk('la moderadora lee un cancelado', getDoc(doc(moderator.db, 'tryoutNotices', full)));
await expectDenied('la jugadora NO lee un cancelado', getDoc(doc(player.db, 'tryoutNotices', full)));
await expectDenied('otra entrenadora NO lee un cancelado ajeno', getDoc(doc(coach2.db, 'tryoutNotices', forModerator)));
const r1 = await mine(coach);
out('"Mis avisos" incluye los cancelados de la autora', r1 === 4, String(r1));
const activeIds = await getDocs(query(N(player), where('status', '==', 'ACTIVE'))).then((s) => s.docs.map((d) => d.id), (e) => e.code);
out('los cancelados ya no salen en los vigentes', Array.isArray(activeIds) && !activeIds.includes(full) && activeIds.includes(minimal) && activeIds.includes(forOther));

console.log(failures === 0 ? `\nUS-20 tryouts client flow verified in demo-sportpro only: ${count} de ${count} OK.` : `\n${failures} de ${count} comprobaciones fallidas.`);
process.exit(failures === 0 ? 0 : 1);
