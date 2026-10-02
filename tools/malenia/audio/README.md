# Malenia audio

Open [audition.html](audition.html) to listen to the two 66-second battle loops
and the original synthesized parry impact. The in-game mix still needs listening
on a real client; the manifest reports decoded audio checks, not listening approval.

Music: **Invasion**, Sascha Ende, CC BY 4.0.
[Author's page](https://ende.app/en/song/206-invasion),
[author's license](https://ende.app/en/standard-license),
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Changes and hashes are recorded in [manifest.json](manifest.json).
The packaged attribution is `src/main/resources/META-INF/BGM-CREDITS.txt`.

To rebuild, obtain the complete recording from the author's page and run:

```text
python tools/malenia/scripts/prepare_audio_v16.py path/to/invasion.mp3
```

Requires NumPy and imageio-ffmpeg (or set `FFMPEG` to an installed executable).
The script generates both music loops and the parry impact, checks finite and
unclipped decoded audio, and rejects a loop boundary discontinuity above 0.003.
The source recording is not duplicated in the shipped mod.
