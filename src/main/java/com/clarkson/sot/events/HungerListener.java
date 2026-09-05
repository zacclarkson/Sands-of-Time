package com.clarkson.sot.events;

import com.clarkson.sot.main.GameManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * Freezes the hunger bar for everyone taking part in a live round.
 *
 * <p>The survival pressure in Sands of Time is the sand timer, not food, and the loot table carries
 * no food at all ({@link FloorItemManager}: the bread slots are splash potions of healing). So a
 * participant's hunger must never drop — with nothing to eat, a drained bar would take sprint away
 * (vanilla needs a food level above 6) and eventually starve them. This listener cancels every
 * {@link FoodLevelChangeEvent} that would <em>lower</em> a participant's food level; a change that
 * raises it (eating something carried in, a Saturation effect) is deliberately let through.
 *
 * <p>Gated on {@link GameManager#isRoundLive} rather than {@code RUNNING} alone so the countdown and
 * a paused round are covered, and on {@link GameManager#isParticipant} rather than a
 * {@code PlayerStatus}: a player waiting in the death cage or sitting in the trapped box is still in
 * the game and must not starve there, while staff standing elsewhere are untouched. There is no
 * Creative/Spectator bypass because those modes have no hunger drain to bypass.
 *
 * <p>Vanilla drains <em>saturation</em> silently and only fires this event once saturation is gone,
 * so cancelling pins the food level while saturation still runs down to zero. That only disables the
 * fast regeneration; with the bar pinned at 20 the slow natural regeneration (food level 18 or more)
 * keeps ticking for the whole round, and the floor potions are the burst heal on top of it.
 */
public class HungerListener implements Listener {

    /** A full hunger bar. */
    static final int FULL_FOOD_LEVEL = 20;
    /** The saturation a vanilla respawn hands out alongside a full bar. */
    static final float START_SATURATION = 5f;

    private final GameManager gameManager;

    public HungerListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    // Priority is not load-bearing: nothing else in the plugin listens to this event. HIGH just
    // keeps a lower-priority plugin from re-lowering the level after we have cancelled the drop.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!GameManager.isRoundLive(gameManager.getCurrentState())) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!gameManager.isParticipant(player.getUniqueId())) return;

        // Only a drop is blocked; anything that raises the bar may still do so.
        if (event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    /**
     * Fills the player's hunger bar so a round starts sprint-capable whatever they arrived with.
     * Called by {@code GameManager.beginPlay} for every participant when the clock starts: the
     * listener above only stops the bar going <em>down</em>, so a player who came in hungry would
     * otherwise stay hungry all round.
     */
    public static void fillHunger(Player player) {
        // Food level first: saturation is clamped to the current food level, so setting it before
        // the level would leave it at whatever the old level allowed.
        player.setFoodLevel(FULL_FOOD_LEVEL);
        player.setSaturation(START_SATURATION);
        player.setExhaustion(0f);
    }
}
