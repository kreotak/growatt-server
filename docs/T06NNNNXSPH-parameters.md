# Growatt SPH Hybrid Inverter — Parameter Reference

Layout: `T06NNNNXSPH`  
Protocol: `06` (XOR-encrypted)  
Applies to: Growatt SPH series single-phase hybrid inverters (e.g. SPH3000–SPH6000TL BL-UP)

---

## Identity

| Field | Home Assistant name | Unit | Description |
|---|---|---|---|
| `datalogserial` | — | — | Serial number of the WiFi datalogger dongle (ShineWiFi-X / ShineLink) |
| `pvserial` | — | — | Serial number of the inverter itself |

---

## PV Input

| Field | Home Assistant name | Unit | Description |
|---|---|---|---|
| `pvpowerin` | PV Power In | W | Total DC input power from all PV strings combined |
| `pv1voltage` | PV String 1 Voltage | V | DC voltage on PV string 1 |
| `pv1current` | PV String 1 Current | A | DC current on PV string 1 |
| `pv1watt` | PV String 1 Power | W | DC power on PV string 1 (`pv1voltage × pv1current`) |
| `pv2voltage` | PV String 2 Voltage | V | DC voltage on PV string 2 |
| `pv2current` | PV String 2 Current | A | DC current on PV string 2 |
| `pv2watt` | PV String 2 Power | W | DC power on PV string 2 (`pv2voltage × pv2current`) |

---

## AC Output / Grid

| Field | Home Assistant name | Unit | Description |
|---|---|---|---|
| `pvpowerout` | PV Power Output | W | Net AC output power of the inverter, **signed** — negative means the inverter is drawing from the grid |
| `pvfrequentie` | Grid Frequency | Hz | Grid frequency |
| `pvgridvoltage` | Grid Voltage L1 | V | Grid voltage on phase L1 |
| `pvgridcurrent` | Grid Current L1 | A | Grid current on phase L1 |
| `pvgridpower` | Grid Power L1 | W | Grid power on phase L1 |
| `pvgridvoltage2` | Grid Voltage L2 | V | Grid voltage on phase L2 (0 on single-phase systems) |
| `pvgridcurrent2` | Grid Current L2 | A | Grid current on phase L2 |
| `pvgridpower2` | Grid Power L2 | W | Grid power on phase L2 |
| `pvgridvoltage3` | Grid Voltage L3 | V | Grid voltage on phase L3 (0 on single-phase systems) |
| `pvgridcurrent3` | Grid Current L3 | A | Grid current on phase L3 |
| `pvgridpower3` | Grid Power L3 | W | Grid power on phase L3 |

---

## Power Flows (hybrid-specific)

| Field | Home Assistant name | Unit | Description |
|---|---|---|---|
| `pdischarge1` | Battery Discharge Power | W | Power flowing **out of** the battery to loads or grid |
| `p1charge1` | Battery Charge Power | W | Power flowing **into** the battery |
| `pactouserr` | Power to User | W | Power delivered to household loads from inverter, phase L1 |
| `pactousertot` | Power to User Total | W | Total power to loads across all phases — use this on single-phase |
| `pactogridr` | Power to Grid | W | Power exported to grid, phase L1 |
| `pactogridtot` | Power to Grid Total | W | Total power exported to grid across all phases |
| `plocaloadr` | Local Load | W | Total consumption measured at the meter point, phase L1 |
| `plocaloadtot` | Local Load Total *(power)* | W | Total consumption across all phases — same as `plocaloadr` on single-phase |

> **Note:** `plocaloadtot` shows *instantaneous watts*, not kWh. Home Assistant may list both `plocaloadtot` (W) and `elocalload_tot` (kWh) under "Local Load Total" — they are different quantities.

---

## Battery

| Field | Home Assistant name | Unit | Description |
|---|---|---|---|
| `SOC` | Battery SOC | % | Battery state of charge |
| `vbat` | Battery Voltage | V | Battery terminal voltage |
| `bat_dsp` | — | V | Battery voltage as read by the DSP — may differ slightly from `vbat` due to measurement point |
| `batterytype` | — | code | Battery chemistry: `0` = lead-acid, `1` = lithium |

