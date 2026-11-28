// US-21 con el SDK CLIENTE: reportes de publicaciones y comentarios, lectura solo para moderación (también por
// collectionGroup), "Marcar como revisado", ocultar/restaurar con moderatedBy/moderatedAt, sección "Ocultos",
// cancelación de avisos (US-20) y escritura sin conexión. Replica ModerationRepository.
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Límite de 10 get()/exists(): el peor caso (reportar un comentario de una ACADEMIA con academyAccess:
//   post, teams academia, academyAccess, teams equipo, membresía, users y comentario = 7-8) se ejecuta aquí.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, collectionGroup, query, where, setDoc, addDoc, getDoc,
  getDocs, updateDoc, serverTimestamp, disableNetwork, enableNetwork, onSnapshot, Timestamp } = require('firebase/firestore');

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
const pending = await user('entrenadora-pendiente', ['ENTRENADOR'], { first: 'Pia' });
const p1 = await user('paula', ['JUGADOR'], { first: 'Paula', last: 'Díaz' });
const p2 = await user('bea', ['JUGADOR'], { first: 'Bea' });
const noName = await user('sin-nombre', ['JUGADOR']);
const guardian = await user('tutora', ['PADRE / TUTOR'], { first: 'Tita' });
const outsider = await user('ajena', ['JUGADOR'], { first: 'Ajena' });
const moderator = await user('moderadora', ['JUGADOR'], { first: 'Mod' });
const admin = await user('admin', [], { first: 'Admin' });
await seed(`coachApprovals/${coach.uid}`, { approved: true });
await seed(`moderators/${moderator.uid}`, { since: new Date() });
await seed(`admins/${admin.uid}`, { since: new Date() });
const academy = `ac-${run}`, team = `eq-${run}`, otherTeam = `otro-${run}`;
await seed(`teams/${academy}`, { createdBy: coach.uid, name: 'Academia', type: 'ACADEMIA', categories: ['SUB-12'] });
await seed(`teams/${team}`, { createdBy: coach.uid, name: 'Halcones', type: 'EQUIPO', categories: ['SUB-12'], academyId: academy });
await seed(`teams/${otherTeam}`, { createdBy: pending.uid, name: 'Ajeno', type: 'EQUIPO', categories: ['SUB-12'] });
for (const p of [p1, p2, noName]) await seed(`teamMemberships/${team}_${p.uid}`, { teamId: team, userId: p.uid, playerName: p.name, category: 'SUB-12' });
await seed(`teamMemberships/${otherTeam}_${outsider.uid}`, { teamId: otherTeam, userId: outsider.uid, playerName: 'Ajena', category: 'SUB-12' });
await seed(`academyAccess/${academy}_${p1.uid}`, { academyId: academy, teamId: team, updatedAt: new Date() });
const postDoc = (id, visibility, extra = {}) => seed(`communityPosts/${id}`, { authorId: p2.uid, authorName: 'Bea', authorRole: 'JUGADOR',
  content: `Contenido ${id}`, visibility, createdAt: new Date(), active: true, moderated: false, ...extra });
const todos = `todos-${run}`, equipo = `equipo-${run}`, academia = `academia-${run}`, oculta = `oculta-${run}`;
await postDoc(todos, 'TODOS');
await postDoc(equipo, 'EQUIPO', { teamId: team });
await postDoc(academia, 'ACADEMIA', { teamId: academy, authorId: coach.uid, authorName: 'Carla Ruiz', authorRole: 'ENTRENADOR' });
await postDoc(oculta, 'TODOS', { moderated: true });
const notice = `aviso-${run}`;
await seed(`tryoutNotices/${notice}`, { authorId: coach.uid, authorName: 'Carla Ruiz', title: 'Prueba', description: 'Sub-12', status: 'ACTIVE', createdAt: new Date() });

