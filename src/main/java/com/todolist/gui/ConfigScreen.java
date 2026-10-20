package com.todolist.gui;

import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * FICSIT-styled HUD Configuration Screen with non-overlapping 2-column layout and live draggable HUD preview.
 */
public class ConfigScreen extends Screen {

    private static final int FIXED_PREVIEW_HUD_HEIGHT = 58;

    private static final int COL_HEADER_BG     = 0xF532353B;
    private static final int COL_BODY_BG       = 0xEB181A1D;
    private static final int COL_ROW_WELL_BG   = 0x66101214;
    private static final int COL_FICSIT_ORANGE = 0xFFE59344;
    private static final int TEXT_WHITE        = 0xFFFFFFFF;
    private static final int TEXT_GRAY         = 0xFFD0D5DC;

    private final Screen parent;

    private EditBox hudWidthField;
    private EditBox hudMaxHeightField;
    private IntSliderWidget hudTodoLimitSlider;
    private IntSliderWidget hudDoneLimitSlider;
    private DoubleStepSliderWidget hudOpacitySlider;
    private Button hudShowWhenEmptyButton;
    private Button hudVisibilityButton;
    private Button hudProjectSourceButton;
    private Button hudShowSubtasksButton;
    private Button autoSaveButton;
    private Button saveButton;
    private Button cancelButton;

    private int winX, winY, winW, winH;

    private int previewHudX;
    private int previewHudY;
    private ModConfig.HudHorizontalAnchor previewHorizontalAnchor;
    private ModConfig.HudVerticalAnchor previewVerticalAnchor;
    private int previewHorizontalMargin;
    private int previewVerticalMargin;
    private int previewHudWidth;
    private int previewHudHeight;
    private int previewRectX;
    private int previewRectY;
    private boolean draggingHud;
    private boolean previewUseCustom;
    private int dragOffsetX;
    private int dragOffsetY;

