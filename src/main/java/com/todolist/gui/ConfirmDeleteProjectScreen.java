package com.todolist.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * FICSIT-styled Delete Project Confirmation Modal.
 */
public class ConfirmDeleteProjectScreen extends Screen {
    private final Screen parent;
    private final Component message;
    private final Runnable onConfirm;

    public ConfirmDeleteProjectScreen(Screen parent, Component message, Runnable onConfirm) {
        super(Component.translatable("gui.todolist.project.delete_confirm.title"));
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int w = Math.max(220, Math.min(340, width - 20));
        int h = 132;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.delete"), b -> {
            onConfirm.run();
            onClose();
        }).bounds(x + 14, y + h - 28, 110, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.cancel"), b -> onClose())
                .bounds(x + w - 124, y + h - 28, 110, 20).build());
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

        int w = Math.max(220, Math.min(340, width - 20));
        int h = 132;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        context.fill(x, y, x + w, y + 22, 0xF225272B);
        context.fill(x, y + 20, x + w, y + 22, 0xFFD94040);
        context.drawString(font, "⚠", x + 8, y + 7, 0xFFD94040, false);
        context.drawString(font, title, x + 20, y + 7, 0xFFFFFFFF, false);

        context.fill(x, y + 22, x + w, y + h, 0xEB181A1D);
        context.fill(x + 12, y + 30, x + w - 12, y + h - 36, 0xFF121316);
        context.renderOutline(x + 12, y + 30, w - 24, h - 66, 0xFF282B30);
        context.drawWordWrap(font, message, x + 18, y + 38, w - 36, 0xFFD6E0EC);

        context.renderOutline(x, y, w, h, 0xFF141518);
        super.render(context, mouseX, mouseY, delta);
    }
}