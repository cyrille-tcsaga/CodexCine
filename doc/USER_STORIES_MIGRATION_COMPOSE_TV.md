# Migration de Leanback vers Jetpack Compose for TV : user stories

Document de pilotage de la migration de l'interface de Codex Ciné. Il découpe le travail en epics et en
stories livrables une à une, **sans jamais casser l'application existante**.

- Version du projet analysée : `main` au commit `863f061` (AGP 9.5.1, Kotlin 2.2.10, Leanback 1.2.0, `minSdk` 28).
- Points d'effort : suite de Fibonacci (1, 2, 3, 5, 8, 13). 1 point ≈ une demi-journée d'un développeur qui connaît le projet.
- Les versions de bibliothèques Compose / `tv-material` sont à **vérifier au démarrage de l'US-01** (prendre les dernières stables).

---

## 1. Contexte et objectif

L'interface est entièrement construite sur Leanback (24 fichiers sur 106, environ 4 100 lignes d'UI), avec des
fragments hébergés par `FragmentHostActivity` et un thème `Theme.Leanback`.

**Objectif** : remplacer Leanback par Jetpack Compose for TV (`androidx.tv:tv-material` + Compose foundation),
retirer la dépendance `androidx.leanback` et obtenir une application **identique du point de vue de
l'utilisateur** : même design, mêmes comportements à la télécommande, mêmes liens profonds, mêmes performances
ressenties.

**Hors périmètre** : la couche `data/` et `domain/`, les view models (leur API `StateFlow` ne change pas), le
`WebView` durci du lecteur (`HardenedWebViewFactory`, `PlayerBridge`, `AdHostBlocklist`), `WatchNextPublisher`,
`SearchSuggestionProvider`, le mappage de télécommande `RemoteKeyMapper`. Tout cela est réutilisé tel quel.

---

## 2. Inventaire de l'existant

### 2.1 Fichiers qui importent Leanback

| Écran / zone | Fichiers | Équivalent Compose visé |
|---|---|---|
| Accueil (coque) | `MainFragment` (`BrowseSupportFragment`, `PageRow`), `SidebarHeaderPresenter`, `HomePage` | `NavigationDrawer` (tv-material) + navigation interne |
| Accueil (pages) | `HomePageFragment` (`RowsSupportFragment`), `BillboardRow`, `MessageRow`, `HomePage` | `LazyColumn` de `LazyRow` + billboard |
| Cartes | `BaseCardPresenter`, `CardPresenter`, `ContinueWatchingCardPresenter`, `NewEpisodeCardPresenter`, `MoreCardPresenter`, `CastCardPresenter`, `SeasonCardPresenter`, `EpisodeCardPresenter`, `CardOverlays` | composable `CodexCard` (+ variantes) |
| Rangées | `CardRows` (`cardRowPresenter`, `cardGridPresenter`) | composable `CardRow` |
| Détails | `VideoDetailsFragment` (`RowsSupportFragment`), `DetailsHeroRow`, `view_details_hero.xml` | `DetailsScreen` |
| Saison | `SeasonFragment`, `EpisodeMenu`, `EpisodeCardPresenter` | `SeasonScreen` |
| Grilles | `BaseGridFragment` (`VerticalGridSupportFragment`), `MediaGridFragment`, `FilterTitleView`, `PersonFragment` | `MediaGrid` générique |
| Recherche | `SearchFragment` (`SearchSupportFragment`) | `SearchScreen` |
| Erreur | `ErrorFragment` (`ErrorSupportFragment`) | `ErrorScreen` |
| Lecteur direct | `PlaybackVideoFragment` (`VideoSupportFragment`, `LeanbackPlayerAdapter`, glue) | `media3-ui-compose` ou suppression (voir US-18) |
| Fond d'écran | `BackgroundManager` utilisé dans accueil, détails, recherche | état de fond partagé en Compose |

### 2.2 Éléments à ne pas oublier

