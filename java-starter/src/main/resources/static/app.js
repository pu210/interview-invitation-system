const elements = {
  scenario: document.querySelector("#scenario"),
  reset: document.querySelector("#reset"),
  run: document.querySelector("#run"),
  message: document.querySelector("#message"),
  status: document.querySelector("#status"),
  candidate: document.querySelector("#candidate"),
  operation: document.querySelector("#operation"),
  nextActions: document.querySelector("#next-actions")
};

async function request(url, options = {}) {
  const response = await fetch(url, {headers: {"Content-Type": "application/json"}, ...options});
  if (!response.ok) throw new Error(`HTTP_${response.status}`);
  return response.json();
}

function busy(value, message) {
  elements.reset.disabled = value;
  elements.run.disabled = value;
  elements.message.textContent = message;
}

function renderRecords(id, items, formatter) {
  const target = document.querySelector(`#${id}`);
  target.replaceChildren();
  target.className = items.length ? "" : "empty";
  if (!items.length) {
    target.textContent = "尚無資料";
    return;
  }
  for (const item of items) {
    const record = document.createElement("div");
    record.className = "record";
    record.textContent = formatter(item);
    target.append(record);
  }
}

function render(state) {
  elements.status.textContent = state.status;
  elements.status.className = state.status === "READY"
    ? "status-ready"
    : state.status.includes("ERROR") || state.status === "NOT_IMPLEMENTED" ? "status-error" : "";
  elements.candidate.textContent = state.candidateId;
  elements.operation.textContent = state.operationId;
  elements.nextActions.textContent = state.nextActions.join("、") || "無";
  document.querySelector("#mail-count").textContent = state.mailRecords.length;
  document.querySelector("#calendar-count").textContent = state.calendarRecords.length;
  document.querySelector("#drive-count").textContent = state.driveRecords.length;
  renderRecords("mail-records", state.mailRecords, (item) => `${item.mailType}｜${item.status}｜第 ${item.attempt} 次`);
  renderRecords("calendar-records", state.calendarRecords, (item) => `${item.action}｜${item.status}｜${item.eventId || "尚無 ID"}`);
  renderRecords("drive-records", state.driveRecords, (item) => `${item.documentType}｜${item.status}｜${item.path}`);
  renderRecords("audit-events", state.auditEvents, (item) => `${item.eventType}｜${item.status}${item.errorCode ? `｜${item.errorCode}` : ""}`);
}

async function load() {
  try {
    const scenarios = await request("/api/scenarios");
    const options = scenarios.map((item) => {
      const option = document.createElement("option");
      option.value = item.scenarioId;
      option.textContent = `${item.scenarioId}｜${item.title}`;
      return option;
    });
    elements.scenario.replaceChildren(...options);
    await reset();
  } catch (error) {
    busy(false, `載入失敗：${error.message}`);
  }
}

async function reset() {
  busy(true, "正在重設情境…");
  try {
    render(await request(`/api/scenarios/${elements.scenario.value}/reset`, {method: "POST"}));
    busy(false, "情境已重設，可以開始執行。");
  } catch (error) {
    busy(false, `重設失敗：${error.message}`);
  }
}

async function run() {
  busy(true, "正在執行流程…");
  try {
    const state = await request(`/api/scenarios/${elements.scenario.value}/run`, {method: "POST"});
    render(state);
    busy(false, state.status === "NOT_IMPLEMENTED"
      ? "此情境尚待實作；請依下一步與 audit event 完成 P0 規則。"
      : "執行完成，請核對狀態與證據。");
  } catch (error) {
    busy(false, `執行失敗：${error.message}`);
  }
}

elements.scenario.addEventListener("change", reset);
elements.reset.addEventListener("click", reset);
elements.run.addEventListener("click", run);
load();
