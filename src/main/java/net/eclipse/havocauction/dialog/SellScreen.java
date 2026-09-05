package net.eclipse.havocauction.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.manager.AuctionManager;
import net.eclipse.havocauction.util.ItemNames;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists whatever is in the player's main hand.
 *
 * Dialogs have no item slot, so the held item is the input. That is also how the legacy
 * /ah sell command worked, and it removes a whole class of item-duplication bugs that
 * come with a deposit slot.
 */
public class SellScreen extends Screen {

    private static final String PRICE = "price";

    public SellScreen(HavocAuction plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "SELL";
    }

    private ItemStack held() {
        ItemStack stack = player.getInventory().getItemInMainHand();
        return stack == null || stack.getType() == Material.AIR ? null : stack;
    }

    private Map<String, String> placeholders() {
        ItemStack held = held();
        double price = session.getDraftPrice();
        double fee = plugin.auction().listingFee(price);
        double tax = price * Math.max(0, plugin.getConfig().getDouble("AUCTION.TAX-PERCENT", 0)) / 100D;

        Map<String, String> map = new HashMap<>();
        map.put("item", held == null ? "nothing" : ItemNames.display(held));
        map.put("amount", held == null ? "0" : NumberUtil.count(held.getAmount()));
        map.put("price", NumberUtil.money(price));
        map.put("fee", NumberUtil.money(fee));
        map.put("tax", NumberUtil.money(tax));
        map.put("payout", NumberUtil.money(Math.max(0, price - tax)));
        map.put("unit_price", held == null || held.getAmount() == 0
                ? NumberUtil.money(price)
                : NumberUtil.money(price / held.getAmount()));
        map.put("balance", NumberUtil.money(plugin.economy().balance(player)));
        map.put("listings", NumberUtil.count(plugin.auction().activeCount(player.getUniqueId())));
        return map;
    }

    @Override
    protected Component title() {
        return titleFrom(placeholders());
    }

    @Override
    protected List<DialogBody> body() {
        List<DialogBody> body = new ArrayList<>();
        ItemStack held = held();
        if (held != null) body.add(itemBody(held.clone()));
        body.addAll(Dialogs.body(lines(held == null ? "BODY-EMPTY" : "BODY"), placeholders()));
        return body;
    }

    @Override
    protected List<DialogInput> inputs() {
        double price = session.getDraftPrice();
        return List.of(DialogInput.text(PRICE, Text.component(string("PRICE-LABEL", "&fPrice")))
                .initial(price > 0 ? NumberUtil.exact(price) : "")
                .build());
    }

    @Override
    protected ActionButton exitButton() {
        return backButton("BACK", placeholders(), () -> new MyListingsScreen(plugin, player).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        Map<String, String> placeholders = placeholders();
        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(configButton("CONFIRM", placeholders, (view, audience) -> {
            String typed = view.getText(PRICE);
            // A blank box on Bedrock is Geyser losing the value, not the player asking
            // for a free listing. Fall back to any price already set, then the command.
            Double price = typed == null || typed.isBlank()
                    ? (session.getDraftPrice() > 0 ? session.getDraftPrice() : null)
                    : NumberUtil.parse(typed);
            if (price == null || price <= 0) {
                deny();
                tell(plugin.message(isBedrock() ? "SELL-USE-COMMAND" : "PRICE-INVALID"));
                show();
                return;
            }
            session.setDraftPrice(price);

            ItemStack held = held();
            if (held == null) {
                deny();
                tell(plugin.message("NO-ITEM"));
                show();
                return;
            }

            if (plugin.profiles().fastBuy(player.getUniqueId())) {
                submit();
            } else {
                new ConfirmListingScreen(plugin, player).show();
            }
        }));

        buttons.add(configButton("PRICE-PER-ITEM", placeholders, (view, audience) -> {
            // Treat the typed number as a per-item price and scale it to the stack.
            String typedEach = view.getText(PRICE);
            Double each = typedEach == null || typedEach.isBlank() ? null : NumberUtil.parse(typedEach);
            ItemStack held = held();
            if (each == null || each <= 0 || held == null) {
                deny();
                tell(plugin.message("PRICE-INVALID"));
                show();
                return;
            }
            session.setDraftPrice(each * held.getAmount());
            click();
            show();
        }));
        return buttons;
    }

    /** Actually list the held stack and take it out of the player's hand. */
    void submit() {
        ItemStack held = held();
        if (held == null) {
            deny();
            tell(plugin.message("NO-ITEM"));
            new MyListingsScreen(plugin, player).show();
            return;
        }

        AuctionManager.Result result = plugin.auction().list(player, held, session.getDraftPrice());
        tell(result.message());
        if (result.success()) {
            // Only remove the item once the listing is definitely stored.
            player.getInventory().setItemInMainHand(null);
            success();
            session.setDraftPrice(0);
            new MyListingsScreen(plugin, player).show();
        } else {
            deny();
            show();
        }
    }
}
