import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

// Execute the current page script, rather than the retired recruitment store.
const source = readFileSync(new URL('../../main/resources/static/app.js', import.meta.url), 'utf8');
function page() {
  const nodes = new Map();
  const element = () => ({
    value: '', textContent: '', children: [], options: [], dataset: {},
    classList: { toggle() {}, add() {} },
    append(...items) { this.children.push(...items); },
    replaceChildren(...items) { this.children = items; },
    add(option) { this.options.push(option); },
    addEventListener() {}, setAttribute() {},
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
  return { nodes, run, records };
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
