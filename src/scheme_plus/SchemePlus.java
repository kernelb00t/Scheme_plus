package scheme_plus;

import arc.Events;
import mindustry.game.EventType.*;
import mindustry.mod.Mod;

import static mindustry.Vars.ui;

public class SchemePlus extends Mod{
    public SchemePlus(){
        Events.on(ClientLoadEvent.class, e -> {
            ui.schematics = new SchemePlusDialog();
        });
    }
}