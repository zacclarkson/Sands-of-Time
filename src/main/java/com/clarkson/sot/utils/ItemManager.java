package com.clarkson.sot.utils; // Or your preferred package

import com.clarkson.sot.dungeon.VaultColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Manages custom ItemStacks for the Sands of Time plugin.
 * Defines NamespacedKeys, creates custom items (keys, tools),
 * and provides methods to identify them.
 */
public final class ItemManager {

    private static Plugin pluginInstance; // Store plugin instance for keys

    // --- Prevent Instantiation ---
    private ItemManager() { }

    // --- Key Strings (Private Constants) ---
    private static final String KEY_TYPE_STR = "sot_key_type";
    private static final String VAULT_COLOR_STR = "sot_vault_color";
    private static final String TOOL_TYPE_STR = "sot_tool_type";
    private static final String TOOL_VALUE_STR = "sot_tool_value";
    // Add other key strings if needed (e.g., for floor items if managed here)

    // --- Public NamespacedKey Objects (Initialized once) ---
    public static NamespacedKey KEY_TYPE;
    public static NamespacedKey VAULT_COLOR;
    public static NamespacedKey TOOL_TYPE;
    public static NamespacedKey TOOL_VALUE;
    // Add other public keys here...

    // --- Public Constant String Values ---
    // Key Types
    public static final String RUSTY_KEY_VALUE = "RUSTY";
    public static final String VAULT_KEY_VALUE = "VAULT";
    // Tool Types
    public static final String TOOL_TYPE_COIN_PLACER = "COIN_PLACER";
    public static final String TOOL_TYPE_ITEM_SPAWN_PLACER = "ITEM_SPAWN_PLACER";
    public static final String TOOL_TYPE_ENTRY_POINT_PLACER = "ENTRY_POINT_PLACER";

    // --- Custom Model Data IDs (Example) ---
    // Define these centrally if used across items
    // public static final int RUSTY_KEY_MODEL_ID = 2001;
    // public static final int VAULT_KEY_BLUE_MODEL_ID = 2011;
    // public static final int VAULT_KEY_RED_MODEL_ID = 2012;
    // ... etc ...


    /**
     * Initializes all static NamespacedKey fields.
     * MUST be called once during plugin startup (e.g., in onEnable).
     * @param plugin The instance of the main plugin.
     */
    public static void initializeKeys(@NotNull Plugin plugin) {
        Objects.requireNonNull(plugin, "Plugin instance cannot be null for key initialization");
        if (KEY_TYPE != null) {
            plugin.getLogger().warning("ItemManager Keys already initialized!");
            return;
        }
        pluginInstance = plugin; // Store for potential future use if needed
        plugin.getLogger().info("Initializing ItemManager Keys...");

        KEY_TYPE = new NamespacedKey(plugin, KEY_TYPE_STR);
        VAULT_COLOR = new NamespacedKey(plugin, VAULT_COLOR_STR);
        TOOL_TYPE = new NamespacedKey(plugin, TOOL_TYPE_STR);
        TOOL_VALUE = new NamespacedKey(plugin, TOOL_VALUE_STR);
        // Initialize other keys here...

        plugin.getLogger().info("ItemManager Keys initialized.");
    }

    // --- Item Creation Methods ---

