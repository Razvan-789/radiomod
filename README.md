# Radio Mod
**⚠️IMPORTANT: You need to have ffmpeg installed to PATH for this mod to work. And port 28080 open if you're running it on a server.**

### Roadmap
✅ - Complete
⚠️ - Partially Working (will change)
❌ - Planned, but not implemented

- ✅ FM Radio and transmitters
- ✅ RDS and advanced RDS for FM stations.
- ✅ Sporadic-E, Tropo and Aurorascatter propagation.
- ✅ Meteorscatter
- ❌ LW, MW, SW AM radio.
- ⚠️ DRM30 radio with realistic signal propagation.
- ✅ DAB/DAB+ Radio
- ⚠️ ADS-B
- ⚠️ VOR-DME
- ❌ Support for other versions. (mainly 1.20.1)
- ⚠️ Radios (you can never have enough, more to come)

Radio Mod adds a radio system to Minecraft that currently includes FM, DAB/DAB+, DRM and ADS-B.

### Tools
Radio Mod adds transmitter blocks, radios and other items. It also calculates each transmitter's signal based on the player's position. (this includes the distance from a transmitter, height and if the player is underground)

**Linking Tool:** Use it to connect antennas to transmitters and receivers, DAB transmitters to servers, and ADS-B servers to receivers. While holding it in either hand, nearby confirmed links appear as colored lines: cyan for antenna links and amber for server links. The lines disappear as soon as you put the tool away.

**Radio Display:** Place one to three full display blocks side by side, facing the same way, for a single joined screen. Use the Linking Tool on a placeable DX3, TechniSat, Pure The Bug or DRM radio, then click any display in the row. The link stays local on moving contraptions. Right-click a display to choose the single-block view (logo, programme and PTY, or signal) and toggle individual fields. A two-block screen shows programme name, scrolling RT/DLS or RT+/DLS+, PTY and signal bars. A three-block screen also shows the logo, signal level and separate title and artist when RT+/DLS+ is available. Text and signal readings follow the linked radio's current reception; the server sends changes to clients.

**Transmitter site names:** Open any FM, DAB or DRM transmitter and press **SITE NAME** to name its location. The name is shared by transmitters connected through sites no more than 50 blocks apart, including transmitters of different types. Newly added transmitters at that site inherit the name. The name appears in the FM-DX Webserver identification field after PI and PS decode, on Squaremap's transmitter marker and station panel, and in the TX Registry. Site names stay with the transmitter location when presets are loaded; clearing the name makes the site unnamed.

**Spectrum Analyser:** A receive-only handheld spectrum and waterfall display for DRM, FM, VOR, DAB/DAB+ and ADS-B. Right-click to open it. Enter a frequency in MHz and press SET, click the spectrum, use the tuning buttons or arrow keys, or scroll over the display to tune; crossing a band edge changes mode automatically. FM/OIRT tuning uses 100 kHz steps, DAB uses channel steps, DRM uses 5 kHz, and VOR uses 50 kHz. SPAN controls the displayed frequency range; BW sets receive bandwidth. The frequency axis marks LW/MW/shortwave broadcast ranges, OIRT/Japanese/standard FM, VOR/ILS, DAB and 1090 MHz ADS-B. VOR carriers are continuous; ADS-B shows brief, randomly spaced data bursts whose frequency increases with the number of receivable transponders. Band ranges appear as red lines with inset labels along the bottom of the spectrum. Enter upper and lower signal limits in dBµV and press APPLY to set the spectrum scale and waterfall colors; leave either field blank to keep that limit automatic, or press AUTO to reset both. Clicking a received DRM carrier shows its service label, mode, bitrate and text, and plays its audio. Clicking a received DAB ensemble shows its multiplex, error correction, TII code and station list; click a station to hear it and see its bitrate and SID. FM plays its tuned station with static that follows reception quality: a narrow bandwidth adds static even on strong stations, while a wide bandwidth adds static to weaker ones. FM playback continues after closing the analyser screen while the item remains in the player's inventory or hotbar and FM is selected. On FM, PI, PS, RT, PTY and the last decoded RDS group appear at the top of the spectrum; PS and RT fill in as their groups arrive at 11.4 groups per second, and high BER makes decoding slower or unavailable. When decoding stops, the last received RDS remains visible in gray until a different frequency is tuned. DAB and DRM play static only when the tuned signal is unavailable. When blocks cover the player's head, digital carriers show moving multipath peaks and deeper holes that leave bright and dark diagonal trails in the waterfall. The dashed lines mark the selected bandwidth, and the RF reading reflects how much of a station it captures. The server sends received signal and service data while the client draws the spectrum, noise and waterfall.

**Tablet — Propagation app:** Open the tablet and choose **PROPAGATION** to see the current meteor rate and FM openings, Sporadic-E MUF and opening status, tropo strength and ducting, and aurorascatter intensity. The readout updates once per second. The separate Meteor Forecaster item has been removed.

