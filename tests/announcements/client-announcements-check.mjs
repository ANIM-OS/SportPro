// US-17 con el SDK CLIENTE: anuncios internos por equipo (announcements), acceso del tutor
// (announcementGuardians/{teamId}_{uid}) y marcas de visto (users/{uid}/announcementReads/{teamId}).
// Replica AnnouncementRepository: consultas siempre con where('teamId', '==', …).
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Requiere el emulador arrancado con firebase.local.json. Sale con código 1 si algo falla.
// - Límite de 10 get()/exists() por petición: cada caso que pasa por el peor camino (tutor que lee:
//   teams, admins, membresía propia, acceso, guardianLinks y membresía del jugador = 6 documentos;
//   admin que crea: users, coachApprovals, teams y admins = 4) se ejecuta aquí. Si una regla superara
//   el límite, el emulador devolvería un error en lugar de OK y la prueba fallaría.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, collectionGroup, query, where, setDoc, addDoc, getDoc, getDocs,
  updateDoc, deleteDoc, onSnapshot, serverTimestamp, Timestamp } = require('firebase/firestore');

const project = 'demo-sportpro';
const root = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents`;
const authUrl = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const password = 'SportProLocal2026!';
const run = Date.now().toString(36);
const J = { 'Content-Type': 'application/json' };
// "Bearer owner" se salta las reglas: solo para preparar datos.
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
const p2 = await user('jugadora-otro-equipo', ['JUGADOR']);
const guardian = await user('tutora', ['PADRE / TUTOR']);
const unverified = await user('tutora-sin-verificar', ['PADRE / TUTOR'], false);
const noLink = await user('tutora-sin-vinculo', ['PADRE / TUTOR']);
const revoked = await user('tutora-revocada', ['PADRE / TUTOR']);
const admin = await user('admin', []);
for (const c of [coach, otherCoach]) await seed(`coachApprovals/${c.uid}`, { approved: true });
await seed(`admins/${admin.uid}`, { since: new Date() });
const team = `eq-${run}`, team2 = `eq2-${run}`, team3 = `eq3-${run}`, academy = `ac-${run}`;
await seed(`teams/${team}`, { createdBy: coach.uid, name: 'Halcones', type: 'EQUIPO', categories: ['SUB-12'] });
await seed(`teams/${team2}`, { createdBy: pending.uid, name: 'Pendiente', type: 'EQUIPO', categories: ['SUB-12'] });
await seed(`teams/${team3}`, { createdBy: otherCoach.uid, name: 'Águilas', type: 'EQUIPO', categories: ['SUB-12'] });
await seed(`teams/${academy}`, { createdBy: coach.uid, name: 'Academia', type: 'ACADEMIA', categories: ['SUB-12'] });
await seed(`teamMemberships/${team}_${p1.uid}`, { teamId: team, userId: p1.uid, playerName: 'Paula', category: 'SUB-12' });
await seed(`teamMemberships/${team3}_${p2.uid}`, { teamId: team3, userId: p2.uid, playerName: 'Otra', category: 'SUB-12' });
for (const g of [guardian, unverified, revoked]) await seed(`guardianLinks/${p1.uid}_${g.uid}`, { playerId: p1.uid, guardianId: g.uid, inviteId: 'x', status: 'ACTIVE' });

let count = 0, failures = 0;
const out = (label, ok, detail = '') => { count++; if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${count}. ${label}${detail ? ': ' + detail : ''}`); };
const code = (p) => p.then(() => 'OK', (e) => e.code ?? e.message ?? String(e));
const expectOk = async (label, p) => { const c = await code(p); out(label, c === 'OK', c); };
const expectDenied = async (label, p) => { const c = await code(p); out(label, c === 'permission-denied', c); };
const list = (u, teamId) => getDocs(query(collection(u.db, 'announcements'), where('teamId', '==', teamId))).then((s) => `${s.size} docs`, (e) => e.code);
const checkList = async (label, u, teamId, want) => { const r = await list(u, teamId); out(`${label} → ${want}`, r === want, r); };

// ---------- Réplica de AnnouncementRepository ----------
const A = (u) => collection(u.db, 'announcements');
const announcement = (u, teamId, extra = {}) => ({ teamId, type: 'CONVOCATORIA', title: 'Convocatoria sábado', body: 'Revisa la app para los detalles.',
  authorId: u.uid, createdAt: serverTimestamp(), ...extra });
