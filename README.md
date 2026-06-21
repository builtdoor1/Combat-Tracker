# Combat Tracker

A **client-side** [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.11** that measures and records your PvP timing — both **jump resets** and **combo cadence** — and presents it as anti-cheat evidence. By *builtdoor*.

Combat Tracker is **purely observational**: it never modifies movement, knockback, attack timing, or any other game mechanic. It only watches what already happens and keeps statistics.

Two things it proves about *you*:

- **Jump resets** are human, not an auto-reset cheat — a human shows natural timing spread (standard deviation); a bot is near-constant.
- **Combos** are human, not a triggerbot — a triggerbot swings on a near-constant interval (low *jitter*); a human's clicks vary.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) (0.19.3+).
2. Put these in your `.minecraft/mods` folder:
   - `combat-tracker-<version>+1.21.11.jar` — from the [latest release](../../releases/latest)
   - [Fabric API](https://modrinth.com/mod/fabric-api) (`0.141.4+1.21.11`)
   - *(optional)* [Mod Menu](https://modrinth.com/mod/modmenu) — for the in-game config screen
3. Launch Minecraft.

---

## How detection works

Everything is client-side and observational.

### Jump resets
- **Jump** is detected from the vertical-velocity **impulse** (delta-vy), sampled *before* physics by a mixin — so it fires even on the exact tick a hit lands.
- **Hit** is detected when `hurtTime` goes 0→positive while the regen timer is active **and** horizontal knockback exceeds a threshold (which filters fall/fire/poison).
- A hit opens a short tick window; the next jump in it is scored. Timing uses `System.nanoTime()` with one-way **ping compensation**. The signed delta is classed **HIT** (inside the success window), **MISS – too late**, or **MISS – too early**.

### Combos (triggerbot signal)
- A **combo** is a chain of **≥2 sprint hits** on the *same* player, with no self-damage in between and no gap longer than `maxComboGapMs`.
- Each outgoing hit is detected via the attack method (a landed left-click on a player); only hits made while **sprinting** count.
- The mod records the **interval** between consecutive hits, then reports the **average interval** and the **jitter** (standard deviation of those intervals).
- **Low jitter ≈ triggerbot** (machine-constant cadence); **high jitter ≈ human**.

> Jump-reset detection is adapted from the open-source [sombreror/JumpReset-mod](https://github.com/sombreror/JumpReset-mod).

---

## The HUD

Toggle with **`J`** (rebindable under *Options → Controls*). Drag to reposition via the config screen → **Move HUD…**. A red `● REC` line appears while a session is recording.

**Detailed layout** shows:

| Line | Meaning |
|------|---------|
| Jump: X hit / Y miss | Jump-reset hits and misses |
| Rate / Avg / SD | Success rate, average delta, standard deviation (ms) |
| Combo … jitter ±… | Average combo interval and its jitter (ms) |
| Combos / Last JR | Number of combos, and your last jump-reset result |

**Compact layout** condenses this to two lines.

### Customizing the HUD
All in the config screen:

| Control | Default | Effect |
|---------|---------|--------|
| **HUD scale** | `1.00x` | Shrinks/enlarges the whole overlay (0.5–2.0). |
| **BG opacity** | `56%` | Background box transparency (0 = no box). |
| **Layout** | Detailed | Toggle Detailed ↔ Compact. |
| **Theme** | Yellow | Accent color (Yellow / Aqua / Green / Pink / Orange / White). |
| **Move HUD…** | — | Drag the overlay anywhere on screen. |

---

## Chat messages

When **Chat** is enabled, each jump-reset *attempt* prints one line (green HIT / red MISS). Ordinary jumps and ordinary hits never spam chat.

---

## Recording sessions & reports

Use **Start Recording** in the config screen (or bind the *Start/Stop Recording* key under Controls). While recording, every jump-reset attempt and every combo interval is captured with a timestamp. **Stop Recording** writes two files to `config/jump_reset_tracker/recordings/` and prints the path to chat:

- `session-<timestamp>.html` — a self-contained report you open in any browser: start/end **time signatures**, summary cards, and **SVG charts** (jump-reset delta over time, combo interval over time) with hover tooltips.
- `session-<timestamp>.json` — the canonical data plus the integrity block.

Use **Open recordings folder** in the config screen to jump straight there.

### Integrity (read this honestly)
Each session embeds a **SHA-256 hash** and an **HMAC-SHA256 signature** over the exact recorded data, plus start/end timestamps. Editing the file breaks the hash, so **casual tampering is detectable**.

**It is not unforgeable.** The signing key ships inside this open-source mod, so anyone who recompiles it can fabricate "clean" data. Truly unforgeable evidence would require a trusted server or external video capture, which a client mod cannot provide. Treat a report as *tamper-evidence*, not proof against a determined cheater.

---

## Configuration files

```
.minecraft/config/jump_reset_tracker/
├── config.json        # timing window, HUD options, detection tuning
├── stats.json         # jump-reset history + aggregates
├── combo_stats.json   # combo intervals + aggregates
└── recordings/        # session .html reports + .json data
```

### Advanced tuning (`config.json`, no GUI)

| Key | Default | Meaning |
|-----|---------|---------|
| `jumpDeltaThreshold` | `0.25` | Min upward velocity impulse to register a jump. |
| `knockbackThreshold` | `0.065` | Min horizontal speed after damage to count it as a combat hit. |
| `windowTicksGround` / `windowTicksAir` | `6` / `10` | Ticks after a hit during which a jump still counts. |
| `pingCompFactor` | `0.5` | Fraction of round-trip ping treated as one-way latency. `0` disables. |
| `maxComboGapMs` | `1500` | Max gap between sprint hits to stay one combo. |

The success-window bounds (`lowerBoundMs` `0`, `upperBoundMs` `80`) are editable from the config screen sliders.

---

## Building from source

```bash
./gradlew build
```

Output: `build/libs/combat-tracker-<modversion>+<mcversion>.jar`. Requires **JDK 21**.

---

## License

[CC0-1.0](LICENSE) — public domain.
