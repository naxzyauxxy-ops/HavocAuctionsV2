package net.eclipse.havocauction.ui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.Listener;
import net.eclipse.havocauction.HavocAuction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

/** Routes chest-menu clicks back to the screen's button actions. */
public class ChestListener implements Listener {

    private final HavocAuction plugin;

    public ChestListener(HavocAuction plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ChestRenderer.View view)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(view.getInventory())) {
            return;
        }

        ScreenModel.Button button = view.buttonAt(event.getRawSlot());
        if (button == null) return;

        if (button.action() == null) {
            view.screen().viewer().closeInventory();
            return;
        }
        // Chest menus collect typed values through chat prompts, not the menu itself.
        ScreenModel.Responses responses = event.getWhoClicked() instanceof Player player
                ? plugin.prompts().responsesFor(player)
                : ScreenModel.Responses.EMPTY;
        button.action().run(responses);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ChestRenderer.View) {
            event.setCancelled(true);
        }
    }
}
