# STATUS (24.09.2026, posle ručnog reset-a korisnika)

Korisnik je ručno (van ove sesije, verovatno kroz Android Studio) uradio `git reset` na
`db01558` - isti commit na koji sam i ja prethodno vratio stanje. Provereno `git diff -w
db01558` na celom working tree-u = nula stvarnih razlika (samo CRLF kozmetika koja je i dalje
prisutna, nebitna za build/rad app-a).

**Izmena ekrana za vežbanje (na zahtev):** ranije je gornja polovina ekrana za vežbanje imala
DVA odvojena prikaza jedan pored drugog - desno live kamera, levo posebna crna kutija sa
iscrtanim skeletom (koji uopšte nije bio ni skaliran prema toj kutiji, samo su sirove piksel
koordinate iz analize slike crtane direktno preko nje). Sad je to jedan jedinstven prikaz -
skelet se iscrtava DIREKTNO preko live kamere, u istom Box-u.

Za ovo je bilo potrebno srediti koordinatno mapiranje (do sad nije ni postojalo kako treba):
- `PoseDetectorProcessor.processImage` sad pored landmarka vraća i dimenzije analizirane slike
  (`imageWidth`/`imageHeight`), svedene na "upright" orijentaciju (zamenjene ako je rotacija
  90/270) - isti pristup kao u zvaničnom ML Kit quickstart uzorku (`GraphicOverlay.
  setImageSourceInfo`).
- `ExerciseViewModel` prosleđuje te dimenzije kroz novi `frameSize: StateFlow<Pair<Int,Int>>`,
  potpuno odvojeno od `landmarks` - logika brojanja (`applyEMAAndOutlierRejection`, analyzeri)
  i dalje radi sa sirovim landmark koordinatama, ništa od ovoga nije dirano.
- `PoseOverlay` sad prima `sourceWidth`/`sourceHeight`/`mirror` i sam preračunava landmark
  pozicije na stvarnu veličinu Canvas-a, istom FILL_CENTER logikom koju `PreviewView` koristi za
  samu kameru (scale = max(canvasW/imgW, canvasH/imgH), centrirano) + horizontalni mirror za
  prednju kameru (PreviewView sam ogleda prednju kameru radi "selfie" efekta, sirovi landmarci
  iz ImageAnalysis-a NISU ogledani, pa bi se bez ovoga skelet video horizontalno obrnuto u
  odnosu na sliku).
- `ExerciseScreen` - top Box sad ima CameraPreview.fillMaxSize() ispod i PoseOverlay.fillMaxSize()
  iznad njega (isti Box, dva sloja), umesto dva odvojena Box-a sa 45% širine.

Verifikovano: `PoseOverlay.kt` kompajlira čisto protiv ručno napravljenih Compose Canvas/DrawScope
stub-ova (sandbox nema pravi Compose classpath, ali stub API oblik odgovara pravom). Čista
matematika mapiranja (scale/offset/mirror) izvučena i testirana samostalno - centar slike mapira
se u centar Canvas-a, mirror tačno reflektuje X oko horizontalne sredine Canvas-a, Y ostaje
netaknut mirror-om. `PoseDetectorProcessor`/`ExerciseViewModel` diff protiv `db01558` potvrđuje
da je logika brojanja ponavljanja (EMA, outlier rejection, analyzeri) potpuno netaknuta - dirano
je samo prosleđivanje dimenzija slike za overlay.

**Napomena za fizičko testiranje:** matematika i tipovi su verifikovani koliko je moguće bez
pravog Android SDK-a/kamere, ali stvarno poklapanje skeleta sa telom na ekranu (posebno mirror
za prednju kameru, i FILL_CENTER crop na uređajima gde PreviewView zaista seče ivice slike) mora
se potvrditi na telefonu.

---

# STATUS (23.09.2026.)

**VRAĆENO (24.09.2026, na eksplicitan zahtev korisnika):** ceo UI/performance rad opisan ispod
(Help/onboarding ekrani + "optimizacija" pipeline-a, i sva tri kruga jurenja regresija koja su
usledila - rezolucija, executor, zamrzavanje landmarka) je vraćen commit-om `6ab3ebf`. Trenutno
stanje projekta = tačno `db01558` (fix za wrist-stability kod zgibova) za sve fajlove koje je taj
rad dirao - provereno `git diff db01558 -- <fajl>` = prazno za svih 13 izmenjenih fajlova, a 3
nova fajla (`TrackedLandmark.kt`, `HelpScreen.kt`, `OnboardingScreen.kt`) su uklonjena. Urađeno
kao NOVI commit (ne `reset --hard`), pa je sav ovaj rad i dalje potpuno dostupan u istoriji
(`f4b9120`/`1bb9dec`) ako se ikad poželi vratiti pojedinačni deo (npr. sama Help stranica, ili
napomena o wrist-stability tajmingu). Razlog: i posle tri popravke istog simptoma (kašnjenje u
brojanju/"brljanje"), korisnik je i dalje osećao da nešto ne štima uživo na telefonu - umesto
dalje lova na četvrti mogući uzrok, odlučeno da se vrati na poslednje potvrđeno dobro stanje.
Napomena o alatu: ovaj mount ne dozvoljava `rm`/`unlink` direktno (ni git-u internamo), pa su 3
nova fajla fizički premeštena u `_reverted_removed_ask_before_deleting/` u root-u projekta (van
build-a, ne kompajliraju se više) - taj folder možeš slobodno obrisati sa svog računara kad
budeš hteo, git ga ionako ne prati.

