package com.todolist.gui;

import com.todolist.client.ClientBridge;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.TodoListCommon;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * FICSIT-styled Project Settings & Team Member Management Screen.
 */
public class ProjectSettingsScreen extends Screen implements ProjectManager.ProjectChangeListener {

    private static final int COL_HEADER_BG     = 0xF225272B;
    private static final int COL_BODY_BG       = 0xEB181A1D;
    private static final int COL_WELL_BG       = 0xFF121316;
    private static final int COL_FICSIT_ORANGE = 0xFFE59344;
    private static final int COL_FICSIT_CYAN   = 0xFF4FC3F7;
    private static final int TEXT_WHITE        = 0xFFFFFFFF;
    private static final int TEXT_GRAY         = 0xFFB0B5BC;
    private static final int TEXT_DARK         = 0xFF181A1D;

    private static final class SettingsLayout {
        private final int dialogX;
        private final int dialogY;
        private final int dialogWidth;
        private final int dialogHeight;
        private final int scopeBadgeX;
        private final int scopeBadgeY;
        private final int scopeBadgeWidth;
        private final int scopeBadgeHeight;
        private final int memberListX;
        private final int memberListY;
        private final int memberListWidth;
        private final int memberListHeight;

        private SettingsLayout(int dialogX, int dialogY, int dialogWidth, int dialogHeight,
                               int scopeBadgeX, int scopeBadgeY, int scopeBadgeWidth, int scopeBadgeHeight,
                               int memberListX, int memberListY, int memberListWidth, int memberListHeight) {
            this.dialogX = dialogX;
            this.dialogY = dialogY;
            this.dialogWidth = dialogWidth;
            this.dialogHeight = dialogHeight;
            this.scopeBadgeX = scopeBadgeX;
            this.scopeBadgeY = scopeBadgeY;
            this.scopeBadgeWidth = scopeBadgeWidth;
            this.scopeBadgeHeight = scopeBadgeHeight;
            this.memberListX = memberListX;
            this.memberListY = memberListY;
            this.memberListWidth = memberListWidth;
            this.memberListHeight = memberListHeight;
        }
    }

    private final Screen parent;
    private Project project;
    private EditBox nameField;
    private EditBox memberSearchField;
    private boolean canEdit;
    private MemberListWidget memberList;
    private Button addMemberBtn;
    private Button allowMemberCreateBtn;
    private Button allowAllPlayersClaimCompleteBtn;
    private Button saveButton;
    private boolean allowMemberCreate;
    private boolean allowAllPlayersClaimComplete;
    private SettingsLayout layout;

    public ProjectSettingsScreen(Screen parent, Project project) {
        super(Component.translatable("gui.todolist.project_settings.title"));
        this.parent = parent;
        this.project = project;
    }

    Project getProjectForTest() { return project; }
    boolean canEditForTest() { return canEdit; }
    EditBox getMemberSearchFieldForTest() { return memberSearchField; }
    Button getAddMemberButtonForTest() { return addMemberBtn; }
    EditBox getNameFieldForTest() { return nameField; }
    Button getAllowMemberCreateButtonForTest() { return allowMemberCreateBtn; }
    Button getAllowAllPlayersClaimCompleteButtonForTest() { return allowAllPlayersClaimCompleteBtn; }
    Button getSaveButtonForTest() { return saveButton; }

    List<String> getVisibleMemberNamesForTest() {
        return memberList == null ? List.of() : memberList.getVisibleMemberNamesForTest();
    }

    int[] getDialogBoundsForTest() {
        return layout == null ? new int[] {0, 0, 0, 0} : new int[] {layout.dialogX, layout.dialogY, layout.dialogWidth, layout.dialogHeight};
    }

    int[] getScopeBadgeBoundsForTest() {
        return layout == null ? new int[] {0, 0, 0, 0} : new int[] {layout.scopeBadgeX, layout.scopeBadgeY, layout.scopeBadgeWidth, layout.scopeBadgeHeight};
    }

    String getScopeBadgeTextForTest() {
        return project.getScope() == Project.Scope.TEAM ? "gui.todolist.scope.team" : "gui.todolist.scope.personal";
    }

    String getAllowMemberCreateStateKeyForTest() {
        return allowMemberCreate ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
    }

    String getAllowAllPlayersClaimCompleteStateKeyForTest() {
        return allowAllPlayersClaimComplete ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
    }

