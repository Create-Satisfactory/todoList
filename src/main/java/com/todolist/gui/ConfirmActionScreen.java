package com.todolist.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * FICSIT-styled Confirmation Modal Dialog.
 */
public class ConfirmActionScreen extends Screen {
    private static final int COL_HEADER_BG   = 0xF225272B;
    private static final int COL_BODY_BG     = 0xEB181A1D;
    private static final int COL_WELL_BG     = 0xFF121316;
    private static final int COL_DANGER_RED  = 0xFFD94040;

    private final Screen parent;
    private final Component message;
    private final Component confirmButtonText;
    private final Runnable onConfirm;

    public ConfirmActionScreen(Screen parent,
                               Component title,
                               Component message,
                               Component confirmButtonText,
                               Runnable onConfirm) {
        super(title);
        this.parent = parent;
        this.message = message == null ? Component.empty() : message;
        this.confirmButtonText = confirmButtonText == null ? Component.empty() : confirmButtonText;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int dialogWidth = Math.max(220, Math.min(340, width - 20));
        int dialogHeight = 132;
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - dialogHeight) / 2;

        addRenderableWidget(Button.builder(confirmButtonText, button -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
            onClose();
        }).bounds(dialogX + 14, dialogY + dialogHeight - 28, 110, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.cancel"), button -> onClose())
                .bounds(dialogX + dialogWidth - 124, dialogY + dialogHeight - 28, 110, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x78090A0C);

        int dialogWidth = Math.max(220, Math.min(340, width - 20));
        int dialogHeight = 132;
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - dialogHeight) / 2;

        // Header Bar + Hazard Red Accent
        context.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + 22, COL_HEADER_BG);
        context.fill(dialogX, dialogY + 20, dialogX + dialogWidth, dialogY + 22, COL_DANGER_RED);
        context.drawString(font, "⚠", dialogX + 8, dialogY + 7, COL_DANGER_RED, false);
        context.drawString(font, title, dialogX + 20, dialogY + 7, 0xFFFFFFFF, false);

        // Main Body & Inset Message Well
        context.fill(dialogX, dialogY + 22, dialogX + dialogWidth, dialogY + dialogHeight, COL_BODY_BG);
        context.fill(dialogX + 12, dialogY + 30, dialogX + dialogWidth - 12, dialogY + dialogHeight - 36, COL_WELL_BG);
        context.renderOutline(dialogX + 12, dialogY + 30, dialogWidth - 24, dialogHeight - 66, 0xFF282B30);
        context.drawWordWrap(font, message, dialogX + 18, dialogY + 38, dialogWidth - 36, 0xFFD6E0EC);

        context.renderOutline(dialogX, dialogY, dialogWidth, dialogHeight, 0xFF141518);
        super.render(context, mouseX, mouseY, delta);
    }
}