package com.todolist.gui;

import com.todolist.client.ClientBridge;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import org.lwjgl.glfw.GLFW;

import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * FICSIT-styled Add Project Modal Dialog.
 */
public class AddProjectScreen extends Screen {
    private static final int COL_HEADER_BG       = 0xF225272B;
    private static final int COL_BODY_BG       = 0xEB181A1D;
    private static final int COL_WELL_BG       = 0xFF121316;
    private static final int COL_FICSIT_ORANGE = 0xFFE59344;
    private static final int TEXT_WHITE        = 0xFFFFFFFF;
    private static final int TEXT_GRAY         = 0xFFB0B5BC;
    private static final int TEXT_DARK         = 0xFF181A1D;

    private final Screen parent;
    private EditBox nameField;
    private Project.Scope scope = Project.Scope.PERSONAL;
    private Button scopeButton;
    private Button createButton;
    private Button cancelButton;
    private boolean teamProjectsEnabled = true;
    private final Random random = new Random();

    public AddProjectScreen(Screen parent) {
        this(parent, null);
    }

    public AddProjectScreen(Screen parent, Project.Scope defaultScope) {
        super(Component.translatable("gui.todolist.add_project.title"));
        this.parent = parent;
        if (defaultScope != null) {
            this.scope = defaultScope;
        }
    }

    @Override
    protected void init() {
        teamProjectsEnabled = ClientBridge.ops().isTeamProjectsEnabled();
        if (!teamProjectsEnabled) {
            scope = Project.Scope.PERSONAL;
        }

        int w = Math.max(220, Math.min(340, width - 20));
        int h = 176;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        nameField = new EditBox(font, x + 24, y + 44, w - 48, 16, Component.translatable("gui.todolist.project.name"));
        nameField.setBordered(false);
        nameField.setMaxLength(32);
        nameField.setTextColor(TEXT_WHITE);
        nameField.setHint(Component.literal("Project Name...").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameField);

        scopeButton = Button.builder(getScopeText(), button -> {
            if (!teamProjectsEnabled) return;
            scope = (scope == Project.Scope.PERSONAL) ? Project.Scope.TEAM : Project.Scope.PERSONAL;
            button.setMessage(getScopeText());
        }).bounds(x + 24, y + 88, w - 48, 20).build();
        scopeButton.active = teamProjectsEnabled;
        addRenderableWidget(scopeButton);

        createButton = Button.builder(Component.translatable("gui.todolist.create"), button -> createProject())
                .bounds(x + 14, y + h - 28, 110, 20).build();
        addRenderableWidget(createButton);

        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), button -> onClose())
                .bounds(x + w - 124, y + h - 28, 110, 20).build();
        addRenderableWidget(cancelButton);

        setFocused(nameField);
    }

    private Component getScopeText() {
        return Component.translatable("gui.todolist.scope",
                Component.translatable("gui.todolist.scope." + scope.name().toLowerCase()));
    }

    EditBox getNameFieldForTest() { return nameField; }
    Button getScopeButtonForTest() { return scopeButton; }
    Button getCreateButtonForTest() { return createButton; }
    Button getCancelButtonForTest() { return cancelButton; }
    Project.Scope getScopeForTest() { return scope; }

    private void createProject() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) return;

        Project project = new Project();
        project.setName(name);
        project.setScope(teamProjectsEnabled ? scope : Project.Scope.PERSONAL);
        if (minecraft.player != null) {
            project.setOwnerUuid(minecraft.player.getUUID().toString());
        }

        float hue = random.nextFloat();
        float saturation = 0.5f + random.nextFloat() * 0.5f;
        float brightness = 0.8f + random.nextFloat() * 0.2f;
        int color = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        project.setColor(color);

        ClientBridge.ops().sendAddProject(project);
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && nameField.isFocused()) {
            createProject();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x78090A0C);

        int w = Math.max(220, Math.min(340, width - 20));
        int h = 176;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        // Header Bar + FICSIT Orange Accent
        context.fill(x, y, x + w, y + 22, COL_HEADER_BG);
        context.fill(x, y + 20, x + w, y + 22, COL_FICSIT_ORANGE);
        context.drawString(font, "+", x + 8, y + 6, COL_FICSIT_ORANGE, false);
        context.drawString(font, title, x + 22, y + 6, TEXT_WHITE, false);

        // Body & Input Wells
        context.fill(x, y + 22, x + w, y + h, COL_BODY_BG);

        // Name Well
        context.fill(x + 18, y + 36, x + w - 18, y + 62, COL_WELL_BG);
        context.renderOutline(x + 18, y + 36, w - 36, 26, 0xFF282B30);
        context.pose().pushPose();
        context.pose().translate(x + 24, y + 28, 0);
        context.pose().scale(0.75f, 0.75f, 1.0f);
        context.drawString(font, "PROJECT NAME", 0, 0, TEXT_GRAY, false);
        context.pose().popPose();

        // Scope Well
        context.fill(x + 18, y + 78, x + w - 18, y + 120, COL_WELL_BG);
        context.renderOutline(x + 18, y + 78, w - 36, 42, 0xFF282B30);
        context.pose().pushPose();
        context.pose().translate(x + 24, y + 70, 0);
        context.pose().scale(0.75f, 0.75f, 1.0f);
        context.drawString(font, "SCOPE ASSIGNMENT", 0, 0, TEXT_GRAY, false);
        context.pose().popPose();

        context.renderOutline(x, y, w, h, 0xFF141518);
        super.render(context, mouseX, mouseY, delta);
    }
}