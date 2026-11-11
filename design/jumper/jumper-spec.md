# Jumper design spec: anchor block, upgrades, icon (design session, 2026-11-10)

For the coding session. Textures and icon are final and are already in the repo under the names the mod uses (section 2). Design copies live in `design/jumper/` (store icons, previews, and `jumper_kit.zip`, the original kit with its own placeholder names).

## 1. The owner's decisions
- **Direction B, "Pad":** a machined steel block with a glowing round pad on top, a glow strip and vents on the sides. Chosen over a dark-stone "Rift" look and a deepslate/brass "Compass" look.
- **The block shows its tier.** Four lights on each side face; lit count = tier. The glow color also changes with tier.
- **Tier colors:** T1 `#CFE6EE` (pale, base block), T2 `#38D3E6` (cyan), T3 `#A36BFF` (violet), **T4 = animated hue cycle** (rainbow, saturation 0.62, value 1.0), inspired by the color-shifting look of AE2 controllers but original art.
- **Upgrades** (T2–T4) are core cartridges: steel body, glass window in the tier color, LEDs on the front (2/3/4 = tier, so they read without color), gold pins.
- **Icon, no wordmark.** The mod is represented by a square icon: a side-view figure leaping right, with its back half breaking into cyan pixels ("blink"). The platforms show the mod name as text beside the icon, so the icon carries no lettering. The earlier wordmark explorations (figure over "JUMPER", speed lines, sheared lettering) are retired.

## 2. Files in the repo (`src/main/resources/assets/jumper/`)
| File | Size | Status |
|---|---|---|
| `textures/block/jumper_anchor_top.png`, `jumper_anchor_side.png` | 16×16 | tier 1; replaced the placeholders, used now |
| `textures/block/jumper_anchor_bottom.png` | 16×16 | new, shared by all tiers, used now |
| `textures/block/jumper_anchor_top_t2.png`, `_t3.png` | 16×16 | new, waiting for the tier wiring (section 3) |
| `textures/block/jumper_anchor_side_t2.png`, `_t3.png` | 16×16 | new, waiting for the tier wiring |
| `textures/block/jumper_anchor_top_t4.png`, `jumper_anchor_side_t4.png` + `.png.mcmeta` | 16×256 | new, 16-frame strips, waiting for the tier wiring |
| `textures/item/anchor_upgrade_2.png`, `anchor_upgrade_3.png` | 16×16 | replaced the placeholders, used now |
| `textures/item/anchor_upgrade_4.png` + `.png.mcmeta` | 16×256 | replaced the placeholder, animated, used now |
| `models/block/jumper_anchor.json` | | now `cube_bottom_top` (top / bottom / side; particle = side) |
| `models/block/jumper_anchor_t2.json` … `_t4.json` | | new, same layout with the tier textures |
| `src/main/resources/logo.png` | 192×192 | in-game mod list logo |

- Block textures are fully opaque. Item textures use hard alpha only (0 or 255).
- **T4 animation:** `{"animation": {"frametime": 5, "interpolate": true}}` = 16 × 5 ticks = 4 s loop. All three T4 textures share this timing, so block and upgrade stay in sync. Checked: the loop seam step equals every other frame step, and the upgrade's silhouette is identical in every frame.
- The repo's `scripts/anchor_textures.py` and `scripts/upgrade_item_textures.py` drew the old placeholders. Re-running them would overwrite these textures.

## 3. Still to wire (code)
- The tier lives in the anchor's saved data (`anchor.tier()`), not in the blockstate, so the block can't show it yet. Simplest route: add an `IntegerProperty` `tier` (1–4) to the anchor block, set it whenever the anchor's tier changes, and point the blockstate variants at the models: `tier=1` → `jumper:block/jumper_anchor`, `tier=2..4` → `jumper:block/jumper_anchor_t2..t4`.
- The block item keeps pointing at the tier 1 model.
- **Logo (done):** `src/main/templates/META-INF/neoforge.mods.toml` now has `logoFile="logo.png"` and `logoBlur=false` under `[[mods]]`, so the mod list draws it with nearest-neighbor filtering instead of blurring it.

## 4. Icon
- Drawn on a 24×24 pixel grid, dark tile background `#15171C` with a 1 px lighter top-left edge.
- `design/jumper/store/jumper_icon_384.png` for Modrinth / CurseForge uploads (Modrinth shows icons at 96 px; CurseForge resizes to 400 px). `jumper_icon_96.png` is a small copy.
- Keep any rift/portal imagery irregular (a jagged tear), never a smooth oval, so it can't be mistaken for Valve's Portal.

## 5. Open risk
- The name "Jumper" is already used on CurseForge (an auto-jump Fabric mod at the `jumper` slug), by a Bukkit teleport plugin ("Jumper - Easy Teleportation") and by a Forge teleport mod ("Jumper: Awakening"). Fine for a personal mod; if publishing, use a more specific name/mod ID and rename the `assets/jumper/` folder to match. Don't use the 2008 film's logo or stills on any mod page.
