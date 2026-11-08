# Spell Engine Expansion_LD

Spell Engine 1.10.9 additions for the Fallen Eden skill tree (Minecraft 1.21.1, Fabric).
Feature list and reasoning: kept with the Fallen Eden skill-tree design notes (not in this repo).

Needs Spell Engine (`>=1.10.9`) and Fabric API on both the server and the client. The client part
draws the charge count on the spell hotbar and applies the same cast-time maths to tooltips and the
cast bar; without it the server still works, the client just shows plain cooldowns.

## 1. Spell charges

A spell holds N charges. Each cast spends one, and the spell can be cast while any charge is left.
Charges come back **one at a time**: a single timer runs while any charge is missing; when it ends one
charge returns and the timer restarts if more are missing.

### Per-spell settings: `data/<any namespace>/spell_charges/<spell namespace>/<spell path>.json`

```json
{
  "charges": 2,
  "recharge": 8.0,
  "recast_lock": 0.5
}
```

| Field | Meaning | Default |
|---|---|---|
| `charges` | casts the spell holds (at least 1) | required |
| `recharge` | seconds one charge takes to come back | the spell's `cost.cooldown.duration` |
| `recast_lock` | seconds between two casts, so a full stack can't go out in one tick | `0.5` |
| `spell` | optional, overrides the spell id taken from the file path | from the path |

Path to spell id: `data/falleneden/spell_charges/archers_expansion/explosive_barrel.json` →
`archers_expansion:explosive_barrel`. A file with no sub-folder (`data/wizards/spell_charges/frost_spikes.json`)
means `wizards:frost_spikes`.

The recharge is treated like the cooldown: `cooldown_duration_deduct` modifiers shorten it, and haste
divides it when the spell's cooldown is `haste_affected` (and Spell Engine's `haste_affects_cooldown` is on).
Spells with a `proportional` cooldown scale the recharge by the channel ratio of the cast that started it.

### Tree nodes: `charges_add` on a modifier

A MODIFIER spell's `modifiers[]` entry can carry `charges_add` next to Spell Engine's own fields:

```json
{
  "type": "MODIFIER",
  "modifiers": [
    { "spell_pattern": "archers_expansion:explosive_barrel", "charges_add": 1 }
  ]
}
```

`charges_add` is summed over every matching modifier. A spell with no `spell_charges` file becomes a
charged spell with `1 + charges_add` charges (recharge = its cooldown, lock 0.5 s). A raised maximum
hands the new charge out at once; a lowered one takes it back.

### Behaviour notes

- HUD: the charge count sits in the icon's bottom-right corner (white, red at 0). The cooldown sweep
  shows the recharge only at 0 charges, and a short sweep for the recast lock after each cast.
- `COOLDOWN` impacts (`actives` / `passives` with `duration_add`, `duration_multiplier`) move the
  recharge timer; a reset (`duration_multiplier: 0`) gives back **one** charge (LeDok, 2026-10-18).
- Cooldown groups (`cost.cooldown.group`, the pack's `weapon` group) keep their one-at-a-time rule
  (LeDok, 2026-10-18): a running group cooldown blocks the charged spell even with charges left, and
  each cast of the charged spell puts its group on cooldown for the `recast_lock` only. Example: Frostbolt
  with 2 charges and Frost Shard share `weapon`. Frost Shard's cooldown blocks Frostbolt; a Frostbolt cast
  blocks Frost Shard for 0.5 s, then Frostbolt's second charge can follow at once.
- `attempt_duration` and the instant-cast global cooldown become a recast lock (no charge spent).
- The hosting item's cooldown lock (`spell_item_cooldown_lock`) is skipped for charged spells.
- State is saved in the player's NBT (`seeld_spell_charges`) and re-sent on join, dimension change
  and `/reload`. `/spell_cooldown reset <players> <spell>` and `/spell_cooldown clear <players>` refill charges too. `/seeld charges <spell>` and `/seeld modifiers <spell>` (op 2) print what this mod sees for you.

## 2. Recasts (LoL-style follow-up casts)

A root spell declares follow-up stages. Casting the root opens a window; while it is open the root's
hotbar slot shows the stage's icon and the same key casts the stage (its own cast time, targeting,
delivery, animation and cost). After the last stage, or when the window runs out, the slot is the root again.
Decided with LeDok 2026-10-18: stages are their own spells (tree modifiers for the root don't reach them,
nodes target the stage ids), and casting other spells does not close the window unless the file says so.

### Per-root settings: `data/<any namespace>/spell_recast/<spell namespace>/<spell path>.json`

```json
{
  "stages": ["falleneden:leap_slam"],
  "window": 4.0,
  "cooldown_start": "END",
  "cancel_on_other_cast": false
}
```

| Field | Meaning | Default |
|---|---|---|
| `stages` | follow-up spell ids in order; `"recast": "ns:id"` is shorthand for one stage | required |
| `window` | seconds to press again, restarted after each stage | `4.0` |
| `cooldown_start` | `FIRST`: the root's cooldown runs from its own cast. `END`: it starts when the last stage is cast or the window expires | `END` |
| `cancel_on_other_cast` | casting a spell outside the chain closes the window (and with `END` starts the cooldown) | `false` |
| `spell` | optional, overrides the root id taken from the path | from the path |

Stage spells are normal ACTIVE spells in `data/<ns>/spell/`. They must not be granted by a book or a
node (then they would get their own slot too); the root's slot is their only way in. A stage's own
cooldown and cost apply normally.

### Behaviour notes