- Thème : `Theme.CodexCine` a pour parent `Theme.Leanback` (à remplacer). `Theme.CodexCine.Starting` (splash) reste.
- Hôte : `FragmentHostActivity` (remplacement d'un fragment par un écran d'erreur, nouvelle tentative).
- Vues personnalisées en XML : `view_details_hero`, `view_home_hero`, `view_grid_title`, `view_message_row`, `view_sidebar_item`, `view_player_overlay`, `view_player_speed_option`.
- `PlayerOverlayView` et `PlayerProgressView` (lecteur web) : vues personnalisées, panneaux `SPEED`, `EXIT`, `RESUME`, `NEXT`, `ERROR`.
- Manifeste : `android.software.leanback` est une **déclaration de fonctionnalité** pour le lanceur TV, pas la bibliothèque. **Elle reste** après la migration.
- Liens profonds : `codexcine://play/...` et `codexcine://details/...` (`PlayLinkActivity`), recherche externe (`SearchActivity` exportée).
- Aucun test d'interface n'existe (`androidTest` absent). 16 fichiers de tests unitaires couvrent surtout les view models et la logique : ils restent valables.

### 2.3 Comportements à conserver (liste de parité)

Cette liste sert de grille de recette pour chaque story d'écran.

1. Carte : image à coins arrondis, anneau ivoire et léger zoom quand elle est sélectionnée, titre et détail dessous.
2. « Continuer à regarder » : barre de progression sur l'image, reste à voir, appui long OK → menu Reprendre / Recommencer / Retirer.
3. Accueil : les rangées arrivent une à une **sans voler le focus** ; le billboard change l'arrière-plan selon la sélection ; la page s'ouvre sur le billboard.
4. Barre latérale : pages Accueil, Films, Séries, etc. (voir `HomePage`), icônes, logo, accès à la recherche.
5. Rangées : carte finale « Voir tout » (sauf « Ma liste » et « À propos »), carte « À propos ».
6. Détails : fond = backdrop, métadonnées, badges VF/VOSTFR/Indisponible, boutons Lecture / Bande-annonce / Ma liste, rangées Saga, Saisons, Casting, Recommandations. Lecture disponible avant la fin du chargement.
7. Saison : épisodes vus marqués, appui long → menu, indication de l'appui long, trois cartes de 264 dp par ligne.
8. Grilles : 5 colonnes, pagination, filtres (genre, décennie, note, tri).
9. Recherche : saisie, voix (permission `RECORD_AUDIO`), recherche externe (Assistant), résultats en rangées.
10. Erreur : message, bouton Réessayer (absent si jeton invalide).
11. Lecteur : panneaux de l'overlay, invite de reprise, compte à rebours de 5 s avant l'épisode suivant, vitesse, sortie, télécommande native.
12. Ligne système « Play next » et liens profonds inchangés.
13. Retour (touche Back) : même comportement partout (fermeture d'un menu, retour à l'écran précédent, focus restauré).

---

## 3. Principes pour ne rien casser

1. **Migration par étranglement** : l'ancien écran Leanback reste en place tant que le nouveau n'a pas passé la recette. Les deux coexistent via un hôte `ComposeView` dans un `Fragment`.
2. **Drapeau par écran** : chaque écran migré est derrière un drapeau (`BuildConfig` en debug, valeur par défaut = ancien écran). On ne bascule la valeur par défaut qu'après recette. Le drapeau est supprimé en fin d'epic.
3. **View models intouchables** : un changement de view model n'est autorisé que s'il est nécessaire et couvert par un test.
4. **Une story = un commit (ou une petite série)**, mergeable seule, application toujours compilable et lançable.
5. **Porte de qualité à chaque story** : `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` au vert, parcours de recette sur l'émulateur `Television_1080p` (voir US-03), aucune exception dans `logcat`.
6. **Pas de big bang** : l'accueil, le plus risqué, passe en dernier. Le lecteur web ne change que dans son overlay.
7. **Focus d'abord** : le focus à la télécommande est le premier risque. Chaque story d'écran a des critères d'acceptation de focus explicites.

---

## 4. Definition of Done commune

- [ ] Critères d'acceptation de la story remplis, éléments de la liste de parité (§2.3) concernés vérifiés.
- [ ] Tests unitaires verts ; nouveaux tests ajoutés pour toute logique extraite des vues.
- [ ] Parcours D-pad complet fait sur l'émulateur TV : haut, bas, gauche, droite, OK, appui long OK, Back.
- [ ] Aucun crash ni `StrictMode` violation dans `logcat` pendant le parcours.
- [ ] Pas de régression de temps de démarrage ni de fluidité de défilement (mesure en US-03).
- [ ] Drapeau de l'écran présent, valeur par défaut documentée.
- [ ] `DECISIONS.md` mis à jour si un choix non trivial a été fait.

