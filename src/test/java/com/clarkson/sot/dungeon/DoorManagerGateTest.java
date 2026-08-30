package com.clarkson.sot.dungeon;

import com.clarkson.sot.entities.Area;
import com.clarkson.sot.entities.Gate;
import com.clarkson.sot.main.GameManager;
import com.clarkson.sot.main.GameState;
import com.clarkson.sot.utils.TeamManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers gates and their levers end to end: built closed at instantiation, opened by the segment's
 * own lever, and one-way once pulled.
 *
 * <p>Two things here are easy to get wrong and both are pinned below. The lever marker is an
 * <em>air</em> cell, so unless a real lever block is written into the world the right-click never
 * fires at all -- the same trap {@code Door.buildClosed()} exists for. And the first pull is
 * deliberately left uncancelled so vanilla flips the lever on, while every later click is cancelled
 * so it cannot be flipped back off over an open gate.
 */
class DoorManagerGateTest {

    private ServerMock server;
    private World world;
    private Plugin plugin;
    private GameManager gameManager;
    private DoorManager doorManager;
    private Player player;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("gate-test-world");
        teamId = UUID.randomUUID();

        // A real MockBukkit plugin, not a Mockito mock: the gate animation needs a live scheduler.
        plugin = MockBukkit.createMockPlugin();

        player = server.addPlayer();
        TeamManager teamManager = mock(TeamManager.class);
        when(teamManager.getPlayerTeamId(player)).thenReturn(teamId);
        gameManager = mock(GameManager.class);
        when(gameManager.getCurrentState()).thenReturn(GameState.RUNNING);
        when(gameManager.getTeamManager()).thenReturn(teamManager);

        doorManager = new DoorManager(plugin, gameManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A location whose chunk (and its neighbours across a chunk border) is loaded. */
    private Location at(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                world.loadChunk((x + dx) >> 4, (z + dz) >> 4);
        return new Location(world, x, y, z);
    }

    private Area area(Location min, Location max) {
        return new Area(min, max);
    }

    /** Puts a solid block next to the lever cell, standing in for the wall the builder clicked. */
    private void wallNorthOf(Location leverCell) {
        leverCell.getBlock().getRelative(BlockFace.NORTH).setType(Material.STONE);
    }

    private PlayerInteractEvent rightClick(Block block) {
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.SOUTH);
        doorManager.onPlayerInteract(event);
        return event;
    }

