<p align="center">
  <img src="branding/png/subway-coders-logo.png" alt="Subway Coders" width="520">
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/32509"><img src="https://img.shields.io/jetbrains/plugin/v/32509?label=Marketplace&logo=jetbrains" alt="JetBrains Marketplace"></a>
  <a href="https://plugins.jetbrains.com/plugin/32509"><img src="https://img.shields.io/jetbrains/plugin/d/32509?label=Downloads" alt="Downloads"></a>
  <a href="https://plugins.jetbrains.com/plugin/32509"><img src="https://img.shields.io/jetbrains/plugin/r/rating/32509?label=Rating" alt="Rating"></a>
</p>

# Subway Coders 🏄

A JetBrains/IntelliJ plugin that loops Subway Surfers-style gameplay in tool windows next to your
code. Minecraft parkour and story clips, Temple Run, and the usual brain-rot are in there too. It's
a joke that turned out to be genuinely nice to have running in the corner.

Everything renders through JCEF, the Chromium browser that ships with the JetBrains Runtime. Drop in
a YouTube link and it plays as an embed; any other URL loops as a plain video. There are four players,
one docked to each edge of the IDE, and they run independently.

## What it does

- Four separate players, one per edge (left, right, top, bottom). Each remembers the category you left
  it on.
- Categories come from a JSON config. A category is just a list of clips; the window picks one at
  random and **Shuffle** rolls again. Out of the box you get Subway Surfers, Temple Run, Minecraft
  Parkour, Minecraft Story, GTA Ramps, Satisfying, and Slime.
- Clips are URLs, so a YouTube link works and so does a direct video file (say, a WebM you host
  yourself). Paste a URL straight into a window to override its category.
- Feeling brave? Pick **Doomscroll (YouTube Shorts)** from the category dropdown and the window turns
  into the actual `youtube.com/shorts` feed that you scroll by hand. No API keys, no curated list,
  it just loads the real thing in the embedded browser and follows your IDE's light/dark theme.
- Playback starts muted (otherwise autoplay gets blocked); unmute from the player's own controls if
  you want sound. You can also hide the toolbar per window via **Show Controls** in the gear menu.
- If you use Claude Code, the plugin hides the players and jumps focus to the built-in **Terminal**
  whenever Claude asks something or wraps up, so you snap back out of the feed. It's on by default and
  there's a toggle in the gear menu.

## Configuring categories & clips

Two files matter here. The defaults live in `src/main/resources/config/default-categories.json`, and
on first run the plugin copies them to an editable version at
`<IDE config dir>/subway-coders/categories.json`.

To change anything, open the gear menu and hit **Edit Config…** (that opens the editable copy). Save,
then click **Shuffle** and the window re-reads the file. Every clip is just a URL, either a YouTube
link or a video file you host somewhere:

```json
{
  "categories": [
    { "name": "Minecraft Story", "clips": [
      "https://youtu.be/n_Dv4JMiwK8",
      "https://media.example.com/clips/my-minecraft-story.webm"
    ] }
  ]
}
```

## Install from JetBrains Marketplace

Go to *Settings → Plugins → Marketplace*, search for **Subway Coders**, and click **Install**. Or grab
it straight from the [Marketplace page](https://plugins.jetbrains.com/plugin/32509) if you prefer.

## Run it (development)

You'll need JDK 21.

```bash
./gradlew runIde
```

The first run pulls down the IntelliJ Platform SDK (a few hundred MB) and then spins up a sandbox IDE
with the plugin loaded. Open any of the **Subway Coders Left/Right/Top/Bottom** tool windows from its
edge and pick a category.

## Build an installable plugin zip

```bash
./gradlew buildPlugin
# -> build/distributions/subway-jetbrains-extension-1.0.0.zip
```

Then install it through *Settings → Plugins → ⚙ → Install Plugin from Disk…*

## License

[MIT](LICENSE) © suprexde