---

## 5. Vue d'ensemble

| Epic | Contenu | Points | Risque |
|---|---|---|---|
| E0 Fondations | Compose, thème, hôte, filet de sécurité, composants de base | 21 | faible |
| E1 Écrans simples | Erreur, À propos, personne, saison | 13 | faible |
| E2 Grilles et recherche | Grille générique, filtres, recherche | 16 | moyen |
| E3 Détails | Écran de détails complet | 13 | moyen |
| E4 Accueil | Coque, rangées, billboard, fond, appui long | 34 | **élevé** |
| E5 Lecteur | Overlay web, lecteur direct | 21 | **élevé** |
| E6 Nettoyage | Retrait de Leanback, recette finale | 8 | faible |
| **Total** | | **126** | ≈ 12 à 16 semaines-personne à mi-temps, 6 à 8 à plein temps |

Ordre conseillé : E0 → E1 → E2 → E3 → E4 → E5 → E6. E5 peut démarrer en parallèle d'E4 (aucun fichier commun).

---

## E0. Fondations

### US-01. Ajouter Compose et `tv-material` au build

**En tant que** développeur, **je veux** pouvoir écrire des composables dans le projet, **afin de** migrer écran par écran sans changer le reste.

- **Critères d'acceptation**
  - Le plugin `org.jetbrains.kotlin.plugin.compose` (aligné sur Kotlin 2.2.10), la BOM Compose, `androidx.tv:tv-material`, `activity-compose`, `lifecycle-runtime-compose` sont ajoutés via `libs.versions.toml`.
  - L'application compile, se lance et se comporte comme avant (aucun écran changé).
  - R8 en release : règles vérifiées, build `release` lancé.
  - Taille de l'APK et temps de démarrage à froid mesurés et notés dans `DECISIONS.md` (référence avant/après).
- **Tâches** : versions, plugin, `buildFeatures { compose = true }`, test `@Preview`/composable minimal, vérif `lint`.
- **Points** : 3 · **Dépend de** : rien.

### US-02. Thème Compose équivalent au thème actuel

**En tant que** spectateur, **je veux** retrouver les mêmes couleurs et polices, **afin que** l'application ne change pas d'aspect.

- **Critères d'acceptation**
  - `CodexCineTheme` reprend les couleurs `brand_*` (night, ink, ivory, mist, amber…) et les polices `brand_text` / `brand_display` depuis `res/`.
  - Échelle typographique (titre 52 sp, texte 18 sp, badges 15 sp, etc.) extraite des layouts existants.
  - Marges de sécurité TV (overscan) respectées, mêmes valeurs que `hero_padding_start` et dimensions actuelles.
  - Tailles d'écran 1080p et 720p vérifiées sur émulateur.
- **Points** : 3 · **Dépend de** : US-01.

### US-03. Filet de sécurité : hôte Compose, drapeaux, recette automatisable

**En tant que** développeur, **je veux** un moyen sûr de basculer un écran entre Leanback et Compose, **afin de** pouvoir revenir en arrière à tout moment.

- **Critères d'acceptation**
  - `ComposeScreenFragment` (ou helper) héberge un `ComposeView` avec `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` et fonctionne dans `FragmentHostActivity` (y compris remplacement par `ErrorFragment`).
  - Un drapeau par écran (`ComposeFlags`) choisit l'ancienne ou la nouvelle implémentation ; défaut = ancienne.
  - Un script `tools/smoke.sh` ouvre, via `adb` sur l'émulateur **uniquement** (`-s emulator-5554`), les écrans par liens profonds (`codexcine://details/movie/550`, etc.), attend, puis échoue s'il trouve `FATAL EXCEPTION` dans `logcat`.
  - Une checklist de recette (liste de parité §2.3) est ajoutée à la description des PR.
  - Mesures de référence prises : démarrage à froid, mémoire, images perdues au défilement de l'accueil (`dumpsys gfxinfo`).