**Propagation:** Sporadic-E affects the whole FM band when an opening's maximum usable frequency (MUF) reaches it. Openings are scheduled for 5–15 real minutes during Minecraft daytime/evening; distant signals between 600 and 1,200 configured mod kilometres waver on a roughly 3.5-second random pattern and usually peak around 20–25 dBµV, rarely 40 dBµV. Tropo affects FM and DAB in clear weather, with its strongest lift near 06:00 Minecraft time. Mild lift is common; rare ducts can extend reception up to 200 mod kilometres and last 3–4 Minecraft days. Aurorascatter affects FM and DAB throughout full-moon nights in cold or snowy biomes, including snowy peaks, and produces weak, unstable signals that waver on a roughly 3-second random pattern, up to roughly 5–8 dBµV. Its intensity varies by night when set to random. The tablet reports whether the player's current biome qualifies. The mod's kilometres-per-block setting controls all these distances. Transmitter chunks must be loaded for their signals to be received.

Operators can set each dimension's conditions with `/radiomod meteorscatter rate <0–1200>`, `/radiomod tropo rate <0–11>`, `/radiomod sporadic rate <0 or 60–108>`, and `/radiomod aurora rate <0–10>`. Zero selects random conditions. Meteor values are meteors per real hour; sporadic values are MUF in MHz; tropo and aurora values are strength and intensity. Leave off `rate` to read the current status. Rate changes require permission level 2.

Use `/radiomod sporadic enable 1` or `/radiomod aurora enable 1` to force an opening continuously. These commands override the natural schedule and time, weather, moon and biome requirements; distance and signal fading still apply. With a random MUF setting, forced Sporadic-E uses 108 MHz so the FM band opens. Use `enable 0` to return to random openings. The setting persists per dimension and appears as **FORCED** in the tablet's Propagation app.

The Spectrum Analyser shows the selected DAB or DRM service's LIC language in its service information.

**FM**
- FM transmitters can be connected to antennas using the linking tool and support realistic RDS controls (by editing the groups)
- FM, DAB and DRM transmitters have three personal presets. The preset picker shows the saved FM PS and PI, DAB ensemble name and ID, or DRM service and short labels. Loading a preset keeps the transmitter's frequency, power and antenna link.
- Beside the stream field, **HISTORY** opens your previously used streams with the FM PS/PI, DAB label/SID or DRM service/short label where each was used. Select an entry to reuse its URL. This history is saved per player in the world. FM transmitters also have **SYNC STREAM**: it copies the most commonly configured stream to all loaded FM transmitters with the same PI, across dimensions. If streams are tied, it favors the current transmitter's stream.
- FM signal gets weaker gradually and is affected by height and if you're underground (the number and type of blocks above you matters)
- RDS is affected by the signal and its BER (Bit Error Rate) is simulated.
- In RDS group 0A, set the network's PI and press **SYNC NETWORK** beside the AF list. The server finds FM transmitters with that PI across all dimensions, sorts their distinct frequencies, and writes the same AF list to each transmitter. Repeat the sync after adding a transmitter or changing one of its frequencies.
- In the 2A RT URL field, paste a plain text URL, a TuneIn `nowPlaying` JSON URL, or a TuneIn station page link. Saving a URL enables RT URL mode automatically. The server reads `Header.Subtitle` in the background every 10 seconds; SSE sources with a JSON `title` field also work.
- When the FM transmitter has an audio stream URL and no manual RT or RT URL, it reads ICY song text directly from that configured stream. The transmitter screen reports whether ICY metadata is available. `StreamTitle` is used as broadcast; separate `artist` and `title` fields become `Artist - Title`. The check repeats every 10 seconds. Manual RT and RT URLs take priority; **Use audio stream ICY** explicitly selects the stream when you want to override them.
- To broadcast RT+, open RDS group 11A, 12A or 13A and choose **Use for RT+**. This enables the needed 2A, 3A and selected group. When the decoded RadioText contains `Artist - Title`, the DX3 and TechniSat player screens show separate artist and title fields. The 3A page can disable RT+ or change its group.
- RDS Groups:

| Group | Usage |
| -------- | -------- | 
| 0A    | PI, PS, AF, PTY, TP | 
| 1A    | ECC, PIN, LIC | 
| 2A | RT |
| 3A | ODA (Not in use yet) |
| 4A | CT |
| 10A | PTYN |
| 15A | Long PS (works, but it's not implemented in any radios yet) |

These are all based on real RDS groups. More information about RDS groups can be found on Wikipedia.

**DAB**
- DAB Servers are required to create multiplexes and stations. Each station has an optional LIC language field. DAB transmitters can then be linked to a server to broadcast a multiplex from that server.
- DAB signals are more affected by blocks above the player than FM.
- In a station's DLS URL mode, the DLS URL field accepts the same plain text, TuneIn JSON, TuneIn station page, and SSE sources as FM RT.
- When a DAB station has an audio stream URL and no manual DLS or DLS URL, it reads ICY song text directly from that configured stream. The DAB server screen reports whether the stream provides ICY metadata. `StreamTitle` is used directly; separate `artist` and `title` fields become `Artist - Title`. Manual DLS and DLS URLs take priority; **Use ICY** explicitly selects the stream when you want to override them.
- Each station has a DLS+ switch. When enabled, DLS in `Artist - Title` form produces separate artist and title fields on the DX3 and TechniSat player screens. The setting is saved with the station.

**DRM**
- DRM implementation is still in an early phase and **will contain bugs**
- DRM signals that propagate realistically and are influenced by frequency and time.
- MW range uses the server's configured kilometres per block. At 0.05 km/block, a 50 kW transmitter around 909 kHz with a linked antenna reaches roughly 4,000 blocks by day and 15,000–18,000 blocks at night in clear conditions; decoder mode, underground cover, antenna direction and receiver noise change the result. LW groundwave is longer and continuous.
- LW and SW also use the configured distance scale. Lower LW frequencies travel farther, with a modest nighttime improvement. SW has a short local groundwave range, a skip zone, then a distant skywave region whose onset and strength depend on frequency and time of day.
- DRM transmitters have an optional LIC language field on their Service / Audio page.

**VOR/DME**
- VOR blocks that allow for navigation using a Navigator item.
- DME modules that attach to the VOR transmitters and allow for a distance reading to be transmitted alongside the directional signal.
- Works great with plane mods and large worlds.

**ADS-B**
- ADS-B transponders and recievers that allow for flight tracking. (ADS-B transponders can be stored in an entity's storage and will still work)

**Radios**
- Deepelec DP666 (black and white versions, for FM)
- DX3 Radio/Reciever (not based on a real radio, for FM/DAB); the player tab shows the selected station's LIC language. Its PIN overlay shows decoded RT+/DLS+ song titles in white with artists below in gray.
- DRM Radio/Reciever (for DRM)
- Technisat DIGITRADIO 21 (based on the real version, for FM/DAB/DRM); INFO shows the selected service's LIC language. Its handheld and placed-receiver PIN overlays show decoded RT+/DLS+ titles in white with artists below in gray.
- Pure The Bug (placeable DAB-only speaker): place it and right-click to open its display. Press SCAN to find regular DAB services from channels 5A–13F, MENU to view the saved station list, arrows and ENTER to select, and MUTE to silence the speaker. Its display shows the service label, DLS, channel, bitrate, signal bars and time. It ignores FM and DAB+ broadcasts.

Placeable DX3, TechniSat, Pure The Bug and DRM receivers can receive on Create: Aeronautics airships and other Sable sublevels. Their speaker audio follows the moving block, and FM static follows signal quality. Reception uses the radio or linked antenna's current world position; antenna links remain local to the contraption. ADS-B transponders stored in contraption chests broadcast from the chest's current world position, refreshed every two game seconds. Aeronautics and Sable are optional. This compatibility covers reception and speaker audio; moving transmitters are not yet supported.
- Sony XDR-S41D (white and black handheld versions, for FM/DAB): right-click to open its blue display. DAB/FM switches modes. On FM, the arrows tune in 100 kHz steps and AUTO TUNE seeks the next station. On DAB, AUTO TUNE scans channels 5A–13F, pauses briefly at each receivable multiplex, counts services, and opens a selectable station list; MENU reopens the saved list. The Sony stays on its selected DAB channel and does not automatically switch to a linked channel. FM alternative-frequency switching is disabled. VOL mutes. FM displays decoded PS and RT; DAB displays the selected service label and DLS. Its information screen shows the broadcast LIC language.

**NOTE:** This project was made with the help of an AI agent.





























## Squaremap (optional server integration)

Squaremap is not needed to play or host Radio Mod. If a compatible Squaremap build for NeoForge 1.21.1 is installed on a server, Radio Mod adds a **Radio transmitters** layer to each enabled Squaremap world. Transmitters within 50 blocks are represented by one marker; click it to see its FM, DAB/DAB+ and DRM services in the Radio transmitters panel. The marker size follows the highest transmitter power at the site; the supplied low, medium and high power icons are bundled in the mod, including selected variants.

The Squaremap page gains a station panel with simultaneous country, mode, frequency, PI/SID, minimum power and name filters. Click a frequency or PI/SID in the table to filter map transmitters by that value. ERP is displayed in kW with three decimal places (for example, 1.000 for 1 kW); click a station's ERP to show or hide that transmitter's coverage. Click **Network** to show coverage from every transmitter with the same PI/SID, then use **FM coverage** or **DAB coverage** to switch modes. DAB rows have a light-blue background and show the station's MOT logo when available. Their **Services** button opens an ensemble table with status, label, PTY, coding, bitrate, protection, SVCID and MOT Slideshow/DLS+ remarks; the play button opens a public HTTP(S) stream in a new browser tab. Uploaded local audio has no browser link. FM coverage uses contours for at least 5, 25, 50 and 75 dBµV in different colors; DAB coverage is a single purple contour at the multiplex's error-correction threshold. Coverage is calculated on the server from transmitter power, direction and height, using Y=100 as the map's receiving height. Calculated contours are cached in the world save and reused after a server restart when their transmitter and antenna settings are unchanged. The server rebuilds Squaremap radio markers after transmitter, antenna, DAB server or registry changes, coalescing rapid edits into one update; it resamples coverage only when its signal inputs change. An open browser checks for updated station data every 20 seconds. No Squaremap files are written when the optional mod is absent.
