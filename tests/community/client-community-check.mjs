// US-18 con el SDK CLIENTE: publicaciones de la comunidad (communityPosts) por rol y visibilidad,
// acceso a academia (academyAccess/{academyId}_{uid}), lectura del tutor (announcementGuardians, US-17),
// publicaciones ocultadas y comentarios/likes de US-19. Replica CommunityRepository.
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Límite de 10 get()/exists(): los peores caminos se ejecutan aquí (lectura del tutor = 7, comentarios
//   del tutor = 8, crear ACADEMIA = 6, lectura ACADEMIA = 6). Si alguno lo superara, el emulador daría error.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, query, where, setDoc, addDoc, getDoc, getDocs,
  updateDoc, deleteDoc, serverTimestamp, Timestamp } = require('firebase/firestore');

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

async function user(name, roles, { verified = true, first, last } = {}) {
  const email = `${name}-${run}@sportpro.test`;
  const r = await fetch(`${authUrl}/accounts:signUp?key=local`, { method: 'POST', headers: J, body: JSON.stringify({ email, password, returnSecureToken: true }) }).then((x) => x.json());
  if (verified) await fetch(`${authUrl}/accounts:update`, { method: 'POST', headers: { ...J, Authorization: 'Bearer owner' }, body: JSON.stringify({ localId: r.localId, emailVerified: true }) });
  await seed(`users/${r.localId}`, { roles, firstName: first, lastName: last });
  const app = initializeApp({ projectId: project, apiKey: 'local' }, `${name}-${run}`);
  const auth = getAuth(app); connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  const db = getFirestore(app); connectFirestoreEmulator(db, '127.0.0.1', 8080);
  await signInWithEmailAndPassword(auth, email, password);
  return { uid: r.localId, email, name, db, display: first ? [first, last].filter(Boolean).join(' ') : 'Usuario SportPro' };
}

// ---------- Datos ----------
const coach = await user('entrenadora', ['ENTRENADOR'], { first: 'Carla', last: 'Ruiz' });
const pending = await user('entrenadora-pendiente', ['ENTRENADOR'], { first: 'Pia' });
const p1 = await user('paula', ['JUGADOR'], { first: 'Paula', last: 'Díaz' });
const p2 = await user('otra-jugadora', ['JUGADOR'], { first: 'Otra' });
const p3 = await user('jugadora-equipo-b', ['JUGADOR']);                 // sin nombre → "Usuario SportPro"
const both = await user('jugadora-tutora', ['JUGADOR', 'PADRE / TUTOR'], { first: 'Doble' });
const guardian = await user('tutora', ['PADRE / TUTOR'], { first: 'Tita' });
const unverified = await user('tutora-sin-verificar', ['PADRE / TUTOR'], { verified: false });
const noLink = await user('tutora-sin-vinculo', ['PADRE / TUTOR']);
const moderator = await user('moderadora', ['JUGADOR'], { first: 'Mod' });
const admin = await user('admin', []);
await seed(`coachApprovals/${coach.uid}`, { approved: true });
await seed(`admins/${admin.uid}`, { since: new Date() });
await seed(`moderators/${moderator.uid}`, { since: new Date() });
const team = `eq-${run}`, teamB = `eqb-${run}`, other = `otro-${run}`, academy = `ac-${run}`, pendTeam = `pend-${run}`;
await seed(`teams/${academy}`, { createdBy: coach.uid, name: 'Academia', type: 'ACADEMIA', categories: ['SUB-12'] });
await seed(`teams/${team}`, { createdBy: coach.uid, name: 'Halcones', type: 'EQUIPO', categories: ['SUB-12'], academyId: academy });
await seed(`teams/${teamB}`, { createdBy: coach.uid, name: 'Halcones B', type: 'EQUIPO', categories: ['SUB-12'], academyId: academy });
await seed(`teams/${other}`, { createdBy: pending.uid, name: 'Ajeno', type: 'EQUIPO', categories: ['SUB-12'] });
await seed(`teams/${pendTeam}`, { createdBy: pending.uid, name: 'Pendiente', type: 'EQUIPO', categories: ['SUB-12'] });
await seed(`teamMemberships/${team}_${p1.uid}`, { teamId: team, userId: p1.uid, playerName: 'Paula', category: 'SUB-12' });
await seed(`teamMemberships/${team}_${both.uid}`, { teamId: team, userId: both.uid, playerName: 'Doble', category: 'SUB-12' });
await seed(`teamMemberships/${teamB}_${p3.uid}`, { teamId: teamB, userId: p3.uid, playerName: 'B', category: 'SUB-12' });
await seed(`teamMemberships/${other}_${p2.uid}`, { teamId: other, userId: p2.uid, playerName: 'Otra', category: 'SUB-12' });
for (const g of [guardian, unverified]) await seed(`guardianLinks/${p1.uid}_${g.uid}`, { playerId: p1.uid, guardianId: g.uid, inviteId: 'x', status: 'ACTIVE' });