**Sledeći koraci ako se ikad ponovo krene u optimizaciju/UI dodatke:** raditi mnogo manje
korake odjednom (jedna izmena, jedan build+test na telefonu, tek onda sledeća), umesto celog
paketa optimizacija u jednom prolazu - ovoga puta je bilo teško izolovati koja od 6+ izmena
uzrokuje koji simptom baš zato što je sve urađeno i testirano zajedno.

---

# STATUS (23.09.2026.)

Sve faze (0-4) su implementirane i commit-ovane lokalno (7 commit-ova ispred origin/master - **treba `git push`**, cloud alat nema mrežni pristup do tvog računara pa to ja ne mogu odavde).

- Faza 0 (git higijena): gotovo.
- Faza 1 (baza - delete, exportSchema): gotovo.
- Faza 2 (ExerciseType + registry + slobodan ručni unos + dinamički Stats filter): gotovo.
- Faza 3 (deljeni PhaseBasedRepCounter za sklekove i zgibove): gotovo, ali pragovi za sklekove
  su prva procena - treba fizičko testiranje/kalibracija u Android Studio-u, kao što je
  ranije rađeno za zgibove.
- Faza 4 (ime app-a, kamera nije obavezna, EMA duplikat, front/back kamera toggle, tačan
  7-dnevni grafikon, unit testovi za PhaseBasedRepCounter): gotovo. Testovi su stvarno
  kompajlirani i pokrenuti (van Android Studio-a, protiv istog JUnit4 jar-a koji projekat
  koristi) pre commit-a - 6/6 prolazi.
- Dodatni prolaz kroz `PullUpAnalyzer` (23.09.2026, na zahtev - "zgibovi najzanimljiviji,
  neka rade savršeno"): nađen i ispravljen pravi regresivni bug uveden refaktorom iz Faze 3
  (commit `db01558`) - `wristStableFrames` (zaštita od "zamahivanja"/varanja) se ažurirao
  bezuslovno svaki frejm umesto samo tokom `WORKING` faze, pa je provera skoro uvek prolazila
  i pored zamahivanja. Popravljeno tako da se broji samo tokom `WORKING`, resetuje na
  `RESTING`, zamrzava tokom `RETURNING`, i vraćeno na tačno mesto (posle `handsAboveHead`
  provere) kao u originalnom kodu pre refaktora. Dokazano sintetičkom simulacijom nad pravim
  `PhaseBasedRepCounter`-om (čist zgib se i dalje broji, zamahnut zgib se sada ispravno
  odbija) i kompajliranjem cele klase protiv tipski tačnih MLKit/Android stub-ova.
  NAPOMENA za fizičko testiranje: `MIN_STABLE_WRIST_FRAMES = 5` (treba 6 uzastopnih stabilnih
  frejmova tokom same faze vučenja) sada se stvarno primenjuje kako je i zamišljeno - ako se
  na telefonu pokaže da brzi, ali tehnički ispravni zgibovi povremeno dobijaju "KEEP HANDS
  STILL ON BAR" (jer `AccuratePoseDetectorOptions` + `STREAM_MODE` mogu da rade sporije od
  kamere pa WORKING faza fizički kratkog zgiba dobije premalo analiziranih frejmova), prag
  treba spustiti (npr. na 3) ili prebaciti na vremenski prag umesto brojanja frejmova.
  Ostatak `PhaseBasedRepCounter`/`PullUpAnalyzer` (kalibracija, RESTING/WORKING/RETURNING
  prelazi, timeout, biranje strane) pregledan i deluje ispravno - nije nađen dodatni potvrđen
  bug.

