package com.yymod.wardrobe.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import com.yymod.wardrobe.content.data.WardrobeSlotConfig;
import com.yymod.wardrobe.content.menu.WardrobeMenu;
import com.yymod.wardrobe.content.transfer.WardrobeTransfer;
import com.yymod.wardrobe.network.WardrobeActionPacket;
import com.yymod.wardrobe.network.WardrobeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;

import java.util.HashSet;
import java.util.Set;

public class WardrobeScreen extends AbstractContainerScreen<WardrobeMenu> {
    private static final int COLOR_SETUP_BG = 0xFF2B3A4B;
    private static final int COLOR_OPERATION_BG = 0xFF4B3A2B;
    private static final int COLOR_PANEL = 0xFF1B1B1B;
    private static final int COLOR_SLOT_BG = 0xFF2A2A2A;
    private static final int COLOR_HIGHLIGHT_GREEN = 0x8800FF00;
    private static final int COLOR_HIGHLIGHT_YELLOW = 0x88FFD200;
    private static final int COLOR_HIGHLIGHT_RED = 0x88FF0000;
    private static final int COLOR_BORDER_BLUE = 0xFF4A76FF;
    private static final int COLOR_BORDER_GREEN = 0xFF3FD46B;
    private static final int COLOR_BORDER_RED = 0xFFE04848;
    private static final int COLOR_PRESET_DARKEN = 0xA6000000;
    private static final int COLOR_PRESET_MISMATCH = 0xA6FF3333;

    private Button modeButton;
    private Button transferButton;
    private Button settingsButton;
    private Button unloadAllButton;
    private EditBox setupNameBox;
    private final Button[] setupButtons = new Button[WardrobeBlockEntity.SETUP_COUNT];
    private final Button[] unloadButtons = new Button[WardrobeBlockEntity.SETUP_COUNT];
    private final Set<Integer> dragSlots = new HashSet<>();
    private boolean dragActive = false;
    private String lastSentName = "";
    private Slot hoveredSlot = null;

    public WardrobeScreen(WardrobeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = WardrobeMenu.MENU_WIDTH;
        imageHeight = 200;
    }