let count = 0, failures = 0;
const out = (label, ok, detail = '') => { count++; if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${count}. ${label}${detail ? ': ' + detail : ''}`); };
const code = (p) => p.then(() => 'OK', (e) => e.code ?? e.message ?? String(e));
const expectOk = async (label, p) => { const c = await code(p); out(label, c === 'OK', c); };
const expectDenied = async (label, p) => { const c = await code(p); out(label, c === 'permission-denied', c); };
const size = (q) => getDocs(q).then((s) => `${s.size} docs`, (e) => e.code);
const checkQ = async (label, q, want) => { const r = await size(q); out(`${label} → ${want}`, r === want, r); };

// ---------- Réplica de CommunityRepository ----------
const P = (u) => collection(u.db, 'communityPosts');
const post = (u, visibility, teamId, extra = {}) => ({ authorId: u.uid, authorName: u.display, authorRole: 'JUGADOR', content: 'Hola equipo',
  visibility, createdAt: serverTimestamp(), active: true, moderated: false, ...(teamId ? { teamId } : {}), ...extra });
const create = (u, visibility, teamId, extra = {}) => addDoc(P(u), post(u, visibility, teamId, extra));
const academyAccess = (u, academyId, teamId, id = `${academyId}_${u.uid}`) => setDoc(doc(u.db, 'academyAccess', id), { academyId, teamId, updatedAt: serverTimestamp() });
const guardianAccess = (u, teamId, playerId) => setDoc(doc(u.db, 'announcementGuardians', `${teamId}_${u.uid}`), { teamId, playerId, updatedAt: serverTimestamp() });
const qPublic = (u) => query(P(u), where('visibility', '==', 'TODOS'), where('active', '==', true), where('moderated', '==', false));
const qAudience = (u, teamId, vis) => query(P(u), where('teamId', '==', teamId), where('visibility', '==', vis), where('active', '==', true), where('moderated', '==', false));
const qOwn = (u) => query(P(u), where('authorId', '==', u.uid));

// ---------- 1. Crear ----------
console.log('\n--- 1. Crear por rol y visibilidad ---');
let publicId, teamPostId, academyPostId;
await expectOk('jugadora publica para TODOS (sin teamId)', create(p1, 'TODOS').then((r) => { publicId = r.id; }));
await expectOk('jugadora publica para su EQUIPO', create(p1, 'EQUIPO', team).then((r) => { teamPostId = r.id; }));
await expectOk('entrenadora aprobada publica como ENTRENADOR en su ACADEMIA', create(coach, 'ACADEMIA', academy, { authorRole: 'ENTRENADOR' }).then((r) => { academyPostId = r.id; }));
await expectDenied('entrenadora NO aprobada como ENTRENADOR', create(pending, 'TODOS', null, { authorRole: 'ENTRENADOR' }));
await expectDenied('tutora no publica', create(guardian, 'TODOS', null, { authorRole: 'JUGADOR' }));
await expectOk('Jugador + Tutor publica como JUGADOR', create(both, 'TODOS'));
await expectOk('sin nombre en el perfil: authorName "Usuario SportPro"', create(p3, 'TODOS'));
await expectDenied('TODOS con teamId', create(p1, 'TODOS', team));
await expectDenied('EQUIPO sin teamId', create(p1, 'EQUIPO', null));
await expectDenied('EQUIPO con teamId ajeno', create(p1, 'EQUIPO', other));
await expectDenied('EQUIPO con teamId de una ACADEMIA (tipo equivocado)', create(coach, 'EQUIPO', academy, { authorRole: 'ENTRENADOR' }));
await expectDenied('ACADEMIA con teamId de un EQUIPO (tipo equivocado)', create(p1, 'ACADEMIA', team));
await expectDenied('equipo inexistente', create(p1, 'EQUIPO', `nada-${run}`));
await expectDenied('authorName de otra persona', create(p1, 'TODOS', null, { authorName: 'Carla Ruiz' }));
await expectDenied('authorName sacado del correo', create(p1, 'TODOS', null, { authorName: p1.email.split('@')[0] }));
await expectDenied('authorName vacío', create(p1, 'TODOS', null, { authorName: '' }));
await expectOk('contenido de 1000 caracteres', create(p1, 'TODOS', null, { content: 'x'.repeat(1000) }));
await expectDenied('contenido de 1001 caracteres', create(p1, 'TODOS', null, { content: 'x'.repeat(1001) }));
await expectDenied('falta un campo obligatorio (sin active)', addDoc(P(p1), (({ active, ...rest }) => rest)(post(p1, 'TODOS'))));
await expectDenied('miembro de equipo vinculado SIN academyAccess no publica en la ACADEMIA', create(p1, 'ACADEMIA', academy));
await expectOk('miembro guarda academyAccess por su equipo vinculado', academyAccess(p1, academy, team));
await expectOk('…y publica en la ACADEMIA (peor caso de create: 6 documentos)', create(p1, 'ACADEMIA', academy));
await expectDenied('academyAccess por un equipo del que no es miembro', academyAccess(p2, academy, team));
await expectDenied('academyAccess a nombre de otro uid', academyAccess(p1, academy, team, `${academy}_${p2.uid}`));
await expectDenied('academyAccess con un equipo no vinculado a esa academia', academyAccess(p2, academy, other));

// ---------- 2. Leer por rol ----------
console.log('\n--- 2. Leer por rol ---');
await checkQ('jugadora de otro equipo: públicas', qPublic(p2), `${(await getDocs(qPublic(p1))).size} docs`);
await checkQ('miembro: EQUIPO de su equipo', qAudience(p1, team, 'EQUIPO'), '1 docs');
await checkQ('dueña: EQUIPO de su equipo', qAudience(coach, team, 'EQUIPO'), '1 docs');
await checkQ('jugadora de otro equipo: EQUIPO ajeno', qAudience(p2, team, 'EQUIPO'), 'permission-denied');
await expectDenied('jugadora de otro equipo: abrir por ID una de EQUIPO', getDoc(doc(p2.db, 'communityPosts', teamPostId)));
await checkQ('dueña de la academia: ACADEMIA', qAudience(coach, academy, 'ACADEMIA'), '2 docs');
await checkQ('miembro con academyAccess: ACADEMIA (peor caso: 6 documentos)', qAudience(p1, academy, 'ACADEMIA'), '2 docs');
await checkQ('miembro de equipo vinculado SIN academyAccess: ACADEMIA', qAudience(p3, academy, 'ACADEMIA'), 'permission-denied');
await expectOk('jugadora del equipo B guarda academyAccess', academyAccess(p3, academy, teamB));
await checkQ('jugadora del equipo B con acceso: ACADEMIA', qAudience(p3, academy, 'ACADEMIA'), '2 docs');
await seed(`teams/${teamB}`, { createdBy: coach.uid, name: 'Halcones B', type: 'EQUIPO', categories: ['SUB-12'] });   // se desvincula
await checkQ('equipo B desvinculado (acceso revalidado): ACADEMIA', qAudience(p3, academy, 'ACADEMIA'), 'permission-denied');
await checkQ('jugadora de otro equipo: ACADEMIA', qAudience(p2, academy, 'ACADEMIA'), 'permission-denied');
await checkQ('tutora SIN documento de acceso: EQUIPO del equipo de su jugadora', qAudience(guardian, team, 'EQUIPO'), 'permission-denied');
await expectOk('tutora vinculada y verificada guarda su acceso (announcementGuardians)', guardianAccess(guardian, team, p1.uid));
await checkQ('tutora con acceso: EQUIPO (peor caso: 7 documentos)', qAudience(guardian, team, 'EQUIPO'), '1 docs');
await checkQ('tutora con acceso: NO ve la ACADEMIA', qAudience(guardian, academy, 'ACADEMIA'), 'permission-denied');
await expectDenied('tutora sin verificar no guarda acceso', guardianAccess(unverified, team, p1.uid));
await checkQ('tutora sin verificar: EQUIPO', qAudience(unverified, team, 'EQUIPO'), 'permission-denied');
await expectDenied('tutora sin vínculo no guarda acceso', guardianAccess(noLink, team, p1.uid));
await checkQ('tutora sin vínculo: EQUIPO', qAudience(noLink, team, 'EQUIPO'), 'permission-denied');
const unfiltered = await size(collection(p2.db, 'communityPosts'));
out('consulta sin filtros → permission-denied', unfiltered === 'permission-denied', unfiltered);

// ---------- 3. Ocultas y comentarios (US-19) ----------
console.log('\n--- 3. Moderación y comentarios ---');
await expectOk('miembro comenta en EQUIPO', addDoc(collection(p1.db, 'communityPosts', teamPostId, 'comments'), { authorId: p1.uid, authorName: p1.display, text: 'Bien', createdAt: serverTimestamp(), moderated: false }));
await expectDenied('tutora NO comenta (solo lee)', addDoc(collection(guardian.db, 'communityPosts', teamPostId, 'comments'), { authorId: guardian.uid, authorName: 'Tita', text: 'Hola', createdAt: serverTimestamp(), moderated: false }));
await expectDenied('tutora NO da like', setDoc(doc(guardian.db, 'communityPosts', teamPostId, 'likes', guardian.uid), { userId: guardian.uid, createdAt: serverTimestamp() }));
await checkQ('tutora lee comentarios (peor caso: 8 documentos)', query(collection(guardian.db, 'communityPosts', teamPostId, 'comments'), where('moderated', '==', false)), '1 docs');
await expectOk('miembro con academyAccess comenta en la ACADEMIA', addDoc(collection(p1.db, 'communityPosts', academyPostId, 'comments'), { authorId: p1.uid, authorName: p1.display, text: 'Ok', createdAt: serverTimestamp(), moderated: false }));
await expectOk('miembro da like en la ACADEMIA', setDoc(doc(p1.db, 'communityPosts', academyPostId, 'likes', p1.uid), { userId: p1.uid, createdAt: serverTimestamp() }));
await expectDenied('la autora no puede ocultar su publicación', updateDoc(doc(p1.db, 'communityPosts', publicId), { moderated: true }));
await expectDenied('nadie edita el contenido (autora)', updateDoc(doc(p1.db, 'communityPosts', publicId), { content: 'otro' }));
await expectDenied('nadie borra (autora)', deleteDoc(doc(p1.db, 'communityPosts', publicId)));
await expectDenied('nadie borra (admin)', deleteDoc(doc(admin.db, 'communityPosts', publicId)));
await expectOk('la moderadora oculta la publicación de EQUIPO', updateDoc(doc(moderator.db, 'communityPosts', teamPostId), { moderated: true }));
await expectOk('la moderadora oculta la pública', updateDoc(doc(moderator.db, 'communityPosts', publicId), { moderated: true }));
await expectDenied('un tercero (otra jugadora) NO lee por ID la pública oculta', getDoc(doc(p2.db, 'communityPosts', publicId)));
await expectDenied('un miembro (no autor) NO lee por ID la de EQUIPO oculta', getDoc(doc(both.db, 'communityPosts', teamPostId)));
await expectOk('la autora lee la suya oculta por ID', getDoc(doc(p1.db, 'communityPosts', publicId)));
const own = await getDocs(qOwn(p1)).then((s) => s.docs.filter((d) => d.get('moderated') === true).length, (e) => e.code);
out('la autora ve sus ocultas en "Mis publicaciones" (authorId == uid)', own === 2, String(own));
await expectOk('la moderadora lee la oculta', getDoc(doc(moderator.db, 'communityPosts', publicId)));
await expectOk('el admin lee la oculta', getDoc(doc(admin.db, 'communityPosts', publicId)));
await checkQ('un tercero NO lee comentarios de un post oculto', query(collection(both.db, 'communityPosts', teamPostId, 'comments'), where('moderated', '==', false)), 'permission-denied');
await checkQ('la tutora NO lee comentarios de un post oculto', query(collection(guardian.db, 'communityPosts', teamPostId, 'comments'), where('moderated', '==', false)), 'permission-denied');
await checkQ('la autora lee los comentarios de su post oculto', query(collection(p1.db, 'communityPosts', teamPostId, 'comments'), where('moderated', '==', false)), '1 docs');
await checkQ('la moderadora lee los comentarios del post oculto', query(collection(moderator.db, 'communityPosts', teamPostId, 'comments'), where('moderated', '==', false)), '1 docs');
await expectDenied('nadie comenta en un post oculto (miembro)', addDoc(collection(both.db, 'communityPosts', teamPostId, 'comments'), { authorId: both.uid, authorName: 'Doble', text: 'x', createdAt: serverTimestamp(), moderated: false }));

console.log(failures === 0 ? `\nUS-18 community client flow verified in demo-sportpro only: ${count} de ${count} OK.` : `\n${failures} de ${count} comprobaciones fallidas.`);
process.exit(failures === 0 ? 0 : 1);