**POZNAT NEDOSTATAK (nije još rešeno, na zahtev ostavljeno za kasnije):** i `PullUpAnalyzer`
(`WRIST_STABILITY_THRESHOLD`, `minMovement`) i `PushUpAnalyzer` (`minMovement`, rastojanje
rame-zglob) mere pomeraj u sirovim pikselima ML Kit-ovog `PoseLandmark.position`, koji su u
koordinatnom sistemu analizirane slike (ne normalizovano 0-1) - ta rezolucija zavisi od uređaja
(CameraX bira rezoluciju za `ImageAnalysis`, nema `.setTargetResolution()` u `CameraPreview.kt`),
kao i od udaljenosti korisnika od kamere. Isti fizički pokret na drugom uređaju/udaljenosti daje
drugačiji broj piksela pomeraja, pa trenutni pragovi (15px i sl.) nisu preneseni sa uređaja na
uređaj. Ugaoni pragovi (`angleDropToStart`, `angleRecoverMargin`) OVIM NISU pogođeni - ugao je
geometrijski invarijantan na rezoluciju/udaljenost.

Predloženo rešenje (kad se bude radilo): normalizovati pomeraj kao odnos prema stabilnoj
telesnoj referenci izmerenoj u istom frejmu (npr. dužina rame-kuk ili rame-lakat), umesto
sirovih piksela - `pomeraj_px / referenca_px` je (skoro) nezavisno i od rezolucije kamere i od
udaljenosti korisnika jer se brojilac i imenilac skaliraju zajedno. Dira zajednički
`PhaseBasedRepCounter` (koriste ga i zgibovi i sklekovi), i posle izmene bi trebalo ponovo
fizički ištelovati sve px-bazirane pragove na telefonu jer se menja jedinica mere.

Sledeći korak: build + fizičko testiranje u Android Studio-u - prvo zgibovi (proveriti gornju
napomenu o MIN_STABLE_WRIST_FRAMES), zatim sklekovi. Normalizacija piksela je odložena za kasnije
(videti pasus iznad).

**POTVRDA problema iz prakse (24.09.2026):** u sklopu optimizacije pipeline-a (uklanjanje
reflection-a, pozadinski executor za ML Kit callback-ove, itd.) probano je i ograničavanje
rezolucije analize kamere na ~480p (`ResolutionSelector`/`ResolutionStrategy` u
`CameraPreview.kt`) radi brže detekcije. Posle toga je brojanje počelo da kasni za stvarnim
pokretom ("stiže sa malim zakašnjenjem") - tačno zbog gore opisanog problema: isti fizički pokret
na manjoj rezoluciji daje manje piksela pomeraja, pa je trebalo više stvarnog pokreta da bi fiksni
prag u pikselima (npr. `rise > 5f` za prelazak WORKING -> RETURNING) bio dostignut. Promena je
VRAĆENA (`CameraPreview.kt` više ne postavlja `ResolutionSelector`, kamera koristi CameraX-ov
podrazumevani izbor rezolucije kao i pre) - ovo je direktan dokaz da su pikselski pragovi zaista
osetljivi na rezoluciju, i da čim se rezolucija analize promeni (bilo namerno ograničavanjem, bilo
razlikom između uređaja), tajming brojanja se pomera. Kad se bude radila normalizacija (pasus
iznad), rezoluciju bi trebalo biti slobodno moguće menjati/ograničiti bez posledica po tajming.

**Drugi pokušaj i drugi revert (24.09.2026):** posle vraćanja rezolucije, korisnik je i dalje
prijavio kašnjenje (~1-2s) koje ranije nije postojalo - konkretno, čim se ruke ispruže/spuste na
dole kod zgibova, brojanje se ranije javljalo skoro odmah, a sad kasni. Drugi osumnjičeni iz iste
runde optimizacije: `PoseDetectorProcessor` je ML Kit-ove Task callback-ove
(`addOnSuccessListener`/`addOnFailureListener`/`addOnCompleteListener`) prebacio sa
podrazumevanog glavnog (UI) thread-a na dedikovani pozadinski `Executor`, u nameri da se EMA
smoothing i `PhaseBasedRepCounter`-ova matematika ne takmiče sa Compose renderovanjem za CPU.
`image.close()` (koji `STRATEGY_KEEP_ONLY_LATEST` koristi da otključa isporuku SLEDEĆEG frejma od
CameraX-a) se poziva baš u `addOnCompleteListener`, pa je promena thread-a na kom se to dešava
verovatno uticala na stvarni protok frejmova (throttling), a ne samo na CPU efikasnost - za
razliku od ostalih optimizacija iz iste runde (uklonjen reflection, spojene mape, EMA bez
boksovanja, hoistovane liste u PoseOverlay) koje su algoritamski/matematički dokazano identične
prethodnom ponašanju i nemaju mehanizam kojim bi usporile dolazak frejmova. Ova promena je
VRAĆENA (`PoseDetectorProcessor.kt` opet koristi podrazumevane, glavni-thread callback-ove) -
vraćeno je na konfiguraciju za koju je potvrđeno da nema kašnjenja. Ako se ubuduće ipak želi
skloniti obrada sa UI thread-a, bolji pristup bi bio: zadržati ML Kit callback-ove na glavnom
thread-u (da se `image.close()`/protok frejmova ne dira), a samo TEŽAK deo posla (EMA + analyzer)
prebaciti na pozadinski coroutine/dispatcher POSLE prijema landmarka - to zahteva pažljivije
rukovanje thread-safety-jem oko deljenog stanja (landmarkStates mapa) i nije urađeno ovom
prilikom zbog rizika bez mogućnosti fizičkog testiranja uživo.

