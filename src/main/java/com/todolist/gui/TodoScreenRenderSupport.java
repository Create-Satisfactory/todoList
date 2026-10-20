package com.todolist.gui;

import com.todolist.gui.TodoScreenLayoutSupport.LayoutRect;
import com.todolist.gui.TodoScreenLayoutSupport.MainLayoutMetrics;
import com.todolist.gui.TodoScreenLayoutSupport.ResponsiveTier;
import com.todolist.project.Project;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * FICSIT-styled rendering support for TodoScreen (3-tier panel hierarchy, header bars, prefix dropdowns, and ADA toast notifications).
 */
final class TodoScreenRenderSupport {

    // Authentic Satisfactory FICSIT Palette
    private static final int COL_HEADER_BG     = 0xF225272B;
    private static final int COL_SIDEBAR_BG    = 0xEB23262A;
    private static final int COL_CENTER_BG     = 0xE616181B;
    private static final int COL_DETAIL_BG     = 0xF0202327;
    private static final int COL_OVERLAY_BG    = 0xF5181A1D;
    private static final int COL_BORDER_DARK   = 0xFF141518;
    private static final int COL_BORDER_MID    = 0xFF32353C;
    private static final int COL_FICSIT_ORANGE = 0xFFE59344;
    private static final int COL_FICSIT_CYAN   = 0xFF4FC3F7;
    private static final int TEXT_WHITE        = 0xFFFFFFFF;
    private static final int TEXT_GRAY         = 0xFFB0B5BC;

    private TodoScreenRenderSupport() {
    }

    /**
     * Renders the FICSIT-styled project search prefix autocomplete dropdown.
     */
    static void renderProjectSearchPrefixDropdown(GuiGraphics context,
                                                  int mouseX,
                                                  int mouseY,
                                                  Font font,
                                                  EditBox projectSearchField,
                                                  boolean projectSearchPrefixDropdownOpen,
                                                  boolean teamSpace,
                                                  boolean sidebarVisible) {
        boolean prefixDropdownVisible = TodoScreenProjectSearchSupport.shouldShowProjectSearchPrefixDropdown(
                projectSearchPrefixDropdownOpen,
                teamSpace,
                sidebarVisible,
                projectSearchField
        );
        if (!prefixDropdownVisible || font == null || projectSearchField == null) {
            return;
        }
        int[] bounds = TodoScreenProjectSearchSupport.getProjectSearchPrefixDropdownBounds(
                projectSearchField.getX(),
                projectSearchField.getY(),
                projectSearchField.getWidth(),
                projectSearchField.getHeight()
        );
        if (bounds[2] <= 0 || bounds[3] <= 0) {
            return;
        }

        // FICSIT Dropdown Container
        context.fill(bounds[0], bounds[1], bounds[0] + bounds[2], bounds[1] + bounds[3], 0xF5181A1D);
        context.renderOutline(bounds[0], bounds[1], bounds[2], bounds[3], COL_FICSIT_ORANGE);

        List<TodoScreenProjectSearchSupport.ProjectSearchPrefixOption> options =
                TodoScreenProjectSearchSupport.getProjectSearchPrefixOptions();
        for (int i = 0; i < options.size(); i++) {
            int[] itemBounds = TodoScreenProjectSearchSupport.getProjectSearchPrefixOptionBounds(
                    projectSearchField.getX(),
                    projectSearchField.getY(),
                    projectSearchField.getWidth(),
                    projectSearchField.getHeight(),
                    i
            );
            boolean hovered = mouseX >= itemBounds[0] && mouseX < itemBounds[0] + itemBounds[2]
                    && mouseY >= itemBounds[1] && mouseY < itemBounds[1] + itemBounds[3];
            if (hovered) {
                context.fill(itemBounds[0] + 1, itemBounds[1], itemBounds[0] + itemBounds[2] - 1, itemBounds[1] + itemBounds[3], 0xFF2E323A);
                context.fill(itemBounds[0] + 1, itemBounds[1], itemBounds[0] + 4, itemBounds[1] + itemBounds[3], COL_FICSIT_ORANGE);
            }
            int textY = itemBounds[1] + Math.max(0, (itemBounds[3] - font.lineHeight) / 2);
            TodoScreenProjectSearchSupport.ProjectSearchPrefixOption option = options.get(i);
            context.drawString(font, option.prefix, itemBounds[0] + 6, textY, COL_FICSIT_ORANGE, false);
            context.drawString(
                    font,
                    TodoScreenProjectSearchSupport.getProjectSearchPrefixOptionDescription(option),
                    itemBounds[0] + 36,
                    textY,
                    TEXT_GRAY,
                    false
            );
        }
    }

