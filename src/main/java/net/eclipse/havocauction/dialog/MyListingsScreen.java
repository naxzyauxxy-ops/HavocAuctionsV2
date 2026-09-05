package net.eclipse.havocauction.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Your live listings, plus the way into selling, collecting and history. */
public class MyListingsScreen extends Screen {

    /** Computed once per draw: title, body, buttons and footer all need it. */
    private List<Listing> cached;

    public MyListingsScreen(HavocAuction plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void show() {
        cached = null;
        super.show();
    }

    @Override
    protected String configPath() {
        return "MY-LISTINGS";
    }

    private int perPage() {
        return Math.max(1, plugin.getConfig().getInt("DIALOG.LISTINGS-PER-PAGE", 21));
    }

    private List<Listing> results() {
        if (cached == null) cached = plugin.auction().activeOf(player.getUniqueId());
        return cached;
    }

    private Map<String, String> screen(List<Listing> results) {
        int pages = totalPages(results.size(), perPage());
        Map<String, String> map = new HashMap<>();
        map.put("count", NumberUtil.count(results.size()));
        map.put("collectable", NumberUtil.count(plugin.auction().collectable(player.getUniqueId()).size()));
        map.put("collectable_items", NumberUtil.count(plugin.auction().collectableCount(player.getUniqueId())));
        map.put("page", String.valueOf(Math.min(session.getMyListingsPage() + 1, pages)));
        map.put("pages", String.valueOf(pages));
        map.put("value", NumberUtil.money(results.stream().mapToDouble(Listing::getPrice).sum()));
        map.put("alerts", plugin.profiles().alertsEnabled(player.getUniqueId()) ? "ON" : "OFF");
        map.put("fast_buy", plugin.profiles().fastBuy(player.getUniqueId()) ? "ON" : "OFF");
        return map;
    }

    @Override
    protected Component title() {
        return titleFrom(screen(results()));
    }

    @Override
    protected List<DialogBody> body() {
        List<Listing> results = results();
        List<DialogBody> body = Dialogs.body(lines("BODY"), screen(results));
        if (results.isEmpty()) {
            body.add(DialogBody.plainMessage(Text.component(string("EMPTY", "&7You have nothing listed."))));
        }
        return body;
    }

    @Override
    protected ActionButton exitButton() {
        return backButton("BACK", screen(results()), () -> new AuctionScreen(plugin, player).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        List<Listing> results = results();
        Map<String, String> screen = screen(results);
        int pages = totalPages(results.size(), perPage());
        List<ActionButton> buttons = new ArrayList<>();

        for (Listing listing : slice(results, session.getMyListingsPage(), perPage())) {
            buttons.add(configButton("LISTING", Placeholders.of(plugin, listing), (view, audience) -> {
                click();
                new ManageListingScreen(plugin, player, listing.getId()).show();
            }));
        }

        if (session.getMyListingsPage() > 0) {
            buttons.add(configButton("PREVIOUS", screen, (view, audience) -> {
                session.setMyListingsPage(session.getMyListingsPage() - 1);
                click();
                show();
            }));
        }
        if (session.getMyListingsPage() < pages - 1) {
            buttons.add(configButton("NEXT", screen, (view, audience) -> {
                session.setMyListingsPage(session.getMyListingsPage() + 1);
                click();
                show();
            }));
        }

        buttons.add(configButton("SELL", screen, (view, audience) -> {
            click();
            new SellScreen(plugin, player).show();
        }));
        buttons.add(configButton("COLLECT", screen, (view, audience) -> {
            click();
            new CollectScreen(plugin, player).show();
        }));
        buttons.add(configButton("TRANSACTIONS", screen, (view, audience) -> {
            click();
            new TransactionsScreen(plugin, player).show();
        }));
        buttons.add(configButton("ALERTS", screen, (view, audience) -> {
            boolean enabled = plugin.profiles().toggleAlerts(player.getUniqueId());
            click();
            tell(plugin.message(enabled ? "ALERTS-ON" : "ALERTS-OFF"));
            show();
        }));
        buttons.add(configButton("FAST-BUY", screen, (view, audience) -> {
            boolean enabled = plugin.profiles().toggleFastBuy(player.getUniqueId());
            click();
            tell(plugin.message(enabled ? "FAST-BUY-ON" : "FAST-BUY-OFF"));
            show();
        }));
        return buttons;
    }
}
