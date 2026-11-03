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

**Spectrum Analyser:** A receive-only handheld display for FM, DAB and DRM. Right-click to open its live spectrum and waterfall. BAND switches radio systems; TUNE changes the center frequency; SPAN controls the displayed frequency range; BW changes the selected receive bandwidth. The left/down and right/up arrow keys tune down and up by 100 kHz on FM/OIRT, one channel on DAB, or 5 kHz on DRM. Click the spectrum to tune to a frequency, or enter a frequency in MHz and press SET. Enter upper and lower signal limits in dBµV and press APPLY to set the spectrum scale and waterfall colors; leave either field blank to keep that limit automatic, or press AUTO to reset both. FM plays its tuned station with static that follows reception quality: a narrow bandwidth adds static even on strong stations, while a wide bandwidth adds static to weaker ones. FM playback continues after closing the analyser screen while the item remains in the player's inventory or hotbar and FM is selected. On FM, PI, PS, RT, PTY and the last decoded RDS group appear at the top of the spectrum; PS and RT fill in as their groups arrive, and high BER makes decoding slower or unavailable. DAB and DRM play static only when the tuned signal is unavailable. When blocks cover the player's head, digital carriers show moving multipath peaks and deeper holes that leave bright and dark diagonal trails in the waterfall. The dashed lines mark the selected bandwidth, and the RF reading reflects how much of a station it captures. The server sends received transmitter strengths, RDS readings and overhead-block count while the client draws the moving spectrum, noise and waterfall.

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




























