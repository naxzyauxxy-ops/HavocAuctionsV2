package net.eclipse.havocauction.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Private text input. The value travels from the client to the server with the button
 * click, so it never appears in chat or in any chat-logging plugin.
 */
public class SearchScreen extends Screen {

    private static final String KEY = "query";

    private final String initial;
    private final Consumer<String> onSubmit;
    private final Runnable onBack;

    public SearchScreen(HavocAuction plugin, Player player, String initial,
                        Consumer<String> onSubmit, Runnable onBack) {
        super(plugin, player);
        this.initial = initial == null ? "" : initial;
        this.onSubmit = onSubmit;
        this.onBack = onBack;
    }

    @Override
    protected String configPath() {
        return "SEARCH";
    }

    @Override
    protected Component title() {
        return titleFrom(Map.of());
    }

    @Override
    protected List<DialogBody> body() {
        return Dialogs.body(lines("BODY"), Map.of());
    }

    @Override
    protected List<DialogInput> inputs() {
        return List.of(DialogInput.text(KEY, Text.component(string("INPUT-LABEL", "&fSearch")))
                .initial(initial)
                .build());
    }

    @Override
    protected ActionButton exitButton() {
        return backButton("BACK", Map.of(), onBack);
    }

    @Override
    protected List<ActionButton> buttons() {
        return List.of(
                configButton("CONFIRM", Map.of(), (view, audience) -> {
                    String value = view.getText(KEY);
                    click();
                    if (value == null || value.isBlank()) {
                        if (isBedrock()) tell(plugin.message("SEARCH-USE-COMMAND"));
                        onSubmit.accept(initial);
                        return;
                    }
                    onSubmit.accept(value.trim());
                }),
                configButton("CLEAR", Map.of(), (view, audience) -> {
                    click();
                    onSubmit.accept("");
                })
        );
    }
}
