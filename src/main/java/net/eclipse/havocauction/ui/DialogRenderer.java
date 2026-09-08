package net.eclipse.havocauction.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.eclipse.havocauction.dialog.Dialogs;
import net.eclipse.havocauction.dialog.Screen;
import net.eclipse.havocauction.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Renders a screen as a Minecraft dialog. */
public class DialogRenderer implements Renderer {

    private static final ClickCallback.Options OPTIONS = ClickCallback.Options.builder()
            .uses(32)
            .lifetime(Duration.ofMinutes(10))
            .build();

    @Override
    public void render(Screen screen) {
        List<DialogBody> body = new ArrayList<>();
        if (screen.bodyIcon() != null) {
            body.add(Dialogs.item(screen.bodyIcon(), screen.itemSize()));
        }
        for (String line : screen.bodyLines()) {
            body.add(DialogBody.plainMessage(Text.component(line)));
        }

        List<DialogInput> inputs = new ArrayList<>();
        for (ScreenModel.Input input : screen.inputs()) {
            inputs.add(DialogInput.text(input.key(), Text.component(input.label()))
                    .initial(input.initial() == null ? "" : input.initial())
                    .build());
        }

        List<ActionButton> buttons = new ArrayList<>();
        for (ScreenModel.Button button : screen.buttons()) {
            buttons.add(toAction(screen, button));
        }

        ScreenModel.Button exit = screen.exitButton();
        ActionButton exitButton = exit == null ? null : toAction(screen, exit);

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Text.component(screen.title()))
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(buttons, exitButton, Math.max(1, screen.columns()))));

        screen.viewer().showDialog(dialog);
    }

    private ActionButton toAction(Screen screen, ScreenModel.Button button) {
        String label = screen.style().text(button.label());
        List<String> tooltip = screen.style().text(button.tooltip());

        if (screen.style().inlineTooltip() && !tooltip.isEmpty()) {
            label = label + "\n" + String.join("\n", tooltip);
        }
        Component hover = tooltip.isEmpty() ? null : Text.multiline(tooltip);

        if (button.action() == null) {
            return ActionButton.create(Text.component(label), hover, screen.width(), null);
        }
        return ActionButton.create(Text.component(label), hover, screen.width(),
                DialogAction.customClick(
                        (view, audience) -> button.action().run(view::getText), OPTIONS));
    }
}
