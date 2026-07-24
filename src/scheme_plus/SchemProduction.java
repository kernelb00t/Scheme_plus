package scheme_plus;

import arc.struct.*;
import mindustry.Vars;
import mindustry.content.Liquids;
import mindustry.game.Schematic;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.consumers.*;

import java.lang.reflect.Field;

public class SchemProduction {

    public static class GeneratorFuelGroup {
        public Block generator;
        public int count = 0;
        public ObjectFloatMap<Item> itemRates = new ObjectFloatMap<>();
    }

    public static class DrillOptionGroup {
        public Block drill;
        public int count = 0;
        public ObjectFloatMap<Item> itemRates = new ObjectFloatMap<>();
    }

    public static class OptionalBoostGroup {
        public Block block;
        public int count = 0;
        public ObjectFloatMap<Item> itemRates = new ObjectFloatMap<>();
        public ObjectFloatMap<Liquid> liquidRates = new ObjectFloatMap<>();
        public ObjectFloatMap<Liquid> liquidMultipliers = new ObjectFloatMap<>();
    }

    public static class Result {
        public ObjectFloatMap<Item> items = new ObjectFloatMap<>();
        public ObjectFloatMap<Liquid> liquids = new ObjectFloatMap<>();

        // Probabilistic outputs (e.g., Separator)
        public ObjectFloatMap<Item> probItems = new ObjectFloatMap<>();
        public ObjectFloatMap<Item> probItemChances = new ObjectFloatMap<>(); // item -> chance percentage

        // Generic pump speed when liquid config is missing
        public float genericPumpSpeed = 0f;

        // Generator fuel options
        public Seq<GeneratorFuelGroup> generatorFuels = new Seq<>();

        // Drill mining options (when ore config is missing)
        public Seq<DrillOptionGroup> drillOptions = new Seq<>();

        // Optional liquid/item boosts and modifier blocks (e.g., Water for Drills, Phase for Overdrives/Domes)
        public Seq<OptionalBoostGroup> optionalBoosts = new Seq<>();
    }

    private static ObjectSet<Item> mineableItems = null;

    private static boolean isMineableItem(Item item) {
        if (item.hardness > 0) return true;
        if (mineableItems == null) {
            mineableItems = new ObjectSet<>();
            if (Vars.content != null && Vars.content.blocks() != null) {
                for (Block b : Vars.content.blocks()) {
                    if (b.itemDrop != null) {
                        mineableItems.add(b.itemDrop);
                    }
                }
            }
        }
        return mineableItems.contains(item);
    }

    public static boolean isModifierBlock(Block block) {
        if (block instanceof OverdriveProjector || block instanceof MendProjector 
                || block instanceof ForceProjector || block instanceof RegenProjector) {
            return true;
        }
        String name = block.name.toLowerCase();
        return name.contains("overdrive") || name.contains("mend") || name.contains("force") 
                || name.contains("regen") || name.contains("dome") || name.contains("shield");
    }

