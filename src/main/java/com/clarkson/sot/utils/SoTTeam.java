package com.clarkson.sot.utils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

public class SoTTeam {
    private final UUID teamId;
    private String teamName;
    private Set<UUID> memberUUIDs;
    private int teamSandCount; // Shared sand resource
    private int bankedScore; // Score banked via Sphinx or leaving

    public SoTTeam(UUID teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.memberUUIDs = ConcurrentHashMap.newKeySet(); // Thread-safe set
        this.teamSandCount = 0;
        this.bankedScore = 0;
    }

    public void addMember(Player player) { memberUUIDs.add(player.getUniqueId()); }
    public void removeMember(Player player) { memberUUIDs.remove(player.getUniqueId()); }
    public Set<UUID> getMemberUUIDs() { return memberUUIDs; }

    public int getSandCount() { return teamSandCount; }
    public void addSand(int amount) { this.teamSandCount += amount; }
    public boolean tryUseSand(int amount) {
        if (this.teamSandCount >= amount) {
            this.teamSandCount -= amount;
            return true;
        }
        return false;
    }

    public int getBankedScore() { return bankedScore; }
    public void addBankedScore(int score) { this.bankedScore += score; }
}