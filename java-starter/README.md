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
- `PATCH /api/recruitment/records/{id}/status`
- `POST /api/recruitment/records/{id}/follow-up-cases`
- `POST /api/recruitment/second-invitation-schedule/run`
- `POST /api/recruitment/records/{id}/second-invitation`
- `PUT /api/recruitment/records/{id}/arrangement`

## 測試

```bash
./mvnw test
```

Fixture tests 在起始版可通過。`WorkflowContractTests` 預設 Disabled；完成一個情境後移除對應標記並以 `expected-results.json` 驗證。
