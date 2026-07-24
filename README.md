# Scheme+ (Scheme Plus)

Scheme Plus is a Java mod for Mindustry v8 (build 158+) that extends the built-in schematic information dialog (`SchematicsDialog`). The mod calculates and displays a detailed breakdown of resource consumption, production rates, power flow, fuel choices, and modifiers directly in the schematic preview window.

---

## Features

### Consumption & Production Tables
- Consumption: Power draw (in power/sec) and negative item/liquid flow rates (`-X/s`).
- Production: Power production (in power/sec) and positive item/liquid output rates (`+X/s`).
- Probabilistic Outputs: Exact output rates for chance-based blocks (e.g., Separator) alongside output probabilities (`~X/s (N%)`).
- Pumps: Calculated output rates for generic and configured liquid pumps.

### Drill Rates
- Calculates mining rates for unconfigured drills.
- Full support for all mineable floor ores and resources.

### Fuel Options
- Displays fuel options for generators with accurate burn durations calculated via fuel efficiency multipliers and flammability metrics.

### Modifiers & Boosts
- Dedicated breakdown table for support and modifier blocks:
  - Overdrives and Domes - phase fabric and silicon requirements.
  - Menders and Regenerators - silicon, phase fabric, and hydrogen consumption.
  - Force Projectors - water and cryofluid coolant consumption rates alongside phase fabric range boosts.
  - Drill Boosts - liquid boosting rates and intensity multipliers.

---

## Building and Installation

### Requirements
- JDK 17+
- Mindustry v8 (build 158+)

### Building from Source
To build the mod and automatically install it into your local Mindustry mods directory, run:

```bash
./gradlew mjar
```

The compiled mod JAR will be placed into your system's Mindustry mods folder.

---

## Issues & Bug Reports

If you encounter any bugs, calculation discrepancies, or have feature suggestions, please open an Issue on the GitHub repository.

---

## License & Authorship

- Author: Munline
- Target Version: Mindustry v8 (build 158+)