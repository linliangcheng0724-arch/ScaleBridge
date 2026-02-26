# ScaleBridge

USB 電子秤橋接 Android App，提供 HTTP API 讓 Odoo POS 查詢重量。

## 功能

- 📱 連接 USB 電子秤
- 🌐 提供 HTTP API (`/weight`)
- 🔄 開機自動啟動
- 💪 背景持續運行

## API

```
GET http://<手機IP>:8080/weight
```

回應：
```json
{
  "status": "SUCCESS",
  "weight": "1.234",
  "message": "成功讀取重量: 1.234"
}
```

## 編譯步驟

### 1. 生成簽名金鑰

```bash
keytool -genkey -v -keystore scalebridge.keystore -alias scalebridge -keyalg RSA -keysize 2048 -validity 10000
```

### 2. 創建 keystore.properties

在專案根目錄創建 `keystore.properties`：

```properties
storeFile=scalebridge.keystore
storePassword=你的密碼
keyAlias=scalebridge
keyPassword=你的密碼
```

### 3. 編譯 Release APK

```bash
./gradlew assembleRelease
```

APK 位置：`app/build/outputs/apk/release/app-release.apk`

## 安裝

1. 將 APK 傳到手機
2. 開啟 APK 進行安裝
3. 允許「安裝未知來源應用程式」

## 使用

1. 開啟 ScaleBridge App
2. 連接 USB 電子秤
3. 點擊「測試讀取重量」確認正常
4. 記下通知列顯示的 API URL
5. 在 Odoo POS 設定中填入該 URL

## License

MIT

