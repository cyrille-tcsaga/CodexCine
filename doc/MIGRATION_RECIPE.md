# Recette d'une story de migration

À copier dans la description de chaque PR ou commit de story, et à cocher.

## Porte automatique

- [ ] `tools/gate.sh` : tests unitaires, build debug et lint au vert.
- [ ] `tools/smoke.sh` : aucun `FATAL EXCEPTION`.

## Parcours manuel sur l'émulateur `Television_1080p` (jamais un appareil personnel)

Ouvrir un écran sans naviguer : `adb -s emulator-5554 shell am start -n dev.codexsoft.codexcine/.ui.compose.DebugLaunchActivity --es screen <ecran>` (voir `tools/smoke.sh` pour des exemples).

- [ ] Haut, bas, gauche, droite : le focus ne saute pas et ne se perd pas.
- [ ] OK, appui long OK, Back : mêmes effets que Leanback.
- [ ] Retour depuis un écran ouvert : le focus revient où il était.
- [ ] Aucune exception ni `StrictMode` dans `logcat`.

## Parité (voir doc/USER_STORIES_MIGRATION_COMPOSE_TV.md §2.3)

1. Carte : coins arrondis, anneau ivoire et zoom à la sélection.
2. Continuer à regarder : barre de progression, reste à voir, menu d'appui long.
3. Accueil : rangées sans vol de focus, billboard et fond dynamique.
4. Barre latérale : pages, icônes, logo, recherche.
5. Rangées : « Voir tout », « À propos ».
6. Détails : fond, métadonnées, badges, boutons, rangées Saga, Saisons, Casting, Recommandations.
7. Saison : épisodes vus, menu d'appui long, indication, trois cartes par ligne.
8. Grilles : 5 colonnes, pagination, filtres.
9. Recherche : saisie, voix, externe.
10. Erreur : message, Réessayer (absent si jeton invalide).
11. Lecteur : panneaux, reprise, compte à rebours, télécommande.
12. « Play next » et liens profonds inchangés.
13. Back : même comportement partout.

## Mesures (si l'écran touche l'accueil ou le défilement)

- [ ] Démarrage à froid, images lentes et mémoire comparés aux références de `DECISIONS.md` (US-03).