- The window opens only when the root actually fired (delivery succeeded and cost was taken).
- The HUD draws a gold line under the stage icon that drains with the window.
- With `cooldown_start: END` and an unused window, the root waits `window` + its cooldown.
- Windows are not saved: a relog drops them without starting the root's cooldown.
- `/seeld recast <spell>` prints a root's settings and open window, or which root a stage belongs to.
  The server log warns at start-up about stage ids that don't exist.

## 3. Cast-time modifier fields

Also on a modifier entry, summed over matching modifiers, STANDARD casts only (not channels or charges):

```json
{ "spell_pattern": "wizards:frost_shield", "cast_duration_deduct": 0.5, "cast_duration_multiply": -0.25 }
```

Final cast time = `(duration - sum(cast_duration_deduct)) * (1 + sum(cast_duration_multiply))`, never
below 0, applied before haste. The cast animation speeds up by the same ratio. A cast brought to 0
becomes an instant cast.

## 4. Scroll refund on unbinding (LeDok's issue #2, 2026-11-06)

Spells on Fallen Eden come only from loot scrolls (`learn` was removed from every spell), and binding
a scroll consumes it. Spell Engine 1.10.9 lists a bound spell in the table only while a scroll of it sits
in the slot, so unbinding needed a second scroll and the first one was lost.

With this mod (server side is enough for this feature):
- The table always lists the spells the book holds. Clicking one opens Spell Engine's own unbind dialog.
- Unbinding removes the spell and puts **one scroll of it** in the player's inventory (the same item as a
  loot scroll: single-spell container, rarity, pool model), dropped at the feet if the inventory is full.
  No second scroll is needed or consumed. Levels and lapis paid for binding are not refunded.
- No loop: a bind consumes exactly one scroll and an unbind returns exactly one.
- A spell bound with lapis (a `learn` offer, no scroll in the slot) is marked on the book
  (`custom_data.seeld_no_refund`) and gives no scroll back. Spells bound before this mod existed carry
  no mark and are refunded. Loot books that come with spells pre-bound are refunded too.
- Each unbind writes one `[Spells] <player> unbound <spell> from <book>: ...` line to the server log.
- Grindstone: untouched (LeDok, 2026-11-06). Vanilla grindstones don't take spell books; Spell Engine lets
  one turn a scroll into paper, and that stays as it is.

### Config: `config/spell_engine_expansion_ld.json` (written with defaults on first start)

```json
{
  "unbind_refunds_scroll": true,
  "mark_lapis_bound_spells": true
}
```

Everything else in this mod is data driven and inert by default: charges and recasts need
`spell_charges` / `spell_recast` files, the modifier fields need spell JSON that uses them.

## 5. Hotbar: cooldown numbers and ready border (LeDok's request, 2026-11-06)

Client side only, drawn over Spell Engine's spell hotbar after its own render pass:
- While a spell is on cooldown, the time left is written on its icon, centred, white with a shadow:
  whole seconds from 10 s up ("13"), one decimal below ("4.2"). Values are rounded up, so the number
  never claims less time than is left. The time comes from the same timers as the cooldown sweep
  (own cooldown, cooldown group, charge recast lock, recharge at 0 charges), so both always agree.
- While a spell is ready, its icon gets a gold frame (`#FFCC33`) in place of the grey slot frame.
  On cooldown the frame stays grey.
- Charge counts and the recast window line (sections 1 and 2) are unchanged. On a charged spell the
  number sits a little higher so it does not cross the count.
- Item-use slots (the use-key slot showing an item) are left as they are.
- The hotbar widget is the same in every Spell Engine input mode (use key, number keys, scroll), so
  the overlay shows in all of them. The HUD config screen's mock hotbar is not touched.

### Client config: `config/spell_engine_expansion_ld-client.json` (written with defaults on first start)

```json
{
  "cooldown_numbers": true,
  "cooldown_decimals_below": 10.0,
  "ready_border": true,
  "ready_border_color": "#FFCC33"
}
```

Edit and restart the client. `cooldown_decimals_below` is in seconds (0 = always whole seconds).

### Testing aid: `/seeld cooldown <spell> <seconds>` (op level 2)

Puts the caller's spell on cooldown without casting it (0 clears it). A charged spell gets its recast
lock instead, like after a cast. Example: `/seeld cooldown wizards:fire_bolt 13.5`.

## Building

```
./gradlew build          # build/libs/SpellEngineExpansion_LD-<version>.jar
./gradlew runServer      # dev server in run-server/ (Spell Engine and its libraries come from Maven)
./gradlew runClient -PseeldQuickPlay=localhost:25565   # dev client that joins the dev server at once
```

For HUD screenshots the client honours `SEELD_SCREENSHOT_AFTER=<ticks>` in its environment: that many
ticks after joining it saves `run/screenshots/seeld-hud.png` and quits. `run/resourcepacks/seeld_test`
holds icons for the test spells (borrowed from Spell Engine's `rpg_series` set, dev only).

Mixins target Spell Engine classes (also `SpellContainerSource` and the client `SpellHotbar` for recasts); Loom rewrites the Minecraft types in their selectors to intermediary
names at build time, so no refmap is involved. Every Spell Engine update needs a re-check of
`SpellCooldownManager`, `SpellCooldowns`, `SpellParameters`, `Spell.Modifier` and `HudRenderHelper`.

Spell JSON (including the modifier fields of section 3) is a datapack registry: it loads once at server start and
`/reload` does not re-read it. Only the `spell_charges` and `spell_recast` folders reload live. The "Spell data"
log line after a `/reload` therefore still counts the spells from the last start.
