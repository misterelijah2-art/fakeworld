package com.example.fakeworld.mixin;

import com.example.fakeworld.Fakeworld;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Prevents the fake_overworld dimension from being registered/loaded
 * when fakeworldEnabled = false in fakeworld.json.
 *
 * Side: SERVER
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    /**
     * Intercepts the level map lookup used to access/load dimensions.
     * If the config disables fakeworld and the requested key is FAKE_OVERWORLD,
     * we return null so Minecraft treats it as a missing dimension and never
     * loads or generates it — leaving the vanilla overworld (e.g. superflat) intact.
     */
    @Inject(
            method = "getLevel",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fakeworld$blockFakeOverworldLoad(
            ResourceKey<Level> key,
            CallbackInfoReturnable<ServerLevel> cir
    ) {
        if (!Fakeworld.CONFIG.fakeworldEnabled && Fakeworld.FAKE_OVERWORLD.equals(key)) {
            cir.setReturnValue(null);
        }
    }
}
