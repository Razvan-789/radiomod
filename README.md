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




