    /** Creates the ItemStack for a Rusty Key. */
    @NotNull
    public static ItemStack createRustyKey() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK); // Base material
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Rusty Key", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text("Unlocks standard dungeon doors.", NamedTextColor.GRAY)
                         .decoration(TextDecoration.ITALIC, false)
            ));
            // Set PDC Tag
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_TYPE, PersistentDataType.STRING, RUSTY_KEY_VALUE);
            // Set Custom Model Data if applicable
            // meta.setCustomModelData(RUSTY_KEY_MODEL_ID);
            key.setItemMeta(meta);
        }
        return key;
    }

    /** Creates the ItemStack for a specific colored Vault Key. */
    @NotNull
    public static ItemStack createVaultKey(@NotNull VaultColor color) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK); // Base material
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            TextColor keyColor = getVaultColorTextColor(color);
            meta.displayName(Component.text(color.name() + " Vault Key", keyColor, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text("Unlocks the ", NamedTextColor.GRAY)
                         .append(Component.text(color.name().toLowerCase(), keyColor))
                         .append(Component.text(" vault.", NamedTextColor.GRAY))
                         .decoration(TextDecoration.ITALIC, false)
            ));
            // Set PDC Tags
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_TYPE, PersistentDataType.STRING, VAULT_KEY_VALUE);
            pdc.set(VAULT_COLOR, PersistentDataType.STRING, color.name());
            // Set Custom Model Data if applicable
            // meta.setCustomModelData(getCustomModelDataForKey(color));
            key.setItemMeta(meta);
        }
        return key;
    }

    /** Creates the ItemStack for the Coin Placer tool. */
    @NotNull
    public static ItemStack createCoinPlacerTool(int value) {
        ItemStack tool = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gradient:gold:yellow><bold>Coin Placer Tool</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Value: ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Right-click to place a visual", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("coin display for segment design.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(TOOL_TYPE, PersistentDataType.STRING, TOOL_TYPE_COIN_PLACER);
            pdc.set(TOOL_VALUE, PersistentDataType.INTEGER, value);
            tool.setItemMeta(meta);
        }
        return tool;
    }

    /** Creates the ItemStack for the Item Spawn Placer tool. */
     @NotNull
     public static ItemStack createItemSpawnPlacerTool() {
         ItemStack tool = new ItemStack(Material.IRON_SHOVEL);
         ItemMeta meta = tool.getItemMeta();
         if (meta != null) {
             meta.displayName(MiniMessage.miniMessage().deserialize("<gradient:aqua:blue><bold>Item Spawn Placer</bold></gradient>"));
             List<Component> lore = new ArrayList<>();
             lore.add(Component.empty());
             lore.add(Component.text("Right-click block to place an", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
             lore.add(Component.text("item spawn marker (Torch).", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
             lore.add(Component.text("Left-click marker to remove.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
             meta.lore(lore);
             PersistentDataContainer pdc = meta.getPersistentDataContainer();
             pdc.set(TOOL_TYPE, PersistentDataType.STRING, TOOL_TYPE_ITEM_SPAWN_PLACER);
             tool.setItemMeta(meta);
         }
         return tool;
     }

     /** Creates the ItemStack for the Entry Point Placer tool. */
      @NotNull
      public static ItemStack createEntryPointPlacerTool() {
          ItemStack tool = new ItemStack(Material.ARROW);
          ItemMeta meta = tool.getItemMeta();
          if (meta != null) {
              meta.displayName(MiniMessage.miniMessage().deserialize("<gradient:light_purple:dark_purple><bold>Entry Point Placer</bold></gradient>"));
              List<Component> lore = new ArrayList<>();
              lore.add(Component.empty());
              lore.add(Component.text("Right-click block face to place.", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
              lore.add(Component.text("Right-click existing marker to rotate.", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
              lore.add(Component.text("Left-click marker to remove.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
              meta.lore(lore);
              PersistentDataContainer pdc = meta.getPersistentDataContainer();
              pdc.set(TOOL_TYPE, PersistentDataType.STRING, TOOL_TYPE_ENTRY_POINT_PLACER);
              tool.setItemMeta(meta);
          }
          return tool;
      }


    // --- Item Checking Methods ---

    /** Safely gets the PDC from an ItemStack, handling nulls. */
    @Nullable
    private static PersistentDataContainer getPDC(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return (meta != null) ? meta.getPersistentDataContainer() : null;
    }

    /** Checks if an ItemStack is a Rusty Key. */
    public static boolean isRustyKey(@Nullable ItemStack item) {
        PersistentDataContainer pdc = getPDC(item);
        if (pdc == null || KEY_TYPE == null) return false;
        return RUSTY_KEY_VALUE.equals(pdc.get(KEY_TYPE, PersistentDataType.STRING));
    }

    /** Checks if an ItemStack is any Vault Key. */
    public static boolean isVaultKey(@Nullable ItemStack item) {
        PersistentDataContainer pdc = getPDC(item);
        if (pdc == null || KEY_TYPE == null) return false;
        return VAULT_KEY_VALUE.equals(pdc.get(KEY_TYPE, PersistentDataType.STRING));
    }

    /** Gets the VaultColor from a Vault Key ItemStack, returns null if not a valid vault key or color tag missing/invalid. */
    @Nullable
    public static VaultColor getVaultKeyColor(@Nullable ItemStack item) {
        PersistentDataContainer pdc = getPDC(item);
        if (pdc == null || KEY_TYPE == null || VAULT_COLOR == null) return null;

        // Check if it's actually a vault key first
        if (!VAULT_KEY_VALUE.equals(pdc.get(KEY_TYPE, PersistentDataType.STRING))) {
            return null;
        }

        String colorString = pdc.get(VAULT_COLOR, PersistentDataType.STRING);
        if (colorString != null) {
            try {
                return VaultColor.valueOf(colorString);
            } catch (IllegalArgumentException e) {
                if (pluginInstance != null) pluginInstance.getLogger().warning("Item has invalid VaultColor string in PDC: " + colorString);
                return null;
            }
        }
        if (pluginInstance != null) pluginInstance.getLogger().warning("Vault key item missing color tag!");
        return null;
    }

     /** Checks if an ItemStack is the Coin Placer tool. */
     public static boolean isCoinPlacerTool(@Nullable ItemStack item) {
         PersistentDataContainer pdc = getPDC(item);
         if (pdc == null || TOOL_TYPE == null) return false;
         return TOOL_TYPE_COIN_PLACER.equals(pdc.get(TOOL_TYPE, PersistentDataType.STRING));
     }

     /** Gets the value stored on a Coin Placer tool. Returns null if not a coin tool or value missing. */
     @Nullable
     public static Integer getCoinToolValue(@Nullable ItemStack item) {
         PersistentDataContainer pdc = getPDC(item);
         if (pdc == null || TOOL_TYPE == null || TOOL_VALUE == null) return null;
         if (!TOOL_TYPE_COIN_PLACER.equals(pdc.get(TOOL_TYPE, PersistentDataType.STRING))) {
             return null; // Not the right tool type
         }
         return pdc.get(TOOL_VALUE, PersistentDataType.INTEGER); // Returns null if key missing
     }

     /** Checks if an ItemStack is the Item Spawn Placer tool. */
     public static boolean isItemSpawnPlacerTool(@Nullable ItemStack item) {
         PersistentDataContainer pdc = getPDC(item);
         if (pdc == null || TOOL_TYPE == null) return false;
         return TOOL_TYPE_ITEM_SPAWN_PLACER.equals(pdc.get(TOOL_TYPE, PersistentDataType.STRING));
     }

      /** Checks if an ItemStack is the Entry Point Placer tool. */
      public static boolean isEntryPointPlacerTool(@Nullable ItemStack item) {
          PersistentDataContainer pdc = getPDC(item);
          if (pdc == null || TOOL_TYPE == null) return false;
          return TOOL_TYPE_ENTRY_POINT_PLACER.equals(pdc.get(TOOL_TYPE, PersistentDataType.STRING));
      }

    // --- Utility Methods ---

    /** Gets the Adventure API TextColor for a vault color. */
    private static TextColor getVaultColorTextColor(VaultColor color) {
        switch (color) {
            case BLUE: return NamedTextColor.BLUE;
            case RED: return NamedTextColor.RED;
            case GREEN: return NamedTextColor.GREEN;
            case GOLD: return NamedTextColor.GOLD;
            default: return NamedTextColor.WHITE;
        }
    }

    // TODO: Add method to get CustomModelData for keys if needed
    // private static int getCustomModelDataForKey(VaultColor color) { ... }

}
