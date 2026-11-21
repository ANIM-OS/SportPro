import assert from 'node:assert/strict';
import {root,auth,request,set,account,coach,player,other,fixture,selection,expect,query,fields} from './emulator-check.mjs';

const guardianEmail='tutor@sportpro.test';
let guardian=await account(guardianEmail);
await set(`users/${guardian.id}`,{email:guardianEmail,roles:['PADRE / TUTOR'],profileComplete:true,firstName:'Tutor',lastName:'Local'});
const linkId=`${player.id}_${guardian.id}`;
const inviteId='local-invite';
await request(`${root}/guardianLinks/${linkId}`,undefined,'owner','DELETE');
await request(`${root}/guardianInvites/${inviteId}`,undefined,'owner','DELETE');
await set(`playerGuardians/${player.id}_${guardianEmail}`,{playerId:player.id,playerName:'Jugador de prueba',guardianEmail,createdBy:player.id});

const id=`local-match_${player.id}`;
const deadline=new Date(Date.now()+3600000);
await set('matchFixtures/local-match',{...fixture,responseDeadline:deadline},'owner',true);
const details={category:fixture.category,opponent:fixture.opponent,kickoff:fixture.kickoff,responseDeadline:deadline};
expect(await set(`matchCallupPlayers/${id}`,{...selection(player),encounter:details},coach.token,true),200,'US09 saves encounter details for guardian');
const response=(actor,status='AVAILABLE')=>({teamId:fixture.teamId,matchId:'local-match',ownerId:coach.id,playerId:player.id,status,respondedBy:actor.id});
expect(await set(`matchAvailability/${id}`,response(player),player.token,true),200,'US10 player confirms availability');
let stored=await request(`${root}/matchAvailability/${id}`,undefined,player.token,'GET');
assert.equal(stored.body.fields.status.stringValue,'AVAILABLE');console.log('PASS US10 availability persisted after read');
expect(await set(`matchAvailability/${id}`,response(player,'UNAVAILABLE'),player.token,true),200,'US10 player changes response before deadline');
expect(await set(`matchAvailability/${id}`,response(other),other.token,true),403,'US10 another player cannot respond');
expect(await set(`matchAvailability/${id}`,response(coach),coach.token,true),403,'US10 coach cannot impersonate player');
expect(await set(`matchAvailability/${id}`,response(player,'UNKNOWN'),player.token,true),403,'US10 invalid answer rejected');

await request(`${auth}/accounts:update?key=local`,{localId:guardian.id,emailVerified:true},'owner');
guardian=await account(guardianEmail);
expect(await set(`matchAvailability/${id}`,response(guardian),guardian.token,true),403,'US10 legacy email link alone cannot authorize guardian');
await request(`${auth}/accounts:update?key=local`,{localId:player.id,emailVerified:true},'owner');
const verifiedPlayer=await account('jugador@sportpro.test');
const path=p=>`projects/demo-sportpro/databases/(default)/documents/${p}`;
const transform=fieldPath=>({fieldPath,setToServerValue:'REQUEST_TIME'});
const write=(p,data,timestamps=[],mask)=>({update:{name:path(p),fields:fields(data)},
  ...(mask?{updateMask:{fieldPaths:mask}}:{}),
  ...(timestamps.length?{updateTransforms:timestamps.map(transform)}:{})});