    /**
     * Renders the active Project / Space / View header summary inside the center content column.
     */
    static void renderContentHeaderSummary(GuiGraphics context,
                                           Font font,
                                           MainLayoutMetrics layoutMetrics,
                                           ResponsiveTier responsiveTier,
                                           SpaceMode currentSpaceMode,
                                           TaskViewOption currentTaskViewOption,
                                           Project currentProject,
                                           Button configButton,
                                           Button sidebarToggleButton) {
        if (layoutMetrics == null || configButton == null || font == null || responsiveTier == null || currentSpaceMode == null) {
            return;
        }
        String summaryText = TodoScreenTextSupport.buildContentHeaderSummaryText(
                currentSpaceMode == SpaceMode.TEAM,
                currentProject,
                currentTaskViewOption
        );
        if (summaryText.isEmpty()) {
            return;
        }

        boolean isFtbTeam = currentProject != null && currentProject.getId() != null && currentProject.getId().startsWith("ftbteam_");
        String prefixSymbol = isFtbTeam ? "⚑ " : "❖ ";

        int startX = layoutMetrics.contentBounds.x + TodoScreenUiMetricsSupport.getContentControlInset(responsiveTier);
        if (sidebarToggleButton != null && sidebarToggleButton.visible) {
            startX = sidebarToggleButton.getX() + sidebarToggleButton.getWidth()
                    + TodoScreenUiMetricsSupport.getContentActionGap(responsiveTier);
        }
        int maxWidth = configButton.getX() - TodoScreenUiMetricsSupport.getContentActionGap(responsiveTier) - startX;
        if (maxWidth <= 12) {
            return;
        }
        String displayText = TodoScreenTextSupport.trimTextToWidth(font, prefixSymbol + summaryText, maxWidth);
        if (displayText.isEmpty()) {
            return;
        }
        int drawY = configButton.getY() + Math.max(0, (configButton.getHeight() - font.lineHeight) / 2);
        context.drawString(font, displayText, startX, drawY, isFtbTeam ? COL_FICSIT_ORANGE : TEXT_WHITE, false);
    }

    /**
     * Renders the 3 FICSIT panels: Left Sidebar, Center Task List, and Right Inspector Drawer.
     */
    static void renderLayoutPanels(GuiGraphics context,
                                   MainLayoutMetrics layoutMetrics,
                                   boolean sidebarPanelVisible,
                                   boolean detailPanelVisible) {
        if (layoutMetrics == null) {
            return;
        }

        // 1. Center Task List Column
        renderFicsitPanel(
                context,
                layoutMetrics.contentBounds,
                COL_CENTER_BG,
                COL_FICSIT_ORANGE,
                false
        );

        // 2. Left Project & Space Sidebar
        if (sidebarPanelVisible) {
            renderFicsitPanel(
                    context,
                    layoutMetrics.sidebarBounds,
                    layoutMetrics.sidebarOverlay ? COL_OVERLAY_BG : COL_SIDEBAR_BG,
                    COL_FICSIT_ORANGE,
                    layoutMetrics.sidebarOverlay
            );
        }

        // 3. Right Task Detail Inspector Drawer
        if (detailPanelVisible) {
            renderFicsitPanel(
                    context,
                    layoutMetrics.detailBounds,
                    layoutMetrics.detailOverlay ? COL_OVERLAY_BG : COL_DETAIL_BG,
                    COL_FICSIT_CYAN,
                    layoutMetrics.detailOverlay
            );
        }
    }

    /**
     * Draws an individual FICSIT panel with a Gunmetal top header strip, colored accent bar, and crisp border.
     */
    private static void renderFicsitPanel(GuiGraphics context,
                                          LayoutRect bounds,
                                          int bodyFillColor,
                                          int topAccentColor,
                                          boolean isOverlay) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }

        int x = bounds.x;
        int y = bounds.y;
        int w = bounds.width;
        int h = bounds.height;

        // Main Panel Body
        context.fill(x, y, x + w, y + h, bodyFillColor);

        // Top Gunmetal Header Strip + 2px Accent Line
        int headerH = Math.min(24, h);
        context.fill(x, y, x + w, y + headerH, COL_HEADER_BG);
        context.fill(x, y + headerH - 2, x + w, y + headerH, topAccentColor);

        // Outer & Inner Framing
        context.renderOutline(x, y, w, h, isOverlay ? topAccentColor : COL_BORDER_MID);
        if (w > 2 && h > 2) {
            context.renderOutline(x - 1, y - 1, w + 2, h + 2, COL_BORDER_DARK);
        }
    }

    /**
     * Renders FICSIT ADA-styled toast notifications with a left orange indicator bar.
     */
    static void renderNotifications(GuiGraphics context,
                                    Font font,
                                    int screenWidth,
                                    MainLayoutMetrics layoutMetrics,
                                    List<TodoScreenNotificationSupport.NotificationEntry> notifications) {
        if (font == null || notifications == null || notifications.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int boxWidth = TodoScreenNotificationSupport.BOX_WIDTH;
        int boxHeight = TodoScreenNotificationSupport.BOX_HEIGHT;
        LayoutRect contentBounds = layoutMetrics == null ? null : layoutMetrics.contentBounds;
        int startX = TodoScreenNotificationSupport.resolveStartX(screenWidth, contentBounds);
        int startY = TodoScreenNotificationSupport.resolveStartY(contentBounds);

        List<TodoScreenNotificationSupport.NotificationEntry> active =
                TodoScreenNotificationSupport.retainActive(notifications, now);
        notifications.clear();
        notifications.addAll(active);

        int dy = 0;
        for (TodoScreenNotificationSupport.NotificationEntry notification : notifications) {
            int bx1 = startX;
            int by1 = startY + dy;
            int bx2 = bx1 + boxWidth;
            int by2 = by1 + boxHeight;

            // FICSIT Toast Box + 3px Left Orange Accent Bar
            context.fill(bx1, by1, bx2, by2, 0xF21E2024);
            context.fill(bx1, by1, bx1 + 3, by2, COL_FICSIT_ORANGE);
            context.renderOutline(bx1, by1, boxWidth, boxHeight, COL_BORDER_MID);

            int tx = bx1 + TodoScreenNotificationSupport.TEXT_LEFT_PADDING + 2;
            int ty = by1 + (boxHeight - font.lineHeight) / 2;
            context.drawString(font, Component.nullToEmpty(notification.text), tx, ty, TEXT_WHITE, false);
            dy += boxHeight + TodoScreenNotificationSupport.BOX_GAP;
        }
    }
}