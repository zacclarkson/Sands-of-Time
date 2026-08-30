# Sands of Time — source textures (not yet wired up)

Hand-made 16×16 source art for the four **vault colours** (`RED`, `GOLD`, `GREEN`, `BLUE` — see
`VaultColor.java`). These are the raw texture files only. Nothing in the plugin or the resource pack
references them yet — they are checked in so the art is version-controlled and ready to wire up.

```
sot-source-textures/
  keys/              key_{red,gold,green,blue}.png       # vault keys   (base item: TRIPWIRE_HOOK)
  keyholes/          keyhole_{red,gold,green,blue}.png   # vault keyholes (base: black glazed terracotta)
  vault-doors/       vault_door_{red,gold,green,blue}.png# vault doors  (base: black glazed terracotta)
  branch-signifiers/ branch_{red,gold,green,blue}.png    # branch/vault-exit wall markers (base: sandstone)
```

## Status / outstanding work

These textures are **not** served to clients and are **not** mapped to any model or custom model
data. Two things are needed before they show up in game (tracked as issues):

1. **Delivery** — the dev server does not serve any resource pack, and `resourcepack/` is currently a
   full vanilla asset dump rather than a slim overrides-only pack. See issue #89.
2. **Branch-colour signifier feature** — there is no in-game feature (no placeholder marker, no
   generation/runtime logic) that signifies which vault colour a branch leads to, so the
   `branch_*.png` textures have nowhere to attach. See issue #90.

Keys can be wired via the currently commented-out CustomModelData hooks in `ItemManager.java`.