    private void assertFilledWith(Area bounds, Material expected) {
        Location min = bounds.getMinPoint();
        Location max = bounds.getMaxPoint();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++)
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++)
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++)
                    assertEquals(expected, world.getBlockAt(x, y, z).getType(),
                            "block at " + x + "," + y + "," + z);
    }

    // --- construction ---

    @Test
    void gatesAreBuiltFromIronBarsAndTheLeverIsARealBlock() {
        Location leverCell = at(5, 64, 5);
        wallNorthOf(leverCell);
        Area gate = area(at(7, 64, 5), at(9, 67, 5));

        doorManager.initializeGatesForInstance(teamId,
                List.of(new GateGroup(leverCell, List.of(gate), "gateroom")), List.of());

        assertFilledWith(gate, DoorManager.GATE_MATERIAL);
        // The marker cell was air; without a real block here the right-click never fires.
        assertEquals(Material.LEVER, leverCell.getBlock().getType(),
                "a lever block must be written at the marker cell");
    }

    @Test
    void aWallLeverFacesAwayFromTheBlockItHangsOn() {
        Location leverCell = at(5, 64, 5);
        wallNorthOf(leverCell);

        doorManager.initializeGatesForInstance(teamId,
                List.of(new GateGroup(leverCell, List.of(area(at(7, 64, 5), at(9, 67, 5))), "gateroom")),
                List.of());

        BlockData data = leverCell.getBlock().getBlockData();
        assumeTrue(data instanceof Switch, "this server's LEVER block data is not a Switch");
        Switch lever = (Switch) data;
        assertEquals(FaceAttachable.AttachedFace.WALL, lever.getAttachedFace(), "bolted to the wall");
        assertEquals(BlockFace.SOUTH, lever.getFacing(), "a wall lever points away from its wall");
    }

    @Test
    void aLeverCellWithNothingToHangOnGetsASupportBlock() {
        // Authored mid-air. A lever with no support pops off at the first neighbour update, and gates
        // nothing can open are worse than one stray block.
        Location leverCell = at(40, 70, 40);

        doorManager.initializeGatesForInstance(teamId,
                List.of(new GateGroup(leverCell, List.of(area(at(42, 70, 40), at(44, 73, 40))), "floating")),
                List.of());

        assertEquals(DoorManager.LEVER_SUPPORT_MATERIAL,
                leverCell.getBlock().getRelative(BlockFace.DOWN).getType(), "a support block is placed");
        assertEquals(Material.LEVER, leverCell.getBlock().getType(), "and the lever still goes down");
    }

    // --- pulling ---

    @Test
    void pullingTheLeverOpensEveryGateInTheSegment() {
        Location leverCell = at(5, 64, 5);
        wallNorthOf(leverCell);
        Area first = area(at(7, 64, 5), at(9, 67, 5));
        Area second = area(at(7, 64, 9), at(9, 67, 9));

        doorManager.initializeGatesForInstance(teamId,
                List.of(new GateGroup(leverCell, List.of(first, second), "gateroom")), List.of());

        rightClick(leverCell.getBlock());
        server.getScheduler().performTicks(18L); // 4 layers at the default 3-tick delay, plus slack

        assertFilledWith(first, Material.AIR);
        assertFilledWith(second, Material.AIR);
    }

    @Test
    void theFirstPullIsNotCancelledSoVanillaFlipsTheLever() {
        Location leverCell = at(5, 64, 5);
        wallNorthOf(leverCell);

        doorManager.initializeGatesForInstance(teamId,
                List.of(new GateGroup(leverCell, List.of(area(at(7, 64, 5), at(9, 67, 5))), "gateroom")),
                List.of());

        PlayerInteractEvent event = rightClick(leverCell.getBlock());

        assertFalse(event.isCancelled(),
                "vanilla flips the lever on, so the world itself records that the gates are open");
    }

    @Test
    void aSecondPullIsCancelledAndChangesNothing() {
        Location leverCell = at(5, 64, 5);
        wallNorthOf(leverCell);
        Area gate = area(at(7, 64, 5), at(9, 67, 5));

        doorManager.initializeGatesForInstance(teamId,
                List.of(new GateGroup(leverCell, List.of(gate), "gateroom")), List.of());

        rightClick(leverCell.getBlock());
        server.getScheduler().performTicks(18L);
        PlayerInteractEvent second = rightClick(leverCell.getBlock());
        server.getScheduler().performTicks(18L);

        assertTrue(second.isCancelled(),
                "a pulled lever cannot be flipped back off while it sits over an open gate");
        assertFilledWith(gate, Material.AIR);
    }

    @Test
    void oneSegmentsLeverDoesNotOpenAnothersGates() {
        Location firstLever = at(5, 64, 5);
        Location secondLever = at(45, 64, 45);
        wallNorthOf(firstLever);
        wallNorthOf(secondLever);
        Area firstGate = area(at(7, 64, 5), at(9, 67, 5));
        Area secondGate = area(at(47, 64, 45), at(49, 67, 45));

        doorManager.initializeGatesForInstance(teamId, List.of(
                new GateGroup(firstLever, List.of(firstGate), "first"),
                new GateGroup(secondLever, List.of(secondGate), "second")), List.of());

        rightClick(firstLever.getBlock());
        server.getScheduler().performTicks(18L);

        assertFilledWith(firstGate, Material.AIR);
        assertFilledWith(secondGate, DoorManager.GATE_MATERIAL);
    }

    // --- ownership and teardown ---

    @Test
    void aGateIsNotAKeyDoor() {
        Location leverCell = at(5, 64, 5);
        wallNorthOf(leverCell);
        Area gate = area(at(7, 64, 5), at(9, 67, 5));

        doorManager.initializeGatesForInstance(teamId,
                List.of(new GateGroup(leverCell, List.of(gate), "gateroom")), List.of());

        // getDoorAt feeds the rusty-key branch of onPlayerInteract. A gate resolving through it would
        // ask the player for a key it can never use -- the shape of bug #65.
        assertNull(doorManager.getDoorAt(teamId, leverCell), "the lever is not a keyed door");
        assertNull(doorManager.getDoorAt(teamId, gate.getMinPoint()), "nor is a gate block");
    }

    @Test
    void clearTeamStateDropsTheGates() {
        Location leverCell = at(5, 64, 5);
        wallNorthOf(leverCell);
        Area gate = area(at(7, 64, 5), at(9, 67, 5));

        doorManager.initializeGatesForInstance(teamId,
                List.of(new GateGroup(leverCell, List.of(gate), "gateroom")), List.of());
        doorManager.clearTeamState(teamId);

        assertFalse(doorManager.pullLever(teamId, leverCell, player),
                "a torn-down team's lever no longer resolves to anything");
    }

    @Test
    void aGateTakesNoKey() {
        Gate gate = new Gate(plugin, teamId, area(at(7, 64, 5), at(9, 67, 5)));

        assertFalse(gate.isCorrectKey(null), "gates are opened by levers, not keys");
        assertFalse(gate.close(player), "gates are one-way once opened");
    }
}
