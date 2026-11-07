// Node's built-in fetch only. All requests are restricted to local emulators.
import assert from 'node:assert/strict';
const project = 'demo-sportpro';
const root = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents`;
const auth = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const password = 'SportProLocal2026!';
async function request(url, body, token = 'owner', method = 'POST') {
  const response = await fetch(url, {method, headers: {'Content-Type':'application/json', ...(token ? {Authorization:`Bearer ${token}`} : {})}, ...(body === undefined ? {} : {body:JSON.stringify(body)})});
  return {status:response.status, body:await response.json()};
}
function value(v) {
  if (v instanceof Date) return {timestampValue:v.toISOString()};
  if (Array.isArray(v)) return {arrayValue:{values:v.map(value)}};
  if (v === null) return {nullValue:null};
  if (typeof v === 'boolean') return {booleanValue:v};
  if (typeof v === 'number') return {integerValue:String(v)};
  if (typeof v === 'object') return {mapValue:{fields:fields(v)}};
  return {stringValue:v};
}
function fields(data) { return Object.fromEntries(Object.entries(data).map(([k,v]) => [k,value(v)])); }
async function set(path, data, token='owner', timestamp=false) {
  return request(`${root}:commit`, {writes:[{update:{name:`projects/${project}/databases/(default)/documents/${path}`, fields:fields(data)}, ...(timestamp ? {updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'}]} : {})}]}, token);
}
async function account(email) {
  let r = await request(`${auth}/accounts:signUp?key=local`, {email,password,returnSecureToken:true}, null);
  if (r.status !== 200) r = await request(`${auth}/accounts:signInWithPassword?key=local`, {email,password,returnSecureToken:true}, null);
  assert.equal(r.status,200,JSON.stringify(r.body));
  assert.ok(r.body.idToken, "Missing user token: never run authorization tests as admin");
  return {id:r.body.localId, token:r.body.idToken};
}
// Clean only the deterministic test lineups so repeated runs can withdraw test callups.
for (const matchId of ['local-match','lineup-other']) {
  for (const child of ['players','slots']) {
    const result = await request(`${root}/matchLineups/${matchId}/${child}`,undefined,'owner','GET');
    for (const doc of result.body.documents ?? []) await request(`http://127.0.0.1:8080/v1/${doc.name}`,undefined,'owner','DELETE');
  }
  await request(`${root}/matchLineups/${matchId}`,undefined,'owner','DELETE');
}
const coach=await account('entrenador@sportpro.test');
const player=await account('jugador@sportpro.test');
const other=await account('otro@sportpro.test');
const wrongCategory=await account('otra.categoria@sportpro.test');
for (const [u,role,email] of [[coach,'ENTRENADOR','entrenador@sportpro.test'],[player,'JUGADOR','jugador@sportpro.test'],[other,'JUGADOR','otro@sportpro.test'],[wrongCategory,'JUGADOR','otra.categoria@sportpro.test']]) {
  assert.equal((await set(`users/${u.id}`,{email,roles:[role],profileComplete:true})).status,200);
}
await set(`coachApprovals/${coach.id}`,{approved:true});
await set('teams/local-team',{name:'Equipo de prueba Brayan',type:'EQUIPO',createdBy:coach.id,categories:['SUB-17','SUB-15']});
await set(`teamMemberships/local-team_${player.id}`,{teamId:'local-team',userId:player.id,playerName:'Jugador de prueba',category:'SUB-17'});
await set(`teamMemberships/local-team_${wrongCategory.id}`,{teamId:'local-team',userId:wrongCategory.id,playerName:'Jugador Sub-15',category:'SUB-15'});
for (const id of ['local-match','forbidden-match','past-match','wrong-category']) {
  await request(`${root}/matchFixtures/${id}`,undefined,'owner','DELETE');
}
const fixture = {teamId:'local-team',ownerId:coach.id,category:'SUB-17',opponent:'Rival de prueba',kickoff:new Date(Date.now()+86400000)};
const expect = (r,status,label) => {assert.equal(r.status,status,`${label}: ${JSON.stringify(r.body)}`);console.log(`PASS ${label}`);};
expect(await set('matchFixtures/local-match',fixture,coach.token,true),200,'coach creates fixture');
expect(await set('matchFixtures/forbidden-match',{...fixture,ownerId:other.id},other.token,true),403,'outsider cannot create fixture');
expect(await set('matchFixtures/past-match',{...fixture,kickoff:new Date(0)},coach.token,true),403,'past fixture rejected');
expect(await set('matchFixtures/wrong-category',{...fixture,category:'INVALID'},coach.token,true),403,'unknown team category rejected');
const selection = p => ({teamId:'local-team',ownerId:coach.id,matchId:'local-match',playerId:p.id,selected:true});
expect(await set(`matchCallupPlayers/local-match_${player.id}`,selection(player),coach.token,true),200,'coach selects team player');
expect(await set(`matchCallupPlayers/local-match_${wrongCategory.id}`,selection(wrongCategory),coach.token,true),403,'wrong category rejected');
expect(await set(`matchCallupPlayers/local-match_${other.id}`,selection(other),coach.token,true),403,'outsider selection rejected');
expect(await set(`matchCallupPlayers/local-match_${player.id}`,selection(player),player.token,true),403,'player cannot edit selection');
expect(await request(`${root}/matchCallupPlayers/local-match_${player.id}`,undefined,player.token,'GET'),200,'selected player reads own callup');
expect(await request(`${root}/matchCallupPlayers/local-match_${player.id}`,undefined,other.token,'GET'),403,'outsider cannot read callup');
expect(await request(`${root}/matchFixtures/local-match`,undefined,player.token,'GET'),200,'player reads encounter');
expect(await request(`${root}/matchFixtures/local-match`,undefined,other.token,'GET'),403,'outsider cannot read encounter');
const query = (collection, field, value) => ({structuredQuery:{from:[{collectionId:collection}],where:{fieldFilter:{field:{fieldPath:field},op:'EQUAL',value:{stringValue:value}}}}});
expect(await request(`${root}:runQuery`,query('matchFixtures','teamId','local-team'),coach.token),200,'coach fixture selector query');
expect(await request(`${root}:runQuery`,query('matchCallupPlayers','playerId',player.id),player.token),200,'player live list query');
const coachQuery=query('matchCallupPlayers','ownerId',coach.id);
coachQuery.structuredQuery.where={compositeFilter:{op:'AND',filters:[coachQuery.structuredQuery.where,query('matchCallupPlayers','matchId','local-match').structuredQuery.where]}};
expect(await request(`${root}:runQuery`,coachQuery,coach.token),200,'coach selection query');
expect(await set(`matchCallupPlayers/local-match_${player.id}`,{...selection(player),selected:false},coach.token,true),200,'coach withdraws callup');
let r = await request(`${root}/matchCallupPlayers/local-match_${player.id}`,undefined,player.token,'GET');
assert.equal(r.body.fields.selected.booleanValue,false); console.log('PASS player sees withdrawal');
expect(await set('matchFixtures/local-match',{...fixture,opponent:'Rival actualizado'},coach.token,true),200,'coach edits match');
r=await request(`${root}/matchFixtures/local-match`,undefined,player.token,'GET');assert.equal(r.body.fields.opponent.stringValue,'Rival actualizado');console.log('PASS player sees updated opponent');
expect(await set('matchFixtures/local-match',{...fixture,category:'SUB-15'},coach.token,true),403,'cannot switch category after creating match');
expect(await set(`matchCallupPlayers/local-match_${player.id}`,selection(player),coach.token,true),200,'restore visible test callup');
expect(await request(`${root}/matchCallupPlayers/local-match_${player.id}`,undefined,null,'GET'),403,'anonymous access rejected');
expect(await set('matchCallupPlayers/wrong-id',selection(player),coach.token,true),403,'selection document identity enforced');
await set(`teamMemberships/local-team_${player.id}`,{teamId:'local-team',userId:player.id,playerName:'Jugador de prueba',category:'SUB-15'});
expect(await set(`matchCallupPlayers/local-match_${player.id}`,selection(player),coach.token,true),403,'membership change is revalidated at save');
expect(await set(`matchCallupPlayers/local-match_${player.id}`,{...selection(player),selected:false},coach.token,true),200,'invalidated player can still be withdrawn');
expect(await request(`${root}/matchFixtures/local-match`,undefined,player.token,'GET'),403,'changed category loses encounter access');
await set(`teamMemberships/local-team_${player.id}`,{teamId:'local-team',userId:player.id,playerName:'Jugador de prueba',category:'SUB-17'});
await set('matchFixtures/local-match',{...fixture,kickoff:new Date(0)},'owner',true);
expect(await set(`matchCallupPlayers/local-match_${player.id}`,selection(player),coach.token,true),403,'cannot edit callup after kickoff');
await set('matchFixtures/local-match',{...fixture,opponent:'Rival actualizado'},'owner',true);
expect(await set(`matchCallupPlayers/local-match_${player.id}`,selection(player),coach.token,true),200,'restore local demonstration');
console.log('\nLocal accounts: entrenador@sportpro.test / jugador@sportpro.test');
console.log('Local password: '+password);

export {project, root, auth, password, request, fields, set, account, coach, player, other, wrongCategory, fixture, selection, expect, query};
