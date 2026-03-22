package io.github.thebusybiscuit.exoticgarden;

import org.bukkit.potion.PotionEffectType;

public class VersionedPotionEffectType {
    public static final PotionEffectType DAMAGE_RESISTANCE = get("DAMAGE_RESISTANCE", "RESISTANCE");
    public static final PotionEffectType INCREASE_DAMAGE = get("INCREASE_DAMAGE", "STRENGTH");
    public static final PotionEffectType HEAL = get("HEAL", "INSTANT_HEAL");
    public static final PotionEffectType CONFUSION = get("CONFUSION", "NAUSEA");

    public static PotionEffectType get(String... names) {
        for (PotionEffectType type : PotionEffectType.values()) {
            for (String name : names) {
                if (type.getName().equalsIgnoreCase(name)) {
                    return type;
                }
            }
        }

        return null;
    }
}
