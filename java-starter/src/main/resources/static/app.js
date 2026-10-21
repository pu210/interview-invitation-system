const ROLES={HR:"HR",MANAGER:"MANAGER"},STATUSES=["EMAIL_CONTACT","EMAIL_COMMUNICATING","TIME_CONFIRMING","INTERVIEW_CONFIRMED","REJECTED","SECOND_INVITATION_NO_RESPONSE"],LABELS={EMAIL_CONTACT:"信件聯繫",EMAIL_COMMUNICATING:"信件溝通中",TIME_CONFIRMING:"時間確認中",INTERVIEW_CONFIRMED:"確認面試",REJECTED:"婉拒",SECOND_INVITATION_NO_RESPONSE:"二次邀約未回覆"};
let state={records:[],scheduleRuns:[]},selectedRecordId,activeModule="records",notificationBusy=false;
const $=selector=>document.querySelector(selector),role=()=>$("#current-role").value,actor=()=>role()===ROLES.HR?"Demo HR":"Demo 主管",record=id=>state.records.find(item=>item.id===id),fmt=value=>value?new Intl.DateTimeFormat("zh-TW",{dateStyle:"medium",timeStyle:"short"}).format(new Date(value)):"—",local=value=>{if(!value)return"";const date=new Date(value);return new Date(date-date.getTimezoneOffset()*60000).toISOString().slice(0,16)},iso=value=>value?new Date(value).toISOString():null,label=value=>LABELS[value]??value;
const closed=item=>item.status==="REJECTED"||item.status==="INTERVIEW_CONFIRMED"&&!item.interviewCancelled&&item.interviewAt&&new Date(item.interviewAt)<new Date();
async function api(path,method="GET",body){const response=await fetch(`/api/recruitment${path}`,{method,headers:{"Content-Type":"application/json"},body:body===undefined?undefined:JSON.stringify(body)});if(!response.ok){let error={};try{error=await response.json()}catch{}throw new Error(error.message??"後端服務目前無法完成操作。")}return response.json()}
async function refresh(){state=await api("");render()}
function message(text,type="success",target="#message"){const item=$(target);item.textContent=text;item.className=`message ${type}`}
function action(labelText,kind,name,id){const item=document.createElement("button");item.type="button";item.className=kind;item.textContent=labelText;item.dataset.action=name;item.dataset.recordId=id;return item}
function render(){const currentRole=role();$("#records-module").hidden=activeModule!=="records";$("#arrangements-module").hidden=activeModule!=="arrangements";document.querySelectorAll(".hr-only").forEach(item=>item.hidden=currentRole!==ROLES.HR);$("#permission-note").textContent=currentRole===ROLES.HR?"HR 可調整未結案案件狀態、執行今日排程及處理二邀；結案案件不可重新啟動。":"主管可查看邀約與二邀狀態，但不能執行排程、二邀或其他修改。";$("#reminder-count").textContent=state.records.filter(item=>item.secondInvitationReminderAt&&!item.secondInvitationAt&&!item.candidateRespondedAt&&!closed(item)).length;$("#last-schedule").textContent=state.scheduleRuns.length?fmt(state.scheduleRuns.at(-1).at):"尚未執行";const keyword=$("#search").value.trim().toLowerCase(),status=$("#status-filter").value,records=state.records.filter(item=>(!keyword||[item.id,item.candidate.name,item.candidate.email,item.candidate.position].join(" ").toLowerCase().includes(keyword))&&(!status||item.status===status));const rows=$("#record-rows");rows.replaceChildren();for(const item of records){const row=document.createElement("tr"),second=item.secondInvitationAt?`已執行｜${fmt(item.secondInvitationAt)}\n${item.secondInvitationResult}`:item.candidateRespondedAt?"已回覆，不提醒":closed(item)?"已結案，不提醒":item.secondInvitationReminderAt?"需要二邀":"尚未到期";[item.id,`${item.candidate.name}\n${item.candidate.email}`,item.candidate.position,label(item.status),fmt(item.keyinAt),second,closed(item)?"已結案":"進行中"].forEach(value=>{const cell=document.createElement("td");cell.textContent=value;row.append(cell)});const actions=document.createElement("td");actions.className="actions";actions.append(action("查看歷程","text-button","history",item.id));if(currentRole===ROLES.HR&&!closed(item))actions.append(action("更新狀態","small primary","status",item.id));if(currentRole===ROLES.HR&&item.secondInvitationReminderAt&&!item.secondInvitationAt&&!item.candidateRespondedAt&&!closed(item))actions.append(action("執行二邀","small warning","second",item.id));if(currentRole===ROLES.HR&&closed(item))actions.append(action("另立新案","small secondary","follow",item.id));actions.append(action("通知／日曆紀錄","small secondary","notification",item.id));row.append(actions);rows.append(row)}$("#empty-state").hidden=records.length>0;renderArrangements();renderExtraModules()}
function renderArrangements(){$("#arrangement-permission-note").textContent=role()===ROLES.HR?"顯示時間確認中及已確認面試案件；HR 可管理安排並確認最終面試時間。":"顯示時間確認中及已確認面試案件；主管可修改四類指定欄位。";const rows=$("#arrangement-rows");rows.replaceChildren();for(const item of state.records.filter(value=>["TIME_CONFIRMING","INTERVIEW_CONFIRMED"].includes(value.status))){const row=document.createElement("tr");[`${item.id}\n${item.candidate.name}`,`${item.candidate.position}\n${label(item.status)}`,(item.interviewManagers??[]).join("、")||"—",item.interviewLocation||"—",(item.expectedTimeSlots??[]).map(fmt).join("\n")||"—",fmt(item.interviewAt)].forEach(value=>{const cell=document.createElement("td");cell.textContent=value;row.append(cell)});const actions=document.createElement("td");if(!closed(item))actions.append(action("編輯安排","small primary","arrangement",item.id));actions.append(action("通知／日曆紀錄","small secondary","notification",item.id));row.append(actions);rows.append(row)}}
function statusFields(){const confirmed=$("#new-status").value==="INTERVIEW_CONFIRMED",rejected=$("#new-status").value==="REJECTED";$("#interview-at-field").hidden=!confirmed;$("#rejection-fields").hidden=!rejected}
function openDialog(kind,id){const item=record(id);selectedRecordId=id;if(kind==="notification"){openNotification(item);return}if(kind==="history"){$("#history-dialog-title").textContent=`${item.id}｜${item.candidate.name}`;const list=$("#history-list");list.replaceChildren();for(const event of [...item.statusHistory].reverse()){const li=document.createElement("li"),title=document.createElement("strong"),detail=document.createElement("span");title.textContent=event.fromStatus?`${label(event.fromStatus)} → ${label(event.toStatus)}`:`建立案件｜${label(event.toStatus)}`;detail.textContent=`${fmt(event.at)}｜${event.actorRole}／${event.actorName}${event.reason?`｜${event.reason}`:""}`;li.append(title,detail);list.append(li)}$("#history-dialog").showModal();return}if(kind==="status"){$("#status-form").reset();$("#status-dialog-title").textContent=`${item.id}｜${item.candidate.name}`;$("#new-status").value=item.status;$("#interview-at").value=local(item.interviewAt);$("#status-error").textContent="";statusFields();$("#status-dialog").showModal()}if(kind==="follow"){$("#new-case-form").reset();$("#new-case-dialog-title").textContent=`${item.id}｜${item.candidate.name}`;$("#new-case-error").textContent="";$("#new-case-dialog").showModal()}if(kind==="second"){$("#second-invitation-form").reset();$("#second-invitation-title").textContent=`${item.id}｜${item.candidate.name}`;$("#second-invitation-error").textContent="";$("#second-invitation-dialog").showModal()}if(kind==="arrangement"){$("#arrangement-title").textContent=`${item.id}｜${item.candidate.name}`;$("#interview-managers").value=(item.interviewManagers??[]).join("\n");$("#interview-location").value=item.interviewLocation??"";$("#interview-notes").value=item.interviewNotes??"";$("#expected-time-slots").value=(item.expectedTimeSlots??[]).map(local).join("\n");$("#confirmed-interview-at").value=local(item.interviewAt);$("#confirmed-interview-field").hidden=role()!==ROLES.HR;$("#expected-time-slots").disabled=false;$("#expected-time-help").textContent="可填寫多個期待時段（例如 2026-11-02 10:00）；最終面試時間由 HR 確認。";$("#arrangement-error").textContent="";$("#arrangement-dialog").showModal()}}
$("#record-rows").addEventListener("click",event=>{const button=event.target.closest("button[data-action]");if(button)openDialog(button.dataset.action,button.dataset.recordId)});$("#arrangement-rows").addEventListener("click",event=>{const button=event.target.closest("button[data-action]");if(button)openDialog(button.dataset.action,button.dataset.recordId)});
$("#add-record").addEventListener("click",()=>{$("#add-form").reset();$("#add-error").textContent="";$("#possible-duplicate").hidden=true;$("#add-dialog").showModal()});
$("#add-form").addEventListener("submit",async event=>{if(event.submitter?.value==="cancel")return;event.preventDefault();try{const created=await api("/records","POST",{role:role(),actorName:actor(),name:$("#candidate-name").value,email:$("#candidate-email").value,phone:$("#candidate-phone").value,position:$("#candidate-position").value,differentPersonConfirmed:$("#different-person-confirm").checked});$("#add-dialog").close();message(`已新增 ${created.candidate.name}，案件編號 ${created.id}。`);await refresh()}catch(error){$("#add-error").textContent=error.message;if(error.message.startsWith("可能重複邀約")){$("#possible-duplicate").hidden=false;$("#duplicate-details").textContent=error.message}}});
$("#status-form").addEventListener("submit",async event=>{if(event.submitter?.value==="cancel")return;event.preventDefault();try{await api(`/records/${selectedRecordId}/status`,"PATCH",{role:role(),actorName:actor(),newStatus:$("#new-status").value,interviewAt:iso($("#interview-at").value),rejectionType:$("#rejection-type").value,rejectionReason:$("#rejection-reason").value,reason:$("#status-reason").value});$("#status-dialog").close();message("狀態已更新，異動歷程已保存至後端。");await refresh()}catch(error){$("#status-error").textContent=error.message}});
$("#new-case-form").addEventListener("submit",async event=>{if(event.submitter?.value==="cancel")return;event.preventDefault();try{const created=await api(`/records/${selectedRecordId}/follow-up-cases`,"POST",{role:role(),actorName:actor(),confirmed:$("#new-case-confirm").checked,reason:$("#new-case-reason").value});$("#new-case-dialog").close();message(`已建立新案件 ${created.id}；原案件保持不變。`);await refresh()}catch(error){$("#new-case-error").textContent=error.message}});
$("#second-invitation-form").addEventListener("submit",async event=>{if(event.submitter?.value==="cancel")return;event.preventDefault();try{await api(`/records/${selectedRecordId}/second-invitation`,"POST",{role:role(),actorName:actor(),result:$("#second-invitation-result").value});$("#second-invitation-dialog").close();message("二次邀約已保存至後端。");await refresh()}catch(error){$("#second-invitation-error").textContent=error.message}});
$("#arrangement-form").addEventListener("submit",async event=>{if(event.submitter?.value==="cancel")return;event.preventDefault();const item=record(selectedRecordId),proposed={interviewManagers:$("#interview-managers").value.split(/[、,\n]/).map(value=>value.trim()).filter(Boolean),interviewLocation:$("#interview-location").value,interviewNotes:$("#interview-notes").value.trim(),expectedTimeSlots:$("#expected-time-slots").value.split("\n").map(value=>value.trim()).filter(Boolean).map(iso)};if(role()===ROLES.HR&&$("#confirmed-interview-at").value)proposed.confirmedInterviewAt=iso($("#confirmed-interview-at").value);const current={interviewManagers:item.interviewManagers??[],interviewLocation:item.interviewLocation??"",interviewNotes:item.interviewNotes??"",expectedTimeSlots:item.expectedTimeSlots??[],confirmedInterviewAt:item.interviewAt??null},changes=Object.fromEntries(Object.entries(proposed).filter(([key,value])=>JSON.stringify(value)!==JSON.stringify(current[key])));try{await api(`/records/${selectedRecordId}/arrangement`,"PUT",{role:role(),actorName:actor(),changedFields:Object.keys(changes),...changes});$("#arrangement-dialog").close();message("面試安排已保存至後端。","success","#arrangement-message");await refresh()}catch(error){$("#arrangement-error").textContent=error.message}});
$("#run-schedule").addEventListener("click",async()=>{try{const run=await api("/second-invitation-schedule/run","POST",{role:role(),actorName:actor()});message(run.createdCount?`今日排程完成，新增 ${run.createdCount} 筆二邀提醒。`:`今日排程完成；沒有新增重複提醒。`,"info");await refresh()}catch(error){message(error.message,"error")}});
$("#reset-demo").addEventListener("click",async()=>{if(!confirm("確定要清除目前異動並還原虛構示範資料嗎？"))return;try{await api("/demo/reset","POST",{role:role(),actorName:actor()});await api("/second-invitation-schedule/run","POST",{role:"HR",actorName:"重置後補查"});message("示範資料已由後端重置並完成補查。","info");await refresh()}catch(error){message(error.message,"error")}});
$("#new-status").addEventListener("change",statusFields);$("#current-role").addEventListener("change",()=>{document.querySelectorAll("dialog[open]").forEach(item=>item.close());if(role()!==ROLES.HR&&["logs","templates"].includes(activeModule))activeModule="records";message(role()===ROLES.HR?"已切換為 HR。":"已切換為主管；邀約紀錄為唯讀。","info");render()});$("#search").addEventListener("input",render);$("#status-filter").addEventListener("change",render);document.querySelectorAll("[data-module]").forEach(item=>item.addEventListener("click",()=>{activeModule=item.dataset.module;loadExtraModule();document.querySelectorAll("[data-module]").forEach(nav=>nav.classList.toggle("active",nav===item));render()}));document.querySelectorAll(".close-dialog").forEach(item=>item.addEventListener("click",()=>item.closest("dialog").close()));
for(const status of STATUSES){$("#status-filter").add(new Option(label(status),status));$("#new-status").add(new Option(label(status),status))}
if(location.protocol==="file:"){
  render();
  message("目前以本機檔案模式開啟；導覽可預覽，但資料操作需要先啟動 Spring Boot，再開啟 http://localhost:8080。","error");
}else{
  refresh().catch(error=>message(`載入失敗：${error.message}`,"error"));
}