**Treći pokušaj (24.09.2026) - pravi uzrok pronađen kroz `git diff db01558..HEAD`:** korisnik je
i dalje prijavljivao "sporije reaguje"/"brljavi", potvrđeno odmah na hladnom startu (isključuje
thermal throttling) i potvrđeno da nije bilo tako pre optimizacije. Pošto su prva dva uzroka
(rezolucija, executor) već vraćena i `git diff db01558..HEAD` na celom pipeline-u
(`PoseDetectorProcessor`, `CameraPreview`, `ExerciseViewModel`, `PullUpAnalyzer`,
`PushUpAnalyzer`, `EMA`, `PoseOverlay`, `MainActivity`) nije pokazao nijednu preostalu
rezolucijsku/thread-ing razliku u odnosu na `db01558`, sumnja je pala na JEDINU preostalu
funkcionalnu (ne samo refaktor) izmenu: fix iz commit-a `f4b9120` (zamrzavanje landmarka ispod
`MIN_CONFIDENCE_FOR_LANDMARK` da se reši "noga se razvuče" bag) primenjivao se na SVE landmarke,
uključujući rame/lakat/šaku - baš one koje `PullUpAnalyzer`/`PushUpAnalyzer` koriste za računanje
ugla i brojanje. Mehanizam: brz pokret (npr. tačno u trenutku ispružanja/spuštanja ruku kod
zgiba) izaziva motion blur, ML Kit-ova pouzdanost za lakat/šaku kratko padne ispod 0.3, landmark
se "zamrzava" na poslednjoj poziciji BAŠ u tom kritičnom trenutku, a pošto EMA filter za pozicije
koristi vrlo spor `alpha=0.08` (potrebno mnogo frejmova da "sustigne" skokovitu promenu), taj
kratak zamrzaj se kroz EMA razvuče u primetno kašnjenje - isti simptom kao kod prva dva uzroka,
ali kroz treći, potpuno drugi mehanizam koji je slučajno uveden dok se rešavao NEPOVEZAN vizuelni
bag.

**Fix:** dodata `COUNTING_CRITICAL_LANDMARKS` (rame/lakat/šaka, oba boka) u
`ExerciseViewModel.applyEMAAndOutlierRejection` - zamrzavanje ispod `MIN_CONFIDENCE_FOR_LANDMARK`
se sada namerno PRESKAČE za te landmarke (vraćeno na ponašanje pre `f4b9120`: sirova vrednost
ispod 0.5, outlier-rejection iznad 0.5), dok se za sve ostale (kuk, kolena, članci - čisto
kozmetički, koriste se samo za `PoseOverlay` i push-up-ovu opcionu "FULL BODY NOT VISIBLE" poruku,
nikad za sam brojač) zamrzavanje i dalje primenjuje, pa "noga se razvuče" bag ostaje rešen.
Dokazano sintetičkim testom (`ViewModelSmoothingLogic.kt`, grupa 2): lakat sa naglo palom
pouzdanošću (0.2) i dalje pomera EMA izlaz (`moved=true`), dok koleno sa istom situacijom u grupi
1 i dalje ostaje zamrznuto. Napomena: ovo je i dalje samo rezonovanje + sintetički test bez
kamere/telefona - potrebna je fizička potvrda da je "sporije reaguje" osećaj zaista nestao.

---

# Plan sređivanja WorkoutApp projekta

## Kontekst

Projekat je funkcionalan MVP (Android/Kotlin, Compose, MVVM, Hilt, Room, CameraX + ML Kit Pose) koji broji sklekove i zgibove preko kamere. Iz pregleda koda i git istorije se vidi da je logika brojanja ponavljanja prošla kroz dosta ručnog štelovanja (posebno zgibovi), da je baza svedena na jednu tabelu bez mogućnosti brisanja i bez migracione strategije, da su vežbe hardkodovane na više mesta umesto na jednom, i da su IDE fajlovi (`.idea/`) greškom ostali u git istoriji iako ih `.gitignore` već pokriva. Cilj ovog plana je da se to sredi pre nego što počnu veće prepravke i dodavanje novih vežbi, kako bi svaka sledeća izmena bila jednostavnija i sigurnija.

