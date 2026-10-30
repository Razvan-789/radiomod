package com.radiomod.signal;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** World-wide country names indexed by the first hexadecimal PI or service SID digit. */
public final class RegistryCountries extends SavedData {
    public static final String PREFIXES = "0123456789ABCDEF";
    private static final String DATA_NAME = "radio_registry_countries";
    private final String[] names = new String[16];

    public RegistryCountries() {
        java.util.Arrays.fill(names, "");
    }

    public static RegistryCountries get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RegistryCountries::new, RegistryCountries::load), DATA_NAME);
    }

    public static RegistryCountries load(CompoundTag tag, HolderLookup.Provider registries) {
        RegistryCountries data = new RegistryCountries();
        for (int i = 0; i < 16; i++) data.names[i] = tag.getString("Country" + PREFIXES.charAt(i));
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < 16; i++) tag.putString("Country" + PREFIXES.charAt(i), names[i]);
        return tag;
    }

    public String[] names() { return names.clone(); }

    public String countryFor(String code) {
        if (code == null || code.isBlank()) return "Unknown";
        int index = PREFIXES.indexOf(Character.toUpperCase(code.charAt(0)));
        return index < 0 || names[index].isBlank() ? "Unknown" : names[index];
    }

    public void set(int index, String name) {
        if (index < 0 || index >= 16 || name == null) return;
        String cleaned = name.strip().replaceAll("[\\p{Cntrl}]", "");
        if (cleaned.length() > 40) cleaned = cleaned.substring(0, 40);
        if (!names[index].equals(cleaned)) {
            names[index] = cleaned;
            setDirty();
        }
    }
}
