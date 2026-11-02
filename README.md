# Radio+

![Radio+ pixel-art logo](src/main/resources/radio_logo.png)

Radio+ **1.2.0** adds FM, DAB/DAB+, and DRM radio systems, plus ADS-B tracking and VOR navigation, to Minecraft **1.21.1** with NeoForge. Stations are placed in the world, and reception changes with distance, antennas, height, and terrain.

## Install and get started

- Use **Minecraft 1.21.1**, **NeoForge 21.1.229 or newer** for that version, and **Java 21**. Install the mod JAR on the server and every connecting client; for single player, install it on the client.
- There are currently no crafting recipes. Get equipment from the **Radio+** creative tab or with `/give @s radio:<item_id>`.
- To make your first FM station, place an **FM Transmitter**, set its frequency, power, and audio URL, then link an **Antenna** with the **Linking Tool**. Right-click a handheld **Radio** and tune to the same frequency.
- To make a DAB station, create a service on a **DAB Server**, select that server and MUX in a **DAB Transmitter**, then link an antenna.
- Placed receivers play for nearby players; portable radios play for the player carrying them. Pinned displays stack at the top right of the HUD.

## Tools and station management

### Linking Tool (`radio:linking_tool`)

Select an antenna, then right-click a compatible transmitter or receiver to link them. The tool also connects an **ADS-B Receiver** to an **ADS-B Server**. A physical antenna has one active link at a time; linking it elsewhere moves the connection. DAB transmitters select their server and MUX in their own screen.

### Recorder (`radio:recorder`)

Place and open it to upload, rename, delete, and inspect your personal audio files. Its storage meter shows your remaining quota. Files can be loaded from FM or DRM transmitters and from DAB stations on the DAB Server.

Each upload may be at most **128 MiB** and **2 hours** long. Supported inputs include MP3, WAV, OGG, Opus, FLAC, AAC, M4A, and MP4; the server validates audio duration with FFmpeg. Each player has a separate library in each world. The default quota is **512 MiB per player**, configurable with `maxAudioStorageMiB` in `config/radio-common.toml` (128–16384 MiB). Files are stored in the world's `radio_uploads/` directory.

### Transmitter Registry (`radio:registry`)

Browse loaded FM, DAB, DAB+, and DRM transmitters across dimensions. The list includes mode, frequency, coordinates, power, programme, and audio source; each DAB service appears separately with its MUX. Sort by type, frequency, name, country, or power. Search by name, PI, or SID and combine mode, frequency, and power filters. The **Countries** tab assigns names to the first hexadecimal digit of FM PI and DAB service SID codes for the whole world; unassigned codes show **Unknown**.

### Signal Meter (`radio:signal_meter`)

Measure FM or DAB without playing audio. Tune FM by frequency or step through DAB channels; **PIN** keeps the technical display on the HUD. FM shows signal strength, SNR, estimated RDS BER, PI, PS, and decoded RDS groups. DAB shows the MUX, protection setting, FIC/MSC/frame-sync estimates, service list, and TII levels. In an SFN, the strongest TII signal is **0.0 dB** and others are relative to it.

### Meteor Forecaster (`radio:meteor_forecaster`)

Right-click to see the current meteor-scatter rate and upcoming or active FM openings. `/meteorscatter` shows the same status in chat. An admin (permission level 2) can set `/meteorscatter rate <meteors_per_hour>` to **20–1200** meteors per real hour, or **0** for a random rate each Minecraft night. Openings occur only at night, last **1–10 seconds**, and can briefly lift distant FM signals to **0–25 dBµV**.

## Antennas

### Antenna (`radio:antenna`)

The general-purpose antenna links to a transmitter or placed receiver. A VOR Transmitter must use this regular antenna. Antennas improve reception or provide the transmitter's broadcast point.

### Directional Antenna (`radio:directional_antenna`)

Link it like a regular antenna, then right-click it without the Linking Tool to edit its eight compass sectors. Gain ranges from **−24 to +8 dB** per sector; improving one direction reduces gain in opposing directions. The signal blends smoothly as a receiver moves between sectors. Angles are world directions: **0° east, 90° south, 180° west, 270° north**; 360° equals 0°.

### Loop Antenna (`radio:loop_antenna`)

A receive-only antenna for a placed **DRM Radio**. Link the loop to that radio with the Linking Tool; it does not broadcast and cannot serve other receivers.

### 3-Element Yagi (`radio:yagi_3`)

A receive-only directional antenna with **+5 dB** forward gain. Place it facing the desired direction and right-click it to rotate.

### 5-Element Yagi (`radio:yagi_5`)

