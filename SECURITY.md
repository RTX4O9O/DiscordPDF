# 隱私與安全設計 / Privacy & Security

discordpdf 的目標是「處理完就忘記」：機器人不應該保留、記錄或外洩任何使用者資料。
本文件說明實際做法，方便任何人檢查程式碼是否名副其實。

## 資料生命週期

| 階段 | 做法 | 程式位置 |
|---|---|---|
| 接收 | 只透過斜線指令接收檔案或網址；不申請任何 Gateway Intent，因此**讀不到訊息內容、成員清單或狀態** | `bot/DiscordBot.java` |
| 暫存 | 每個工作建立權限 `700` 的專屬資料夾；Docker 中整個 `/tmp` 是 RAM 上的 tmpfs，檔案從不寫入硬碟 | `core/Workspace.java`, `docker-compose.yml` |
| 處理 | 所有中間檔都在該資料夾內；JVM 的 `java.io.tmpdir` 也被導向工作目錄，並關閉 ImageIO 磁碟快取，避免函式庫在別處留下碎片 | `Workspace.redirectJvmTemp` |
| 回傳 | 結果先讀進記憶體，**暫存資料夾在上傳之前就刪除** | `core/JobRunner.java` |
| 清理 | 啟動與關閉時再清一次，防止當機殘留 | `Workspace.purgeStale` |

## 不記錄的東西

紀錄檔只會出現：工具名稱、成功／被拒（含錯誤代碼）／失敗、耗時。
**不會**出現：檔名、網址、檔案內容、使用者 ID、伺服器 ID、頻道 ID。

- 例外訊息可能含有檔名或文件文字，因此預設只記錄例外類別與程式位置（`LOG_ERROR_DETAILS=false`）。
- PDFBox、POI、openhtmltopdf 的記錄等級預設為 `error`，避免第三方套件輸出文件細節。
- 冷卻時間（rate limit）以「啟動時隨機產生的 salt + SHA-256」雜湊使用者 ID，只存在記憶體，重啟即消失。

## 移除結果檔中的個資

- **PDF**：清除 Info 字典（作者、建立工具、日期）、XMP 中繼資料、頁面縮圖、應用程式私有資料（PieceInfo）。適用於所有輸出 PDF 的工具，包括 LibreOffice 的轉檔結果（會帶入原 Word 的作者、公司名稱）。
- **照片**：JPG 轉 PDF 時移除 EXIF（含 **GPS 座標**、相機序號）、XMP、IPTC 與註解，但保留色彩描述檔；方向資訊改以頁面旋轉呈現，不需重新壓縮。
- **ZIP**：壓縮檔內的時間戳記固定為 1980-01-01。

## 網址下載的防護（SSRF）

使用者可以提供任意網址，因此必須避免機器人被利用來存取內部網路：

1. 只允許 `http` / `https`，拒絕帶帳密的網址。
2. 解析主機後，**所有** IP 都必須是公開位址：封鎖 loopback、私有網段、link-local（含雲端 metadata `169.254.169.254`）、CGNAT、multicast、文件／測試網段，以及 IPv6 的對應範圍與 NAT64 / 6to4 / Teredo 內嵌位址。
3. 轉址不自動跟隨，每一跳都重新檢查，最多 5 次。
4. **DNS rebinding 防護**：透過 JEP 418 註冊全 JVM 的 DNS 解析過濾器，任何主機名稱在本程序內都不可能解析成內部位址（不只是下載前的檢查）。
5. 大小上限在下載過程中強制執行，不信任 `Content-Length`。
6. 請求不帶 Cookie、Referer 或任何與 Discord 使用者有關的資訊。

## 文件內容本身的防護

- **Markdown → PDF**：原始 HTML 一律跳脫；渲染器拒絕所有外部資源（只允許 `data:` 內嵌圖片），遠端圖片改為顯示替代文字，因此無法藉由文件讓機器人發出請求。
- **Word / PowerPoint → PDF**：交給 LibreOffice 之前先移除 `.docx` / `.pptx` 中所有外部關聯（連結圖片、OLE 物件、外部範本；超連結保留）。LibreOffice 以獨立、用完即刪的設定檔執行，且所有 proxy 環境變數指向關閉的本機埠。可再用 `OFFICE_COMMAND_PREFIX` 包一層沙箱（例如 `firejail --net=none`）。
- **資源上限**：頁數、輸入大小、圖片像素（防解壓縮炸彈）、ZIP 解壓總量、LibreOffice 執行時間、同時工作數都有上限。
- 依檔案內容（magic bytes）判斷格式，不信任副檔名。

## 部署建議

- 使用提供的 `docker-compose.yml`：唯讀檔案系統、tmpfs、`no-new-privileges`、移除所有 capabilities、記憶體與 PID 上限。
- 不要把 `.env` 提交到版本控制。
- 若在公開伺服器提供服務，建議在機器人簡介中附上本文件或 `/pdf privacy` 的說明。

## 回報安全問題

請不要開公開 issue。請使用 GitHub 的 **Security → Report a vulnerability**（private vulnerability reporting）通報。
