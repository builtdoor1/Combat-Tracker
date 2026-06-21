# Combat Tracker

A **client-side** [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.11** that detects and records **jump-reset** timing in PvP.

A *jump reset* is jumping at the exact moment another player hits you, which partially cancels the incoming knockback. Combat Tracker is **purely observational** — it never modifies movement, knockback, or any other game mechanic. It only measures *when* you jumped relative to *when* you were hit, and keeps statistics.

Because it records the full distribution of your timings (including standard deviation), it doubles as evidence that your jump resets come from human reflexes: a human shows natural spread, while an auto-reset cheat shows near-zero variance.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) (0.19.3 or newer).
2. Put the following in your `.minecraft/mods` folder:
   - `combat-tracker-1.21.11.jar` — from the [latest release](../../releases/latest)
   - [Fabric API](https://modrinth.com/mod/fabric-api) (`0.141.4+1.21.11`)
   - *(optional)* [Mod Menu](https://modrinth.com/mod/modmenu) — needed only for the in-game config screen
3. Launch Minecraft.

---

## How it works

Detection is fully client-side and observational:

- **Jump** (Event B): a mixin samples the player's upward velocity *before* physics runs each tick, and a jump is detected from the velocity **impulse** (delta-vy) — not from an `onGround` flip. This fires reliably even on the exact tick a hit lands.
- **Hit** (Event A): the mod watches `hurtTime` go from 0 to positive while the damage invulnerability (regen) timer is active **and** horizontal knockback exceeds a threshold. The knockback check filters out fall, fire and poison damage (which have no horizontal push) without needing any packet inspection.
- **Pairing & timing**: a hit opens a short **tick window**; the next jump inside it is scored. Timing uses `System.nanoTime()` with **one-way ping compensation**, so the delta approximates true server-side timing.

The signed **delta** (`jumpTime − hitTime`, in ms) is then classified:

| Delta | Result |
|-------|--------|
| Between LOWER and UPPER | ✅ **HIT** |
| Greater than UPPER | ❌ **MISS** (too late) |
| Less than LOWER (jumped before the hit) | ❌ **MISS** (too early) |

A hit with **no** following jump (the window simply expires) is *not* counted — only real attempts are recorded.

> Detection method adapted from the open-source [sombreror/JumpReset-mod](https://github.com/sombreror/JumpReset-mod).

---

## The HUD

A small overlay (top-left by default) shows:

| Line | Meaning |
|------|---------|
| **Hits** | Number of successful jump resets |
| **Misses** | Number of failed attempts |
| **Rate** | Success percentage |
| **Avg / SD** | Average delta and standard deviation (ms) across all attempts |
| **Last** | Result of your most recent attempt |

- **Toggle the HUD:** press **`J`** (rebindable under *Options → Controls → Miscellaneous*).
- **Move the HUD:** open the config screen → **Move HUD…** → drag the overlay anywhere on screen → **Done**.

---

## Chat messages

When **Chat messages** is enabled, every *attempt* prints a single line:

- `[Combat Tracker] Jump reset HIT! (+42ms)` — green
- `[Combat Tracker] Jump reset MISS - too late (+120ms)` — red
- `[Combat Tracker] Jump reset MISS - too early (-30ms)` — red

Ordinary jumps (not near a hit) never produce a message.

---

## Configuration

Open **Mod Menu → Combat Tracker → Config**. Everything is saved to `config/jump_reset_tracker/config.json`.

| Option | Default | What it does |
|--------|---------|--------------|
| **Success window LOWER** | `0 ms` | Lower edge of the success window. A delta below this is classed *too early*. |
| **Success window UPPER** | `80 ms` | Upper edge of the success window. A delta above this is classed *too late*. An attempt is a **HIT** only when the delta is between LOWER and UPPER. |
| **HUD** | `ON` | Show or hide the on-screen overlay (same as pressing **`J`**). |
| **Chat messages** | `ON` | Whether each attempt prints a HIT/MISS line in chat. |
| **Move HUD…** | — | Opens a screen where you drag the HUD to any position. |
| **Reset Stats** | — | Clears all recorded attempts and statistics. **Click twice** to confirm. |

> The success-window sliders range from **0–600 ms**, and must satisfy `LOWER ≤ UPPER` (clamped automatically when you close the config screen).

### Advanced detection tuning

These live in `config.json` (no GUI) and rarely need changing:

| Key | Default | What it does |
|-----|---------|--------------|
| `jumpDeltaThreshold` | `0.25` | Minimum upward velocity impulse to register a jump. |
| `knockbackThreshold` | `0.065` | Minimum horizontal speed after damage to treat it as a real combat hit. |
| `windowTicksGround` | `6` | Ticks after a grounded hit during which a jump still counts as an attempt. |
| `windowTicksAir` | `10` | Ticks after an airborne hit during which a jump still counts. |
| `pingCompFactor` | `0.5` | Fraction of round-trip ping treated as one-way latency for timing. Set `0` to disable ping compensation. |

---

## Clearing your stats

Your stats live in `config/jump_reset_tracker/stats.json`. To wipe them:

- **In-game:** config screen → **Reset Stats** → click again to confirm, **or**
- **Manually:** delete `config/jump_reset_tracker/stats.json` while the game is closed.

---

## File locations

```
.minecraft/config/jump_reset_tracker/
├── config.json   # timing window, HUD/chat toggles, HUD position
└── stats.json    # full attempt history + computed aggregates
```

---

## Building from source

```bash
./gradlew build
```

The remapped mod jar is written to `build/libs/combat-tracker-<modversion>+<mcversion>.jar`. Requires **JDK 21**.

---

## A note on timing accuracy

A hit is registered the tick the **client** applies its knockback — there is no purely client-side way to know another player hit you before the server reports it. To compensate, the hit timestamp is shifted back by your estimated one-way latency (`pingCompFactor × ping`), so the delta approximates true server-side timing rather than purely client-perceived timing. It still won't be frame-perfect, but it's consistent across attempts — which is exactly what makes the standard-deviation (variance) figure meaningful as a human-vs-bot signal.

---

## License

[CC0-1.0](LICENSE) — public domain. Learn from it, copy it, do whatever you like.
