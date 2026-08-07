Combat Tracker records how you fight and turns it into a report you can show people.

Got accused of reach, killaura, autoclicker or auto jump-reset? Hit record, play a few fights, and you get charts of your own timing and aim, plus a link you can paste in Discord.

It never touches your gameplay. It cannot help you aim, reach further, click faster or reset better. It only watches and writes down what already happened. Client side only, and it works on any server.

## What a human looks like

Four accusations, four answers. In every case the tell is inconsistency. People vary, cheats do not. The mod shows you the shape — it does not settle the argument, but it gives someone willing to look something concrete to look at.

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

The fight data is never uploaded. It is compressed into the part of the link after the `#`, which browsers never send to any server. There is no account and no server holding your fights. The page at the other end just unpacks the link in your browser and draws the same charts, with your name and skin at the top. One exception worth naming: the page fetches player heads from `mc-heads.net`, so that service sees the names on the report.

Links are kept short enough to paste into a Discord message. If a fight was long, the charts show an evenly spaced sample and the page says so. The mod calculates the summary numbers from every event, though the viewer prints whatever the link carries rather than re-deriving them from the plotted points.

Note that a shared link names the people you fought. The reverse is also true: the name and skin at the top of a link are just text in the payload, so anyone can produce a link that appears to be someone else's.

## What this can and cannot show

Being straight about it, because a tool like this is worthless if it oversells itself.

It makes the texture of your play visible: scattered aim against a knot on centre, timing that wanders against timing that does not. That is what an admin is actually trying to see, and it reads off a chart in ten seconds where it takes twenty minutes of clips.

The share link proves nothing. It carries no signature at all, and the page that renders it only checks that the format version is one it knows. Anyone can write one from scratch in about a hundred lines of Python, in any name they like, and the page will fetch that player's real skin to go with it. No mod and no recompile required.

The saved files are a checksum, not a seal. The HTML and JSON carry a SHA-256 and an HMAC-SHA256 over the recorded data, which catch a corrupted file and catch someone editing the numbers in a text editor. They do not stop anyone who edits the numbers and recomputes the two values, because the key ships inside this open source mod. Real proof would need a trusted server or a video, which no client side mod can provide.

Reach and aim are measured on your computer. Other players' positions are estimated between updates on your end, and the server saw them slightly differently depending on your ping, so these figures will not exactly match a server anti-cheat's.

It cannot prove a negative. It shows what your fights looked like. Someone determined not to believe you still will not.

A report is a record of what a fight looked like. To someone who already has reason to take you seriously it is worth a lot, because it shows a shape that is tedious to fake and instant to read. To someone who does not, it is worth nothing, and you should expect them to say so.

## Settings

Mod Menu, Combat Tracker, Config. Three tabs.

**General** covers the overlay: show or hide, chat messages, compact layout, scale, background opacity, accent colour, and dragging the overlay anywhere on screen.

**Timing** has the success window, which decides what counts as a successful reset. It changes the scoring only, never the measurement. Everything else about detection is fixed on purpose. A wrong value would quietly corrupt the numbers, and two reports are only comparable if both were measured the same way.

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