function renderExtraModules(){
  document.querySelectorAll("[data-module]").forEach(item=>item.classList.toggle("active",item.dataset.module===activeModule));
  for(const name of ["logs","templates"])$(`#${name}-module`).hidden=activeModule!==name||role()!==ROLES.HR;
  const select=$("#template-record"),previous=select.value;
  select.replaceChildren(new Option("請選擇案件", ""));
  for(const item of state.records.filter(item=>item.status==="INTERVIEW_CONFIRMED"&&!closed(item)))select.add(new Option(`${item.id}｜${item.candidate.name}｜${fmt(item.interviewAt)}`,item.id));
  select.value=previous;
  $("#template-apply").disabled=!select.value;
  $("#template-message").textContent=select.options.length===1?"目前沒有可套用案件，請先由 HR 確認面試時間。":"可使用 REC-DEMO-NOTICE 虛構案件展示；也可從邀約紀錄開啟通知資料。";
}
async function loadTemplates(){
  const templates=await api(`/notification-templates?role=${role()}`),select=$("#notice-template"),previous=select.value;
  select.replaceChildren(new Option("未選取／缺少範本（驗收用）", ""));
  for(const template of templates)select.add(new Option(template.name,template.id));
  select.value=previous;
  $("#template-source").textContent=templates.map(t=>`${t.name}\n${t.html}`).join("\n");
}
async function loadExtraModule(){
  if(role()!==ROLES.HR)return;
  try{
    if(activeModule==="logs"){
      const events=await api(`/audit-events?role=${role()}`);
      $("#audit-log").textContent=events.slice().reverse().map(e=>`${fmt(e.at)}｜${e.recordId??"系統"}｜${e.action}｜${e.result}｜${e.reason??""}`).join("\n");
    }
    if(activeModule==="templates"){
      await loadTemplates();
    }
  }catch(error){message(error.message,"error")}
}
const noticeDetails=()=>({templateId:$("#notice-template").value,surname:$("#notice-surname").value,salutation:$("#notice-salutation").value,preparationUrl:$("#notice-preparation").value,meetingUrl:$("#notice-meeting").value,resumeUrl:$("#notice-resume").value,hrEmail:$("#notice-hr").value});
async function openNotification(item){
  if(notificationBusy)return;
  if(role()===ROLES.HR){try{await loadTemplates()}catch(error){message(error.message,"error");return}}
  const details=item.notificationDetails??{};
  selectedRecordId=item.id;
  $("#notification-title").textContent=`${item.id}｜通知信與模擬日曆`;
  for(const [id,key] of Object.entries({template:"templateId",surname:"surname",salutation:"salutation",preparation:"preparationUrl",meeting:"meetingUrl",resume:"resumeUrl",hr:"hrEmail"}))$(`#notice-${id}`).value=details[key]??"";
  $("#notice-fail-email").checked=false;$("#notice-fail-calendar").checked=false;
  $("#notice-error").textContent="";$("#notice-html").hidden=true;$("#notice-html").srcdoc="";
  renderNoticeResults(item);
  $("#notification-dialog").showModal();
}
function renderNoticeResults(item){
  const task=item.notificationTask,canOperate=role()===ROLES.HR&&!closed(item);
  $("#notification-details-form").hidden=role()!==ROLES.HR;
  $("#notification-details-fields").disabled=!canOperate||!!task;
  $("#notice-controls").hidden=!canOperate;
  $("#notice-context").textContent=`求職者：${item.candidate.name}｜To：${item.candidate.email}\n職缺：${item.candidate.position}｜面試時間：${item.interviewAt?new Intl.DateTimeFormat("zh-TW",{dateStyle:"medium",timeStyle:"short",timeZone:"Asia/Taipei"}).format(new Date(item.interviewAt)):"尚未確認"}（Asia/Taipei）｜地點：${item.interviewLocation||"尚未安排"}`;
  const event=task?.event;
  $("#notice-results").textContent=task?
    `任務：${task.id}\nEmail：${task.emailStatus}${task.emailFailure?"｜"+task.emailFailure:""}\nTo：${task.to}\nCC（Demo 假地址）：${task.cc.join("、")}\n日曆：${task.calendarStatus}${task.calendarFailure?"｜"+task.calendarFailure:""}\n`+
    (event?`事件：${event.id}｜${event.status}\nHR：${event.hrEmail}\n主管：${event.managers.join("、")}\n時間：${fmt(event.interviewAt)}\n地點：${event.location}\n會議：${event.meetingUrl}\n履歷：${event.resumeUrl}\n`:"")+
    task.attempts.map(a=>`${fmt(a.at)}｜${a.item}｜${a.result}｜${a.reason??""}`).join("\n"):"Email：未執行\n日曆：未執行\n填寫資料後可直接驗證與預覽。";
}
function clearNoticePreview(){
  $("#notice-html").hidden=true;$("#notice-html").srcdoc="";
}
$("#notification-details-form").addEventListener("input",()=>{clearNoticePreview();$("#notice-error").textContent="資料已變更；預覽／執行時會先保存並重新驗證。"});
async function saveNoticeDetails(id){
  const item=record(id),details=noticeDetails();
  if(!item.notificationTask&&JSON.stringify(details)!==JSON.stringify(item.notificationDetails)){
    await api(`/records/${id}/notification-details`,"PUT",{role:role(),actorName:actor(),details});
  }
}
async function noticeAction(kind){
  if(notificationBusy)return;
  notificationBusy=true;
  const id=selectedRecordId;
  $("#current-role").disabled=true;
  $("#notification-details-fields").disabled=true;
  document.querySelectorAll("#notice-controls button, #notification-dialog .close-dialog").forEach(button=>button.disabled=true);
  clearNoticePreview();$("#notice-error").textContent="";
  try{
    await saveNoticeDetails(id);
    if(kind==="preview"){
      const result=await api(`/records/${id}/notification/preview`,"POST",{role:role(),actorName:actor()});
      $("#notice-error").textContent=result.errors.length?result.errors.join("\n"):"驗證通過；以下為目前保存資料的 HTML 預覽。執行結果請見通知紀錄。";
      if(result.html){
        // Create one isolated document per preview, avoiding competing blank/srcdoc navigations.
        const frame=$("#notice-html").cloneNode(false);
        frame.srcdoc='<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; base-uri \'none\'; form-action \'none\'">'+result.html;
        frame.hidden=false;$("#notice-html").replaceWith(frame);
      }
    }else if(kind==="execute"){
      await api(`/records/${id}/notification/execute`,"POST",{role:role(),actorName:actor(),failEmail:$("#notice-fail-email").checked,failCalendar:$("#notice-fail-calendar").checked});
    }else $("#notice-error").textContent="通知資料已保存，可驗證與預覽。";
  }catch(error){$("#notice-error").textContent=error.message}
  finally{
    try{await refresh();renderNoticeResults(record(id))}catch(error){clearNoticePreview();$("#notice-error").textContent=error.message}
    notificationBusy=false;$("#current-role").disabled=false;
    document.querySelectorAll("#notice-controls button, #notification-dialog .close-dialog").forEach(button=>button.disabled=false);
  }
}
$("#notification-dialog").addEventListener("cancel",event=>{if(notificationBusy)event.preventDefault()});
$("#notification-details-form").addEventListener("submit",event=>{event.preventDefault();noticeAction("save")});
$("#notice-preview").addEventListener("click",()=>noticeAction("preview"));
$("#notice-execute").addEventListener("click",()=>noticeAction("execute"));
$("#template-record").addEventListener("change",()=>{$("#template-apply").disabled=!$("#template-record").value});
$("#template-apply").addEventListener("click",async()=>{
  const id=$("#template-record").value;
  if(!id||role()!==ROLES.HR)return;
  await openNotification(record(id));
  if($("#notification-dialog").open&&!record(id).notificationTask)$("#notice-template").value="interview-notice";
});