Plan je podeljen u faze po prioritetu/riziku. Faze 0 i 1 su brze i bezbedne, Faza 2 je najveći i najvažniji posao (arhitektura vežbi), Faza 3 cilja direktno na problem "brojanje ne radi uvek lepo", Faza 4 su preostale manje odluke.

**Proizvodna vizija (bitno za arhitekturu, potvrđeno od korisnika):** aplikacija treba u potpunosti da pokrije evidenciju treninga za street workout (vežbe bez tegova — bitan je samo broj ponavljanja, nema kilaže). Dva ravnopravna puta unosa u istu bazu:
1. **Kamera** — samo za vežbe za koje postoji naš `ExerciseAnalyzer` (trenutno sklekovi i zgibovi, plan je da se dodaju čučnjevi i dr.) — automatski broji.
2. **Ručni unos** — mora da pokrije *bilo koju* vežbu koju je korisnik radio, ne samo onu dvojicu koje imaju analyzer (npr. dips, L-sit, muscle-up — sve što nema kamersku podršku).

Oba puta pišu u istu `Exercise` tabelu, a Stats ekran treba da pokaže akumulirane ukupne brojeve po vežbi ("koliko je ukupno uradio čega"), ne samo zbir preko trenutnog filtera. Ovo direktno utiče na dizajn iz Faze 2 — vidi izmene dole.

---

## Faza 0 — Git higijena (nizak rizik, radi se prva)

**Problem:** `.gitignore` (root) već ispravno ignoriše `.idea/`, ali gomila `.idea/*` fajlova je već bila commit-ovana pre nego što je pravilo dodato, pa git nastavlja da ih prati i prijavljuje kao izmenjene (videli smo 11 takvih fajlova u `git status`).

**Odluka:**
- `git rm -r --cached .idea` — skida fajlove iz praćenja, ali ih **ne briše sa diska** (ostaju lokalno za Android Studio).
- Uskladiti `app/.gitignore` sa root `.gitignore`-om: root već ima blanket `.idea/` pravilo, `app/.gitignore` ima samo delimičnu listu pojedinačnih `.idea/*.xml` fajlova — pojednostaviti tako da ne postoji duplirana/nepotpuna logika (root `.gitignore` je dovoljan za ceo repo, `app/.gitignore` može ostati samo za `app`-specifične stvari kao `google_maps_api.xml`).
- Proveriti da `local.properties` i `build/` ostanu neuključeni (već su, potvrđeno).
- Jedan poseban commit: `chore: untrack IDE files, tighten gitignore` — odvojeno od funkcionalnih izmena da diff ostane čitljiv.
- Posle ovoga: push 5 lokalnih commit-ova ka `origin/master` koji trenutno stoje samo lokalno (rizik od gubitka rada ako se ne pushuju).

---

## Faza 1 — Baza podataka: sigurnost i osnovne operacije

**Problem 1 — nema migracione strategije.** `ExerciseDatabase` je `version = 1`, `exportSchema = false`, bez `Migration` objekata i bez `fallbackToDestructiveMigration()`. Čim se doda kolona/tabela i podigne se verzija, aplikacija će pucati kod postojećih korisnika sa podacima.

**Odluka:** uključiti `exportSchema = true` (šeme idu u `app/schemas/`, korisno za dijafing budućih migracija) i od sledeće promene šeme pisati eksplicitne `Migration` objekte umesto `fallbackToDestructiveMigration()` — pošto je ovo lični trening dnevnik, brisanje istorije korisnika kod update-a nije prihvatljivo.

**Problem 2 — nema brisanja unosa.** DAO ima samo `insert`/`update`(neiskorišćen)/`getAllExercises`.

**Odluka:**
- Dodati u `ExerciseDao`: `@Delete suspend fun delete(e: Exercise)`.
- Dodati `deleteExercise()` u `ExerciseViewModel`.
- UI: swipe-to-delete (ili long-press → confirm dialog) na redovima istorije u `HomeScreen` i `StatsScreens` — ista `Exercise` lista se prikazuje na oba mesta, pa se pravi jedan zajednički composable za red istorije sa delete akcijom umesto trenutne duplirane logike (`HomeScreen`-ov inline `Card`, `HistoryRow` u `ExerciseScreen.kt` koji izgleda da je čak i mrtav/neiskorišćen kod, i `StatsScreens`-ov `ListItem`).

**Problem 3 — `type: Boolean` polje se ne koristi** (predviđeno za brojive vs. vremenske vežbe, ali se uvek šalje `true`).

