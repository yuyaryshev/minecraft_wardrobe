package com.yymod.wardrobe.client.screen;

import com.yymod.wardrobe.content.data.WardrobeFastTransferMode;
import com.yymod.wardrobe.content.data.WardrobeSetupJson;
import com.yymod.wardrobe.content.menu.WardrobeMenu;
import com.yymod.wardrobe.network.WardrobeActionPacket;
import com.yymod.wardrobe.network.WardrobeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WardrobeSettingsScreen extends Screen {
    private final Screen parent;
    private final WardrobeMenu menu;
    private WardrobeFastTransferMode fastTransferMode;
    private Button fastTransferButton;
    private Button scanRangeButton;

    public WardrobeSettingsScreen(Screen parent, WardrobeMenu menu) {
        super(Component.literal("Settings"));
        this.parent = parent;
        this.menu = menu;
        this.fastTransferMode = menu.getFastTransferMode();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int startY = height / 2 - 48;

        fastTransferButton = addRenderableWidget(Button.builder(Component.literal(getFastTransferLabel(fastTransferMode)),
                        button -> cycleFastTransfer())
                .bounds(centerX - 100, startY, 200, 20)
                .build());

        if (menu.isSetupMode()) {
            scanRangeButton = addRenderableWidget(Button.builder(Component.literal(getScanRangeLabel()),
                            button -> cycleScanRange())
                    .bounds(centerX - 100, startY + 24, 200, 20)
                    .build());
        }

        addRenderableWidget(Button.builder(Component.literal("Copy all setups"), button -> copyAllSetups())
                .bounds(centerX - 100, startY + 48, 200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Copy current setup"), button -> copyCurrentSetup())
                .bounds(centerX - 100, startY + 72, 200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Paste setup"), button -> pasteSetup())
                .bounds(centerX - 100, startY + 96, 200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("How to?"), button -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new WardrobeHowToScreen(this));
                    }
                })
                .bounds(centerX - 100, startY + 120, 200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                    }
                })
                .bounds(centerX - 50, startY + 148, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void cycleFastTransfer() {
        fastTransferMode = switch (fastTransferMode) {
            case NONE -> WardrobeFastTransferMode.RIGHT_CLICK;
            case RIGHT_CLICK -> WardrobeFastTransferMode.SHIFT_CLICK;
            case SHIFT_CLICK -> WardrobeFastTransferMode.LEFT_CLICK;
            case LEFT_CLICK -> WardrobeFastTransferMode.SHIFT_LEFT_CLICK;
            case SHIFT_LEFT_CLICK -> WardrobeFastTransferMode.NONE;
        };
        fastTransferButton.setMessage(Component.literal(getFastTransferLabel(fastTransferMode)));
        WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                WardrobeActionPacket.Action.SET_FAST_TRANSFER, fastTransferMode.ordinal(), false, 0, false, false, ""));
    }

    private void cycleScanRange() {
        int range = menu.getArmorStandScanRange();
        int next = range >= 16 ? 1 : range + 1;
        WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                WardrobeActionPacket.Action.SET_SCAN_RANGE, next, false, 0, false, false, ""));
        if (scanRangeButton != null) {
            scanRangeButton.setMessage(Component.literal(getScanRangeLabel(next)));
        }
    }

    private String getScanRangeLabel() {
        return getScanRangeLabel(menu.getArmorStandScanRange());
    }

    private String getScanRangeLabel(int range) {
        return "Scan range: " + range;
    }

    private String getFastTransferLabel(WardrobeFastTransferMode mode) {
        return switch (mode) {
            case NONE -> "Fast transfer: None";
            case RIGHT_CLICK -> "Fast transfer: Right click";
            case SHIFT_CLICK -> "Fast transfer: Shift-click";
            case LEFT_CLICK -> "Fast transfer: Left click";
            case SHIFT_LEFT_CLICK -> "Fast transfer: Shift-left";
        };
    }

    private void copyAllSetups() {
        if (minecraft == null) {
            return;
        }
        String json = WardrobeSetupJson.toJsonAll(menu.getBlockEntity().getSetups());
        minecraft.keyboardHandler.setClipboard(json);
    }

    private void copyCurrentSetup() {
        if (minecraft == null) {
            return;
        }
        String json = WardrobeSetupJson.toJson(menu.getBlockEntity().getActiveSetup());
        minecraft.keyboardHandler.setClipboard(json);
    }

    private void pasteSetup() {
        if (minecraft == null) {
            return;
        }
        String text = minecraft.keyboardHandler.getClipboard();
        if (text == null || text.isBlank()) {
            return;
        }
        if (minecraft != null) {
            minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(result -> {
                minecraft.setScreen(this);
                if (result) {
                    sendPasteChunks(text);
                }
            }, Component.literal("Paste setup?"), Component.literal("This will overwrite existing setups.")));
        }
    }

    private void sendPasteChunks(String text) {
        if (text == null) {
            return;
        }
        int chunkSize = 30000;
        int totalChunks = (text.length() + chunkSize - 1) / chunkSize;
        if (totalChunks <= 1) {
            WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                    WardrobeActionPacket.Action.PASTE_SETUP, 0, false, 0, false, false, text));
            return;
        }
        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(text.length(), start + chunkSize);
            String chunk = text.substring(start, end);
            WardrobeNetwork.sendToServer(new WardrobeActionPacket(menu.getBlockEntity().getBlockPos(),
                    WardrobeActionPacket.Action.PASTE_SETUP, i, true, totalChunks, false, false, chunk));
        }
    }
}
