package io.github.thebusybiscuit.exoticgarden;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import io.github.thebusybiscuit.slimefun4.libraries.dough.skins.PlayerHead;
import io.github.thebusybiscuit.slimefun4.libraries.dough.skins.PlayerSkin;

public class SkullUtil {
    public static @NotNull ItemStack getByBase64(@NotNull String base64) {
        PlayerSkin skin = PlayerSkin.fromBase64(base64);
        return PlayerHead.getItemStack(skin);
    }

    public static @NotNull ItemStack getByHash(@NotNull String hash) {
        PlayerSkin skin = PlayerSkin.fromHashCode(hash);
        return PlayerHead.getItemStack(skin);
    }

    public static @NotNull ItemStack getByURL(@NotNull String url) {
        PlayerSkin skin = PlayerSkin.fromURL(url);
        return PlayerHead.getItemStack(skin);
    }
}