---

## Temperatures

| Field | Home Assistant name | Unit | Description |
|---|---|---|---|
| `pvtemperature` | Inverter Temperature | °C | Inverter heat-sink / ambient internal temperature |
| `pvipmtemperature` | IPM Temperature | °C | Intelligent Power Module (IGBT driver) temperature |
| `pvboosttemp` | Boost Temperature | °C | DC-DC boost converter module temperature |

---

## DC Bus

| Field | Home Assistant name | Unit | Description |
|---|---|---|---|
| `pbusvolt` | P Bus Voltage | V | Positive DC bus link voltage inside the inverter |
| `spbusvolt` | — | V | SP (split-phase) secondary bus voltage |

---

## Energy Counters — Today

| Field | Home Assistant name | Unit | Description |
|---|---|---|---|
| `eactoday` / `pvenergytoday` | AC Energy Today | kWh | AC energy converted by the inverter from DC sources (PV + battery discharge) today — grid pass-through is **not** included |
| `epv1today` | PV1 Energy Today | kWh | Energy harvested from PV string 1 today |
| `epv2today` | PV2 Energy Today | kWh | Energy harvested from PV string 2 today |
| `eacharge_today` | AC Charge Today | kWh | Energy drawn from the grid to charge the battery today |
| `eharge1_tod` | Battery Charge Today | kWh | Total battery charge energy today (PV + grid sources) |
| `edischarge1_tod` | Battery Discharge Today | kWh | Battery discharge energy today |
| `etouser_tod` | Energy to User Today | kWh | Total energy delivered to household loads today |
| `etogrid_tod` | Energy to Grid Today | kWh | Energy exported to the grid today |
| `elocalload_tod` | Local Load Today | kWh | Total local consumption energy today |

---

## Energy Counters — Lifetime

| Field | Home Assistant name | Unit | Description |
|---|---|---|---|
| `eactotal` | AC Energy Total | kWh | Total AC energy converted by the inverter from DC sources (PV + battery discharge) since installation — grid pass-through is **not** included |
| `epvtotal` | PV Energy Total | kWh | Total PV harvest from all strings since installation |
| `epv1total` | PV1 Energy Total | kWh | Total energy from PV string 1 since installation |
| `epv2total` | PV2 Energy Total | kWh | Total energy from PV string 2 since installation |
| `eacharge_total` | AC Charge Total | kWh | Total energy drawn from grid to charge battery since installation |
| `eharge1_tot` | Battery Charge Total | kWh | Total battery charge energy since installation |
| `edischarge1_tot` | Battery Discharge Total | kWh | Total battery discharge energy since installation |
| `etouser_tot` | Energy to User Total | kWh | Total energy delivered to loads since installation |
| `etogrid_tot` | Energy to Grid Total | kWh | Total energy exported to grid since installation |
| `elocalload_tot` | Local Load Total *(energy)* | kWh | Total local consumption since installation |
| `totworktime` | — | h | Total inverter operating hours (raw value in 0.5-second units, divided by 7200) |

---

## Status & Faults

| Field | Home Assistant name | Unit | Description |
|---|---|---|---|
| `pvstatus` | — | code | Inverter system status |
| `uwsysworkmode` | — | code | Current work mode: `0`=waiting, `1`=normal, `4`=bypass, `5`=fault, `6`=charge, `7`=discharge |
| `systemfaultword0`–`7` | — | bits | Fault status registers (bit-fields); `0` = no fault on that register |
| `spdspstatus` | — | code | SP DSP status; ignore the `÷10` decimal — raw value `5` → displayed as `0.5`, meaning status code `5` |

---

## Notes

- Fields prefixed with `#` (e.g. `#pactousers`) are excluded from the default API output (`incl: no`). They can be enabled by setting `includeAll: true` in the server config.
- On a **single-phase** installation L2 and L3 fields are always zero.
- `pvpowerout` uses type `numx` (signed integer) — it goes negative when the inverter imports power rather than exports.
- `spdspstatus` has `divide: 10` in the layout which produces fractional values like `0.5`. Treat the displayed value as a status code divided by 10; only whole-number raw values carry meaning.
