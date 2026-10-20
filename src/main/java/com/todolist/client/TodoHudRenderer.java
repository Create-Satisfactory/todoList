package com.todolist.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.storage.H2TaskQueryService;
import com.todolist.storage.H2TaskStore;
import com.todolist.storage.StorageBackendFactory;
import com.todolist.task.Task;
import com.todolist.task.TaskAssignmentSupport;
import com.todolist.task.TaskManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Renders the Satisfactory-inspired FICSIT To-Do List overlay on the HUD:
 * - Public Notes (Synced via FTB Teams)
 * - Private Notes (Personal)
 * - Recipes (Pinned items/buildings with live inventory progress bars)
 */
public class TodoHudRenderer {
    private static final long HUD_MODEL_REFRESH_INTERVAL_MS = 500L;

    // Authentic Satisfactory HUD Palette
    private static final int COL_FICSIT_HEADER_BG = 0xE625272B;
    private static final int COL_FICSIT_BODY_BG   = 0xB8141619;
    private static final int COL_FICSIT_SUBHEADER = 0xCC32353C;
    private static final int COL_FICSIT_ORANGE    = 0xFFE59344;
    private static final int COL_FICSIT_GREEN     = 0xFF66BB6A;
    private static final int COL_FICSIT_BORDER    = 0x8832353C;

    private enum HudViewMode {
        PERSONAL,
        TEAM_UNASSIGNED,
        TEAM_ALL,
        TEAM_ASSIGNED
    }

    public record ParsedItemRequirement(Item item, ItemStack stack, int neededCount) {}

    private final Minecraft client;
    private boolean expanded;
    private long lastPersonalReloadMs;
    private long lastTeamReloadMs;
    private long cachedPersonalLastSavedMs = -1L;
    private long lastHudModelRefreshMs;
    private boolean hudModelCacheInitialized;
    private int cachedLayoutHudWidth = -1;
    private double cachedGuiScale = -1D;
    private boolean cachedPersonalTasksInitialized;
    private boolean cachedPersonalTasksFromGui;
    private List<Task> cachedPersonalTasks = new ArrayList<>();
    private List<Task> cachedTeamTasks = new ArrayList<>();
    private List<Task> cachedPendingTasks = new ArrayList<>();
    private List<Task> cachedDoneTasks = new ArrayList<>();
    private int cachedPendingTotalCount;
    private int cachedDoneTotalCount;
    private int cachedPendingSummaryCount;
    private int cachedDoneSummaryCount;
    private final Map<String, RowRenderCache> rowRenderCacheByTaskId = new HashMap<>();
    private final Map<String, ParentSubtaskProgress> parentProgressByTaskId = new HashMap<>();
    private HudViewMode cachedHudViewMode = HudViewMode.PERSONAL;
    private Project.Scope cachedHudScope = Project.Scope.PERSONAL;
    private String cachedProjectSourceMode = "";
    private String cachedActiveProjectId = "";
    private String cachedPlayerUuid = "";

    private static final Component ELLIPSIS = Component.literal("...");
    private static final float HUD_LABEL_SCALE = 0.85F;
    private static final int HUD_LABEL_MAX_CHARS = 8;
    private static final int HUD_PRIORITY_BLOCK_WIDTH = 3;
    private static final int HUD_PRIORITY_BLOCK_GAP = 4;
    private static final int HUD_SUBTASK_EXTRA_INDENT = 4;
    private static final String HUD_SUBTASK_PREFIX = "- ";

    private static final Component LABEL_SPACE_PERSONAL = Component.translatable("hud.todolist.space_label.personal");
    private static final Component LABEL_SPACE_TEAM = Component.translatable("hud.todolist.space_label.team");
    private static final Component LABEL_TEAM_VIEW_UNASSIGNED = Component.translatable("hud.todolist.team_view_label.unassigned");
    private static final Component LABEL_TEAM_VIEW_ALL = Component.translatable("hud.todolist.team_view_label.all");
    private static final Component LABEL_TEAM_VIEW_ASSIGNED = Component.translatable("hud.todolist.team_view_label.assigned");
    private static final Component LABEL_PROJECT_SOURCE_STARRED = Component.translatable("gui.todolist.hud.project_source.starred");
    private static final Component LABEL_PROJECT_SOURCE_ALL = Component.translatable("gui.todolist.hud.project_source.all");

    private static class HudRowVisual {
        private final int priorityBlockOffset;
        private final int priorityBlockColor;
        private final int prefixOffset;
        private final Component prefixText;
        private final String priorityText;
        private final String assigneeText;
        private final String tagText;
        private final int assigneeOffset;
        private final int tagOffset;
        private final int titleOffset;
        private final Component titleText;
        private final int progressOffset;
        private final Component progressText;

        private HudRowVisual(int priorityBlockOffset, int priorityBlockColor, int prefixOffset, Component prefixText,
                             String priorityText, String assigneeText, String tagText,
                             int assigneeOffset, int tagOffset, int titleOffset, Component titleText,
                             int progressOffset, Component progressText) {
            this.priorityBlockOffset = priorityBlockOffset;
            this.priorityBlockColor = priorityBlockColor;
            this.prefixOffset = prefixOffset;
            this.prefixText = prefixText;
            this.priorityText = priorityText;
            this.assigneeText = assigneeText;
            this.tagText = tagText;
            this.assigneeOffset = assigneeOffset;
            this.tagOffset = tagOffset;
            this.titleOffset = titleOffset;
            this.titleText = titleText;
            this.progressOffset = progressOffset;
            this.progressText = progressText;
        }
    }

    private static final class ParentSubtaskProgress {
        private final int completedCount;
        private final int totalCount;

        private ParentSubtaskProgress(int completedCount, int totalCount) {
            this.completedCount = completedCount;
            this.totalCount = totalCount;
        }
    }

    private static final class HudDisplayModel {
        private final List<Task> pendingTasks;
        private final List<Task> doneTasks;
        private final Map<String, ParentSubtaskProgress> parentProgress;

        private HudDisplayModel(List<Task> pendingTasks, List<Task> doneTasks, Map<String, ParentSubtaskProgress> parentProgress) {
            this.pendingTasks = List.copyOf(pendingTasks);
            this.doneTasks = List.copyOf(doneTasks);
            this.parentProgress = Map.copyOf(parentProgress);
        }
    }

    private static class HudRenderPlan {
        private final int shownPending;
        private final int shownDone;
        private final int hiddenCount;
        private final boolean showDoneSection;
        private final boolean showMore;

        private HudRenderPlan(int shownPending, int shownDone, int hiddenCount, boolean showDoneSection, boolean showMore) {
            this.shownPending = shownPending;
            this.shownDone = shownDone;
            this.hiddenCount = hiddenCount;
            this.showDoneSection = showDoneSection;
            this.showMore = showMore;
        }
    }

    private static class HudSqlProjectFilter {
        private final Set<String> projectIds;
        private final boolean includeAnyAssignedProject;

        private HudSqlProjectFilter(Set<String> projectIds, boolean includeAnyAssignedProject) {
            this.projectIds = projectIds;
            this.includeAnyAssignedProject = includeAnyAssignedProject;
        }
    }

    private static class RowRenderCache {
        private final String taskId;
        private final String layoutKey;
        private final HudRowVisual rowVisual;
        private final int hudWidth;
        private final double guiScale;

        private RowRenderCache(String taskId, String layoutKey, HudRowVisual rowVisual, int hudWidth, double guiScale) {
            this.taskId = taskId;
            this.layoutKey = layoutKey;
            this.rowVisual = rowVisual;
            this.hudWidth = hudWidth;
            this.guiScale = guiScale;
        }
    }

    public TodoHudRenderer(Minecraft client) {
        this.client = client;
        this.expanded = ModConfig.getInstance().isHudDefaultExpanded();
    }