**Odluka:** ne dirati izolovano — rešava se prirodno kroz Fazu 2 kad se uvede pravi katalog vežbi (vidi dole), jer tip vežbe (reps vs. vreme) tada postaje osobina `ExerciseType`, ne proizvoljan boolean koji se ručno postavlja.

**Problem 4 — naziv baze `"running_database"`** ne odgovara nameni (ostatak iz starijeg template-a).

**Odluka:** ne menjati sada — preimenovanje fajla baze zahteva sopstvenu migraciju sa kopiranjem podataka, nosi rizik, a naziv fajla je čisto interna stvar bez uticaja na korisnika. Ostaviti kako jeste, eventualno dodati komentar u kodu zašto se zove tako.

---

## Faza 2 — Proširiva arhitektura vežbi (glavni deo)

**Problem:** identitet vežbe je slobodan string (`"Push Ups"`, `"Pull Ups"`) dupliran na najmanje 4 mesta:
- `ExerciseViewModel.analyzer()` — ručni `if/else`
- `HomeScreen` — `listOf("Push Ups", "Pull Ups")`
- `StatsScreens` — `listOf("All", "Push Ups", "Pull Ups")`
- `ExerciseScreen` — `if (exerciseType == "Push Ups")` grane za naslov/boje

Dodatno (potvrđeno prod. vizijom): ručni unos je danas zaključan na `currentExerciseType`, tj. korisnik može ručno da uloguje *samo* Push Ups ili Pull Ups — a treba da može bilo koju street workout vežbu (dips, čučnjeve, L-sit, muscle-up...), bez obzira da li ima kamerski analyzer.

**Odluka — dvoslojni model umesto jednog zatvorenog enuma:**

1. **Sloj A — kamera-analizirane vežbe (zatvoren skup, definisan u kodu).** `enum class ExerciseType(val id: String, val displayName: String, val isTimeBased: Boolean)` u `exercise/ExerciseType.kt`, sa `PUSH_UPS`, `PULL_UPS` za sada (kasnije `SQUATS` i dr. kad se napiše analyzer). Koristi se isključivo za Training/kamera flow — bira koji `ExerciseAnalyzer` da se pokrene. `id` je identičan trenutnim string vrednostima u bazi (`"Push Ups"`, `"Pull Ups"`) da se izbegne migracija postojećih redova.
2. **Sloj B — ručni unos (otvoren skup, slobodan tekst).** `Exercise.name` ostaje `String` kao i danas — to je i dalje jedini izvor istine za ono što je stvarno ulogovano, bilo kamerom bilo ručno. Ručni dijalog u `HomeScreen` se menja iz "koliko push-ups/pull-ups" (zaključano na `currentExerciseType`) u polje za naziv vežbe + broj ponavljanja, gde naziv nudi autocomplete/quick-pick iz: (a) `ExerciseType.entries` (ugrađene vežbe) i (b) istorijski već korišćenih naziva iz baze (nova DAO metoda `@Query("SELECT DISTINCT name FROM Exercise ORDER BY name") fun getDistinctExerciseNames(): Flow<List<String>>`), ali dozvoljava i potpuno nov, slobodno ukucan naziv. Nema posebne "katalog" tabele za v1 — nepotrebna komplikacija dok se ne pokaže potreba (npr. tipfeleri koji cepaju statistiku na dva različita naziva se rešavaju kroz isti autocomplete koji ohrabruje ponovnu upotrebu postojećeg naziva).
3. `ExerciseAnalyzerRegistry` (Hilt-injected mapiranje `ExerciseType -> ExerciseAnalyzer`, sastavljeno iz postojećih `@Provides` u `ExerciseModule`) — zamenjuje ručni `if/else` u ViewModel-u za kamera-flow. Dodavanje nove kamera-vežbe = nova `ExerciseAnalyzer` implementacija + jedan novi `@Provides`/mapping unos, bez diranja ViewModel-a ili UI-ja.
4. `ExerciseViewModel`: `_currentExerciseType` (za Training ekran) postaje `MutableStateFlow<ExerciseType>`, `analyzer()` postaje `registry.get(type)`. Za ručni unos dodaje se odvojen `logManualExercise(name: String, reps: Int)` koji prima proizvoljan naziv (ne prolazi kroz `ExerciseType`).
5. `HomeScreen`/`ExerciseScreen` Training deo prelazi sa hardkodovanih `if/else` grana na iteraciju kroz `ExerciseType.entries` + `.displayName` — nova kamera-vežba više ne traži izmene ovde.
6. **Stats ekran — nova stavka:** dodati sekciju "Ukupno po vežbi" (accumulated totals) koja grupiše *celu* istoriju (ne samo trenutni filter) po `name` i prikazuje zbir ponavljanja po svakoj — direktan odgovor na "koliko je akumulirano uradio koječega". Filter-dropdown u `StatsScreens` (`listOf("All", "Push Ups", "Pull Ups")`) prelazi sa hardkodovane liste na dinamičku iz `getDistinctExerciseNames()`, tako da se odmah pojavi i svaka ručno uneta vežba, ne samo dve ugrađene.
7. `ExerciseAnalyzer` interfejs (u `exercise/ExerciseAnalyzer.kt`) ostaje nepromenjen — dobar je i dovoljno generički (`analyze`/`reset`).

