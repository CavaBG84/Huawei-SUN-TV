# Huawei Solar TV - Dashboard Fotovoltaico per Android TV

Applicazione nativa Android TV (Kotlin + Jetpack Compose for TV, minSdk 26, targetSdk 35) per monitorare in tempo reale l'impianto fotovoltaico con inverter **Huawei SUN2000** e sistema di accumulo **LUNA2000** tramite **Modbus TCP** su rete locale Wi-Fi / Ethernet.

---

## 🌟 Caratteristiche Principali

- **Jetpack Compose for TV (`androidx.tv:tv-material:1.0.0`)**: Interfaccia moderna a tema scuro ad alto contrasto (Dark OLED/Navy), ottimizzata per visione a 3 metri di distanza (10-foot UI).
- **Zero-Dependency Modbus TCP Client**: Implementazione interna pura in Kotlin con `java.net.Socket` su `Dispatchers.IO` (Function Code 03: Read Holding Registers). Nessuna libreria esterna pesante.
- **Rispetto rigoroso delle specifiche Huawei Modbus TCP**:
  - Socket persistente riutilizzato tra i cicli di polling con riconnessione automatica e backoff esponenziale (2s -> 30s) in caso di EOF/timeout.
  - **`delay(1500)`** obbligatorio immediatamente dopo l'apertura del socket prima della prima richiesta (fondamentale per evitare disconnessioni premature da parte del dongle Huawei SDongleA-05 / WLAN).
  - **`delay(200)`** obbligatorio tra una richiesta di registro e la successiva.
  - Timeout socket: **5000 ms**.
- **Flusso Energetico Animato in Tempo Reale**:
  - Schema a 4 nodi (Fotovoltaico FV, Batteria LUNA2000, Rete Elettrica, Casa/Consumo) + Hub Centrale Inverter.
  - Linee di flusso animate con particelle la cui direzione e velocità rispecchiano fedelmente il verso (carica/scarica, immissione/prelievo) e la potenza istantanea in Watt.
- **Navigazione 100% D-Pad (Telecomando TV)**:
  - Focus border ad alto contrasto e ingrandimento fluido dei componenti selezionati.
  - Tasto `MENU` o `SETTINGS` per aprire direttamente la schermata di configurazione.
- **Schermata Singola Senza Scroll**: Progettata per stare perfettamente in viewport 16:9 (1080p Full HD e 4K).
- **`FLAG_KEEP_SCREEN_ON`**: Mantiene lo schermo della TV costantemente acceso evitando screensaver o standby.
- **Modalità Mock (Simulazione)**: Attivabile dalle Impostazioni per testare e mostrare l'interfaccia con dati simulati realistici senza bisogno di essere collegati all'inverter.

---

## 📊 Mappatura Registri Modbus (Huawei SUN2000 + LUNA2000)

Tutte le letture utilizzano la **Function Code 03** (Read Holding Registers) con codifica **Big-Endian**:

| Registro | Tipo | Q.tà | Fattore Scala | Unità | Descrizione |
|---|---|---|---|---|---|
| `32064` | INT32 | 2 | 1 | W | Potenza in ingresso Fotovoltaico (PV Input Power) |
| `32080` | INT32 | 2 | 1 | W | Potenza Attiva AC Inverter (Active Power) |
| `32114` | UINT32 | 2 | / 100.0 | kWh | Produzione Giornaliera (Daily Yield) |
| `37113` | INT32 | 2 | 1 | W | Potenza Rete/Meter (`+` Immissione, `-` Prelievo) |
| `37760` | UINT16 | 1 | / 10.0 | % | Stato di Carica Batteria LUNA2000 (Battery SoC) |
| `37765` | INT32 | 2 | 1 | W | Potenza Batteria (`+` In Carica, `-` In Scarica) |

### Calcolo Fabbisogno Domestico (Casa)
```kotlin
val houseConsumptionW = (activePowerW - gridPowerW).coerceAtLeast(0)
```
Questo calcolo garantisce l'esatta corrispondenza con il consumo reale dell'abitazione sia durante la carica/scarica della batteria che durante l'immissione o prelievo da rete.

---

## 🏗️ Architettura del Progetto

