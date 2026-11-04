# Radio Mod
**⚠️IMPORTANT: You need to have ffmpeg installed to PATH for this mod to work. And port 28080 open if you're running it on a server.**

### Roadmap
✅ - Complete
⚠️ - Partially Working (will change)
❌ - Planned, but not implemented

- ✅ FM Radio and transmitters
- ⚠️ RDS and advanced RDS for FM stations.
- ❌ Sporadic-E, Tropo and Aurorascatter.
- ⚠️ Meteorscatter
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

**Linking Tool:** The linking tool is the universal tool for linking blocks together by shift right-clicking.

**Spectrum Analyser:** A receive-only handheld spectrum and waterfall display for DRM, FM, VOR, DAB/DAB+ and ADS-B. Right-click to open it. Enter a frequency in MHz and press SET, click the spectrum, use the tuning buttons or arrow keys, or scroll over the display to tune; crossing a band edge changes mode automatically. FM/OIRT tuning uses 100 kHz steps, DAB uses channel steps, DRM uses 5 kHz, and VOR uses 50 kHz. SPAN controls the displayed frequency range; BW sets receive bandwidth. The frequency axis marks LW/MW/shortwave broadcast ranges, OIRT/Japanese/standard FM, VOR/ILS, DAB and 1090 MHz ADS-B. VOR carriers are continuous; ADS-B shows brief, randomly spaced data bursts whose frequency increases with the number of receivable transponders. Band ranges appear as red lines with inset labels along the bottom of the spectrum. Enter upper and lower signal limits in dBµV and press APPLY to set the spectrum scale and waterfall colors; leave either field blank to keep that limit automatic, or press AUTO to reset both. Clicking a received DRM carrier shows its service label, mode, bitrate and text, and plays its audio. Clicking a received DAB ensemble shows its multiplex, error correction, TII code and station list; click a station to hear it and see its bitrate and SID. FM plays its tuned station with static that follows reception quality: a narrow bandwidth adds static even on strong stations, while a wide bandwidth adds static to weaker ones. FM playback continues after closing the analyser screen while the item remains in the player's inventory or hotbar and FM is selected. On FM, PI, PS, RT, PTY and the last decoded RDS group appear at the top of the spectrum; PS and RT fill in as their groups arrive, and high BER makes decoding slower or unavailable. DAB and DRM play static only when the tuned signal is unavailable. When blocks cover the player's head, digital carriers show moving multipath peaks and deeper holes that leave bright and dark diagonal trails in the waterfall. The dashed lines mark the selected bandwidth, and the RF reading reflects how much of a station it captures. The server sends received signal and service data while the client draws the spectrum, noise and waterfall.

**FM**
- FM transmitters can be connected to antennas using the linking tool and support realistic RDS controls (by editing the groups)
- FM signal gets weaker gradually and is affected by height and if you're underground (the number and type of blocks above you matters)
- RDS is affected by the signal and its BER (Bit Error Rate) is simulated.
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
- DAB Servers are required to create multiplexes and stations. DAB transmitters can then be linked to a server to broadcast a multiplex from that server.
- DAB signals are more affected by blocks above the player than FM.

**DRM**
- DRM implementation is still in an early phase and **will contain bugs**
- DRM signals that propagate realistically and are influenced by frequency and time.

**VOR/DME**
- VOR blocks that allow for navigation using a Navigator item.
- DME modules that attach to the VOR transmitters and allow for a distance reading to be transmitted alongside the directional signal.
- Works great with plane mods and large worlds.

**ADS-B**
- ADS-B transponders and recievers that allow for flight tracking. (ADS-B transponders can be stored in an entity's storage and will still work)

**Radios**
- Deepelec DP666 (black and white versions, for FM)
- DX3 Radio/Reciever (not based on a real radio, for FM/DAB)
- DRM Radio/Reciever (for DRM)
- Technisat DIGITRADIO 21 (based on the real version, for FM/DAB/DRM)

**NOTE:** This project was made with the help of an AI agent.





























## Squaremap (optional server integration)

Squaremap is not needed to play or host Radio Mod. If a compatible Squaremap build for NeoForge 1.21.1 is installed on a server, Radio Mod adds a **Radio transmitters** layer to each enabled Squaremap world. Transmitters within 50 blocks are represented by one marker listing all of their FM, DAB/DAB+ and DRM services. The marker size follows the highest transmitter power at the site; the supplied low, medium and high power icons are bundled in the mod, including selected variants.

The Squaremap page gains a station panel with simultaneous country, mode, frequency, PI/SID, minimum power and name filters. Click a transmitter icon on the map to list only its stations. ERP is displayed in kW with three decimal places (for example, 1.000 for 1 kW); click a station's ERP to show or hide that transmitter's coverage. FM coverage uses contours for at least 5, 25, 50 and 75 dBµV in different colors; DAB coverage is a single purple contour at the multiplex's error-correction threshold. Coverage is calculated on the server from transmitter power, direction and height, using Y=100 as the map's receiving height. Calculated contours are cached in the world save and reused after a server restart when their transmitter and antenna settings are unchanged. The server rebuilds Squaremap radio markers after transmitter, antenna, DAB server or registry changes, coalescing rapid edits into one update; it resamples coverage only when its signal inputs change. An open browser checks for updated station data every 20 seconds. No Squaremap files are written when the optional mod is absent.
