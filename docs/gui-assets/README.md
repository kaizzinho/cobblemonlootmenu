# Cobblemon loot selection GUI — asset set

All sprites are 1× Minecraft source pixels, PNG with alpha, drawn on the Cobblemon
grayscale ramp sampled from the mod's own textures.

Palette
- #2f2f2f outline / well fill
- #4b4b4b inner shadow
- #676767 bezel band (sides, bottom)
- #8d8d8d top band, side rims
- #c6c6c6 top rim highlight
- #ffffff button top rim
- red family  #752626 #a03535 #cc5556 #e6aaaa  (danger)
- green family #26752f #35a03f #56cc5f #aae6ad (accept / selected)

Frame recipe (matches battle_log.png / party_slot.png)
1px #2f2f2f outline -> 1px rim (#c6c6c6 top, #8d8d8d sides/bottom)
-> 3px bezel band (#8d8d8d top, #676767 sides/bottom) -> #2f2f2f well.
1px chamfered corners (corner pixel transparent, diagonal pixel #2f2f2f).

## Layout (offsets relative to the panel origin, source px)

| element              | x, y     | size    |
|----------------------|----------|---------|
| panel                | 0, 0     | 276×180 |
| title bar            | 5, 5     | 266×18  |
| ball icon            | 9, 6     | 16×16   |
| title text baseline  | 29, 10   | —       |
| close X              | 262, 9   | 9×9     |
| grid well            | 8, 28    | 126×118 |
| slot grid origin     | 12, 32   | 22px slots, 2px gaps (5×4 = 118×94) |
| count strip          | 12, 130  | 118×12  |
| detail well          | 140, 28  | 128×118 |
| item frame           | 180, 52  | 48×48   |
| divider (upper)      | 148, 43  | 112×2   |
| divider (lower)      | 148, 107 | 112×2   |
| button row origin    | 7, 152   | 4 × 64×20, 2px gaps (262×20) |

Button row x positions: 7 (Drop All, danger), 73 (Take Selected, neutral),
139 (Take All, accept), 205 (Close, neutral).

## Files

batch1/ panel, title bar, grid well, detail well
batch2/ slot states (normal, hover, selected, taken, disabled) + optional scrollbar
        track/grip for loot pools larger than 20 entries
batch3/ buttons (neutral, accept, danger, each + hover; disabled) 64×20,
        item frame 48×48, qty badge 28×12, divider 112×2
batch4/ ball icon 16×16, close X 9×9 (+hover), check 9×9, pointer 8×12,
        "none" glyph 12×12, item placeholder 16×16 (mockup only — not for shipping)

mockup_full_gui.png — everything mounted at 4×, with stand-in text/items.

Ship path suggestion: assets/<modid>/textures/gui/loot/<file>.png