- **Points** : 5 · **Dépend de** : US-01.

### US-04. Composants de base : carte, rangée, focus, appui long

**En tant que** développeur, **je veux** des briques Compose réutilisables, **afin que** tous les écrans aient le même rendu et le même focus.

- **Critères d'acceptation**
  - `CodexCard` : image `widthDp × heightDp` à coins arrondis, anneau ivoire et zoom quand sélectionnée, titre et détail dessous ; variante ronde (casting) ; emplacement pour une surimpression (barre de progression, remplace `ProgressBarOverlay`).
  - Variantes : `MediaCard`, `ContinueWatchingCard`, `NewEpisodeCard`, `MoreCard`, `CastCard`, `SeasonCard`, `EpisodeCard` (mêmes tailles qu'aujourd'hui : 264×148 pour « continuer », 120×180 saison, 124 rond casting).
  - `Modifier.onLongClickWithDpad(...)` : appui long OK au D-pad, sans déclencher aussi le clic au relâchement.
  - `CardRow` (titre + `LazyRow`) avec clés stables, `focusRestorer`, mémoire de la dernière carte focalisée.
  - Chargement d'images : choix documenté (Glide actuel via `glide-compose` ou Coil), mêmes tailles décodées (`override`, RGB_565 si conservé) ; image d'erreur et de remplissage identiques.
  - Tests : prévisualisations et un test Compose sur le comportement de l'appui long (`createComposeRule`).
- **Points** : 10 · **Dépend de** : US-02, US-03.

---

## E1. Écrans simples

### US-05. Écran d'erreur

**En tant que** spectateur, **je veux** voir le même écran d'erreur et pouvoir réessayer, **afin de** me remettre d'un échec réseau.

- **Critères d'acceptation**
  - `ErrorScreen` remplace `ErrorFragment` : titre optionnel, message, bouton Réessayer (focus initial dessus), pas de bouton si le jeton est invalide (`retryable = false`).
  - Réessayer recrée l'écran contenu comme aujourd'hui (`FragmentHostActivity.showContent`).
  - Back ferme l'écran comme avant.
- **Points** : 2 · **Dépend de** : US-04.

### US-06. Écran « À propos »

- **Critères d'acceptation** : même contenu, même navigation, `AboutActivity` hébergée par l'hôte Compose.
- **Points** : 2 · **Dépend de** : US-04.

### US-07. Écran de personne (filmographie)

- **Critères d'acceptation**
  - Grille de 5 colonnes de `MediaCard`, tri par popularité inchangé (logique dans `PersonViewModel`).
  - OK ouvre le détail (`openMediaItem`), Back revient.
  - Utilise `MediaGrid` (US-09) ; en attendant, grille minimale réutilisable.
- **Points** : 3 · **Dépend de** : US-04 (et US-09 si livrée avant).

### US-08. Écran de saison

**En tant que** spectateur, **je veux** retrouver la grille d'épisodes, les marques « vu » et le menu d'appui long, **afin de** suivre une série.

- **Critères d'acceptation**
  - Trois cartes `EpisodeCard` de 264 dp par ligne, épisodes vus marqués, titre `EpisodeNames` inchangé.
  - Appui long OK → menu (actions de `SeasonViewModel.actionsFor`), mêmes libellés et mêmes effets, focus restauré à la fermeture.
  - Indication d'appui long affichée comme aujourd'hui (`season_hint_long_press`).
  - OK lance la lecture de l'épisode (extras identiques).
- **Points** : 6 · **Dépend de** : US-04.

---

## E2. Grilles et recherche

### US-09. Grille générique `MediaGrid`

**En tant que** développeur, **je veux** une grille réutilisable, **afin de** remplacer `BaseGridFragment`.

- **Critères d'acceptation**
  - 5 colonnes, défilement vertical fluide, focus qui monte et descend sans saut, restauration du focus au retour.
  - Pagination déclenchée près de la fin (comportement de `GridViewModel` conservé).
  - États vide / chargement / erreur identiques.
- **Points** : 5 · **Dépend de** : US-04.

### US-10. Écran « Voir tout » avec filtres

**En tant que** spectateur, **je veux** filtrer par genre, décennie, note et tri, **afin de** trouver un titre.

