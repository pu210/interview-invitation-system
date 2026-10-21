# Netlify + Render 免費 Demo 部署

目前只準備部署檔，尚未建立雲端服務。使用 GitHub 的 main 分支，先部署 Java 後端，再建立 Netlify 前端。

## 1. 將設定提交至 GitHub

本次檔案包含 netlify.toml、scripts/build-netlify.mjs、Dockerfile、.dockerignore、render.yaml、DEPLOYMENT.md 與 .gitignore 的新增忽略規則。
使用 Git 工具檢查變更，只提交需要的檔案，再推送到 main。不要將本機 recruitment-state.json 加入 Git。

## 2. 建立免費 Render 後端

1. 開啟 https://dashboard.render.com/，登入並連接 GitHub。
2. 選 New → Blueprint，選擇 pu210/interview-invitation-system 與 main 分支，使用根目錄 render.yaml。
3. 確認服務為 Free，建立服務。設定檔不建立付費磁碟或資料庫。
4. 等待 Docker 建置與測試完成，服務狀態成為 Live，複製實際 HTTPS 網址；請勿自行猜測服務網址。
5. 開啟 `https://實際服務網址/api/recruitment`，確認回傳含 records 的 JSON。

Docker 建置使用 Java 21 和專案 Maven Wrapper，執行 verify，測試資料放在容器建置階段的 target/test-data。
training-fixtures/data 會一起放入建置環境；本機執行中的 recruitment-state.json 不會帶入映像。
雲端第一次啟動若找不到資料檔，既有 Java seed() 會自動建立虛構 Demo 資料。

## 3. 建立 Netlify 前端

1. 開啟 https://app.netlify.com/，登入。
2. 選 Add new project → Import an existing project → GitHub。
3. 授權指定儲存庫 pu210/interview-invitation-system，選 main 分支。
4. Base directory 留空。netlify.toml 應自動帶入 Build command `node scripts/build-netlify.mjs` 和 Publish directory `netlify-dist`。
5. 在部署環境變數新增 `BACKEND_URL`，值填 Render 的實際 HTTPS 根網址，例如 `https://your-service.onrender.com`（不要加 /api）。此變數需能在 build 階段使用。
6. 開始部署。如果先前因缺少 BACKEND_URL 失敗，補好變數後重新部署。
7. 分享 Netlify 顯示的實際 `https://...netlify.app` 網址給主管。

建置腳本只複製正式入口 index.html、styles.css、app.js，並產生 /api/* 的 200 代理規則。
網頁仍透過原本的 /api/recruitment 路徑操作 Java 後端；沒有切換成 localStorage 或模擬 API。

## 4. 展示驗證

1. 先開啟 Render 後端，等它完成喚醒，再開啟 Netlify 網址。
2. 確認邀約列表出現初始虛構資料。
3. 切 HR，修改一筆未結案紀錄的狀態，重新整理後確認仍有變更。
4. 切主管，確認邀約列表唯讀，面試安排可依原規格修改四類欄位。
5. 從另一個瀏覽器開啟同一 Netlify 網址，確認讀到同一份後端最新資料。
6. 若要還原情境，由 HR 使用既有「重置示範資料」；會清除雲端目前的 Demo 異動。

## 免費方案限制與更新

- Render 免費服務閒置約 15 分鐘會休眠，首次重新開啟可能需要約一分鐘或更久。若 Netlify API 請求先逾時，等 Render 後端可正常開啟後再重新整理前端。
- 免費服務沒有持久化磁碟，重啟或重新部署可能失去 JSON，回到初始種子資料。不要放需要永久保存的資料。
- 後端休眠／停機時，每日 10:00 排程不保證執行；Demo 可使用既有手動排程。
- 此系統依原規格以角色切換做 Demo，無真正登入，僅使用虛構資料；所有訪客共用同一份雲端 Demo 狀態。
- GitHub main 更新後，Netlify 與 Render 各自自動部署。兩邊完成時間可能不同，展示前等待兩邊成功。
- Netlify Deploy Preview 若使用相同 BACKEND_URL，也會操作同一份 Demo 資料。
- 免費服務受帳號額度與平台當時條款限制，不保證永久免費或持續可用。

## 本機驗證結果（2026-10-21）

- Maven verify：BUILD SUCCESS，54 項測試、0 失敗、0 錯誤、1 項既有跳過；使用 target/test-data 隔離資料。
- Netlify 建置：三個正式前端檔案逐一比對一致，代理保留 /api/ 路徑；缺少網址、HTTP 或含額外路徑／查詢的網址均被拒絕。
- 本機沒有可用的 Docker 指令，尚未執行 Docker 映像建置；Render／Netlify 雲端部署與跨站實際操作尚未驗證。
- 工作區原有其他未提交修改，仍保留原狀；本次尚未建立 Git 提交或推送。

官方文件：
- https://docs.netlify.com/start/quickstarts/deploy-from-repository/
- https://docs.netlify.com/manage/routing/redirects/rewrites-proxies/
- https://render.com/docs/free
- https://render.com/docs/blueprint-spec
