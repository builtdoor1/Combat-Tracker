# Combat Tracker

A **client-side** [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.11** that measures and records how you fight — **jump resets**, **combo cadence**, **reach** and **aim** — and presents it as anti-cheat evidence. By *builtdoor*.

Combat Tracker is **purely observational**: it never modifies movement, knockback, attack timing, reach, or aim. It only watches what already happens and keeps statistics.

Four things it proves about *you*:

- **Jump resets** are human, not an auto-reset cheat — a human shows natural timing spread (standard deviation); a bot is near-constant.
- **Combos** are human, not a triggerbot — a triggerbot swings on a near-constant interval (low *jitter*); a human's clicks vary.
- **Reach** is normal — every swing, landed or whiffed, plotted against vanilla's 3.0-block melee range.
- **Aim** is human, not an aim assist — where your crosshair sat on the hitbox, scattered across it rather than pinned to dead centre.

Any session can be turned into a **shareable link** that carries the whole fight inside the URL — nothing is uploaded anywhere.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) (0.19.3+).
2. Put these in your `.minecraft/mods` folder:
   - `combat-tracker-<version>+1.21.11.jar` — from the [latest release](../../releases/latest)
   - [Fabric API](https://modrinth.com/mod/fabric-api) (`0.141.4+1.21.11`)
   - [Cloth Config](https://modrinth.com/mod/cloth-config) — builds the settings screen
   - *(optional)* [Mod Menu](https://modrinth.com/mod/modmenu) — adds the button that opens it
3. Launch Minecraft.

---

## How detection works

Everything is client-side and observational.

### Jump resets
- **Jump** is detected from the real `jumpFromGround()` call via a mixin — so knockback (e.g. a crit while standing still) can never be mistaken for a jump.
- **Hit** is detected when `hurtTime` goes 0→positive while the regen timer is active **and** horizontal knockback exceeds a threshold (which filters fall/fire/poison).
- A hit opens a short tick window; the next jump in it is scored. Timing uses `System.nanoTime()`, wound back by **one-way latency** — measured automatically as half the median of your reported ping, so a single lag spike can't skew a result. The signed delta is classed **HIT** (inside the success window), **MISS – too late**, or **MISS – too early**.

### Combos (triggerbot signal)
- A **combo** is a chain of **≥2 sprint hits** on the *same* player, with no self-damage in between and no gap longer than `maxComboGapMs`.
- Each outgoing hit is detected via the attack method (a landed left-click on a player); only hits made while **sprinting** count.
- The mod records the **interval** between consecutive hits, then reports the **average interval** and the **jitter** (standard deviation of those intervals).
- **Low jitter ≈ triggerbot** (machine-constant cadence); **high jitter ≈ human**.

### Reach & aim
- Every left-click attack is caught at `startAttack()` — **including the ones that miss**, which the attack method never sees. Vanilla's own guards are re-checked so clicks it discards (post-whiff cooldown, hands busy) don't enter the data.
- **Reach** is eye-to-hitbox distance: the point where your aim ray crosses the target's box, or the nearest point on it when you whiffed. That's the figure the community and server anti-cheats both mean by "reach".
- **Aim** is recorded two ways: the **angle** between your crosshair and the hitbox centre, and **where on the hitbox** the ray passed, plotted as a scatter with the box drawn to scale.
- A whiff is attributed to the player closest to your crosshair within 6 blocks and 30°. Swinging at empty air near nobody records nothing.

> **These are your client's numbers.** Other players are interpolated locally and the server saw them somewhere slightly different depending on latency, so reach measured here won't match a server-side anti-cheat exactly. The report says so too.

> Jump-reset detection is adapted from the open-source [sombreror/JumpReset-mod](https://github.com/sombreror/JumpReset-mod).

---

## The HUD

Toggle with **`J`** (rebindable under *Options → Controls*). Drag to reposition via the config screen → **Move HUD…**. A red `● REC` line appears while a session is recording.

**Detailed layout** shows:

| Line | Meaning |
|------|---------|
| Jump: X hit / Y miss | Jump-reset hits and misses |
| Rate / Avg / SD | Success rate, average delta, standard deviation (ms) |
| Last JR | Your last jump-reset result |
| Combo: N   Last … | Live combo counter and the time of the last combo hit |
| Variance … | How much your combo timing varies — **colored red when it barely varies (bot-like) and white when it varies a lot (human)** |

The combo counter resets to 0 once you go ~0.7s without landing a hit — roughly the sword's full attack-cooldown recharge (~0.625s) plus a little grace. If your sword is charged and you don't swing, the combo is over. The Variance value stays gray until there are enough samples to be meaningful.

**Compact layout** condenses this to a few lines.

### Customizing the HUD
Open the settings screen from **Mod Menu → Combat Tracker → Config**. It has three tabs.

**General:**

| Control | Default | Effect |
|---------|---------|--------|
| **Show HUD** | On | Master switch for the overlay (same as the `J` keybind). |
| **Chat messages** | On | One line per jump-reset attempt. |
| **Compact layout** | Off | Condense the HUD to a few lines. |
| **HUD scale** | `1.00x` | Shrinks/enlarges the whole overlay (0.5–2.0). |
| **Background opacity** | `56%` | Background box transparency (0 = no box). |
| **Theme** | Yellow | Accent color (Yellow / Aqua / Green / Pink / Orange / White). |
| **Move HUD…** | — | Drag the overlay anywhere on screen. |

**Timing** holds the detection tuning (see below). **Recording** holds the session controls and **Reset stats**, which clears **both** jump-reset and combo statistics (click twice to confirm).

---

## Chat messages

When **Chat** is enabled, each jump-reset *attempt* prints one line (green HIT / red MISS). Ordinary jumps and ordinary hits never spam chat.

---

## Recording sessions & reports

Use **Start Recording** in the config screen (or bind the *Start/Stop Recording* key under Controls). While recording, every jump-reset attempt and every combo interval is captured with a timestamp. **Stop Recording** writes two files to `config/combat_tracker/recordings/` and prints the path to chat:

- `session-<timestamp>.html` — a self-contained report you open in any browser: start/end **time signatures**, summary cards, and **four interactive SVG charts** (jump-reset delta, combo interval, reach per swing, and the aim-placement scatter). **Scroll to zoom, drag to pan, double-click to reset** — so you can inspect small jitters. Hover a point for its exact value.
- `session-<timestamp>.json` — the canonical data plus the integrity block.

Use **Open recordings folder** in the config screen to jump straight there.

### Shareable links

Stopping a recording also prints a **click-to-copy share link**, and **Copy share link** in the config screen copies the most recent one again.

The entire session is compressed into the part of the URL after `#`, which browsers never send to any server. So the link works for anyone you send it to, but **nothing is uploaded anywhere** — there's no account, no host storing your fights, and nothing to go offline. The page at the other end is a static viewer in [`docs/`](docs/index.html) that decodes the link in your browser and draws the same charts, headed by your IGN and skin.

Links are kept under **1800 characters** so they paste into a Discord message. If a session has more events than fits, the *graphs* carry an evenly spaced sample and the viewer says so — the summary figures are always computed over every event and are never approximated.

> A shared link names the players you fought, and anyone who recompiles this open-source mod can fabricate one. It's a readable record of a fight, not proof.

### Integrity (read this honestly)
Each session embeds a **SHA-256 hash** and an **HMAC-SHA256 signature** over the exact recorded data, plus start/end timestamps. Editing the file breaks the hash, so **casual tampering is detectable**.

**It is not unforgeable.** The signing key ships inside this open-source mod, so anyone who recompiles it can fabricate "clean" data. Truly unforgeable evidence would require a trusted server or external video capture, which a client mod cannot provide. Treat a report as *tamper-evidence*, not proof against a determined cheater.

---

## Configuration files

```
.minecraft/config/combat_tracker/
├── config.json        # timing window, HUD options, detection tuning
├── stats.json         # jump-reset history + aggregates
├── combo_stats.json   # combo intervals + aggregates
└── recordings/        # session .html reports + .json data
```

### Timing (**Timing** tab)

| Setting | Key | Default | Meaning |
|---------|-----|---------|---------|
| Success window min / max | `lowerBoundMs` / `upperBoundMs` | `0` / `80` ms | Bounds of a successful jump reset. |

That is the only adjustable timing value, and it only decides what counts as a *success* — it never changes what gets measured.

**Detection tuning is fixed on purpose.** The knockback threshold (`0.065`), the post-hit tick windows (`6` grounded / `10` airborne), and the combo gap (~0.7s) are constants in the source, and one-way latency is measured automatically from a median of your reported ping rather than a hand-set factor. These were never preferences: a wrong value silently corrupts the very statistics the mod exists to defend, and a report only means something if every copy of the mod measured the same way. Older `config.json` files may still list the removed keys — they are ignored and dropped on the next save.

---

## Building from source

```bash
./gradlew build
```

Output: `build/libs/combat-tracker-<modversion>+<mcversion>.jar`. Requires **JDK 21**.

Everything downstream of a recorded session — statistics, charts, link encoding — is deliberately free of any Minecraft import, so it can be rendered without launching the game:

```bash
./gradlew reportPreview
```

That writes a synthetic session to `build/preview/` as both an HTML report and a link fragment, and prints how link length behaves as sessions get longer.

---

## License

[MIT](LICENSE) © 2026 builtdoor
