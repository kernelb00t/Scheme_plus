package scheme_plus;

import arc.scene.Element;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectFloatMap;
import arc.scene.ui.ScrollPane;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Strings;
import mindustry.game.Schematic;
import mindustry.graphics.Pal;
import mindustry.ui.dialogs.SchematicsDialog;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;

public class SchemePlusDialog extends SchematicsDialog{

    @Override
    public void showInfo(Schematic schem){
        super.showInfo(schem);

        SchematicInfoDialog info = Reflect.get(SchematicsDialog.class, this, "info");
                
        SchemProduction.Result res = SchemProduction.compute(schem);
        float cons = schem.powerConsumption() * 60, prod = schem.powerProduction() * 60;
        if(res.items.size == 0 && res.liquids.size == 0 && cons == 0 && prod == 0) return;

        ScrollPane pane = (ScrollPane) info.cont.getCells().first().get();
        Table inner = (Table) (((Table) pane.getWidget()).getCells().first().get());

        Log.info("res: items: @, liquids: @, cons: @, prod: @", res.items.size, res.liquids.size, cons, prod);
        
        // ========= BUILD TABLE ========
        Table prodTable = new Table();
        prodTable.table(mainTable -> {
            mainTable.margin(10);
            mainTable.table(left -> {
                left.top();
                left.add("Consumption").color(Pal.remove).padBottom(4).row();

                if (cons != 0){
                    Table row = left.table().left().get();
                    row.image(Icon.powerSmall).color(Pal.remove).padRight(3);
                    row.add("-" + roundToPositive(cons)).color(Pal.remove).left();
                    left.row();;
                }

                addResourceRows(left, res.items, false);
                addResourceRows(left, res.liquids, false);
            }).top().padRight(20); 

            mainTable.table(right -> {
                right.top();
                right.add("Production").color(Pal.accent).padBottom(4).row();

                if (prod != 0){
                    Table row = right.table().left().get();
                    row.image(Icon.powerSmall).color(Pal.powerLight).padRight(3);
                    row.add("+" + roundToPositive(prod)).color(Pal.powerLight).left();
                    right.row();
                }
                
                addResourceRows(right, res.items, true);
                addResourceRows(right, res.liquids, true);
            }).top();

        });

        Cell<?> targetCell = null;
        for(int i = 0; i < inner.getCells().size; i++){
            Cell<?> cell = inner.getCells().get(i);
            Element el = cell.get();
            
            if(el instanceof Table){
                Table t = (Table)el;
                for(Element child : t.getChildren()) {
                    if(child instanceof arc.scene.ui.Image) {
                        if(((arc.scene.ui.Image)child).getDrawable() == Icon.powerSmall) {
                            targetCell = cell;
                            break;
                        }
                    }
                }
            }
        }
        if(targetCell != null){
            targetCell.setElement(prodTable).center().growX();
        } else {
            inner.row();
            if(!schem.description().isEmpty()){
                inner.removeChild(inner.getCells().get(inner.getCells().size-1).get());
                inner.add(prodTable).center().growX();
                inner.row();
                inner.add("[lightgray]" + schem.description()).wrap().padTop(20).growX().maxWidth(500).padLeft(8).padRight(8).row();
            }else{
                inner.add(prodTable).center().growX();
            }

        }
        inner.invalidateHierarchy();
    }

    private <T extends UnlockableContent> void addResourceRows(Table t, ObjectFloatMap<T> elements, boolean positive){
        elements.each(e -> {
            if(Math.abs(e.value) < 0.01f || (positive ? e.value <= 0 : e.value >= 0)) return;
            Table row = t.table().left().get();
            row.image(e.key.uiIcon).size(Vars.iconMed).padRight(4);
            row.add((positive ? "+" : "-") + roundToPositive(e.value) + "/s").color(positive ? Pal.accent : Pal.remove);
            t.row();
        });
    }

    String roundToPositive(float value){
        return Strings.autoFixed(Math.round(Math.abs(value) * 100f)/100f, 2);
    }
}