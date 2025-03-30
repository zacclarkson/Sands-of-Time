package com.clarkson.sot.main;
import org.bukkit.plugin.java.JavaPlugin;

import com.clarkson.sot.commands.CommandManager;
import com.clarkson.sot.dungeon.DungeonManager;

public class SoT extends JavaPlugin {

    @Override
    public void onEnable() {
        DungeonManager dungeonManager = new DungeonManager(this);

        CommandManager commandManager = new CommandManager(dungeonManager);

        this.getCommand("generatedungeon").setExecutor(commandManager);
        this.getCommand("cloneDoor").setExecutor(commandManager);
    }

    @Override
    public void onDisable() {

    }

}