- **Critères d'acceptation**
  - `FilterTitleView` remplacé par une barre de filtres focalisable (menus de choix en dialogue Compose ou `AlertDialog` conservé).
  - Résultats mis à jour via `GridViewModel` sans le modifier ; filtres actifs visibles ; réinitialisation possible.
  - Tests de `GridViewModelTest` inchangés et verts.
- **Points** : 5 · **Dépend de** : US-09.

### US-11. Recherche texte, voix et externe

**En tant que** spectateur, **je veux** chercher par saisie ou par la voix, **afin de** trouver un titre vite.

- **Critères d'acceptation**
  - Champ de saisie avec clavier TV, résultats en rangées de cartes, regroupés comme aujourd'hui (`SearchViewModel`), état « aucun résultat ».
  - Voix : `SpeechRecognizer` (ou intent de reconnaissance) avec demande de `RECORD_AUDIO` ; refus géré sans crash. Remplace le recogniser intégré de Leanback.
  - `SearchActivity` exportée, filtres d'intents (`SEARCH`, `SEARCH_ACTION`) et `SearchSuggestionProvider` inchangés ; requête nettoyée comme avant.
  - `SearchViewModelTest` inchangé et vert.
- **Points** : 6 · **Dépend de** : US-04.

---

## E3. Détails

### US-12. Écran de détails

**En tant que** spectateur, **je veux** la même page de titre, **afin de** décider de lancer la lecture.

- **Critères d'acceptation**
  - Héros : étiquette Film/Série, titre, métadonnées (année, durée ou saisons, certification, note avec étoile), genres, résumé (4 lignes), badges de langue ou « Indisponible » (`PlayAvailability`).
  - Boutons Lecture / Bande-annonce (si clé) / Ma liste (si état connu), Lecture utilisable avant la réponse réseau, focus initial sur Lecture, **focus conservé** quand les données arrivent.
  - Fond : backdrop plein écran avec voile (`backdrop_scrim`), couleur `brand_night` pendant le chargement.
  - Rangées : Saga (juste sous le héros), Saisons, Casting, Recommandations, sans doublons si une seconde réponse arrive.
  - Indicateur de chargement affiché **dans le conteneur de l'écran** (cause d'un crash passé : voir commit `2153488`), jamais ajouté à un `RecyclerView`.
  - Liens : casting → personne, saison → saison, recommandation/saga → autre détail, bande-annonce → YouTube (app ou navigateur, toast si aucune).
  - Parcours testé sur un film, une série, un film de saga, un titre sans backdrop ni résumé.
- **Points** : 13 · **Dépend de** : US-04, US-08.

---

## E4. Accueil

L'accueil concentre l'essentiel du risque. Il est découpé pour être livré par étapes, chacune derrière le drapeau `home`.

### US-13. Coque de navigation : barre latérale et pages

**En tant que** spectateur, **je veux** naviguer entre Accueil, Films, Séries… avec la barre latérale, **afin de** parcourir le catalogue.

- **Critères d'acceptation**
  - `ModalNavigationDrawer` (tv-material), comme dans le guide Android TV : rail d'icônes replié, déployé par-dessus la page sur un fond dégradé, logo en tête, **« Recherche » en premier élément**, puis les pages de `HomePage` (icône + libellé). Plus de bouton de recherche flottant. La page change quand on valide un élément, pas au simple passage du focus.
  - Ouverture de la barre : Gauche depuis la première colonne ; fermeture : Droite ou OK ; Back ferme la barre puis quitte l'application comme avant.
  - Chaque page lit le même `MainViewModel` (chargé une seule fois, partagé).
  - L'état d'erreur initial affiche `ErrorScreen`.
- **Points** : 8 · **Dépend de** : US-05, US-04.

### US-14. Rangées de la page, sans vol de focus

**En tant que** spectateur, **je veux** que les rangées apparaissent au fur et à mesure sans que ma sélection saute, **afin de** pouvoir naviguer pendant le chargement.