    private boolean hudShowWhenEmptyValue;
    private boolean hudShowSubtasksValue;
    private boolean hudVisibleValue;
    private String hudProjectSourceValue;
    private int hudProjectSourceIndex;
    private boolean autoSaveValue;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("gui.todolist.config.title"));
        this.parent = parent;
    }

    EditBox getHudWidthFieldForTest() { return hudWidthField; }
    EditBox getHudMaxHeightFieldForTest() { return hudMaxHeightField; }
    Button getHudVisibilityButtonForTest() { return hudVisibilityButton; }
    Button getHudProjectSourceButtonForTest() { return hudProjectSourceButton; }
    Button getHudShowSubtasksButtonForTest() { return hudShowSubtasksButton; }
    Button getSaveButtonForTest() { return saveButton; }
    Button getAutoSaveButtonForTest() { return autoSaveButton; }
    Button getCancelButtonForTest() { return cancelButton; }
    boolean isHudVisibleValueForTest() { return hudVisibleValue; }
    String getHudProjectSourceValueForTest() { return hudProjectSourceValue; }
    boolean isHudShowSubtasksValueForTest() { return hudShowSubtasksValue; }
    boolean isAutoSaveValueForTest() { return autoSaveValue; }
    boolean isPreviewUseCustomForTest() { return previewUseCustom; }
    int getPreviewHudWidthForTest() { return previewHudWidth; }
    int getPreviewHudHeightForTest() { return previewHudHeight; }
    int getPreviewHudXForTest() { return previewHudX; }
    int getPreviewHudYForTest() { return previewHudY; }

    void syncPreviewRectForTest() {
        previewRectX = previewHudX;
        previewRectY = previewHudY;
    }

    @Override
    protected void init() {
        ModConfig config = ModConfig.getInstance();

        this.winW = clampInt(this.width - 28, 380, 480);
        this.winH = 224;
        this.winX = (this.width - this.winW) / 2;
        this.winY = Math.max(16, (this.height - this.winH) / 2);

        int padX = 14;
        int innerX = winX + padX;
        int innerW = winW - padX * 2;
        int startY = winY + 30;
        int row = 0;
        int rowHeight = 24;
        int fieldHeight = 18;

        int columnGap = 16;
        int columnWidth = (innerW - columnGap) / 2;
        int labelWidth = Math.min(112, Math.max(88, (int) (columnWidth * 0.52f)));
        int fieldWidth = Math.max(64, columnWidth - labelWidth);

        int leftFieldX = innerX + labelWidth;
        int rightFieldX = innerX + columnWidth + columnGap + labelWidth;

        // Row 0: HUD Width | HUD Max Height
        hudWidthField = new EditBox(this.font, leftFieldX, startY + row * rowHeight, fieldWidth, fieldHeight, Component.empty());
        hudWidthField.setValue(Integer.toString(config.getHudWidth()));
        this.addRenderableWidget(hudWidthField);

        hudMaxHeightField = new EditBox(this.font, rightFieldX, startY + row * rowHeight, fieldWidth, fieldHeight, Component.empty());
        hudMaxHeightField.setValue(Integer.toString(config.getHudMaxHeight()));
        this.addRenderableWidget(hudMaxHeightField);
        row++;

        // Row 1: Todo Display Count | Done Display Count
        hudTodoLimitSlider = new IntSliderWidget(leftFieldX, startY + row * rowHeight, fieldWidth, fieldHeight, 0, 30, config.getHudTodoLimit());
        hudDoneLimitSlider = new IntSliderWidget(rightFieldX, startY + row * rowHeight, fieldWidth, fieldHeight, 0, 30, config.getHudDoneLimit());
        this.addRenderableWidget(hudTodoLimitSlider);
        this.addRenderableWidget(hudDoneLimitSlider);
        row++;

        // Row 2: Show HUD when empty | HUD Visibility
        hudShowWhenEmptyValue = config.isHudShowWhenEmpty();
        hudShowWhenEmptyButton = Button.builder(Component.empty(), button -> {
            hudShowWhenEmptyValue = !hudShowWhenEmptyValue;
            updateHudShowWhenEmptyButtonLabel();
        }).bounds(leftFieldX, startY + row * rowHeight, fieldWidth, fieldHeight).build();
        this.addRenderableWidget(hudShowWhenEmptyButton);

        hudVisibleValue = ClientBridge.ops().isHudVisible();
        hudVisibilityButton = Button.builder(Component.empty(), button -> {
            hudVisibleValue = !hudVisibleValue;
            ClientBridge.ops().setHudVisible(hudVisibleValue);
            updateHudVisibilityButtonLabel();
        }).bounds(rightFieldX, startY + row * rowHeight, fieldWidth, fieldHeight).build();
        this.addRenderableWidget(hudVisibilityButton);
        row++;

        // Row 3: HUD Opacity | GUI Auto Save
        hudOpacitySlider = new DoubleStepSliderWidget(leftFieldX, startY + row * rowHeight, fieldWidth, fieldHeight, 0.0D, 1.0D, 0.1D, config.getHudOpacity());
        this.addRenderableWidget(hudOpacitySlider);

        autoSaveValue = config.isAutoSave();
        autoSaveButton = Button.builder(Component.empty(), button -> {
            autoSaveValue = !autoSaveValue;
            updateAutoSaveButtonLabel();
        }).bounds(rightFieldX, startY + row * rowHeight, fieldWidth, fieldHeight).build();
        this.addRenderableWidget(autoSaveButton);
        row++;

        // Row 4: Show HUD Subtasks | Project Source
        hudShowSubtasksValue = config.isHudShowSubtasks();
        hudShowSubtasksButton = Button.builder(Component.empty(), button -> {
            hudShowSubtasksValue = !hudShowSubtasksValue;
            updateHudShowSubtasksButtonLabel();
        }).bounds(leftFieldX, startY + row * rowHeight, fieldWidth, fieldHeight).build();
        this.addRenderableWidget(hudShowSubtasksButton);

        hudProjectSourceValue = normalizeHudProjectSource(config.getHudProjectSource());
        hudProjectSourceIndex = resolveHudProjectSourceIndex(hudProjectSourceValue);
        hudProjectSourceButton = Button.builder(Component.empty(), button -> {
            hudProjectSourceIndex = (hudProjectSourceIndex + 1) % HudProjectSourceOptions.VALUES.length;
            hudProjectSourceValue = HudProjectSourceOptions.VALUES[hudProjectSourceIndex];
            updateHudProjectSourceButtonLabel();
        }).bounds(rightFieldX, startY + row * rowHeight, fieldWidth, fieldHeight).build();
        this.addRenderableWidget(hudProjectSourceButton);
        row++;

        updateHudShowWhenEmptyButtonLabel();
        updateHudVisibilityButtonLabel();
        updateHudProjectSourceButtonLabel();
        updateAutoSaveButtonLabel();
        updateHudShowSubtasksButtonLabel();

        previewUseCustom = config.isHudUseCustomPosition();
        previewHudWidth = Math.max(120, config.getHudWidth());
        previewHudHeight = Math.max(40, resolvePreviewHudHeight());
        syncPreviewPositionFromConfig(config);

        // Bottom Footer Action Bar (Save & Apply | Cancel)
        int btnY = winY + winH - 28;
        int btnW = (innerW - 10) / 2;
        saveButton = Button.builder(Component.translatable("gui.todolist.config.save_apply"), button -> applyAndReturn())
                .bounds(innerX, btnY, btnW, 20)
                .build();
        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), button -> this.minecraft.setScreen(parent))
                .bounds(innerX + btnW + 10, btnY, btnW, 20)
                .build();
        this.addRenderableWidget(saveButton);
        this.addRenderableWidget(cancelButton);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x68090A0C);

        // 1. Main FICSIT Config Window Frame
        context.fill(winX, winY, winX + winW, winY + 22, COL_HEADER_BG);
        context.fill(winX, winY + 20, winX + winW, winY + 22, COL_FICSIT_ORANGE);
        context.drawString(this.font, "⚙", winX + 8, winY + 7, COL_FICSIT_ORANGE, false);
        context.drawString(this.font, "To-Do List HUD Configuration", winX + 20, winY + 7, TEXT_WHITE, false);

        // Top-right Close 'X' button
        int closeX = winX + winW - 26;
        boolean closeHover = mouseX >= closeX && mouseX < winX + winW && mouseY >= winY && mouseY < winY + 22;
        context.fill(closeX, winY, winX + winW, winY + 20, closeHover ? 0xFFD9534F : 0xFF45484F);
        context.drawCenteredString(this.font, "✕", closeX + 13, winY + 6, TEXT_WHITE);

        // Window Body + Subtle Row Stripes
        context.fill(winX, winY + 22, winX + winW, winY + winH, COL_BODY_BG);
        for (int r = 0; r < 5; r++) {
            int ry = winY + 28 + r * 24;
            context.fill(winX + 10, ry, winX + winW - 10, ry + 22, COL_ROW_WELL_BG);
        }
        // Bottom Footer Strip
        context.fill(winX, winY + winH - 34, winX + winW, winY + winH, 0xFF22252A);
        context.fill(winX, winY + winH - 34, winX + winW, winY + winH - 33, 0xFF32353C);
        context.renderOutline(winX, winY, winW, winH, 0xFF141518);

        super.render(context, mouseX, mouseY, delta);

        // 2. Render Non-Overlapping Column Labels
        int padX = 14;
        int innerX = winX + padX;
        int innerW = winW - padX * 2;
        int columnGap = 16;
        int columnWidth = (innerW - columnGap) / 2;
        int rightColStartX = innerX + columnWidth + columnGap;

        drawBoundedLabelForWidget(context, Component.translatable("gui.todolist.config.hud_width"), hudWidthField, innerX);
        drawBoundedLabelForWidget(context, Component.translatable("gui.todolist.config.hud_max_height"), hudMaxHeightField, rightColStartX);
        drawBoundedLabelForWidget(context, Component.translatable("gui.todolist.config.hud_todo_limit"), hudTodoLimitSlider, innerX);
        drawBoundedLabelForWidget(context, Component.translatable("gui.todolist.config.hud_done_limit"), hudDoneLimitSlider, rightColStartX);
        drawBoundedLabelForWidget(context, Component.translatable("gui.todolist.config.hud_show_when_empty"), hudShowWhenEmptyButton, innerX);
        drawBoundedLabelForWidget(context, Component.translatable("gui.todolist.config.hud_visibility"), hudVisibilityButton, rightColStartX);
        drawBoundedLabelForWidget(context, Component.translatable("gui.todolist.config.hud_opacity"), hudOpacitySlider, innerX);
        drawBoundedLabelForWidget(context, Component.translatable("gui.todolist.config.gui_auto_save"), autoSaveButton, rightColStartX);
        drawBoundedLabelForWidget(context, Component.translatable("gui.todolist.config.hud_show_subtasks"), hudShowSubtasksButton, innerX);
        drawBoundedLabelForWidget(context, Component.translatable("gui.todolist.config.hud_project_source"), hudProjectSourceButton, rightColStartX);

        // 3. Draggable FICSIT HUD Position Preview Box
        previewHudWidth = Math.max(120, parseIntSafe(hudWidthField.getValue(), ModConfig.getInstance().getHudWidth()));
        previewHudHeight = Math.max(44, resolvePreviewHudHeight());
        if (previewUseCustom) {
            applyPreviewAnchorsToAbsolutePosition();
        } else {
            syncPreviewPositionFromConfig(ModConfig.getInstance());
        }

        previewRectX = previewHudX;
        previewRectY = previewHudY;

        double opacity = hudOpacitySlider == null ? ModConfig.getInstance().getHudOpacity() : hudOpacitySlider.getDoubleValue();
        int alpha = (int) Math.round(clampRatio(opacity) * 230.0D);
        int previewBodyCol = (alpha << 24) | 0x141619;
        context.fill(previewHudX, previewHudY, previewHudX + previewHudWidth, previewHudY + previewHudHeight, previewBodyCol);
        context.fill(previewHudX, previewHudY, previewHudX + previewHudWidth, previewHudY + 15, COL_HEADER_BG);
        context.fill(previewHudX, previewHudY + 14, previewHudX + previewHudWidth, previewHudY + 15, COL_FICSIT_ORANGE);
        context.renderOutline(previewHudX, previewHudY, previewHudWidth, previewHudHeight, COL_FICSIT_ORANGE);

        context.drawString(this.font, "≡ To-Do List (Preview)", previewHudX + 5, previewHudY + 3, COL_FICSIT_ORANGE, false);
        String hintStr = "Drag box to reposition HUD";
        context.pose().pushPose();
        context.pose().translate(previewHudX + previewHudWidth / 2f, previewHudY + 28, 0);
        context.pose().scale(0.8f, 0.8f, 1.0f);
        context.drawCenteredString(this.font, hintStr, 0, 0, TEXT_GRAY);
        context.pose().popPose();
    }

    /**
     * Draws a widget label strictly within [minX .. widget.getX() - 6], scaling down slightly if needed so it never overflows.
     */
    private void drawBoundedLabelForWidget(GuiGraphics context, Component label, AbstractWidget widget, int minX) {
        if (widget == null || !widget.visible) return;
        String text = label.getString();
        int maxAvailWidth = Math.max(30, widget.getX() - minX - 6);
        int rawW = this.font.width(text);
        float scale = rawW > maxAvailWidth ? Math.max(0.72f, (float) maxAvailWidth / rawW) : 1.0f;

        float drawY = widget.getY() + (widget.getHeight() - (this.font.lineHeight * scale)) / 2.0f;
        context.pose().pushPose();
        context.pose().translate(minX, drawY, 0);
        context.pose().scale(scale, scale, 1.0f);
        context.drawString(this.font, text, 0, 0, TEXT_GRAY, false);
        context.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Top-right Close 'X' button
            if (mouseX >= winX + winW - 26 && mouseX < winX + winW && mouseY >= winY && mouseY < winY + 22) {
                this.minecraft.setScreen(parent);
                return true;
            }

            int x = (int) mouseX;
            int y = (int) mouseY;
            if (x >= previewRectX && x <= previewRectX + previewHudWidth
                    && y >= previewRectY && y <= previewRectY + previewHudHeight) {
                draggingHud = true;
                dragOffsetX = x - previewRectX;
                dragOffsetY = y - previewRectY;
                if (!previewUseCustom) {
                    updatePreviewAnchorsFromAbsolutePosition();
                }
                previewUseCustom = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingHud) {
            draggingHud = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingHud) {
            int newX = (int) mouseX - dragOffsetX;
            int newY = (int) mouseY - dragOffsetY;
            previewHudX = clampPreviewCoordinate(newX, this.width, previewHudWidth);
            previewHudY = clampPreviewCoordinate(newY, this.height, previewHudHeight);
            updatePreviewAnchorsFromAbsolutePosition();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void applyAndReturn() {
        ModConfig config = ModConfig.getInstance();
        config.setHudWidth(parseIntSafe(hudWidthField.getValue(), config.getHudWidth()));
        config.setHudMaxHeight(parseIntSafe(hudMaxHeightField.getValue(), config.getHudMaxHeight()));
        config.setHudTodoLimit(hudTodoLimitSlider.getIntValue());
        config.setHudDoneLimit(hudDoneLimitSlider.getIntValue());
        config.setHudOpacity(hudOpacitySlider.getDoubleValue());
        config.setHudShowWhenEmpty(hudShowWhenEmptyValue);
        config.setHudShowSubtasks(hudShowSubtasksValue);
        config.setHudProjectSource(hudProjectSourceValue);
        config.setAutoSave(autoSaveValue);

        previewHudWidth = Math.max(1, config.getHudWidth());
        previewHudHeight = Math.max(1, resolvePreviewHudHeight());
        if (previewUseCustom) {
            config.updateHudCustomPosition(previewHudX, previewHudY, this.width, this.height, previewHudWidth, previewHudHeight);
        } else {
            config.setHudUseCustomPosition(false);
        }
        ClientBridge.ops().setHudVisible(hudVisibleValue);
        this.minecraft.setScreen(parent);
    }

    private void syncPreviewPositionFromConfig(ModConfig config) {
        previewHudWidth = Math.max(1, previewHudWidth);
        previewHudHeight = Math.max(1, previewHudHeight);
        if (previewUseCustom) {
            if (config.hasHudCustomAnchors()) {
                previewHorizontalAnchor = config.getHudCustomHorizontalAnchor();
                previewVerticalAnchor = config.getHudCustomVerticalAnchor();
                previewHorizontalMargin = config.getHudCustomHorizontalMargin();
                previewVerticalMargin = config.getHudCustomVerticalMargin();
                applyPreviewAnchorsToAbsolutePosition();
            } else {
                previewHudX = resolveLegacyPreviewCoordinate(
                        config.getHudCustomX(), config.getHudCustomXRatio(),
                        config.hasHudCustomPositionRatios(), this.width, previewHudWidth
                );
                previewHudY = resolveLegacyPreviewCoordinate(
                        config.getHudCustomY(), config.getHudCustomYRatio(),
                        config.hasHudCustomPositionRatios(), this.height, previewHudHeight
                );
                updatePreviewAnchorsFromAbsolutePosition();
            }
        } else {
            ModConfig.HudPlacement placement = config.resolveHudPlacement(this.width, this.height, previewHudWidth, previewHudHeight);
            previewHudX = placement.getX();
            previewHudY = placement.getY();
            updatePreviewAnchorsFromAbsolutePosition();
        }
    }

    private int resolvePreviewHudHeight() {
        return FIXED_PREVIEW_HUD_HEIGHT;
    }

    private int resolveLegacyPreviewCoordinate(int absoluteCoordinate, double ratioCoordinate, boolean preferRatio,
                                               int screenSize, int hudSize) {
        if (preferRatio) {
            int maxCoordinate = Math.max(0, screenSize - Math.max(0, hudSize));
            return clampPreviewCoordinate((int) Math.round(clampRatio(ratioCoordinate) * maxCoordinate), screenSize, hudSize);
        }
        return clampPreviewCoordinate(absoluteCoordinate, screenSize, hudSize);
    }

    private static double clampRatio(double ratio) {
        return Math.max(0.0D, Math.min(1.0D, ratio));
    }

    private void updatePreviewAnchorsFromAbsolutePosition() {
        previewHudX = clampPreviewCoordinate(previewHudX, this.width, previewHudWidth);
        previewHudY = clampPreviewCoordinate(previewHudY, this.height, previewHudHeight);

        int leftMargin = previewHudX;
        int rightMargin = Math.max(0, this.width - Math.max(0, previewHudWidth) - previewHudX);
        if (leftMargin <= rightMargin) {
            previewHorizontalAnchor = ModConfig.HudHorizontalAnchor.LEFT;
            previewHorizontalMargin = leftMargin;
        } else {
            previewHorizontalAnchor = ModConfig.HudHorizontalAnchor.RIGHT;
            previewHorizontalMargin = rightMargin;
        }

        int topMargin = previewHudY;
        int bottomMargin = Math.max(0, this.height - Math.max(0, previewHudHeight) - previewHudY);
        if (topMargin <= bottomMargin) {
            previewVerticalAnchor = ModConfig.HudVerticalAnchor.TOP;
            previewVerticalMargin = topMargin;
        } else {
            previewVerticalAnchor = ModConfig.HudVerticalAnchor.BOTTOM;
            previewVerticalMargin = bottomMargin;
        }
    }

    private void applyPreviewAnchorsToAbsolutePosition() {
        int resolvedX = previewHorizontalAnchor == ModConfig.HudHorizontalAnchor.LEFT
                ? previewHorizontalMargin
                : this.width - Math.max(0, previewHudWidth) - previewHorizontalMargin;
        int resolvedY = previewVerticalAnchor == ModConfig.HudVerticalAnchor.TOP
                ? previewVerticalMargin
                : this.height - Math.max(0, previewHudHeight) - previewVerticalMargin;
        previewHudX = clampPreviewCoordinate(resolvedX, this.width, previewHudWidth);
        previewHudY = clampPreviewCoordinate(resolvedY, this.height, previewHudHeight);
    }

    private int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void updateHudShowWhenEmptyButtonLabel() {
        if (hudShowWhenEmptyButton == null) return;
        String key = hudShowWhenEmptyValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
        hudShowWhenEmptyButton.setMessage(Component.translatable(key));
    }

    private void updateHudVisibilityButtonLabel() {
        if (hudVisibilityButton == null) return;
        String key = hudVisibleValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
        hudVisibilityButton.setMessage(Component.translatable(key));
    }

    private void updateHudProjectSourceButtonLabel() {
        if (hudProjectSourceButton == null) return;
        if ("CURRENT".equalsIgnoreCase(hudProjectSourceValue)) {
            Project.Scope scope = resolveHudScope();
            Project project = getActiveProject(scope);
            Component projectName = getProjectDisplayName(project);
            if (projectName != null && !projectName.getString().trim().isEmpty()) {
                hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.current_selected", projectName.getString()));
            } else {
                hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.current_selected_fallback"));
            }
            return;
        }
        if ("STARRED".equalsIgnoreCase(hudProjectSourceValue)) {
            hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.starred"));
            return;
        }
        hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.all"));
    }

    private void updateAutoSaveButtonLabel() {
        if (autoSaveButton == null) return;
        String key = autoSaveValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
        autoSaveButton.setMessage(Component.translatable(key));
    }

    private void updateHudShowSubtasksButtonLabel() {
        if (hudShowSubtasksButton == null) return;
        String key = hudShowSubtasksValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
        hudShowSubtasksButton.setMessage(Component.translatable(key));
    }

    private String normalizeHudProjectSource(String source) {
        if (source == null || source.isBlank()) return HudProjectSourceOptions.VALUES[2];
        for (String option : HudProjectSourceOptions.VALUES) {
            if (option.equalsIgnoreCase(source)) return option;
        }
        return HudProjectSourceOptions.VALUES[2];
    }

    private int resolveHudProjectSourceIndex(String source) {
        for (int index = 0; index < HudProjectSourceOptions.VALUES.length; index++) {
            if (HudProjectSourceOptions.VALUES[index].equalsIgnoreCase(source)) return index;
        }
        return 0;
    }

    private Project.Scope resolveHudScope() {
        String view = ModConfig.getInstance().getHudDefaultView();
        if ("TEAM_UNASSIGNED".equalsIgnoreCase(view)
                || "TEAM_ALL".equalsIgnoreCase(view)
                || "TEAM_ASSIGNED".equalsIgnoreCase(view)) {
            return Project.Scope.TEAM;
        }
        return Project.Scope.PERSONAL;
    }

    private static Project getActiveProject(Project.Scope scope) {
        return ClientBridge.getActiveProject(TodoListCommon.getProjectManager(), scope);
    }

    private static Component getProjectDisplayName(Project project) {
        return ProjectNameFormatter.toDisplayText(project);
    }

    private static int clampInt(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static int clampPreviewCoordinate(int coordinate, int screenSize, int hudSize) {
        int maxCoordinate = Math.max(0, screenSize - Math.max(0, hudSize));
        return Math.max(0, Math.min(maxCoordinate, coordinate));
    }

    private static final class HudProjectSourceOptions {
        private static final String[] VALUES = new String[] {"CURRENT", "STARRED", "ALL"};
        private HudProjectSourceOptions() {}
    }

    private static class IntSliderWidget extends AbstractSliderButton {
        private final int min;
        private final int max;

        IntSliderWidget(int x, int y, int width, int height, int min, int max, int value) {
            super(x, y, width, height, Component.empty(), 0.0D);
            this.min = min;
            this.max = max;
            setValueFromInt(value);
        }

        private void setValueFromInt(int value) {
            int clamped = Math.max(min, Math.min(max, value));
            this.value = max == min ? 0.0D : (double) (clamped - min) / (double) (max - min);
            updateMessage();
        }

        int getIntValue() {
            if (max == min) return min;
            int resolved = (int) Math.round(this.value * (max - min)) + min;
            return clampInt(resolved, min, max);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(Integer.toString(getIntValue())));
        }

        @Override
        protected void applyValue() {}
    }

    private static class DoubleStepSliderWidget extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final double step;

        DoubleStepSliderWidget(int x, int y, int width, int height, double min, double max, double step, double value) {
            super(x, y, width, height, Component.empty(), 0.0D);
            this.min = min;
            this.max = max;
            this.step = step;
            setValueFromDouble(value);
        }

        private void setValueFromDouble(double value) {
            double clamped = Math.max(min, Math.min(max, value));
            double stepped = Math.round(clamped / step) * step;
            this.value = max == min ? 0.0D : clampRatio((stepped - min) / (max - min));
            updateMessage();
        }

        double getDoubleValue() {
            double raw = min + clampRatio(this.value) * (max - min);
            double stepped = Math.round(raw / step) * step;
            return Math.max(min, Math.min(max, stepped));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format(java.util.Locale.ROOT, "%.1f", getDoubleValue())));
        }

        @Override
        protected void applyValue() {}
    }
}