const create = (u, teamId, extra = {}) => addDoc(A(u), announcement(u, teamId, extra));
const access = (u, teamId, playerId, id = `${teamId}_${u.uid}`) => setDoc(doc(u.db, 'announcementGuardians', id), { teamId, playerId, updatedAt: serverTimestamp() });
const seen = (u, teamId, extra = {}, owner = u) => setDoc(doc(u.db, 'users', owner.uid, 'announcementReads', teamId), { lastSeenAt: serverTimestamp(), ...extra });

// ---------- 1. Crear ----------
console.log('\n--- 1. Crear ---');
let coachAnn, adminAnn;
await expectOk('dueña aprobada crea en su equipo', create(coach, team).then((r) => { coachAnn = r.id; }));
await expectOk('admin crea en cualquier equipo (peor caso de create: 4 documentos)', create(admin, team, { type: 'CAMBIO_HORARIO' }).then((r) => { adminAnn = r.id; }));
await expectOk('admin crea en otro equipo', create(admin, team3, { type: 'ENTRENAMIENTO' }));
await expectDenied('dueña NO aprobada no puede crear', create(pending, team2));
await expectDenied('otra entrenadora no puede crear en un equipo ajeno', create(otherCoach, team));
await expectDenied('jugadora no puede crear', create(p1, team));
await expectDenied('tutora no puede crear', create(guardian, team));
await expectDenied('tipo inválido', create(coach, team, { type: 'OTRO' }));
await expectDenied('título vacío', create(coach, team, { title: '' }));
await expectOk('título de 80 caracteres', create(coach, team, { title: 'x'.repeat(80) }));
await expectDenied('título de 81 caracteres', create(coach, team, { title: 'x'.repeat(81) }));
await expectDenied('cuerpo vacío', create(coach, team, { body: '' }));
await expectOk('cuerpo de 500 caracteres', create(coach, team, { body: 'y'.repeat(500) }));
await expectDenied('cuerpo de 501 caracteres', create(coach, team, { body: 'y'.repeat(501) }));
await expectDenied('título no es texto', create(coach, team, { title: 5 }));
await expectDenied('campo extra (teamName)', create(coach, team, { teamName: 'Halcones' }));
await expectDenied('falta un campo (sin body)', addDoc(A(coach), { teamId: team, type: 'CONVOCATORIA', title: 't', authorId: coach.uid, createdAt: serverTimestamp() }));
await expectDenied('authorId de otra persona', create(coach, team, { authorId: p1.uid }));
await expectDenied('createdAt con la hora del celular', create(coach, team, { createdAt: Timestamp.now() }));
await expectDenied('equipo tipo ACADEMIA', create(coach, academy));

// ---------- 2. Leer ----------
console.log('\n--- 2. Leer (where teamId) ---');
await checkList('dueña', coach, team, '4 docs');
await checkList('miembro del equipo', p1, team, '4 docs');
await checkList('admin', admin, team, '4 docs');
await checkList('jugadora de otro equipo', p2, team, 'permission-denied');
await checkList('otra entrenadora', otherCoach, team, 'permission-denied');
await checkList('tutora SIN documento de acceso (denegado limpio por exists())', guardian, team, 'permission-denied');
await expectOk('tutora vinculada y verificada guarda su acceso', access(guardian, team, p1.uid));
await checkList('tutora con acceso (peor caso de lectura: 6 documentos)', guardian, team, '4 docs');
await expectDenied('tutora sin verificar no puede guardar acceso', access(unverified, team, p1.uid));
await checkList('tutora sin verificar', unverified, team, 'permission-denied');
await expectDenied('tutora sin vínculo no puede guardar acceso', access(noLink, team, p1.uid));
await checkList('tutora sin vínculo', noLink, team, 'permission-denied');
await expectDenied('tutora no puede guardar acceso a nombre de otro uid', access(guardian, team, p1.uid, `${team}_${noLink.uid}`));
await expectDenied('tutora no puede guardar acceso a un equipo donde su jugador no está', access(guardian, team3, p1.uid));
await expectDenied('tutora no puede guardar acceso por un jugador ajeno', access(guardian, team3, p2.uid));
await expectOk('tutora (luego revocada) guarda su acceso', access(revoked, team, p1.uid));
await checkList('tutora antes de revocar', revoked, team, '4 docs');
await seed(`guardianLinks/${p1.uid}_${revoked.uid}`, { playerId: p1.uid, guardianId: revoked.uid, inviteId: 'x', status: 'REVOKED' });
await checkList('tutora con vínculo revocado (aunque conserve el acceso)', revoked, team, 'permission-denied');
await expectDenied('jugadora de otro equipo no abre un anuncio por ID', getDoc(doc(p2.db, 'announcements', coachAnn)));
await expectOk('miembro abre un anuncio por ID', getDoc(doc(p1.db, 'announcements', coachAnn)));
const all = await getDocs(A(p1)).then((s) => `${s.size} docs`, (e) => e.code);
out('consulta sin filtro de teamId → permission-denied', all === 'permission-denied', all);
for (const u of [coach, p1, guardian, admin]) {
  const r = await getDocs(collectionGroup(u.db, 'announcements')).then((s) => `${s.size} docs`, (e) => e.code);
  out(`${u.name}: collectionGroup('announcements') → permission-denied`, r === 'permission-denied', r);
}
const otherAccess = await getDoc(doc(noLink.db, 'announcementGuardians', `${team}_${guardian.uid}`)).then(() => 'OK', (e) => e.code);
out('nadie lee el acceso de otro tutor', otherAccess === 'permission-denied', otherAccess);
// Tiempo real: la jugadora recibe un anuncio nuevo sin recargar.
const sizes = [];
const stop = onSnapshot(query(collection(p1.db, 'announcements'), where('teamId', '==', team)), (s) => sizes.push(s.size), (e) => sizes.push(e.code));
await sleep(400);
await create(coach, team, { title: 'Cambio de hora' });
await sleep(800); stop();
out('la jugadora recibe el anuncio nuevo en tiempo real', sizes[0] === 4 && sizes.at(-1) === 5, sizes.join('→'));

