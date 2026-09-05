package net.eclipse.havocauction.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.manager.Session;
import net.eclipse.havocauction.util.Bedrock;
import net.eclipse.havocauction.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** Base for every dialog screen: config lookup, sounds, and the show call. */
public abstract class Screen {

    protected final HavocAuction plugin;
    protected final Player player;
    protected final Session session;

    protected Screen(HavocAuction plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.session = plugin.sessions().get(player);
    }

    /** Section name under DIALOGS in dialogs.yml. */
    protected abstract String configPath();

    protected abstract Component title();

    protected abstract List<DialogBody> body();

    protected List<DialogInput> inputs() {
        return List.of();
    }

    protected abstract List<ActionButton> buttons();

    /** Footer button, shown under the grid. Usually back or close. */
    protected ActionButton exitButton() {
        return null;
    }

    /** Grid width. A per-dialog COLUMNS entry wins over the global default. */
    protected int columns() {
        int fallback = plugin.getConfig().getInt("DIALOG.COLUMNS", 3);
        ConfigurationSection section = section();
        int columns = section == null ? fallback : section.getInt("COLUMNS", fallback);
        return Math.max(1, columns);
    }

    /** Item preview body at the configured size. */
    protected DialogBody itemBody(org.bukkit.inventory.ItemStack stack) {
        return Dialogs.item(stack, plugin.getConfig().getInt("DIALOG.ITEM-SIZE", 48));
    }

    /** Button width in pixels. The API caps this at 1024. */
    protected int width() {
        int width = plugin.getConfig().getInt("DIALOG.BUTTON-WIDTH", 200);
        return Math.max(1, Math.min(1024, width));
    }

    /** Placeholders every screen gets, whatever else it adds. */
    protected Map<String, String> common(Map<String, String> placeholders) {
        Map<String, String> merged = new java.util.HashMap<>(placeholders);
        merged.putIfAbsent("separator",
                plugin.line("SEPARATOR", "&8&m                                                  "));
        merged.putIfAbsent("player", player.getName());
        return merged;
    }

    protected ConfigurationSection section() {
        return plugin.dialogSection(configPath());
    }

    protected ConfigurationSection button(String key) {
        ConfigurationSection section = section();
        if (section == null) return null;
        ConfigurationSection buttons = section.getConfigurationSection("BUTTONS");
        return buttons == null ? null : buttons.getConfigurationSection(key);
    }

    protected String string(String key, String fallback) {
        ConfigurationSection section = section();
        return section == null ? fallback : section.getString(key, fallback);
    }

    protected List<String> lines(String key) {
        ConfigurationSection section = section();
        return section == null ? List.of() : section.getStringList(key);
    }

    /** Rendering rules for whoever is looking at this screen. */
    protected Dialogs.Style style() {
        if (!Bedrock.isBedrock(player)) return Dialogs.Style.JAVA;
        return new Dialogs.Style(
                plugin.getConfig().getBoolean("BEDROCK.ASCII-LABELS", true),
                plugin.getConfig().getBoolean("BEDROCK.INLINE-TOOLTIPS", true));
    }

    protected boolean isBedrock() {
        return Bedrock.isBedrock(player);
    }

    protected ActionButton configButton(String key, Map<String, String> placeholders,
                                        DialogActionCallback callback) {
        return Dialogs.fromConfig(button(key), placeholders, width(), callback, style());
    }

    /** Footer button that just navigates somewhere else. */
    protected ActionButton backButton(String key, Map<String, String> placeholders,
                                      Runnable target) {
        return configButton(key, placeholders, (view, audience) -> {
            click();
            target.run();
        });
    }

    protected Component titleFrom(Map<String, String> placeholders) {
        return Text.component(style().text(Text.apply(string("TITLE", "Menu"), placeholders)));
    }

    public void show() {
        Dialog dialog = Dialogs.build(title(), body(), inputs(), buttons(),
                exitButton(), columns());
        player.showDialog(dialog);
    }

    /** Re-show this screen after an action. Runs on the main thread next tick. */
    protected void reopen() {
        plugin.sync(this::show);
    }

    protected void tell(String message) {
        if (message == null || message.isEmpty()) return;
        player.sendMessage(Text.component(message));
    }

    protected void click() {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5F, 1.2F);
    }

    protected void success() {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.0F);
    }

    protected void deny() {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.6F);
    }

    // ------------------------------------------------------------------ paging

    protected static int totalPages(int elements, int perPage) {
        return Math.max(1, (int) Math.ceil(elements / (double) perPage));
    }

    protected static <T> List<T> slice(List<T> all, int page, int perPage) {
        int total = totalPages(all.size(), perPage);
        int safePage = Math.min(Math.max(0, page), total - 1);
        int from = safePage * perPage;
        int to = Math.min(all.size(), from + perPage);
        return from >= to ? List.of() : all.subList(from, to);
    }
}
