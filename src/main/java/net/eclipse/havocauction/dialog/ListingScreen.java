package net.eclipse.havocauction.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.manager.AuctionManager;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Buy confirmation. Skipped entirely for players with fast buy enabled. */
public class ListingScreen extends Screen {

    private final UUID listingId;

    public ListingScreen(HavocAuction plugin, Player player, UUID listingId) {
        super(plugin, player);
        this.listingId = listingId;
    }

    @Override
    protected String configPath() {
        return "CONFIRM-PURCHASE";
    }

    private Listing listing() {
        return plugin.auction().byId(listingId);
    }

    private Map<String, String> placeholders(Listing listing) {
        Map<String, String> map = Placeholders.of(plugin, listing);
        map.put("balance", NumberUtil.money(plugin.economy().balance(player)));
        map.put("after", NumberUtil.money(plugin.economy().balance(player) - listing.getPrice()));
        return map;
    }

    /** Buys straight away when the player has turned confirmations off. */
    public void showOrBuy() {
        if (plugin.profiles().fastBuy(player.getUniqueId())) {
            purchase();
        } else {
            show();
        }
    }

    @Override
    protected Component title() {
        Listing listing = listing();
        return titleFrom(listing == null ? Map.of() : placeholders(listing));
    }

    @Override
    protected List<DialogBody> body() {
        Listing listing = listing();
        if (listing == null) {
            return List.of(DialogBody.plainMessage(Text.component(plugin.message("LISTING-UNAVAILABLE"))));
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(itemBody(listing.getItemCopy()));
        body.addAll(Dialogs.body(lines("BODY"), placeholders(listing)));
        return body;
    }

    @Override
    protected ActionButton exitButton() {
        return backButton("BACK", Map.of(), () -> new AuctionScreen(plugin, player).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        Listing listing = listing();
        List<ActionButton> buttons = new ArrayList<>();
        if (listing == null) {
            buttons.add(backButton("BACK", Map.of(), () -> new AuctionScreen(plugin, player).show()));
            return buttons;
        }

        Map<String, String> placeholders = placeholders(listing);
        buttons.add(configButton("CONFIRM", placeholders, (view, audience) -> purchase()));

        if (listing.isPreviewable()) {
            buttons.add(configButton("PREVIEW", placeholders, (view, audience) -> {
                click();
                new ContainerPreviewScreen(plugin, player, listingId,
                        () -> new ListingScreen(plugin, player, listingId).show()).show();
            }));
        }
        return buttons;
    }

    private void purchase() {
        AuctionManager.Result result = plugin.auction().buy(player, listingId);
        tell(result.message());
        if (result.success()) success();
        else deny();
        new AuctionScreen(plugin, player).show();
    }
}
