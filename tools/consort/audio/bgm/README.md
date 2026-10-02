# Promised Consort battle music

Current candidate: **Blackmoor Colossus**, by **Matthew Pablo**.
[Source](https://opengameart.org/content/colossal-boss-battle-theme),
[CC BY 3.0](https://creativecommons.org/licenses/by/3.0/).
No Elden Ring recording or melody is used.

Open [audition.html](audition.html) locally to compare the two 117.65-second loops.
Phase one uses the author's no-vocals version; phase two uses his choir version.
Both are stereo 44.1 kHz Ogg Vorbis, normalized to -20 LUFS with 8 ms boundary
fades. The source archive and per-track hashes are recorded in `manifest.json`.
Original downloads stay in ignored `.workspace-tools/audio-source/`; only the two adapted
loops and [attribution](../../../../src/main/resources/META-INF/BGM-CREDITS.txt)
are shipped. CC BY 3.0 continues to apply to the music separately from the mod.

Reproduce using Python, numpy and FFmpeg (`FFMPEG` environment variable or
imageio-ffmpeg):

```powershell
python tools/consort/scripts/prepare_bgm.py path/to/source.zip
```

Vorbis output container IDs may differ across FFmpeg runs; deliberate regeneration
updates the output hashes. The script checks finite samples, decoded peak,
stereo duration equality and loop-boundary discontinuity. The Java music check
covers encounter selection, lifecycle, fades and processed resources on both
loaders. These checks do not establish in-game listening quality.

Runtime events are `elder_bosses:music.promised_consort.phase_one` and
`elder_bosses:music.promised_consort.phase_two`. Resource packs can replace
`sounds/music/promised_consort/phase_one.ogg` and `phase_two.ogg` while retaining
the same playback logic. Client settings are under `[boss_music]` in the existing
common TOML. No new configuration file or network packet is introduced.
