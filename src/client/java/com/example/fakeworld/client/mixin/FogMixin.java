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

		// Fog density scales with darkness: darker = thicker fog
		float darkness = FakeworldClient.currentDarkness;
		float fogStart = 30.0f - darkness * 20.0f;  // 10-30 blocks
		float fogEnd   = 80.0f - darkness * 50.0f;  // 30-80 blocks

		RenderSystem.setShaderFogStart(fogStart);
		RenderSystem.setShaderFogEnd(fogEnd);
		RenderSystem.setShaderFogColor(0.02f, 0.02f, 0.04f, 1.0f); // near-black dark blue fog
	}
}
