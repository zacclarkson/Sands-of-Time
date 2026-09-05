# Sands of Time resource pack

A **slim, overrides-only** pack — it ships *only* the SoT custom assets plus `pack.mcmeta` / `pack.png`,
not a vanilla asset dump. Minecraft falls back to its built-in assets for everything not listed here.

```
resourcepack/
  pack.mcmeta                                  # pack_format 88 (Paper 26.2) + supported_formats range
  pack.png
  assets/
    minecraft/items/
      gold_nugget.json                         # override: range_dispatch, coin CMD 1001/1002/1003 -> coin model
      tripwire_hook.json                       # override: range_dispatch, vault-key CMD 2011-2014 -> key models
    sot/
      models/item/  coin_stack_small.json, key_{red,gold,green,blue}.json
      textures/item/ coin_small.png, key_{red,gold,green,blue}.png
  sot-source-textures/                         # art master, NOT shipped (see its README)
```

## How it maps to code

CustomModelData is set by the plugin and matched by the `assets/minecraft/items/*.json` overrides
(the 1.21.4+ item-model format: a `minecraft:range_dispatch` on `minecraft:custom_model_data`):

| Item | Base material | CMD | Set in | Pack override |
| --- | --- | --- | --- | --- |
| Coin (small/med/large) | `GOLD_NUGGET` | 1001 / 1002 / 1003 | `CoinStack`, `ToolListener` | `items/gold_nugget.json` |
| Vault key (BLUE/RED/GREEN/GOLD) | `TRIPWIRE_HOOK` | 2011 / 2012 / 2013 / 2014 | `ItemManager.getCustomModelDataForKey` | `items/tripwire_hook.json` |

Keep the CMD ids in `ItemManager` in lockstep with the thresholds in `tripwire_hook.json`.

## Build & serve

`scripts/build-resourcepack.sh` zips this dir (excluding `sot-source-textures/` and READMEs, so
`pack.mcmeta` is at the zip root) into `target/sot-resourcepack.zip`. The dev server serves it via the
`pack` nginx sidecar; the resource-pack CD (`.github/workflows/resourcepack-deploy.yml`) rebuilds and
hot-swaps it on any `resourcepack/**` change. See `deploy/sot-test/README.md`.
