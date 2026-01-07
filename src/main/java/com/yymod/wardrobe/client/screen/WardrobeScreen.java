package com.yymod.wardrobe.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import com.yymod.wardrobe.content.data.WardrobeFastTransferMode;
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
    private static final int COLOR_PRESET_GRAY = 0x88C0C0C0;
    private static final int COLOR_PRESET_SEPIA = 0x66C9A15F;

    private Button modeButton;
    private Button transferButton;
    private Button rightClickButton;
    private EditBox setupNameBox;
    private final Button[] setupButtons = new Button[WardrobeBlockEntity.SETUP_COUNT];
    private final Set<Integer> dragSlots = new HashSet<>();
    private boolean dragActive = false;
    private String lastSentName = "";
    private Slot hoveredSlot = null;

    public WardrobeScreen(WardrobeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 252;
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

        transferButton = addRenderableWidget(Button.builder(Component.literal("Transfer"), button -> sendTransfer())
                .pos(left + imageWidth - 92, top + 28)
                .size(86, 20)
                .build());

        rightClickButton = addRenderableWidget(Button.builder(Component.literal(""), button -> toggleRightClick())
                .pos(left + imageWidth - 150, top + 52)
                .size(140, 18)
                .build());

        setupNameBox = new EditBox(font, left + 80, top + 6, 120, 18, Component.literal("Setup name"));
        setupNameBox.setMaxLength(32);
        setupNameBox.setResponder(this::renameActiveSetup);
        addRenderableWidget(setupNameBox);

        for (int i = 0; i < setupButtons.length; i++) {
            int y = top + 18 + i * 18;
            int index = i;
            setupButtons[i] = addRenderableWidget(Button.builder(Component.literal(""), button -> selectSetup(index))
                    .pos(left + 28, y)
                    .size(44, 18)
                    .build());
        }
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

    private void toggleRightClick() {
        WardrobeFastTransferMode current = menu.getFastTransferMode();
        WardrobeFastTransferMode next = switch (current) {
            case NONE -> WardrobeFastTransferMode.RIGHT_CLICK;
            case RIGHT_CLICK -> WardrobeFastTransferMode.SHIFT_CLICK;
            case SHIFT_CLICK -> WardrobeFastTransferMode.NONE;
        };
        WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                WardrobeActionPacket.Action.SET_FAST_TRANSFER, next.ordinal(), false, 0, false, false, ""));
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
        boolean fast = hasShiftDown();
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
        if (!(slot.container instanceof Inventory)) {
            return -1;
        }
        int invIndex = slot.getSlotIndex();
        if (invIndex < 0) {
            return -1;
        }
        if (invIndex < 9) {
            return invIndex;
        }
        if (invIndex < 36) {
            return invIndex;
        }
        if (invIndex < 40) {
            return 36 + (invIndex - 36);
        }
        if (invIndex == 40) {
            return 40;
        }
        return -1;
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
            int color = switch (mode) {
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
            guiGraphics.fill(x1, y1, x2, y1 + 1, color);
            guiGraphics.fill(x1, y2 - 1, x2, y2, color);
            guiGraphics.fill(x1, y1, x1 + 1, y2, color);
            guiGraphics.fill(x2 - 1, y1, x2, y2, color);
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
            boolean showMax = minCount != maxStackSize;
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
        rightClickButton.visible = menu.isSetupMode();
        rightClickButton.setMessage(Component.literal(getFastTransferLabel(menu.getFastTransferMode())));
        setupNameBox.setVisible(menu.isSetupMode());
        setupNameBox.setEditable(menu.isSetupMode());

        for (int i = 0; i < setupButtons.length; i++) {
            setupButtons[i].visible = true;
            setupButtons[i].setMessage(Component.literal(menu.getBlockEntity().getSetup(i).getName()));
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
        } else {
            renderOperationalMarkers(guiGraphics);
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
            ItemStack bound = config.getBoundItem();
            if (bound.isEmpty()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            guiGraphics.renderItem(bound, x, y);
            guiGraphics.fill(x, y, x + 16, y + 16, COLOR_PRESET_GRAY);
            guiGraphics.fill(x, y, x + 16, y + 16, COLOR_PRESET_SEPIA);
        }
    }

    private String getFastTransferLabel(WardrobeFastTransferMode mode) {
        return switch (mode) {
            case NONE -> "Fast transfer: None";
            case RIGHT_CLICK -> "Fast transfer: Right click";
            case SHIFT_CLICK -> "Fast transfer: Shift-click";
        };
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
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            ItemStack stack = hoveredSlot.getItem();
            guiGraphics.renderTooltip(font, getTooltipFromItem(minecraft, stack), stack.getTooltipImage(), mouseX, mouseY);
            return;
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
