# Sands of Time — source textures (art master)

Hand-made 16×16 source art for the four **vault colours** (`RED`, `GOLD`, `GREEN`, `BLUE` — see
`VaultColor.java`). This directory is the **art master**: the working PNGs live here and are **not**
shipped in the served pack (`scripts/build-resourcepack.sh` excludes `sot-source-textures/`). To use
a texture in game, copy it into `resourcepack/assets/sot/textures/item/` and wire it (see below).

```
sot-source-textures/
  keys/              key_{red,gold,green,blue}.png       # vault keys   (base item: TRIPWIRE_HOOK)
  keyholes/          keyhole_{red,gold,green,blue}.png   # vault keyholes (base: black glazed terracotta)
  vault-doors/       vault_door_{red,gold,green,blue}.png# vault doors  (base: black glazed terracotta)
  branch-signifiers/ branch_{red,gold,green,blue}.png    # branch/vault-exit wall markers (base: sandstone)
```

## Status

**Keys and coins are wired and served (issue #89 done).** The dev server now serves a slim,
overrides-only pack via the `pack` sidecar (`deploy/sot-test/compose.yml`), rebuilt and hot-swapped
by `.github/workflows/resourcepack-deploy.yml` on any `resourcepack/**` change.

- **Vault keys** — `keys/key_*.png` are copied to `assets/sot/textures/item/key_*.png`, modelled by
  `assets/sot/models/item/key_*.json`, and driven by CustomModelData ids `2011–2014` (see
  `ItemManager.getCustomModelDataForKey`) via the `items/tripwire_hook.json` range_dispatch override.
- **Coins** — the already-emitted CMD `1001/1002/1003` (from `CoinStack` / `ToolListener`) are mapped
  by `items/gold_nugget.json` to the `coin_stack_small` model. Only small-coin art exists today, so
  1002/1003 reuse it as a placeholder until medium/large art is added.

**Still not wired** — `keyholes/`, `vault-doors/`, and `branch-signifiers/` have no in-game attach
point yet: their names map to nothing, `VaultDoor` renders concrete/gold blocks (not the intended
black glazed terracotta), keyholes have no code representation, and branch signifiers depend on the
signifier feature (issue #90). These need **block-model overrides and/or new gameplay** — tracked
separately (issue #112, plus #90 for the branch signifier).
