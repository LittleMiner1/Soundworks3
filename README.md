# Soundworks (Fabric, Minecraft 26.3)

Press your own MP3 files onto records and play them in the world. Everyone nearby hears the music in stereo, even without owning the files.

## How to use
1. Put MP3 files into the `music` folder in your game directory. In-game you can see them (and open the folder) under **Options > Music & Sounds > Soundworks**.
2. Place a **Record Press**, insert a **Blank Record** (and optionally a dye for the inner ring), pick an MP3 and click *Press*.
3. Put the record into a normal jukebox, the **Turntable** or the **Jukebox**.
   Turntable and Jukebox have sliders for **speed (75 % - 125 %, default 100 %)** and **volume (0 % - 200 %, default 100 %)**.
4. To copy a record: put the pressed record into *Source (copy)*, a blank record into *Blank record*, then click *Copy*.

All blocks and the blank record are in the **Soundworks** creative tab.
A pressed record shows the artist (ID3 tag, otherwise "Unknown Artist") instead of "custom".

## Recipes
- Blank Record (x2): 4 coal/charcoal in a plus shape, iron nugget in the center
- Record Press: piston on top; iron ingot, black dye, iron ingot in the middle; 3 stone at the bottom
- Turntable: diamond, wool below it, 3 iron ingots below that
- Jukebox: note block, turntable, note block in a row; a chest below

## Sound network
- **Record Box**: 27 slots. Touching boxes (all 6 directions, no limit) form one big storage; click any box to open it.
  Scroll with the buttons or the mouse wheel, search with the search field. Placed standing by default, lying when placed while sneaking
  (sneak + empty-hand right click switches).
- **Server**: 9 record slots, IP (digits and dots), name and an optional password. Servers with the same IP and password form one network.
  A server only works (green lamp) while an **Antenna** with the same IP is loaded; a blinking green lamp means a song is being streamed from it.
- **Antenna**: click it and enter the IP (no password).
- **Radio**: enter IP and password, search the songs of the whole network and play them from anywhere. Has its own speaker frequency.
- **Terminal**: wall or table display; enter IP and password to see which server holds which song (search field included).
- **Speaker**: enter a frequency. Turntable, Jukebox and Radio have a *speaker frequency*; every speaker with the same frequency plays that sender.
  A frequency can only be used by one sender, but by any number of speakers. The sender is muted when a speaker of its frequency is closer to you.
- Servers and antennas only count while their chunks are loaded.

## Network recipes (optional, changeable)
- Record Box: wooden slabs around two sticks (`PPP / S S / PPP`)
- Server: antenna on top; iron, record box, iron; iron, redstone lamp, iron
- Antenna: copper ingot, redstone-iron-redstone, iron
- Radio: antenna, planks-redstone-planks, planks row
- Terminal: 3 glass panes, 3 iron ingots, 1 iron ingot below
- Speaker: antenna, planks-note block-planks, planks-redstone-planks

## Multiplayer
When pressing, your client uploads the MP3 to the server (`<world>/soundworks/<sha256>.mp3`). A record only stores the hash,
so anyone who plays it downloads the file automatically into `soundworks_cache/`. Everyone needs the mod, but not the MP3s.
Players who join later hear the song at the same position as everyone else. Works with Essential worlds.

## Building
Java 25 is required: `./gradlew build` produces `build/libs/soundworks-1.0.0.jar`.
The included GitHub workflow (`.github/workflows/build.yml`) builds the jar on every push; push a tag like `v1.0.0` to get a release.
