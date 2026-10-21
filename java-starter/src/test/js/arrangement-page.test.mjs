import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

// Execute the current page script, rather than the retired recruitment store.
const source = readFileSync(new URL('../../main/resources/static/app.js', import.meta.url), 'utf8');
function page() {
  const nodes = new Map();
  const element = () => ({
    value: '', textContent: '', children: [], options: [], dataset: {}, listeners: {},
    classList: { toggle() {}, add() {} },
    append(...items) { this.children.push(...items); },
    replaceChildren(...items) { this.children = items; },
    add(option) { this.options.push(option); },
    addEventListener(name, handler) { this.listeners[name] = handler; }, setAttribute() {},
    reset() {}, close() { this.open = false; },
    showModal() { this.open = true; }
  });
  const document = {
    querySelector(selector) {
      if (!nodes.has(selector)) nodes.set(selector, element());
      return nodes.get(selector);
    },
    querySelectorAll() { return []; },
    createElement: element
  };
  document.querySelector('#current-role').value = 'HR';
  const context = vm.createContext({ document, location: { protocol: 'file:' },
    Option: function(text, value) { this.text = text; this.value = value; }
  });
  vm.runInContext(source, context);
  const run = script => vm.runInContext(script, context);
  const records = ['EMAIL_CONTACT', 'EMAIL_COMMUNICATING', 'TIME_CONFIRMING',
    'INTERVIEW_CONFIRMED', 'REJECTED', 'SECOND_INVITATION_NO_RESPONSE'].map(status => ({
    id: status, status, candidate: { name: status, position: '工程師' },
    interviewAt: status === 'INTERVIEW_CONFIRMED' ? '2099-11-01T10:00:00+08:00' : null
  }));
  context.records = records;
  run('state.records = records');
  return { nodes, run, records, context };
}

for (const role of ['HR', 'MANAGER']) {
  test(`${role}: 信件溝通中起顯示安排，保留後續狀態並排除其他狀態`, () => {
    const { nodes, run } = page();
    nodes.get('#current-role').value = role;
    run('renderArrangements()');
    assert.deepEqual(nodes.get('#arrangement-rows').children.map(row => row.children[0].textContent.split('\n')[0]),
      ['EMAIL_COMMUNICATING', 'TIME_CONFIRMING', 'INTERVIEW_CONFIRMED']);
    assert.match(nodes.get('#arrangement-permission-note').textContent, /信件溝通中/);
    const row = nodes.get('#arrangement-rows').children[0];
    assert.equal(row.children.at(-1).children[0].dataset.action, 'arrangement');
    run('openDialog("arrangement", "EMAIL_COMMUNICATING")');
    assert.equal(nodes.get('#arrangement-dialog').open, true);
    assert.equal(nodes.get('#expected-time-slots').disabled, false);
    assert.equal(nodes.get('#confirmed-interview-field').hidden, role !== 'HR');
  });
}

test('狀態前進與退回後重新渲染，總表立即同步', () => {
  const { nodes, run } = page();
  run('state.records = [records[0]]; renderArrangements()');
  assert.equal(nodes.get('#arrangement-rows').children.length, 0);
  for (const status of ['EMAIL_COMMUNICATING', 'TIME_CONFIRMING', 'EMAIL_COMMUNICATING']) {
    run(`state.records[0].status = '${status}'; renderArrangements()`);
    assert.equal(nodes.get('#arrangement-rows').children.length, 1);
  }
  run('state.records[0].status = "EMAIL_CONTACT"; renderArrangements()');
  assert.equal(nodes.get('#arrangement-rows').children.length, 0);
});


test('30 天內結案仍顯示可用的新案入口與視窗提醒', () => {
  const { nodes, run } = page();
  run(`state.records = [{id:'closed',status:'REJECTED',keyinAt:new Date().toISOString(),
    candidate:{name:'測試',email:'test@example.invalid'},statusHistory:[]}];render();openDialog('follow','closed')`);
  const buttons = nodes.get('#record-rows').children[0].children.at(-1).children;
  assert.equal(buttons.at(-1).dataset.action, 'follow');
  assert.notEqual(buttons.at(-1).disabled, true);
  assert.match(nodes.get('#new-case-warning').textContent, /未滿 30 天/);
  run(`state.records[0].keyinAt='2020-01-01T00:00:00Z';openDialog('follow','closed')`);
  assert.doesNotMatch(nodes.get('#new-case-warning').textContent, /未滿 30 天/);
});