    int[] getMemberListBoundsForTest() {
        return layout == null ? new int[] {0, 0, 0, 0} : new int[] {layout.memberListX, layout.memberListY, layout.memberListWidth, layout.memberListHeight};
    }

    int[] getMemberRoleButtonBoundsForTest(int rowIndex) {
        MemberListWidget.MemberEntry entry = getMemberEntryForTest(rowIndex);
        if (entry == null || memberList == null) return new int[] {0, 0, 0, 0};
        entry.syncButtonLayoutForTest(rowIndex, memberList);
        return entry.getRoleButtonBoundsForTest();
    }

    int[] getMemberRemoveButtonBoundsForTest(int rowIndex) {
        MemberListWidget.MemberEntry entry = getMemberEntryForTest(rowIndex);
        if (entry == null || memberList == null) return new int[] {0, 0, 0, 0};
        entry.syncButtonLayoutForTest(rowIndex, memberList);
        return entry.getRemoveButtonBoundsForTest();
    }

    void clickMemberRoleButtonForTest(int rowIndex) {
        MemberListWidget.MemberEntry entry = getMemberEntryForTest(rowIndex);
        if (entry != null) entry.clickRoleButtonForTest();
    }

    void clickMemberRemoveButtonForTest(int rowIndex) {
        MemberListWidget.MemberEntry entry = getMemberEntryForTest(rowIndex);
        if (entry != null) entry.clickRemoveButtonForTest();
    }

    @Override
    protected void init() {
        TodoListCommon.getProjectManager().addListener(this);
        canEdit = checkPermission();
        boolean isTeam = project.getScope() == Project.Scope.TEAM;
        allowMemberCreate = project.isAllowMemberCreate();
        allowAllPlayersClaimComplete = project.isAllowAllPlayersClaimComplete();
        layout = computeResponsiveLayout(isTeam);

        int headerRightWidth = isTeam ? 118 : 88;
        int contentLeft = layout.dialogX + 10;
        int contentRight = layout.dialogX + layout.dialogWidth - 10;
        int nameFieldWidth = Math.max(90, layout.dialogWidth - headerRightWidth - 30);

        nameField = new EditBox(font, contentLeft, layout.dialogY + 35, nameFieldWidth, 20, Component.translatable("gui.todolist.project.name"));
        nameField.setValue(ProjectNameFormatter.toDisplayText(project).getString());
        nameField.setMaxLength(32);
        nameField.setEditable(canEdit);
        addRenderableWidget(nameField);

        if (isTeam) {
            allowMemberCreateBtn = Button.builder(getAllowMemberCreateText(), button -> {
                allowMemberCreate = !allowMemberCreate;
                button.setMessage(getAllowMemberCreateText());
            }).bounds(contentRight - 108, layout.dialogY + 34, 108, 18).build();
            allowMemberCreateBtn.active = canEdit;
            addRenderableWidget(allowMemberCreateBtn);

            allowAllPlayersClaimCompleteBtn = Button.builder(getAllowAllPlayersClaimCompleteText(), button -> {
                allowAllPlayersClaimComplete = !allowAllPlayersClaimComplete;
                button.setMessage(getAllowAllPlayersClaimCompleteText());
            }).bounds(contentRight - 108, layout.dialogY + 54, 108, 18).build();
            allowAllPlayersClaimCompleteBtn.active = canEdit;
            addRenderableWidget(allowAllPlayersClaimCompleteBtn);

            memberSearchField = new EditBox(font, contentLeft, layout.dialogY + 95, layout.dialogWidth - 20, 16, Component.empty());
            memberSearchField.setHint(Component.translatable("gui.todolist.member.search"));
            memberSearchField.setResponder(text -> memberList.updateEntries(text));
            addRenderableWidget(memberSearchField);

            int listTop = layout.memberListY;
            memberList = new MemberListWidget(minecraft, layout.memberListWidth, layout.memberListHeight, listTop, 20);
            memberList.setX(layout.memberListX);
            addRenderableWidget(memberList);

            addMemberBtn = Button.builder(Component.translatable("gui.todolist.add_member"), button -> {
                minecraft.setScreen(new AddMemberScreen(this, project.getId()));
            }).bounds(contentRight - 70, layout.dialogY + 73, 60, 16).build();
            addMemberBtn.active = checkAdminPermission();
            addRenderableWidget(addMemberBtn);
        }

        saveButton = Button.builder(Component.translatable("gui.todolist.save"), button -> saveProject())
                .bounds(layout.dialogX + 80, layout.dialogY + layout.dialogHeight - 28, 60, 20).build();
        saveButton.active = canEdit;
        addRenderableWidget(saveButton);

        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.cancel"), button -> onClose())
                .bounds(layout.dialogX + layout.dialogWidth - 70, layout.dialogY + layout.dialogHeight - 28, 60, 20).build());