A receive-only directional antenna with **+7 dB** forward gain and a narrower beam than the 3-element model.

### 7-Element Yagi (`radio:yagi_7`)

A receive-only directional antenna with **+9 dB** forward gain and a narrower beam than the 5-element model.

### 11-Element Yagi (`radio:yagi_11`)

A receive-only directional antenna with **+12 dB** forward gain and the narrowest beam. All Yagis can link to placed DX3 or TechniSat Receivers, DRM Radios, FM-DX Webservers, and ADS-B Receivers; they cannot transmit.

## FM equipment

### FM Transmitter (`radio:transmitter`)

Place it, set a frequency in **kHz**, power in **watts**, and an HTTP(S) audio URL or a file from your Recorder library. The default is **101700 kHz (101.7 MHz)**. Link an antenna to broadcast. **RDS Config** sets PI, PS, RadioText, AF, clock data, and enabled RDS groups. Its **PRESETS** screen has three personal slots for settings and RDS; loading a preset leaves the placed transmitter's frequency, power, and antenna link in place.

### Radio (`radio:radio`)

A portable FM/DAB receiver. Right-click to tune FM from **65–108 MHz** (including OIRT and Japanese FM) or select a DAB channel. FM can use decoded RDS AF frequencies to follow the same PI; DAB can retune to another channel carrying the same MUX or SID. Use **MUTE** for audio and **PIN** for a compact HUD display. Keep the radio in your inventory for playback after closing its screen.

### Deepelec DP-666 (`radio:digital_radio`)

An FM-only portable radio with a light frame. Enter a frequency in tenths of a MHz with the keypad: `894` tunes **89.4 MHz**, and `1017` tunes **101.7 MHz**. Valid inputs from `650` to `1080` tune automatically. **STEP** cycles 0.1, 0.5, and 1.0 MHz steps and seek; **+** and **−** tune, **POWER** mutes, **PIN** shows the HUD, and **X** closes the screen. RDS PS and RadioText decode gradually; the clock appears only if the station sends RDS clock data.

### Deepelec DP-666 Black (`radio:black_digital_radio`)

The same FM receiver and controls as the light DP-666, with a black frame and dark screen.

### FM-DX Webserver (`radio:webserver`)

Place it and link a regular, directional, or Yagi antenna. Set its location name, background image URL, and theme color. The Tablet's Webservers app can remotely tune it to **65.000–108.000 MHz FM** or **0.150–30.000 MHz DRM**. Every viewer shares its mode and frequency. The display shows signal, SNR, PS/RadioText or DRM text, a 60-second signal graph, and the noise floor on empty frequencies. FM transmitter details and a matching DAB logo appear only after PI and part of PS decode; the **AF** view lists decoded alternatives in frequency order. Live reception updates five times per second and the graph records once per second.

## DAB and multi-band radios

### DAB Server (`radio:dab_server`)

Create up to **16 multiplexes**, each with up to **32 services**. For every service, set its SID, label, PTY (0–31), DLS text or URL, optional MOT logo URL, audio source, and bitrate. A service's bitrate must be a multiple of 8 kbps. The MUX editor shows used and remaining capacity:

| Protection | MUX capacity |
| --- | ---: |
| EEP-1A | 576 kbps |
| EEP-2A | 864 kbps |
| EEP-3A | 1152 kbps |
| EEP-4A | 1728 kbps |

