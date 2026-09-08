package net.eclipse.havocauction.ui;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.dialog.Screen;
import net.eclipse.havocauction.util.ItemBuilder;
import net.eclipse.havocauction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the same screen as a chest menu, for servers that prefer the classic look.
 *
 * Layout is worked out from the button count rather than fixed slots: content buttons fill
 * from the top, and the screen's own controls sit on the bottom row, which keeps every
 * screen consistent without a slot map per menu in config.
 */
public class ChestRenderer implements Renderer {

    /** Holder so clicks can be traced back to the screen that drew them. */
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

    private final HavocAuction plugin;

    public ChestRenderer(HavocAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public void render(Screen screen) {
        List<ScreenModel.Button> buttons = new ArrayList<>(screen.buttons());
        List<ScreenModel.Input> inputs = screen.inputs();

        // Inputs become buttons that ask for the value in chat, because a chest menu has
        // nowhere to type. Dialog mode keeps them as proper private fields.
        for (ScreenModel.Input input : inputs) {
            buttons.add(promptButton(screen, input));
        }

        ScreenModel.Button exit = screen.exitButton();
        int controls = countControls(buttons) + (exit == null ? 0 : 1);
        int content = buttons.size() - countControls(buttons);
        int size = size(content, controls);

        View view = new View(screen);
        Inventory inventory = Bukkit.createInventory(view, size, Text.component(screen.title()));
        view.inventory = inventory;

        int controlRow = size - 9;
        int contentSlot = 0;
        int controlSlot = controlRow;

        for (ScreenModel.Button button : buttons) {
            boolean isControl = isControl(button);
            int slot = isControl ? controlSlot++ : contentSlot++;
            if (isControl && slot >= size) break;
            if (!isControl && slot >= controlRow) break;
            inventory.setItem(slot, icon(button));
            view.actions.put(slot, button);
        }

        if (exit != null && controlSlot < size) {
            inventory.setItem(size - 1, icon(exit));
            view.actions.put(size - 1, exit);
        }

        // The body text has no natural home in a chest, so it becomes an info item.
        List<String> body = new ArrayList<>(screen.bodyLines());
        if (!body.isEmpty() || screen.bodyIcon() != null) {
            ItemStack info = screen.bodyIcon() == null
                    ? ItemBuilder.of(Material.PAPER).name(screen.title()).lore(body).build()
                    : ItemBuilder.of(screen.bodyIcon()).lore(body).build();
            int slot = size - 5;
            if (view.buttonAt(slot) == null) inventory.setItem(slot, info);
        }

        screen.viewer().openInventory(inventory);
    }

    private ScreenModel.Button promptButton(Screen screen, ScreenModel.Input input) {
        List<String> tooltip = List.of(
                "&7Current: &f" + (input.initial() == null || input.initial().isEmpty()
                        ? "none" : input.initial()),
                "&8Typed in chat - other players cannot see it,",
                "&8but chat-logging plugins can.");
        return ScreenModel.Button.of("INPUT", input.label(), tooltip, null, Material.OAK_SIGN,
                responses -> plugin.prompts().request(screen.viewer(), input, screen));
    }

    private boolean isControl(ScreenModel.Button button) {
        String key = button.key();
        return key.equals("PREVIOUS") || key.equals("NEXT") || key.equals("SORT")
                || key.equals("FILTER") || key.equals("SEARCH") || key.equals("INPUT")
                || key.equals("CLOSE") || key.equals("BACK") || key.equals("MY-LISTINGS")
                || key.equals("MY-LISTINGS") || key.equals("REFRESH");
    }

    private int countControls(List<ScreenModel.Button> buttons) {
        int count = 0;
        for (ScreenModel.Button button : buttons) {
            if (isControl(button)) count++;
        }
        return count;
    }

    private int size(int content, int controls) {
        int rows = Math.max(1, (int) Math.ceil(content / 9.0D)) + 1;
        return Math.max(18, Math.min(54, rows * 9));
    }

    private ItemStack icon(ScreenModel.Button button) {
        ItemStack base = button.icon() != null
                ? button.icon().clone()
                : new ItemStack(button.fallbackIcon() == null ? Material.PAPER : button.fallbackIcon());
        return ItemBuilder.of(base).name(button.label()).lore(button.tooltip()).build();
    }
}