- **Critères d'acceptation**
  - `LazyColumn` de `CardRow` avec clés stables (`headerId`) ; ajout, retrait et mise à jour (continuer à regarder, nouveaux épisodes, ma liste) sans reconstruire la liste ni déplacer le focus.
  - Types de cartes gérés : `MediaItem`, `WatchProgress`, `NewEpisode`, `ActionCard` (Voir tout, À propos), `MessageRow` (page vide).
  - Retrait de l'élément sélectionné d'une rangée : le focus passe à un voisin ; si la rangée disparaît, au plus proche.
  - Restauration du focus au retour d'un écran ouvert (position de rangée et de carte).
  - Défilement fluide mesuré (comparé à la référence de US-03).
- **Points** : 13 · **Dépend de** : US-13.

### US-15. Billboard et arrière-plan dynamique

**En tant que** spectateur, **je veux** le grand billboard et le fond qui suit ma sélection, **afin de** retrouver l'ambiance actuelle.

- **Critères d'acceptation**
  - Billboard (`BillboardRow` / `view_home_hero`) : titre, résumé, Lecture / Plus d'infos, rotation des titres mis en avant toutes les 8 s (`ROTATION_MS`) comme aujourd'hui ; la page s'ouvre dessus.
  - Fond (`BackgroundManager` remplacé) : image du billboard ou de la carte sélectionnée en fondu, voile `backdrop_scrim`, annulation des chargements obsolètes (`backgroundJob`).
  - Lecture depuis le billboard : démarre à l'épisode où le spectateur s'est arrêté pour une série (`latestEpisode`).
- **Points** : 8 · **Dépend de** : US-14.

### US-16. Menu d'appui long de « Continuer à regarder »

- **Critères d'acceptation**
  - Mêmes trois actions (Reprendre, Recommencer sans supprimer la progression via `startOver`, Retirer), mêmes libellés, titre avec saison/épisode.
  - Retrait immédiat de la carte, écriture sur `persistenceScope`, ligne système « Play next » à jour.
  - Focus restauré après fermeture ; menu fermé si l'écran est détruit.
  - Indication d'appui long envisagée (hors parité, à valider avec le produit).
- **Points** : 5 · **Dépend de** : US-14.

---

## E5. Lecteur

### US-17. Overlay du lecteur web en Compose

**En tant que** spectateur, **je veux** les mêmes contrôles par-dessus la vidéo, **afin de** garder une lecture fiable.

- **Critères d'acceptation**
  - `WebPlayerFragment` conserve son `WebView` durci (`HardenedWebViewFactory`, bridge, liste de blocage inchangés) ; l'overlay passe de `PlayerOverlayView` / `PlayerProgressView` à des composables superposés au `WebView` (via `AndroidView`).
  - Panneaux `SPEED`, `EXIT`, `RESUME`, `NEXT`, `ERROR` identiques, barre de progression, compte à rebours de 5 s avant l'épisode suivant, invite de reprise.
  - Les touches continuent d'être traitées par `dispatchKeyEvent` + `RemoteKeyMapper` : aucune régression de la télécommande (lecture/pause, avance/retour, Back).
  - Le focus Compose ne vole jamais les touches destinées au `WebView` pendant la lecture.
  - `WebPlayerViewModelTest`, `WebPlayerLogicTest` inchangés et verts.
  - Test sur un film et une série, avec reprise, `startOver`, fin d'épisode et erreur réseau.
- **Points** : 13 · **Dépend de** : US-04.

### US-18. Lecteur direct (Media3) : migrer ou supprimer

**En tant que** développeur, **je veux** décider du sort de `PlaybackVideoFragment`, **afin de** ne pas garder Leanback pour un seul cas.

- **Critères d'acceptation**
  - Décision écrite dans `DECISIONS.md` : ce chemin sert aux flux `StreamSource.Direct` (échantillon de test et debug). Soit suppression avec `SampleStreamResolver`, soit migration vers `media3-ui-compose` (`PlayerSurface` + contrôles Compose).
  - Aucune référence à `media3-ui-leanback` ni à `LeanbackPlayerAdapter` à la fin.
  - Si migré : lecture, pause, avance/retour, reprise, sauvegarde de progression vérifiées avec le flux d'échantillon.
- **Points** : 8 · **Dépend de** : US-17.

---

## E6. Nettoyage et recette finale

### US-19. Retirer Leanback

