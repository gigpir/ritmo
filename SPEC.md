# Ritmo — specifiche del timer a intervalli per iPhone

*Nome provvisorio. Versione 3, 3 novembre 2026: app solo in inglese, funzioni ridotte all'essenziale, round e cicli come in Timer Plus.*

## 1. Obiettivo

Timer Plus – Timer per Workout (App Store id 1279716547, Android `com.vgfit.timerplus`) ora chiede una licenza a pagamento per avviare il timer. Inoltre:

- non è più dello sviluppatore originale VGFIT: su iOS la pubblica ArGames, su Android Uniqo Lab.;
- la sua etichetta privacy dichiara il tracciamento;
- un aggiornamento di settembre 2026 ha modificato i timer salvati.

Ritmo fa lo stesso lavoro, per uso personale: **gratis, senza account, senza pubblicità, senza tracciamento, offline**. L'uso principale è il **Tabata**.

Principi:

- **Semplice**: solo ciò che serve in palestra. Ogni funzione in più deve guadagnarsi il posto.
- **Solo inglese**: testi e voce in inglese, nessuna traduzione.
- **Niente copia**: nome, icona, grafica, testi e suoni sono nostri. Dell'originale riprendiamo solo le funzioni.

## 2. Cosa c'è e cosa no

**Nella prima versione**

- Allenamenti salvati, senza limiti. Struttura: Prepare → intervalli × round → cicli, con un riposo tra un ciclo e l'altro, annunciato come "Recover".
- Timer a schermo intero: Marea in verticale, Tabellone in orizzontale.
- Un bip a ogni secondo degli ultimi 3 di ogni fase, e una voce che annuncia la fase nuova: "Prepare", "Work", "Rest", "Recover".
- Suoni sopra la musica, che si abbassa durante gli avvisi. Vibrazione.
- Funziona con l'iPhone bloccato o in tasca, e il conto alla rovescia resta visibile sulla schermata di blocco e nella Dynamic Island.
- Cinque allenamenti già pronti al primo avvio, a partire dal Tabata.
- Workout da condividere come file `.ritmo` (AirDrop, Messaggi, File) e da importare aprendo il file.

**Tolto per semplicità**

| Funzione | Al suo posto |
|---|---|
| Traduzioni; scelta e velocità della voce | solo inglese, con la voce di sistema |
| Più blocchi di round diversi; defaticamento | una sola lista di intervalli, ripetuta |
| Colore dell'allenamento; colore, suono e testo vocale per ogni intervallo | colore e suono dipendono dalla fase; la voce legge il nome |
| Sezione Modelli, preferiti, riordino | allenamenti già pronti; in cima c'è l'ultimo usato |
| Interruttore "salta l'ultimo recupero" | una regola fissa (§4.2) |
| Opzioni di formato (rimanente o trascorso, tipo di contatori, bip a 0/3/5 s, schermo acceso) | una scelta sola per ciascuna |
| Storico, pulsanti sulla schermata di blocco, Apple Watch, Salute, Siri, widget, playlist | niente. Se ti manca, il primo candidato è lo storico |

## 3. Modello dati

```text
Workout
  name        "Tabata"
  prepare     secondi, default 10 (0 = nessuna fase Prepare)
  intervals   [Interval] in ordine, almeno uno
  rounds      ≥ 1: quante volte si ripete la lista di intervalli
  cycles      ≥ 1: quante volte si ripete il blocco di round
  cycleRest   secondi di riposo tra un ciclo e l'altro, default 0 (nessuno)

Interval
  name        "Work", "Rest", "Burpees"…
  kind        work | rest
  seconds     da 1 s a 99:59
```

Tutti gli allenamenti stanno in un unico file JSON dell'app, nell'ordine della lista: quando ne apri uno, va in cima.

I nomi sono quelli di Timer Plus: i **round** ripetono la lista di intervalli, i **cicli** ripetono i round, con un **riposo tra cicli**. Fino al 3 novembre 2026 cicli e riposo si chiamavano `sets` e `recover`: l'app legge ancora gli elenchi salvati con quei nomi.

Esempi (con la regola §4.2):

