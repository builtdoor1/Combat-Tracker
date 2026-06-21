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

- **Hit** (Event A): the mod reads the client damage-event packet and only counts damage dealt by **another player** — mobs, fall damage, fire, etc. are ignored.
- **Jump** (Event B): every client tick it checks whether you just left the ground while holding the jump key.
- **Delta** = `jumpTime − hitTime`, in milliseconds. When a hit and a jump occur within the *outer window* of each other, it's recorded as an **attempt** and classified:

| Delta | Result |
|-------|--------|
| Between LOWER and UPPER | ✅ **HIT** |
| Greater than UPPER | ❌ **MISS** (too late) |
| Less than LOWER (jumped before the hit) | ❌ **MISS** (too early) |

All timing is measured on the client.

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
| **Outer attempt window** | `400 ms` | How close (in either direction) a hit and a jump must be to count as an *attempt* at all. Jumps further than this from any hit are ignored as unrelated, so casual jumping never affects your stats. |
| **HUD** | `ON` | Show or hide the on-screen overlay (same as pressing **`J`**). |
| **Chat messages** | `ON` | Whether each attempt prints a HIT/MISS line in chat. |
| **Move HUD…** | — | Opens a screen where you drag the HUD to any position. |
| **Reset Stats** | — | Clears all recorded attempts and statistics. **Click twice** to confirm. |

> All sliders range from **0–600 ms**. The success window must satisfy `LOWER ≤ UPPER`; it is clamped automatically when you close the config screen.

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

The remapped mod jar is written to `build/libs/combat-tracker-<mc-version>.jar`. Requires **JDK 21**.

---

## A note on timing accuracy

Hit timing is taken from when the **client receives** the damage packet — there is no purely client-side way to know another player hit you before the server reports it. So each delta reflects your reaction relative to *seeing* the hit, and includes your ping as a roughly constant offset. This is consistent across attempts and is exactly what makes the standard-deviation (variance) figure meaningful.

---

## License

[CC0-1.0](LICENSE) — public domain. Learn from it, copy it, do whatever you like.
