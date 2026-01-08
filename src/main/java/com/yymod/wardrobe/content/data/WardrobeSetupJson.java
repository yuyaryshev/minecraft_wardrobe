package com.yymod.wardrobe.content.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class WardrobeSetupJson {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WardrobeSetupJson() {
    }

    public static String toJson(WardrobeSetup setup) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "setup");
        root.add("setup", setupToJson(setup));
        return GSON.toJson(root);
    }

    public static String toJsonAll(List<WardrobeSetup> setups) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "all_setups");
        JsonArray array = new JsonArray();
        for (WardrobeSetup setup : setups) {
            array.add(setupToJson(setup));
        }
        root.add("setups", array);
        return GSON.toJson(root);
    }

    public static boolean applyJson(String json, List<WardrobeSetup> setups, int activeIndex) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("setups")) {
            JsonArray array = root.getAsJsonArray("setups");
            for (int i = 0; i < setups.size(); i++) {
                if (i < array.size() && array.get(i).isJsonObject()) {
                    applyToSetup(setups.get(i), array.get(i).getAsJsonObject());
                }
            }
            return true;
        }
        JsonObject setupObj;
        if (root.has("setup")) {
            setupObj = root.getAsJsonObject("setup");
        } else if (root.has("slots")) {
            setupObj = root;
        } else {
            return false;
        }
        if (activeIndex >= 0 && activeIndex < setups.size()) {
            applyToSetup(setups.get(activeIndex), setupObj);
            return true;
        }
        return false;
    }

    private static JsonObject setupToJson(WardrobeSetup setup) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", setup.getName());
        obj.addProperty("icon", stackToString(setup.getIcon()));
        JsonArray slots = new JsonArray();
        for (int i = 0; i < WardrobeSlotConfig.SLOT_COUNT; i++) {
            WardrobeSlotConfig config = setup.getSlot(i);
            JsonObject slotObj = new JsonObject();
            slotObj.addProperty("boundItem", stackToString(config.getBoundItem()));
            slotObj.addProperty("mode", config.getMode().name());
            slotObj.addProperty("min", config.getMinCount());
            slotObj.addProperty("max", config.getMaxCount());
            slotObj.addProperty("matchMode", config.getMatchMode().name());
            slotObj.addProperty("matchTag", config.getMatchTagId());
            slotObj.addProperty("equipmentSlot", config.isEquipmentSlot());
            slots.add(slotObj);
        }
        obj.add("slots", slots);
        return obj;
    }

    private static void applyToSetup(WardrobeSetup setup, JsonObject obj) {
        if (obj.has("name")) {
            setup.setName(obj.get("name").getAsString());
        }
        if (obj.has("icon")) {
            setup.setIcon(stackFromString(obj.get("icon").getAsString()));
        }
        if (!obj.has("slots")) {
            return;
        }
        JsonArray slots = obj.getAsJsonArray("slots");
        for (int i = 0; i < WardrobeSlotConfig.SLOT_COUNT; i++) {
            WardrobeSlotConfig config = setup.getSlot(i);
            if (i < slots.size() && slots.get(i).isJsonObject()) {
                JsonObject slotObj = slots.get(i).getAsJsonObject();
                ItemStack bound = stackFromString(getString(slotObj, "boundItem"));
                if (bound.isEmpty()) {
                    config.clear();
                } else {
                    config.setBoundItem(bound);
                }
                config.setMode(WardrobeSlotMode.valueOf(getString(slotObj, "mode", WardrobeSlotMode.BOTH.name())));
                config.setMinCount(getInt(slotObj, "min", 0));
                config.setMaxCount(getInt(slotObj, "max", 0));
                config.setMatchMode(WardrobeMatchMode.valueOf(getString(slotObj, "matchMode", WardrobeMatchMode.NORMAL.name())));
                config.setMatchTagId(getString(slotObj, "matchTag"));
                config.setEquipmentSlot(getBoolean(slotObj, "equipmentSlot", false));
            } else {
                config.clear();
            }
        }
        setup.enforceEquipmentDefaults();
    }

    private static String stackToString(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag tag = new CompoundTag();
        stack.save(tag);
        return tag.toString();
    }

    private static ItemStack stackFromString(String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            CompoundTag tag = TagParser.parseTag(snbt);
            return ItemStack.of(tag);
        } catch (Exception ex) {
            throw new JsonParseException("Invalid item data", ex);
        }
    }

    private static String getString(JsonObject obj, String key) {
        return getString(obj, key, "");
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        return element.getAsString();
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        return element.getAsInt();
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        return element.getAsBoolean();
    }
}
