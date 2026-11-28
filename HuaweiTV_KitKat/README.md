# Huawei Solar TV (Versione KitKat Android 4.4.4 - API 19)

Progetto Android TV nativo puro sviluppato appositamente per Smart TV con versioni legacy di Android (a partire da **Android 4.4.4 KitKat - API 19** fino ad Android 15), con **ZERO dipendenze esterne** (nessun Jetpack Compose, nessun AndroidX, nessun Material Design, nessun DataStore).

---

## 🎯 Specifiche Tecniche

- **Configurazione SDK:**
  - `minSdk 19` (Android 4.4.4 KitKat)
  - `targetSdk 28` (Android 9.0 Pie)
  - `compileSdk 34` (Android 14)
- **Linguaggio & Framework:**
  - **100% Java puro** (`JavaVersion.VERSION_1_8`), zero librerie runtime (no kotlin-stdlib, no androidx).
  - Dimensione APK compilato: appena **~44 KB** (istantaneo all'avvio, memoria RAM occupata < 15 MB).
- **Firma APK:**
  - **Firma v1 (JAR signing) abilitata esplicitamente**: obbligatoria per Android 4.4.4 (KitKat non supporta v2/v3).
- **Concorrenza & Rete:**
  - Threading nativo con `ExecutorService` (`newSingleThreadExecutor`) + `Handler(Looper.getMainLooper())`.
  - Client Modbus TCP minimale implementato con `java.net.Socket`:
    - Socket persistente riutilizzato tra i cicli di polling.
    - **`Thread.sleep(1500)`** obbligatorio subito dopo la connessione TCP.
    - **`Thread.sleep(200)`** obbligatorio tra ciascuna richiesta di registro.
    - Timeout socket: **5000 ms**.
    - Riconnessione automatica con backoff esponenziale (2s -> 30s) in caso di errore o EOF.
- **Registri Modbus Huawei SUN2000 (FC 03, Big-Endian):**
  - `32064` (INT32, 2 reg): Potenza FV (W)
  - `32080` (INT32, 2 reg): Potenza Attiva AC Inverter (W)
  - `32114` (UINT32, 2 reg, /100.0): Produzione Giornaliera (kWh)
  - `37113` (INT32, 2 reg): Potenza Rete Meter (W, `+` immissione, `-` prelievo)
  - `37760` (UINT16, 1 reg, /10.0): SoC Batteria LUNA2000 (%)
  - `37765` (INT32, 2 reg): Potenza Batteria (W, `+` carica, `-` scarica)
  - Calcolo fabbisogno: `houseConsumptionW = Math.max(0, activePowerW - gridPowerW)`.
- **Persistenza:**
  - `SharedPreferences` nativo (`Context.MODE_PRIVATE`) per salvare:
    - **IP Inverter:** `192.168.1.53` (default)
    - **Porta Modbus:** `6607` (default)
    - **Unit ID:** `0` (default)
    - **Intervallo Polling:** `10s` (default)
    - **Modalità Mock:** `false` (default)
- **Interfaccia Utente TV (10-Foot UI):**
  - Schermata singola senza scrolling (16:9), tema scuro OLED ad alto contrasto leggibile da 3 metri.
  - `EnergyFlowView`: View custom con rendering hardware accelerato su Canvas (60 FPS) che visualizza i nodi (FV, Batteria con indicatore di livello, Rete, Casa, Inverter AC centrale) e le **particelle energetiche animate direzionali**.
  - **100% Navigabile con telecomando D-Pad**: pulsanti e card focusabili con stati visivi ad alto contrasto.
  - `FLAG_KEEP_SCREEN_ON` per impedire lo standby dello schermo TV.
  - Finestra Impostazioni attivabile dal pulsante a schermo o con il tasto `MENU` / `SETTINGS` del telecomando.

---

## 📦 File APK Compilato

- **Percorso:** `app/build/outputs/apk/debug/app-debug.apk`
- **Firma:** `v1 (JAR signing)` = `true`, `v2 (APK Signature)` = `true`
- **Installazione via ADB:**
  ```powershell
  adb connect <IP_DELLA_TV>:5555
  adb install -r HuaweiTV_KitKat\app\build\outputs\apk\debug\app-debug.apk
  ```
