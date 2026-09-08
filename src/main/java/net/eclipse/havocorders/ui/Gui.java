package net.eclipse.havocorders.ui;

import net.eclipse.havocorders.HavocOrders;
import net.eclipse.havocorders.dialog.Screen;
import net.eclipse.havocorders.util.ItemBuilder;
import net.eclipse.havocorders.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws a screen as a chest menu.
 *
 * Layout is driven entirely by menus.yml. Each menu declares its size, which slots hold
 * content, and a fixed slot for each named control, so a menu can be rearranged without
 * touching code and every screen keeps the same shape as players page through it.
 *
 * Anything the layout does not place is filled with a border item, which is what stops a
 * chest menu from looking like a pile of buttons in the top-left corner.
 */
public class Gui {

    /** Holder so a click can be traced back to the screen that drew it. */
    public static final class View implements InventoryHolder {

        private final Screen screen;
        private final Map<Integer, ScreenModel.Button> actions = new HashMap<>();
        private Inventory inventory;

        View(Screen screen) {
            this.screen = screen;
        }

        public Screen screen() {
            return screen;
        }

        public ScreenModel.Button buttonAt(int slot) {
            return actions.get(slot);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final HavocOrders plugin;

    public Gui(HavocOrders plugin) {
        this.plugin = plugin;
    }

    public void render(Screen screen) {
        ConfigurationSection layout = screen.layout();
        int size = size(layout);

        View view = new View(screen);
        Inventory inventory = Bukkit.createInventory(view, size,
                Text.component(screen.style().text(screen.title())));
        view.inventory = inventory;

        List<ScreenModel.Button> buttons = new ArrayList<>(screen.buttons());
        for (ScreenModel.Input input : screen.inputs()) {
            buttons.add(promptButton(screen, input));
        }
        ScreenModel.Button exit = screen.exitButton();
        if (exit != null) buttons.add(exit);

        List<Integer> contentSlots = contentSlots(layout, size);
        Set<Integer> used = new LinkedHashSet<>();

        // Controls first: they have reserved slots, so paging never shifts them around.
        List<ScreenModel.Button> content = new ArrayList<>();
        for (ScreenModel.Button button : buttons) {
            int slot = slotFor(layout, button.key(), size);
            if (slot < 0) {
                content.add(button);
                continue;
            }
            place(inventory, view, slot, button);
            used.add(slot);
        }

        int index = 0;
        for (ScreenModel.Button button : content) {
            while (index < contentSlots.size() && used.contains(contentSlots.get(index))) index++;
            if (index >= contentSlots.size()) break;
            place(inventory, view, contentSlots.get(index++), button);
        }

        renderInfo(screen, layout, inventory, view, size);
        fill(layout, inventory);

        screen.viewer().openInventory(inventory);
    }

    // ------------------------------------------------------------------ pieces

    private void place(Inventory inventory, View view, int slot, ScreenModel.Button button) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, icon(button));
        view.actions.put(slot, button);
    }

    private ItemStack icon(ScreenModel.Button button) {
        ItemStack base = button.icon() != null
                ? button.icon().clone()
                : new ItemStack(button.fallbackIcon() == null ? Material.PAPER : button.fallbackIcon());
        return ItemBuilder.of(base).name(button.label()).lore(button.tooltip()).build();
    }

    /** The body text lives on an information item rather than being lost. */
    private void renderInfo(Screen screen, ConfigurationSection layout, Inventory inventory,
                            View view, int size) {
        List<String> body = screen.bodyLines();
        if (body.isEmpty() && screen.bodyIcon() == null) return;

        int slot = layout == null ? -1 : layout.getInt("INFO-SLOT", -1);
        if (slot < 0 || slot >= size || view.buttonAt(slot) != null) return;

        Material material = Material.matchMaterial(
                layout.getString("INFO-MATERIAL", "PAPER"));
        ItemStack base = screen.bodyIcon() != null
                ? screen.bodyIcon().clone()
                : new ItemStack(material == null ? Material.PAPER : material);

        inventory.setItem(slot, ItemBuilder.of(base)
                .name(screen.style().text(screen.title()))
                .lore(body)
                .build());
    }

    private void fill(ConfigurationSection layout, Inventory inventory) {
        if (layout == null || !layout.getBoolean("FILL", true)) return;
        ConfigurationSection filler = layout.getConfigurationSection("FILLER");

        String materialName = filler == null
                ? plugin.menuString("FILLER.MATERIAL", "GRAY_STAINED_GLASS_PANE")
                : filler.getString("MATERIAL", "GRAY_STAINED_GLASS_PANE");
        String name = filler == null
                ? plugin.menuString("FILLER.NAME", " ")
                : filler.getString("NAME", " ");

        Material material = Material.matchMaterial(materialName);
        if (material == null || material == Material.AIR) return;

        ItemStack pane = ItemBuilder.of(material).name(name).build();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, pane);
        }
    }

    private ScreenModel.Button promptButton(Screen screen, ScreenModel.Input input) {
        List<String> tooltip = List.of(
                "&7Current: &f" + (input.initial() == null || input.initial().isEmpty()
                        ? "none" : input.initial()),
                "",
                "&fClick to type a new value");
        return ScreenModel.Button.of("INPUT", input.label(), tooltip, null, Material.OAK_SIGN,
                responses -> plugin.prompts().request(screen.viewer(), input, screen));
    }

    // ------------------------------------------------------------------ layout config

    private int size(ConfigurationSection layout) {
        int size = layout == null ? 54 : layout.getInt("SIZE", 54);
        if (size % 9 != 0 || size < 9 || size > 54) size = 54;
        return size;
    }

    private int slotFor(ConfigurationSection layout, String key, int size) {
        if (layout == null) return -1;
        ConfigurationSection slots = layout.getConfigurationSection("SLOTS");
        if (slots == null || !slots.contains(key)) return -1;
        int slot = slots.getInt(key, -1);
        return slot >= 0 && slot < size ? slot : -1;
    }

    /**
     * Slots that repeat content fills. Defaults to the classic inset rectangle, which
     * leaves a one-slot border all the way round.
     */
    private List<Integer> contentSlots(ConfigurationSection layout, int size) {
        if (layout != null && layout.isList("CONTENT-SLOTS")) {
            List<Integer> slots = new ArrayList<>();
            for (int slot : layout.getIntegerList("CONTENT-SLOTS")) {
                if (slot >= 0 && slot < size) slots.add(slot);
            }
            if (!slots.isEmpty()) return slots;
        }

        List<Integer> slots = new ArrayList<>();
        int rows = size / 9;
        for (int row = 1; row < rows - 1; row++) {
            for (int column = 1; column <= 7; column++) {
                slots.add(row * 9 + column);
            }
        }
        return slots.isEmpty() ? defaultFlat(size) : slots;
    }

    private List<Integer> defaultFlat(int size) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < size - 9; slot++) slots.add(slot);
        return slots;
    }
}
