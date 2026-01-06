package com.yymod.wardrobe.client.screen;

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

    private Button modeButton;
    private Button transferButton;
    private Button rightClickButton;
    private EditBox setupNameBox;
    private final Button[] setupButtons = new Button[WardrobeBlockEntity.SETUP_COUNT];
    private final Set<Integer> dragSlots = new HashSet<>();
    private boolean dragActive = false;
    private String lastSentName = "";

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
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
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
                if (highlight == WardrobeTransfer.HIGHLIGHT_UNLOAD) {
                    guiGraphics.drawString(font, "X", x1 + 5, y1 + 3, 0xFFFFFFFF, false);
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
        if (menu.isSetupMode()) {
            Slot slot = getSlotUnderMouse();
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
        if (!menu.isSetupMode() && button == 0) {
            dragActive = true;
            Slot slot = getSlotUnderMouse();
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
        boolean next = !menu.isRightClickEnabled();
        WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                WardrobeActionPacket.Action.TOGGLE_RIGHT_CLICK, 0, next, 0, false, false, ""));
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

    private void updateWidgets() {
        modeButton.setMessage(menu.isSetupMode() ? Component.literal("Setup") : Component.literal("Operational"));
        transferButton.visible = !menu.isSetupMode();
        rightClickButton.visible = menu.isSetupMode();
        rightClickButton.setMessage(Component.literal(menu.isRightClickEnabled() ? "Right click: On" : "Right click: Off"));
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
}
