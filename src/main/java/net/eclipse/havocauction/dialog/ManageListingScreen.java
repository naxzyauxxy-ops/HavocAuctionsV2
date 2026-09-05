package net.eclipse.havocauction.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.manager.AuctionManager;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One of your own listings: preview it, or pull it off the board. */
public class ManageListingScreen extends Screen {

    private final UUID listingId;

    public ManageListingScreen(HavocAuction plugin, Player player, UUID listingId) {
        super(plugin, player);
        this.listingId = listingId;
    }

    @Override
    protected String configPath() {
        return "MANAGE-LISTING";
    }

    private Listing listing() {
        return plugin.auction().byId(listingId);
    }

    @Override
    protected Component title() {
        Listing listing = listing();
        return titleFrom(listing == null ? Map.of() : Placeholders.of(plugin, listing));
    }

    @Override
    protected List<DialogBody> body() {
        Listing listing = listing();
        if (listing == null) {
            return List.of(DialogBody.plainMessage(Text.component(plugin.message("LISTING-UNAVAILABLE"))));
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(itemBody(listing.getItemCopy()));
        body.addAll(Dialogs.body(lines("BODY"), Placeholders.of(plugin, listing)));
        return body;
    }

    @Override
    protected ActionButton exitButton() {
        return backButton("BACK", Map.of(), () -> new MyListingsScreen(plugin, player).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        Listing listing = listing();
        List<ActionButton> buttons = new ArrayList<>();
        if (listing == null) {
            buttons.add(backButton("BACK", Map.of(), () -> new MyListingsScreen(plugin, player).show()));
            return buttons;
        }

        Map<String, String> placeholders = Placeholders.of(plugin, listing);
        buttons.add(configButton("CANCEL-LISTING", placeholders, (view, audience) -> {
            AuctionManager.Result result = plugin.auction().cancel(player, listingId);
            tell(result.message());
            if (result.success()) success();
            else deny();
            new MyListingsScreen(plugin, player).show();
        }));

        if (listing.isPreviewable()) {
            buttons.add(configButton("PREVIEW", placeholders, (view, audience) -> {
                click();
                new ContainerPreviewScreen(plugin, player, listingId,
                        () -> new ManageListingScreen(plugin, player, listingId).show()).show();
            }));
        }
        return buttons;
    }
}
