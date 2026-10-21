# Java 21 起始專案

## 需求

- Java 21。
- 不需要預先安裝 Maven；專案提供 Maven Wrapper。
- 不需要資料庫、Google 權限或前端套件管理器。

## 啟動

```bash
./mvnw spring-boot:run
```

開啟 `http://localhost:8080`。P0-A／P0-B 與面試安排資料由 Spring Boot API 管理，並保存於
`data/recruitment-state.json`。首次啟動會建立虛構 Demo 種子資料；頁面的「重置示範資料」可還原種子資料。

二次邀約排程會在 Asia/Taipei 時區每日 10:00 執行，也可在頁面手動觸發。前端不再以
`localStorage` 作為正式資料來源。

主要 API：

- `GET /api/recruitment`
- `POST /api/recruitment/records`
- `POST /api/recruitment/duplicate-decisions/same-person`（HR 確認同一人，僅保存判斷，不新增案件）
- `PATCH /api/recruitment/records/{id}/status`
- `POST /api/recruitment/records/{id}/follow-up-cases`
- `POST /api/recruitment/second-invitation-schedule/run`
- `POST /api/recruitment/records/{id}/second-invitation`
- `PUT /api/recruitment/records/{id}/arrangement`

## 測試

```bash
./mvnw -Drecruitment.data-file=target/test-data/recruitment-state.json verify
```

Fixture tests 在起始版可通過。`WorkflowContractTests` 預設 Disabled；完成一個情境後移除對應標記並以 `expected-results.json` 驗證。

## P0-F｜重複邀約與結案攔截

沿用 `/api/recruitment/records` 新增流程與後端 JSON 保存。以伺服器當下時間回溯 30 天（含邊界），比對所有狀態；Email 會去除頭尾空白並忽略大小寫，手機會去除格式分隔符號，空白舊欄位不參與比對。

Email 命中直接拒絕，人工確認亦不能解除。手機或姓名命中時，API 回傳 `422`、`message` 及 `duplicateMatches`，頁面列出案件、姓名、Email、手機、狀態與 KEYIN 時間。HR 可確認同一人而不新增，或勾選不同人後新增；兩種結果均保存在「設定與紀錄」。修改輸入會清除舊確認。手機與姓名命中不同案件時，Demo 暫定由 HR 核對全部候選案件後人工判斷。

已結案案件只提供 HR「另立新案」專屬入口，須確認及填寫原因；新案歷程可查原案件 ID 與原因。新案不繼承面試安排、通知或日曆，原案維持不變。所有一般修改均受後端結案鎖定，攔截只留下稽核紀錄。

本次建置、測試、網頁操作與重新啟動的結果見 [P0-F-ACCEPTANCE.md](P0-F-ACCEPTANCE.md)。測試及重置請使用隔離資料檔，並維持固定啟動工作目錄。若建置遇到 JAR 被既有 Demo 程序占用，應先停止該程序或以獨立檔名建置。
