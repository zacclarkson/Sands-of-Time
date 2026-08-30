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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins how {@link MobManager} runs an encounter (#46).
 *
 * <p>The contract: a spawner block sits dormant until a member of the owning team comes within
 * {@code ACTIVATION_RADIUS}, then produces a wave every {@code SPAWN_INTERVAL_TICKS} for as long as
 * someone stays in range. It never stops on its own — only breaking the block ends it (see
 * {@link MobSpawnerBreakTest}) — so the pressure is bounded by the per-spawner live-mob cap rather
 * than by a one-shot trigger.
 *
 * <p>The clock is injected rather than read from the server, so waves can be stepped
 * deterministically without a live scheduler (the idiom {@code CoinPickupNotifier} uses).
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

    /** Drives MobManager's notion of "now"; advanced by {@link #advanceTicks(long)}. */
    private final AtomicLong now = new AtomicLong(0);

    private final UUID teamId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();

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

        mobManager = new MobManager(plugin, gameManager, now::get);
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

    private void standAt(Location location) {
        player.teleport(location);
    }

    /** Moves the clock forward and runs the spawn check, as the repeating task would. */
    private void advanceTicks(long ticks) {
        now.addAndGet(ticks);
        mobManager.tick();
    }

    private void arm(Location location, int depth) {
        mobManager.armSpawner(location, teamId, instanceId, depth);
    }

    @Test
    void placingASpawnerSpawnsNothingOnItsOwn() {
        arm(at(100, 64, 100), 0);

        assertEquals(1, mobManager.getActiveSpawnerCount(teamId));
        assertTrue(mobsInWorld().isEmpty(),
                "a spawner must stay dormant until a player comes near it");
    }

    @Test
    void theMarkerCellBecomesASpawnerBlock() {
        arm(at(100, 64, 100), 0);

        assertEquals(MobManager.SPAWNER_BLOCK, at(100, 64, 100).getBlock().getType(),
                "the marker must become a real block, or there is nothing to break");
    }

    @Test
    void aDistantPlayerDoesNotStartTheSpawner() {
        arm(at(100, 64, 100), 0);
        standAt(at(0, 64, 0));

        advanceTicks(1);

        assertTrue(mobsInWorld().isEmpty(), "100 blocks away is well outside the activation radius");
    }

    @Test
    void approachingTheSpawnerStartsItImmediately() {
        arm(at(100, 64, 100), 0);
        standAt(at(100, 64, 96));

        advanceTicks(1);

        assertEquals(1, mobsInWorld().size(), "depth 0 produces a single mob per wave");
        assertEquals(1, mobManager.getTrackedMobCount(teamId));
    }

    @Test
    void activationRadiusExceedsPlayerReach() {
        // The point of the wide radius: a player cannot walk up and break the spawner before the
        // fight starts. Standing 8 blocks out — beyond reach — must already have started it.
        arm(at(100, 64, 100), 0);
        standAt(at(108, 64, 100));

        advanceTicks(1);

        assertFalse(mobsInWorld().isEmpty(),
                "the encounter must begin before the player is close enough to attack the block");
    }

    @Test
    void aStationaryPlayerKeepsTakingWaves() {
        arm(at(100, 64, 100), 0);
        standAt(at(100, 64, 96));

        advanceTicks(1);
        assertEquals(1, mobsInWorld().size(), "first wave");

        advanceTicks(MobManager.SPAWN_INTERVAL_TICKS);
        assertEquals(2, mobsInWorld().size(), "a spawner keeps producing without the player moving");

        advanceTicks(MobManager.SPAWN_INTERVAL_TICKS);
        assertEquals(3, mobsInWorld().size(), "and keeps going until it is destroyed");
    }

    @Test
    void wavesRespectTheInterval() {
        arm(at(100, 64, 100), 0);
        standAt(at(100, 64, 96));

        advanceTicks(1);
        advanceTicks(MobManager.SPAWN_INTERVAL_TICKS - 10); // still inside the cooldown

        assertEquals(1, mobsInWorld().size(), "a second wave must wait out the interval");
    }

    @Test
    void aSpawnerStopsWhileNobodyIsNear() {
        arm(at(100, 64, 100), 0);
        standAt(at(100, 64, 96));
        advanceTicks(1);
        assertEquals(1, mobsInWorld().size(), "precondition: it started");

        standAt(at(0, 64, 0));
        advanceTicks(MobManager.SPAWN_INTERVAL_TICKS * 3);

        assertEquals(1, mobsInWorld().size(), "an unattended spawner should not keep filling the room");
    }

    @Test
    void aSpawnerWillNotExceedItsLiveMobCap() {
        arm(at(100, 64, 100), 8); // deep band: three mobs per wave
        standAt(at(100, 64, 96));

        for (int i = 0; i < 10; i++) {
            advanceTicks(MobManager.SPAWN_INTERVAL_TICKS);
        }

        assertEquals(MobManager.MAX_LIVE_MOBS_PER_SPAWNER, mobsInWorld().size(),
                "ignoring a spawner must cost a bounded number of entities, not an unbounded stream");
    }

    @Test
    void deeperSegmentsProduceLargerWaves() {
        arm(at(100, 64, 100), 8);
        standAt(at(100, 64, 96));

        advanceTicks(1);

        assertEquals(3, mobsInWorld().size(), "depth 8 falls in the deep band");
    }

    @Test
    void spawnedMobsAreTaggedForTheOwningTeam() {
        arm(at(100, 64, 100), 0);
        standAt(at(100, 64, 96));
        advanceTicks(1);

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
     */
    @Test
    void spawnedMobsAreMarkedPersistent() {
        arm(at(100, 64, 100), 0);
        standAt(at(100, 64, 96));
        advanceTicks(1);

        assertTrue(mobsInWorld().get(0).isPersistent(),
                "a designed encounter must survive until teardown");
    }

    @Test
    void mobsNeverSpawnInsideTheSpawnerBlock() {
        arm(at(100, 64, 100), 8);
        standAt(at(100, 64, 96));
        advanceTicks(1);

        for (Mob mob : mobsInWorld()) {
            Location loc = mob.getLocation();
            assertFalse(loc.getBlockX() == 100 && loc.getBlockY() == 64 && loc.getBlockZ() == 100,
                    "a mob spawned inside the spawner block would suffocate: " + loc);
        }
    }

    @Test
    void aPlayerOnAnotherTeamDoesNotStartTheSpawner() {
        UUID otherTeamId = UUID.randomUUID();
        mobManager.armSpawner(at(100, 64, 100), otherTeamId, instanceId, 0);
        standAt(at(100, 64, 96));

        advanceTicks(1);

        assertTrue(mobsInWorld().isEmpty(),
                "a spawner belongs to one team's instance and only that team may trigger it");
        assertEquals(1, mobManager.getActiveSpawnerCount(otherTeamId));
    }

    @Test
    void nothingSpawnsOutsideARunningGame() {
        when(gameManager.getCurrentState()).thenReturn(GameState.SETUP);
        arm(at(100, 64, 100), 0);
        standAt(at(100, 64, 96));

        advanceTicks(1);

        assertTrue(mobsInWorld().isEmpty(), "dungeons exist during setup, but the round has not begun");
    }

    @Test
    void aPlayerWithNoTeamTriggersNothing() {
        when(teamManager.getPlayerTeamId(player)).thenReturn(null);
        arm(at(100, 64, 100), 0);
        standAt(at(100, 64, 96));

        assertDoesNotThrow(() -> advanceTicks(1));
        assertTrue(mobsInWorld().isEmpty());
    }

    @Test
    void aSpawnerWithNoWorldIsIgnoredRatherThanThrowing() {
        assertDoesNotThrow(
                () -> mobManager.armSpawner(new Location(null, 0, 64, 0), teamId, instanceId, 0));

        assertEquals(0, mobManager.getActiveSpawnerCount(teamId));
    }

    @Test
    void clearTeamStateRemovesTheMobsAndTheSpawners() {
        arm(at(100, 64, 100), 0);
        arm(at(200, 64, 200), 0);
        standAt(at(100, 64, 96));
        advanceTicks(1);
        assertFalse(mobsInWorld().isEmpty(), "precondition: a spawner fired");

        mobManager.clearTeamState(teamId);

        assertTrue(mobsInWorld().isEmpty(), "teardown must remove the mobs it spawned");
        assertEquals(0, mobManager.getActiveSpawnerCount(teamId));
        assertEquals(0, mobManager.getTrackedMobCount(teamId));
    }

    @Test
    void clearAllTeamStatesCoversTeamsThatNeverSpawnedAnything() {
        UUID otherTeamId = UUID.randomUUID();
        arm(at(100, 64, 100), 0);
        mobManager.armSpawner(at(300, 64, 300), otherTeamId, instanceId, 0);
        standAt(at(100, 64, 96));
        advanceTicks(1);

        mobManager.clearAllTeamStates();

        assertTrue(mobsInWorld().isEmpty());
        assertEquals(0, mobManager.getActiveSpawnerCount(teamId));
        assertEquals(0, mobManager.getActiveSpawnerCount(otherTeamId),
                "a team whose spawners never fired still has blocks registered");
    }

    @Test
    void aClearedTeamStopsSpawningEvenWithAPlayerStandingThere() {
        arm(at(100, 64, 100), 0);
        standAt(at(100, 64, 96));
        advanceTicks(1);

        mobManager.clearTeamState(teamId);
        advanceTicks(MobManager.SPAWN_INTERVAL_TICKS * 2);

        assertTrue(mobsInWorld().isEmpty(), "a torn-down instance must not keep producing mobs");
    }
}