| Allenamento | Struttura | Totale |
|---|---|---|
| Tabata | Prepare 0:10 · [Work 0:20, Rest 0:10] × 8 round | 4:00 |
| HIIT 60/15 | Prepare 0:10 · [Work 1:00, Rest 0:15] × 6 round · 5 cicli · riposo tra cicli 0:45 | 39:25 |
| HIIT 40/20 | Prepare 0:10 · [Work 0:40, Rest 0:20] × 10 round | 9:50 |
| Boxing | Prepare 0:10 · [Work 3:00, Rest 1:00] × 12 round | 47:10 |
| Full-body circuit | Prepare 0:10 · [Squats 0:40, Rest 0:20, Push-ups 0:40, Rest 0:20, Plank 0:40, Rest 0:20] × 1 round · 3 cicli · riposo tra cicli 2:00 | 12:10 |

## 4. Logica del timer

### 4.1 Dall'allenamento ai passi

Prima di partire, l'allenamento diventa una lista lineare di passi:

```text
Prepare, se > 0
per ogni ciclo c = 1…C
  per ogni round r = 1…R
    ogni intervallo → passo (ciclo c, round r)
  se c < C e cycleRest > 0 → passo Recover
```

Ogni passo ha nome, fase (prepare | work | rest | recover), durata, ciclo, round e istante di inizio. La durata totale è la somma dei passi.

### 4.2 Regola dell'ultimo Rest

Un Rest seguito subito da un Recover o dalla fine dell'allenamento viene tolto: non ha senso riposare 10 secondi prima di un riposo più lungo, o a fine allenamento. Se tra i cicli non c'è riposo, il Rest resta. Timer Plus funzionava così prima dell'aggiornamento contestato dagli utenti. Qui la regola è fissa, senza interruttore.

### 4.3 Orologio e comandi

- Il tempo si calcola dall'ora di avvio e dalle pause, non a scatti. Così non accumula ritardi, nemmeno dopo il blocco schermo o il background.
- Le cifre arrotondano per eccesso: un passo da 20 s mostra 0:20 al via e 0:01 nell'ultimo secondo. Formato m:ss senza zero iniziale ("0:09", "12:00"), e h:mm:ss oltre l'ora.
- Comandi:
  - pausa/riprendi;
  - avanti;
  - indietro: riavvia il passo se ne sono passati più di 2 s, altrimenti torna al precedente;
  - fine tenendo premuta la X, per evitare tocchi accidentali;
  - audio on/off.
- Una chiamata, Siri o un'altra interruzione audio mettono il timer in pausa.
- Lo schermo resta acceso finché il timer è aperto.

### 4.4 Suoni, voce e vibrazione