**En tant que** développeur, **je veux** supprimer la dépendance, **afin de** finir la migration.

- **Critères d'acceptation**
  - Plus aucun `import androidx.leanback` ; dépendances `androidx-leanback` et `media3-ui-leanback` retirées de `libs.versions.toml` et `build.gradle.kts`.
  - `Theme.CodexCine` n'a plus `Theme.Leanback` pour parent (thème adapté, splash `Theme.CodexCine.Starting` conservé) ; styles et layouts XML devenus inutiles supprimés ; `FragmentHostActivity` remplacée par `ComponentActivity` + `setContent`, ou conservée si des fragments subsistent.
  - Tous les drapeaux de migration supprimés, une seule implémentation par écran.
  - **`uses-feature android.software.leanback` conservé** dans le manifeste.
  - Build `release` (R8) lancé, application installée sur l'émulateur TV.
- **Points** : 5 · **Dépend de** : toutes les stories d'écran.

### US-20. Recette finale et non-régression

- **Critères d'acceptation**
  - Liste de parité (§2.3) rejouée en entier sur l'émulateur TV et sur au moins un appareil réel (box ou TV).
  - Mesures comparées à la référence de US-03 : démarrage à froid, mémoire, fluidité de l'accueil ; écart accepté documenté.
  - Liens profonds, recherche externe, ligne « Play next », mise à jour GitHub (`UpdatePrompt`) vérifiés.
  - `DECISIONS.md` et `README.md` à jour (architecture UI, thème, focus).
  - Nouvelle version taguée seulement après validation.
- **Points** : 3 · **Dépend de** : US-19.

---

## 6. Risques et parades

| Risque | Gravité | Parade |
|---|---|---|
| Perte ou saut de focus au D-pad (accueil, détails, retours d'écran) | élevée | `focusRestorer`, clés stables, critères de focus par story, recette manuelle systématique |
| Vol de focus quand les rangées arrivent en différé | élevée | mises à jour de liste par clé, test manuel pendant le chargement lent (réseau limité) |
| Régressions de performance sur box d'entrée de gamme | moyenne | mesures en US-03, profil de base (baseline profile), tailles d'images décodées identiques |
| Touches interceptées pendant la lecture (overlay vs `WebView`) | élevée | garder `dispatchKeyEvent` + `RemoteKeyMapper`, tests sur série et film |
| Voix et recherche externe (Assistant) | moyenne | conserver `SearchActivity` et le fournisseur, tester `RECORD_AUDIO` refusée |
| Maturité de `tv-material` (API qui bouge) | moyenne | isoler derrière nos propres composables (`CodexCard`, `CardRow`), verrouiller les versions |
| Aucun test d'interface existant | moyenne | tests Compose ciblés (carte, appui long), `tools/smoke.sh`, recette manuelle outillée |
| Double maintenance pendant la coexistence | faible | drapeaux courts, un écran à la fois, suppression en E6 |

---

## 7. Suivi

| Story | Titre | Points | État |
|---|---|---|---|
| US-01 | Compose et `tv-material` dans le build | 3 | à faire |
| US-02 | Thème Compose | 3 | à faire |
| US-03 | Filet de sécurité (hôte, drapeaux, recette) | 5 | à faire |
| US-04 | Composants de base | 10 | à faire |
| US-05 | Écran d'erreur | 2 | à faire |
| US-06 | Écran « À propos » | 2 | à faire |
| US-07 | Écran de personne | 3 | à faire |
| US-08 | Écran de saison | 6 | à faire |
| US-09 | Grille générique | 5 | à faire |
| US-10 | « Voir tout » avec filtres | 5 | à faire |
| US-11 | Recherche | 6 | à faire |
| US-12 | Détails | 13 | à faire |
| US-13 | Coque de navigation | 8 | à faire |
| US-14 | Rangées sans vol de focus | 13 | à faire |
| US-15 | Billboard et fond dynamique | 8 | à faire |
| US-16 | Appui long « Continuer à regarder » | 5 | à faire |
| US-17 | Overlay du lecteur web | 13 | à faire |
| US-18 | Lecteur direct Media3 | 8 | à faire |
| US-19 | Retirer Leanback | 5 | à faire |
| US-20 | Recette finale | 3 | à faire |
