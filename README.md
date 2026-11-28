# ☀️ SolarDeck v2.0

[![GitHub](https://img.shields.io/badge/GitHub-CavaBG84%2FSolarDeck-181717?style=for-the-badge&logo=github)](https://github.com/CavaBG84/SolarDeck)
![Version](https://img.shields.io/badge/Version-2.0.0-f59e0b?style=for-the-badge)
![Platform](https://img.shields.io/badge/Android_TV_%26_Mobile-API_19%2B_%2F_21%2B-10b981?style=for-the-badge)
![Network](https://img.shields.io/badge/100%25_LAN-Zero_Cloud-38bdf8?style=for-the-badge)
![Inverters](https://img.shields.io/badge/Inverter-Huawei_SUN2000_%7C_Fronius-0ea5e9?style=for-the-badge)
![Languages](https://img.shields.io/badge/Lang-IT_%7C_EN_%7C_DE_%7C_FR-8b5cf6?style=for-the-badge)

> **Nota / Notice:** Dalla versione **2.0**, l'applicazione **Huawei-SUN-TV** cambia ufficialmente nome in **SolarDeck**, evolvendosi in una dashboard solare multi-inverter completa. L'identificativo interno dell'app (`com.huaweitv.solar`) rimane invariato: chi ha già installato la precedente versione 1.0 può aggiornare direttamente alla 2.0 senza perdere le configurazioni salvate.

---

## 🌿 Panoramica & Filosofia 100% Locale

**SolarDeck v2.0** è una dashboard fotovoltaica ed energy manager nativa per **Android TV, Amazon Fire TV e Smartphone**, progettata per funzionare interamente sulla **rete locale (LAN)**:

- **Zero cloud proprietari**: i dati viaggiano solo tra il tuo inverter/contatore e il dispositivo in casa.
- **Zero account obbligatori** e **zero pubblicità**.
- **Navigazione 100% D-Pad** (telecomando TV) e layout ottimizzato anche per schermi touch (Portrait e Landscape).

---

## ⚡ Inverter Supportati (Multi-Brand)

- **Huawei SUN2000** *(via Modbus TCP locale)* con supporto completo per la batteria DC-coupled **LUNA2000** e Smart Power Sensor / Meter.
- **Fronius** *(via Solar API v1 HTTP JSON locale)* su porta `80` con `DeviceId` configurabile (`0..99`).
- **Modalità Mock (Simulazione)** integrata nelle Impostazioni per testare la UI senza inverter fisico connesso.

---

## ☀️ Calcolo Reale Produzione Solare con Accumulo (Huawei SUN2000)

Sui sistemi **Huawei SUN2000** dotati di batteria DC-coupled (**LUNA2000**), il solo registro `32114` (*Daily Energy Yield*) riporta l'energia transitata lato AC dell'inverter, escludendo la quota FV inviata direttamente in carica alla batteria.

**SolarDeck v2.0** legge in blocco contiguo i registri giornalieri della batteria (`37015..37018`) e calcola l'energia solare **realmente prodotta dai pannelli**:

$$\text{Daily PV Yield} = \max\big(0,\ \text{Daily Inverter Yield (32114)} + \text{Battery Daily Charge (37015)} - \text{Battery Daily Discharge (37017)}\big)$$

- Include anche l'energia FV caricata direttamente in batteria in DC.
- Gestisce automaticamente gli impianti **senza accumulo** (fallback silenzioso su `32114` in caso di valore `0xFFFFFFFF` o eccezione Modbus) e protegge da valori negativi transitori a mezzanotte.

---

## 🛡️ PowerGuard & Automazione Shelly (Anti-Distacco Contatore)

- **Monitoraggio continuo del prelievo da rete** secondo le soglie contrattuali Open Meter 2G:
  - **WARNING** (`> +10%` fino a `+33%` rispetto alla potenza contrattuale): alert visivo con countdown da `180 minuti`.
  - **CRITICAL** (`> +33%`): alert critico ad alto contrasto con countdown da `2 minuti` e segnale acustico opzionale (silenziabile per la sessione).
- **Distacco automatico dei carichi via Shelly (LAN HTTP)**:
  - Supporta relè **Shelly Gen 1** (`/relay/<ch>?turn=off`) e **Shelly Gen 2+** (`/rpc/Switch.Set?id=<ch>&on=false`) direttamente sulla LAN, con ritardo anti-spunto configurabile (`1..300 s`), riarmo manuale dal banner e pulsanti dedicati di test nelle Impostazioni (**Testa allarme soglia** e **Testa comando OFF**).

---

## 🌍 Interfaccia Multilingua (IT, EN, DE, FR)

- **Rilevamento automatico** della lingua di sistema all'avvio.
- **Selezione manuale rapida** nelle Impostazioni tramite **4 pulsanti compatti con bandiera** (`IT`, `EN`, `DE`, `FR`) basati su `VectorDrawable` compatibili da **Android 4.4.4 KitKat (API 19)** ad **Android 15 (API 35)**.
- Navigabile interamente con telecomando TV (D-Pad) e applicata istantaneamente al salvataggio.

---

## 📦 Build Disponibili & Installazione

| Build | Cartella | Stack & Compatibilità | Target |
|---|---|---|---|
| **Moderna (TV & Mobile)** | [`HuaweiTV`](HuaweiTV) | Kotlin + Jetpack Compose (`tv-material` + Material 3), `minSdk 21`, `targetSdk 35` | Android TV, Fire TV, Smartphone |
| **Legacy KitKat** | [`HuaweiTV_KitKat`](HuaweiTV_KitKat) | 100% Java puro, zero dipendenze esterne, `minSdk 19`, `targetSdk 28`, firma v1 + v2 | Smart TV Android 4.4.4 KitKat+ |

- Guide tecniche dettagliate nei moduli: [`HuaweiTV/README.md`](HuaweiTV/README.md) e [`HuaweiTV_KitKat/README.md`](HuaweiTV_KitKat/README.md).
- Repository ufficiale: [`CavaBG84/SolarDeck`](https://github.com/CavaBG84/SolarDeck) ([Tutte le Release](https://github.com/CavaBG84/SolarDeck/releases)).

### Download APK (`v2.0.0`)

| Versione | File APK | Link Diretto (GitHub Releases) |
|---|---|---|
| **Moderna (TV & Mobile)** | `SolarDeck-v2.0-modern.apk` | [Scarica `SolarDeck-v2.0-modern.apk`](https://github.com/CavaBG84/SolarDeck/releases/download/v2.0.0/SolarDeck-v2.0-modern.apk) |
| **Legacy KitKat (Android 4.4.4+)** | `SolarDeck-v2.0-kitkat.apk` | [Scarica `SolarDeck-v2.0-kitkat.apk`](https://github.com/CavaBG84/SolarDeck/releases/download/v2.0.0/SolarDeck-v2.0-kitkat.apk) |

| Versione | Codice Downloader | Note |
|---|---|---|
| **v2.0.0** *(Moderna – `SolarDeck-v2.0-modern.apk`)* | **`6680500`** | Android 5.0+, Android TV e Amazon Fire TV Stick |
| **v2.0.0** *(KitKat – `SolarDeck-v2.0-kitkat.apk`)* | **`4961801`** | Build legacy dedicata ad Android 4.4.4 KitKat |
| **v1.0.0** *(Legacy `Huawei-SUN-TV`)* | `9183283` (Modern) / `1728411` (KitKat) | Versione precedente solo Huawei SUN2000 |

### Compilazione e Test da riga di comando (JDK 17)

```powershell
# Build moderna (Android TV + Smartphone)
cd HuaweiTV
.\gradlew.bat testDebugUnitTest assembleDebug

# Build legacy (Android 4.4.4 KitKat+)
cd ..\HuaweiTV_KitKat
.\gradlew.bat testDebugUnitTest assembleDebug
```

---

## 📊 Mappatura Registri Modbus (Huawei SUN2000 + LUNA2000)

| Registro | Tipo | Q.tà | Scala | Unità | Descrizione |
|---|---|---|---|---|---|
| `32064` | INT32 | 2 | `1` | W | Potenza in ingresso Fotovoltaico (PV Input Power) |
| `32080` | INT32 | 2 | `1` | W | Potenza Attiva AC Inverter (Active Power) |
| `32114` | UINT32 | 2 | `/ 100.0` | kWh | Produzione Giornaliera AC Inverter (Daily Energy Yield) |
| `37015` | UINT32 | 2 | `/ 100.0` | kWh | Carica Giornaliera Batteria (blocco contiguo `37015..37018`) |
| `37017` | UINT32 | 2 | `/ 100.0` | kWh | Scarica Giornaliera Batteria (blocco contiguo `37015..37018`) |
| `37113` | INT32 | 2 | `1` | W | Potenza Rete/Meter (`+` Immissione, `-` Prelievo) |
| `37760` | UINT16 | 1 | `/ 10.0` | % | Stato di Carica Batteria LUNA2000 (SoC) |
| `37765` | INT32 | 2 | `1` | W | Potenza Batteria (`+` In Carica, `-` In Scarica) |

---

## ☕ Supporta il Progetto (Donazione Volontaria)

**SolarDeck** è gratuito, senza pubblicità e sviluppato nel tempo libero. Se l'app ti è utile ogni giorno per monitorare il tuo impianto fotovoltaico, puoi offrirmi un caffè volontario tramite PayPal:

- **PayPal:** [https://paypal.me/cavalleridaniele](https://paypal.me/cavalleridaniele)
- Nell'app è presente un promemoria periodico non invasivo ogni 3 giorni (con link diretto, QR Code generato localmente e possibilità di disattivazione definitiva dopo la conferma).