let count = 0, failures = 0;
const out = (label, ok, detail = '') => { count++; if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${count}. ${label}${detail ? ': ' + detail : ''}`); };
const code = (p) => p.then(() => 'OK', (e) => e.code ?? e.message ?? String(e));
const expectOk = async (label, p) => { const c = await code(p); out(label, c === 'OK', c); };
const expectDenied = async (label, p) => { const c = await code(p); out(label, c === 'permission-denied', c); };
const size = (q) => getDocs(q).then((s) => `${s.size} docs`, (e) => e.code);
const checkQ = async (label, q, want) => { const r = await size(q); out(`${label} → ${want}`, r === want, r); };
const has = (q, id) => getDocs(q).then((s) => s.docs.some((d) => d.id === id), (e) => e.code);

// ---------- Réplica de ModerationRepository ----------
const target = (u, post, comment) => comment ? doc(u.db, 'communityPosts', post, 'comments', comment) : doc(u.db, 'communityPosts', post);
const reportRef = (u, post, comment, id = u.uid) => doc(comment ? collection(target(u, post, comment), 'reports') : collection(target(u, post), 'reports'), id);
const fields = (u, post, comment, reason = 'Contenido inapropiado') => ({ targetType: comment ? 'COMMENT' : 'POST', postId: post,
  ...(comment ? { commentId: comment } : {}), reporterId: u.uid, reporterName: u.display, reason, status: 'PENDING', createdAt: serverTimestamp() });
const report = (u, post, comment = null, extra = {}, id = u.uid) => setDoc(reportRef(u, post, comment, id), { ...fields(u, post, comment), ...extra });
const setHidden = (u, post, comment, hidden, extra = {}) => updateDoc(target(u, post, comment), { moderated: hidden, moderatedBy: u.uid, moderatedAt: serverTimestamp(), ...extra });
const review = (u, path, extra = {}) => updateDoc(doc(u.db, path), { status: 'REVIEWED', reviewedBy: u.uid, reviewedAt: serverTimestamp(), ...extra });
const pendingQ = (u) => query(collectionGroup(u.db, 'reports'), where('status', '==', 'PENDING'));
const hiddenPostsQ = (u) => query(collection(u.db, 'communityPosts'), where('moderated', '==', true));
const hiddenCommentsQ = (u) => query(collectionGroup(u.db, 'comments'), where('moderated', '==', true));
const comment = (u, post) => addDoc(collection(u.db, 'communityPosts', post, 'comments'), { authorId: u.uid, authorName: u.display, text: 'Comentario', createdAt: serverTimestamp(), moderated: false });

// ---------- 1. Reportar publicaciones ----------
console.log('\n--- 1. Reportar publicaciones ---');
await expectOk('jugadora reporta una publicación ajena (tipo, postId, nombre del perfil, motivo, PENDING)', report(p1, todos));
await expectDenied('un solo reporte por usuario y publicación (segundo set = update)', report(p1, todos, null, { reason: 'Spam' }));
await expectDenied('no reporta su propia publicación', report(p2, todos));
await expectOk('reporta una ACADEMIA con academyAccess (peor caso: 7 documentos)', report(p1, academia));
await expectDenied('no reporta una publicación de EQUIPO que no ve', report(outsider, equipo));
await expectDenied('no reporta una publicación oculta', report(p1, oculta));
await expectDenied('la tutora no reporta', report(guardian, todos));
await expectDenied('la entrenadora NO aprobada no reporta', report(pending, todos));
await expectOk('jugadora sin nombre reporta como "Usuario SportPro"', report(noName, todos));
await expectDenied('reporterName de otra persona', report(coach, todos, null, { reporterName: 'Bea' }));
await expectDenied('reporterName sacado del correo', report(coach, todos, null, { reporterName: coach.email.split('@')[0] }));
await expectDenied('"Usuario SportPro" si el perfil sí tiene nombre', report(coach, todos, null, { reporterName: 'Usuario SportPro' }));
await expectDenied('motivo vacío', report(coach, todos, null, { reason: '' }));
await expectDenied('motivo de 501 caracteres', report(coach, todos, null, { reason: 'x'.repeat(501) }));
await expectDenied('nace como REVIEWED', report(coach, todos, null, { status: 'REVIEWED' }));
await expectDenied('targetType COMMENT en una publicación', report(coach, todos, null, { targetType: 'COMMENT' }));
await expectDenied('commentId en un reporte de publicación', report(coach, todos, null, { commentId: 'x' }));
await expectDenied('postId de otra publicación', report(coach, todos, null, { postId: equipo }));
await expectDenied('reporterId de otra persona', report(coach, todos, null, { reporterId: p2.uid }));
await expectDenied('ID del documento de otra persona', report(coach, todos, null, {}, p2.uid));
await expectDenied('fecha del celular en vez del servidor', report(coach, todos, null, { createdAt: Timestamp.now() }));
await expectDenied('campo extra', report(coach, todos, null, { extra: 1 }));
await expectOk('entrenadora aprobada reporta con motivo de 500 caracteres', report(coach, todos, null, { reason: 'x'.repeat(500) }));

// ---------- 2. Reportar comentarios ----------
console.log('\n--- 2. Reportar comentarios ---');
let c2, c1, cAc;
await expectOk('Bea comenta en TODOS', comment(p2, todos).then((r) => { c2 = r.id; }));
await expectOk('Paula comenta en TODOS', comment(p1, todos).then((r) => { c1 = r.id; }));
await expectOk('la entrenadora comenta en su ACADEMIA', comment(coach, academia).then((r) => { cAc = r.id; }));
await expectOk('Paula reporta el comentario de Bea (con commentId)', report(p1, todos, c2));
await expectOk('Paula reporta un comentario de la ACADEMIA (peor caso: 8 documentos)', report(p1, academia, cAc));
await expectDenied('un solo reporte por usuario y comentario', report(p1, todos, c2, { reason: 'Spam' }));
await expectDenied('no reporta su propio comentario', report(p1, todos, c1));
await expectDenied('reporte de comentario sin commentId', setDoc(reportRef(coach, todos, c2), (({ commentId, ...rest }) => rest)(fields(coach, todos, c2))));
await expectDenied('commentId de otro comentario', report(coach, todos, c2, { commentId: c1 }));
await expectDenied('targetType POST en un comentario', report(coach, todos, c2, { targetType: 'POST' }));
await expectDenied('jugadora de otro equipo no reporta comentarios de EQUIPO ajeno', report(outsider, equipo, 'cualquiera'));
await expectDenied('la tutora no reporta comentarios', report(guardian, todos, c2));

// ---------- 3. Leer reportes ----------
console.log('\n--- 3. Leer reportes (solo moderador o admin) ---');
await expectDenied('quien reporta no lee su reporte por ID', getDoc(reportRef(p1, todos, null)));
await checkQ('jugadora: collectionGroup de reportes pendientes', pendingQ(p1), 'permission-denied');
await checkQ('jugadora: collectionGroup de sus propios reportes', query(collectionGroup(p1.db, 'reports'), where('reporterId', '==', p1.uid)), 'permission-denied');
await checkQ('autora: reportes de su publicación', collection(p2.db, 'communityPosts', todos, 'reports'), 'permission-denied');
await checkQ('tutora: collectionGroup de reportes', collectionGroup(guardian.db, 'reports'), 'permission-denied');
// Este run: publicaciones (Paula ×2, sin nombre, entrenadora) + comentarios (Paula ×2) = 6.
const modCount = await size(pendingQ(moderator));
const mine = await getDocs(pendingQ(moderator)).then((s) => s.docs.filter((d) => d.get('postId').endsWith(run)).length, (e) => e.code);
out('moderadora: collectionGroup de pendientes (este run: 6)', mine === 6, `${mine} de ${modCount}`);
const adminMine = await getDocs(pendingQ(admin)).then((s) => s.docs.filter((d) => d.get('postId').endsWith(run)).length, (e) => e.code);
out('admin: collectionGroup de pendientes (este run: 6)', adminMine === 6, String(adminMine));
await expectOk('moderadora lee un reporte por ID', getDoc(reportRef(moderator, todos, null, p1.uid)));

// ---------- 4. Marcar como revisado ----------
console.log('\n--- 4. Marcar como revisado ---');
const postReport = `communityPosts/${todos}/reports/${p1.uid}`;
const commentReport = `communityPosts/${todos}/comments/${c2}/reports/${p1.uid}`;
await expectDenied('una jugadora no marca como revisado', review(p1, postReport));
await expectDenied('reviewedBy de otra persona', review(moderator, postReport, { reviewedBy: admin.uid }));
await expectDenied('reviewedAt del celular', review(moderator, postReport, { reviewedAt: Timestamp.now() }));
await expectDenied('estado distinto de REVIEWED', review(moderator, postReport, { status: 'CERRADO' }));
await expectDenied('cambiar también el motivo', review(moderator, postReport, { reason: 'Spam' }));
await expectOk('moderadora marca como revisado (PENDING → REVIEWED)', review(moderator, postReport));
await expectDenied('no se vuelve a revisar ni a PENDING', updateDoc(doc(moderator.db, postReport), { status: 'PENDING' }));
await expectOk('admin marca como revisado un reporte de comentario', review(admin, commentReport));
const left = await getDocs(pendingQ(moderator)).then((s) => s.docs.filter((d) => d.get('postId').endsWith(run)).length, (e) => e.code);
out('ya no aparecen en pendientes (6 → 4)', left === 4, String(left));
const reviewed = await getDoc(doc(moderator.db, postReport));
out('guarda reviewedBy = uid y reviewedAt', reviewed.get('reviewedBy') === moderator.uid && reviewed.get('reviewedAt') != null);

// ---------- 5. Ocultar y restaurar ----------
console.log('\n--- 5. Ocultar y restaurar ---');
await expectDenied('la autora no oculta su publicación', setHidden(p2, todos, null, true));
await expectDenied('una jugadora no oculta una publicación ajena', setHidden(p1, todos, null, true));
await expectDenied('ocultar sin moderatedBy/moderatedAt (contrato anterior)', updateDoc(target(moderator, todos), { moderated: true }));
await expectDenied('moderatedBy de otra persona', setHidden(moderator, todos, null, true, { moderatedBy: admin.uid }));
await expectDenied('moderatedAt del celular', setHidden(moderator, todos, null, true, { moderatedAt: Timestamp.now() }));
await expectDenied('ocultar y cambiar el contenido a la vez', setHidden(moderator, todos, null, true, { content: 'otro' }));
await expectDenied('"restaurar" algo ya visible (sin cambio)', setHidden(moderator, todos, null, false));
await expectOk('moderadora oculta la publicación', setHidden(moderator, todos, null, true));
await expectDenied('ocultar algo ya oculto', setHidden(moderator, todos, null, true));
await expectDenied('un tercero ya no la lee por ID', getDoc(target(p1, todos)));
out('un tercero no la recibe en el feed', (await has(query(collection(p1.db, 'communityPosts'), where('visibility', '==', 'TODOS'), where('active', '==', true), where('moderated', '==', false)), todos)) === false);
const authorView = await getDoc(target(p2, todos)).then((d) => d.get('moderated'), (e) => e.code);
out('la autora la ve (oculta → "Ocultada por moderación")', authorView === true, String(authorView));
out('moderadora la ve en "Ocultos"', (await has(hiddenPostsQ(moderator), todos)) === true);
await checkQ('jugadora no consulta las ocultas', hiddenPostsQ(p1), 'permission-denied');
const hiddenBy = await getDoc(target(moderator, todos));
out('guarda moderatedBy = uid y moderatedAt', hiddenBy.get('moderatedBy') === moderator.uid && hiddenBy.get('moderatedAt') != null);
await expectDenied('una jugadora no restaura', setHidden(p1, todos, null, false));
await expectOk('el admin restaura la publicación', setHidden(admin, todos, null, false));
await expectOk('un tercero vuelve a leerla', getDoc(target(p1, todos)));
out('moderatedBy pasa a ser el admin', (await getDoc(target(admin, todos))).get('moderatedBy') === admin.uid);

await expectDenied('ocultar comentario sin moderatedBy/moderatedAt', updateDoc(target(moderator, todos, c2), { moderated: true }));
await expectOk('moderadora oculta el comentario de Bea', setHidden(moderator, todos, c2, true));
await expectDenied('un tercero no lee el comentario oculto', getDoc(target(p1, todos, c2)));
await expectOk('la autora lee su comentario oculto', getDoc(target(p2, todos, c2)));
out('moderadora lo ve en "Ocultos" (collectionGroup comments)', (await has(hiddenCommentsQ(moderator), c2)) === true);
out('admin también lo ve en "Ocultos"', (await has(hiddenCommentsQ(admin), c2)) === true);
await checkQ('jugadora: collectionGroup de comentarios ocultos', hiddenCommentsQ(p1), 'permission-denied');
await checkQ('moderadora lista los ocultos de la publicación', query(collection(moderator.db, 'communityPosts', todos, 'comments'), where('moderated', '==', true)), '1 docs');
await checkQ('jugadora no lista los ocultos de la publicación', query(collection(p1.db, 'communityPosts', todos, 'comments'), where('moderated', '==', true)), 'permission-denied');
await expectDenied('una jugadora no restaura el comentario', setHidden(p1, todos, c2, false));
await expectOk('moderadora restaura el comentario', setHidden(moderator, todos, c2, false));
await expectOk('un tercero vuelve a leerlo', getDoc(target(p1, todos, c2)));

// ---------- 6. Avisos (US-20) ----------
console.log('\n--- 6. Cancelar avisos (US-20) ---');
const cancel = (u, extra = {}) => updateDoc(doc(u.db, 'tryoutNotices', notice), { status: 'CANCELLED', cancelledAt: serverTimestamp(), cancelledBy: u.uid, ...extra });
await expectDenied('una jugadora no cancela un aviso ajeno', cancel(p1));
await expectDenied('moderadora con cancelledBy de otra persona', cancel(moderator, { cancelledBy: coach.uid }));
await expectOk('moderadora cancela el aviso (quién y cuándo)', cancel(moderator));

// ---------- 7. Sin conexión ----------
console.log('\n--- 7. Sin conexión ---');
// Como la app: el listener ya está abierto (documento en caché) cuando se pierde la conexión.
const offlineRef = target(moderator, equipo);
let pendingSeen = null, firstSnap;
const loaded = new Promise((r) => { firstSnap = r; });
const unsub = onSnapshot(offlineRef, { includeMetadataChanges: true }, (s) => {
  firstSnap();
  if (pendingSeen === null && s.get('moderated') === true) pendingSeen = s.metadata.hasPendingWrites;
}, () => {});
await loaded;
await disableNetwork(moderator.db);
const write = setHidden(moderator, equipo, null, true);
await new Promise((r) => setTimeout(r, 300));
out('sin conexión se oculta al instante con "Pendiente de sincronizar" (hasPendingWrites)', pendingSeen === true, String(pendingSeen));
await enableNetwork(moderator.db);
await expectOk('al reconectar el servidor lo confirma', write);
unsub();
await expectDenied('ya sincronizado: un tercero no la lee', getDoc(target(p1, equipo)));

console.log(failures === 0 ? `\nUS-21 moderation client flow verified in demo-sportpro only: ${count} de ${count} OK.` : `\n${failures} de ${count} comprobaciones fallidas.`);
process.exit(failures === 0 ? 0 : 1);