```
HuaweiTV/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/huaweitv/solar/
│       │   │   ├── MainActivity.kt               # Entry point TV, FLAG_KEEP_SCREEN_ON, D-Pad
│       │   │   ├── data/
│       │   │   │   ├── model/
│       │   │   │   │   ├── SolarData.kt          # Modello dati telemetria e calcolo consumo
│       │   │   │   │   ├── ConnectionState.kt    # Stati connessione Modbus (Connected, Mock, etc.)
│       │   │   │   │   └── AppSettings.kt        # Configurazione IP, porta, slave ID, polling
│       │   │   │   ├── modbus/
│       │   │   │   │   ├── HuaweiRegisters.kt    # Indirizzi registri SUN2000 / LUNA2000
│       │   │   │   │   ├── ModbusPacket.kt       # Costruzione frame FC 03 e parser Big-Endian
│       │   │   │   │   └── ModbusTcpClient.kt    # Socket client con delay(1500) e delay(200)
│       │   │   │   └── repository/
│       │   │   │       ├── SettingsRepository.kt # Persistenza Jetpack DataStore
│       │   │   │       └── SolarRepository.kt    # Loop di polling e motore Mock
│       │   │   └── ui/
│       │   │       ├── theme/                    # Colori dark OLED, tipografia TV a 3m
│       │   │       ├── components/
│       │   │       │   ├── FocusableTvCard.kt    # Card focusabile D-Pad con glow animato
│       │   │       │   ├── EnergyNodeCard.kt     # Card FV, Batteria, Rete, Casa
│       │   │       │   ├── AnimatedFlowLine.kt   # Particelle animate direzionali con velocità dinamica
│       │   │       │   ├── EnergyFlowDiagram.kt  # Schema 4 nodi + Hub Inverter centrale
│       │   │       │   ├── DailyYieldCard.kt     # Card produzione giornaliera (kWh)
│       │   │       │   └── StatusBar.kt          # Barra superiore con orologio e stato rete
│       │   │       ├── screens/
│       │   │       │   ├── DashboardScreen.kt    # Dashboard a schermata singola (no scroll)
│       │   │       │   └── SettingsDialog.kt     # Dialog impostazioni controllabile con D-Pad
│       │   │       └── viewmodel/
│       │   │           ├── SolarViewModel.kt
│       │   │           └── SolarViewModelFactory.kt
│       │   └── res/                              # Icone vettoriali (sole, batteria, rete, casa) e banner TV
│       └── test/                                 # Unit test per ModbusPacket e calcolo flussi
├── gradle/
│   ├── libs.versions.toml                        # Version Catalog Gradle
│   └── wrapper/
│       ├── gradle-wrapper.properties
│       └── gradle-wrapper.jar
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 Come Aprire e Compilare il Progetto

1. **Apri in Android Studio**:
   - Avvia Android Studio (Ladybug, Koala o successivo).
   - Seleziona **Open** e scegli la cartella `HuaweiTV`.
   - Android Studio eseguirà il sync di Gradle automaticamente.

2. **Compilazione da riga di comando**:
   ```bash
   cd HuaweiTV
   ./gradlew assembleDebug
   ```

3. **Installazione su Android TV**:
   - Abilita le Opzioni Sviluppatore e il Debug ADB sulla tua Android TV o Chromecast con Google TV.
   - Connetti ADB via Wi-Fi:
     ```bash
     adb connect <IP_DELLA_TUA_TV>:5555
     adb install app/build/outputs/apk/debug/app-debug.apk
     ```

---

## ⚙️ Configurazione Inverter Huawei

Per connettere l'app all'inverter:
1. Assicurati che il Modbus TCP sia abilitato sull'inverter Huawei SUN2000 tramite l'app di installazione **FusionSolar** (*Impostazioni comunicazione -> Parametri connessione -> Modbus TCP -> Abilita senza restrizioni*).
2. Dalla dashboard dell'app sulla TV, premi il tasto **MENU** sul telecomando o seleziona il pulsante **Impostazioni**.
3. Imposta l'IP dell'inverter/dongle nella tua rete locale (o `192.168.200.1` se connesso all'AP Wi-Fi interno dell'inverter), porta `502` e Unit ID `1`.
4. Premi **Salva e Applica**: l'app si connetterà immediatamente e aggiornerà i dati ad ogni intervallo di polling configurato.
