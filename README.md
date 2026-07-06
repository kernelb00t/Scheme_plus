# Scheme Plus
 
A lightweight [Mindustry](https://mindustrygame.github.io/) v8 mod that extends the built-in schematic info dialog with **resource consumption and production data**.
 
## What it does
 
When you open a schematic's info panel in-game, Mindustry only shows you the power draw/output at a glance. Scheme Plus adds a compact breakdown of every item and liquid the schematic consumes or produces per second, alongside the existing power stats — so you can evaluate a schematic's resource footprint without loading it onto the map first.
 
The dialog shows two columns:
 
- **Consumption** — power draw and negative item/liquid flow rates (red)
- **Production** — power output and positive item/liquid flow rates (accent color)
Rates are computed per-second across every crafter block in the schematic, aggregated by resource type.