const commit=(writes,token)=>request(`${root}:commit`,{writes},token);
const expiresAt=new Date(Date.now()+30*60*1000);
expect(await commit([write(`guardianInvites/${inviteId}`,{playerId:player.id,playerName:'Jugador de prueba',status:'OPEN',expiresAt},['createdAt'])],verifiedPlayer.token),200,'US10 player creates short-lived invite');
expect(await request(`${root}/guardianInvites/${inviteId}`,undefined,guardian.token,'GET'),200,'US10 verified guardian reads open invite');
const claim=write(`guardianInvites/${inviteId}`,{status:'CLAIMED',guardianId:guardian.id,guardianName:guardianEmail},['claimedAt'],['status','guardianId','guardianName','claimedAt']);
expect(await commit([claim],guardian.token),200,'US10 guardian claims invitation');
expect(await request(`${root}:runQuery`,query('guardianInvites','guardianId',guardian.id),guardian.token),200,'US10 guardian sees pending confirmation');
expect(await set(`matchAvailability/${id}`,response(guardian),guardian.token,true),403,'US10 claim alone cannot respond before player confirmation');
const activate=write(`guardianInvites/${inviteId}`,{status:'ACTIVE'},['resolvedAt'],['status','resolvedAt']);
const link=write(`guardianLinks/${linkId}`,{playerId:player.id,guardianId:guardian.id,inviteId,status:'ACTIVE'},['activatedAt']);
expect(await commit([activate,link],verifiedPlayer.token),200,'US10 player confirms guardian atomically');
expect(await request(`${root}:runQuery`,query('guardianLinks','guardianId',guardian.id),guardian.token),200,'US10 guardian lists confirmed players');
expect(await request(`${root}:runQuery`,query('guardianLinks','playerId',player.id),verifiedPlayer.token),200,'US10 player lists confirmed guardians');
expect(await request(`${root}:runQuery`,query('matchCallupPlayers','playerId',player.id),guardian.token),200,'US10 confirmed guardian lists child callups');
expect(await set(`matchAvailability/${id}`,response(guardian),guardian.token,true),200,'US10 confirmed guardian responds');
stored=await request(`${root}/matchAvailability/${id}`,undefined,player.token,'GET');
assert.equal(stored.body.fields.respondedBy.stringValue,guardian.id);console.log('PASS US10 player sees guardian response');
await request(`${auth}/accounts:update?key=local`,{localId:guardian.id,emailVerified:false},'owner');
guardian=await account(guardianEmail);
expect(await set(`matchAvailability/${id}`,response(guardian),guardian.token,true),403,'US10 unverified guardian cannot respond despite active link');
await request(`${auth}/accounts:update?key=local`,{localId:guardian.id,emailVerified:true},'owner');
guardian=await account(guardianEmail);
const coachQuery=query('matchAvailability','ownerId',coach.id);
coachQuery.structuredQuery.where={compositeFilter:{op:'AND',filters:[coachQuery.structuredQuery.where,query('matchAvailability','matchId','local-match').structuredQuery.where,query('matchAvailability','teamId','local-team').structuredQuery.where]}};
expect(await request(`${root}:runQuery`,coachQuery,coach.token),200,'US10 coach lists persisted responses');
expect(await request(`${root}/matchAvailability/${id}`,undefined,other.token,'GET'),403,'US10 unrelated user cannot read response');
const revoke=write(`guardianLinks/${linkId}`,{status:'REVOKED',revokedBy:player.id},['revokedAt'],['status','revokedBy','revokedAt']);
expect(await commit([revoke],verifiedPlayer.token),200,'US10 player revokes guardian');
expect(await set(`matchAvailability/${id}`,response(guardian),guardian.token,true),403,'US10 revoked guardian rejected');
const reinviteId='local-reinvite';
await request(`${root}/guardianInvites/${reinviteId}`,undefined,'owner','DELETE');
expect(await commit([write(`guardianInvites/${reinviteId}`,{playerId:player.id,playerName:'Jugador de prueba',status:'OPEN',expiresAt:new Date(Date.now()+30*60*1000)},['createdAt'])],verifiedPlayer.token),200,'US10 player creates new invite after revocation');
expect(await commit([write(`guardianInvites/${reinviteId}`,{status:'CLAIMED',guardianId:guardian.id,guardianName:guardianEmail},['claimedAt'],['status','guardianId','guardianName','claimedAt'])],guardian.token),200,'US10 guardian claims renewed invite');
expect(await commit([
  write(`guardianInvites/${reinviteId}`,{status:'ACTIVE'},['resolvedAt'],['status','resolvedAt']),
  write(`guardianLinks/${linkId}`,{playerId:player.id,guardianId:guardian.id,inviteId:reinviteId,status:'ACTIVE'},['activatedAt'])
],verifiedPlayer.token),200,'US10 player can reauthorize revoked guardian');
await set(`matchCallupPlayers/${id}`,{...selection(player),selected:false,encounter:details},'owner',true);
expect(await set(`matchAvailability/${id}`,response(player),player.token,true),403,'US10 withdrawn callup cannot respond');
await set(`matchCallupPlayers/${id}`,{...selection(player),encounter:details},'owner',true);
await set('matchFixtures/local-match',{...fixture,responseDeadline:new Date(Date.now()-1000)},'owner',true);
expect(await set(`matchAvailability/${id}`,response(player),player.token,true),403,'US10 server rejects late response');
await set('matchFixtures/local-match',{...fixture,responseDeadline:new Date(fixture.kickoff.getTime()+1000)},coach.token,true).then(r=>expect(r,403,'US10 deadline cannot exceed kickoff'));
await set('matchFixtures/local-match',{...fixture,responseDeadline:deadline},'owner',true);
console.log('US09/US10 dependencies verified in demo-sportpro only.');
export {guardian};
