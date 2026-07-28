Combat Tracker records how you fight and turns it into a report you can show people.

Got accused of reach, killaura, autoclicker or auto jump-reset? Hit record, play a few fights, and you get charts of your own timing and aim, plus a link you can paste in Discord.

It never touches your gameplay. It cannot help you aim, reach further, click faster or reset better. It only watches and writes down what already happened. Client side only, and it works on any server.

## What clears you

Four accusations, four answers. In every case the thing that clears you is inconsistency. People vary, cheats do not.

### Jump resets

![Jump reset timing, human against macro](https://builtdoor1.github.io/Combat-Tracker/images/example-jump-reset.png)

Every attempt is timed and plotted. A person's timing wanders by tens of milliseconds and never repeats exactly. A macro parks on the same value, which shows up as a flat line.

### Combos

![Combo timing, human against autoclicker](https://builtdoor1.github.io/Combat-Tracker/images/example-combo.png)

The gap between consecutive hits in a combo. Human clicking jitters. A bot's interval is machine steady, and the jitter figure in the summary puts a number on it.

Intervals are timed from your actual mouse press rather than from the game tick. Timing them off the tick rounds every interval to a 50ms boundary, which erases the exact variation this chart exists to show.

### Reach

![Reach, human against reach cheat](https://builtdoor1.github.io/Combat-Tracker/images/example-reach.png)

How far the target was on every swing, hit or miss. Green landed, red whiffed, dashed line at vanilla's 3.0 blocks.

This is the distance from your eye to the nearest point of the target hitbox, which is exactly what the game itself checks and what server anti-cheats measure.

Red dots above the line are normal. That is a swing thrown from too far away, which is why it missed. Green dots above the line are the thing that would matter.

### Aim

![Aim placement, human against aim assist](https://builtdoor1.github.io/Combat-Tracker/images/example-aim.png)

Where your crosshair actually sat on the target, with the hitbox drawn to scale. A messy cloud over the body is a person tracking a moving target. Aim assist produces a tight knot on centre.

## In game

Press `J` to show or hide the overlay. It keeps a running count while you play.

```
Jump: 12 hit / 5 miss
Rate 70.6%  Avg 44 SD 21
Last JR: HIT +38ms
Combo: 3   Last 640ms
Variance 47ms
```

The Variance line changes colour, and it is the one to watch. Red means your clicking barely varies, which is the bot-like pattern. White means it varies a lot, which is what a person looks like. White is good.

## Recording a fight

Open the settings from Mod Menu, go to the Recording tab, hit Start Recording, play, then hit Stop. You can also bind a key for it under Options, Controls.

Two files land in `.minecraft/config/combat_tracker/recordings/`:

- A self contained HTML report. Double click it, it opens in your browser and needs no internet.
- A JSON file with the raw numbers, for anyone who wants to check them.

On every chart you can click a point for its exact value, scroll to zoom, drag to pan, and double click to reset.

## Sharing

Stopping a recording also gives you a click to copy link.

Nothing is uploaded. The whole fight is compressed into the link itself, into the part after the `#`, which browsers never send to any server. There is no account, no server holding your fights, and nothing that can leak later. The page at the other end just unpacks the link in your browser and draws the same charts, with your name and skin at the top.

Links are kept short enough to paste into a Discord message. If a fight was long, the charts show an evenly spaced sample and the page says so. The summary numbers are always calculated from every event.

Note that a shared link names the people you fought.

## What this can and cannot prove

Being straight about it, because a tool like this is worthless if it oversells itself.

It shows the natural inconsistency of a real person playing. That is hard to fake convincingly and is usually what an admin wants to see.

It is not unforgeable. Every report carries a SHA-256 hash and a signature, so casual editing of the file gets caught, but the signing key ships inside this open source mod. Anyone who recompiles it can produce whatever numbers they like. Real proof would need a trusted server or a video, which no client side mod can provide.

Reach and aim are measured on your computer. Other players' positions are estimated between updates on your end, and the server saw them slightly differently depending on your ping, so these figures will not exactly match a server anti-cheat's.

It cannot prove a negative. It shows what your fights looked like. Someone determined not to believe you still will not.

Treat a report as strong supporting evidence, not a verdict.

## Settings

Mod Menu, Combat Tracker, Config. Three tabs.

**General** covers the overlay: show or hide, chat messages, compact layout, scale, background opacity, accent colour, and dragging the overlay anywhere on screen.

**Timing** has the success window, which decides what counts as a successful reset. It changes the scoring only, never the measurement. Everything else about detection is fixed on purpose. A wrong value would quietly corrupt the numbers this mod exists to defend, and a report only means something if every copy measured the same way.

**Recording** has the session controls, the recordings folder, copy share link, and reset stats.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.19.3 or newer
- Fabric API
- Cloth Config, which builds the settings screen
- Mod Menu, optional, adds the button that opens the settings

## Links

- [Source on GitHub](https://github.com/builtdoor1/Combat-Tracker)
- [Report an issue](https://github.com/builtdoor1/Combat-Tracker/issues)

Licensed MIT.

Jump reset detection is adapted from [sombreror/JumpReset-mod](https://github.com/sombreror/JumpReset-mod). The reach metric follows [Wolren/ReachDisplay](https://github.com/Wolren/ReachDisplay). Dropping ping compensation follows [sootysplash/jump-reset](https://github.com/sootysplash/jump-reset).
