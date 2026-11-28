// US-19 con el SDK CLIENTE: comentarios, "Me gusta" y reportes de communityPosts. Replica
// CommunityRepository (addComment, setLiked, reportComment, observeComments con la consulta propia).
//
// - Solo emulador: auth 127.0.0.1:9099 y firestore 127.0.0.1:8080, proyecto demo-sportpro.
// - Usa el SDK web `firebase` instalado en tests/rules/node_modules (se carga con createRequire).
// - Límite de 10 get()/exists(): comentar en EQUIPO (post, teams, membresía, users, coachApprovals) y
//   reportar (+ comentario) se ejecutan aquí; el peor caso ACADEMIA (7) lo ejecuta client-community-check.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../rules/package.json', import.meta.url));
const { initializeApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, collection, query, where, setDoc, addDoc, getDoc, getDocs,
  updateDoc, deleteDoc, serverTimestamp } = require('firebase/firestore');

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
const p2 = await user('bea', ['JUGADOR'], { first: 'Bea' });
const noName = await user('sin-nombre', ['JUGADOR']);
const guardian = await user('tutora', ['PADRE / TUTOR'], { first: 'Tita' });
const outsider = await user('ajena', ['JUGADOR'], { first: 'Ajena' });
const moderator = await user('moderadora', ['JUGADOR'], { first: 'Mod' });
await seed(`coachApprovals/${coach.uid}`, { approved: true });
await seed(`moderators/${moderator.uid}`, { since: new Date() });
const team = `eq-${run}`, otherTeam = `otro-${run}`;
await seed(`teams/${team}`, { createdBy: coach.uid, name: 'Halcones', type: 'EQUIPO', categories: ['SUB-12'] });
await seed(`teams/${otherTeam}`, { createdBy: pending.uid, name: 'Ajeno', type: 'EQUIPO', categories: ['SUB-12'] });
for (const p of [p1, p2, noName]) await seed(`teamMemberships/${team}_${p.uid}`, { teamId: team, userId: p.uid, playerName: p.name, category: 'SUB-12' });
await seed(`teamMemberships/${otherTeam}_${outsider.uid}`, { teamId: otherTeam, userId: outsider.uid, playerName: 'Ajena', category: 'SUB-12' });
await seed(`guardianLinks/${p1.uid}_${guardian.uid}`, { playerId: p1.uid, guardianId: guardian.uid, inviteId: 'x', status: 'ACTIVE' });
const postDoc = (id, visibility, extra = {}) => seed(`communityPosts/${id}`, { authorId: p2.uid, authorName: 'Bea', authorRole: 'JUGADOR',
  content: 'Hola', visibility, createdAt: new Date(), active: true, moderated: false, ...extra });
const todos = `todos-${run}`, equipo = `equipo-${run}`, hidden = `oculta-${run}`;
await postDoc(todos, 'TODOS');
await postDoc(equipo, 'EQUIPO', { teamId: team });
await postDoc(hidden, 'TODOS', { moderated: true });
await seed(`communityPosts/${hidden}/comments/c-oculta`, { authorId: p1.uid, authorName: 'Paula Díaz', text: 'antes de ocultar', createdAt: new Date(), moderated: false });

let count = 0, failures = 0;
const out = (label, ok, detail = '') => { count++; if (!ok) failures++; console.log(`${ok ? 'PASS' : 'FAIL'} ${count}. ${label}${detail ? ': ' + detail : ''}`); };
const code = (p) => p.then(() => 'OK', (e) => e.code ?? e.message ?? String(e));
const expectOk = async (label, p) => { const c = await code(p); out(label, c === 'OK', c); };
const expectDenied = async (label, p) => { const c = await code(p); out(label, c === 'permission-denied', c); };
const size = (q) => getDocs(q).then((s) => `${s.size} docs`, (e) => e.code);
const checkQ = async (label, q, want) => { const r = await size(q); out(`${label} → ${want}`, r === want, r); };

