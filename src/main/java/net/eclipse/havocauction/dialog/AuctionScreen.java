package net.eclipse.havocauction.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.util.Category;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The public auction board. */
public class AuctionScreen extends Screen {

    public AuctionScreen(HavocAuction plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "AUCTION";
    }

    private int perPage() {
        return Math.max(1, plugin.getConfig().getInt("DIALOG.LISTINGS-PER-PAGE", 21));
    }

    /**
     * Filtered and sorted board, recomputed only when a listing changed or the player
     * altered a filter. Turning a page is a sublist.
     */
    private List<Listing> results() {
        long version = plugin.auction().version();
        if (!session.isBoardStale(version)) return session.getCachedBoard();

        List<String> tokens = tokenise(session.getQuery());
        Category filter = session.getFilter();
        // Off by default: matching renamed items would let a block of dirt called
        // "Elytra" answer an elytra search.
        boolean searchCustomNames = plugin.getConfig().getBoolean("AUCTION.SEARCH-CUSTOM-NAMES", false);

        List<Listing> matched = new ArrayList<>();
        for (Listing listing : plugin.auction().board()) {
            if (!filter.matches(listing.getMaterial())) continue;
            if (!listing.matchesSearch(tokens, searchCustomNames)) continue;
            matched.add(listing);
        }
        matched.sort(session.getSort().getComparator());
        session.cacheBoard(matched, version);
        return matched;
    }

    /** Splits a query into words; every word has to match for a listing to show. */
    static List<String> tokenise(String query) {
        if (query == null || query.isBlank()) return List.of();
        List<String> tokens = new ArrayList<>();
        for (String part : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!part.isBlank()) tokens.add(part);
        }
        return tokens;
    }

    private Map<String, String> screen(List<Listing> results) {
        int pages = totalPages(results.size(), perPage());
        Map<String, String> map = new HashMap<>();
        map.put("page", String.valueOf(Math.min(session.getPage() + 1, pages)));
        map.put("pages", String.valueOf(pages));
        map.put("previous", String.valueOf(Math.max(1, session.getPage())));
        map.put("next", String.valueOf(Math.min(session.getPage() + 2, pages)));
        map.put("results", NumberUtil.count(results.size()));
        map.put("sort", plugin.sortName(session.getSort()));
        map.put("filter", plugin.categoryName(session.getFilter()));
        map.put("query", session.getQuery().isEmpty() ? "none" : session.getQuery());
        map.put("balance", NumberUtil.money(plugin.economy().balance(player)));
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
            body.add(DialogBody.plainMessage(Text.component(string("EMPTY", "&7Nothing listed."))));
        }
        return body;
    }

    @Override
    protected ActionButton exitButton() {
        return Dialogs.closeButton(button("CLOSE"), screen(results()), width(), style());
    }

    @Override
    protected List<ActionButton> buttons() {
        List<Listing> results = results();
        Map<String, String> screen = screen(results);
        int pages = totalPages(results.size(), perPage());
        List<ActionButton> buttons = new ArrayList<>();

        for (Listing listing : slice(results, session.getPage(), perPage())) {
            boolean mine = listing.getSeller().equals(player.getUniqueId());
            Map<String, String> placeholders = Placeholders.of(plugin, listing);
            buttons.add(configButton(mine ? "OWN-LISTING" : "LISTING", placeholders, (view, audience) -> {
                click();
                // Your own listing goes to its management screen rather than a purchase
                // dialog you are not allowed to complete.
                if (mine) {
                    new ManageListingScreen(plugin, player, listing.getId()).show();
                } else {
                    new ListingScreen(plugin, player, listing.getId()).show();
                }
            }));
        }

        if (session.getPage() > 0) {
            buttons.add(configButton("PREVIOUS", screen, (view, audience) -> {
                session.setPage(session.getPage() - 1);
                click();
                show();
            }));
        }
        if (session.getPage() < pages - 1) {
            buttons.add(configButton("NEXT", screen, (view, audience) -> {
                session.setPage(session.getPage() + 1);
                click();
                show();
            }));
        }

        buttons.add(configButton("SORT", screen, (view, audience) -> {
            session.setSort(session.getSort().next());
            click();
            show();
        }));
        buttons.add(configButton("FILTER", screen, (view, audience) -> {
            session.setFilter(session.getFilter().next());
            click();
            show();
        }));
        buttons.add(configButton("SEARCH", screen, (view, audience) -> {
            click();
            new SearchScreen(plugin, player, session.getQuery(), value -> {
                session.setQuery(value);
                new AuctionScreen(plugin, player).show();
            }, () -> new AuctionScreen(plugin, player).show()).show();
        }));
        buttons.add(configButton("MY-LISTINGS", screen, (view, audience) -> {
            click();
            new MyListingsScreen(plugin, player).show();
        }));
        return buttons;
    }
}
