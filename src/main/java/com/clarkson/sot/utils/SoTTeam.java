package com.clarkson.sot.utils;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SoTTeam {
    
    private TeamDefinition team;
    private Set<UUID> memberUUIDs = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public SoTTeam(TeamDefinition team) {
        this.team = team;

        
    }

    public TeamDefinition getTeam() {
        return team;
    }
}