    @Override
    protected void init() {
        super.init();
        int left = leftPos;
        int top = topPos;

        modeButton = addRenderableWidget(Button.builder(Component.literal(""), button -> toggleMode())
                .pos(left + imageWidth - 74, top + 6)
                .size(68, 20)
                .build());

        settingsButton = addRenderableWidget(Button.builder(Component.literal("Settings"), button -> openSettings())
                .pos(left + imageWidth - 74, top + 28)
                .size(68, 18)
                .build());

        transferButton = addRenderableWidget(Button.builder(Component.literal("Transfer all"), button -> sendTransfer())
                .pos(left + imageWidth - 74, top + 48)
                .size(68, 18)
                .build());

        setupNameBox = new EditBox(font, left + 80, top + 6, 120, 18, Component.literal("Setup name"));
        setupNameBox.setMaxLength(32);
        setupNameBox.setResponder(this::renameActiveSetup);
        addRenderableWidget(setupNameBox);

        for (int i = 0; i < setupButtons.length; i++) {
            int y = top + 28 + i * 18;
            int index = i;
            setupButtons[i] = addRenderableWidget(Button.builder(Component.literal(""), button -> selectSetup(index))
                    .pos(left + 28, y)
                    .size(68, 18)
                    .build());
            unloadButtons[i] = addRenderableWidget(Button.builder(Component.literal("E"), button -> unloadSetup(index))
                    .pos(left + 28 + 68 + 4, y)
                    .size(18, 18)
                    .build());
        }

        unloadAllButton = addRenderableWidget(Button.builder(Component.literal("Unload all"), button -> unloadAll())
                .pos(left + 28, top + 28 + setupButtons.length * 18 + 4)
                .size(90, 18)
                .build());

        setupNameBox.setX(left + (imageWidth - 120) / 2);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        setupNameBox.tick();
        updateWidgets();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        if (menu.isSetupMode()) {
            renderBg(guiGraphics, partialTick, mouseX, mouseY);
            hoveredSlot = getSlotAt(mouseX, mouseY);
            renderSetupPresets(guiGraphics);
            renderItemOverrides(guiGraphics);
            renderLabels(guiGraphics, mouseX, mouseY);
            for (var renderable : renderables) {
                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            renderTooltip(guiGraphics, mouseX, mouseY);
            renderOverlays(guiGraphics);
        } else {
            hoveredSlot = null;
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            renderOperationalPresets(guiGraphics);
            renderOverlays(guiGraphics);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        int bgColor = menu.isSetupMode() ? COLOR_SETUP_BG : COLOR_OPERATION_BG;
        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, bgColor);
        guiGraphics.fill(left + 4, top + 4, left + imageWidth - 4, top + imageHeight - 4, COLOR_PANEL);

        for (Slot slot : menu.slots) {
            guiGraphics.fill(left + slot.x - 1, top + slot.y - 1,
                    left + slot.x + 17, top + slot.y + 17, COLOR_SLOT_BG);
        }

        if (!menu.isSetupMode()) {
            for (int slotIndex = 0; slotIndex < WardrobeSlotConfig.SLOT_COUNT; slotIndex++) {
                int highlight = menu.getHighlight(slotIndex);
                if (highlight == WardrobeTransfer.HIGHLIGHT_NONE) {
                    continue;
                }
                Slot slot = getSlotForWardrobeIndex(slotIndex);
                if (slot == null) {
                    continue;
                }
                int x1 = left + slot.x;
                int y1 = top + slot.y;
                int color = switch (highlight) {
                    case WardrobeTransfer.HIGHLIGHT_LOAD_OK -> COLOR_HIGHLIGHT_GREEN;
                    case WardrobeTransfer.HIGHLIGHT_LOAD_MISSING -> COLOR_HIGHLIGHT_YELLOW;
                    case WardrobeTransfer.HIGHLIGHT_UNLOAD -> COLOR_HIGHLIGHT_RED;
                    default -> 0;
                };
                if (color != 0) {
                    guiGraphics.fill(x1, y1, x1 + 16, y1 + 16, color);
                }
            }
        }

        if (menu.getBlockEntity().isOutputFull()) {
            guiGraphics.drawString(font, "Output chest is full!", left + 8, top + imageHeight - 12, 0xFFFF5555, false);
        }

        if (!menu.getLastError().isEmpty()) {
            guiGraphics.drawString(font, menu.getLastError(), left + 8, top + imageHeight - 24, 0xFFFF5555, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 8, 6, 0xFFFFFFFF, false);
        if (!menu.isSetupMode()) {
            String name = menu.getBlockEntity().getActiveSetup().getName();
            if (!name.isEmpty()) {
                int x = (imageWidth - 120) / 2;
                guiGraphics.drawString(font, name, x, 8, 0xFFE6E6E6, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!menu.isSetupMode()) {
            if (!hasShiftDown()) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            Slot slot = getSlotAt(mouseX, mouseY);
            if (slot != null) {
                int slotIndex = menuSlotToWardrobeIndex(slot);
                if (slotIndex >= 0) {
                    WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                            WardrobeActionPacket.Action.OPERATE_SLOT, slotIndex, false, 0, false, false, ""));
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (menu.isSetupMode()) {
            Slot slot = getSlotAt(mouseX, mouseY);
            if (slot != null) {
                int slotIndex = menuSlotToWardrobeIndex(slot);
                if (slotIndex >= 0) {
                    if (handleQuarterAdjust(slot, slotIndex, mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!menu.isSetupMode() && button == 0 && hasShiftDown()) {
            dragActive = true;
            Slot slot = getSlotAt(mouseX, mouseY);
            if (slot != null) {
                int slotIndex = menuSlotToWardrobeIndex(slot);
                if (slotIndex >= 0 && dragSlots.add(slotIndex)) {
                    WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                            WardrobeActionPacket.Action.OPERATE_SLOT, slotIndex, false, 0, false, false, ""));
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragActive && button == 0) {
            dragActive = false;
            dragSlots.clear();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (setupNameBox.isFocused()) {
            if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                return true;
            }
            if (setupNameBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode == 256) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (setupNameBox.isFocused() && setupNameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        super.onClose();
        dragSlots.clear();
    }

    private void toggleMode() {
        boolean nextMode = !menu.isSetupMode();
        WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                WardrobeActionPacket.Action.TOGGLE_MODE, 0, nextMode, 0, false, false, ""));
    }

    private void sendTransfer() {
        menu.setLastError("");
        WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                WardrobeActionPacket.Action.TRANSFER_ALL, 0, false, 0, false, false, ""));
    }

    private void selectSetup(int index) {
        WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                WardrobeActionPacket.Action.SELECT_SETUP, index, false, 0, false, false, ""));
    }

    private void unloadSetup(int index) {
        WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                WardrobeActionPacket.Action.UNLOAD_SETUP, index, false, 0, false, false, ""));
    }

    private void unloadAll() {
        WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                WardrobeActionPacket.Action.UNLOAD_ALL, 0, false, 0, false, false, ""));
    }

    private void openSettings() {
        if (minecraft != null) {
            minecraft.setScreen(new WardrobeSettingsScreen(this, menu));
        }
    }

    private void renameActiveSetup(String text) {
        if (text.equals(lastSentName)) {
            return;
        }
        lastSentName = text;
        int index = menu.getBlockEntity().getActiveSetupIndex();
        WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                WardrobeActionPacket.Action.RENAME_SETUP, index, false, 0, false, false, text));
    }

    private boolean handleQuarterAdjust(Slot slot, int slotIndex, double mouseX, double mouseY, int button) {
        if (hasShiftDown()) {
            return false;
        }
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || !stack.isStackable()) {
            return false;
        }

        int x = (int) mouseX - (leftPos + slot.x);
        int y = (int) mouseY - (topPos + slot.y);
        if (x < 8) {
            return false;
        }

        boolean adjustMax = y < 8;
        boolean fast = false;
        int delta = button == 0 ? 1 : -1;

        WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                WardrobeActionPacket.Action.ADJUST_COUNT, slotIndex, false, delta, adjustMax, fast, ""));
        return true;
    }

    private Slot getSlotForWardrobeIndex(int slotIndex) {
        for (Slot slot : menu.slots) {
            int mapped = menuSlotToWardrobeIndex(slot);
            if (mapped == slotIndex) {
                return slot;
            }
        }
        return null;
    }

    private int menuSlotToWardrobeIndex(Slot slot) {
        return menu.getWardrobeSlotIndex(slot);
    }

    private void renderSlotBorders(GuiGraphics guiGraphics) {
        int left = leftPos;
        int top = topPos;
        for (Slot slot : menu.slots) {
            int slotIndex = menuSlotToWardrobeIndex(slot);
            if (slotIndex < 0) {
                continue;
            }
            WardrobeSlotConfig config = menu.getBlockEntity().getActiveSetup().getSlot(slotIndex);
            if (!config.isBound()) {
                continue;
            }
            com.yymod.wardrobe.content.data.WardrobeSlotMode mode = config.getEffectiveMode();
            if (mode == com.yymod.wardrobe.content.data.WardrobeSlotMode.NONE) {
                continue;
            }
            int color = config.isEquipmentSlot() ? COLOR_BORDER_GREEN : switch (mode) {
                case BOTH -> COLOR_BORDER_BLUE;
                case UNLOAD -> COLOR_BORDER_RED;
                case LOAD -> COLOR_BORDER_GREEN;
                case NONE -> 0;
            };
            if (color == 0) {
                continue;
            }
            int x1 = left + slot.x - 1;
            int y1 = top + slot.y - 1;
            int x2 = x1 + 18;
            int y2 = y1 + 18;
            int thickness = config.isEquipmentSlot() ? 2 : 1;
            guiGraphics.fill(x1, y1, x2, y1 + thickness, color);
            guiGraphics.fill(x1, y2 - thickness, x2, y2, color);
            guiGraphics.fill(x1, y1, x1 + thickness, y2, color);
            guiGraphics.fill(x2 - thickness, y1, x2, y2, color);
        }
    }

    private void renderSetupCounts(GuiGraphics guiGraphics) {
        int left = leftPos;
        int top = topPos;
        for (Slot slot : menu.slots) {
            int slotIndex = menuSlotToWardrobeIndex(slot);
            if (slotIndex < 0) {
                continue;
            }
            WardrobeSlotConfig config = menu.getBlockEntity().getActiveSetup().getSlot(slotIndex);
            if (!config.isBound()) {
                continue;
            }
            ItemStack bound = config.getBoundItem();
            if (!bound.isStackable()) {
                continue;
            }
            int x = left + slot.x;
            int y = top + slot.y;
            int maxCount = config.getMaxCount();
            int minCount = config.getMinCount();
            int maxStackSize = bound.getMaxStackSize();
            boolean showMax = minCount != maxStackSize && maxCount != 0;
            boolean showMin = maxCount != 0;

            if (showMax) {
                String maxText = Integer.toString(maxCount);
                guiGraphics.drawString(font, maxText, x + 17 - font.width(maxText), y + 1, 0xFFFFFFFF, false);
            }
            if (showMin) {
                String minText = Integer.toString(minCount);
                guiGraphics.drawString(font, minText, x + 17 - font.width(minText), y + 9, 0xFFFFFFFF, false);
            }
        }
    }

    private void updateWidgets() {
        modeButton.setMessage(menu.isSetupMode() ? Component.literal("Setup") : Component.literal("Operational"));
        transferButton.visible = !menu.isSetupMode();
        settingsButton.visible = true;
        unloadAllButton.visible = !menu.isSetupMode();
        setupNameBox.setVisible(menu.isSetupMode());
        setupNameBox.setEditable(menu.isSetupMode());

        for (int i = 0; i < setupButtons.length; i++) {
            setupButtons[i].visible = true;
            setupButtons[i].setMessage(Component.literal(menu.getBlockEntity().getSetup(i).getName()));
            unloadButtons[i].visible = !menu.isSetupMode();
        }

        if (menu.isSetupMode() && !setupNameBox.isFocused()) {
            String name = menu.getBlockEntity().getActiveSetup().getName();
            lastSentName = name;
            setupNameBox.setValue(name);
        }
    }

    private void renderOverlays(GuiGraphics guiGraphics) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 1000);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(519);
        renderSlotBorders(guiGraphics);
        if (menu.isSetupMode()) {
            renderSetupCounts(guiGraphics);
            renderSetupPresetOverlays(guiGraphics);
            renderSetupMismatchMarkers(guiGraphics);
            renderSetupMatchMarkers(guiGraphics);
        } else {
            renderOperationalMarkers(guiGraphics);
            renderOperationalPresetOverlays(guiGraphics);
        }
        RenderSystem.depthFunc(515);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();
    }

    private void renderOperationalPresets(GuiGraphics guiGraphics) {
        for (Slot slot : menu.slots) {
            int slotIndex = menuSlotToWardrobeIndex(slot);
            if (slotIndex < 0 || slot.hasItem()) {
                continue;
            }
            WardrobeSlotConfig config = menu.getBlockEntity().getActiveSetup().getSlot(slotIndex);
            if (!config.isBound()) {
                continue;
            }
            if (config.getMaxCount() == 0) {
                continue;
            }
            ItemStack bound = config.getBoundItem();
            if (bound.isEmpty()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            guiGraphics.renderItem(bound, x, y);
        }
    }

    private void renderSetupPresets(GuiGraphics guiGraphics) {
        for (Slot slot : menu.slots) {
            int slotIndex = menuSlotToWardrobeIndex(slot);
            if (slotIndex < 0 || slot.hasItem()) {
                continue;
            }
            WardrobeSlotConfig config = menu.getBlockEntity().getActiveSetup().getSlot(slotIndex);
            if (!config.isBound()) {
                continue;
            }
            if (config.getMaxCount() == 0) {
                continue;
            }
            ItemStack bound = config.getBoundItem();
            if (bound.isEmpty()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            guiGraphics.renderItem(bound, x, y);
        }
    }

    private void renderOperationalPresetOverlays(GuiGraphics guiGraphics) {
        for (Slot slot : menu.slots) {
            int slotIndex = menuSlotToWardrobeIndex(slot);
            if (slotIndex < 0 || slot.hasItem()) {
                continue;
            }
            WardrobeSlotConfig config = menu.getBlockEntity().getActiveSetup().getSlot(slotIndex);
            if (!config.isBound()) {
                continue;
            }
            if (config.getMaxCount() == 0) {
                continue;
            }
            if (config.getBoundItem().isEmpty()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            guiGraphics.fill(x, y, x + 16, y + 16, COLOR_PRESET_DARKEN);
        }
    }

    private void renderSetupPresetOverlays(GuiGraphics guiGraphics) {
        for (Slot slot : menu.slots) {
            int slotIndex = menuSlotToWardrobeIndex(slot);
            if (slotIndex < 0 || slot.hasItem()) {
                continue;
            }
            WardrobeSlotConfig config = menu.getBlockEntity().getActiveSetup().getSlot(slotIndex);
            if (!config.isBound()) {
                continue;
            }
            if (config.getMaxCount() == 0) {
                continue;
            }
            if (config.getBoundItem().isEmpty()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            guiGraphics.fill(x, y, x + 16, y + 16, COLOR_PRESET_DARKEN);
        }
    }

    private void renderSetupMismatchMarkers(GuiGraphics guiGraphics) {
        for (Slot slot : menu.slots) {
            int slotIndex = menuSlotToWardrobeIndex(slot);
            if (slotIndex < 0 || !slot.hasItem()) {
                continue;
            }
            WardrobeSlotConfig config = menu.getBlockEntity().getActiveSetup().getSlot(slotIndex);
            if (!config.isBound()) {
                continue;
            }
            ItemStack bound = config.getBoundItem();
            if (bound.isEmpty()) {
                continue;
            }
            ItemStack actual = slot.getItem();
            if (ItemStack.isSameItemSameTags(actual, bound)) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            for (int i = 0; i < 5; i++) {
                guiGraphics.fill(x + i, y + i, x + i + 1, y + i + 1, COLOR_PRESET_MISMATCH);
                guiGraphics.fill(x + 4 - i, y + i, x + 5 - i, y + i + 1, COLOR_PRESET_MISMATCH);
            }
        }
    }

    private void renderSetupMatchMarkers(GuiGraphics guiGraphics) {
        int left = leftPos;
        int top = topPos;
        for (Slot slot : menu.slots) {
            int slotIndex = menuSlotToWardrobeIndex(slot);
            if (slotIndex < 0) {
                continue;
            }
            WardrobeSlotConfig config = menu.getBlockEntity().getActiveSetup().getSlot(slotIndex);
            if (!config.isBound()) {
                continue;
            }
            String marker = switch (config.getMatchMode()) {
                case TAG -> "#";
                case TYPE -> "T";
                default -> "";
            };
            if (marker.isEmpty()) {
                continue;
            }
            int x = left + slot.x + 1;
            int y = top + slot.y + 1;
            guiGraphics.drawString(font, marker, x, y, 0xFFFFFFFF, false);
        }
    }

    private void renderOperationalMarkers(GuiGraphics guiGraphics) {
        int left = leftPos;
        int top = topPos;
        for (int slotIndex = 0; slotIndex < WardrobeSlotConfig.SLOT_COUNT; slotIndex++) {
            if (menu.getHighlight(slotIndex) != WardrobeTransfer.HIGHLIGHT_UNLOAD) {
                continue;
            }
            Slot slot = getSlotForWardrobeIndex(slotIndex);
            if (slot == null) {
                continue;
            }
        }
    }

    private void renderItemOverrides(GuiGraphics guiGraphics) {
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            guiGraphics.renderItem(stack, x, y);
            if (!menu.isSetupMode()) {
                guiGraphics.renderItemDecorations(font, stack, x, y, "1");
            }
        }

        if (hoveredSlot != null && hoveredSlot.isActive()) {
            int x = leftPos + hoveredSlot.x;
            int y = topPos + hoveredSlot.y;
            renderSlotHighlight(guiGraphics, x, y, 0, getSlotColor(hoveredSlot.getSlotIndex()));
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (hoveredSlot != null) {
            if (menu.isSetupMode()) {
                int slotIndex = menuSlotToWardrobeIndex(hoveredSlot);
                if (slotIndex >= 0) {
                    WardrobeSlotConfig config = menu.getBlockEntity().getActiveSetup().getSlot(slotIndex);
                    if (config.getMatchMode() == com.yymod.wardrobe.content.data.WardrobeMatchMode.TAG
                            && !config.getMatchTagId().isEmpty()) {
                        Component tagLine = Component.literal("Tag: " + config.getMatchTagId());
                        if (hoveredSlot.hasItem()) {
                            ItemStack stack = hoveredSlot.getItem();
                            var tooltip = getTooltipFromItem(minecraft, stack);
                            tooltip.add(tagLine);
                            guiGraphics.renderTooltip(font, tooltip, stack.getTooltipImage(), mouseX, mouseY);
                        } else {
                            guiGraphics.renderTooltip(font, tagLine, mouseX, mouseY);
                        }
                        return;
                    }
                }
            }
            if (hoveredSlot.hasItem()) {
                ItemStack stack = hoveredSlot.getItem();
                guiGraphics.renderTooltip(font, getTooltipFromItem(minecraft, stack), stack.getTooltipImage(), mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private Slot getSlotAt(double mouseX, double mouseY) {
        int left = leftPos;
        int top = topPos;
        for (Slot slot : menu.slots) {
            int x = left + slot.x;
            int y = top + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }
}