Ovim se dodavanje nove **kamera** vežbe svodi na: (a) nova `Analyzer` klasa, (b) jedan enum unos, (c) jedan Hilt `@Provides`. Dodavanje nove **ručne** vežbe ne traži nikakvu izmenu koda — korisnik je prosto ukuca u dijalogu.

**Napomena o kilaži:** potvrđeno da nije potrebna (street workout, samo ponavljanja) — trenutna šema (`numOf: Int` = reps ili sekunde preko `isTimeBased`) ostaje dovoljna, ne dodaje se novo polje za težinu.

---

## Faza 3 — Logika brojanja ponavljanja (zašto ne radi uvek lepo)

**Dijagnoza iz koda:**

- `PullUpAnalyzer` je solidno urađen: ima fazu kalibracije (15 frejmova) koja izračuna ličnu baznu liniju ugla, state-machine sa `HANGING → PULLING_UP → LOWERING`, potvrdu preko više stabilnih frejmova (`hangStableFrames`, `endStableFrames`) pre nego što prizna fazu, i timeout zaštitu. Ovo objašnjava zašto zgibovi rade relativno pouzdano nakon svih onih "calibrating pullups params" commit-ova.
- `PushUpAnalyzer` **nema ništa od toga**: fiksni pragovi (`155`, `100`, `130`, `distRatio 0.85`) bez ikakve kalibracije po korisniku/kameri/uglu snimanja, jedna `isUp` boolean varijabla bez potvrde kroz više frejmova, bez minimalne pouzdanosti landmarka pre računanja ugla (samo bira stranu sa boljim confidence-om, ali ne odbacuje frejm ako je i najbolja strana loša). Ovo je najverovatniji uzrok da "ne radi uvek baš lepo" — pragovi štimovani za jedan setup (rastojanje od kamere, ugao) neće raditi identično za drugog korisnika/telefon.

**Odluka:** izvući zajedničku, već dokazanu logiku iz `PullUpAnalyzer`-a (kalibracija + faze + potvrda kroz stabilne frejmove + timeout) u deljenu komponentu, i naslediti/koristiti je i za sklekove. Konkretno:

1. Novi `exercise/PhaseBasedRepCounter` (ili apstraktna baza) koja generalizuje šablon iz `PullUpAnalyzer`-a: kalibracija bazne linije ugla, EMA smoothing, faze sa hysteresis pragovima, potvrda kroz N stabilnih frejmova, timeout na "zaglavljenoj" fazi. Parametrizuje se po vežbi: koji ugao se meri (koje tri tačke), pragovi (drop/recover/min-rise), minimalna pouzdanost landmarka.
2. `PushUpAnalyzer` se prepravlja da koristi ovu bazu umesto svoje pojednostavljene `isUp` logike — dobija kalibraciju i potvrdu kroz stabilne frejmove kao i zgibovi.
3. `PullUpAnalyzer` se u istom prolazu refaktoriše da koristi istu deljenu bazu (umesto da ostane jedini koji ima punu logiku) — ovo direktno servisira i Fazu 2 (nova vežba = samo isporuči ugao + pragove, dobiješ proverenu state-machinu besplatno).
4. Dodati minimalni prag pouzdanosti (npr. `inFrameLikelihood > 0.25`, po uzoru na `PullUpAnalyzer.chooseBestSide`) u zajedničku bazu, da se izbegne računanje ugla iz nepouzdanih landmarka.
5. Popraviti `PoseDetectorProcessor.processImage` — `addOnFailureListener` je trenutno prazan; dodati bar `Log.w` da se greške ML Kit-a vide umesto da nestanu tiho (olakšava buduće debug-ovanje "zašto nije izbrojao rep").

**Napomena:** stvarna preciznost se u krajnjoj liniji mora proveriti fizičkim testiranjem (kao i do sad), ali ovo direktno adresira strukturni uzrok nekonzistentnosti kod sklekova.

