package com.clarkson.sot.main;
import org.bukkit.plugin.java.JavaPlugin;

import com.clarkson.sot.commands.GiveCoinToolCommand;
import com.clarkson.sot.events.ToolListener;

public class SoT extends JavaPlugin {

    @Override
    public void onEnable() {
        this.getCommand("sotgetcointool").setExecutor(new GiveCoinToolCommand());
        getServer().getPluginManager().registerEvents(new ToolListener(this), this);
    }

    @Override
    public void onDisable() {

    }

}

