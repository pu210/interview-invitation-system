# Java 21 起始專案

## 需求

- Java 21。
- 不需要預先安裝 Maven；專案提供 Maven Wrapper。
- 不需要資料庫、Google 權限或前端套件管理器。

## 啟動

```bash
./mvnw spring-boot:run
```

開啟 `http://localhost:8080`。起始版會回報 `NOT_IMPLEMENTED`，請從 `WorkflowEngine.java` 的 TODO 開始完成六個 P0。

## 測試

```bash
./mvnw test
```

Fixture tests 在起始版可通過。`WorkflowContractTests` 預設 Disabled；完成一個情境後移除對應標記並以 `expected-results.json` 驗證。