The strictest linked transmitter protection determines the limit; an unlinked MUX uses EEP-3A. These limits derive from [ETSI EN 300 401](https://www.etsi.org/deliver/etsi_en/300400_300499/300401/02.01.01_20/en_300401v020101a.pdf).

### DAB/DAB+ Transmitter (`radio:dab_transmitter`)

Open **Ensemble** and choose a DAB Server and one of its MUXes directly in the transmitter screen. Set the DAB channel, power, ensemble ID, DAB or DAB+ mode, error correction, and four-digit TII code; the default channel is **11C (220352 kHz)**. Link an antenna. Its **PRESETS** screen has three personal slots for configuration, excluding frequency, power, and physical antenna link.

Transmitters using the **same server MUX**, frequency, ensemble ID, version, and error correction form a single-frequency network (SFN). Their received powers add; two equally strong members provide about **3 dB** more signal than one. Give each transmitter a distinct TII code. Legacy name-based MUX links continue until explicitly linked to a server.

### DX3 Radio (`radio:dx3_radio`)

A portable FM/DAB station-list radio. Choose **FM**, **DAB**, or **FM + DAB**, scroll the list, and sort by frequency, name, or group. FM uses the first PS frame for stable ordering while Dynamic PS remains visible; DAB entries show service names and MOT logos. Stations sharing a PI or SID group together. **PLAYER** shows programme information, MUX, codec, bitrate, and RT or DLS. **PIN** keeps station details on the HUD even after signal loss.

**PREF** cycles Prefer DAB, Prefer FM, Balanced, and Prefer audio quality. Linked services follow the same PI or SID across frequencies; an FM PI matching a DAB SID links the bands. Balanced switches as reception deteriorates. Prefer audio quality chooses DAB over available FM above **72 kbps DAB+** or **128 kbps DAB**, but can use lower bitrate DAB when FM is unavailable.

### DX3 Receiver (`radio:dx3_receiver`)

A placeable DX3 with the same station list and preference modes. Link an antenna for best reception; without one, it has a **22 dB** penalty. Its selected FM or DAB station plays for everyone within **32 blocks**, fading with distance and using Minecraft's Records/Jukebox volume. **MUTE** silences its speaker for everyone. Selection, preference, mute, and antenna link persist with the block.

### TechniSat DIGITRADIO 21 (`radio:technisat_radio`)

A portable **FM, DAB+, and DRM** receiver. **MODE** changes band; **INFO** shows programme data; **PIN** keeps the display on the HUD; **PRESET** opens the DAB or DRM service list. Arrow buttons step FM by **0.1 MHz** and DRM by **1 kHz**, and seek buttons find FM signals. Press **VOL** to mute. **FULL SCAN** searches DAB from **5A to 13F**, pausing on found MUXes; DRM has its own scan. FM has no station list. Saved DAB/DRM lists persist when the radio moves, but services that change channel require another scan.

### TechniSat Receiver (`radio:technisat_receiver`)

The placeable TechniSat uses the same controls and scans as the portable version. It saves scans with the block and plays audio for nearby players.

## DRM equipment

### DRM Transmitter (`radio:drm_transmitter`)

Broadcast on **140–30000 kHz** with a regular or directional antenna. Set power, audio URL or Recorder file, artwork URL, full and short labels, and text. The band changes automatically: **LW 140–299 kHz**, **MW 300–2999 kHz**, **SW 3000–30000 kHz**. Choose mode **A–D** and **5, 10, or 20 kHz** bandwidth: wider and less robust settings allow more bitrate but need cleaner reception. The displayed bitrate is an Opus-quality equivalent; server MP3 playback uses roughly twice that bitrate, rounded up and capped at **320 kbps**.

### DRM Radio (`radio:drm_radio`)

A placed DRM receiver that needs a linked **Loop Antenna** or Yagi. Tune in kHz to see service labels, text, artwork, mode, bandwidth, bitrate, SNR, signal, and transmitter power. It plays for players within **32 blocks**, with distance fade and Records/Jukebox volume; **MUTE** silences its speaker.

### Portable DRM Radio (`radio:portable_drm_radio`)

A handheld DRM receiver with tuning, mute, and **PIN**. It receives without a placed antenna but has an **8 dB** penalty compared with a linked loop. Keep it in inventory for continued playback. The pinned HUD shows service information and says **DRM Unavailable** if decoding stops.

LW carries continuously over long distances and works moderately underground. MW range increases at night. SW has a skip zone and a distant return, but performs poorly underground. These are gameplay propagation rules rather than a standards-compliant DRM decoder.

## Flight tracking and navigation

### ADS-B Transponder (`radio:adsb_transponder`)

Right-click to set its identifier and operator. While held, dropped, or stored in a loaded entity or block container, it broadcasts its carrier's coordinates, altitude, heading, and speed. Speed uses **1 block = 1 meter** and is shown in km/h regardless of the mod's radio-distance scale.

### ADS-B Receiver (`radio:adsb_receiver`)

Place it and link any compatible antenna to open a radar of receivable transponders in the same dimension. Select a contact for identifier, operator, coordinates, altitude, heading, speed, distance, and SNR. The radar offers **5, 10, 25, 70, 140, and 280 mod km** views and refreshes about twice per second.

### ADS-B Server (`radio:adsb_server`)

Place it and link an ADS-B Receiver with the Linking Tool. Every connected server contributes its receiver's contacts to the shared Tablet network; duplicate contacts use the strongest signal. Servers are not plotted on the Tablet map. A server at **Y=320** can add up to **30 mod km** to its linked receiver's range, while blocks above an underground server reduce range and SNR.

### Tablet (`radio:web_tablet`)

Its home screen has **Webservers** and **FlightTracking** apps. Webservers opens the shared FM-DX receivers. FlightTracking combines contacts from all linked ADS-B Servers in the selected dimension and displays received transponders and VOR transmitters on a draggable, zoomable **X/Z coordinate grid**. Hover over a marker for details. Use **HOME** to change apps. The map uses the server's distance scale; a server only contributes contacts that its linked receiver can decode.

### VOR Transmitter (`radio:vor_transmitter`)

Set an identifier and a frequency from **108.000 to 117.950 MHz** in **0.050 MHz** steps, then link a **regular Antenna**. A directional, loop, or Yagi antenna cannot transmit VOR. The Navigator shows radial and SNR when tuned to its frequency.

### DME Module (`radio:dme_module`)

Place it directly beside a VOR Transmitter in any of the six directions. It attaches automatically and enables slant-range distance in **blocks** on the Navigator. Removing the VOR detaches it.

### Navigator (`radio:navigator`)

The **ADS-B** tab lists nearby decodable transponders on a **280 mod km** radar and plots tuned VORs that have DME. Set independent **VOR 1** and **VOR 2** frequencies to see identifier, radial, SNR, transmitter position, and DME distance. **PIN VOR** keeps both readings on the HUD; the registry tab lists loaded VOR transmitters across dimensions.

VOR and ADS-B decode at **0 dB SNR** or better. Transmitter and receiver height contribute equally to range: two endpoints at **Y=62** reach about **4 mod km**, while two at **Y=640** can reach **280 mod km**. Obstruction lowers reception. The configured `kilometersPerBlock` determines how many world blocks a mod kilometer spans; it does **not** change transponder speed.

## Audio, signal, and server settings

### Audio playback

An FM transmitter, DRM transmitter, or DAB service can use an HTTP(S) stream or a Recorder file. Uploaded files loop and keep their own playback clock even with no listeners; joining later starts at the current elapsed position, including after a restart. A deleted file becomes unavailable until the station selects another source.

The server looks for FFmpeg on `PATH` and common locations. Set `RADIO_FFMPEG_PATH` or the Java property `radio.ffmpeg.path` if needed. The proxy uses the first free TCP port in **28080–28110**. Uploaded audio is sent to listeners through the Minecraft connection; live URLs also require clients to reach the proxy port and the server to reach the stream. FM is transcoded to stereo MP3 at **44.1 kHz, 128 kbps** when FFmpeg is available. DAB uses its selected MP3 bitrate; DAB+ treats its configured bitrate as an Opus-quality target and uses roughly twice that for MP3, rounded up and capped at **320 kbps**. If live proxy playback fails, radios may try the source URL directly, subject to client codec support.

### Signal behavior

- FM and DAB lose signal over distance, frequency mismatch, obstruction, and water. Antenna direction and transmitter power matter; altitude improves coverage through **Y=320**. DAB falls off faster than FM but stays clean until it loses decode.
- Blocks above a receiver cost FM **1.25 dB** each, up to 30 dB, and DAB another **1.25 dB** each. Being underwater costs FM **12 dB** and DAB **20 dB**. DAB stops decoding above **84 blocks/s** player or vehicle speed.
- DAB needs at least **3, 6, 9, or 12 dBµV** for EEP-1A through EEP-4A, as well as enough SNR. Handheld and placed DX3 DAB audio waits **1.5 seconds** of stable reception after tuning or a brief loss.
- FM audio blends with static at weak levels. FM displayed SNR reaches roughly **53 dB** near 98 dBµV; DAB approaches a **35 dB** ceiling. RDS PS and RadioText decode gradually with BER, and RDS time appears only when broadcast.

### Config and commands

NeoForge creates `config/radio-common.toml`. `kilometersPerBlock` defaults to `0.001` (one block per meter for radio range); `noiseFloorMinDbuv` and `noiseFloorMaxDbuv` default to `-5.0` and `-1.0`; `maxAudioStorageMiB` defaults to `512`. Transmitter, DAB Server, webserver, and navigation network chunks may be kept loaded while those blocks exist. Large networks can keep many chunks active.

## Build and license

Run `./gradlew build` from the project root. For development, use `./gradlew runClient` or `./gradlew runServer` (`gradlew.bat` on Windows). The JAR is written to `build/libs/`. The Gradle wrapper uses **Gradle 9.2.1** and packages MP3 Java dependencies through JarJar. `gradle.properties` currently pins `org.gradle.java.home` to a local macOS Java 21 path; change or remove it on other systems.

Radio+ code and original artwork are released under the [MIT License](LICENSE). The NeoForged MDK template files retain their separate [template license](TEMPLATE_LICENSE.txt). The project uses Mojang's official mappings; see the [mapping license](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md) and [NeoForge documentation](https://docs.neoforged.net/).
