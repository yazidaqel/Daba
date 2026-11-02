# Charte graphique Daba — palette Zellige

Décidée le 2 novembre 2026. Source unique des couleurs pour toutes les apps (Flutter et web). Typographie et icônes : voir `CLAUDE.md`.

## Jetons

| Jeton | Clair | Sombre | Usage |
|---|---|---|---|
| `primary` | `#0F5E59` | `#4FB3A9` | Actions principales, navigation, liens, info |
| `primary-hover` | `#0B4A46` | `#6CC4BB` | Survol / appui |
| `primary-soft` | `#E6F2F1` | `#123B38` | Fond léger d'élément sélectionné |
| `on-primary` | `#FFFFFF` | `#062B29` | Texte sur `primary` |
| `promo` | `#E0A526` | `#EFC764` | **Promotions uniquement** : badge, fil promo, ligne promo d'un BL |
| `promo-soft` | `#FBEFD3` | `#3A2C0A` | Fond d'une carte promo |
| `on-promo` | `#3D2A02` | `#3D2A02` | Texte sur `promo` |
| `background` | `#F6F1E7` | `#121A19` | Fond de page (sable) |
| `surface` | `#FFFDF8` | `#1A2423` | Cartes, feuilles, champs |
| `border` | `#E4DCCB` | `#2C3A38` | Bordures et séparateurs |
| `text` | `#1D2B2A` | `#ECE7DC` | Texte principal (encre) |
| `text-muted` | `#6B6559` | `#A39D90` | Texte secondaire |
| `success` | `#2E7D32` | `#6BBF6F` | Payé, en stock, livré |
| `success-soft` | `#E6F2E6` | `#16301A` | |
| `warning` | `#B45309` | `#F0A060` | Plafond proche, péremption proche |
| `warning-soft` | `#FDEBD8` | `#3A2410` | |
| `danger` | `#B42318` | `#F2867A` | Impayé, rupture, plafond dépassé, erreur |
| `danger-soft` | `#FDECEA` | `#3A1714` | |

Tous les couples texte / fond ci-dessus atteignent au moins WCAG AA (4,5:1) ; `on-primary` sur `primary` dépasse 7:1 pour la lecture en plein soleil.

## Règles

- Le safran (`promo`) est réservé aux promotions. Jamais pour un avertissement : c'est `warning`.
- Un état (succès, avertissement, danger) s'affiche toujours avec une icône et un libellé, jamais par la couleur seule.
- Un seul bouton `primary` plein par écran ; les autres actions sont en contour ou en texte.
- Pas de couleur codée en dur dans les écrans : uniquement ces jetons (Flutter : `ColorScheme` + `ThemeExtension` ; web : variables CSS reliées au thème Tailwind / shadcn).
- Mode sombre suivi du réglage système.