    /**
     * Parses all item requirements from a Task's tags (e.g., "minecraft:iron_ingot:64", "iron_ingot*32", "create:cogwheel:16").
     */
    public record ResolvedRecipeInfo(
            Item outputItem,
            ItemStack outputStack,
            int outputCountPerCraft,
            List<ParsedItemRequirement> ingredients
    ) {}

    /**
     * Looks up the crafting recipe for a target item in the client's RecipeManager
     * and aggregates its required ingredients (e.g. Iron Bars -> 6x Iron Ingot).
     */
    public static ResolvedRecipeInfo lookupCraftingRecipe(Item targetItem) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || targetItem == null || targetItem == Items.AIR) {
            return null;
        }
        try {
            RecipeManager rm = mc.level.getRecipeManager();
            var registryAccess = mc.level.registryAccess();

            RecipeHolder<?> bestHolder = null;
            for (RecipeHolder<?> holder : rm.getRecipes()) {
                ItemStack result = holder.value().getResultItem(registryAccess);
                if (result != null && !result.isEmpty() && result.getItem() == targetItem) {
                    if (!holder.value().getIngredients().isEmpty()) {
                        bestHolder = holder;
                        // Prefer standard vanilla/mod namespace recipe over nugget unpacking recipes
                        if (!holder.id().getPath().contains("from_block") && !holder.id().getPath().contains("from_nugget")) {
                            break;
                        }
                    }
                }
            }

            if (bestHolder != null) {
                ItemStack resultStack = bestHolder.value().getResultItem(registryAccess);
                int outCount = Math.max(1, resultStack.getCount());

                Map<Item, Integer> counts = new LinkedHashMap<>();
                Map<Item, ItemStack> stacks = new LinkedHashMap<>();

                for (Ingredient ing : bestHolder.value().getIngredients()) {
                    if (ing == null || ing.isEmpty()) continue;
                    ItemStack[] matching = ing.getItems();
                    if (matching.length > 0 && !matching[0].isEmpty()) {
                        Item ingItem = matching[0].getItem();
                        counts.put(ingItem, counts.getOrDefault(ingItem, 0) + 1);
                        stacks.putIfAbsent(ingItem, new ItemStack(ingItem));
                    }
                }

                if (!counts.isEmpty()) {
                    List<ParsedItemRequirement> reqs = new ArrayList<>();
                    for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
                        reqs.add(new ParsedItemRequirement(entry.getKey(), stacks.get(entry.getKey()), entry.getValue()));
                    }
                    return new ResolvedRecipeInfo(targetItem, new ItemStack(targetItem), outCount, reqs);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Parses item requirements from a Task's tags.
     * If a single target item is specified (and not marked "raw:"), it automatically expands
     * into that item's Crafting Recipe ingredients!
     */
    public static List<ParsedItemRequirement> parseAllTaskItemRequirements(Task task) {
        List<ParsedItemRequirement> explicitList = new ArrayList<>();
        if (task == null || task.getTags() == null) return explicitList;

        boolean expandRecipe = true;
        for (String t : task.getTags()) {
            if ("raw_ingredients".equalsIgnoreCase(t)) {
                expandRecipe = false;
                break;
            }
        }

        for (String rawTag : task.getTags()) {
            if (rawTag == null || rawTag.isBlank()) continue;
            String tag = rawTag.trim().toLowerCase();
            if (tag.equals("public_note") || tag.equals("private_note") || tag.equals("note")
                    || tag.equals("recipe") || tag.equals("raw_ingredients") || tag.startsWith("qty:")
                    || tag.startsWith("target_item:") || tag.startsWith("out_per_craft:")) {
                continue;
            }
            if (tag.startsWith("item:")) {
                tag = tag.substring(5).trim();
            }

            int qty = 1;
            String itemPart = tag;

            int starIdx = tag.lastIndexOf('*');
            int xIdx = tag.lastIndexOf(" x");
            if (starIdx > 0) {
                itemPart = tag.substring(0, starIdx).trim();
                try { qty = Math.max(1, Integer.parseInt(tag.substring(starIdx + 1).trim())); } catch (Exception ignored) {}
            } else if (xIdx > 0) {
                itemPart = tag.substring(0, xIdx).trim();
                try { qty = Math.max(1, Integer.parseInt(tag.substring(xIdx + 2).trim())); } catch (Exception ignored) {}
            } else {
                int firstColon = tag.indexOf(':');
                int lastColon = tag.lastIndexOf(':');
                if (lastColon > 0 && lastColon != firstColon) {
                    String suffix = tag.substring(lastColon + 1).trim();
                    try {
                        qty = Math.max(1, Integer.parseInt(suffix));
                        itemPart = tag.substring(0, lastColon).trim();
                    } catch (NumberFormatException ignored) {}
                } else if (firstColon > 0) {
                    String suffix = tag.substring(firstColon + 1).trim();
                    if (suffix.matches("\\d+")) {
                        qty = Math.max(1, Integer.parseInt(suffix));
                        itemPart = "minecraft:" + tag.substring(0, firstColon).trim();
                    }
                }
            }

            if (!itemPart.contains(":")) {
                itemPart = "minecraft:" + itemPart;
            }

            Item matchedItem = findItemByIdOrDisplayName(itemPart);
            if (matchedItem != null && matchedItem != Items.AIR) {
                explicitList.add(new ParsedItemRequirement(matchedItem, new ItemStack(matchedItem), qty));
            }
        }

        // If the task has a single target item and expandRecipe is true, automatically resolve its Crafting Recipe ingredients!
        if (expandRecipe && explicitList.size() == 1) {
            ParsedItemRequirement target = explicitList.get(0);
            ResolvedRecipeInfo recipeInfo = lookupCraftingRecipe(target.item());
            if (recipeInfo != null && !recipeInfo.ingredients().isEmpty()) {
                return recipeInfo.ingredients();
            }
        }

        return explicitList;
    }

    /**
     * Resolves an Item by either its ResourceLocation ("minecraft:iron_bars") or its English display name ("iron bars").
     */
    public static Item findItemByIdOrDisplayName(String query) {
        if (query == null || query.isBlank()) return Items.AIR;
        String clean = query.trim().toLowerCase();
        try {
            ResourceLocation id = ResourceLocation.parse(clean.replace(' ', '_'));
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                return BuiltInRegistries.ITEM.get(id);
            }
        } catch (Exception ignored) {}

        String normalizedSearch = clean.replace("minecraft:", "").replace('_', ' ').trim();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String display = item.getDescription().getString().toLowerCase().trim();
            if (display.equals(normalizedSearch)) {
                return item;
            }
        }
        return Items.AIR;
    }

    public static ParsedItemRequirement parseTaskItemRequirement(Task task) {
        List<ParsedItemRequirement> all = parseAllTaskItemRequirements(task);
        return all.isEmpty() ? null : all.get(0);
    }

    public static boolean isRecipeTask(Task task) {
        if (task == null) return false;
        if (!parseAllTaskItemRequirements(task).isEmpty()) return true;
        if (task.getTags() != null) {
            for (String t : task.getTags()) {
                if ("recipe".equalsIgnoreCase(t)) return true;
                if ("public_note".equalsIgnoreCase(t) || "private_note".equalsIgnoreCase(t) || "note".equalsIgnoreCase(t)) {
                    return false;
                }
            }
        }
        return false;
    }

    public static int parseMultiplierFromTags(Task task) {
        if (task == null || task.getTags() == null) return 1;
        for (String t : task.getTags()) {
            if (t != null && t.startsWith("qty:")) {
                try {
                    return Math.max(1, Integer.parseInt(t.substring(4).trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return 1;
    }

    public static int countItemInPlayerInventory(Player player, Item target) {
        if (player == null || target == null || target == Items.AIR) return 0;
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == target) {
                total += s.getCount();
            }
        }
        return total;
    }

    public void render(GuiGraphics context, float delta) {
        if (client.options.hideGui || client.screen instanceof com.todolist.gui.ConfigScreen) return;

        ModConfig config = ModConfig.getInstance();
        if (!config.isEnableHud() || !ClientBridge.ops().isHudVisible()) return;

        HudViewMode viewMode = resolveViewMode(config);
        Project.Scope scope = getScopeByView(viewMode);
        refreshHudModelIfNeeded(config, viewMode, scope);

        List<Task> publicNotes = getPublicNoteTasks();
        List<Task> privateNotes = getPrivateNoteTasks();
        List<Task> recipeTasks = getRecipeTasks();

        if (publicNotes.isEmpty() && privateNotes.isEmpty() && recipeTasks.isEmpty() && !config.isHudShowWhenEmpty()) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int hudWidth = Math.max(165, config.getHudWidth());
        int panelHeight = calculateSatisfactoryHudHeight(hudWidth, publicNotes, privateNotes, recipeTasks);

        ModConfig.HudPlacement placement = config.resolveHudPlacement(screenWidth, screenHeight, hudWidth, panelHeight);
        int x = placement.getX();
        int y = placement.getY();

        renderSatisfactoryHud(context, x, y, hudWidth, panelHeight, publicNotes, privateNotes, recipeTasks, config);
    }

    private List<Task> getPublicNoteTasks() {
        List<Task> teamTasks = loadTasksByScope(Project.Scope.TEAM);
        List<Task> result = new ArrayList<>();
        for (Task t : teamTasks) {
            if (t != null && !t.isCompleted() && !isRecipeTask(t)) {
                result.add(t);
            }
        }
        return result;
    }

    private List<Task> getPrivateNoteTasks() {
        List<Task> personalTasks = loadTasksByScope(Project.Scope.PERSONAL);
        List<Task> result = new ArrayList<>();
        for (Task t : personalTasks) {
            if (t != null && !t.isCompleted() && !isRecipeTask(t)) {
                result.add(t);
            }
        }
        return result;
    }

    private List<Task> getRecipeTasks() {
        List<Task> result = new ArrayList<>();
        for (Task t : loadTasksByScope(Project.Scope.PERSONAL)) {
            if (t != null && !t.isCompleted() && isRecipeTask(t)) {
                result.add(t);
            }
        }
        for (Task t : loadTasksByScope(Project.Scope.TEAM)) {
            if (t != null && !t.isCompleted() && isRecipeTask(t)) {
                result.add(t);
            }
        }
        return result;
    }

    private int calculateSatisfactoryHudHeight(int width, List<Task> publicNotes, List<Task> privateNotes, List<Task> recipes) {
        if (!expanded) {
            return 28;
        }
        int h = 20; // Top Header
        int innerW = width - 12;

        if (!publicNotes.isEmpty()) {
            h += 15; // "Public Notes" bar
            for (Task note : publicNotes) {
                String text = buildNoteDisplayString(note);
                List<FormattedCharSequence> lines = client.font.split(Component.literal(text), innerW - 6);
                h += Math.max(1, lines.size()) * 10 + 4;
            }
            h += 2;
        }

        if (!privateNotes.isEmpty()) {
            h += 15; // "Private Notes" bar
            for (Task note : privateNotes) {
                String text = buildNoteDisplayString(note);
                List<FormattedCharSequence> lines = client.font.split(Component.literal(text), innerW - 6);
                h += Math.max(1, lines.size()) * 10 + 4;
            }
            h += 2;
        }

        if (!recipes.isEmpty() || (publicNotes.isEmpty() && privateNotes.isEmpty())) {
            h += 15; // "Recipes" bar
            for (Task recipe : recipes) {
                List<ParsedItemRequirement> reqs = parseAllTaskItemRequirements(recipe);
                boolean hasSubtitle = recipe.getDescription() != null && !recipe.getDescription().isBlank();
                h += (hasSubtitle ? 22 : 14); // Title + white line
                h += Math.max(1, reqs.size()) * 20 + 4;
            }
        }

        return Math.max(32, h + 4);
    }

    private String buildNoteDisplayString(Task note) {
        if (note == null) return "";
        String title = note.getTitle() == null ? "" : note.getTitle().trim();
        String desc = note.getDescription() == null ? "" : note.getDescription().trim();
        if (!title.isEmpty() && !desc.isEmpty()) {
            return title + ": " + desc;
        }
        return !title.isEmpty() ? title : desc;
    }

    /**
     * Renders the authentic Satisfactory To-Do List HUD overlay matching the in-game screenshots.
     */
    private void renderSatisfactoryHud(GuiGraphics context, int x, int y, int width, int panelHeight,
                                       List<Task> publicNotes, List<Task> privateNotes, List<Task> recipes,
                                       ModConfig config) {
        float opacity = (float) config.getHudOpacity();
        int bodyBgColor = applyBackgroundOpacity(COL_FICSIT_BODY_BG, opacity);
        int headerBgColor = applyBackgroundOpacity(COL_FICSIT_HEADER_BG, Math.min(1.0f, opacity + 0.15f));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 1. Dark Translucent Backdrop & Border
        context.fill(x, y, x + width, y + panelHeight, bodyBgColor);
        context.renderOutline(x, y, width, panelHeight, applyBackgroundOpacity(COL_FICSIT_BORDER, opacity));

        // 2. Top Header Bar ("≡ To-Do List")
        int headerH = 16;
        context.fill(x, y, x + width, y + headerH, headerBgColor);
        context.fill(x, y + headerH - 1, x + width, y + headerH, toOpaqueColor(COL_FICSIT_ORANGE));
        context.drawString(client.font, "≡ To-Do List", x + 6, y + 4, toOpaqueColor(0xFFFFFFFF), false);

        int currentY = y + headerH + 3;
        int innerX = x + 6;
        int innerW = width - 12;

        if (!expanded) {
            int totalItems = publicNotes.size() + privateNotes.size() + recipes.size();
            context.drawString(client.font, totalItems + " active entries [H]", innerX, currentY, toOpaqueColor(0xFFB0B5BC), false);
            return;
        }

        // 3. PUBLIC NOTES (FTB Team Shared)
        if (!publicNotes.isEmpty()) {
            currentY = renderHudSectionBar(context, innerX, currentY, innerW, "Public Notes");
            for (Task note : publicNotes) {
                String text = buildNoteDisplayString(note);
                for (FormattedCharSequence line : client.font.split(Component.literal(text), innerW - 6)) {
                    context.drawString(client.font, line, innerX + 3, currentY, toOpaqueColor(0xFFE8ECEF), false);
                    currentY += 10;
                }
                currentY += 4;
            }
            currentY += 2;
        }

        // 4. PRIVATE NOTES (Personal)
        if (!privateNotes.isEmpty()) {
            currentY = renderHudSectionBar(context, innerX, currentY, innerW, "Private Notes");
            for (Task note : privateNotes) {
                String text = buildNoteDisplayString(note);
                for (FormattedCharSequence line : client.font.split(Component.literal(text), innerW - 6)) {
                    context.drawString(client.font, line, innerX + 3, currentY, toOpaqueColor(0xFFD0D5DC), false);
                    currentY += 10;
                }
                currentY += 4;
            }
            currentY += 2;
        }

        // 5. RECIPES (Satisfactory-style Item & Ingredient Checklist)
        if (!recipes.isEmpty() || (publicNotes.isEmpty() && privateNotes.isEmpty())) {
            currentY = renderHudSectionBar(context, innerX, currentY, innerW, "Recipes");
            if (recipes.isEmpty()) {
                context.drawString(client.font, "No pinned recipes.", innerX + 3, currentY + 2, toOpaqueColor(0xFF888D94), false);
            } else {
                for (Task recipe : recipes) {
                    currentY = drawSatisfactoryRecipeBlock(context, innerX, currentY, innerW, recipe);
                }
            }
        }
    }

    private int renderHudSectionBar(GuiGraphics context, int x, int y, int width, String title) {
        context.fill(x, y, x + width, y + 12, COL_FICSIT_SUBHEADER);
        context.drawCenteredString(client.font, title, x + width / 2, y + 2, toOpaqueColor(0xFFFFFFFF));
        return y + 15;
    }

    /**
     * Draws a Satisfactory Recipe Entry (matches Screenshot 1:
     *   Recipe: Alternate: Steel Rod
     *   Iron Rod                                   4
     *   --------------------------------------------
     *   Steel Ingot                         0 / 1 [icon]
     *   [========== progress bar ==========]
     */
    private int drawSatisfactoryRecipeBlock(GuiGraphics context, int x, int y, int width, Task task) {
        int curY = y;
        int multiplier = parseMultiplierFromTags(task);

        // Optional small grey subtitle ("Recipe: ...")
        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            context.pose().pushPose();
            context.pose().translate(x, curY, 0);
            context.pose().scale(0.75f, 0.75f, 1.0f);
            context.drawString(client.font, task.getDescription().trim(), 0, 0, toOpaqueColor(0xFF9EA4B0), false);
            context.pose().popPose();
            curY += 8;
        }

        // Bold Recipe/Task Title on the left + Multiplier Count on the right
        String title = (task.getTitle() == null || task.getTitle().isBlank()) ? "Item" : task.getTitle();
        String qtyStr = String.valueOf(multiplier);
        context.drawString(client.font, title, x, curY, toOpaqueColor(0xFFFFFFFF), false);
        context.drawString(client.font, qtyStr, x + width - client.font.width(qtyStr), curY, toOpaqueColor(0xFFFFFFFF), false);
        curY += 11;

        // Crisp 1px White Horizontal Divider Line
        context.fill(x, curY, x + width, curY + 1, toOpaqueColor(0xFFD8DEE9));
        curY += 4;

        // Ingredients Breakdown Rows
        List<ParsedItemRequirement> reqs = parseAllTaskItemRequirements(task);
        if (reqs.isEmpty()) {
            // If no item tag was parsed, render a single progress row for the task itself
            context.drawString(client.font, title, x, curY + 2, toOpaqueColor(0xFFD8DEE9), false);
            String status = task.isCompleted() ? "1 / 1" : "0 / 1";
            context.drawString(client.font, status, x + width - client.font.width(status), curY + 2, toOpaqueColor(0xFFFFFFFF), false);
            curY += 14;
            context.fill(x, curY, x + width, curY + 2, 0x88000000);
            if (task.isCompleted()) {
                context.fill(x, curY, x + width, curY + 2, toOpaqueColor(COL_FICSIT_ORANGE));
            }
            return curY + 6;
        }

        for (ParsedItemRequirement req : reqs) {
            int have = client.player != null ? countItemInPlayerInventory(client.player, req.item()) : 0;
            int needed = Math.max(1, req.neededCount() * multiplier);
            float progress = Math.clamp((float) have / needed, 0.0f, 1.0f);

            int iconBoxSize = 18;
            int iconBoxX = x + width - iconBoxSize;
            int barWidth = width - iconBoxSize - 4;

            // Ingredient Name on Left + "0 / 1" Ratio on Right (before icon box)
            String ingName = trimWithEllipsis(req.stack().getHoverName().getString(), barWidth - 36);
            String ratioStr = have + " / " + needed;
            context.drawString(client.font, ingName, x, curY + 2, toOpaqueColor(0xFFE5E9F0), false);
            context.drawString(client.font, ratioStr, iconBoxX - 4 - client.font.width(ratioStr), curY + 2,
                    toOpaqueColor(have >= needed ? COL_FICSIT_GREEN : 0xFFFFFFFF), false);

            // Progress Bar Track + Fill below Ingredient Name
            int barY = curY + 13;
            context.fill(x, barY, x + barWidth, barY + 3, 0xAA101214);
            if (progress > 0.0f) {
                int fillW = Math.max(1, Math.round(barWidth * progress));
                context.fill(x, barY, x + fillW, barY + 3,
                        toOpaqueColor(have >= needed ? COL_FICSIT_GREEN : COL_FICSIT_ORANGE));
            }

            // Right 18x18 Rounded Grey Item Slot Box + 3D Item Icon
            context.fill(iconBoxX, curY, iconBoxX + iconBoxSize, curY + iconBoxSize, 0xCC4A4E57);
            context.renderOutline(iconBoxX, curY, iconBoxSize, iconBoxSize, 0xFF282B30);
            context.pose().pushPose();
            context.pose().translate(iconBoxX + 1, curY + 1, 50);
            context.renderItem(req.stack(), 0, 0);
            context.pose().popPose();

            curY += 21;
        }

        return curY + 3;
    }

    private void refreshHudModelIfNeeded(ModConfig config, HudViewMode viewMode, Project.Scope scope) {
        long now = System.currentTimeMillis();
        String sourceMode = config.getHudProjectSource();
        String activeProjectId = ClientBridge.ops().getActiveProjectId();
        String playerUuid = client.player == null ? "" : client.player.getStringUUID();
        int hudWidth = config.getHudWidth();
        double guiScale = client.getWindow().getGuiScale();
        boolean contextChanged = hasHudRefreshContextChanged(
                viewMode, scope, sourceMode, activeProjectId, playerUuid, hudWidth, guiScale
        );
        boolean shouldInspectModel = !hudModelCacheInitialized
                || contextChanged
                || now - lastHudModelRefreshMs >= HUD_MODEL_REFRESH_INTERVAL_MS;
        if (!shouldInspectModel) {
            return;
        }

        H2TaskQueryService.HudTaskQueryResult sqlResult = queryHudTasksFromH2(config, viewMode, scope);
        List<Task> pending;
        List<Task> done;
        int pendingTotal;
        int doneTotal;
        int pendingSummaryTotal;
        int doneSummaryTotal;
        parentProgressByTaskId.clear();
        if (sqlResult != null) {
            List<Task> topLevelTasks = new ArrayList<>(sqlResult.getPendingTasks());
            topLevelTasks.addAll(sqlResult.getDoneTasks());
            HudDisplayModel displayModel = buildHudDisplayModel(topLevelTasks, config.isHudShowSubtasks());
            pending = displayModel.pendingTasks;
            done = displayModel.doneTasks;
            pendingTotal = sqlResult.getPendingTotal();
            doneTotal = sqlResult.getDoneTotal();
            pendingSummaryTotal = sqlResult.getPendingTotal();
            doneSummaryTotal = sqlResult.getDoneTotal();
            parentProgressByTaskId.putAll(displayModel.parentProgress);
        } else {
            List<Task> tasks = loadTasksByScope(scope);
            tasks = filterByViewMode(tasks, viewMode);
            tasks = filterByProjectSource(tasks, scope, config);

            HudDisplayModel displayModel = buildHudDisplayModel(tasks, config.isHudShowSubtasks());
            pending = displayModel.pendingTasks;
            done = displayModel.doneTasks;
            pendingTotal = pending.size();
            doneTotal = done.size();
            pendingSummaryTotal = countTopLevelTasks(pending);
            doneSummaryTotal = countTopLevelTasks(done);
            parentProgressByTaskId.putAll(displayModel.parentProgress);
        }
        cachedPendingTasks = pending;
        cachedDoneTasks = done;
        cachedPendingTotalCount = pendingTotal;
        cachedDoneTotalCount = doneTotal;
        cachedPendingSummaryCount = pendingSummaryTotal;
        cachedDoneSummaryCount = doneSummaryTotal;
        rebuildRowRenderCache(pending, done, hudWidth);
        cachedHudViewMode = viewMode;
        cachedHudScope = scope;
        cachedProjectSourceMode = sourceMode;
        cachedActiveProjectId = valueOrEmpty(activeProjectId);
        cachedPlayerUuid = playerUuid;
        cachedLayoutHudWidth = hudWidth;
        cachedGuiScale = guiScale;
        hudModelCacheInitialized = true;
        lastHudModelRefreshMs = now;
    }

    private boolean hasHudRefreshContextChanged(HudViewMode viewMode, Project.Scope scope, String sourceMode,
                                                String activeProjectId, String playerUuid, int hudWidth, double guiScale) {
        return viewMode != cachedHudViewMode
                || scope != cachedHudScope
                || !sourceMode.equals(cachedProjectSourceMode)
                || !valueOrEmpty(activeProjectId).equals(cachedActiveProjectId)
                || !playerUuid.equals(cachedPlayerUuid)
                || hudWidth != cachedLayoutHudWidth
                || Double.compare(guiScale, cachedGuiScale) != 0;
    }

    private HudDisplayModel buildHudDisplayModel(List<Task> source, boolean showSubtasks) {
        List<Task> input = source == null ? List.of() : new ArrayList<>(source);
        if (input.isEmpty()) {
            return new HudDisplayModel(List.of(), List.of(), Map.of());
        }

        Map<String, List<Task>> subtasksByParentId = new LinkedHashMap<>();
        Map<String, Integer> originalIndexes = new HashMap<>();
        Map<String, ParentSubtaskProgress> progressByParentId = new HashMap<>();
        List<Task> orphanSubtasks = new ArrayList<>();

        for (int index = 0; index < input.size(); index++) {
            Task task = input.get(index);
            if (task == null) continue;
            String taskId = valueOrEmpty(task.getId());
            if (!taskId.isEmpty()) {
                originalIndexes.put(taskId, index);
            }
            if (task.isSubtask()) {
                String parentTaskId = valueOrEmpty(task.getParentTaskId());
                subtasksByParentId.computeIfAbsent(parentTaskId, ignored -> new ArrayList<>()).add(task);
            }
        }

        Comparator<Task> subtaskComparator = Comparator
                .comparingLong(Task::getSubtaskSortOrder)
                .thenComparingInt(task -> originalIndexes.getOrDefault(valueOrEmpty(task.getId()), Integer.MAX_VALUE));
        for (List<Task> siblingSubtasks : subtasksByParentId.values()) {
            siblingSubtasks.sort(subtaskComparator);
        }

        List<Task> pending = new ArrayList<>(input.size());
        List<Task> done = new ArrayList<>(input.size());
        for (Task task : input) {
            if (task == null || task.isSubtask()) continue;
            String parentTaskId = valueOrEmpty(task.getId());
            List<Task> childTasks = subtasksByParentId.remove(parentTaskId);
            ParentSubtaskProgress progress = buildParentSubtaskProgress(childTasks);
            if (progress != null && !parentTaskId.isEmpty()) {
                progressByParentId.put(parentTaskId, progress);
            }
            List<Task> target = isHudGroupCompleted(task, progress) ? done : pending;
            target.add(task);
            if (showSubtasks && childTasks != null && !childTasks.isEmpty()) {
                target.addAll(childTasks);
            }
        }

        if (showSubtasks) {
            for (List<Task> remainingSubtasks : subtasksByParentId.values()) {
                orphanSubtasks.addAll(remainingSubtasks);
            }
            orphanSubtasks.sort(Comparator.comparingInt(task -> originalIndexes.getOrDefault(valueOrEmpty(task.getId()), Integer.MAX_VALUE)));
            for (Task orphanSubtask : orphanSubtasks) {
                if (orphanSubtask == null) continue;
                (orphanSubtask.isCompleted() ? done : pending).add(orphanSubtask);
            }
        }
        return new HudDisplayModel(pending, done, progressByParentId);
    }

    private H2TaskQueryService.HudTaskQueryResult queryHudTasksFromH2(ModConfig config, HudViewMode viewMode, Project.Scope scope) {
        return null;
    }

    private HudViewMode resolveViewMode(ModConfig config) {
        String raw = config.getHudDefaultView();
        if ("TEAM_UNASSIGNED".equalsIgnoreCase(raw)) {
            return ClientBridge.ops().isTeamProjectsEnabled() ? HudViewMode.TEAM_UNASSIGNED : HudViewMode.PERSONAL;
        }
        if ("TEAM_ALL".equalsIgnoreCase(raw)) {
            return ClientBridge.ops().isTeamProjectsEnabled() ? HudViewMode.TEAM_ALL : HudViewMode.PERSONAL;
        }
        if ("TEAM_ASSIGNED".equalsIgnoreCase(raw)) {
            return ClientBridge.ops().isTeamProjectsEnabled() ? HudViewMode.TEAM_ASSIGNED : HudViewMode.PERSONAL;
        }
        return HudViewMode.PERSONAL;
    }

    private Project.Scope getScopeByView(HudViewMode viewMode) {
        return viewMode == HudViewMode.PERSONAL ? Project.Scope.PERSONAL : Project.Scope.TEAM;
    }

    private List<Task> loadTasksByScope(Project.Scope scope) {
        long now = System.currentTimeMillis();
        long reloadIntervalMs = 1000L;
        if (scope == Project.Scope.PERSONAL) {
            if (cachedPersonalTasksFromGui) {
                return new ArrayList<>(cachedPersonalTasks);
            }
            if (StorageBackendFactory.isH2Selected()) {
                if (!cachedPersonalTasksInitialized) {
                    cachedPersonalTasks = ClientTaskStorageHelper.loadPersonalTasksSafe(TodoListCommon.getTaskStorage(), client);
                    cachedPersonalTasksInitialized = true;
                    lastPersonalReloadMs = now;
                }
                return new ArrayList<>(cachedPersonalTasks);
            }
            long localLastSaved = ClientTaskStorageHelper.getPersonalTasksLastSaved(TodoListCommon.getTaskStorage(), client);
            boolean shouldReload = cachedPersonalTasks.isEmpty()
                    || localLastSaved != cachedPersonalLastSavedMs
                    || (now - lastPersonalReloadMs >= reloadIntervalMs && localLastSaved == 0L && cachedPersonalLastSavedMs == 0L);
            if (shouldReload) {
                cachedPersonalTasks = ClientTaskStorageHelper.loadPersonalTasksSafe(TodoListCommon.getTaskStorage(), client);
                cachedPersonalTasksInitialized = true;
                cachedPersonalLastSavedMs = localLastSaved;
                lastPersonalReloadMs = now;
            }
            return new ArrayList<>(cachedPersonalTasks);
        }
        if (now - lastTeamReloadMs >= reloadIntervalMs) {
            TaskManager teamTaskManager = ClientBridge.ops().getTeamTaskManager();
            cachedTeamTasks = teamTaskManager == null ? new ArrayList<>() : teamTaskManager.getAllTasks();
            lastTeamReloadMs = now;
        }
        return new ArrayList<>(cachedTeamTasks);
    }

    private List<Task> filterByViewMode(List<Task> source, HudViewMode viewMode) {
        if (viewMode == HudViewMode.PERSONAL) return source;
        List<Task> result = new ArrayList<>();
        String myUuid = client.player == null ? null : client.player.getStringUUID();
        for (Task task : source) {
            if (viewMode == HudViewMode.TEAM_UNASSIGNED && TaskAssignmentSupport.isVisibleInTeamUnassigned(task, source)) {
                result.add(task);
            } else if (viewMode == HudViewMode.TEAM_ALL) {
                result.add(task);
            } else if (viewMode == HudViewMode.TEAM_ASSIGNED && TaskAssignmentSupport.isVisibleInTeamAssigned(task, source, myUuid)) {
                result.add(task);
            }
        }
        return result;
    }

    private List<Task> filterByProjectSource(List<Task> source, Project.Scope scope, ModConfig config) {
        ProjectManager manager = TodoListCommon.getProjectManager();
        List<Project> scopedProjects = manager.getProjectsByScope(scope);
        String sourceMode = config.getHudProjectSource();
        Set<String> allowedProjectIds = new HashSet<>();
        Project activeProject = resolveScopedActiveProject(manager, scope, scopedProjects);

        if ("CURRENT".equalsIgnoreCase(sourceMode)) {
            if (activeProject != null) {
                allowedProjectIds.add(activeProject.getId());
            } else {
                collectProjectIds(scopedProjects, allowedProjectIds);
            }
        } else if ("STARRED".equalsIgnoreCase(sourceMode)) {
            for (Project project : scopedProjects) {
                if (config.isHudProjectStarred(project.getId())) {
                    allowedProjectIds.add(project.getId());
                }
            }
            if (scope == Project.Scope.TEAM && allowedProjectIds.isEmpty() && scopedProjects.isEmpty()) {
                return filterAssignedProjectTasks(source);
            }
        } else {
            collectProjectIds(scopedProjects, allowedProjectIds);
        }

        if (scope == Project.Scope.TEAM && allowedProjectIds.isEmpty()) {
            return filterAssignedProjectTasks(source);
        }

        List<Task> result = new ArrayList<>();
        for (Task task : source) {
            if (task.isProjectUnassigned()) continue;
            if (allowedProjectIds.contains(task.getProjectId())) {
                result.add(task);
            }
        }
        return result;
    }

    private Project resolveScopedActiveProject(ProjectManager manager, Project.Scope scope, List<Project> scopedProjects) {
        Project scopedActive = ClientBridge.getActiveProject(manager, scope);
        if (scopedActive != null) return scopedActive;
        String activeProjectId = ClientBridge.ops().getActiveProjectId();
        if (activeProjectId != null && !activeProjectId.isEmpty()) {
            Project activeProject = manager.getProject(activeProjectId);
            if (activeProject != null && activeProject.getScope() == scope) {
                return activeProject;
            }
        }
        for (Project project : scopedProjects) {
            if (scope == Project.Scope.TEAM && project.isDefaultTeamProject()) return project;
            if (scope == Project.Scope.PERSONAL && project.isDefaultPersonalProject()) return project;
        }
        return scopedProjects.isEmpty() ? null : scopedProjects.get(0);
    }

    private void collectProjectIds(List<Project> projects, Set<String> projectIds) {
        for (Project project : projects) {
            if (project != null && project.getId() != null && !project.getId().isEmpty()) {
                projectIds.add(project.getId());
            }
        }
    }

    private List<Task> filterAssignedProjectTasks(List<Task> source) {
        List<Task> result = new ArrayList<>();
        for (Task task : source) {
            if (!task.isProjectUnassigned()) {
                result.add(task);
            }
        }
        return result;
    }

    private Component buildHeaderTitle(HudViewMode viewMode) {
        Project.Scope scope = getScopeByView(viewMode);
        ProjectManager manager = TodoListCommon.getProjectManager();
        List<Project> scopedProjects = manager.getProjectsByScope(scope);
        String projectName = buildHeaderProjectSourceText(scope, manager, scopedProjects, ModConfig.getInstance().getHudProjectSource());

        StringBuilder builder = new StringBuilder(getSpaceLabel(scope).getString());
        if (!projectName.isEmpty()) {
            builder.append('-').append(projectName);
        }

        Component teamViewLabel = getTeamViewLabel(viewMode);
        if (teamViewLabel != null) {
            builder.append('-').append(teamViewLabel.getString());
        }
        return Component.literal(builder.toString());
    }

    private String buildHeaderProjectSourceText(Project.Scope scope, ProjectManager manager, List<Project> scopedProjects, String sourceMode) {
        if ("STARRED".equalsIgnoreCase(sourceMode)) return LABEL_PROJECT_SOURCE_STARRED.getString();
        if ("ALL".equalsIgnoreCase(sourceMode)) return LABEL_PROJECT_SOURCE_ALL.getString();
        Project activeProject = resolveScopedActiveProject(manager, scope, scopedProjects);
        return ProjectNameFormatter.toDisplayText(activeProject).getString().trim();
    }

    private Component getSpaceLabel(Project.Scope scope) {
        return scope == Project.Scope.TEAM ? LABEL_SPACE_TEAM : LABEL_SPACE_PERSONAL;
    }

    private Component getTeamViewLabel(HudViewMode viewMode) {
        return switch (viewMode) {
            case TEAM_UNASSIGNED -> LABEL_TEAM_VIEW_UNASSIGNED;
            case TEAM_ALL -> LABEL_TEAM_VIEW_ALL;
            case TEAM_ASSIGNED -> LABEL_TEAM_VIEW_ASSIGNED;
            case PERSONAL -> null;
        };
    }

    private void rebuildRowRenderCache(List<Task> pending, List<Task> done, int hudWidth) {
        rowRenderCacheByTaskId.clear();
        for (Task task : pending) {
            RowRenderCache cache = buildRowRenderCache(task, hudWidth);
            rowRenderCacheByTaskId.put(cache.taskId, cache);
        }
        for (Task task : done) {
            RowRenderCache cache = buildRowRenderCache(task, hudWidth);
            rowRenderCacheByTaskId.put(cache.taskId, cache);
        }
    }

    private RowRenderCache buildRowRenderCache(Task task, int hudWidth) {
        String taskId = valueOrEmpty(task.getId());
        double guiScale = client.getWindow().getGuiScale();
        String layoutKey = buildRowLayoutKey(task, hudWidth, guiScale);
        HudRowVisual rowVisual = buildRowVisual(task, hudWidth);
        return new RowRenderCache(taskId, layoutKey, rowVisual, hudWidth, guiScale);
    }

    private HudRowVisual buildRowVisual(Task task, int hudWidth) {
        int rowWidth = Math.max(0, hudWidth - 8);
        boolean subtask = task != null && task.isSubtask();
        int prefixWidth = subtask ? client.font.width(HUD_SUBTASK_PREFIX) : 0;
        int priorityBlockOffset = subtask ? HUD_SUBTASK_EXTRA_INDENT + prefixWidth : 0;
        int currentOffset = priorityBlockOffset + HUD_PRIORITY_BLOCK_WIDTH + HUD_PRIORITY_BLOCK_GAP;
        int maxLabelWidth = Math.max(18, rowWidth / 3);

        String tagToken = buildHudLabelToken(resolveFirstTaskTag(task), maxLabelWidth);
        ParentSubtaskProgress parentProgress = resolveParentSubtaskProgress(task);
        String progressToken = buildParentProgressText(parentProgress);
        int progressWidth = progressToken.isEmpty() ? 0 : client.font.width(progressToken);

        int assigneeOffset = 0;
        int tagOffset = 0;
        if (!tagToken.isEmpty()) {
            tagOffset = currentOffset;
            currentOffset += measureScaledTextWidth(tagToken, HUD_LABEL_SCALE);
        }

        int titleOffset = currentOffset;
        int titleMaxWidth = Math.max(0, rowWidth - titleOffset - (progressWidth <= 0 ? 0 : progressWidth + 6));
        String titleCore = trimWithEllipsis(buildHudRowTitle(task), titleMaxWidth);
        boolean rowCompleted = isTaskCompletedInHud(task);
        Component prefixText = null;
        if (subtask) {
            prefixText = Component.literal(HUD_SUBTASK_PREFIX).withStyle(rowCompleted ? ChatFormatting.GRAY : ChatFormatting.WHITE);
        }
        Component titleText = titleCore.isEmpty()
                ? null
                : (rowCompleted
                ? Component.literal(titleCore).withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH)
                : Component.literal(titleCore).withStyle(ChatFormatting.WHITE));
        int progressOffset = progressWidth <= 0 ? 0 : Math.max(titleOffset, rowWidth - progressWidth);
        Component progressText = progressToken.isEmpty()
                ? null
                : Component.literal(progressToken).withStyle(rowCompleted ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY);

        return new HudRowVisual(priorityBlockOffset, resolvePriorityBlockColor(task.getPriority()),
                subtask ? HUD_SUBTASK_EXTRA_INDENT : 0, prefixText, "",
                null,
                tagToken.isEmpty() ? null : tagToken,
                assigneeOffset, tagOffset, titleOffset, titleText, progressOffset, progressText);
    }

    private String buildRowLayoutKey(Task task, int hudWidth, double guiScale) {
        return valueOrEmpty(task.getId()) + '|'
                + valueOrEmpty(task.getTitle()) + '|'
                + valueOrEmpty(task.getParentTaskId()) + '|'
                + task.getPriority().name() + '|'
                + isTaskCompletedInHud(task) + '|'
                + buildParentProgressText(resolveParentSubtaskProgress(task)) + '|'
                + resolveFirstTaskTag(task) + '|'
                + hudWidth + '|'
                + guiScale;
    }

    private ParentSubtaskProgress resolveParentSubtaskProgress(Task task) {
        if (task == null || task.isSubtask()) return null;
        String taskId = valueOrEmpty(task.getId());
        return taskId.isEmpty() ? null : parentProgressByTaskId.get(taskId);
    }

    private String buildParentProgressText(ParentSubtaskProgress progress) {
        if (progress == null || progress.totalCount <= 0) return "";
        return progress.completedCount + "/" + progress.totalCount;
    }

    private ParentSubtaskProgress buildParentSubtaskProgress(List<Task> subtasks) {
        if (subtasks == null || subtasks.isEmpty()) return null;
        int completedCount = 0;
        for (Task subtask : subtasks) {
            if (subtask != null && subtask.isCompleted()) {
                completedCount++;
            }
        }
        return new ParentSubtaskProgress(completedCount, subtasks.size());
    }

    private boolean isHudGroupCompleted(Task task, ParentSubtaskProgress progress) {
        if (task == null) return false;
        if (progress != null && progress.totalCount > 0) {
            return progress.completedCount >= progress.totalCount;
        }
        return task.isCompleted();
    }

    private boolean isTaskCompletedInHud(Task task) {
        if (task == null) return false;
        if (task.isSubtask()) return task.isCompleted();
        return isHudGroupCompleted(task, resolveParentSubtaskProgress(task));
    }

    private int resolvePriorityBlockColor(Task.Priority priority) {
        return priority == null ? Task.Priority.MEDIUM.getColor() : priority.getColor();
    }

    private String buildHudRowTitle(Task task) {
        return task == null ? "" : valueOrEmpty(task.getTitle());
    }

    private String buildHudLabelToken(String rawLabel, int maxScaledWidth) {
        String limited = limitLabelContent(rawLabel, HUD_LABEL_MAX_CHARS);
        if (limited.isEmpty()) return "";
        return trimScaledTextWithEllipsis("[" + limited + "]", maxScaledWidth, HUD_LABEL_SCALE);
    }

    private String resolveFirstTaskTag(Task task) {
        if (task == null) return "";
        for (String tag : task.getTags()) {
            if (tag == null) continue;
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) return trimmed;
        }
        return "";
    }

    private String limitLabelContent(String text, int maxChars) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.length() <= maxChars) return trimmed;
        return trimmed.substring(0, maxChars) + "...";
    }

    private int measureScaledTextWidth(String text, float scale) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, Math.round(client.font.width(text) * scale));
    }

    private String trimScaledTextWithEllipsis(String text, int maxScaledWidth, float scale) {
        if (text == null || maxScaledWidth <= 0) return "";
        if (measureScaledTextWidth(text, scale) <= maxScaledWidth) return text;
        int unscaledWidth = Math.max(1, (int) Math.floor(maxScaledWidth / Math.max(0.01F, scale)));
        return trimWithEllipsis(text, unscaledWidth);
    }

    private static int applyOpacityToColor(int color, float opacity) {
        int argb = (color & 0xFF000000) == 0 ? (0xFF000000 | (color & 0x00FFFFFF)) : color;
        int baseAlpha = (argb >>> 24) & 0xFF;
        int scaledAlpha = Math.round(baseAlpha * Math.max(0.0f, Math.min(1.0f, opacity)));
        if (scaledAlpha < 0) scaledAlpha = 0;
        if (scaledAlpha > 255) scaledAlpha = 255;
        return (scaledAlpha << 24) | (argb & 0x00FFFFFF);
    }

    private static int applyBackgroundOpacity(int color, float opacity) {
        return applyOpacityToColor(color, opacity);
    }

    private static int toOpaqueColor(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private String trimWithEllipsis(String text, int maxWidth) {
        if (text == null || maxWidth <= 0) return "";
        if (client.font.width(text) <= maxWidth) return text;
        int ellipsisWidth = client.font.width(ELLIPSIS);
        int coreWidth = maxWidth - ellipsisWidth;
        if (coreWidth <= 0) return "...";
        String core = client.font.plainSubstrByWidth(text, coreWidth);
        return core + "...";
    }

    public void forceRefreshTasks() {
        lastPersonalReloadMs = 0L;
        lastTeamReloadMs = 0L;
        cachedPersonalLastSavedMs = -1L;
        lastHudModelRefreshMs = 0L;
        hudModelCacheInitialized = false;
        cachedPersonalTasksInitialized = false;
        cachedPersonalTasksFromGui = false;
        cachedLayoutHudWidth = -1;
        cachedGuiScale = -1D;
        cachedPersonalTasks = new ArrayList<>();
        cachedTeamTasks = new ArrayList<>();
        cachedPendingTasks = new ArrayList<>();
        cachedDoneTasks = new ArrayList<>();
        cachedPendingTotalCount = 0;
        cachedDoneTotalCount = 0;
        cachedPendingSummaryCount = 0;
        cachedDoneSummaryCount = 0;
        parentProgressByTaskId.clear();
        rowRenderCacheByTaskId.clear();
    }

    public void syncPersonalTasksFromGui(List<Task> tasks) {
        cachedPersonalTasks = copyTaskList(tasks);
        cachedPersonalTasksInitialized = true;
        cachedPersonalTasksFromGui = true;
        lastPersonalReloadMs = System.currentTimeMillis();
        invalidateHudModelCache();
    }

    private void invalidateHudModelCache() {
        lastHudModelRefreshMs = 0L;
        hudModelCacheInitialized = false;
        cachedLayoutHudWidth = -1;
        cachedGuiScale = -1D;
        cachedPendingTasks = new ArrayList<>();
        cachedDoneTasks = new ArrayList<>();
        cachedPendingTotalCount = 0;
        cachedDoneTotalCount = 0;
        cachedPendingSummaryCount = 0;
        cachedDoneSummaryCount = 0;
        parentProgressByTaskId.clear();
        rowRenderCacheByTaskId.clear();
    }

    private List<Task> copyTaskList(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) return new ArrayList<>();
        List<Task> copies = new ArrayList<>();
        for (Task task : tasks) {
            if (task != null) copies.add(Task.fromNbt(task.toNbt()));
        }
        return copies;
    }

    public int getCurrentPanelHeight() {
        ModConfig config = ModConfig.getInstance();
        int hudWidth = Math.max(165, config.getHudWidth());
        return calculateSatisfactoryHudHeight(hudWidth, getPublicNoteTasks(), getPrivateNoteTasks(), getRecipeTasks());
    }

    void refreshHudModelForTest() {
        ModConfig config = ModConfig.getInstance();
        HudViewMode viewMode = resolveViewMode(config);
        Project.Scope scope = getScopeByView(viewMode);
        refreshHudModelIfNeeded(config, viewMode, scope);
    }

    String getResolvedViewModeNameForTest() { return resolveViewMode(ModConfig.getInstance()).name(); }
    String getHeaderTitleForTest() { return buildHeaderTitle(resolveViewMode(ModConfig.getInstance())).getString(); }
    List<Task> getCachedPendingTasksForTest() { return new ArrayList<>(cachedPendingTasks); }
    List<Task> getCachedDoneTasksForTest() { return new ArrayList<>(cachedDoneTasks); }
    int getRowRenderCacheSizeForTest() { return rowRenderCacheByTaskId.size(); }
    int getPriorityBlockColorForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null ? 0 : cache.rowVisual.priorityBlockColor;
    }
    String getPriorityTextForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null ? "" : cache.rowVisual.priorityText;
    }
    String getAssigneeTextForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null || cache.rowVisual.assigneeText == null ? "" : cache.rowVisual.assigneeText;
    }
    String getTagTextForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null || cache.rowVisual.tagText == null ? "" : cache.rowVisual.tagText;
    }
    String getRowTitleTextForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null || cache.rowVisual.titleText == null ? "" : cache.rowVisual.titleText.getString();
    }
    String getRowPrefixTextForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null || cache.rowVisual.prefixText == null ? "" : cache.rowVisual.prefixText.getString();
    }
    String getRowTrailingTextForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null || cache.rowVisual.progressText == null ? "" : cache.rowVisual.progressText.getString();
    }
    boolean isRowTitleStrikethroughForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache != null && cache.rowVisual.titleText != null && cache.rowVisual.titleText.getStyle().isStrikethrough();
    }
    boolean isRowTitleGrayForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        if (cache == null || cache.rowVisual.titleText == null || cache.rowVisual.titleText.getStyle().getColor() == null) return false;
        return cache.rowVisual.titleText.getStyle().getColor().getValue() == ChatFormatting.GRAY.getColor();
    }
    int getRowTitleOffsetForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null ? 0 : cache.rowVisual.titleOffset;
    }
    int getPriorityBlockOffsetForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null ? 0 : cache.rowVisual.priorityBlockOffset;
    }
    int getShownPendingCountForTest() {
        return buildRenderPlan(ModConfig.getInstance(), cachedPendingTasks, cachedDoneTasks, cachedPendingTotalCount, cachedDoneTotalCount).shownPending;
    }
    int getShownDoneCountForTest() {
        return buildRenderPlan(ModConfig.getInstance(), cachedPendingTasks, cachedDoneTasks, cachedPendingTotalCount, cachedDoneTotalCount).shownDone;
    }
    int getHiddenCountForTest() {
        return buildRenderPlan(ModConfig.getInstance(), cachedPendingTasks, cachedDoneTasks, cachedPendingTotalCount, cachedDoneTotalCount).hiddenCount;
    }
    int getPanelBackgroundColorForTest(float opacity) { return applyBackgroundOpacity(0xFF232323, opacity); }
    int getHeaderTextColorForTest() { return toOpaqueColor(0xFFE0B240); }
    int getTaskTextColorForTest() { return toOpaqueColor(0xFFFFFF); }
    String getHiddenCountTextForTest() { return buildExpandedFooterSummaryText(cachedPendingSummaryCount, cachedDoneSummaryCount).getString(); }
    String getCollapsedSummaryTextForTest() { return buildCollapsedSummaryText(cachedPendingSummaryCount, cachedDoneSummaryCount).getString(); }

    private String valueOrEmpty(String value) { return value == null ? "" : value; }

    private HudRenderPlan buildRenderPlan(ModConfig config, List<Task> pending, List<Task> done, int pendingTotal, int doneTotal) {
        int rowHeight = 12;
        int headerHeight = 14;
        int maxRowsByHeight = Math.max(0, (config.getHudMaxHeight() - headerHeight) / rowHeight);
        int todoLimit = Math.max(0, config.getHudTodoLimit());
        int doneLimit = Math.max(0, config.getHudDoneLimit());
        if (!expanded) {
            int hiddenCount = Math.max(0, pendingTotal) + Math.max(0, doneTotal);
            return new HudRenderPlan(0, 0, hiddenCount, false, false);
        }

        HudRenderPlan visiblePlan = buildVisibleRowsPlan(maxRowsByHeight, todoLimit, doneLimit, Math.max(0, pendingTotal), Math.max(0, doneTotal));
        if (visiblePlan.hiddenCount <= 0 || maxRowsByHeight <= 0) {
            return clampRenderPlanToLoadedRows(visiblePlan, pending, done);
        }

        HudRenderPlan reservedPlan = buildVisibleRowsPlan(Math.max(0, maxRowsByHeight - 1), todoLimit, doneLimit,
                Math.max(0, pendingTotal), Math.max(0, doneTotal));
        return clampRenderPlanToLoadedRows(new HudRenderPlan(reservedPlan.shownPending, reservedPlan.shownDone, reservedPlan.hiddenCount,
                reservedPlan.showDoneSection, true), pending, done);
    }

    private HudRenderPlan clampRenderPlanToLoadedRows(HudRenderPlan plan, List<Task> pending, List<Task> done) {
        int pendingRows = pending == null ? 0 : pending.size();
        int doneRows = done == null ? 0 : done.size();
        int shownPending = Math.min(plan.shownPending, pendingRows);
        int shownDone = Math.min(plan.shownDone, doneRows);
        return new HudRenderPlan(shownPending, shownDone, plan.hiddenCount, shownDone > 0 && plan.showDoneSection, plan.showMore);
    }

    private HudRenderPlan buildVisibleRowsPlan(int availableRows, int todoLimit, int doneLimit, int pendingCount, int doneCount) {
        int safeRows = Math.max(0, availableRows);
        int shownPending = Math.min(pendingCount, Math.min(todoLimit, safeRows));
        int rowsAfterPending = Math.max(0, safeRows - shownPending);
        int shownDone = 0;
        if (doneCount > 0 && doneLimit > 0 && rowsAfterPending > 1) {
            shownDone = Math.min(doneCount, Math.min(doneLimit, rowsAfterPending - 1));
        }
        boolean showDoneSection = shownDone > 0;
        int hiddenCount = Math.max(0, pendingCount - shownPending) + Math.max(0, doneCount - shownDone);
        return new HudRenderPlan(shownPending, shownDone, hiddenCount, showDoneSection, false);
    }

    private Component buildCollapsedSummaryText(int pendingCount, int doneCount) {
        String summaryKey = doneCount <= 0 ? "hud.todolist.summary" : "hud.todolist.summary.with_completed";
        return doneCount <= 0
                ? Component.translatableWithFallback(summaryKey, "Todo: %s", Integer.toString(pendingCount))
                : Component.translatableWithFallback(summaryKey, "Todo: %s | Done: %s",
                Integer.toString(pendingCount), Integer.toString(doneCount));
    }

    private Component buildExpandedFooterSummaryText(int pendingCount, int doneCount) {
        return Component.translatableWithFallback(
                "hud.todolist.summary.fixed",
                "Todo %s | Done %s",
                Integer.toString(Math.max(0, pendingCount)),
                Integer.toString(Math.max(0, doneCount))
        );
    }

    private int countTopLevelTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) return 0;
        int count = 0;
        for (Task task : tasks) {
            if (task != null && !task.isSubtask()) count++;
        }
        return count;
    }

    public void toggleExpanded() {
        this.expanded = !this.expanded;
    }

    public boolean isExpanded() {
        return expanded;
    }
}