        if (canEdit) {
            setFocused(nameField);
        }
    }

    private SettingsLayout computeResponsiveLayout(boolean isTeam) {
        int dialogWidth = Math.max(220, Math.min(360, width - 20));
        int dialogHeight = isTeam ? Math.max(220, Math.min(320, height - 20)) : Math.max(150, Math.min(220, height - 20));
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - dialogHeight) / 2;
        int scopeBadgeWidth = 72;
        int scopeBadgeHeight = 14;
        int scopeBadgeX = dialogX + dialogWidth - 10 - scopeBadgeWidth;
        int scopeBadgeY = dialogY + 4;
        int memberListX = dialogX + 10;
        int memberListY = dialogY + 115;
        int memberListWidth = dialogWidth - 20;
        int memberListHeight = Math.max(40, dialogHeight - 155);
        return new SettingsLayout(dialogX, dialogY, dialogWidth, dialogHeight,
                scopeBadgeX, scopeBadgeY, scopeBadgeWidth, scopeBadgeHeight,
                memberListX, memberListY, memberListWidth, memberListHeight);
    }

    private void renderScopeBadge(GuiGraphics context) {
        if (layout == null) return;
        boolean isFtbTeam = project.getId() != null && project.getId().startsWith("ftbteam_");
        Component badgeText = isFtbTeam
                ? Component.literal("FTB TEAM")
                : (project.getScope() == Project.Scope.TEAM
                ? Component.translatable("gui.todolist.scope.team")
                : Component.translatable("gui.todolist.scope.personal"));
        int pillColor = isFtbTeam ? COL_FICSIT_ORANGE : (project.getScope() == Project.Scope.TEAM ? COL_FICSIT_CYAN : 0xFF3E4147);
        int txtColor = (isFtbTeam || project.getScope() == Project.Scope.TEAM) ? TEXT_DARK : TEXT_WHITE;

        context.fill(layout.scopeBadgeX, layout.scopeBadgeY,
                layout.scopeBadgeX + layout.scopeBadgeWidth, layout.scopeBadgeY + layout.scopeBadgeHeight, pillColor);
        context.renderOutline(layout.scopeBadgeX, layout.scopeBadgeY, layout.scopeBadgeWidth, layout.scopeBadgeHeight, 0xFF141518);
        context.drawCenteredString(font, badgeText,
                layout.scopeBadgeX + layout.scopeBadgeWidth / 2,
                layout.scopeBadgeY + 3, txtColor);
    }

    private MemberListWidget.MemberEntry getMemberEntryForTest(int rowIndex) {
        if (memberList == null || rowIndex < 0 || rowIndex >= memberList.children().size()) return null;
        return memberList.children().get(rowIndex);
    }

    public void optimisticAddMember(String memberUuid, String memberName) {
        if (memberUuid == null || memberUuid.isEmpty() || project.getScope() != Project.Scope.TEAM) return;
        if (project.getMembers().containsKey(memberUuid)) return;
        project.addMember(memberUuid, Project.ProjectRole.MEMBER, memberName);
        if (memberList != null) {
            String search = memberSearchField != null ? memberSearchField.getValue() : "";
            memberList.updateEntries(search);
        }
    }

    private boolean checkPermission() {
        if (minecraft.player == null) return false;
        String uuid = minecraft.player.getUUID().toString();
        if (project.getScope() == Project.Scope.PERSONAL) {
            String owner = project.getOwnerUuid();
            return owner == null || uuid.equals(owner);
        }
        Role role = getCurrentRole();
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        return PermissionCenter.canPerform(Operation.EDIT_PROJECT, role, ctx);
    }

    private boolean checkAdminPermission() {
        Role role = getCurrentRole();
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        return PermissionCenter.canPerform(Operation.ADD_MEMBER, role, ctx);
    }

    private boolean isOpClient() {
        return minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    private Role getCurrentRole() {
        if (isOpClient()) return Role.OP;
        if (minecraft == null || minecraft.player == null) return Role.MEMBER;
        String uuid = minecraft.player.getUUID().toString();
        if (uuid.equals(project.getOwnerUuid())) return Role.PROJECT_MANAGER;
        Project.ProjectRole r = project.getMemberRole(uuid);
        return r == Project.ProjectRole.LEAD ? Role.LEAD : Role.MEMBER;
    }

    private void saveProject() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) return;

        String normalizedName = normalizeProjectNameForSave(name, project.getName());
        project.setName(normalizedName);
        project.setAllowMemberCreate(allowMemberCreate);
        project.setAllowAllPlayersClaimComplete(allowAllPlayersClaimComplete);
        ClientBridge.ops().sendUpdateProject(project);
        onClose();
    }

    private Component getAllowMemberCreateText() {
        return Component.translatable(
                "gui.todolist.project.allow_member_create",
                Component.translatable(allowMemberCreate ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off")
        );
    }

    private Component getAllowAllPlayersClaimCompleteText() {
        return Component.translatable(
                "gui.todolist.project.allow_all_players_claim_complete",
                Component.translatable(allowAllPlayersClaimComplete ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off")
        );
    }

    private String normalizeProjectNameForSave(String editedName, String originalName) {
        if (!ProjectNameFormatter.isTranslationKey(originalName)) return editedName;
        String originalDisplay = ProjectNameFormatter.toDisplayText(originalName).getString();
        return editedName.equals(originalDisplay) ? originalName : editedName;
    }

    @Override
    public void onClose() {
        TodoListCommon.getProjectManager().removeListener(this);
        minecraft.setScreen(parent);
    }

    @Override
    public void onProjectChanged(ProjectManager.ProjectChangeType type, Project project) {
        if (project != null && project.getId().equals(this.project.getId())) {
            this.project = project;
            if (minecraft != null) {
                minecraft.execute(() -> {
                    if (memberList != null) {
                        String search = memberSearchField != null ? memberSearchField.getValue() : "";
                        memberList.updateEntries(search);
                    }
                });
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x78090A0C);
        boolean isTeam = project.getScope() == Project.Scope.TEAM;
        layout = computeResponsiveLayout(isTeam);

        // 1. FICSIT Header Bar + Orange Accent Line
        context.fill(layout.dialogX, layout.dialogY, layout.dialogX + layout.dialogWidth, layout.dialogY + 22, COL_HEADER_BG);
        context.fill(layout.dialogX, layout.dialogY + 20, layout.dialogX + layout.dialogWidth, layout.dialogY + 22, COL_FICSIT_ORANGE);
        context.drawString(font, "⚙", layout.dialogX + 8, layout.dialogY + 7, COL_FICSIT_ORANGE, false);
        context.drawString(font, title, layout.dialogX + 20, layout.dialogY + 7, TEXT_WHITE, false);
        renderScopeBadge(context);

        // 2. Main Modal Body
        context.fill(layout.dialogX, layout.dialogY + 22, layout.dialogX + layout.dialogWidth, layout.dialogY + layout.dialogHeight, COL_BODY_BG);
        context.drawString(font, Component.translatable("gui.todolist.label.name"), layout.dialogX + 10, layout.dialogY + 25, TEXT_GRAY, false);

        if (isTeam) {
            context.fill(layout.memberListX - 2, layout.memberListY - 2,
                    layout.memberListX + layout.memberListWidth + 2, layout.memberListY + layout.memberListHeight + 2, COL_WELL_BG);
            context.renderOutline(layout.memberListX - 2, layout.memberListY - 2,
                    layout.memberListWidth + 4, layout.memberListHeight + 4, 0xFF282B30);
            context.drawString(font, Component.translatable("gui.todolist.label.members"), layout.dialogX + 10, layout.dialogY + 76, COL_FICSIT_ORANGE, false);
        }

        context.renderOutline(layout.dialogX, layout.dialogY, layout.dialogWidth, layout.dialogHeight, 0xFF141518);
        super.render(context, mouseX, mouseY, delta);
    }

    private class MemberListWidget extends ContainerObjectSelectionList<MemberListWidget.MemberEntry> {
        public MemberListWidget(Minecraft client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
            updateEntries("");
        }

        public void updateEntries(String search) {
            this.clearEntries();
            String q = search.toLowerCase().trim();

            String ownerUuid = project.getOwnerUuid();
            if (ownerUuid != null && !ownerUuid.isEmpty()) {
                Project.ProjectRole ownerRole = project.getMemberRole(ownerUuid);
                if (ownerRole == null) ownerRole = Project.ProjectRole.PROJECT_MANAGER;
                String name = project.getMemberName(ownerUuid);
                if (name == null || name.isEmpty()) name = ownerUuid;
                try {
                    UUID id = UUID.fromString(ownerUuid);
                    if (minecraft.getConnection() != null) {
                        PlayerInfo ple = minecraft.getConnection().getPlayerInfo(id);
                        if (ple != null) {
                            name = ple.getProfile().getName();
                            project.setMemberName(ownerUuid, name);
                        }
                    }
                } catch (Exception ignored) {}
                if (q.isEmpty() || name.toLowerCase().contains(q)) {
                    this.addEntry(new MemberEntry(ownerUuid, ownerRole));
                }
            }

            for (Map.Entry<String, Project.ProjectRole> entry : project.getMembers().entrySet()) {
                String uuid = entry.getKey();
                if (ownerUuid != null && ownerUuid.equals(uuid)) continue;

                Project.ProjectRole role = entry.getValue();
                String name = project.getMemberName(uuid);
                if (name == null || name.isEmpty()) name = uuid;
                try {
                    UUID id = UUID.fromString(uuid);
                    if (minecraft.getConnection() != null) {
                        PlayerInfo ple = minecraft.getConnection().getPlayerInfo(id);
                        if (ple != null) {
                            name = ple.getProfile().getName();
                            project.setMemberName(uuid, name);
                        }
                    }
                } catch (Exception ignored) {}

                if (q.isEmpty() || name.toLowerCase().contains(q)) {
                    this.addEntry(new MemberEntry(uuid, role));
                }
            }
        }

        public List<String> getVisibleMemberNamesForTest() {
            return this.children().stream().map(MemberEntry::getNameForTest).toList();
        }

        @Override
        public int getRowWidth() { return this.getWidth() - 10; }

        @Override
        protected int getScrollbarPosition() { return this.getX() + this.getWidth() + 6; }

        public class MemberEntry extends ContainerObjectSelectionList.Entry<MemberEntry> {
            private final String uuid;
            private final Project.ProjectRole role;
            private final Button roleBtn;
            private final Button removeBtn;
            private String name;

            public MemberEntry(String uuid, Project.ProjectRole role) {
                this.uuid = uuid;
                this.role = role;

                this.name = project.getMemberName(uuid);
                if (this.name == null || this.name.isEmpty()) this.name = uuid;
                try {
                    UUID id = UUID.fromString(uuid);
                    if (minecraft.getConnection() != null) {
                        PlayerInfo entry = minecraft.getConnection().getPlayerInfo(id);
                        if (entry != null) {
                            name = entry.getProfile().getName();
                            project.setMemberName(uuid, name);
                        }
                    }
                } catch (Exception ignored) {}

                this.roleBtn = Button.builder(getRoleText(role), b -> toggleRole())
                        .bounds(0, 0, 60, 16).build();
                this.roleBtn.active = canEditRole();

                this.removeBtn = Button.builder(Component.translatable("gui.todolist.remove_member").withStyle(ChatFormatting.RED), b -> {
                    ClientBridge.ops().sendRemoveMember(project.getId(), uuid);
                }).bounds(0, 0, 34, 16).build();

                boolean targetSelf = minecraft.player != null && minecraft.player.getUUID().toString().equals(uuid);
                boolean targetProjectManager = project.getOwnerUuid() != null && project.getOwnerUuid().equals(uuid);
                Role actorRole = getCurrentRole();
                Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false, targetSelf, targetProjectManager);
                this.removeBtn.active = PermissionCenter.canPerform(Operation.REMOVE_MEMBER, actorRole, ctx);
            }

            @Override
            public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                context.fill(x, y + 1, x + entryWidth, y + entryHeight - 1, hovered ? 0xFF2B2E34 : 0xFF1E2024);
                context.fill(x, y + 1, x + 2, y + entryHeight - 1, role == Project.ProjectRole.PROJECT_MANAGER ? COL_FICSIT_ORANGE : COL_FICSIT_CYAN);
                context.drawString(font, name, x + 6, y + 4, TEXT_WHITE, false);
                syncButtonLayout(x, y, entryWidth, entryHeight);
                this.removeBtn.render(context, mouseX, mouseY, tickDelta);
                this.roleBtn.render(context, mouseX, mouseY, tickDelta);
            }

            private void syncButtonLayout(int x, int y, int entryWidth, int entryHeight) {
                int btnY = y + (entryHeight - 16) / 2;
                int removeWidth = Math.max(28, Math.min(34, entryWidth / 6));
                int roleWidth = Math.min(60, Math.max(44, entryWidth / 4));
                int removeX = x + entryWidth - removeWidth - 2;
                int roleBtnX = removeX - 4 - roleWidth;
                this.roleBtn.setWidth(roleWidth);
                this.roleBtn.setX(roleBtnX);
                this.roleBtn.setY(btnY);
                this.removeBtn.setX(removeX);
                this.removeBtn.setY(btnY);
            }

            public void syncButtonLayoutForTest(int index, MemberListWidget owner) {
                int entryTop = owner.getRowTop(index);
                int rowLeft = owner.getX() + (owner.getWidth() - owner.getRowWidth()) / 2;
                syncButtonLayout(rowLeft, entryTop, owner.getRowWidth(), owner.itemHeight);
            }

            private boolean canEditRole() {
                boolean targetSelf = minecraft.player != null && minecraft.player.getUUID().toString().equals(uuid);
                boolean targetProjectManager = project.getOwnerUuid() != null && project.getOwnerUuid().equals(uuid);
                Role actorRole = getCurrentRole();
                Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false, targetSelf, targetProjectManager);
                if (!PermissionCenter.canPerform(Operation.CHANGE_MEMBER_ROLE, actorRole, ctx)) return false;
                return role == Project.ProjectRole.MEMBER || role == Project.ProjectRole.LEAD;
            }

            private void toggleRole() {
                if (!canEditRole()) return;

                Project.ProjectRole prevRole = project.getMemberRole(uuid);
                Project.ProjectRole newRole = role == Project.ProjectRole.MEMBER ? Project.ProjectRole.LEAD : Project.ProjectRole.MEMBER;

                project.addMember(uuid, newRole, project.getMemberName(uuid));
                if (memberList != null) {
                    String search = memberSearchField != null ? memberSearchField.getValue() : "";
                    memberList.updateEntries(search);
                }

                if (!ClientBridge.ops().canSendUpdateMemberRole()) {
                    if (prevRole != null) project.addMember(uuid, prevRole, project.getMemberName(uuid));
                    if (memberList != null) {
                        String search = memberSearchField != null ? memberSearchField.getValue() : "";
                        memberList.updateEntries(search);
                    }
                    if (minecraft.player != null) {
                        minecraft.player.displayClientMessage(Component.translatable("message.todolist.role_update_failed"), false);
                    }
                    return;
                }

                ClientBridge.ops().sendUpdateMemberRole(project.getId(), uuid, newRole);
            }

            @Override
            public List<? extends GuiEventListener> children() { return List.of(roleBtn, removeBtn); }

            @Override
            public List<? extends NarratableEntry> narratables() { return List.of(roleBtn, removeBtn); }

            private Component getRoleText(Project.ProjectRole role) {
                if (role == Project.ProjectRole.MEMBER) return Component.translatable("gui.todolist.role.member");
                if (role == Project.ProjectRole.LEAD) return Component.translatable("gui.todolist.role.lead");
                return Component.translatable("gui.todolist.role.manager");
            }

            public String getNameForTest() { return name; }
            public int[] getRoleButtonBoundsForTest() { return new int[] {roleBtn.getX(), roleBtn.getY(), roleBtn.getWidth(), roleBtn.getHeight()}; }
            public int[] getRemoveButtonBoundsForTest() { return new int[] {removeBtn.getX(), removeBtn.getY(), removeBtn.getWidth(), removeBtn.getHeight()}; }
            public void clickRoleButtonForTest() { roleBtn.onPress(); }
            public void clickRemoveButtonForTest() { removeBtn.onPress(); }
        }
    }
}