export const ROLES = Object.freeze({HR: "HR", MANAGER: "MANAGER"});
export const STATUSES = Object.freeze(["EMAIL_CONTACT", "EMAIL_COMMUNICATING", "TIME_CONFIRMING", "INTERVIEW_CONFIRMED", "REJECTED", "SECOND_INVITATION_NO_RESPONSE"]);
const STATUS_LABELS = Object.freeze({EMAIL_CONTACT:"信件聯繫",EMAIL_COMMUNICATING:"信件溝通中",TIME_CONFIRMING:"時間確認中",INTERVIEW_CONFIRMED:"確認面試",REJECTED:"婉拒",SECOND_INVITATION_NO_RESPONSE:"二次邀約未回覆"});
const STORAGE_KEY = "interview-invitation-demo:v1";

export function statusLabel(status) { return STATUS_LABELS[status] ?? status; }
export function isClosed(record, now = new Date()) {
  if (record.status === "REJECTED") return true;
  if (record.status !== "INTERVIEW_CONFIRMED" || record.interviewCancelled || !record.interviewAt) return false;
  const interviewAt = new Date(record.interviewAt);
  return !Number.isNaN(interviewAt.getTime()) && interviewAt.getTime() < now.getTime();
}

function seedState() {
  return {schemaVersion:1,records:[
    {id:"REC-20261018-001",candidate:{name:"林晨宇",email:"chenyu.lin@example.invalid",phone:"0911-000-101",position:"後端工程師"},keyinAt:"2026-10-18T09:20:00+08:00",candidateRespondedAt:null,secondInvitationReminderAt:null,secondInvitationAt:null,secondInvitationResult:null,status:"EMAIL_CONTACT",interviewAt:null,interviewCancelled:false,rejectionType:null,rejectionReason:null,linkedOriginalCaseId:null,newCaseReason:null,statusHistory:[{fromStatus:null,toStatus:"EMAIL_CONTACT",actorRole:"HR",actorName:"Demo HR",at:"2026-10-18T09:20:00+08:00",reason:"建立招募紀錄"}]},
    {id:"REC-20261012-002",candidate:{name:"陳雨晴",email:"yuqing.chen@example.invalid",phone:"0922-000-202",position:"前端工程師"},keyinAt:"2026-10-12T14:05:00+08:00",candidateRespondedAt:"2026-10-13T10:30:00+08:00",secondInvitationReminderAt:null,secondInvitationAt:null,secondInvitationResult:null,status:"TIME_CONFIRMING",interviewAt:null,interviewCancelled:false,rejectionType:null,rejectionReason:null,linkedOriginalCaseId:null,newCaseReason:null,statusHistory:[{fromStatus:null,toStatus:"EMAIL_CONTACT",actorRole:"HR",actorName:"Demo HR",at:"2026-10-12T14:05:00+08:00",reason:"建立招募紀錄"},{fromStatus:"EMAIL_CONTACT",toStatus:"EMAIL_COMMUNICATING",actorRole:"HR",actorName:"Demo HR",at:"2026-10-13T10:30:00+08:00",reason:"求職者已回覆"},{fromStatus:"EMAIL_COMMUNICATING",toStatus:"TIME_CONFIRMING",actorRole:"HR",actorName:"Demo HR",at:"2026-10-14T11:10:00+08:00",reason:"開始協調面試時間"}]},
    {id:"REC-20260928-003",candidate:{name:"張書豪",email:"shuhao.zhang@example.invalid",phone:"0933-000-303",position:"測試工程師"},keyinAt:"2026-09-28T10:00:00+08:00",status:"REJECTED",interviewAt:null,interviewCancelled:false,rejectionType:"求職者婉拒",rejectionReason:"已接受其他工作機會",linkedOriginalCaseId:null,newCaseReason:null,statusHistory:[{fromStatus:null,toStatus:"EMAIL_CONTACT",actorRole:"HR",actorName:"Demo HR",at:"2026-09-28T10:00:00+08:00",reason:"建立招募紀錄"},{fromStatus:"EMAIL_CONTACT",toStatus:"REJECTED",actorRole:"HR",actorName:"Demo HR",at:"2026-09-30T16:40:00+08:00",reason:"求職者婉拒：已接受其他工作機會"}]},
    {id:"REC-20260920-004",candidate:{name:"黃可欣",email:"kexin.huang@example.invalid",phone:"0955-000-404",position:"商業分析師"},keyinAt:"2026-09-20T09:15:00+08:00",status:"INTERVIEW_CONFIRMED",interviewAt:"2026-10-01T14:00:00+08:00",interviewCancelled:false,rejectionType:null,rejectionReason:null,linkedOriginalCaseId:null,newCaseReason:null,statusHistory:[{fromStatus:null,toStatus:"EMAIL_CONTACT",actorRole:"HR",actorName:"Demo HR",at:"2026-09-20T09:15:00+08:00",reason:"建立招募紀錄"},{fromStatus:"EMAIL_CONTACT",toStatus:"INTERVIEW_CONFIRMED",actorRole:"HR",actorName:"Demo HR",at:"2026-09-25T15:20:00+08:00",reason:"已確認面試時間"}]},
    {id:"REC-20261020-005",candidate:{name:"吳品妍",email:"pinyan.wu@example.invalid",phone:"0966-000-505",position:"產品設計師"},keyinAt:"2026-10-20T10:00:00+08:00",candidateRespondedAt:null,secondInvitationReminderAt:null,secondInvitationAt:null,secondInvitationResult:null,status:"EMAIL_CONTACT",interviewAt:null,interviewCancelled:false,rejectionType:null,rejectionReason:null,linkedOriginalCaseId:null,newCaseReason:null,statusHistory:[{fromStatus:null,toStatus:"EMAIL_CONTACT",actorRole:"HR",actorName:"Demo HR",at:"2026-10-20T10:00:00+08:00",reason:"建立招募紀錄（未滿 24 小時測試）"}]},
    {id:"REC-20261017-006",candidate:{name:"周柏廷",email:"boting.zhou@example.invalid",phone:"0977-000-606",position:"DevOps 工程師"},keyinAt:"2026-10-17T08:00:00+08:00",candidateRespondedAt:null,secondInvitationReminderAt:"2026-10-18T10:00:00+08:00",secondInvitationAt:"2026-10-18T10:20:00+08:00",secondInvitationResult:"已完成二邀，等待求職者回覆",status:"EMAIL_CONTACT",interviewAt:null,interviewCancelled:false,rejectionType:null,rejectionReason:null,linkedOriginalCaseId:null,newCaseReason:null,statusHistory:[{fromStatus:null,toStatus:"EMAIL_CONTACT",actorRole:"HR",actorName:"Demo HR",at:"2026-10-17T08:00:00+08:00",reason:"建立招募紀錄"}]}
  ],auditEvents:[],scheduleRuns:[]};
}