test('沒有二邀時間時停用狀態選項；已有時間可選', () => {
  const { nodes, run } = page();
  run(`openDialog('status','EMAIL_CONTACT')`);
  const option = nodes.get('#new-status').options.find(o => o.value === 'SECOND_INVITATION_NO_RESPONSE');
  assert.equal(option.disabled, true);
  run(`records[0].secondInvitationAt=new Date().toISOString();openDialog('status','EMAIL_CONTACT')`);
  assert.equal(option.disabled, false);
});

test('退回先確認；取消不呼叫 API、確認後送出並重新取得日曆', async () => {
  const { nodes, run, context } = page();
  run(`state.records=[records[3]];selectedRecordId=records[3].id;state.records[0].notificationTask={event:{id:'event'}}`);
  nodes.get('#new-status').value = 'TIME_CONFIRMING';
  let warning = '', calls = [];
  context.confirm = text => { warning = text; return false; };
  context.fetch = async (url, options) => {
    calls.push({url, options});
    return {ok:true,json:async()=>url.includes('/status')?{}:{records:[],scheduleRuns:[]}};
  };
  const event = {preventDefault(){},submitter:{value:'save'}};
  await nodes.get('#status-form').listeners.submit(event);
  assert.match(warning, /清空.*刪除/);
  assert.equal(calls.length, 0);
  context.confirm = () => true;
  await nodes.get('#status-form').listeners.submit(event);
  assert.match(calls[0].url, /\/status$/);
  assert.equal(JSON.parse(calls[0].options.body).newStatus, 'TIME_CONFIRMING');
  assert.equal(calls[1].url, '/api/recruitment?role=HR');
});

test('HR 可編輯未結案紀錄；主管與結案紀錄不顯示編輯入口', () => {
  const { nodes, run } = page();
  run('render()');
  const buttons = () => nodes.get('#record-rows').children.map(row => row.children.at(-1).children.map(button => button.dataset.action));
  assert.equal(buttons()[0].includes('edit'), true);
  assert.equal(buttons()[4].includes('edit'), false);
  run('openDialog("edit", "EMAIL_CONTACT")');
  assert.equal(nodes.get('#edit-record-dialog').open, true);
  assert.equal(nodes.get('#edit-candidate-name').value, 'EMAIL_CONTACT');
  nodes.get('#current-role').value = 'MANAGER';
  run('render()');
  assert.equal(buttons().some(actions => actions.includes('edit')), false);
});

test('編輯送出使用正式 API，成功後重讀；錯誤保留表單', async () => {
  const { nodes, run, context } = page();
  run('openDialog("edit", "EMAIL_CONTACT")');
  nodes.get('#edit-candidate-name').value = '修改姓名';
  let calls = [];
  context.fetch = async (url, options) => {
    calls.push({ url, options });
    return {ok:true,json:async()=>options.method==='PUT'?{}:{records:[],scheduleRuns:[]}};
  };
  await nodes.get('#edit-record-form').listeners.submit({preventDefault(){}});
  assert.equal(calls[0].url, '/api/recruitment/records/EMAIL_CONTACT');
  assert.equal(calls[0].options.method, 'PUT');
  assert.equal(JSON.parse(calls[0].options.body).name, '修改姓名');
  assert.equal(calls[1].url, '/api/recruitment?role=HR');
  assert.equal(nodes.get('#edit-record-dialog').open, false);
  nodes.get('#edit-record-dialog').open = true;
  context.fetch = async () => ({ok:false,json:async()=>({message:'手機格式不正確'})});
  await nodes.get('#edit-record-form').listeners.submit({preventDefault(){}});
  assert.equal(nodes.get('#edit-record-dialog').open, true);
  assert.equal(nodes.get('#edit-record-error').textContent, '手機格式不正確');
});
