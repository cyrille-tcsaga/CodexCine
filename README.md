# Codex Ciné

Navigateur de films et de séries pour Android TV, basé sur l'API TMDB et l'interface Leanback.
Application à usage personnel, installée manuellement : elle n'est pas destinée à Google Play.

Appareils visés : Xiaomi TV Box S (Google TV 14, API 34) et Mi Box S (Android TV 9, API 28).

TMDB ne fournit que des métadonnées. Les vidéos viennent du service tiers **Vidzy**, dont le lecteur
web s'affiche dans une `WebView` verrouillée, sous des commandes natives pilotées à la télécommande.
Si Vidzy n'a pas le titre, l'application affiche « Aucune source de lecture disponible pour ce titre. »

Ni l'application ni son auteur ne contrôlent ce service, ses contenus ou leurs droits de diffusion :
vérifiez que son usage est légal là où vous vous trouvez.

## 1. Configurer le jeton TMDB

1. Créez un compte sur [themoviedb.org](https://www.themoviedb.org/).
2. Ouvrez [Paramètres > API](https://www.themoviedb.org/settings/api) et demandez une clé.
3. Copiez le **jeton d'accès en lecture à l'API** (*API Read Access Token*), une longue chaîne qui
   commence par `eyJ`. Ce n'est pas la « clé API » courte.
4. Ajoutez-le au fichier `local.properties`, à la racine du projet :

   ```properties
   TMDB_ACCESS_TOKEN=eyJhbGciOi...
   ```

5. Recompilez l'application. Sans jeton, le projet compile quand même et l'application affiche un
   écran d'erreur qui explique quoi faire.

`local.properties` est ignoré par Git : le jeton n'est jamais versionné. Il est en revanche inclus
dans l'APK, donc ne partagez pas un APK compilé avec votre jeton.

## 2. Tester le lecteur avec une vidéo d'exemple

Pour vérifier le lecteur de bout en bout, ajoutez cette ligne à `local.properties` :

```properties
USE_SAMPLE_STREAM=true
```

Après recompilation, le bouton **Lecture** diffuse une vidéo de test publique avec le lecteur Media3,
quel que soit le titre, sans passer par Vidzy. Cette option ne s'applique qu'aux versions `debug` : elle
est toujours désactivée en `release`.

## 3. Installer sur le boîtier par le réseau

Sur le téléviseur :

1. Ouvrez **Paramètres > Système > À propos** et appuyez sept fois sur **Build Android TV** pour
   activer les options pour les développeurs.
2. Dans **Paramètres > Système > Options pour les développeurs**, activez le **débogage USB** ou le
   **débogage sans fil**, selon ce que propose le boîtier.
3. Notez l'adresse IP du boîtier.

Sur l'ordinateur :

```bash
# Mi Box S (Android TV 9) et autres appareils en débogage réseau classique
adb connect <IP_DU_BOITIER>:5555

# Xiaomi TV Box S (Google TV 14) en débogage sans fil :
# le port et le code d'association sont affichés dans les options pour les développeurs
adb pair <IP_DU_BOITIER>:<PORT_ASSOCIATION>
adb connect <IP_DU_BOITIER>:<PORT_CONNEXION>

# Vérifier que l'appareil est bien connecté, puis installer
adb devices
./gradlew :app:installDebug
```

L'application apparaît ensuite sur l'écran d'accueil du téléviseur.

## 4. La lecture

1. **Lecture** interroge `https://vidzy.org/api/{id}`, sans jamais envoyer le jeton TMDB.
2. Si le titre est disponible, sa page s'ouvre dans une `WebView` : pas de fenêtres surgissantes, pas de
   lien vers d'autres applications, et un filtre des hôtes publicitaires connus (`assets/blocklist.txt`).
3. Un script injecté dans chaque cadre de la page trouve l'élément `<video>` et échange avec l'application.
   Les commandes, la reprise, la progression et l'épisode suivant sont entièrement natifs.

Limites à connaître :

- **La langue ne se choisit pas** : aucun paramètre n'est documenté pour cela. Les badges VF ou VOSTFR sont
  informatifs, et les menus « Audio » et « Sous-titres » du lecteur web ne sont pas accessibles à la télécommande.
- Le lecteur web affiche aussi ses propres commandes pendant quelques secondes, derrière les nôtres.
- Sans une version récente d'« Android System WebView » (possible sur Android TV 9), le **mode simplifié**
  s'active : pas de barre de progression, de reprise ni d'épisode suivant. Mettez ce composant à jour.

### Télécommande

| Touche | Commandes masquées | Commandes visibles |
|---|---|---|
| OK | Lecture/pause et affichage | Active le bouton sélectionné |
| Lecture/pause (touches média) | Agit directement | Agit directement |
| Gauche / Droite | Recule / avance de 10 s, puis 30 s, puis 60 s si répété | Change de bouton ; sur la barre : recule / avance |
| Haut | Affiche, sélection sur la barre | Sélection vers le haut |
| Bas | Affiche, sélection sur Lecture/Pause | Sélection vers le bas |
| Retour rapide / Avance rapide | -30 s / +30 s | -30 s / +30 s |
| Suivant | Épisode suivant | Épisode suivant |
| Précédent | Revient à 0 après 10 s de lecture, sinon épisode précédent | Idem |
| 0 à 9 | Va à 0 %, 10 %… 90 % de la durée | Idem |
| Menu | Vitesse de lecture | Vitesse de lecture |
| Retour | Demande confirmation ; un second appui en moins de 2 s quitte | Masque les commandes |

Le volume reste géré par le système. Le bouton **Son** coupe uniquement le son de la vidéo.

### Page de test (versions `debug`)

La page de Vidzy peut changer à tout moment : une page locale reproduit sa structure (la vidéo dans un
cadre imbriqué). Sur l'écran **À propos**, appuyez rapidement sur OK :

| Appuis | Résultat |
|---|---|
| 5 | Page de test : commandes, progression, vitesse, reprise |
| 7 | Page sans vidéo : « La source ne répond pas. » au bout de 25 s |
| 9 | Page de test en mode simplifié |

Un seul appui sur OK garde son effet habituel (**Retour**), avec un léger délai.

### Si la page de la source change

Le script cherche n'importe quel élément `<video>`, sans dépendre de la structure de la page. En cas de
problème, regardez ce qu'il rapporte :

```bash
adb logcat -s CodexBridge
```

- `A frame is ready` : le script tourne dans ce cadre.
- `Active video frame set` : une vidéo d'au moins 60 s a été trouvée.
- `Dropped an invalid message` : un message ne respecte pas le protocole.

Sans `Active video frame set`, la vidéo n'est plus dans un `<video>` accessible, par exemple dans un
*shadow root* fermé, ou la page attend une action. Si des publicités gênent, ajoutez leurs hôtes à
`app/src/main/assets/blocklist.txt`, sans jamais y mettre l'hôte qui sert la vidéo.

### Si la lecture saccade

Le lecteur web de Vidzy décode le flux en JavaScript, ce qui est lourd pour un boîtier TV. Deux pistes :

1. **Mode TV.** Ajoutez `WEB_PLAYER_TV_MODE=true` à `local.properties`, recompilez et réinstallez. L'application
   se déclare alors comme téléviseur, et la page laisse le WebView lire le flux lui-même, sans passer par
   JavaScript. Sur l'émulateur, le résultat n'a pas été concluant : comparez sur votre boîtier.
2. **Diagnostic** (versions `debug`). Toutes les 15 s, une ligne indique le mode de lecture (`js` ou `native`),
   les images perdues et les codes renvoyés par les segments vidéo :

   ```bash
   adb logcat | grep -E "CodexDiag|Refused a resource"
   ```

   Beaucoup d'images perdues signalent un boîtier trop sollicité. Des codes autres que `200` signalent un
   problème côté serveur. `Refused a resource` liste les ressources dont le certificat a été refusé : elles
   sont simplement ignorées, sauf si c'est la page de lecture elle-même.

### Changer de fournisseur

Implémentez `StreamSourceResolver`
(`app/src/main/java/dev/codexsoft/codexcine/data/playback/StreamSourceResolver.kt`) et enregistrez la classe
dans `di/AppContainer.kt`. Elle retourne `StreamSource.Direct` pour un flux lu par Media3, ou
`StreamSource.WebEmbed` pour une page web. Dans ce second cas, ajoutez aussi son hôte à
`HardenedWebViewFactory` : toute autre page principale est refusée.

## 5. Identité visuelle

Le logo, l'icône, l'icône de démarrage et la bannière TV sont générés à partir d'une seule géométrie :

```bash
pip install pillow fonttools
python3 tools/make_brand_assets.py
```

Modifiez le script, pas les fichiers qu'il produit. Les polices Space Grotesk et Inter sont incluses dans
l'application (licence SIL OFL, voir `app/src/main/assets/licenses`).

## 6. Commandes utiles

```bash
./gradlew :app:assembleDebug        # APK de débogage
./gradlew :app:testDebugUnitTest    # tests unitaires
./gradlew :app:lintDebug            # analyse statique
./gradlew :app:assembleRelease      # APK de production minifié par R8
```

### Signer la version de production

Ajoutez ces quatre lignes à `local.properties` (ignoré par Git) :

```properties
RELEASE_STORE_FILE=~/keystores/codexcine/codexcine.jks
RELEASE_STORE_PASSWORD=…
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=…
```

`assembleRelease` produit alors `app-release.apk`, signé et installable. Sans ces lignes, il produit
`app-release-unsigned.apk`, qui ne peut pas être installé tel quel. Gardez une copie du fichier de clés
ailleurs : sans lui, les mises à jour ne s'installeront plus par-dessus l'application.

Les choix techniques sont documentés (en anglais) dans [`DECISIONS.md`](DECISIONS.md).

## Attribution

Codex Ciné utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB.