// ---------- Réplica de CommunityRepository ----------
const C = (u, post) => collection(u.db, 'communityPosts', post, 'comments');
const comment = (u, post, extra = {}) => addDoc(C(u, post), { authorId: u.uid, authorName: u.display, text: 'Buen partido', createdAt: serverTimestamp(), moderated: false, ...extra });
const like = (u, post, id = u.uid, extra = {}) => setDoc(doc(u.db, 'communityPosts', post, 'likes', id), { userId: u.uid, createdAt: serverTimestamp(), ...extra });
const unlike = (u, post, id = u.uid) => deleteDoc(doc(u.db, 'communityPosts', post, 'likes', id));
const report = (u, post, commentId, reason = 'Acoso o insulto') => setDoc(doc(u.db, 'communityPosts', post, 'comments', commentId, 'reports', u.uid), { reporterId: u.uid, reason, createdAt: serverTimestamp() });
const visibleQ = (u, post) => query(C(u, post), where('moderated', '==', false));
const ownQ = (u, post) => query(C(u, post), where('authorId', '==', u.uid));

// ---------- 1. Comentar ----------
console.log('\n--- 1. Comentar ---');
let p1Comment, p2Comment;
await expectOk('jugadora comenta en TODOS con el nombre de su perfil', comment(p1, todos).then((r) => { p1Comment = r.id; }));
await expectOk('otra jugadora comenta en TODOS', comment(p2, todos).then((r) => { p2Comment = r.id; }));
await expectOk('jugadora comenta en EQUIPO de su equipo (5 documentos)', comment(p1, equipo));
await expectOk('entrenadora aprobada comenta', comment(coach, todos));
await expectOk('jugadora sin nombre en el perfil comenta como "Usuario SportPro"', comment(noName, todos));
await expectDenied('entrenadora NO aprobada no comenta (TODOS)', comment(pending, todos));
await expectDenied('nombre de otra persona', comment(p1, todos, { authorName: 'Bea' }));
await expectDenied('nombre sacado del correo', comment(p1, todos, { authorName: p1.email.split('@')[0] }));
await expectDenied('"Usuario SportPro" si el perfil sí tiene nombre', comment(p1, todos, { authorName: 'Usuario SportPro' }));
await expectOk('comentario de 300 caracteres', comment(p1, todos, { text: 'x'.repeat(300) }));
await expectDenied('comentario de 301 caracteres', comment(p1, todos, { text: 'x'.repeat(301) }));
await expectDenied('comentario vacío', comment(p1, todos, { text: '' }));
await expectDenied('authorId de otra persona', comment(p1, todos, { authorId: p2.uid }));
await expectDenied('comentario que nace oculto', comment(p1, todos, { moderated: true }));
await expectDenied('tutora no comenta en TODOS', comment(guardian, todos));
await expectOk('tutora guarda su acceso al equipo de su jugadora (US-17)', setDoc(doc(guardian.db, 'announcementGuardians', `${team}_${guardian.uid}`), { teamId: team, playerId: p1.uid, updatedAt: serverTimestamp() }));
await checkQ('tutora SÍ lee los comentarios de EQUIPO', visibleQ(guardian, equipo), '1 docs');
await expectDenied('tutora no comenta en EQUIPO', comment(guardian, equipo));
await expectDenied('jugadora de otro equipo no comenta en EQUIPO ajeno (no visible)', comment(outsider, equipo));
await checkQ('jugadora de otro equipo no lee comentarios de EQUIPO ajeno', visibleQ(outsider, equipo), 'permission-denied');
await expectDenied('nadie comenta en una publicación oculta', comment(p1, hidden));
await expectDenied('la autora no edita su comentario', updateDoc(doc(p1.db, 'communityPosts', todos, 'comments', p1Comment), { text: 'otro' }));
await expectDenied('la autora no borra su comentario', deleteDoc(doc(p1.db, 'communityPosts', todos, 'comments', p1Comment)));

