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
    public void addMember(UUID memberUUID) {
        memberUUIDs.add(memberUUID);
    }

    public void removeMember(UUID memberUUID) {
        memberUUIDs.remove(memberUUID);
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(memberUUIDs);
    }

    public TeamDefinition getTeam() {
        return team;
    }
}