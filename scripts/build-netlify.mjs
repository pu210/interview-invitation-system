import { copyFile, mkdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

// Set BACKEND_URL in Netlify to the HTTPS origin of the Render service.
const value = process.env.BACKEND_URL?.trim();
if (!value) throw new Error("請先在 Netlify 設定 BACKEND_URL（Render 後端 HTTPS 網址）。");
const backend = new URL(value);
if (backend.protocol !== "https:" || backend.username || backend.password ||
    backend.pathname !== "/" || backend.search || backend.hash) {
  throw new Error("BACKEND_URL 必須是 HTTPS 網站根網址，例如 https://your-service.onrender.com。");
}

const root = new URL("../", import.meta.url);
const output = new URL("netlify-dist/", root);
await mkdir(output, { recursive: true });
for (const name of ["index.html", "styles.css", "app.js"]) {
  await copyFile(new URL(`java-starter/src/main/resources/static/${name}`, root), new URL(name, output));
}
await writeFile(new URL("_redirects", output), `/api/* ${backend.origin}/api/:splat 200!\n`);
console.log(`Netlify 前端已產生：${fileURLToPath(output)}；API 代理：${backend.origin}`);
