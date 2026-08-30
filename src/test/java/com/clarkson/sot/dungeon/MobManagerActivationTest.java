package com.clarkson.sot.dungeon;

import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.TeamManager;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins how {@link MobManager} turns an armed MOB_SPAWNER marker into a live encounter (#46).
 *
 * <p>The contract under test is the one that keeps mob spawning affordable and fair: markers are
 * armed at instance setup but spawn nothing until a member of the owning team walks up to them, and
 * a spawner that has fired never fires again. Without the first half, every segment's mobs would
 * tick for the whole round before anyone entered; without the second, a corpse run would mean
 * fighting the same room twice.
 *
 * <p>A real MockBukkit plugin rather than a Mockito mock: {@code JavaPlugin.getName()} is final, so
 * a mock hands {@link NamespacedKey} a null namespace (the same reason
 * {@code DoorManagerSegmentDoorTest} does it).
 */
class MobManagerActivationTest {

    private ServerMock server;
    private World world;
    private Plugin plugin;
    private GameManager gameManager;
    private TeamManager teamManager;
    private MobManager mobManager;
    private PlayerMock player;

    private final UUID teamId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("mob-activation-world");
        plugin = MockBukkit.createMockPlugin();

        player = server.addPlayer();

        teamManager = mock(TeamManager.class);
        when(teamManager.getPlayerTeamId(player)).thenReturn(teamId);

        gameManager = mock(GameManager.class);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        when(gameManager.getTeamManager()).thenReturn(teamManager);

        mobManager = new MobManager(plugin, gameManager);
        server.getPluginManager().registerEvents(mobManager, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    /** Every mob currently in the test world. */
    private List<Mob> mobsInWorld() {
        return List.copyOf(world.getEntitiesByClass(Mob.class));
    }

    /** Walks the player to a location, producing the PlayerMoveEvent the manager listens for. */
    private void walkTo(Location destination) {
        player.simulatePlayerMove(destination);
    }

    @Test
    void armingAloneSpawnsNothing() {
        mobManager.armSpawner(at(100, 64, 100), teamId, 0);

        assertEquals(1, mobManager.getArmedSpawnerCount(teamId));
        assertTrue(mobsInWorld().isEmpty(),
                "a spawner must stay dormant until a player comes near it");
    }

    @Test
    void aDistantPlayerDoesNotTriggerTheSpawner() {
        mobManager.armSpawner(at(100, 64, 100), teamId, 0);

        walkTo(at(0, 64, 0));

        assertTrue(mobsInWorld().isEmpty(), "50+ blocks away is well outside the activation radius");
        assertEquals(1, mobManager.getArmedSpawnerCount(teamId), "the spawner should still be armed");
    }

    @Test
    void approachingTheSpawnerSpawnsMobs() {
        mobManager.armSpawner(at(100, 64, 100), teamId, 0);

        walkTo(at(100, 64, 96));

        assertEquals(1, mobsInWorld().size(), "depth 0 should spawn a single mob");
        assertEquals(0, mobManager.getArmedSpawnerCount(teamId), "the spawner should be spent");
        assertEquals(1, mobManager.getTrackedMobCount(teamId));
    }

    @Test
    void deeperSegmentsSpawnLargerGroups() {
        mobManager.armSpawner(at(100, 64, 100), teamId, 8);

        walkTo(at(100, 64, 96));

        assertEquals(3, mobsInWorld().size(), "depth 8 falls in the deep band");
    }

    @Test
    void aSpawnerFiresOnlyOnce() {
        mobManager.armSpawner(at(100, 64, 100), teamId, 0);

        walkTo(at(100, 64, 96));
        int afterFirst = mobsInWorld().size();
        // Walk away and back again — a cleared room must stay cleared.
        walkTo(at(0, 64, 0));
        walkTo(at(100, 64, 97));

        assertEquals(afterFirst, mobsInWorld().size(), "the spawner must not re-fire");
    }

    @Test
    void spawnedMobsAreTaggedForTheOwningTeam() {
        mobManager.armSpawner(at(100, 64, 100), teamId, 0);

        walkTo(at(100, 64, 96));

        Mob mob = mobsInWorld().get(0);
        NamespacedKey tagKey = new NamespacedKey(plugin, MobManager.MOB_TAG_KEY);
        NamespacedKey teamKey = new NamespacedKey(plugin, MobManager.MOB_TEAM_KEY);
        assertTrue(mob.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE),
                "only tagged mobs are tracked, cleaned up and counted");
        assertEquals(teamId.toString(),
                mob.getPersistentDataContainer().get(teamKey, PersistentDataType.STRING));
    }

    /**
     * Only persistence is asserted here: MockBukkit declares {@code setRemoveWhenFarAway} but throws
     * on it, so the manager applies that half as best-effort hardening and it cannot be read back.
     * The behaviour that can be checked — the mob is not discarded on chunk unload — still holds.
     */
    @Test
    void spawnedMobsAreMarkedPersistent() {
        mobManager.armSpawner(at(100, 64, 100), teamId, 0);

        walkTo(at(100, 64, 96));

        Mob mob = mobsInWorld().get(0);
        assertTrue(mob.isPersistent(), "a designed encounter must survive until teardown");
    }

    @Test
    void aPlayerOnAnotherTeamDoesNotTriggerTheSpawner() {
        UUID otherTeamId = UUID.randomUUID();
        mobManager.armSpawner(at(100, 64, 100), otherTeamId, 0);

        walkTo(at(100, 64, 96));

        assertTrue(mobsInWorld().isEmpty(),
                "a spawner belongs to one team's instance and only that team may trigger it");
        assertEquals(1, mobManager.getArmedSpawnerCount(otherTeamId));
    }

    @Test
    void nothingSpawnsOutsideARunningGame() {
        when(gameManager.getCurrentState()).thenReturn(GameState.SETUP);
        mobManager.armSpawner(at(100, 64, 100), teamId, 0);

        walkTo(at(100, 64, 96));

        assertTrue(mobsInWorld().isEmpty(), "dungeons exist during setup, but the round has not begun");
        assertEquals(1, mobManager.getArmedSpawnerCount(teamId));
    }

    @Test
    void aPlayerWithNoTeamTriggersNothing() {
        when(teamManager.getPlayerTeamId(player)).thenReturn(null);
        mobManager.armSpawner(at(100, 64, 100), teamId, 0);

        assertDoesNotThrow(() -> walkTo(at(100, 64, 96)));
        assertTrue(mobsInWorld().isEmpty());
    }

    @Test
    void aSpawnerWithNoWorldIsIgnoredRatherThanThrowing() {
        assertDoesNotThrow(() -> mobManager.armSpawner(new Location(null, 0, 64, 0), teamId, 0));

        assertEquals(0, mobManager.getArmedSpawnerCount(teamId));
    }

    @Test
    void clearTeamStateRemovesTheMobsAndDisarmsTheRest() {
        mobManager.armSpawner(at(100, 64, 100), teamId, 0);
        mobManager.armSpawner(at(200, 64, 200), teamId, 0);
        walkTo(at(100, 64, 96));
        assertFalse(mobsInWorld().isEmpty(), "precondition: a spawner fired");

        mobManager.clearTeamState(teamId);

        assertTrue(mobsInWorld().isEmpty(), "teardown must remove the mobs it spawned");
        assertEquals(0, mobManager.getArmedSpawnerCount(teamId), "and disarm what never fired");
        assertEquals(0, mobManager.getTrackedMobCount(teamId));
    }

    @Test
    void clearAllTeamStatesCoversTeamsThatNeverSpawnedAnything() {
        UUID otherTeamId = UUID.randomUUID();
        mobManager.armSpawner(at(100, 64, 100), teamId, 0);
        mobManager.armSpawner(at(300, 64, 300), otherTeamId, 0);
        walkTo(at(100, 64, 96));

        mobManager.clearAllTeamStates();

        assertTrue(mobsInWorld().isEmpty());
        assertEquals(0, mobManager.getArmedSpawnerCount(teamId));
        assertEquals(0, mobManager.getArmedSpawnerCount(otherTeamId),
                "a team whose spawners never fired still holds armed markers");
    }
}
