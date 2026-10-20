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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Renders the Satisfactory-inspired FICSIT To-Do List overlay on the HUD.
 */
public class TodoHudRenderer {
    private static final long HUD_MODEL_REFRESH_INTERVAL_MS = 1000L;

    // Authentic Satisfactory FICSIT HUD Palette
    private static final int COL_FICSIT_HEADER_BG = 0xF225272B;
    private static final int COL_FICSIT_BODY_BG   = 0xD9141619;
    private static final int COL_FICSIT_SUBHEADER = 0xE62E3137;
    private static final int COL_FICSIT_ORANGE    = 0xFFE59344;
    private static final int COL_FICSIT_CYAN      = 0xFF4FC3F7;
    private static final int COL_FICSIT_GREEN     = 0xFF66BB6A;
    private static final int COL_FICSIT_BORDER    = 0xFF32353C;

    private enum HudViewMode {
        PERSONAL,
        TEAM_UNASSIGNED,
        TEAM_ALL,
        TEAM_ASSIGNED
    }

    /**
     * Parsed item requirement from a task tag (e.g. "minecraft:iron_ingot:64" or "create:cogwheel*16").
     */
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

    private static final Component SEPARATOR_COMPLETED = Component.translatable("hud.todolist.separator.completed");
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
     * Parses an optional item requirement from a Task's tags (e.g., "minecraft:iron_ingot:64", "iron_ingot*32", "create:cogwheel:16").
     */
    public static ParsedItemRequirement parseTaskItemRequirement(Task task) {
        if (task == null || task.getTags() == null) return null;
        for (String rawTag : task.getTags()) {
            if (rawTag == null || rawTag.isBlank()) continue;
            String tag = rawTag.trim().toLowerCase();
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
            try {
                ResourceLocation id = ResourceLocation.parse(itemPart);
                if (BuiltInRegistries.ITEM.containsKey(id)) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    if (item != Items.AIR) {
                        return new ParsedItemRequirement(item, new ItemStack(item), qty);
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
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
        if (client.options.hideGui) return;

        ModConfig config = ModConfig.getInstance();
        if (!config.isEnableHud() || !ClientBridge.ops().isHudVisible()) return;

        HudViewMode viewMode = resolveViewMode(config);
        Project.Scope scope = getScopeByView(viewMode);
        refreshHudModelIfNeeded(config, viewMode, scope);

        if (cachedPendingTasks.isEmpty() && cachedDoneTasks.isEmpty() && !config.isHudShowWhenEmpty()) return;

        List<Task> pending = cachedPendingTasks;
        List<Task> done = cachedDoneTasks;
        HudRenderPlan renderPlan = buildRenderPlan(config, pending, done, cachedPendingTotalCount, cachedDoneTotalCount);
        int panelHeight = calculatePanelHeight(config, pending, done);

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int hudWidth = config.getHudWidth();

        ModConfig.HudPlacement placement = config.resolveHudPlacement(screenWidth, screenHeight, hudWidth, panelHeight);
        int x = placement.getX();
        int y = placement.getY();

        renderTaskList(context, x, y, hudWidth, panelHeight, pending, done, renderPlan, config, viewMode);
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
        if (!shouldUseSynchronousH2HudQueries()) {
            return null;
        }
        try {
            H2TaskQueryService.HudTaskQuery query = buildHudTaskQuery(config, viewMode, scope);
            if (query == null) return null;
            return new H2TaskQueryService().queryHudTasks(query);
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean shouldUseSynchronousH2HudQueries() {
        return false;
    }

    private H2TaskQueryService.HudTaskQuery buildHudTaskQuery(ModConfig config, HudViewMode viewMode, Project.Scope scope) {
        String bucketType = scope == Project.Scope.TEAM ? H2TaskStore.TEAM_BUCKET : H2TaskStore.LOCAL_PERSONAL_BUCKET;
        String ownerUuid = scope == Project.Scope.TEAM ? H2TaskStore.TEAM_OWNER : H2TaskStore.LOCAL_OWNER;
        if (scope == Project.Scope.PERSONAL && client != null && client.player != null && ClientTaskStorageHelper.shouldUsePublishedLocalPlayerStorage(client)) {
            var storagePlayerUuid = ClientTaskStorageHelper.resolveStoragePlayerUuid(client);
            bucketType = H2TaskStore.PLAYER_PERSONAL_BUCKET;
            ownerUuid = storagePlayerUuid == null ? "" : storagePlayerUuid.toString();
        }

        HudSqlProjectFilter projectFilter = resolveHudSqlProjectFilter(scope, config);
        H2TaskQueryService.HudAssigneeFilter assigneeFilter = resolveHudAssigneeFilter(viewMode);
        String playerUuid = client.player == null ? "" : client.player.getStringUUID();
        int maxRowsByHeight = Math.max(0, (config.getHudMaxHeight() - 14) / 12);
        int pendingLimit = Math.min(Math.max(0, config.getHudTodoLimit()), maxRowsByHeight);
        int doneLimit = Math.min(Math.max(0, config.getHudDoneLimit()), maxRowsByHeight);
        return new H2TaskQueryService.HudTaskQuery(
                bucketType, ownerUuid, projectFilter.projectIds, projectFilter.includeAnyAssignedProject,
                assigneeFilter, playerUuid, pendingLimit, doneLimit
        );
    }

    private HudSqlProjectFilter resolveHudSqlProjectFilter(Project.Scope scope, ModConfig config) {
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
        } else {
            collectProjectIds(scopedProjects, allowedProjectIds);
        }
        return new HudSqlProjectFilter(allowedProjectIds, scope == Project.Scope.TEAM && allowedProjectIds.isEmpty());
    }

    private H2TaskQueryService.HudAssigneeFilter resolveHudAssigneeFilter(HudViewMode viewMode) {
        if (viewMode == HudViewMode.TEAM_UNASSIGNED) return H2TaskQueryService.HudAssigneeFilter.UNASSIGNED;
        if (viewMode == HudViewMode.TEAM_ASSIGNED) return H2TaskQueryService.HudAssigneeFilter.ASSIGNED_TO_PLAYER;
        return H2TaskQueryService.HudAssigneeFilter.ANY;
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
            builder.append(" // ").append(projectName);
        }

        Component teamViewLabel = getTeamViewLabel(viewMode);
        if (teamViewLabel != null) {
            builder.append(" [").append(teamViewLabel.getString()).append(']');
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

    /**
     * Renders the Satisfactory FICSIT To-Do List panel on the HUD.
     */
    private void renderTaskList(GuiGraphics context, int x, int y, int width, int panelHeight, List<Task> pending, List<Task> done,
                                HudRenderPlan renderPlan, ModConfig config, HudViewMode viewMode) {
        int currentY = y;
        float opacity = (float) config.getHudOpacity();
        int bodyBgColor = applyBackgroundOpacity(COL_FICSIT_BODY_BG, opacity);
        int headerBgColor = applyBackgroundOpacity(COL_FICSIT_HEADER_BG, Math.min(1.0f, opacity + 0.15f));
        int rowHeight = 12;
        int headerHeight = 14;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 1. Main FICSIT Translucent Panel Body + Left Orange Stripe
        context.fill(x, y, x + width, y + panelHeight, bodyBgColor);
        context.renderOutline(x, y, width, panelHeight, applyBackgroundOpacity(COL_FICSIT_BORDER, opacity));

        // 2. Top Gunmetal Header Bar + 2px FICSIT Orange Bottom Accent
        context.fill(x, y, x + width, y + headerHeight, headerBgColor);
        context.fill(x, y + headerHeight - 2, x + width, y + headerHeight, toOpaqueColor(COL_FICSIT_ORANGE));

        Component title = buildHeaderTitle(viewMode);
        String rawTitle = trimWithEllipsis("❖ " + title.getString(), width - 8);
        int headerTextY = y + (headerHeight - client.font.lineHeight) / 2;
        context.drawString(client.font, rawTitle, x + 5, headerTextY, toOpaqueColor(COL_FICSIT_ORANGE), false);
        currentY += headerHeight;

        // Collapsed state
        if (!expanded) {
            Component summary = buildCollapsedSummaryText(cachedPendingSummaryCount, cachedDoneSummaryCount);
            int summaryY = currentY + (rowHeight - client.font.lineHeight) / 2;
            context.drawString(client.font, summary, x + 5, summaryY, toOpaqueColor(0xFFE0E4EC), false);
            return;
        }

        // 3. Pending Tasks Section
        for (int i = 0; i < renderPlan.shownPending; i++) {
            if (i % 2 == 1) {
                context.fill(x + 1, currentY, x + width - 1, currentY + rowHeight, applyBackgroundOpacity(0x28FFFFFF, opacity * 0.25f));
            }
            drawTaskRow(context, x, currentY, width, rowHeight, pending.get(i));
            currentY += rowHeight;
        }

        // 4. Completed Tasks Section
        if (renderPlan.showDoneSection) {
            context.fill(x + 1, currentY, x + width - 1, currentY + rowHeight, applyBackgroundOpacity(COL_FICSIT_SUBHEADER, opacity));
            context.fill(x + 1, currentY, x + 3, currentY + rowHeight, toOpaqueColor(COL_FICSIT_GREEN));
            int separatorY = currentY + (rowHeight - client.font.lineHeight) / 2;
            context.drawString(client.font, SEPARATOR_COMPLETED, x + 6, separatorY, toOpaqueColor(0xFFB0B5BC), false);
            currentY += rowHeight;
            for (int i = 0; i < renderPlan.shownDone; i++) {
                drawTaskRow(context, x, currentY, width, rowHeight, done.get(i));
                currentY += rowHeight;
            }
        }

        // 5. Footer Summary Bar
        if (renderPlan.showMore) {
            context.fill(x + 1, currentY, x + width - 1, currentY + rowHeight, applyBackgroundOpacity(0xFF1E2024, opacity));
            int moreY = currentY + (rowHeight - client.font.lineHeight) / 2;
            context.drawString(
                    client.font,
                    buildExpandedFooterSummaryText(cachedPendingSummaryCount, cachedDoneSummaryCount),
                    x + 5,
                    moreY,
                    toOpaqueColor(COL_FICSIT_ORANGE),
                    false
            );
        }
    }

    /**
     * Draws an individual task row on the HUD, including 3D Item Icon + Live Inventory Count if the task has an item tag.
     */
    private void drawTaskRow(GuiGraphics context, int x, int y, int width, int rowHeight, Task task) {
        RowRenderCache rowCache = rowRenderCacheByTaskId.get(task.getId());
        if (!isRowRenderCacheValid(rowCache, task, width)) {
            rowCache = buildRowRenderCache(task, width);
            rowRenderCacheByTaskId.put(rowCache.taskId, rowCache);
        }

        int rowLeft = x + 4;
        int titleTextY = y + (rowHeight - client.font.lineHeight) / 2;
        int labelLineHeight = Math.max(1, Math.round(client.font.lineHeight * HUD_LABEL_SCALE));
        int labelTextY = y + (rowHeight - labelLineHeight) / 2;
        int textColor = toOpaqueColor(0xFFFFFFFF);
        int labelColor = toOpaqueColor(COL_FICSIT_CYAN);

        HudRowVisual rowVisual = rowCache.rowVisual;
        if (rowVisual.prefixText != null) {
            context.drawString(client.font, rowVisual.prefixText, rowLeft + rowVisual.prefixOffset, titleTextY, textColor, false);
        }

        // Priority Indicator Bar
        context.fill(rowLeft + rowVisual.priorityBlockOffset, y + 2,
                rowLeft + rowVisual.priorityBlockOffset + HUD_PRIORITY_BLOCK_WIDTH, y + rowHeight - 2,
                toOpaqueColor(rowVisual.priorityBlockColor));

        // Check if this task has an Item Requirement tag (e.g. "minecraft:iron_ingot:64")
        ParsedItemRequirement itemReq = parseTaskItemRequirement(task);
        if (itemReq != null && client.player != null) {
            int have = countItemInPlayerInventory(client.player, itemReq.item());
            boolean enough = have >= itemReq.neededCount() || isTaskCompletedInHud(task);

            // Render scaled 10x10 3D Item Icon
            int iconX = rowLeft + rowVisual.priorityBlockOffset + HUD_PRIORITY_BLOCK_WIDTH + 3;
            context.pose().pushPose();
            context.pose().translate(iconX, y + 1, 50);
            context.pose().scale(0.625f, 0.625f, 1.0f);
            context.renderItem(itemReq.stack(), 0, 0);
            context.pose().popPose();

            // Render Live Have/Need Count on the right edge
            String countStr = have + "/" + itemReq.neededCount();
            int countW = client.font.width(countStr);
            int countX = x + width - countW - 4;
            context.drawString(client.font, countStr, countX, titleTextY,
                    toOpaqueColor(enough ? COL_FICSIT_GREEN : COL_FICSIT_ORANGE), false);

            // Render Item/Task Title in between
            int titleStartX = iconX + 12;
            int availTitleW = Math.max(16, countX - titleStartX - 4);
            String rawTitle = task.getTitle() == null || task.getTitle().isBlank()
                    ? itemReq.stack().getHoverName().getString()
                    : task.getTitle();
            String clippedTitle = trimWithEllipsis(rawTitle, availTitleW);
            Component styledTitle = enough
                    ? Component.literal(clippedTitle).withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH)
                    : Component.literal(clippedTitle).withStyle(ChatFormatting.WHITE);
            context.drawString(client.font, styledTitle, titleStartX, titleTextY, textColor, false);
            return;
        }

        if (rowVisual.tagText != null && !rowVisual.tagText.isEmpty()) {
            drawScaledString(context, rowVisual.tagText, rowLeft + rowVisual.tagOffset, labelTextY, labelColor, HUD_LABEL_SCALE);
        }
        if (rowVisual.titleText != null) {
            context.drawString(client.font, rowVisual.titleText, rowLeft + rowVisual.titleOffset, titleTextY, textColor, false);
        }
        if (rowVisual.progressText != null) {
            context.drawString(client.font, rowVisual.progressText, rowLeft + rowVisual.progressOffset, titleTextY, toOpaqueColor(COL_FICSIT_ORANGE), false);
        }
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

    private boolean isRowRenderCacheValid(RowRenderCache cache, Task task, int hudWidth) {
        if (cache == null) return false;
        double guiScale = client.getWindow().getGuiScale();
        if (cache.hudWidth != hudWidth || Double.compare(cache.guiScale, guiScale) != 0) {
            return false;
        }
        return cache.layoutKey.equals(buildRowLayoutKey(task, hudWidth, guiScale));
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

    private void drawScaledString(GuiGraphics context, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        context.pose().pushPose();
        context.pose().translate(x, y, 0.0F);
        context.pose().scale(scale, scale, 1.0F);
        context.drawString(client.font, text, 0, 0, color, false);
        context.pose().popPose();
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
        HudViewMode viewMode = resolveViewMode(config);
        Project.Scope scope = getScopeByView(viewMode);
        refreshHudModelIfNeeded(config, viewMode, scope);
        return calculatePanelHeight(config, cachedPendingTasks, cachedDoneTasks, cachedPendingTotalCount, cachedDoneTotalCount);
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

    private int calculatePanelHeight(ModConfig config, List<Task> pending, List<Task> done) {
        return calculatePanelHeight(config, pending, done, pending == null ? 0 : pending.size(), done == null ? 0 : done.size());
    }

    private int calculatePanelHeight(ModConfig config, List<Task> pending, List<Task> done, int pendingTotal, int doneTotal) {
        int rowHeight = 12;
        int headerHeight = 14;
        HudRenderPlan renderPlan = buildRenderPlan(config, pending, done, pendingTotal, doneTotal);
        int rowsForSummary = expanded ? renderPlan.shownPending + (renderPlan.showDoneSection ? 1 + renderPlan.shownDone : 0) : 1;
        int totalRows = rowsForSummary + (renderPlan.showMore ? 1 : 0);
        return headerHeight + totalRows * rowHeight;
    }

    public void toggleExpanded() {
        this.expanded = !this.expanded;
    }

    public boolean isExpanded() {
        return expanded;
    }
}