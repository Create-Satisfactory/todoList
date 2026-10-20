package com.todolist.integration;

import com.todolist.TodoListNeoForge;
import com.todolist.client.TodoHudRenderer;
import com.todolist.gui.TodoScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

/**
 * Universal JEI / EMI / REI Recipe Viewer Integration.
 * Renders compact Satisfactory-styled [≡+] (Add to To-Do List) and [≡-] (Remove from To-Do List)
 * icon buttons next to each recipe in JEI, EMI, and REI.
 */
public final class RecipeViewerTodoIntegration {

    public record ExtractedRecipeCard(
            ItemStack outputStack,
            List<TodoHudRenderer.ParsedItemRequirement> ingredients,
            String categoryTitle,
            int cardX, int cardY, int cardW, int cardH
    ) {}

    private record RecipeButtonPair(
            ExtractedRecipeCard card,
            int addX, int addY,
            int remX, int remY,
            int size
    ) {}

    private static final int BTN_SIZE = 13;

    private static boolean registered = false;
    private static ItemStack lastHoveredIngredientStack = ItemStack.EMPTY;
    private static final List<RecipeButtonPair> ACTIVE_BUTTON_PAIRS = new ArrayList<>();

    private RecipeViewerTodoIntegration() {}

    /**
     * Registers the screen render & click hooks on the NeoForge event bus.
     */
    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(RecipeViewerTodoIntegration::onKeyPressedPre);
        NeoForge.EVENT_BUS.addListener(RecipeViewerTodoIntegration::onScreenRenderPost);
        NeoForge.EVENT_BUS.addListener(RecipeViewerTodoIntegration::onMouseClickedPre);
        TodoListNeoForge.LOGGER.info("Registered JEI/EMI/REI To-Do List icon buttons.");
    }

    private static boolean isRecipeViewerScreen(Screen screen) {
        if (screen == null) return false;
        String cls = screen.getClass().getName().toLowerCase(Locale.ROOT);
        return cls.contains("recipesgui")
                || cls.contains("recipescreen")
                || cls.contains("displayviewingscreen")
                || (cls.contains("jei") && cls.contains("recipe"))
                || (cls.contains("emi") && cls.contains("recipe"))
                || (cls.contains("rei") && cls.contains("viewing"));
    }

    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            Slot slot = containerScreen.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) {
                lastHoveredIngredientStack = slot.getItem().copy();
            }
        }
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        ACTIVE_BUTTON_PAIRS.clear();
        if (!isRecipeViewerScreen(screen)) {
            return;
        }

        List<ExtractedRecipeCard> cards = extractRecipesFromScreen(screen);
        if (cards.isEmpty()) {
            return;
        }

        GuiGraphics context = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        Minecraft mc = Minecraft.getInstance();

        List<Component> pendingTooltip = null;

        context.pose().pushPose();
        context.pose().translate(0, 0, 300);

        for (int i = 0; i < cards.size(); i++) {
            ExtractedRecipeCard card = cards.get(i);

            // Position the [≡+] and [≡-] buttons at the top-right edge of the recipe layout
            // (well above JEI's bottom-right bookmark button so they never collide)
            int bx = (card.cardW() > 0) ? (card.cardX() + card.cardW() + 7) : (screen.width / 2 + 75);
            int addY = (card.cardH() > 0) ? card.cardY() : (screen.height / 2 - 30 + i * 32);
            int remY = addY + BTN_SIZE + 2;

            RecipeButtonPair pair = new RecipeButtonPair(card, bx, addY, bx, remY, BTN_SIZE);
            ACTIVE_BUTTON_PAIRS.add(pair);

            int pinnedCount = TodoScreen.getPinnedRecipeCount(card.outputStack().getItem());
            boolean remEnabled = pinnedCount > 0;

            boolean addHovered = mouseX >= bx && mouseX < bx + BTN_SIZE && mouseY >= addY && mouseY < addY + BTN_SIZE;
            boolean remHovered = mouseX >= bx && mouseX < bx + BTN_SIZE && mouseY >= remY && mouseY < remY + BTN_SIZE;

            // 1. Render [≡+] Add Button
            int addBg = addHovered ? 0xFF7A7E85 : 0xFF5A5E66;
            int addBorder = addHovered ? 0xFFE59344 : 0xFF25272B;
            context.fill(bx, addY, bx + BTN_SIZE, addY + BTN_SIZE, addBg);
            context.renderOutline(bx, addY, BTN_SIZE, BTN_SIZE, addBorder);
            drawPixelTodoIcon(context, bx + 2, addY + 2, true, 0xFFFFFFFF);

            // 2. Render [≡-] Remove Button
            int remBg = !remEnabled ? 0xFF32353B : (remHovered ? 0xFF7A7E85 : 0xFF4A4E55);
            int remBorder = (remEnabled && remHovered) ? 0xFFE59344 : 0xFF25272B;
            int remIconCol = remEnabled ? 0xFFFFFFFF : 0xFF72767D;
            context.fill(bx, remY, bx + BTN_SIZE, remY + BTN_SIZE, remBg);
            context.renderOutline(bx, remY, BTN_SIZE, BTN_SIZE, remBorder);
            drawPixelTodoIcon(context, bx + 2, remY + 2, false, remIconCol);

            // 3. Prepare Tooltip on Hover
            if (addHovered) {
                pendingTooltip = new ArrayList<>();
                pendingTooltip.add(Component.literal("Add to To-Do List").withStyle(ChatFormatting.WHITE));
                if (pinnedCount > 0) {
                    pendingTooltip.add(Component.literal("In To-Do List: " + pinnedCount).withStyle(ChatFormatting.GOLD));
                }
                pendingTooltip.add(Component.literal("Hold Shift for +10").withStyle(ChatFormatting.GRAY));
            } else if (remHovered) {
                pendingTooltip = new ArrayList<>();
                if (remEnabled) {
                    pendingTooltip.add(Component.literal("Remove from To-Do List").withStyle(ChatFormatting.WHITE));
                    pendingTooltip.add(Component.literal("In To-Do List: " + pinnedCount).withStyle(ChatFormatting.GOLD));
                    pendingTooltip.add(Component.literal("Hold Shift for -10").withStyle(ChatFormatting.GRAY));
                } else {
                    pendingTooltip.add(Component.literal("Remove from To-Do List").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }

        if (pendingTooltip != null) {
            context.renderComponentTooltip(mc.font, pendingTooltip, mouseX, mouseY);
        }

        context.pose().popPose();
    }

    public static void onMouseClickedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!isRecipeViewerScreen(screen) || ACTIVE_BUTTON_PAIRS.isEmpty() || event.getButton() != 0) {
            return;
        }

        double mx = event.getMouseX();
        double my = event.getMouseY();
        int step = Screen.hasShiftDown() ? 10 : 1;
        Minecraft mc = Minecraft.getInstance();

        for (RecipeButtonPair pair : ACTIVE_BUTTON_PAIRS) {
            // Check [≡+] Add Button click
            if (mx >= pair.addX() && mx < pair.addX() + pair.size()
                    && my >= pair.addY() && my < pair.addY() + pair.size()) {
                ExtractedRecipeCard card = pair.card();
                TodoScreen.addOrIncrementRecipeTask(
                        card.outputStack(),
                        card.ingredients(),
                        card.categoryTitle(),
                        step
                );
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
                }
                event.setCanceled(true);
                return;
            }

            // Check [≡-] Remove Button click
            if (mx >= pair.remX() && mx < pair.remX() + pair.size()
                    && my >= pair.remY() && my < pair.remY() + pair.size()) {
                ExtractedRecipeCard card = pair.card();
                int currentPinned = TodoScreen.getPinnedRecipeCount(card.outputStack().getItem());
                if (currentPinned > 0) {
                    TodoScreen.removeOrDecrementRecipeTask(card.outputStack(), step);
                    if (mc.player != null) {
                        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 0.85f);
                    }
                }
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * Draws the crisp pixel-art Satisfactory list icon with a '+' or '-' in the bottom-right corner (9x9 area).
     */
    private static void drawPixelTodoIcon(GuiGraphics context, int x, int y, boolean isPlus, int color) {
        // Top bar (full width: 7px)
        context.fill(x, y + 1, x + 7, y + 2, color);
        // Middle bar (full width: 7px)
        context.fill(x, y + 4, x + 7, y + 5, color);
        // Bottom bar (shortened to 4px to make room for + / -)
        context.fill(x, y + 7, x + 4, y + 8, color);

        // Horizontal bar of '+' or '-' at bottom-right
        context.fill(x + 5, y + 7, x + 8, y + 8, color);
        if (isPlus) {
            // Vertical bar of '+'
            context.fill(x + 6, y + 6, x + 7, y + 9, color);
        }
    }

    // ============================================================================================
    // DEEP REFLECTION EXTRACTORS FOR JEI 19.21+, EMI, AND REI
    // ============================================================================================

    private static List<ExtractedRecipeCard> extractRecipesFromScreen(Screen screen) {
        String cls = screen.getClass().getName();
        List<ExtractedRecipeCard> results = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        try {
            if (cls.contains("jei")) {
                scanJeiObjectGraph(screen, 0, visited, results);
            } else if (cls.contains("emi")) {
                scanEmiObjectGraph(screen, 0, visited, results);
            } else if (cls.contains("rei")) {
                extractFromRei(screen, results);
            }
        } catch (Exception ignored) {}

        if (results.isEmpty() && !lastHoveredIngredientStack.isEmpty()) {
            TodoHudRenderer.ResolvedRecipeInfo resolved = TodoHudRenderer.lookupCraftingRecipe(lastHoveredIngredientStack.getItem());
            if (resolved != null) {
                results.add(new ExtractedRecipeCard(
                        new ItemStack(resolved.outputItem(), resolved.outputCountPerCraft()),
                        resolved.ingredients(),
                        "Crafting",
                        0, 0, 0, 0
                ));
            }
        }

        return results;
    }

    private static void scanJeiObjectGraph(Object obj, int depth, Set<Object> visited, List<ExtractedRecipeCard> out) {
        if (obj == null || depth > 4 || !visited.add(obj)) return;

        Class<?> cls = obj.getClass();
        String name = cls.getName();

        if (name.contains("RecipeLayout")) {
            ExtractedRecipeCard card = parseJeiRecipeLayoutObject(obj);
            if (card != null) {
                out.add(card);
                return;
            }
        }

        if (obj instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                scanJeiObjectGraph(item, depth + 1, visited, out);
            }
            return;
        }

        if (!name.startsWith("mezz.jei")) return;

        for (Field f : getAllFields(cls)) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Object child = f.get(obj);
                if (child == null) continue;
                String childClassName = child.getClass().getName();
                if (child instanceof Iterable<?>
                        || childClassName.contains("RecipeGuiLayouts")
                        || childClassName.contains("RecipeLayout")) {
                    scanJeiObjectGraph(child, depth + 1, visited, out);
                }
            } catch (Exception ignored) {}
        }
    }

    private static ExtractedRecipeCard parseJeiRecipeLayoutObject(Object elem) {
        try {
            Object layoutDrawable = elem;

            Object unwrapped = invokeNoArg(elem, "recipeLayout", "getRecipeLayout");
            if (unwrapped != null) {
                layoutDrawable = unwrapped;
            } else {
                for (Field f : getAllFields(elem.getClass())) {
                    f.setAccessible(true);
                    Object fVal = f.get(elem);
                    if (fVal != null && fVal.getClass().getName().contains("RecipeLayout") && fVal != elem) {
                        layoutDrawable = fVal;
                        break;
                    }
                }
            }

            Object slotsView = invokeNoArg(layoutDrawable, "getRecipeSlotsView", "getRecipeSlots");
            if (slotsView == null) return null;

            String catTitle = "Crafting";
            Object cat = invokeNoArg(layoutDrawable, "getRecipeCategory");
            if (cat != null) {
                Object titleObj = invokeNoArg(cat, "getTitle");
                if (titleObj instanceof Component c) {
                    catTitle = c.getString();
                }
            }

            int rx = 0, ry = 0, rw = 0, rh = 0;
            Object rect = invokeNoArg(layoutDrawable, "getRect", "getRectWithBorder");
            if (rect != null) {
                rx = invokeInt(rect, "getX", "x");
                ry = invokeInt(rect, "getY", "y");
                rw = invokeInt(rect, "getWidth", "width");
                rh = invokeInt(rect, "getHeight", "height");
            }

            Object slotViewsObj = invokeNoArg(slotsView, "getSlotViews", "getSlots");
            if (!(slotViewsObj instanceof Iterable<?> slotList)) return null;

            ItemStack outputStack = ItemStack.EMPTY;
            Map<Item, Integer> inputCounts = new LinkedHashMap<>();
            Map<Item, ItemStack> inputStacks = new LinkedHashMap<>();

            for (Object slotView : slotList) {
                if (slotView == null) continue;
                Object roleObj = invokeNoArg(slotView, "getRole");
                String roleName = String.valueOf(roleObj);

                ItemStack stack = extractStackFromJeiSlot(slotView);
                if (!stack.isEmpty()) {
                    if ("OUTPUT".equalsIgnoreCase(roleName) && outputStack.isEmpty()) {
                        outputStack = stack.copy();
                    } else if ("INPUT".equalsIgnoreCase(roleName)) {
                        Item item = stack.getItem();
                        inputCounts.put(item, inputCounts.getOrDefault(item, 0) + Math.max(1, stack.getCount()));
                        inputStacks.putIfAbsent(item, stack.copyWithCount(1));
                    }
                }
            }

            if (!outputStack.isEmpty()) {
                List<TodoHudRenderer.ParsedItemRequirement> reqs = new ArrayList<>();
                for (Map.Entry<Item, Integer> e : inputCounts.entrySet()) {
                    reqs.add(new TodoHudRenderer.ParsedItemRequirement(e.getKey(), inputStacks.get(e.getKey()), e.getValue()));
                }
                return new ExtractedRecipeCard(outputStack, reqs, catTitle, rx, ry, rw, rh);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static ItemStack extractStackFromJeiSlot(Object slotView) {
        try {
            Object dispOpt = invokeNoArg(slotView, "getDisplayedItemStack");
            if (dispOpt instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof ItemStack s && !s.isEmpty()) {
                return s;
            }
            Object streamObj = invokeNoArg(slotView, "getItemStacks");
            if (streamObj instanceof Stream<?> stream) {
                Optional<?> first = stream.findFirst();
                if (first.isPresent() && first.get() instanceof ItemStack s && !s.isEmpty()) {
                    return s;
                }
            }
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    private static void scanEmiObjectGraph(Object obj, int depth, Set<Object> visited, List<ExtractedRecipeCard> out) {
        if (obj == null || depth > 3 || !visited.add(obj)) return;
        Class<?> cls = obj.getClass();
        String name = cls.getName();

        if (name.contains("WidgetGroup")) {
            try {
                for (Field f : getAllFields(cls)) {
                    f.setAccessible(true);
                    if (f.getName().equals("recipe")) {
                        Object emiRecipe = f.get(obj);
                        if (emiRecipe != null) {
                            int gx = invokeInt(obj, "x", "getX");
                            int gy = invokeInt(obj, "y", "getY");
                            int gw = invokeInt(obj, "getWidth", "width");
                            int gh = invokeInt(obj, "getHeight", "height");
                            ExtractedRecipeCard card = parseEmiRecipe(emiRecipe, gx, gy, gw, gh);
                            if (card != null) out.add(card);
                        }
                    }
                }
            } catch (Exception ignored) {}
            return;
        }

        if (obj instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                scanEmiObjectGraph(item, depth + 1, visited, out);
            }
            return;
        }

        if (!name.startsWith("dev.emi")) return;
        for (Field f : getAllFields(cls)) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Object child = f.get(obj);
                if (child instanceof Iterable<?> || (child != null && child.getClass().getName().contains("WidgetGroup"))) {
                    scanEmiObjectGraph(child, depth + 1, visited, out);
                }
            } catch (Exception ignored) {}
        }
    }

    private static ExtractedRecipeCard parseEmiRecipe(Object emiRecipe, int gx, int gy, int gw, int gh) {
        try {
            String catTitle = "Crafting";
            Object cat = invokeNoArg(emiRecipe, "getCategory");
            if (cat != null) {
                Object nameComp = invokeNoArg(cat, "getName");
                if (nameComp instanceof Component c) catTitle = c.getString();
            }

            ItemStack outputStack = ItemStack.EMPTY;
            Object outputsObj = invokeNoArg(emiRecipe, "getOutputs");
            if (outputsObj instanceof Iterable<?> outputs) {
                for (Object emiStack : outputs) {
                    Object mcStack = invokeNoArg(emiStack, "getItemStack");
                    if (mcStack instanceof ItemStack s && !s.isEmpty()) {
                        outputStack = s.copy();
                        break;
                    }
                }
            }
            if (outputStack.isEmpty()) return null;

            Map<Item, Integer> inputCounts = new LinkedHashMap<>();
            Map<Item, ItemStack> inputStacks = new LinkedHashMap<>();
            Object inputsObj = invokeNoArg(emiRecipe, "getInputs");
            if (inputsObj instanceof Iterable<?> inputs) {
                for (Object ing : inputs) {
                    if (ing == null) continue;
                    long amount = 1;
                    Object amtObj = invokeNoArg(ing, "getAmount");
                    if (amtObj instanceof Number n) amount = n.longValue();

                    Object emiStacksObj = invokeNoArg(ing, "getEmiStacks");
                    if (emiStacksObj instanceof Iterable<?> emiStacks) {
                        for (Object emiStack : emiStacks) {
                            Object mcStack = invokeNoArg(emiStack, "getItemStack");
                            if (mcStack instanceof ItemStack s && !s.isEmpty()) {
                                Item item = s.getItem();
                                inputCounts.put(item, inputCounts.getOrDefault(item, 0) + (int) Math.max(1, amount));
                                inputStacks.putIfAbsent(item, s.copyWithCount(1));
                                break;
                            }
                        }
                    }
                }
            }

            List<TodoHudRenderer.ParsedItemRequirement> reqs = new ArrayList<>();
            for (Map.Entry<Item, Integer> e : inputCounts.entrySet()) {
                reqs.add(new TodoHudRenderer.ParsedItemRequirement(e.getKey(), inputStacks.get(e.getKey()), e.getValue()));
            }
            return new ExtractedRecipeCard(outputStack, reqs, catTitle, gx, gy, gw, gh);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void extractFromRei(Screen screen, List<ExtractedRecipeCard> out) {
        String catTitle = "Crafting";
        Object cat = invokeNoArg(screen, "getCurrentCategory");
        if (cat != null) {
            Object title = invokeNoArg(cat, "getTitle");
            if (title instanceof Component c) catTitle = c.getString();
        }

        for (Method m : screen.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && Collection.class.isAssignableFrom(m.getReturnType())) {
                try {
                    m.setAccessible(true);
                    Object res = m.invoke(screen);
                    if (res instanceof Collection<?> col) {
                        for (Object elem : col) {
                            if (elem != null && elem.getClass().getName().contains("Display")) {
                                ExtractedRecipeCard card = parseReiDisplay(elem, catTitle);
                                if (card != null) out.add(card);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private static ExtractedRecipeCard parseReiDisplay(Object display, String catTitle) {
        try {
            Object outEntriesObj = invokeNoArg(display, "getOutputEntries");
            if (!(outEntriesObj instanceof Collection<?> outEntries)) return null;
            ItemStack outputStack = extractFirstItemStackFromReiEntries(outEntries);
            if (outputStack.isEmpty()) return null;

            Map<Item, Integer> inputCounts = new LinkedHashMap<>();
            Map<Item, ItemStack> inputStacks = new LinkedHashMap<>();
            Object inEntriesObj = invokeNoArg(display, "getInputEntries");
            if (inEntriesObj instanceof Collection<?> inEntries) {
                for (Object entryIng : inEntries) {
                    if (entryIng instanceof Collection<?> stacks) {
                        for (Object entryStack : stacks) {
                            Object val = invokeNoArg(entryStack, "getValue");
                            if (val instanceof ItemStack s && !s.isEmpty()) {
                                Item item = s.getItem();
                                inputCounts.put(item, inputCounts.getOrDefault(item, 0) + Math.max(1, s.getCount()));
                                inputStacks.putIfAbsent(item, s.copyWithCount(1));
                                break;
                            }
                        }
                    }
                }
            }

            List<TodoHudRenderer.ParsedItemRequirement> reqs = new ArrayList<>();
            for (Map.Entry<Item, Integer> e : inputCounts.entrySet()) {
                reqs.add(new TodoHudRenderer.ParsedItemRequirement(e.getKey(), inputStacks.get(e.getKey()), e.getValue()));
            }
            return new ExtractedRecipeCard(outputStack, reqs, catTitle, 0, 0, 0, 0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ItemStack extractFirstItemStackFromReiEntries(Collection<?> entries) {
        for (Object entryIng : entries) {
            if (entryIng instanceof Collection<?> stacks) {
                for (Object entryStack : stacks) {
                    Object val = invokeNoArg(entryStack, "getValue");
                    if (val instanceof ItemStack s && !s.isEmpty()) {
                        return s.copy();
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static Object invokeNoArg(Object target, String... methodNames) {
        if (target == null) return null;
        Class<?> cls = target.getClass();
        for (String name : methodNames) {
            for (Method m : cls.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(target);
                    } catch (Exception ignored) {}
                }
            }
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == 0) {
                        try {
                            m.setAccessible(true);
                            return m.invoke(target);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return null;
    }

    private static int invokeInt(Object target, String... names) {
        Object res = invokeNoArg(target, names);
        if (res instanceof Number n) return n.intValue();
        for (String name : names) {
            for (Field f : getAllFields(target.getClass())) {
                if (f.getName().equals(name)) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(target);
                        if (v instanceof Number n) return n.intValue();
                    } catch (Exception ignored) {}
                }
            }
        }
        return 0;
    }

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }
}