// ---------- 2. Me gusta ----------
console.log('\n--- 2. Me gusta ---');
await expectOk('jugadora da Me gusta', like(p1, todos));
await expectDenied('doble Me gusta (segundo set = update) denegado', like(p1, todos));
await expectOk('otra jugadora da Me gusta', like(p2, todos));
await expectDenied('Me gusta con el ID de otra persona', like(p1, todos, p2.uid));
await expectDenied('Me gusta con userId de otra persona', like(p1, equipo, p1.uid, { userId: p2.uid }));
await expectDenied('quitar el Me gusta de otra persona', unlike(p1, todos, p2.uid));
await expectOk('quitar el Me gusta propio', unlike(p1, todos));
await expectOk('volver a darlo tras quitarlo', like(p1, todos));
await checkQ('el contador cuenta un documento por persona', collection(p1.db, 'communityPosts', todos, 'likes'), '2 docs');
await expectOk('entrenadora aprobada da Me gusta', like(coach, todos));
await expectDenied('entrenadora NO aprobada no da Me gusta', like(pending, todos));
await expectDenied('tutora no da Me gusta en TODOS', like(guardian, todos));
await expectDenied('tutora no da Me gusta en EQUIPO', like(guardian, equipo));
await expectDenied('jugadora de otro equipo no da Me gusta en EQUIPO ajeno', like(outsider, equipo));
await expectDenied('nadie da Me gusta en una publicación oculta', like(p1, hidden));

// ---------- 3. Reportes ----------
console.log('\n--- 3. Reportar ---');
await expectOk('jugadora reporta un comentario ajeno con motivo', report(p1, todos, p2Comment));
await expectDenied('no puede reportar dos veces el mismo comentario', report(p1, todos, p2Comment, 'Spam'));
await expectDenied('no puede reportar su propio comentario', report(p1, todos, p1Comment));
await expectDenied('motivo vacío', report(coach, todos, p2Comment, ''));
await expectDenied('motivo de 501 caracteres', report(coach, todos, p2Comment, 'x'.repeat(501)));
await expectOk('entrenadora aprobada reporta (motivo "Datos personales")', report(coach, todos, p2Comment, 'Datos personales'));
await expectDenied('tutora no reporta', report(guardian, todos, p2Comment));
await expectDenied('entrenadora NO aprobada no reporta', report(pending, todos, p2Comment));
await expectDenied('jugadora no lee los reportes', getDoc(doc(p1.db, 'communityPosts', todos, 'comments', p2Comment, 'reports', p1.uid)));
await expectOk('la moderadora lee los reportes', getDoc(doc(moderator.db, 'communityPosts', todos, 'comments', p2Comment, 'reports', p1.uid)));

// ---------- 4. Comentarios ocultos ----------
console.log('\n--- 4. Comentarios ocultos ---');
await expectOk('la moderadora oculta el comentario de la jugadora', updateDoc(doc(moderator.db, 'communityPosts', todos, 'comments', p1Comment), { moderated: true }));
await expectOk('la autora lee su comentario oculto por ID', getDoc(doc(p1.db, 'communityPosts', todos, 'comments', p1Comment)));
const own = await getDocs(ownQ(p1, todos)).then((s) => s.docs.some((d) => d.id === p1Comment && d.get('moderated') === true), (e) => e.code);
out('la autora lo ve en su consulta propia (authorId == uid)', own === true, String(own));
await expectDenied('un tercero NO lee por ID el comentario oculto', getDoc(doc(p2.db, 'communityPosts', todos, 'comments', p1Comment)));
const others = await getDocs(visibleQ(p2, todos)).then((s) => s.docs.some((d) => d.id === p1Comment), (e) => e.code);
out('un tercero no lo recibe en la lista (moderated == false)', others === false, String(others));
await checkQ('un tercero no puede pedir los comentarios de otra persona sin filtrar moderated', query(C(p2, todos), where('authorId', '==', p1.uid)), 'permission-denied');
await expectOk('la moderadora lee el comentario oculto', getDoc(doc(moderator.db, 'communityPosts', todos, 'comments', p1Comment)));
await expectDenied('nadie reporta un comentario oculto', report(p2, todos, p1Comment));
await checkQ('la autora de un post oculto lee sus comentarios', visibleQ(p2, hidden), '1 docs');
await checkQ('un tercero no lee comentarios de un post oculto', visibleQ(outsider, hidden), 'permission-denied');

console.log(failures === 0 ? `\nUS-19 comments client flow verified in demo-sportpro only: ${count} de ${count} OK.` : `\n${failures} de ${count} comprobaciones fallidas.`);
process.exit(failures === 0 ? 0 : 1);
