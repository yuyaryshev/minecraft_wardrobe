package com.yymod.wardrobe.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class WardrobeHowToScreen extends Screen {
    private static final int PADDING = 16;
    private static final String EN_TEXT = """
            Welcome to your Wardrobe!
            To use Wardrobe place a chest(or other inventory) under it and some more chests on side and behind it. Chest below Wardrobe is used for unloading into it. Chests to the side and back are used to load (refill) items.

            In wardrobe UI you can preset which items to load and which to unload from/to your character to do it switch to "Setup" mode.
            - Loading - left-click on an item you are holding in your inventory to make it load to your character - this is shown as green border.
            - Unloading - use right-click to set unloading of an item slot.
            - Complex mode - you can-fine tune loading if you, say, want to have at least 10 cobblestone and at most 20 - click on right bottom quarter to set min=10 and on top right quarter to set max = 20.
            - When in "Setup" some items might get in the way of your setup - switch rapidly to "Operational" - there you can move your items, then switch back.

            After you done "Setup" - switch to "Operational" mode.
            Here you can just "Transfer" all items with one hit of a button.
            Or you can Transfer just one item - Shift + click on that slot.
            Or you can manually move items as in normal inventory.

            Color borders show mode of each cell.
            Color background show operation which will happen on "Transfer".
            - Green - loading/refill
            - Yellow - also loading/refill but you don't have enough items in chests around Wardrobe to fulfill your demands
            - Red - unloading
            """;

    private static final String RU_TEXT = """
            Добро пожаловать в Wardrobe!
            Чтобы пользоваться Wardrobe, поставьте сундук (или другой инвентарь) под него и еще сундуки сбоку и сзади. Сундук под Wardrobe используется для выгрузки. Сундуки по бокам и сзади используются для загрузки (пополнения) предметов.

            В интерфейсе Wardrobe вы можете заранее указать, какие предметы загружать и какие выгружать из/в ваш инвентарь — для этого переключитесь в режим "Setup".
            - Загрузка — левый клик по предмету, который вы держите в инвентаре, чтобы он загружался к персонажу — это отображается зеленой рамкой.
            - Выгрузка — правый клик, чтобы задать выгрузку предмета из слота.
            - Сложный режим — можно точно настроить загрузку: например, иметь минимум 10 булыжника и максимум 20 — кликните по правому нижнему кварталу, чтобы задать min=10, и по правому верхнему кварталу, чтобы задать max = 20.
            - Если в режиме "Setup" предметы мешают настройке — быстро переключитесь в "Operational", там можно перемещать предметы, затем вернитесь обратно.

            После того как закончите "Setup" — переключитесь в режим "Operational".
            Здесь можно "Transfer" все предметы одним нажатием кнопки.
            Или перенести только один предмет — Shift + клик по слоту.
            Или вручную перемещать предметы как в обычном инвентаре.

            Цвет рамки показывает режим каждого слота.
            Цвет фона показывает операцию, которая произойдет при "Transfer".
            - Зеленый — загрузка/пополнение
            - Желтый — тоже загрузка/пополнение, но в сундуках вокруг Wardrobe не хватает предметов для выполнения
            - Красный — выгрузка
            """;

    private final Screen parent;
    private boolean useRussian = false;

    public WardrobeHowToScreen(Screen parent) {
        super(Component.literal("How to?"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonY = height - 28;
        addRenderableWidget(Button.builder(Component.literal("EN"), button -> {
            useRussian = false;
        }).bounds(width / 2 - 64, buttonY, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("RU"), button -> {
            useRussian = true;
        }).bounds(width / 2 - 30, buttonY, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> minecraft.setScreen(parent))
                .bounds(width / 2 + 8, buttonY, 60, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.drawString(font, title, PADDING, PADDING - 2, 0xFFFFFFFF, false);

        String text = useRussian ? RU_TEXT : EN_TEXT;
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width - PADDING * 2);
        int y = PADDING + 12;
        int lineHeight = font.lineHeight + 1;
        for (FormattedCharSequence line : lines) {
            if (y > height - 40) {
                break;
            }
            guiGraphics.drawString(font, line, PADDING, y, 0xFFE6E6E6, false);
            y += lineHeight;
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
