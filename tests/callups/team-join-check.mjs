import {root,auth,request,set,account,coach,player,other,wrongCategory,expect,fields} from './emulator-check.mjs';
const prefix='projects/demo-sportpro/databases/(default)/documents/';
const transform=fieldPath=>({fieldPath,setToServerValue:'REQUEST_TIME'});
const write=(path,data,timestamps=[],mask)=>({update:{name:prefix+path,fields:fields(data)},
  ...(mask?{updateMask:{fieldPaths:mask}}:{}),
  ...(timestamps.length?{updateTransforms:timestamps.map(transform)}:{})});
const commit=(writes,token)=>request(`${root}:commit`,{writes},token);
for(const path of ['teamJoinRequests/join-team_'+player.id,'teamJoinRequests/join-team_'+other.id,
  'teamJoinRequests/join-team_'+wrongCategory.id,'teamMemberships/join-team_'+player.id,
  'teamJoinCodes/join-code-local','matchCallupPlayers/join-match_'+player.id,'matchAvailability/join-match_'+player.id,
  'matchFixtures/join-match','teams/join-team']) await request(`${root}/${path}`,undefined,'owner','DELETE');
await set('teams/join-team',{name:'Equipo de ingreso',type:'EQUIPO',createdBy:coach.id,categories:['SUB-17']});
const code={teamId:'join-team',teamName:'Equipo de ingreso',categories:['SUB-17'],createdBy:coach.id,expiresAt:new Date(Date.now()+30*60*1000)};
expect(await commit([write('teamJoinCodes/join-code-local',code,['createdAt'])],coach.token),200,'US10 integration coach creates reusable QR');
await request(`${auth}/accounts:update?key=local`,{localId:player.id,emailVerified:true},'owner');
const verifiedPlayer=await account('jugador@sportpro.test');
const requestData=(actor,name)=>({teamId:'join-team',teamName:'Equipo de ingreso',playerId:actor.id,
  codeId:'join-code-local',playerName:name,category:'SUB-17',status:'PENDING'});
expect(await request(`${root}/teamJoinRequests/join-team_${player.id}`,undefined,verifiedPlayer.token,'GET'),403,'US10 integration absent request cannot be pre-read');
expect(await set(`teamMemberships/join-team_${player.id}`,{teamId:'join-team',userId:player.id,joinCodeId:'join-code-local',playerName:'Jugador',category:'SUB-17'},verifiedPlayer.token,true),403,'US10 integration direct membership bypass rejected');
expect(await commit([write(`teamJoinRequests/join-team_${player.id}`,requestData(player,'Jugador nuevo'),['createdAt'])],verifiedPlayer.token),200,'US10 integration player requests team membership');
expect(await request(`${root}/teamJoinRequests/join-team_${player.id}`,undefined,other.token,'DELETE'),403,'US10 integration outsider cannot cancel another request');
const coachList={structuredQuery:{from:[{collectionId:'teamJoinRequests'}],where:{fieldFilter:{field:{fieldPath:'teamId'},op:'EQUAL',value:{stringValue:'join-team'}}}}};
expect(await request(`${root}:runQuery`,coachList,coach.token),200,'US10 integration coach sees join request');
const approval=write(`teamJoinRequests/join-team_${player.id}`,{status:'APPROVED'},['resolvedAt'],['status','resolvedAt']);
const member=write(`teamMemberships/join-team_${player.id}`,{teamId:'join-team',userId:player.id,
  requestId:`join-team_${player.id}`,playerName:'Jugador nuevo',category:'SUB-17'},['acceptedAt']);
expect(await commit([approval,member],coach.token),200,'US10 integration coach approves player atomically');
const kickoff=new Date(Date.now()+86400000),deadline=new Date(Date.now()+3600000);
expect(await set('matchFixtures/join-match',{teamId:'join-team',ownerId:coach.id,category:'SUB-17',opponent:'Rival de ingreso',kickoff,responseDeadline:deadline},coach.token,true),200,'US10 integration coach creates match for joined player');
expect(await set(`matchCallupPlayers/join-match_${player.id}`,{teamId:'join-team',ownerId:coach.id,matchId:'join-match',
  playerId:player.id,selected:true,encounter:{opponent:'Rival de ingreso',category:'SUB-17',kickoff,responseDeadline:deadline}},coach.token,true),200,'US10 integration joined player receives callup');
expect(await set(`matchAvailability/join-match_${player.id}`,{teamId:'join-team',ownerId:coach.id,matchId:'join-match',
  playerId:player.id,status:'AVAILABLE',respondedBy:player.id},verifiedPlayer.token,true),200,'US10 integration joined player responds');
await request(`${auth}/accounts:update?key=local`,{localId:other.id,emailVerified:true},'owner');
const verifiedOther=await account('otro@sportpro.test');
expect(await commit([write(`teamJoinRequests/join-team_${other.id}`,requestData(other,'Otro jugador'),['createdAt'])],verifiedOther.token),200,'US10 integration second player requests same QR');
expect(await request(`${root}/teamJoinRequests/join-team_${other.id}`,undefined,verifiedOther.token,'DELETE'),200,'US10 integration player cancels pending join request');
await request(`${auth}/accounts:update?key=local`,{localId:wrongCategory.id,emailVerified:true},'owner');
const verifiedThird=await account('otra.categoria@sportpro.test');
expect(await commit([write(`teamJoinRequests/join-team_${wrongCategory.id}`,requestData(wrongCategory,'Tercer jugador'),['createdAt'])],verifiedThird.token),200,'US10 integration third player requests team');
expect(await request(`${root}/teamJoinRequests/join-team_${wrongCategory.id}`,undefined,coach.token,'DELETE'),200,'US10 integration owner can clean pending request');
console.log('US10 membership to callup to response verified locally.');