**Potvrđeno od strane korisnika:** sklekovi nisu aktivno razvijani/tuning-ovani od početne verzije (otud fiksni, neproveravani pragovi u kodu) — to se poklapa sa dijagnozom iznad. Zgibovi su bili u fokusu i rade uglavnom dobro, ali ne savršeno, pa i posle strukturnog refaktora (deljena baza) treba računati na još jedan krug fine kalibracije pragova (`PULL_ANGLE_DROP`, `LOWER_ANGLE_RECOVER`, `MIN_SHOULDER_RISE_PX` i sl.) kroz fizičko testiranje — refaktor rešava strukturu, ne zamenjuje empirijsko štelovanje koje je već rađeno.

---

## Faza 4 — Preostale odluke (manje, mogu ići nezavisno jedna od druge)

- **Kamera je obavezan hardver u manifestu** (`<uses-feature android:name="android.hardware.camera.any" android:required="true" />`), a app ima i ručni unos bez kamere → promeniti u `required="false"` i dodati runtime proveru (`PackageManager.hasSystemFeature(FEATURE_CAMERA_ANY)`) koja na uređajima bez kamere odmah nudi samo ručni unos, bez pokušaja da se veže CameraX.
- **Duplirana `EMA` klasa** — postoji i u `filters/EMA.kt` i ponovo definisana na dnu `ExerciseViewModel.kt`. Obrisati kopiju iz ViewModel-a, dodati `import dev.milinko.workoutapp.filters.EMA`.
- **Reflection hack** u `ExerciseViewModel.createSmoothedLandmark` (menja privatno polje `PoseLandmark`-a preko reflection-a na svaki frejm/landmark) — pravi strukturni fix (sopstveni `SmoothedLandmark` tip kroz ceo pipeline) je veći refaktor koji ne mora sada. Kompromis: keširati `Field` objekat statički (lookup jednom, ne svaki frejm) — jeftin performance dobitak bez redizajna.
- **Kamera samo prednja** (`CameraSelector.DEFAULT_FRONT_CAMERA` hardkodovano u `CameraPreview.kt`) — dodati front/back toggle na `ExerciseScreen`.
- **Naslov grafikona "Activity Graph (Last 7 Days)"** u `StatsScreens.kt` ne prikazuje bukvalno poslednjih 7 kalendarskih dana, nego poslednjih 7 dana *sa aktivnošću* iz filtrirane liste → promeniti `WorkoutBarChart` da generiše fiksnih 7 kalendarskih dana unazad (uključujući dane sa 0 ponavljanja), tačnije i i dalje jednostavno.
- **Ime aplikacije — odlučeno: "PoseTrack".** Uskladiti na sva mesta: `strings.xml` (`app_name` = "PoseTrack"), TopAppBar u `HomeScreen.kt` (trenutno "FitVision"), i eventualno naslov u `ExerciseScreen`/`StatsScreens` ako referenciraju ime. README već ima ovo ime, ostaje kako jeste. `applicationId`/`namespace` (`dev.milinko.workoutapp`) se ne dira — to je interni paket, menjanje nosi rizik (npr. gubitak potpisa/istorije na Play Store-u ako se ikad objavi) i nema uticaja na korisnika.
- **Nema testova** — nakon Faze 3 (deljena `PhaseBasedRepCounter`), dodati unit testove koji kroz nju provlače sintetičke nizove landmarka/uglova i proveravaju da li se rep ispravno detektuje — mnogo lakše testirati jednu deljenu komponentu nego svaki analyzer posebno.

---

## Redosled rada

1. Faza 0 (git) — odmah, nula rizika po funkcionalnost.
2. Faza 1 (baza: delete + migration safety net) — pre bilo kakvog diranja `Exercise` šeme.
3. Faza 2 (arhitektura vežbi) — temelj za sve posle.
4. Faza 3 (brojanje ponavljanja) — prirodno se nadovezuje na Fazu 2 (deljena baza za analyzere).
5. Faza 4 — sitnice, mogu paralelno/po slobodnom izboru.

## Verifikacija

- Nakon Faze 0: `git status` čist, `git ls-files | grep .idea` prazno.
- Nakon Faze 1/2/3: build kroz Android Studio (`./gradlew assembleDebug` ili Run) posle svake faze — ja mogu da uređujem fajlove i proveravam da kod ima smisla, ali stvarni build/run i fizičko testiranje brojanja ponavljanja mora da se radi na tvom telefonu/emulatoru u Android Studio-u, jer cloud okruženje nema pristup Android SDK-u/emulatoru niti kameri.
- Ručna QA lista za bazu: dodaj unos → obriši unos → restartuj app → proveri da li je istorija konzistentna.
- Za brojanje ponavljanja: isti pristup kao dosad (fizičko testiranje), plus novi unit testovi iz Faze 4 kao regresiona zaštita ubuduće.
