package com.example.fakeworld.client.mixin;

import com.example.fakeworld.client.FakeworldClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class FogMixin {

	@Inject(method = "setupFog", at = @At("RETURN"))
	private static void injectFakeworldFog(CallbackInfo ci) {
		if (!FakeworldClient.inFakeOverworld) return;

		// Make fog feel closer to vanilla: start farther away and only slightly
		// tighten based on darkness, without forcing a custom fog color.
		float darkness = FakeworldClient.currentDarkness;

		// Vanilla-ish base distances; darker caves pull these in a bit.
		float baseStart = 40.0f;
		float baseEnd = 160.0f;
		float fogStart = baseStart - darkness * 10.0f;  // 30–40 blocks
		float fogEnd   = baseEnd - darkness * 40.0f;    // 120–160 blocks

		RenderSystem.setShaderFogStart(fogStart);
		RenderSystem.setShaderFogEnd(fogEnd);
		// Do not override fog color; let vanilla biome/sky color handle it.
	}
}