    public static Result compute(Schematic schem) {
        Result r = new Result();
        ObjectMap<Block, GeneratorFuelGroup> genGroups = new ObjectMap<>();
        ObjectMap<Block, DrillOptionGroup> drillGroups = new ObjectMap<>();
        ObjectMap<Block, OptionalBoostGroup> boostGroups = new ObjectMap<>();

        for (var tile : schem.tiles) {
            Block block = tile.block;
            if (block == null) continue;

            // Exclude unit-related blocks completely
            if (block instanceof mindustry.world.blocks.units.UnitBlock 
                    || block instanceof mindustry.world.blocks.units.UnitFactory 
                    || block instanceof mindustry.world.blocks.units.Reconstructor 
                    || block instanceof mindustry.world.blocks.units.UnitAssembler) {
                continue;
            }

            float craftTime = getCraftTime(block, tile);
            boolean customItemConsumersProcessed = false;

            // --- MODIFIER / BOOST BLOCKS (Overdrives, Domes, Menders, Shields, Regenerators) ---
            if (isModifierBlock(block)) {
                OptionalBoostGroup group = boostGroups.get(block);
                if (group == null) {
                    group = new OptionalBoostGroup();
                    group.block = block;
                    group.count = 1;
                    boostGroups.put(block, group);

                    if (block.consumers != null) {
                        for (Consume c : block.consumers) {
                            if (c instanceof ConsumeItems ci) {
                                for (ItemStack in : ci.items) {
                                    float rate = (in.amount / craftTime) * 60f;
                                    group.itemRates.put(in.item, rate);
                                }
                            } else if (c instanceof ConsumeLiquid cl) {
                                if (cl.liquid != null) {
                                    group.liquidRates.put(cl.liquid, cl.amount * 60f);
                                }
                            } else if (c instanceof ConsumeLiquids cls) {
                                for (LiquidStack in : cls.liquids) {
                                    group.liquidRates.put(in.liquid, in.amount * 60f);
                                }
                            } else if (c instanceof ConsumeLiquidFilter clf) {
                                float rate = clf.amount * 60f;
                                if (Vars.content != null && Vars.content.liquids() != null) {
                                    for (Liquid liq : Vars.content.liquids()) {
                                        if (clf.filter.get(liq)) {
                                            group.liquidRates.put(liq, rate);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    group.count++;
                }
                customItemConsumersProcessed = true;
            }

            // --- PRODUCTION ---
            if (block instanceof Separator separator) {
                float time = separator.craftTime > 0 ? separator.craftTime : 1f;
                if (separator.results != null && separator.results.length > 0) {
                    int totalWeight = 0;
                    for (ItemStack out : separator.results) {
                        totalWeight += out.amount;
                    }
                    if (totalWeight > 0) {
                        for (ItemStack out : separator.results) {
                            float chance = (float) out.amount / totalWeight;
                            float rate = chance / time * 60f; // 1 item of out.item per selection
                            r.probItems.increment(out.item, 0f, rate);
                            r.probItemChances.put(out.item, chance * 100f);
                        }
                    }
                }
            } else if (block instanceof GenericCrafter crafter) {
                float time = crafter.craftTime > 0 ? crafter.craftTime : 1f;
                if (crafter.outputItems != null) {
                    for (ItemStack out : crafter.outputItems) {
                        r.items.increment(out.item, 0f, out.amount / time * 60f);
                    }
                } else if (crafter.outputItem != null) {
                    r.items.increment(crafter.outputItem.item, 0f, crafter.outputItem.amount / time * 60f);
                }

                if (crafter.outputLiquids != null) {
                    for (LiquidStack out : crafter.outputLiquids) {
                        r.liquids.increment(out.liquid, 0f, out.amount * 60f);
                    }
                } else if (crafter.outputLiquid != null) {
                    r.liquids.increment(crafter.outputLiquid.liquid, 0f, crafter.outputLiquid.amount * 60f);
                }
            } else if (block instanceof SolidPump solidPump) {
                if (solidPump.result != null) {
                    r.liquids.increment(solidPump.result, 0f, solidPump.pumpAmount * 60f);
                }
            } else if (block instanceof Pump pump) {
                if (tile.config instanceof Liquid liquid) {
                    float rate = pump.pumpAmount * 60f * (pump.size * pump.size);
                    r.liquids.increment(liquid, 0f, rate);
                } else if (pump instanceof SolidPump sp && sp.result != null) {
                    // Handled above
                } else {
                    float rate = pump.pumpAmount * 60f * (pump.size * pump.size);
                    r.genericPumpSpeed += rate;
                }
            } else if (block instanceof ThermalGenerator thermal) {
                if (thermal.outputLiquid != null) {
                    r.liquids.increment(thermal.outputLiquid.liquid, 0f, thermal.outputLiquid.amount * 60f);
                }
            } else if (block instanceof ConsumeGenerator gen) {
                if (gen.outputLiquid != null) {
                    r.liquids.increment(gen.outputLiquid.liquid, 0f, gen.outputLiquid.amount * 60f);
                }
            } else if (block instanceof Drill drill) {
                if (tile.config instanceof Item item) {
                    float hardness = item.hardness > 0 ? item.hardness : 1f;
                    float timePerItem = drill.drillTime + hardness * drill.hardnessDrillMultiplier;
                    float itemsPerSec = (60f / timePerItem) * (drill.size * drill.size);
                    r.items.increment(item, 0f, itemsPerSec);
                } else {
                    // Unconfigured drill -> Add to DrillOptionGroup
                    DrillOptionGroup group = drillGroups.get(block);
                    if (group == null) {
                        group = new DrillOptionGroup();
                        group.drill = block;
                        group.count = 1;
                        drillGroups.put(block, group);

                        if (Vars.content != null && Vars.content.items() != null) {
                            for (Item item : Vars.content.items()) {
                                if (isMineableItem(item) && item.hardness <= drill.tier) {
                                    float hardness = item.hardness > 0 ? item.hardness : 0f;
                                    float timePerItem = drill.drillTime + hardness * drill.hardnessDrillMultiplier;
                                    float itemsPerSec = (60f / timePerItem) * (drill.size * drill.size);
                                    group.itemRates.put(item, itemsPerSec);
                                }
                            }
                        }
                    } else {
                        group.count++;
                    }
                }
            } else if (block instanceof BeamDrill beamDrill) {
                if (tile.config instanceof Item item) {
                    float mult = (beamDrill.drillMultipliers != null && beamDrill.drillMultipliers.containsKey(item)) 
                            ? beamDrill.drillMultipliers.get(item, 1f) : 1f;
                    float itemsPerSec = (60f / beamDrill.drillTime) * mult;
                    r.items.increment(item, 0f, itemsPerSec);
                } else {
                    // Unconfigured BeamDrill -> Add to DrillOptionGroup using drillMultipliers
                    DrillOptionGroup group = drillGroups.get(block);
                    if (group == null) {
                        group = new DrillOptionGroup();
                        group.drill = block;
                        group.count = 1;
                        drillGroups.put(block, group);

                        final DrillOptionGroup targetGroup = group;
                        if (beamDrill.drillMultipliers != null && !beamDrill.drillMultipliers.isEmpty()) {
                            beamDrill.drillMultipliers.each(e -> {
                                float itemsPerSec = (60f / beamDrill.drillTime) * e.value;
                                targetGroup.itemRates.put(e.key, itemsPerSec);
                            });
                        } else if (Vars.content != null && Vars.content.items() != null) {
                            for (Item item : Vars.content.items()) {
                                if (isMineableItem(item) && item.hardness <= beamDrill.tier) {
                                    float itemsPerSec = 60f / beamDrill.tier;
                                    targetGroup.itemRates.put(item, itemsPerSec);
                                }
                            }
                        }
                    } else {
                        group.count++;
                    }
                }
            }

            // --- OPTIONAL LIQUID BOOSTS (Drills, BeamDrills) ---
            if (block instanceof Drill d) {
                if (block.consumers != null) {
                    for (Consume c : block.consumers) {
                        if (c instanceof ConsumeLiquid cl) {
                            Liquid boostLiquid = cl.liquid != null ? cl.liquid : Liquids.water;
                            OptionalBoostGroup group = boostGroups.get(block);
                            if (group == null) {
                                group = new OptionalBoostGroup();
                                group.block = block;
                                group.count = 1;
                                group.liquidRates.put(boostLiquid, cl.amount * 60f);
                                group.liquidMultipliers.put(boostLiquid, d.liquidBoostIntensity);
                                boostGroups.put(block, group);
                            } else {
                                group.count++;
                            }
                        }
                    }
                }
            } else if (block instanceof BeamDrill bd) {
                if (block.consumers != null) {
                    for (Consume c : block.consumers) {
                        if (c instanceof ConsumeLiquid cl) {
                            Liquid boostLiquid = cl.liquid != null ? cl.liquid : Liquids.water;
                            OptionalBoostGroup group = boostGroups.get(block);
                            if (group == null) {
                                group = new OptionalBoostGroup();
                                group.block = block;
                                group.count = 1;
                                group.liquidRates.put(boostLiquid, cl.amount * 60f);
                                group.liquidMultipliers.put(boostLiquid, bd.optionalBoostIntensity);
                                boostGroups.put(block, group);
                            } else {
                                group.count++;
                            }
                        }
                    }
                }
            }

            // --- GENERATORS WITH FILTERED FUELS ---
            if (block instanceof ConsumeGenerator gen) {
                boolean hasItemFilter = false;
                for (Consume c : block.consumers) {
                    if (c instanceof ConsumeItemFilter) {
                        hasItemFilter = true;
                        break;
                    }
                }
                if (hasItemFilter) {
                    GeneratorFuelGroup group = genGroups.get(block);
                    if (group == null) {
                        group = new GeneratorFuelGroup();
                        group.generator = block;
                        group.count = 1;
                        genGroups.put(block, group);

                        if (Vars.content != null && Vars.content.items() != null) {
                            for (Consume c : block.consumers) {
                                if (c instanceof ConsumeItemFilter ci) {
                                    for (Item item : Vars.content.items()) {
                                        if (ci.filter.get(item)) {
                                            float duration = getGeneratorItemDuration(gen, item);
                                            if (duration > 0) {
                                                group.itemRates.put(item, 60f / duration);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        group.count++;
                    }
                    customItemConsumersProcessed = true;
                }
            }

            // --- CONSUMPTION VIA BLOCK CONSUMERS ---
            if (block.consumers != null) {
                for (Consume c : block.consumers) {
                    boolean isBoostOrOptional = c.optional || c.booster;

                    if (c instanceof ConsumeItems ci) {
                        if (!customItemConsumersProcessed) {
                            for (ItemStack in : ci.items) {
                                float rate = (in.amount / craftTime) * 60f;
                                if (isBoostOrOptional) {
                                    OptionalBoostGroup group = boostGroups.get(block);
                                    if (group == null) {
                                        group = new OptionalBoostGroup();
                                        group.block = block;
                                        group.count = 1;
                                        boostGroups.put(block, group);
                                    }
                                    group.itemRates.increment(in.item, 0f, rate);
                                } else {
                                    r.items.increment(in.item, 0f, -rate);
                                }
                            }
                        }
                    } else if (c instanceof ConsumeLiquid cl) {
                        if (!(block instanceof Drill || block instanceof BeamDrill || isModifierBlock(block))) {
                            if (cl.liquid != null) {
                                float rate = cl.amount * 60f;
                                if (isBoostOrOptional) {
                                    OptionalBoostGroup group = boostGroups.get(block);
                                    if (group == null) {
                                        group = new OptionalBoostGroup();
                                        group.block = block;
                                        group.count = 1;
                                        boostGroups.put(block, group);
                                    }
                                    group.liquidRates.increment(cl.liquid, 0f, rate);
                                } else {
                                    r.liquids.increment(cl.liquid, 0f, -rate);
                                }
                            }
                        }
                    } else if (c instanceof ConsumeLiquids cls) {
                        if (!(block instanceof Drill || block instanceof BeamDrill || isModifierBlock(block))) {
                            for (LiquidStack in : cls.liquids) {
                                float rate = in.amount * 60f;
                                if (isBoostOrOptional) {
                                    OptionalBoostGroup group = boostGroups.get(block);
                                    if (group == null) {
                                        group = new OptionalBoostGroup();
                                        group.block = block;
                                        group.count = 1;
                                        boostGroups.put(block, group);
                                    }
                                    group.liquidRates.increment(in.liquid, 0f, rate);
                                } else {
                                    r.liquids.increment(in.liquid, 0f, -rate);
                                }
                            }
                        }
                    } else if (c instanceof ConsumeLiquidFilter clf) {
                        if (!(block instanceof Drill || block instanceof BeamDrill || isModifierBlock(block))) {
                            float rate = clf.amount * 60f;
                            if (Vars.content != null && Vars.content.liquids() != null) {
                                for (Liquid liq : Vars.content.liquids()) {
                                    if (clf.filter.get(liq)) {
                                        if (isBoostOrOptional) {
                                            OptionalBoostGroup group = boostGroups.get(block);
                                            if (group == null) {
                                                group = new OptionalBoostGroup();
                                                group.block = block;
                                                group.count = 1;
                                                boostGroups.put(block, group);
                                            }
                                            group.liquidRates.increment(liq, 0f, rate);
                                        } else {
                                            r.liquids.increment(liq, 0f, -rate);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        for (GeneratorFuelGroup g : genGroups.values()) {
            r.generatorFuels.add(g);
        }

        for (DrillOptionGroup g : drillGroups.values()) {
            r.drillOptions.add(g);
        }

        for (OptionalBoostGroup g : boostGroups.values()) {
            r.optionalBoosts.add(g);
        }

        return r;
    }

    private static float getGeneratorItemDuration(ConsumeGenerator gen, Item item) {
        float mult = 1f;
        if (gen.itemDurationMultipliers != null && gen.itemDurationMultipliers.containsKey(item)) {
            mult = gen.itemDurationMultipliers.get(item, 1f);
        } else if (item.flammability > 0) {
            mult = item.flammability;
        } else if (item.radioactivity > 0) {
            mult = item.radioactivity;
        }
        return mult > 0 ? gen.itemDuration * mult : gen.itemDuration;
    }

    private static float getCraftTime(Block block, Schematic.Stile tile) {
        if (block instanceof GenericCrafter crafter && crafter.craftTime > 0) {
            return crafter.craftTime;
        }
        if (block instanceof Fracker fracker && fracker.itemUseTime > 0) {
            return fracker.itemUseTime;
        }
        if (block instanceof ConsumeGenerator gen && gen.itemDuration > 0) {
            return gen.itemDuration;
        }
        if (block instanceof ImpactReactor impact && impact.itemDuration > 0) {
            return impact.itemDuration;
        }
        if (block instanceof NuclearReactor reactor && reactor.itemDuration > 0) {
            return reactor.itemDuration;
        }
        if (block instanceof OverdriveProjector op && op.useTime > 0) {
            return op.useTime;
        }
        if (block instanceof MendProjector mp && mp.useTime > 0) {
            return mp.useTime;
        }
        if (block instanceof ForceProjector fp && fp.phaseUseTime > 0) {
            return fp.phaseUseTime;
        }
        if (block instanceof RegenProjector rp && rp.optionalUseTime > 0) {
            return rp.optionalUseTime;
        }

        for (Class<?> cls = block.getClass(); cls != null && cls != Block.class && cls != Object.class; cls = cls.getSuperclass()) {
            for (String fieldName : new String[]{"itemDuration", "useTime", "itemUseTime", "craftTime", "phaseUseTime", "optionalUseTime"}) {
                try {
                    Field f = cls.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    float val = f.getFloat(block);
                    if (val > 0) return val;
                } catch (Exception ignored) {}
            }
        }

        return 1f;
    }
}