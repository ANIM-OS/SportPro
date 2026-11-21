const assert = require("node:assert/strict");
const base = "http://127.0.0.1:8188/v1/projects/demo-sportpro/databases/(default)/documents";
const token = (uid) => [Buffer.from(JSON.stringify({alg:"none",typ:"JWT"})).toString("base64url"),Buffer.from(JSON.stringify({iss:"https://securetoken.google.com/demo-sportpro",aud:"demo-sportpro",sub:uid,user_id:uid,email:uid+"@example.com",...(uid==="guardianWithoutName"?{}:{name:uid}),email_verified:true,iat:Math.floor(Date.now()/1000),exp:Math.floor(Date.now()/1000)+3600,firebase:{sign_in_provider:"password"}})).toString("base64url"),""].join(".");
const path = p => "projects/demo-sportpro/databases/(default)/documents/"+p;
const stringValue = s => ({stringValue:s});
async function commit(writes, who) {
 const res = await fetch(base+":commit",{method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer "+(who==="admin"?"owner":token(who))},body:JSON.stringify({writes})});
 return {ok:res.ok,status:res.status,body:await res.text()};
}
async function read(p, who) {
 const res = await fetch(base+"/"+p,{headers:{Authorization:"Bearer "+token(who)}});
 return {ok:res.ok,status:res.status,body:await res.text()};
}
async function query(filters, who, collection = "communityPosts") {
 const where = {compositeFilter:{op:"AND",filters:filters.map(([field,value])=>({fieldFilter:{field:{fieldPath:field},op:field === "audience" ? "ARRAY_CONTAINS" : "EQUAL",value:stringValue(value)}}))}};
 const res = await fetch(base+":runQuery",{method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer "+token(who)},body:JSON.stringify({structuredQuery:{from:[{collectionId:collection}],where}})});
 return {ok:res.ok,status:res.status,body:await res.text()};
}
const write = (p,fields,timestamps=[]) => ({update:{name:path(p),fields}, ...(timestamps.length?{updateTransforms:timestamps.map(fieldPath=>({fieldPath,setToServerValue:"REQUEST_TIME"}))}:{})});
(async()=>{
 const future = new Date(Date.now()+1800000).toISOString();
 let r=await commit([
 write("admins/sportAdmin",{}),
 write("users/coach",{roles:{arrayValue:{values:[stringValue("ENTRENADOR")]}}}),
 write("coachApprovals/coach",{approved:{booleanValue:true}}),
 write("users/guardianWithoutName",{roles:{arrayValue:{values:[stringValue("PADRE / TUTOR")]}}}),
 write("users/player",{roles:{arrayValue:{values:[stringValue("JUGADOR")]}}}),
 write("users/guardian",{roles:{arrayValue:{values:[stringValue("PADRE / TUTOR")]}}}),
 write("attendance/record",{playerId:stringValue("player"),coachId:stringValue("coach")}),
 write("teams/team",{createdBy:stringValue("coach"),name:stringValue("Norte"),type:stringValue("EQUIPO"),categories:{arrayValue:{values:[stringValue("SUB-15")]}}}),
 write("teams/academy",{createdBy:stringValue("coach"),name:stringValue("Academia"),type:stringValue("ACADEMIA"),categories:{arrayValue:{values:[stringValue("SUB-15")]}}}),
 write("teams/otherAcademy",{createdBy:stringValue("outsider"),name:stringValue("Otra"),type:stringValue("ACADEMIA"),categories:{arrayValue:{values:[stringValue("SUB-15")]}}}),
 write("teamMemberships/team_member",{teamId:stringValue("team"),userId:stringValue("member")}),
 write("communityPosts/public",{visibility:stringValue("TODOS"),authorId:stringValue("player")}),
 write("communityPosts/private",{visibility:stringValue("EQUIPO"),teamId:stringValue("team"),authorId:stringValue("player")}),
 write("playerProfiles/player",{phone:stringValue("private")}),
 write("playerGuardians/player_guardian@example.com",{playerId:stringValue("player"),guardianEmail:stringValue("guardian@example.com")})
 ],"admin");assert.equal(r.ok,true,r.body);
 for (const [doc,who,allowed] of [
  ["communityPosts/public","outsider",true], ["communityPosts/private","outsider",false],
  ["communityPosts/private","member",true], ["communityPosts/private","coach",true],
  ["communityPosts/private","player",true], ["playerProfiles/player","player",true],
  ["playerProfiles/player","coach",false], ["playerProfiles/player","sportAdmin",false], ["playerProfiles/player","guardian",false]
 ]) { const result=await read(doc,who); assert.equal(result.ok,allowed,doc+" / "+who+" / "+result.body); }
 assert.equal((await read("adminAccess/sportAdmin","sportAdmin")).status,404,"An administrator can check their own access without a public document");
 assert.equal((await read("adminAccess/sportAdmin","player")).status,403,"A player cannot probe another UID");
 assert.equal((await read("adminAccess/player","player")).status,403,"A player has no admin access");
 assert.equal((await read("admins/sportAdmin","sportAdmin")).status,403,"The authority collection remains private");
 assert.equal((await commit([write("admins/player",{})],"player")).status,403,"A player cannot make themselves an administrator");
 assert.equal((await commit([write("adminAccess/sportAdmin",{})],"sportAdmin")).status,403,"The access probe has no client writes");
 for (const [filters,who,allowed] of [
  [[["visibility","TODOS"]],"outsider",true],
  [[["teamId","team"],["visibility","EQUIPO"]],"member",true],
  [[["teamId","team"],["visibility","EQUIPO"]],"outsider",false],
  [[["authorId","player"]],"player",true]
 ]) { const result=await query(filters,who); assert.equal(result.ok,allowed,"query / "+who+" / "+result.body); }
 r=await commit([write("teams/crossTeam",{createdBy:stringValue("coach"),name:stringValue("Sur"),type:stringValue("EQUIPO"),categories:{arrayValue:{values:[stringValue("SUB-15")]}}})],"admin");assert.equal(r.ok,true,r.body);
 const academyInvite=write("academyInvites/cross",{academyId:stringValue("otherAcademy"),teamId:stringValue("crossTeam"),academyOwnerId:stringValue("outsider"),coachId:stringValue("coach"),status:stringValue("PENDING"),expiresAt:{timestampValue:future}},["createdAt"]);
 r=await commit([academyInvite],"coach");assert.equal(r.ok,false,"Coach cannot initiate for academy");
 r=await commit([academyInvite],"outsider");assert.equal(r.ok,true,r.body);
 const acceptAcademy={update:{name:path("academyInvites/cross"),fields:{status:stringValue("ACCEPTED")}},updateMask:{fieldPaths:["status"]},updateTransforms:[{fieldPath:"resolvedAt",setToServerValue:"REQUEST_TIME"}]};
 const linkAcademy={update:{name:path("teams/crossTeam"),fields:{academyId:stringValue("otherAcademy"),academyInviteId:stringValue("cross")}},updateMask:{fieldPaths:["academyId","academyInviteId"]}};
 r=await commit([acceptAcademy],"coach");assert.equal(r.ok,false,"Acceptance requires team link");
 r=await commit([acceptAcademy,linkAcademy],"outsider");assert.equal(r.ok,false,"Academy cannot accept for coach");
 r=await commit([acceptAcademy,linkAcademy],"coach");assert.equal(r.ok,true,r.body);
 r=await commit([acceptAcademy,linkAcademy],"coach");assert.equal(r.ok,false,"Cannot consume invite twice");
 const unlinkAcademy={update:{name:path("teams/crossTeam"),fields:{}},updateMask:{fieldPaths:["academyId","academyInviteId"]}};
 r=await commit([unlinkAcademy],"outsider");assert.equal(r.ok,true,r.body);
 const setAcademy = id => ({update:{name:path("teams/team"),fields:{academyId:stringValue(id)}},updateMask:{fieldPaths:["academyId"]}});
 r=await commit([setAcademy("otherAcademy")],"coach");assert.equal(r.ok,false,"Cannot link another owner academy directly");
 r=await commit([setAcademy("academy")],"coach");assert.equal(r.ok,true,r.body);
 r=await commit([setAcademy("otherAcademy")],"coach");assert.equal(r.ok,false,"Cannot transfer directly");
 const teamCode=write("teamJoinCodes/code",{
  teamId:stringValue("team"),teamName:stringValue("Norte"),createdBy:stringValue("coach"),
  categories:{arrayValue:{values:[stringValue("SUB-15")]}},expiresAt:{timestampValue:future}
 },["createdAt"]);
 r=await commit([teamCode],"player");assert.equal(r.status,403,"A player cannot issue a team QR");
 r=await commit([teamCode],"coach");assert.equal(r.ok,true,r.body);
 r=await read("teamJoinCodes/code","player");assert.equal(r.ok,true,r.body);
 r=await read("teamJoinCodes/code","guardian");assert.equal(r.status,403,"A tutor cannot use a team QR as a player");
 r=await read("teams/team","player");assert.equal(r.status,403,"Scanning does not grant access to the team document before approval");
 const request=write("teamJoinRequests/team_player",{teamId:stringValue("team"),teamName:stringValue("Norte"),playerId:stringValue("player"),codeId:stringValue("code"),playerName:stringValue("Jugador"),category:stringValue("SUB-15"),status:stringValue("PENDING")},["createdAt"]);
 r=await commit([request],"player");assert.equal(r.ok,true,r.body);
 r=await query([["playerId","player"]],"player","teamJoinRequests");assert.equal(r.ok,true,r.body);
 r=await query([["playerId","player"]],"outsider","teamJoinRequests");assert.equal(r.ok,false,"Other users cannot list requests");
 r=await commit([request],"player");assert.equal(r.ok,false,"Cannot submit a second pending request");
 const member=write("teamMemberships/team_player",{teamId:stringValue("team"),userId:stringValue("player"),requestId:stringValue("team_player"),playerName:stringValue("Jugador"),category:stringValue("SUB-15")},["acceptedAt"]);
 r=await commit([member],"player");assert.equal(r.ok,false,"Player cannot create membership");
 const approval={update:{name:path("teamJoinRequests/team_player"),fields:{status:stringValue("APPROVED")}},updateMask:{fieldPaths:["status"]},updateTransforms:[{fieldPath:"resolvedAt",setToServerValue:"REQUEST_TIME"}]};
 r=await commit([approval,member],"outsider");assert.equal(r.ok,false,"Outsider cannot approve");
 r=await commit([approval],"coach");assert.equal(r.ok,false,"Approval must create membership atomically");
 r=await commit([approval,member],"coach");assert.equal(r.ok,true,r.body);
 r=await commit([approval,member],"coach");assert.equal(r.ok,false,"Cannot consume twice");
 r=await read("teams/team","player");assert.equal(r.ok,true,r.body);
 r=await query([["userId","player"]],"player","teamMemberships");assert.equal(r.ok,true,r.body);
 r=await query([["teamId","team"]],"coach","teamMemberships");assert.equal(r.ok,true,r.body);
 const legacyAssignment = category => ({update:{name:path("teamMemberships/team_member"),fields:{playerName:stringValue("Jugador existente"),category:stringValue(category)}},updateMask:{fieldPaths:["playerName","category"]}});
 r=await commit([legacyAssignment("SUB-15")],"member");assert.equal(r.status,403,"A legacy member cannot assign their own category");
 r=await commit([legacyAssignment("SUB-17")],"coach");assert.equal(r.status,403,"Coach must select a category offered by the team");
 r=await commit([legacyAssignment("SUB-15")],"coach");assert.equal(r.ok,true,r.body);
 r=await read("teamMemberships/team_member","member");assert.equal(JSON.parse(r.body).fields.category.stringValue,"SUB-15");
 for (const [id,category] of [["session","SUB-15"],["otherCategory","SUB-17"]]) {
  r=await commit([write("trainingSessions/"+id,{teamId:stringValue("team"),createdBy:stringValue("coach"),category:stringValue(category)})],"admin");
  assert.equal(r.ok,true,r.body);
 }
 const attendanceFor=(sessionId,status)=>write("attendance/"+sessionId+"_player",{
  sessionId:stringValue(sessionId),teamId:stringValue("team"),teamName:stringValue("Norte"),coachId:stringValue("coach"),
  playerId:stringValue("player"),playerName:stringValue("Jugador"),status:stringValue(status)
 },["updatedAt"]);
 r=await commit([attendanceFor("session","PRESENTE")],"player");assert.equal(r.status,403,"A player cannot mark attendance");
 r=await commit([attendanceFor("session","PRESENTE")],"guardian");assert.equal(r.status,403,"A guardian cannot mark attendance");
 r=await commit([attendanceFor("otherCategory","PRESENTE")],"coach");assert.equal(r.status,403,"Coach cannot mark a player outside the session category");
 r=await commit([attendanceFor("session","AUSENTE")],"coach");assert.equal(r.ok,true,r.body);
 r=await read("attendance/session_player","player");assert.equal(r.ok,true,r.body);assert.equal(JSON.parse(r.body).fields.status.stringValue,"AUSENTE");
 r=await commit([attendanceFor("session","JUSTIFICADO")],"coach");assert.equal(r.ok,true,r.body);
 r=await query([["playerId","player"]],"player","attendance");assert.equal(r.ok,true,r.body);
 r=await read("attendance/session_player","outsider");assert.equal(r.status,403,"Other accounts cannot read the record");
 const invitation=write("guardianInvites/invite",{playerId:stringValue("player"),playerName:stringValue("Player"),status:stringValue("OPEN"),expiresAt:{timestampValue:future}},["createdAt"]);
 r=await commit([invitation],"guardian");assert.equal(r.ok,false,"Guardian cannot initiate");
 r=await commit([invitation],"player");assert.equal(r.ok,true,r.body);
 r=await read("guardianInvites/invite","guardian");assert.equal(r.ok,true,r.body);
 r=await read("guardianInvites/invite","coach");assert.equal(r.status,403,"A coach who is not a tutor cannot open another player's invitation");
 r=await read("guardianInvites/invite","outsider");assert.equal(r.status,403,"An unrelated account cannot open a tutor QR");
 const claim={update:{name:path("guardianInvites/invite"),fields:{status:stringValue("CLAIMED"),guardianId:stringValue("guardian"),guardianName:stringValue("guardian")}},updateMask:{fieldPaths:["status","guardianId","guardianName"]},updateTransforms:[{fieldPath:"claimedAt",setToServerValue:"REQUEST_TIME"}]};
 const secondInvitation = {...invitation, update: {...invitation.update, name:path("guardianInvites/fallback")}};
 r=await commit([secondInvitation],"player");assert.equal(r.ok,true,r.body);
 const fallbackClaim=(label)=>({...claim,update:{...claim.update,name:path("guardianInvites/fallback"),fields:{
  ...claim.update.fields,guardianId:stringValue("guardianWithoutName"),guardianName:stringValue(label)
 }}});
 r=await commit([fallbackClaim("Another person")],"guardianWithoutName");assert.equal(r.status,403,"A guardian cannot substitute another account label");
 r=await commit([fallbackClaim("guardianWithoutName@example.com")],"guardianWithoutName");assert.equal(r.ok,true,r.body);
 r=await commit([claim],"guardian");assert.equal(r.ok,true,r.body);
 for (const [field,who] of [["playerId","player"],["guardianId","guardian"]]) {
  const result=await query([[field,who]],who,"guardianInvites");assert.equal(result.ok,true,result.body);
 }
 r=await commit([claim],"guardian");assert.equal(r.ok,false,"Invitation can only be claimed once");
 r=await read("attendance/record","guardian");assert.equal(r.ok,false,"Pending invitation grants no access");
 const confirm={update:{name:path("guardianInvites/invite"),fields:{status:stringValue("ACTIVE")}},updateMask:{fieldPaths:["status"]},updateTransforms:[{fieldPath:"resolvedAt",setToServerValue:"REQUEST_TIME"}]};
 const link=write("guardianLinks/player_guardian",{playerId:stringValue("player"),guardianId:stringValue("guardian"),inviteId:stringValue("invite"),status:stringValue("ACTIVE")},["activatedAt"]);
 r=await commit([confirm,link],"guardian");assert.equal(r.ok,false,"Guardian cannot confirm");
 r=await commit([confirm],"player");assert.equal(r.ok,false,"Confirmation must create link atomically");
 r=await commit([confirm,link],"player");assert.equal(r.ok,true,r.body);
 for (const [field,who] of [["playerId","player"],["guardianId","guardian"]]) {
  const result=await query([[field,who]],who,"guardianLinks");assert.equal(result.ok,true,result.body);
 }
 r=await read("attendance/record","guardian");assert.equal(r.ok,true,r.body);
 r=await query([["playerId","player"]],"guardian","attendance");assert.equal(r.ok,true,r.body);
 const revoke={update:{name:path("guardianLinks/player_guardian"),fields:{status:stringValue("REVOKED"),revokedBy:stringValue("player")}},updateMask:{fieldPaths:["status","revokedBy"]},updateTransforms:[{fieldPath:"revokedAt",setToServerValue:"REQUEST_TIME"}]};
 r=await commit([revoke],"player");assert.equal(r.ok,true,r.body);
 r=await read("attendance/record","guardian");assert.equal(r.ok,false,"Revocation removes access");
 r=await query([["playerId","player"]],"guardian","attendance");assert.equal(r.status,403,"Revoked guardian cannot query player attendance");
 r=await commit([
  {update:{name:path("teams/crossTeam"),fields:{academyId:stringValue("otherAcademy")}},updateMask:{fieldPaths:["academyId"]}},
  write("teamMemberships/crossTeam_player",{teamId:stringValue("crossTeam"),userId:stringValue("player")})
 ],"admin");assert.equal(r.ok,true,r.body);
 r=await query([["academyId","otherAcademy"]],"outsider","teams");assert.equal(r.ok,true,r.body);
 r=await query([["teamId","crossTeam"]],"outsider","teamMemberships");assert.equal(r.ok,true,r.body);
 r=await query([["teamId","crossTeam"]],"guardian","teamMemberships");assert.equal(r.ok,false,"Unrelated user cannot list academy roster");
 const feeId="otherAcademy_player_2026-11";
 const fee=write("academyFees/"+feeId,{academyId:stringValue("otherAcademy"),playerId:stringValue("player"),teamId:stringValue("crossTeam"),coachId:stringValue("coach"),period:stringValue("2026-11"),status:stringValue("PENDIENTE"),updatedBy:stringValue("outsider"),dueAt:{timestampValue:future}},["createdAt","updatedAt"]);
 const amount=write("academyFeeAmounts/"+feeId,{feeId:stringValue(feeId),academyId:stringValue("otherAcademy"),playerId:stringValue("player"),amountMinor:{integerValue:"15000"},currency:stringValue("PEN")});
 r=await commit([fee],"outsider");assert.equal(r.ok,false,"Fee must include amount atomically");
 r=await commit([fee,amount],"outsider");assert.equal(r.ok,true,r.body);
 r=await commit([fee,amount],"outsider");assert.equal(r.ok,false,"Cannot duplicate monthly period");
 r=await read("academyFees/"+feeId,"coach");assert.equal(r.ok,true,r.body);
 r=await read("academyFeeAmounts/"+feeId,"coach");assert.equal(r.ok,false,"Coach cannot read amount");
 r=await read("academyFeeAmounts/"+feeId,"player");assert.equal(r.ok,true,r.body);
 const paid={update:{name:path("academyFees/"+feeId),fields:{status:stringValue("PAGADA"),updatedBy:stringValue("outsider")}},updateMask:{fieldPaths:["status","updatedBy"]},updateTransforms:[{fieldPath:"updatedAt",setToServerValue:"REQUEST_TIME"}]};
 r=await commit([paid],"coach");assert.equal(r.ok,false,"Coach cannot update payment status");
 r=await commit([paid],"outsider");assert.equal(r.ok,true,r.body);
 const notice=write("internalNotices/notice",{teamId:stringValue("crossTeam"),playerId:stringValue("player"),authorId:stringValue("outsider"),scope:stringValue("ACADEMIA"),academyId:stringValue("otherAcademy"),title:stringValue("Entrenamiento"),content:stringValue("Horario actualizado"),audience:{arrayValue:{values:[stringValue("JUGADOR"),stringValue("TUTOR"),stringValue("ENTRENADOR")]}}},["createdAt"]);
 r=await commit([notice],"coach");assert.equal(r.ok,false,"Coach cannot publish as academy");
 r=await commit([notice],"outsider");assert.equal(r.ok,true,r.body);
 r=await read("internalNotices/notice","player");assert.equal(r.ok,true,r.body);
 r=await query([["teamId","crossTeam"],["playerId","player"],["scope","ACADEMIA"],["academyId","otherAcademy"],["audience","JUGADOR"]],"player","internalNotices");assert.equal(r.ok,true,r.body);
 r=await query([["authorId","outsider"]],"outsider","internalNotices");assert.equal(r.ok,true,r.body);
 r=await read("internalNotices/notice","coach");assert.equal(r.ok,false,"Coach needs academy delegation");
 const coachNoticeQuery = [["teamId","crossTeam"],["scope","ACADEMIA"],["academyId","otherAcademy"],["audience","ENTRENADOR"]];
 r=await query(coachNoticeQuery,"coach","internalNotices");assert.equal(r.ok,false,"Coach cannot query academy notices without delegation");
 const responsibility=write("academyResponsibilities/otherAcademy_crossTeam",{academyId:stringValue("otherAcademy"),teamId:stringValue("crossTeam"),coachId:stringValue("coach"),permissions:{arrayValue:{values:[stringValue("READ_NOTICES")]}},active:{booleanValue:true},linkId:stringValue(""),updatedBy:stringValue("outsider")},["updatedAt"]);
 r=await commit([responsibility],"coach");assert.equal(r.ok,false,"Coach cannot grant own academy authority");
 r=await commit([responsibility],"outsider");assert.equal(r.ok,true,r.body);
 r=await query([["academyId","otherAcademy"]],"outsider","academyResponsibilities");assert.equal(r.ok,true,r.body);
 r=await read("internalNotices/notice","coach");assert.equal(r.ok,true,r.body);
 r=await query(coachNoticeQuery,"coach","internalNotices");assert.equal(r.ok,true,r.body);
 const revokeResponsibility={update:{name:path("academyResponsibilities/otherAcademy_crossTeam"),fields:{active:{booleanValue:false},updatedBy:stringValue("outsider")}},updateMask:{fieldPaths:["active","updatedBy"]},updateTransforms:[{fieldPath:"updatedAt",setToServerValue:"REQUEST_TIME"}]};
 r=await commit([revokeResponsibility],"outsider");assert.equal(r.ok,true,r.body);
 r=await read("internalNotices/notice","coach");assert.equal(r.ok,false,"Revoking responsibility removes access");
 r=await query(coachNoticeQuery,"coach","internalNotices");assert.equal(r.ok,false,"Revoked delegation removes query access");
 r=await commit([responsibility],"outsider");assert.equal(r.ok,true,r.body);
 r=await read("internalNotices/notice","guardian");assert.equal(r.ok,false,"Revoked guardian cannot read notice");
 r=await commit([write("guardianLinks/player_guardian",{playerId:stringValue("player"),guardianId:stringValue("guardian"),inviteId:stringValue("invite"),status:stringValue("ACTIVE")},["activatedAt"])],"admin");assert.equal(r.ok,true,r.body);
 const teamAccess=write("guardianTeamAccess/crossTeam_guardian",{teamId:stringValue("crossTeam"),playerId:stringValue("player"),guardianId:stringValue("guardian")});
 r=await commit([teamAccess],"player");assert.equal(r.ok,false,"Player cannot impersonate guardian index");
 r=await commit([teamAccess],"guardian");assert.equal(r.ok,true,r.body);
 r=await read("teams/crossTeam","guardian");assert.equal(r.ok,true,r.body);
 r=await read("internalNotices/notice","guardian");assert.equal(r.ok,true,r.body);
 r=await query([["userId","player"]],"guardian","teamMemberships");assert.equal(r.ok,true,r.body);
 r=await query([["playerId","player"],["teamId","crossTeam"],["scope","ACADEMIA"],["academyId","otherAcademy"],["audience","TUTOR"]],"guardian","internalNotices");assert.equal(r.ok,true,r.body);
 r=await commit([revoke],"player");assert.equal(r.ok,true,r.body);
 r=await read("internalNotices/notice","guardian");assert.equal(r.ok,false,"Revocation removes notice access");
 r=await read("teams/crossTeam","guardian");assert.equal(r.ok,false,"Stale team access cannot bypass revoked guardianship");
 r=await commit([{delete:path("teamMemberships/crossTeam_player")}],"admin");assert.equal(r.ok,true,r.body);
 r=await read("internalNotices/notice","player");assert.equal(r.ok,false,"Leaving team removes notice access");
 r=await commit([{update:{name:path("teams/crossTeam"),fields:{}},updateMask:{fieldPaths:["academyId"]}}],"admin");assert.equal(r.ok,true,r.body);
 r=await read("internalNotices/notice","coach");assert.equal(r.ok,false,"Closing academy link removes academy notice access");
 const contacts=write("playerContacts/player",{
   phone:stringValue("1234"),emergencyName:stringValue("Contacto"),
   emergencyRelation:stringValue("Familiar"),emergencyPhone:stringValue("4567")
 });
 r=await commit([contacts],"coach");assert.equal(r.status,403,"Coach cannot edit private contacts");
 r=await commit([contacts],"player");assert.equal(r.ok,true,r.body);
 r=await read("playerContacts/player","coach");assert.equal(r.status,403,"Coach must establish scope through own team");
 const scope=(uid,teamId)=>write("playerContactAccess/"+uid+"/players/player",{teamId:stringValue(teamId)});
 r=await commit([scope("outsider","team")],"outsider");assert.equal(r.status,403,"Outsider cannot obtain contact scope");
 r=await commit([scope("coach","team")],"coach");assert.equal(r.ok,true,r.body);
 r=await read("playerContacts/player","coach");assert.equal(r.ok,true,r.body);
 r=await read("playerContacts/player","guardian");assert.equal(r.status,403,"Revoked tutor cannot get contact");

 r=await commit([write("users/emailPlayer",{roles:{arrayValue:{values:[stringValue("JUGADOR")]}}})],"admin");assert.equal(r.ok,true,r.body);
 const emailInvite=(category)=>write("teamInvites/byEmail",{
  teamId:stringValue("team"),teamName:stringValue("Norte"),invitedEmail:stringValue("emailPlayer@example.com"),
  invitedBy:stringValue("coach"),status:stringValue("PENDING"),...(category?{category:stringValue(category)}:{})
 },["createdAt"]);
 r=await commit([emailInvite("")],"coach");assert.equal(r.status,403,"Email invitations require a category");
 const legacyInvite = emailInvite("");
 legacyInvite.update.name = path("teamInvites/legacyEmail");
 r=await commit([legacyInvite],"admin");assert.equal(r.ok,true,r.body);
 r=await read("teamInvites/legacyEmail","emailPlayer");assert.equal(r.ok,true,r.body);
 const legacyReply = status => ({update:{name:path("teamInvites/legacyEmail"),fields:{status:stringValue(status)}},updateMask:{fieldPaths:["status"]}});
 const legacyEmailMember = write("teamMemberships/team_emailPlayer", {
  teamId:stringValue("team"),userId:stringValue("emailPlayer"),playerName:stringValue("Jugador"),
  inviteId:stringValue("legacyEmail"),category:stringValue("SUB-15")
 },["acceptedAt"]);
 r=await commit([legacyReply("ACCEPTED"),legacyEmailMember],"emailPlayer");assert.equal(r.status,403,"Legacy invite cannot grant a category chosen by the recipient");
 r=await commit([legacyReply("DECLINED")],"emailPlayer");assert.equal(r.ok,true,r.body);
 r=await commit([emailInvite("SUB-17")],"coach");assert.equal(r.status,403,"Category must belong to the team");
 r=await commit([emailInvite("SUB-15")],"coach");assert.equal(r.ok,true,r.body);
 const acceptEmail={update:{name:path("teamInvites/byEmail"),fields:{status:stringValue("ACCEPTED")}},updateMask:{fieldPaths:["status"]}};
 const emailMembership=(category)=>write("teamMemberships/team_emailPlayer",{
  teamId:stringValue("team"),userId:stringValue("emailPlayer"),playerName:stringValue("Jugador"),
  inviteId:stringValue("byEmail"),category:stringValue(category)
 },["acceptedAt"]);
 r=await commit([acceptEmail],"emailPlayer");assert.equal(r.status,403,"Email acceptance needs the membership in one batch");
 r=await commit([acceptEmail,emailMembership("SUB-17")],"emailPlayer");assert.equal(r.status,403,"Recipient cannot change the category");
 r=await commit([acceptEmail,emailMembership("SUB-15")],"emailPlayer");assert.equal(r.ok,true,r.body);

 const archive=(archived)=>({update:{name:path("teams/team"),fields:{archived:{booleanValue:archived}}},updateMask:{fieldPaths:["archived"]}});
 r=await commit([archive(true)],"player");assert.equal(r.status,403,"Member cannot archive the team");
 r=await commit([archive(true)],"coach");assert.equal(r.ok,true,r.body);
 r=await read("teams/team","player");assert.equal(r.ok,true,r.body);
 r=await read("teamJoinCodes/code","player");assert.equal(r.status,403,"Archived team cannot receive QR requests");
 r=await commit([{delete:path("teams/team")}],"coach");assert.equal(r.status,403,"Team cannot be deleted from the client");
 r=await commit([archive(false)],"coach");assert.equal(r.ok,true,r.body);
 r=await commit([{delete:path("teamMemberships/team_player")}],"player");assert.equal(r.ok,true,r.body);
 r=await read("attendance/record","player");assert.equal(r.ok,true,r.body);
 r=await read("playerContacts/player","coach");assert.equal(r.status,403,"Former coach loses access to contact after player leaves");

 const comment=write("communityPosts/private/comments/comment",{
   authorId:stringValue("member"),authorName:stringValue("Jugador"),body:stringValue("Comentario")
 },["createdAt"]);
 r=await commit([comment],"outsider");assert.equal(r.status,403,"Unrelated account cannot comment on restricted post");
 r=await commit([comment],"member");assert.equal(r.ok,true,r.body);
 r=await read("communityPosts/private/comments/comment","member");assert.equal(r.ok,true,r.body);
 r=await read("communityPosts/private/comments/comment","outsider");assert.equal(r.status,403,"Unrelated account cannot read restricted comments");
 const forgedComment=write("communityPosts/private/comments/forged",{
   authorId:stringValue("member"),authorName:stringValue("Jugador"),body:stringValue("Falso")
 },["createdAt"]);
 r=await commit([forgedComment],"coach");assert.equal(r.status,403,"Cannot impersonate a comment author");
 const reaction=write("communityPosts/private/reactions/member",{value:stringValue("👍")});
 r=await commit([reaction],"outsider");assert.equal(r.status,403,"Cannot create another user's reaction");
 r=await commit([reaction],"member");assert.equal(r.ok,true,r.body);
 r=await commit([write("communityPosts/private/reactions/member",{value:stringValue("invalid")})],"member");assert.equal(r.status,403,"Invalid reaction denied");
 r=await commit([{delete:path("communityPosts/private/comments/comment")}],"outsider");assert.equal(r.status,403,"Unrelated account cannot delete another user's comment");
 r=await commit([{delete:path("communityPosts/private/comments/comment")}],"member");assert.equal(r.ok,true,r.body);
 const deletion=write("accountDeletionRequests/player",{status:stringValue("PENDING")},["requestedAt"]);
 r=await commit([deletion],"outsider");assert.equal(r.status,403,"Cannot request deletion for another account");
 r=await commit([deletion],"player");assert.equal(r.ok,true,r.body);
 r=await read("accountDeletionRequests/player","outsider");assert.equal(r.status,403,"Deletion request is private");
 r=await commit([write("accountDeletionRequests/player",{status:stringValue("DONE")})],"player");assert.equal(r.status,403,"Player cannot approve their own deletion");
 console.log("QR, contacts, community, deletion and relationship checks passed");
})().catch(e=>{console.error(e);process.exitCode=1});