// ---------- 3. Editar y borrar ----------
console.log('\n--- 3. Editar y borrar ---');
await expectDenied('nadie edita: la autora', updateDoc(doc(coach.db, 'announcements', coachAnn), { title: 'Otro' }));
await expectDenied('nadie edita: el admin', updateDoc(doc(admin.db, 'announcements', coachAnn), { title: 'Otro' }));
await expectDenied('dueña no autora no borra el anuncio del admin', deleteDoc(doc(coach.db, 'announcements', adminAnn)));
await expectDenied('miembro no borra', deleteDoc(doc(p1.db, 'announcements', coachAnn)));
await expectDenied('tutora no borra', deleteDoc(doc(guardian.db, 'announcements', coachAnn)));
await expectOk('la autora borra su anuncio', deleteDoc(doc(coach.db, 'announcements', coachAnn)));
await expectOk('el admin borra un anuncio ajeno', deleteDoc(doc(admin.db, 'announcements', adminAnn)));
await expectDenied('otro tutor no borra el acceso ajeno', deleteDoc(doc(noLink.db, 'announcementGuardians', `${team}_${guardian.uid}`)));
await expectOk('la tutora borra su propio acceso', deleteDoc(doc(guardian.db, 'announcementGuardians', `${team}_${guardian.uid}`)));
await checkList('tutora tras borrar su acceso', guardian, team, 'permission-denied');

// ---------- 4. Marcas de visto ----------
console.log('\n--- 4. Marcas de visto (users/{uid}/announcementReads) ---');
await expectOk('la jugadora guarda su marca (hora del servidor)', seen(p1, team));
await expectOk('la jugadora lee su marca', getDoc(doc(p1.db, 'users', p1.uid, 'announcementReads', team)));
await expectDenied('marca con la hora del celular', seen(p1, team, { lastSeenAt: Timestamp.now() }));
await expectDenied('marca con campo extra', seen(p1, team, { count: 3 }));
await expectOk('la dueña guarda su marca', seen(coach, team));
await expectDenied('la jugadora no lee la marca de otra persona', getDoc(doc(p1.db, 'users', coach.uid, 'announcementReads', team)));
await expectDenied('la jugadora no escribe la marca de otra persona', seen(p1, team, {}, coach));
await expectDenied('el admin tampoco lee marcas ajenas', getDoc(doc(admin.db, 'users', p1.uid, 'announcementReads', team)));
const marks = await getDocs(collection(p1.db, 'users', p1.uid, 'announcementReads')).then((s) => `${s.size} docs`, (e) => e.code);
out('la jugadora lista sus marcas (listenReads)', marks === '1 docs', marks);

console.log(failures === 0 ? `\nUS-17 announcements client flow verified in demo-sportpro only: ${count} de ${count} OK.` : `\n${failures} de ${count} comprobaciones fallidas.`);
process.exit(failures === 0 ? 0 : 1);
