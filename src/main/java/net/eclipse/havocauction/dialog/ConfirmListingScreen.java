package net.eclipse.havocauction.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocauction.HavocAuction;
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

public class ConfirmListingScreen extends Screen {

    public ConfirmListingScreen(HavocAuction plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "CONFIRM-LISTING";
    }

    private ItemStack held() {
        ItemStack stack = player.getInventory().getItemInMainHand();
        return stack == null || stack.getType() == Material.AIR ? null : stack;
    }

    private Map<String, String> placeholders() {
        double price = session.getDraftPrice();
        double tax = price * Math.max(0, plugin.getConfig().getDouble("AUCTION.TAX-PERCENT", 0)) / 100D;
        ItemStack held = held();
        Map<String, String> map = new HashMap<>();
        map.put("item", held == null ? "nothing" : ItemNames.display(held));
        map.put("amount", held == null ? "0" : NumberUtil.count(held.getAmount()));
        map.put("price", NumberUtil.money(price));
        map.put("fee", NumberUtil.money(plugin.auction().listingFee(price)));
        map.put("tax", NumberUtil.money(tax));
        map.put("payout", NumberUtil.money(Math.max(0, price - tax)));
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
        body.addAll(Dialogs.body(lines("BODY"), placeholders()));
        return body;
    }

    @Override
    protected ActionButton exitButton() {
        return backButton("BACK", placeholders(), () -> new SellScreen(plugin, player).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        return List.of(configButton("CONFIRM", placeholders(), (view, audience) ->
                new SellScreen(plugin, player).submit()));
    }
}
