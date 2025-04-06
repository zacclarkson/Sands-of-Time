package com.clarkson.sot.utils;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.visuals.VisualSandTimerDisplay;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.checkerframework.checker.units.qual.A;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SoTTeam {
    
    private TeamDefinition team;
    private Set<UUID> memberUUIDs = new Set<>();
    public SoTTeam(TeamDefinition team) {
        this.team = team;

        
    }
    public TeamDefinition getTeam() {
        return team;
    }
}