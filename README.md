# Codex Ciné

Navigateur de films et de séries pour Android TV, basé sur l'API TMDB et l'interface Leanback.
Application à usage personnel, installée manuellement : elle n'est pas destinée à Google Play.

Appareils visés : Xiaomi TV Box S (Google TV 14, API 34) et Mi Box S (Android TV 9, API 28).

TMDB ne fournit que des métadonnées, pas de vidéos. Le bouton **Lecture** et le lecteur sont
entièrement fonctionnels, mais tant qu'aucune source n'est branchée, ils affichent
« Aucune source de lecture disponible pour ce titre. »

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

Après recompilation, le bouton **Lecture** diffuse une vidéo de test publique, quel que soit le titre.
Cette option ne s'applique qu'aux versions `debug` : elle est toujours désactivée en `release`.

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

## 4. Brancher une vraie source vidéo

Une seule interface suffit : `StreamSourceResolver`, dans
`app/src/main/java/dev/codexsoft/codexcine/data/playback/StreamSourceResolver.kt`.

```kotlin
class MyStreamResolver : StreamSourceResolver {
    override suspend fun resolve(request: PlaybackRequest): StreamSource? {
        // request contient mediaId, mediaType, title, seasonNumber et episodeNumber.
        // Retournez null si le titre n'est pas disponible.
        return StreamSource(
            uri = "https://exemple.org/video.m3u8",
            mimeType = MimeTypes.APPLICATION_M3U8,
            headers = mapOf("Authorization" to "Bearer ..."),
        )
    }
}
```

Enregistrez ensuite cette classe dans `di/AppContainer.kt`, à la place de `NoStreamSourceResolver` :

```kotlin
val streamSourceResolver: StreamSourceResolver by lazy { MyStreamResolver() }
```

Le lecteur, les contrôles, les touches de la télécommande et les écrans d'erreur fonctionnent déjà.

## 5. Commandes utiles

```bash
./gradlew :app:assembleDebug        # APK de débogage
./gradlew :app:testDebugUnitTest    # tests unitaires
./gradlew :app:lintDebug            # analyse statique
./gradlew :app:assembleRelease      # APK de production minifié par R8, non signé
```

`assembleRelease` produit un APK non signé, qui ne peut pas être installé tel quel. Pour un usage
personnel, `installDebug` suffit. Sinon, signez l'APK, par exemple depuis Android Studio avec
**Build > Generate Signed App Bundle or APK**.

Les choix techniques sont documentés (en anglais) dans [`DECISIONS.md`](DECISIONS.md).

## Attribution

Codex Ciné utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB.