function clone(value) { return JSON.parse(JSON.stringify(value)); }
function assertText(value, message) { if (typeof value !== "string" || !value.trim()) throw new Error(message); return value.trim(); }
function normalizeState(state) {
  if(!state||state.schemaVersion!==1||!Array.isArray(state.records)) return null;
  state.auditEvents=Array.isArray(state.auditEvents)?state.auditEvents:[];
  state.scheduleRuns=Array.isArray(state.scheduleRuns)?state.scheduleRuns:[];
  state.records=state.records.map((record)=>{
    const responseEvent=record.statusHistory?.find((event)=>["EMAIL_COMMUNICATING","TIME_CONFIRMING","INTERVIEW_CONFIRMED"].includes(event.toStatus));
    return {...record,candidateRespondedAt:record.candidateRespondedAt||(responseEvent?.at??null),secondInvitationReminderAt:record.secondInvitationReminderAt??null,secondInvitationAt:record.secondInvitationAt??null,secondInvitationResult:record.secondInvitationResult??null};
  });
  return state;
}
function parseState(raw) { if (!raw) return null; try { return normalizeState(JSON.parse(raw)); } catch { return null; } }

export function createRecruitmentStore(storage, clock = () => new Date()) {
  if (!storage?.getItem || !storage?.setItem) throw new Error("需要可用的 localStorage 相容儲存空間。");
  const save = (state) => { storage.setItem(STORAGE_KEY, JSON.stringify(state)); return state; };
  const load = () => parseState(storage.getItem(STORAGE_KEY)) ?? save(seedState());
  const getState = () => clone(load());
  const getRecord = (recordId) => { const record=load().records.find((item)=>item.id===recordId); if(!record) throw new Error("找不到指定的招募案件。"); return clone(record); };
  function reject(state, action, role, reason) { state.auditEvents.push({action,role,result:"BLOCKED",reason,at:clock().toISOString()}); save(state); throw new Error(reason); }

  function updateStatus(input) {
    const state=load(); const index=state.records.findIndex((item)=>item.id===input.recordId);
    if(index<0) throw new Error("找不到指定的招募案件。");
    const current=state.records[index];
    if(input.role!==ROLES.HR) reject(state,"UPDATE_STATUS",input.role,"主管僅能唯讀查看邀約紀錄，不能修改招募狀態。");
    if(isClosed(current,clock())) reject(state,"UPDATE_STATUS",input.role,"案件已結案，不可回復、退回或重新啟動原案件。");
    if(!STATUSES.includes(input.newStatus)) reject(state,"UPDATE_STATUS",input.role,"請選擇有效的招募狀態。");
    if(input.newStatus===current.status) reject(state,"UPDATE_STATUS",input.role,"新狀態與目前狀態相同，沒有需要儲存的異動。");
    let interviewAt=current.interviewAt,rejectionType=null,rejectionReason=null;
    if(input.newStatus==="INTERVIEW_CONFIRMED") { const value=assertText(input.interviewAt,"確認面試時必須填寫有效面試時間。"); const parsed=new Date(value); if(Number.isNaN(parsed.getTime())) reject(state,"UPDATE_STATUS",input.role,"面試時間格式無效。"); interviewAt=parsed.toISOString(); }
    if(input.newStatus==="REJECTED") { rejectionType=assertText(input.rejectionType,"設為婉拒時必須選擇婉拒種類。"); if(!["求職者婉拒","公司婉拒"].includes(rejectionType)) reject(state,"UPDATE_STATUS",input.role,"婉拒種類無效。"); rejectionReason=assertText(input.rejectionReason,"設為婉拒時必須填寫婉拒原因。"); }
    const now=clock().toISOString();
    const reason=input.newStatus==="REJECTED"?`${rejectionType}：${rejectionReason}`:(typeof input.reason==="string"&&input.reason.trim()?input.reason.trim():"狀態更新");
    const responseStatuses=["EMAIL_COMMUNICATING","TIME_CONFIRMING","INTERVIEW_CONFIRMED"];
    const updated={...current,status:input.newStatus,interviewAt,rejectionType,rejectionReason,candidateRespondedAt:current.candidateRespondedAt||(responseStatuses.includes(input.newStatus)?now:null),statusHistory:[...current.statusHistory,{fromStatus:current.status,toStatus:input.newStatus,actorRole:input.role,actorName:assertText(input.actorName,"缺少操作人。"),at:now,reason}]};
    state.records[index]=updated; state.auditEvents.push({action:"UPDATE_STATUS",role:input.role,result:"SUCCEEDED",recordId:current.id,at:now}); save(state); return clone(updated);
  }

  function nextId(state,date) { const day=date.toISOString().slice(0,10).replaceAll("-",""); let sequence=state.records.length+1; let id=`REC-${day}-${String(sequence).padStart(3,"0")}`; while(state.records.some((item)=>item.id===id)){sequence+=1;id=`REC-${day}-${String(sequence).padStart(3,"0")}`;} return id; }
  function recentRecords(state) {
    const now=clock().getTime(),thirtyDays=30*24*60*60*1000;
    return state.records.filter((record)=>{const keyinAt=new Date(record.keyinAt).getTime();return Number.isFinite(keyinAt)&&now-keyinAt>=0&&now-keyinAt<=thirtyDays;});
  }
  function normalizeEmail(value) { return value.trim().toLowerCase(); }
  function validateCandidate(input) {
    const name=assertText(input.name,"姓名為必填。"),email=normalizeEmail(assertText(input.email,"Email 為必填。")),phone=assertText(input.phone,"手機為必填。"),position=assertText(input.position,"應徵職缺為必填。");
    if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new Error("Email 格式不正確。");
    const phoneDigits=phone.replace(/[^0-9]/g,"");
    if(phoneDigits.length<8||phoneDigits.length>15) throw new Error("手機格式不正確，請輸入 8 至 15 位數字。");
    return {name,email,phone,position};
  }
  function createRecord(input) {
    const state=load();
    if(input.role!==ROLES.HR) reject(state,"CREATE_RECORD",input.role,"只有 HR 可以新增招募紀錄。");
    let candidate;
    try { candidate=validateCandidate(input); }
    catch(error) { reject(state,"CREATE_RECORD",input.role,error.message); }
    const recent=recentRecords(state);
    const emailMatch=recent.find((record)=>record.candidate.email&&normalizeEmail(record.candidate.email)===candidate.email);
    if(emailMatch) reject(state,"CREATE_RECORD",input.role,`30 天內重複邀約：Email 與案件 ${emailMatch.id} 相同，無法新增。`);
    const possibleMatches=recent.filter((record)=>{
      const samePhone=record.candidate.phone&&record.candidate.phone.trim()===candidate.phone;
      const sameName=record.candidate.name&&record.candidate.name.trim()===candidate.name;
      return samePhone||sameName;
    });
    if(possibleMatches.length&&input.differentPersonConfirmed!==true) {
      reject(state,"CREATE_RECORD",input.role,`可能重複邀約：手機或姓名與 ${possibleMatches.map((record)=>record.id).join("、")} 相同。請核對並確認為不同人。`);
    }
    const now=clock(),at=now.toISOString();
    const created={id:nextId(state,now),candidate,keyinAt:at,candidateRespondedAt:null,secondInvitationReminderAt:null,secondInvitationAt:null,secondInvitationResult:null,status:"EMAIL_CONTACT",interviewAt:null,interviewCancelled:false,rejectionType:null,rejectionReason:null,linkedOriginalCaseId:null,newCaseReason:null,duplicateDecision:possibleMatches.length?{result:"DIFFERENT_PERSON",matchedRecordIds:possibleMatches.map((record)=>record.id),actorName:assertText(input.actorName,"缺少操作人。"),at}:null,statusHistory:[{fromStatus:null,toStatus:"EMAIL_CONTACT",actorRole:input.role,actorName:assertText(input.actorName,"缺少操作人。"),at,reason:"建立招募紀錄"}]};
    state.records.unshift(created);state.auditEvents.push({action:"CREATE_RECORD",role:input.role,result:"SUCCEEDED",recordId:created.id,at});save(state);return clone(created);
  }
  function createFollowUpCase(input) {
    const state=load(); const original=state.records.find((item)=>item.id===input.originalRecordId);
    if(!original) throw new Error("找不到指定的原案件。");
    if(input.role!==ROLES.HR) reject(state,"CREATE_FOLLOW_UP_CASE",input.role,"只有 HR 可以對已結案案件另立新案。");
    if(!isClosed(original,clock())) reject(state,"CREATE_FOLLOW_UP_CASE",input.role,"只有已結案案件可以使用另立新案入口。");
    if(input.confirmed!==true) reject(state,"CREATE_FOLLOW_UP_CASE",input.role,"請先確認需要另立新案。");
    const reason=assertText(input.reason,"另立新案原因為必填。"); const now=clock(); const at=now.toISOString();
    const created={id:nextId(state,now),candidate:clone(original.candidate),keyinAt:at,candidateRespondedAt:null,secondInvitationReminderAt:null,secondInvitationAt:null,secondInvitationResult:null,status:"EMAIL_CONTACT",interviewAt:null,interviewCancelled:false,rejectionType:null,rejectionReason:null,linkedOriginalCaseId:original.id,newCaseReason:reason,statusHistory:[{fromStatus:null,toStatus:"EMAIL_CONTACT",actorRole:input.role,actorName:assertText(input.actorName,"缺少操作人。"),at,reason:`另立新案：${reason}（原案件 ${original.id}）`}]};
    state.records.unshift(created); state.auditEvents.push({action:"CREATE_FOLLOW_UP_CASE",role:input.role,result:"SUCCEEDED",recordId:created.id,originalRecordId:original.id,at}); save(state); return clone(created);
  }
  function needsSecondInvitation(record, now=clock()) {
    const keyinAt=new Date(record.keyinAt).getTime();
    return Number.isFinite(keyinAt)&&now.getTime()-keyinAt>24*60*60*1000&&!record.candidateRespondedAt&&!record.secondInvitationAt&&!isClosed(record,now);
  }
  function runDailySchedule(input) {
    const state=load();
    if(input.role!==ROLES.HR) reject(state,"RUN_SECOND_INVITATION_SCHEDULE",input.role,"只有 HR 可以執行今日排程。請切換為 HR 後再試一次。");
    const at=clock().toISOString(); let createdCount=0;
    for(const record of state.records) {
      if(needsSecondInvitation(record,clock())&&!record.secondInvitationReminderAt) {
        record.secondInvitationReminderAt=at; createdCount+=1;
        state.auditEvents.push({action:"SECOND_INVITATION_REMINDER",role:ROLES.HR,result:"CREATED",recordId:record.id,at});
      }
    }
    const eligibleCount=state.records.filter((record)=>needsSecondInvitation(record,clock())).length;
    const run={id:`SCHEDULE-${at}`,role:input.role,actorName:assertText(input.actorName,"缺少操作人。"),at,createdCount,eligibleCount};
    state.scheduleRuns.push(run); state.auditEvents.push({action:"RUN_SECOND_INVITATION_SCHEDULE",role:input.role,result:"SUCCEEDED",at,createdCount,eligibleCount}); save(state); return clone(run);
  }
  function executeSecondInvitation(input) {
    const state=load(); const index=state.records.findIndex((item)=>item.id===input.recordId);
    if(index<0) throw new Error("找不到指定的招募案件。");
    const current=state.records[index];
    if(input.role!==ROLES.HR) reject(state,"EXECUTE_SECOND_INVITATION",input.role,"只有 HR 可以執行二次邀約。");
    if(isClosed(current,clock())) reject(state,"EXECUTE_SECOND_INVITATION",input.role,"案件已結案，不可執行二次邀約。");
    if(current.candidateRespondedAt) reject(state,"EXECUTE_SECOND_INVITATION",input.role,"求職者已回覆，不可執行二次邀約。");
    if(current.secondInvitationAt) reject(state,"EXECUTE_SECOND_INVITATION",input.role,"此案件已執行二次邀約，不可重複執行。");
    if(!current.secondInvitationReminderAt||!needsSecondInvitation(current,clock())) reject(state,"EXECUTE_SECOND_INVITATION",input.role,"此案件目前不符合二次邀約條件。");
    const result=assertText(input.result,"請填寫二次邀約執行結果。"),at=clock().toISOString();
    const updated={...current,secondInvitationAt:at,secondInvitationResult:result}; state.records[index]=updated;
    state.auditEvents.push({action:"EXECUTE_SECOND_INVITATION",role:input.role,result:"SUCCEEDED",recordId:current.id,actorName:assertText(input.actorName,"缺少操作人。"),detail:result,at}); save(state); return clone(updated);
  }
  const reset=()=>clone(save(seedState())); load(); return {getState,getRecord,createRecord,updateStatus,createFollowUpCase,needsSecondInvitation,runDailySchedule,executeSecondInvitation,reset};
}
