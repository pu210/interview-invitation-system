# Skill／Agent／工作流變更清單

更新日期：2026-09-04

## 1. SOLID 軟體開發 Skill

- 檔案：`/Users/sunrisesundown/.codex/skills/solid-software-development/SKILL.md`
- 新增「AI 工程新人訓標準環境」。
- 將安全虛構資料、JSON 唯一輸入、In-Memory adapters、P0／P1／P2、雙語言 contract tests、15 分鐘啟動與發布驗證改成可執行規則。
- 未修改：一般軟體開發、Google Apps Script、測試、安全性及程式碼審查的既有規則。

## 2. Coaching 專案 Agent 規則

- 檔案：`/Users/sunrisesundown/Documents/教練Coaching/AGENTS.md`
- 新增「AI 新人訓標準訓練環境」。
- 要求新人訓優先建立可安全使用、可直接啟動、可重跑、範圍明確的 monorepo；發布前必須驗證 ZIP、README、路由、下載網址及 clasp 同步方向。
- 未修改：新人訓每日進度回報格式、1 on 1、結案報告及其他 Coaching 工作流。

## 3. V2 驗證與發布工作流

- `scripts/verify-fixtures.mjs`：檢查 10 位虛構候選人、10 個情境、六個必做 P0 與 10 份假履歷。
- `scripts/verify-contract-parity.mjs`：確保 C#／Java 都保留六個同名 P0 契約測試。
- `scripts/scan-sensitive.mjs`：掃描 fixtures 與兩套 Starter，阻擋真實 Email、正式網址及敏感 ID。
- `scripts/verify-apps-script-web.mjs`：檢查 Apps Script 白名單、11 個頁面、導覽路由、Script ID 與三個 Drive 下載網址。
- `scripts/build-release.mjs`：建立三份版本化 ZIP，排除編譯輸出、暫存檢查檔與作業系統檔案。
- `scripts/render-apps-script-preview.mjs`：由同一份 Apps Script 模板產生本機預覽，用於桌機與手機視覺驗證。
- C#／Java Starter 各新增 API smoke test，確認情境清單、重設、執行與狀態查詢四個端點可由完整應用程式啟動並回應。
- Java fixture 載入器允許共用 JSON 的非業務 `meta` 欄位，並在讀取後正確關閉輸入串流；避免啟動測試與實際執行對同一份契約產生不同結果。
- 雙語言測試固定保留「fixture 契約通過、API smoke 通過、P0 業務契約刻意停用」三層狀態，讓主管能區分環境完成與新人待實作內容。

## 4. 未納入本次變更

- 未更動新人每日進度回報信件範本。
- 未更動 1 on 1 與結案報告格式。
- 未實作六個 P0 業務答案；這些是新人 Day 2 的明確待實作內容。
- 未啟用正式或測試 Google API、OAuth、真實寄信或正式資料搬移。
