# discordpdf

開源、隱私優先的 PDF 工具箱（類似 iLovePDF），以 Discord 機器人作為使用入口。
以 Java 21 + [JDA](https://github.com/discord-jda/JDA) 製作。

> Open-source, privacy-first iLovePDF-style PDF toolbox served through a Discord bot.
> Java 21 + JDA. Files are processed in a RAM-only scratch folder and deleted before the result is even sent back.

## 功能

所有功能都在 `/pdf` 指令底下，每個工具都可以**直接上傳檔案**或提供**檔案網址**。

| 指令 | 功能 | 主要選項 |
|---|---|---|
| `/pdf merge` | 合併 PDF（最多 10 個，依序） | `file1`…`file10`、`urls` |
| `/pdf split` | 拆分 PDF | `ranges`（如 `1-3,5,8-`）、`every`（每 N 頁）、`mode`（separate / extract） |
| `/pdf compress` | 壓縮 PDF | `level`（low / medium / high） |
| `/pdf pdf2md` | PDF → Markdown | `page_breaks` |
| `/pdf md2pdf` | Markdown → PDF | 檔案、網址或 `text` 直接貼上 |
| `/pdf pdf2word` | PDF → Word | `format`（docx / doc）、`mode`（editable / layout） |
| `/pdf pdf2ppt` | PDF → PowerPoint | `format`（pptx / ppt） |
| `/pdf word2pdf` | Word（.docx / .doc）→ PDF | |
| `/pdf ppt2pdf` | PowerPoint（.pptx / .ppt）→ PDF | |
| `/pdf pdf2jpg` | PDF → JPG（多頁時打包成 zip） | `dpi`（72–300）、`pages` |
| `/pdf jpg2pdf` | 圖片 → PDF（每頁一張） | `image1`…`image10`、`urls`、`page_size`（A4 / Letter / fit） |
| `/pdf help`、`/pdf privacy` | 使用說明、隱私說明 | |

每個工具都有 `public` 選項：預設結果只有使用者自己看得到（ephemeral），設為 `True` 才會公開貼在頻道。
介面支援英文與繁體中文（依 Discord 使用者語言自動切換）。

## 隱私設計重點

- **不保存**：沒有資料庫；每個工作在專屬暫存資料夾處理，**結果上傳前就整個刪除**。Docker 部署時暫存區是 RAM（tmpfs），檔案不會落地到硬碟。
- **不記錄**：紀錄檔只有工具名稱、結果與耗時；不記錄檔名、網址、內容或任何 Discord ID。
- **不讀訊息**：不申請任何 Gateway Intent，只接收斜線指令。
- **清除個資**：輸出的 PDF 會移除作者等中繼資料；照片轉 PDF 會移除 EXIF / GPS。
- **防 SSRF**：網址只能指向公開網際網路，含 DNS rebinding 防護；文件內的外部連結資源不會被抓取。

完整說明見 [SECURITY.md](SECURITY.md)。

## 快速開始

### 1. 建立 Discord 應用程式

1. 到 [Discord Developer Portal](https://discord.com/developers/applications) 建立應用程式 → **Bot** → 取得 Token。
2. **不需要**開啟任何 Privileged Gateway Intents。
3. 邀請機器人（把 `CLIENT_ID` 換成你的 Application ID）：
   ```
   https://discord.com/oauth2/authorize?client_id=CLIENT_ID&scope=bot+applications.commands&permissions=34816
   ```
   （權限只有 Send Messages + Attach Files。）
4. 選用：在 **Installation** 頁面開啟 *User Install*，使用者就能把它裝在自己的帳號上，在任何私訊中使用。

### 2. 用 Docker 執行（建議）

Docker 映像已包含 LibreOffice 與中日韓字型。

```bash
git clone https://github.com/RTX4O9O/DiscordPDF.git
cd DiscordPDF
cp .env.example .env      # 填入 DISCORD_TOKEN
docker compose up -d --build
```

### 3. 不用 Docker 執行

需要 JDK 21 與 Maven。建議安裝 LibreOffice（Office ↔ PDF 品質最好、`.doc` 輸出需要）與 TrueType 中文字型（例如 `fonts-droid-fallback`）。

```bash
mvn package
DISCORD_TOKEN=xxxx java -jar target/discordpdf.jar
```

開發時可設定 `DEV_GUILD_ID`，指令只註冊在單一伺服器，更新會立即生效。

### 不經過 Discord 的命令列模式

方便測試或在本機批次處理，與機器人走同一套程式與防護：

```bash
java -jar target/discordpdf.jar cli merge a.pdf b.pdf -o out/
java -jar target/discordpdf.jar cli split report.pdf ranges=1-3,5 mode=extract
java -jar target/discordpdf.jar cli compress https://example.com/file.pdf level=high
java -jar target/discordpdf.jar cli md2pdf README.md
```

## 設定

全部透過環境變數設定（見 [.env.example](.env.example)）。

| 變數 | 預設 | 說明 |
|---|---|---|
| `DISCORD_TOKEN` | — | 必填 |
| `DEV_GUILD_ID` | 空 | 只在此伺服器註冊指令（開發用） |
| `MAX_INPUT_MB` | 50 | 單一輸入檔大小上限 |
| `MAX_OUTPUT_MB` | 10 | 結果大小上限，不可超過伺服器的 Discord 上傳限制 |
| `MAX_PAGES` | 500 | 頁數上限 |
| `MAX_INPUTS_PER_JOB` | 10 | 一次最多幾個檔案 |
| `MAX_CONCURRENT_JOBS` | 2 | 同時處理的工作數，其餘排隊 |
| `QUEUE_TIMEOUT_SECONDS` | 120 | 排隊等待上限 |
| `USER_COOLDOWN_SECONDS` | 5 | 每位使用者的冷卻時間 |
| `DOWNLOAD_TIMEOUT_SECONDS` | 60 | 網址下載逾時 |
| `DEFAULT_EPHEMERAL` | true | 結果預設只有自己看得到 |
| `WORK_DIR` | 系統暫存/discordpdf | 暫存資料夾 |
| `SOFFICE_PATH` | 自動偵測 | LibreOffice 執行檔 |
| `OFFICE_TIMEOUT_SECONDS` | 180 | LibreOffice 轉檔逾時 |
| `OFFICE_COMMAND_PREFIX` | 空 | 包住 LibreOffice 的沙箱指令，如 `firejail --quiet --net=none` |
| `FONT_CJK` | 自動偵測 | Markdown → PDF 使用的 TrueType 中日韓字型 |
| `LOG_ERROR_DETAILS` | false | 除錯用；完整錯誤訊息可能包含文件文字 |
| `DISCORDPDF_DNS_GUARD` | true | JVM 層級的內部位址 DNS 過濾 |

## 轉換方式與限制

| 轉換 | 實作 | 說明 |
|---|---|---|
| 合併 / 拆分 | Apache PDFBox | 無損 |
| 壓縮 | PDFBox | 影像重新以 JPEG 編碼並降解析度；文字與向量不受影響 |
| PDF → Markdown | PDFBox 文字層 + 字級分析 | 還原標題、段落、清單；掃描檔沒有文字層，不支援（尚無 OCR） |
| Markdown → PDF | commonmark-java + openhtmltopdf | 支援 GFM 表格、刪除線、自動連結；遠端圖片不會下載 |
| PDF → Word | Apache POI | `editable`：可編輯文字（版面會簡化）；`layout`：每頁一張圖，外觀一致但不可編輯。`.doc` 需 LibreOffice |
| PDF → PowerPoint | PDFBox + POI | 每頁一張高解析度圖片投影片 |
| Word / PPT → PDF | LibreOffice（headless） | 未安裝時改用純 Java 備援：Word 保留文字、標題、清單、表格、圖片；投影片以圖片呈現 |
| PDF → JPG | PDFBox | 單頁很大時會自動降低 DPI |
| JPG → PDF | PDFBox | JPG 不重新壓縮，移除 EXIF/GPS，依 EXIF 方向旋轉 |

其他限制：

- Discord 一般伺服器上傳上限是 10 MB；伺服器加成後可提高 `MAX_OUTPUT_MB`。
- 有密碼保護的 PDF / Office 檔案需先移除密碼。
- `md2pdf` 的 `text` 選項是單行輸入框，換行請輸入 `\n`；長文件請上傳 `.md` 檔。

## 專案結構

```
src/main/java/io/github/rtx4o9o/discordpdf/
├── Main.java                 進入點（bot 或 cli）
├── bot/                      Discord 層：斜線指令定義、事件處理、冷卻
├── cli/                      離線命令列
├── config/BotConfig.java     環境變數設定
├── core/                     與 Discord 無關的核心：工作流程、暫存區、檔案類型、i18n
├── net/                      安全下載、SSRF 與 DNS 防護
├── convert/                  轉換引擎：PDF 結構分析、Markdown、HTML→PDF、LibreOffice、OOXML 清理
├── tools/                    11 個工具，每個實作 PdfTool
└── util/                     頁碼範圍、EXIF 清除、圖片、ZIP、字型
```

### 新增一個工具

1. 在 `tools/` 實作 `PdfTool`（只在 `ctx.workspace()` 內寫檔，回傳一個 `ToolResult`）。
2. 在 `tools/Tools.java` 註冊。
3. 在 `bot/SlashCommands.java` 加上子指令與選項。
4. 在 `src/main/resources/i18n/messages*.properties` 加上訊息（英文與繁中都要；`CoreTest` 會檢查）。

## 開發

```bash
mvn verify          # 編譯 + 單元測試
```

## 授權

MIT，見 [LICENSE](LICENSE)。

使用的第三方套件：JDA、Apache PDFBox、Apache POI、commonmark-java、jsoup（Apache-2.0 / MIT / BSD）、
openhtmltopdf（LGPL-2.1，以未修改的函式庫形式使用）、LibreOffice（MPL-2.0，作為外部程式呼叫）。
