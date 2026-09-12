# Vinyl — Figma Design Assets

Hi-fi design exports for the COMP90018 Vinyl app. Reference material only — nothing
here is compiled into the app. Assets that the app actually ships live in
`app/src/main/res/drawable/` and `app/src/main/res/font/`.

## Layout

| Folder | Contents |
| --- | --- |
| `screens/` | Full-screen mockups, 412×915 (Android phone portrait) |
| `components/` | Reusable UI pieces pulled out of the screens |
| `brand/` | Logo lockups and the full Figma board overview |
| `icons/` | App icons as SVG plus 1x/2x/3x PNG (24/48/72 px) |
| `artwork/` | Album covers and record art used as sample content |

## Screens

| File | Screen |
| --- | --- |
| `home-today.png` | Home — "Today" turntable view, the daily-cards entry point |
| `home-record-room.png` | Home — record room with shelves (dark) |
| `home-record-room-light.png` | Home — record room, light theme |
| `collection-shelves.png` | My Collections — grouped shelves with "See all" rows |
| `collection-grid.png` | Your Collection — flat 2-up grid with filter pills |
| `create-music-card.png` | Write a Music Card — rich-text editor and card styles |
| `receive-music-card.png` | "A new Music Card just arrived!" — tap-to-open envelope |
| `music-card-detail.png` | Card detail — cover, 30s preview, message, location/weather |
| `compass-context.png` | Compass — bearing and distance to the sender |
| `settings-profile.png` | Settings / profile |

## Palette

Sampled from the design-system swatches. Mirrored in
`app/src/main/java/com/example/vinyl/ui/theme/Color.kt` as `VinylColors`.

| Swatch | Hex | `VinylColors` |
| --- | --- | --- |
| Charcoal | `#1E1E1E` | `Charcoal` |
| Surface | `#242B2B` | `Surface` |
| Cream | `#F4F0EA` | `Cream` |
| Teal (accent) | `#77EDE5` | `Teal` |
| Rust | `#6B3B32` | `Rust` |

`Color.kt` carries additional shades derived from the screen exports (shelf
gradients, pills, dividers) that were never standalone swatches in Figma.

## Type scale

Poppins throughout; the TTFs are in `app/src/main/res/font/`.

| Role | Style | Size |
| --- | --- | --- |
| Heading 1 | Poppins Medium | 20 px |
| Heading 2 (title) | Poppins Regular | 16 px |
| Body | Poppins Regular | 14 px |
| Button | Poppins Medium | 14 px |
| Caption | Poppins Light | 11 px |

## Notes

- `brand/figma-board-overview.png` is a 5526×3478 export of the entire Figma
  board — useful for seeing how the pieces relate, too large to read inline.
- `brand/logo-dark-ink.png` is the light-theme logo (dark ink);
  `brand/logo-cyan.png` is the dark-theme variant.
- Generic Android UI-kit material (status bars, nav bars, G-Board, empty device
  frames, Wi-Fi/Bluetooth/alarm glyphs) was removed — it came with the Figma
  Android kit and is not part of the Vinyl design.