| Momento | Cosa succede |
|---|---|
| Ultimi 3 secondi di ogni passo | un bip corto per secondo: 3, 2, 1 |
| Inizio di ogni passo | la voce legge il nome del passo ("Prepare", "Work", "Rest", "Recover", oppure "Burpees" se l'hai chiamato così) e il telefono vibra |
| Fine | la voce dice "Workout complete" e il telefono vibra |

- Con la voce spenta, al posto del nome suona un bip lungo.
- Il pulsante audio del timer zittisce tutto.
- I bip sono toni generati dall'app: nessun file preso da altri.

## 5. Schermate

Il tema è solo scuro. Ogni fase ha il suo colore:

- Prepare: ambra `#FFC21A`;
- Work: arancio `#FF5A1F`;
- Rest: azzurro `#38BDF8`;
- Recover: viola `#A78BFA`.

Arancio e azzurro si distinguono anche con il daltonismo rosso-verde. Il nome della fase è comunque sempre scritto.

### 5.1 Workouts (home)

- Titolo "Workouts". In alto a destra: l'ingranaggio per Settings e "+" per un nuovo allenamento.
- Una scheda per allenamento: nome, sintesi ("8 rounds · 0:20 / 0:10"), durata totale e una striscia colorata con tutti i passi.
- Un tocco apre l'allenamento a schermo intero, fermo sulla prima fase: parte solo con Play. Tenendo premuto compaiono Edit, Duplicate, Share e Delete.
- Margini laterali di 16 punti, come nelle app di sistema, e testo grande: nome 30 pt, durata 34 pt.
- In cima c'è l'ultimo usato. Al primo avvio trovi Tabata, HIIT 60/15, HIIT 40/20, Boxing e Full-body circuit, che puoi modificare o eliminare.

### 5.2 Editor

È un modulo unico, con questi campi in ordine:

1. Name
2. Prepare
3. Intervals: lista con "Add interval"; trascini per riordinare e scorri per eliminare.
4. Rounds
5. Cycles
6. Rest between cycles, annunciato come "Recover"

In cima ci sono la durata totale e la striscia, che si aggiornano mentre modifichi.

Ogni intervallo ha nome, durata (rotelle min:sec) e tipo, Work o Rest. "Add interval" aggiunge un Rest dopo un Work, e un Work dopo un Rest.

### 5.3 Timer in verticale: Marea

- Si apre fermo sulla prima fase, con la marea piena e il pulsante Play.
- Il colore della fase riempie lo schermo e scende man mano che passa il tempo. Dove il livello attraversa le cifre, queste cambiano colore.
- In alto:
  - la X: prima di Play chiude con un tocco; durante l'allenamento va tenuta premuta;
  - il nome dell'allenamento e il tempo totale rimasto ("Tabata · 2:39 left");
  - l'audio on/off.
- Al centro: la fase ("WORK", 60 pt) e un countdown enorme (240 pt). Sotto, "Next: Rest 0:10" con il colore della fase successiva.
- Negli ultimi 3 secondi compare una sola cifra gigante, e la fase successiva appare in una fascia del suo colore.
- In basso:
  - la striscia dell'allenamento: i passi fatti sono attenuati, quello attuale è evidenziato;
  - sotto, nell'ordine: round "3/8", indietro, pausa, avanti, ciclo "1/1".
- Alla fine compaiono "Workout complete", la durata e il pulsante Done.

### 5.4 Timer in orizzontale: Tabellone

Fondo nero, con fase e cifre a tutta altezza nel colore della fase e una barra di avanzamento del passo.

La colonna a destra contiene, dall'alto:

- X e audio;
- ROUND 3/8 e CYCLE 1/1;
- NEXT;
- TIME LEFT;
- indietro, pausa e avanti.

### 5.5 Settings

Tre interruttori, tutti attivi di default: Voice, Vibration e Lower music during cues. Sotto, la sezione Workouts: Share all workouts e Import from Files.

### 5.6 Schermata di blocco e Dynamic Island

Durante l'allenamento una Live Activity mostra fase, conto alla rovescia, fase successiva e round, nei colori della fase. Il conto alla rovescia lo fa scorrere iOS da solo; l'app aggiorna la Live Activity solo quando cambia fase, va in pausa o salta un passo. Per ora niente pulsanti.

### 5.7 Condivisione

- Un workout si condivide dal menu della sua scheda (Share); tutti insieme da Settings (Share all workouts).
- Il file è un `.ritmo`: JSON con `format`, `version` e la lista `workouts`, scrivibile anche a mano (gli id mancanti vengono creati). Esempio in `examples/HIIT 60-15.ritmo`.
- Aprire un `.ritmo` sull'iPhone, da AirDrop, Messaggi, Mail o File, aggiunge i suoi workout in cima alla lista; quelli già presenti, uguali in tutto tranne l'id, vengono saltati. Lo stesso fa Import from Files in Settings.

## 6. Requisiti iOS

- **Musica**: la sessione audio è `.playback` + `.mixWithOthers`, quindi l'app suona anche con l'iPhone in silenzioso. Con "Lower music during cues" si aggiunge `.duckOthers`, dal primo bip fino alla fine dell'annuncio.
- **Schermo bloccato**: l'app usa la modalità background "audio" e, durante l'allenamento, riproduce silenzio in loop. Così resta attiva, e bip e voce continuano anche con l'iPhone in tasca.
- **Voce**: `AVSpeechSynthesizer`, con una voce inglese di sistema.
- **Vibrazione**: `AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)`, anche in background.
- **Live Activity**: estensione `RitmoLive` (WidgetKit + ActivityKit) e `NSSupportsLiveActivities` nell'app. Parte al primo Play e si chiude alla fine.
- **Dati**: un file JSON locale. Nessun account, rete, iCloud o SDK esterno.
- **Dispositivi**: solo iPhone, in verticale e in orizzontale, con iOS 17 o successivo.

Da provare sul tuo iPhone nella prima settimana:

- l'app resta attiva a schermo bloccato per un allenamento intero;
- la musica si abbassa solo durante gli avvisi, poi torna al volume normale;
- la vibrazione arriva anche con l'app in background.

## 7. Struttura del codice

SwiftUI, nessuna dipendenza esterna. La logica dei §4.1–4.4 non dipende da iOS ed è coperta da test, che girano sul Mac con `swift test`.

```text
Ritmo.xcodeproj
Ritmo/
  RitmoApp.swift      avvio
  WorkoutStore.swift  lista degli allenamenti, salvata in un file JSON
  LiveActivityController.swift   avvia, aggiorna e chiude la Live Activity
  Sharing.swift       file .ritmo: condivisione e importazione
  Core/               Workout, passi, orologio, segnali, toni   (con test)
  Audio/              CuePlayer: sessione audio, bip, voce, vibrazione
  Views/              Workouts, Editor, Timer (Marea e Tabellone), Settings
  Assets.xcassets     icona e colore
RitmoLive/            la Live Activity: schermata di blocco e Dynamic Island
Shared/               colori delle fasi e dati della Live Activity, usati da entrambi
Config/               Info.plist dell'app e dell'estensione
Tests/                test della logica
Package.swift         serve solo a eseguire i test dal terminale
install.sh            compila e installa sull'iPhone collegato
examples/             workout da importare
docs/screenshots/     immagini per il README
```

## 8. Installazione sul tuo iPhone

Serve **Xcode** (provato con la 27.2). Se il Terminale usa ancora i Command Line Tools, per usare Xcode anche da lì, e lanciare `swift test` senza altro, esegui una volta `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`.

Con un **Apple ID gratuito** (la strada scelta):

1. In Xcode → Settings → Apple Accounts aggiungi il tuo Apple ID.
2. Scrivi il tuo team e il prefisso dell'identificativo dell'app in `Config/Local.xcconfig`, come spiega `Config/Signing.xcconfig`. Quel file resta fuori da git, così il repository pubblico non contiene dati del tuo account.
3. Collega l'iPhone con il cavo, sbloccalo e tocca "Autorizza".
4. Sull'iPhone attiva Impostazioni → Privacy e sicurezza → Modalità sviluppatore, poi riavvialo.
5. In Xcode scegli l'iPhone in alto e premi Run (⌘R). La prima volta l'iPhone ti chiede di autorizzare lo sviluppatore, in Impostazioni → Generali → VPN e gestione dispositivi.

La prima volta macOS chiede la password del Mac per usare la chiave di firma: rispondi "Consenti sempre".

**Ogni settimana**, prima che la firma scada: apri `Ritmo.xcodeproj` in Xcode con l'iPhone collegato e premi ▶ (Run). Con Xcode 27 serve proprio Xcode: da Terminale il tuo Apple ID non si vede, quindi `./install.sh` reinstalla l'app (per esempio dopo una modifica) solo finché la firma della settimana è valida.

**Ogni 7 giorni la firma scade**: l'app non si apre più finché non la reinstalli, ma i dati restano. È una regola di Apple per tutte le app che non arrivano dall'App Store, e non ci sono eccezioni gratuite. Si può però rendere quasi invisibile:

| Modo | Costo | Com'è |
|---|---|---|
| Xcode via Wi-Fi | 0 € | dopo il primo collegamento col cavo, Xcode vede l'iPhone anche in rete: una volta a settimana premi Run, e basta un minuto |
| SideStore | 0 € | un'app open source che rinnova da sola la firma sull'iPhone; la configurazione iniziale è più complicata |
| Account sviluppatore di un amico | 0 € | lui la distribuisce con TestFlight (ogni versione dura 90 giorni) o la firma per il tuo iPhone (dura un anno); ogni aggiornamento passa da lui |
| Apple Developer Program | 99 €/anno | firma valida un anno |
| Web app | 0 € | non scade, ma lo schermo deve restare acceso e non vibra |

## 9. Domande aperte

Nessuna per ora. Gli allenamenti principali sono il Tabata e l'HIIT 60/15.

## 10. Design

Scelta: **Marea** in verticale e **Tabellone** in orizzontale; le schermate sono in `docs/screenshots`. L'app usa i caratteri di sistema: SF Pro Compressed per le cifre, SF Pro per il resto.
