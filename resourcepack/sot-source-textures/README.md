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
  branch-signifiers/ branch_{red,gold,green,blue}.png    # branch/vault-exit wall markers (base: the vault-colour block, see below)
```

## Status

**Keys and coins are wired and served (issue #89 done).** The dev server serves a slim,
overrides-only pack via the `pack` sidecar (`deploy/sot-test/compose.yml`), rebuilt and hot-swapped
by `.github/workflows/resourcepack-deploy.yml` on any `resourcepack/**` change.

- **Vault keys** — `keys/key_*.png` are copied to `assets/sot/textures/item/key_*.png`, modelled by
  `assets/sot/models/item/key_*.json`, and driven by CustomModelData ids `2011–2014` (see
  `ItemManager.getCustomModelDataForKey`) via the `items/tripwire_hook.json` range_dispatch override.
- **Coins** — the already-emitted CMD `1001/1002/1003` (from `CoinStack` / `ToolListener`) are mapped
  by `items/gold_nugget.json` to the `coin_stack_small` model. Only small-coin art exists today, so
  1002/1003 reuse it as a placeholder until medium/large art is added.

**Branch signifiers have an attach point but no override yet.** `BRANCH_SIGNIFIER` markers place a
coloured block beside the exit whose branch they advertise (see
`DungeonGenerator.resolveBranchSignifiers`). The block written is `VaultColor.getConcreteMaterial()` —
`red_concrete`, `lime_concrete`, `blue_concrete` and `gold_block` — so each colour is a distinct block
the pack can override with the matching `branch_*.png`. Until that block override exists, players see
the plain coloured block, which already reads as the branch's colour.

**Still not wired** — `keyholes/` and `vault-doors/` have no in-game attach point: `VaultDoor` renders
concrete/gold blocks (not the intended black glazed terracotta) and keyholes have no code
representation. Those, plus the `branch_*.png` block overrides above, need **block-model overrides
and/or new gameplay** — tracked in issue #112.
