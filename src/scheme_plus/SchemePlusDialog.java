package scheme_plus;

import arc.scene.Element;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectFloatMap;
import arc.scene.ui.ScrollPane;
import arc.util.Reflect;
import arc.util.Strings;
import mindustry.game.Schematic;
import mindustry.graphics.Pal;
import mindustry.ui.dialogs.SchematicsDialog;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;

public class SchemePlusDialog extends SchematicsDialog {

    @Override
    public void showInfo(Schematic schem) {
        super.showInfo(schem);

        SchematicInfoDialog info = Reflect.get(SchematicsDialog.class, this, "info");

        SchemProduction.Result res = SchemProduction.compute(schem);
        float cons = schem.powerConsumption() * 60, prod = schem.powerProduction() * 60;
        if (res.items.size == 0 && res.liquids.size == 0 && res.probItems.size == 0 
                && res.genericPumpSpeed == 0 && res.drillOptions.isEmpty()
                && res.generatorFuels.isEmpty() && res.optionalBoosts.isEmpty()
                && cons == 0 && prod == 0) return;

        ScrollPane pane = (ScrollPane) info.cont.getCells().first().get();
        Table inner = (Table) (((Table) pane.getWidget()).getCells().first().get());

        // ========= BUILD PRODUCTION & CONSUMPTION TABLE ========
        Table prodTable = new Table();
        prodTable.table(mainTable -> {
            mainTable.margin(10);

            // ======== LEFT TABLE (Consumption) =========
            Table left = new Table();

            left.top();
            left.add("Consumption").color(Pal.remove).padBottom(4).row();

            if (cons != 0) {
                Table row = left.table().left().get();
                row.image(Icon.powerSmall).color(Pal.remove).padRight(3);
                row.add("-" + roundToPositive(cons)).color(Pal.remove).left();
                left.row();
            }

            addResourceRows(left, res.items, false);
            addResourceRows(left, res.liquids, false);

            if (left.getChildren().size > 1) {
                mainTable.add(left).top().padRight(20);
            }

            // ======== RIGHT TABLE (Production) ========
            Table right = new Table();

            right.top();
            right.add("Production").color(Pal.accent).padBottom(4).row();

            if (prod != 0) {
                Table row = right.table().left().get();
                row.image(Icon.powerSmall).color(Pal.powerLight).padRight(3);
                row.add("+" + roundToPositive(prod)).color(Pal.powerLight).left();
                right.row();
            }

            addResourceRows(right, res.items, true);
            addResourceRows(right, res.liquids, true);

            // Probabilistic outputs with floored percentages (never exceeds 100% total)
            res.probItems.each(e -> {
                if (e.value <= 0) return;
                float rawChance = res.probItemChances.get(e.key, 0f);
                int chance = (int) Math.floor(rawChance);
                Table row = right.table().left().get();
                row.image(e.key.uiIcon).size(Vars.iconMed).padRight(4);
                row.add("~" + roundToPositive(e.value) + "/s" + (chance > 0 ? " [lightgray](" + chance + "%)[]" : "")).color(Pal.accent);
                right.row();
            });

            // Generic Pump speed if liquid config is empty
            if (res.genericPumpSpeed > 0) {
                Table row = right.table().left().get();
                row.image(Icon.liquidSmall).size(Vars.iconMed).padRight(4);
                row.add("+" + roundToPositive(res.genericPumpSpeed) + "/s [lightgray](Liquid)[]").color(Pal.accent);
                right.row();
            }

            if (right.getChildren().size > 1) {
                mainTable.add(right).top();
            }
        });

        // Insert prodTable into info dialog
        Cell<?> targetCell = null;
        for (int i = 0; i < inner.getCells().size; i++) {
            Cell<?> cell = inner.getCells().get(i);
            Element el = cell.get();

            if (el instanceof Table) {
                Table t = (Table) el;
                for (Element child : t.getChildren()) {
                    if (child instanceof arc.scene.ui.Image) {
                        if (((arc.scene.ui.Image) child).getDrawable() == Icon.powerSmall) {
                            targetCell = cell;
                            break;
                        }
                    }
                }
            }
        }

        if (targetCell != null) {
            targetCell.setElement(prodTable).center().growX();
        } else {
            inner.row();
            if (!schem.description().isEmpty()) {
                inner.removeChild(inner.getCells().get(inner.getCells().size - 1).get());
                inner.add(prodTable).center().growX();
                inner.row();
                inner.add("[lightgray]" + schem.description()).wrap().padTop(20).growX().maxWidth(500).padLeft(8).padRight(8).row();
            } else {
                inner.add(prodTable).center().growX();
            }
        }

        // ======== DRILL MINING OPTIONS SECTION ========
        if (!res.drillOptions.isEmpty()) {
            Table drillSection = new Table();
            drillSection.margin(6);
            drillSection.add("Drill Rates:").color(Pal.lightishGray).padBottom(4).row();

            for (SchemProduction.DrillOptionGroup group : res.drillOptions) {
                Table groupTable = drillSection.table().left().padBottom(4).get();
                groupTable.image(group.drill.uiIcon).size(Vars.iconSmall).padRight(4);
                groupTable.add(group.drill.localizedName + (group.count > 1 ? " (x" + group.count + ")" : "") + ":").color(Pal.accent).padRight(6);

                group.itemRates.each(e -> {
                    float totalRate = e.value * group.count;
                    groupTable.image(e.key.uiIcon).size(Vars.iconSmall).padRight(2);
                    groupTable.add("+" + roundToPositive(totalRate) + "/s").color(Pal.accent).padRight(6);
                });
                drillSection.row();
            }

            inner.row();
            inner.add(drillSection).center().growX();
        }

        // ======== GENERATOR FUEL OPTIONS SECTION ========
        if (!res.generatorFuels.isEmpty()) {
            Table fuelSection = new Table();
            fuelSection.margin(6);
            fuelSection.add("Fuel Options:").color(Pal.lightishGray).padBottom(4).row();

            for (SchemProduction.GeneratorFuelGroup group : res.generatorFuels) {
                Table groupTable = fuelSection.table().left().padBottom(4).get();
                groupTable.image(group.generator.uiIcon).size(Vars.iconSmall).padRight(4);
                groupTable.add(group.generator.localizedName + (group.count > 1 ? " (x" + group.count + ")" : "") + ":").color(Pal.accent).padRight(6);

                group.itemRates.each(e -> {
                    float totalRate = e.value * group.count;
                    groupTable.image(e.key.uiIcon).size(Vars.iconSmall).padRight(2);
                    groupTable.add("-" + roundToPositive(totalRate) + "/s").color(Pal.remove).padRight(6);
                });
                fuelSection.row();
            }

            inner.row();
            inner.add(fuelSection).center().growX();
        }

        // ======== OPTIONAL BOOSTS / MODIFIERS SECTION ========
        if (!res.optionalBoosts.isEmpty()) {
            Table boostSection = new Table();
            boostSection.margin(6);
            boostSection.add("Modifiers / Boosts:").color(Pal.lightishGray).padBottom(4).row();

            for (SchemProduction.OptionalBoostGroup group : res.optionalBoosts) {
                Table groupTable = boostSection.table().left().padBottom(4).get();
                groupTable.image(group.block.uiIcon).size(Vars.iconSmall).padRight(4);
                groupTable.add(group.block.localizedName + (group.count > 1 ? " (x" + group.count + ")" : "") + ":").color(Pal.accent).padRight(6);

                group.liquidRates.each(e -> {
                    float totalRate = e.value * group.count;
                    float mult = group.liquidMultipliers.get(e.key, 1f);
                    groupTable.image(e.key.uiIcon).size(Vars.iconSmall).padRight(2);
                    groupTable.add("-" + roundToPositive(totalRate) + "/s").color(Pal.remove);
                    groupTable.add("[lightgray](x" + roundToPositive(mult * mult) + ")[]").padLeft(2).padRight(6);
                });

                boostSection.row();
            }

            inner.row();
            inner.add(boostSection).center().growX();
        }

        inner.invalidateHierarchy();
    }

    private <T extends UnlockableContent> void addResourceRows(Table t, ObjectFloatMap<T> elements, boolean positive) {
        elements.each(e -> {
            if (Math.abs(e.value) < 0.01f || (positive ? e.value <= 0 : e.value >= 0)) return;
            Table row = t.table().left().get();
            row.image(e.key.uiIcon).size(Vars.iconMed).padRight(4);
            row.add((positive ? "+" : "-") + roundToPositive(e.value) + "/s").color(positive ? Pal.accent : Pal.remove);
            t.row();
        });
    }

    String roundToPositive(float value) {
        return Strings.autoFixed(Math.round(Math.abs(value) * 100f) / 100f, 2);
    }
}