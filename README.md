# 🆘 Miko Emergency - Mesh Network Emergency App

## Proje Özeti
İnternet altyapısı çöktüğünde (deprem, sel, savaş vb.) cihazların doğrudan birbirleriyle iletişim kurmasını sağlayan Android uygulaması.

## Mimari

```
┌─────────────────────────────────────────────────────────┐
│                    MIKO EMERGENCY                        │
│                  Mesh Network Stack                      │
├─────────────┬───────────────┬───────────┬───────────────┤
│   Internet  │  WiFi Direct  │ Bluetooth │  Local WiFi   │
│   (HTTP)    │  (P2P Group)  │    (BLE)  │  (UDP/mDNS)   │
├─────────────┴───────────────┴───────────┴───────────────┤
│              Message Router (Multi-Hop TTL=10)           │
├─────────────────────────────────────────────────────────┤
│         LocalHttpServer (port 8888) + NSD/mDNS           │
├─────────────────────────────────────────────────────────┤
│           AES-256-GCM Encryption + Signature             │
└─────────────────────────────────────────────────────────┘
```

## Bileşenler

### 1. Keşif Katmanı (Discovery)
- **WiFi Direct** (`WifiP2pManager`): Cihaz taraması ve P2P bağlantı
- **BLE Advertisement** (`BluetoothLeAdvertiser`): Bluetooth üzerinden MIKO_SERVICE_UUID yayını
- **UDP Multicast/Broadcast** (`UdpBroadcastDiscovery`): 239.255.77.77:8889 üzerinden yerel ağ keşfi
- **mDNS/NSD** (`NsdManager`): `_mikoem._tcp.` servis tipi ile Bonjour/Zeroconf
- **Subnet Scan**: Aktif IP taraması (arka planda 45 sn'de bir)

### 2. İletişim Katmanı
- **LocalHttpServer**: Her cihazda `http://[ip]:8888` adresinde çalışan gömülü HTTP sunucu
  - `GET /ping` → canlılık kontrolü
  - `GET /node-info` → düğüm bilgileri
  - `GET /help` → HTML acil sayfası
  - `POST /message` → mesaj alma
  - `POST /discover` → düğüm ilanı
- **Mesh URL**: `miko://[NODE-ID]/help` şeması

### 3. Routing Katmanı (MessageRouter)
```
Mesaj Gönderimi Öncelik Sırası:
1. İnternet varsa → bulut API (HTTPS)
2. Gateway düğüm varsa → ona yönlendir
3. Yoksa → tüm komşulara broadcast (TTL-1, hopCount+1)
4. Mesajlar max 10 hop atlayabilir
5. Gördüğü mesajları 5 dakika önbelleğe al (döngü önleme)
```

### 4. Güvenlik
- **AES-256-GCM** şifreleme
- **Mesaj imzası**: SHA-256 tabanlı, zamanla tuzlanmış
- **Tekrar saldırısı önleme**: Görülen mesaj ID'leri cache'lenir

## Kurulum

### Gereksinimler
- Android Studio Hedgehog veya üstü
- Android SDK 26+ (minimum), 34 (target)
- Kotlin 1.9+

### Build
```bash
cd MikoEmergency
./gradlew assembleDebug
```

APK çıktısı: `app/build/outputs/apk/debug/app-debug.apk`

### Gradle Wrapper İndirme
```bash
gradle wrapper --gradle-version=8.1.1
```

## Dosya Yapısı

```
app/src/main/java/com/miko/emergency/
├── model/
│   └── Models.kt              # EmergencyMessage, MeshNode, vb.
├── crypto/
│   └── MessageEncryption.kt   # AES-256-GCM şifreleme
├── mesh/
│   ├── MeshNetworkManager.kt  # Ana mesh yöneticisi
│   ├── MessageRouter.kt       # Çok-hop yönlendirme
│   ├── WifiDirectBroadcastReceiver.kt
│   ├── BluetoothMeshScanner.kt
│   └── UdpBroadcastDiscovery.kt
├── server/
│   └── LocalHttpServer.kt     # Gömülü HTTP sunucu
├── service/
│   ├── MeshForegroundService.kt
│   └── BootReceiver.kt
├── utils/
│   ├── NetworkUtils.kt
│   ├── LocationUtils.kt
│   └── PreferenceManager.kt
└── ui/
    ├── SplashActivity.kt
    ├── OnboardingActivity.kt
    ├── MainActivity.kt
    ├── EmergencyActivity.kt
    ├── MeshMapActivity.kt
    ├── SettingsActivity.kt
    └── adapter/
        └── Adapters.kt
```

## Senaryo: Deprem Anı

1. **Kullanıcı SOS butonuna basar**
2. GPS konumu alınır
3. İnternet kontrolü → gerçek HTTP testi (ping değil)
4. **İnternet varsa**: `emergency-api.miko.net/v1/sos` → HTTPS POST
5. **İnternet yoksa**:
   - WiFi Direct peer discovery başlar
   - BLE advertisement yayınlanır
   - UDP multicast beacon gönderilir
   - mDNS servisi ilan edilir
6. **Komşu bulunursa**: Şifreli mesaj HTTP ile iletilir
7. **Komşunun interneti varsa**: O gateway olarak buluta iletir
8. **Yoksa**: Mesaj hop hop dolaşır (TTL azalarak)
9. Tüm düğümler `miko://[NODE-ID]/help` adresiyle bulunabilir
10. `http://[ip]:8888/help` → tarayıcıdan erişilebilir HTML sayfası

## İzinler

| İzin | Neden |
|------|-------|
| ACCESS_FINE_LOCATION | WiFi Direct + BLE tarama zorunluluğu |
| BLUETOOTH_SCAN/CONNECT/ADVERTISE | BLE mesh keşfi |
| CHANGE_WIFI_STATE | WiFi Direct group oluşturma |
| FOREGROUND_SERVICE | Arka planda mesh servisi |
| INTERNET | Normal koşullarda bulut API |
| RECEIVE_BOOT_COMPLETED | Otomatik başlatma |

## Notlar

- Android 10+ üzerinde uygulamalar WiFi'yi programatik açamaz; kullanıcıya yönlendirme yapılır
- BLE advertisement bazı cihazlarda desteklenmeyebilir (eski Bluetooth chipset)
- WiFi Direct, WiFi aç/kapat olmadan çalışır (Android 8.0+)
- Subnet taraması yoğun ağ trafiği oluşturabilir; 45 saniyelik aralıkla sınırlandırılmıştır
