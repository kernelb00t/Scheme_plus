package scheme_plus;

import arc.struct.*;
import mindustry.game.Schematic;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.consumers.*;

public class SchemProduction {

    public static class Result{
        public ObjectFloatMap<Item> items = new ObjectFloatMap<>();
        public ObjectFloatMap<Liquid> liquids = new ObjectFloatMap<>();
    }

    public static Result compute(Schematic schem){
        Result r = new Result();

        for(var tile : schem.tiles){
            Block block = tile.block;
            if(!(block instanceof GenericCrafter crafter)) continue;

            float time = crafter.craftTime > 0 ? crafter.craftTime : 1f;

            if(crafter.outputItems != null){
                for(ItemStack out : crafter.outputItems){
                    r.items.increment(out.item, 0f, out.amount / time * 60f);
                }
            }
            if(crafter.outputLiquids != null){
                for(LiquidStack out : crafter.outputLiquids){
                    r.liquids.increment(out.liquid, 0f, out.amount * 60f);
                }
            }

            for(Consume c : block.consumers){
                if(c instanceof ConsumeItems ci){
                    for(ItemStack in : ci.items){
                        r.items.increment(in.item, 0f, -(in.amount / time * 60f));
                    }
                }else if(c instanceof ConsumeLiquid cl){
                    r.liquids.increment(cl.liquid, 0f, -(cl.amount * 60f));
                }else if(c instanceof ConsumeLiquids cls){
                    for(LiquidStack in : cls.liquids){
                        r.liquids.increment(in.liquid, 0f, -(in.amount * 60f));
                    }
                }
            }
        }

        return r;
    }
}