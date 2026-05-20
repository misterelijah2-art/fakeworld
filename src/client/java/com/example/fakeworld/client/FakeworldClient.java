package com.example.fakeworld.client;

import com.example.fakeworld.Fakeworld;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class FakeworldClient implements ClientModInitializer {

	private static final ResourceLocation DARKNESS_SHADER =
			new ResourceLocation(Fakeworld.MOD_ID, "shaders/post/darkness.json");

	// Publicly accessible so the fog mixin can read it
	public static float currentDarkness = 0.0f;
	public static boolean inFakeOverworld = false;

	private boolean shaderLoaded = false;

	@Override
	public void onInitializeClient() {
		BlockRenderLayerMap.INSTANCE.putBlock(Fakeworld.BLOOD_SPLATTER, RenderType.cutout());
		EntityRendererRegistry.register(Fakeworld.STALKER, StalkerRenderer::new);
		EntityRendererRegistry.register(Fakeworld.HUNTER, HunterRenderer::new);
		EntityRendererRegistry.register(Fakeworld.MIMIC_VILLAGER, MimicVillagerRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(Fakeworld.DESKTOP_NOTE_PACKET, (client, handler, buf, responseSender) -> {
			String fileName = buf.readUtf();
			String contents = buf.readUtf();
			client.execute(() -> writeDesktopNote(fileName, contents));
		});
		ClientPlayNetworking.registerGlobalReceiver(Fakeworld.OBSERVER_CHECK_PACKET, (client, handler, buf, responseSender) -> {
			client.execute(ObserverSystemCheckClient::startCheck);
		});

		ObserverSystemCheckClient.init();

		// Darkness shader - reacts to block light level
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				inFakeOverworld = false;
				if (shaderLoaded) {
					client.gameRenderer.shutdownEffect();
					shaderLoaded = false;
				}
				return;
			}

			inFakeOverworld = client.level.dimension().equals(Fakeworld.FAKE_OVERWORLD);

			if (inFakeOverworld) {
				if (!shaderLoaded) {
					try {
						client.gameRenderer.loadEffect(DARKNESS_SHADER);
						shaderLoaded = true;
					} catch (Exception e) {
						Fakeworld.LOGGER.warn("Failed to load darkness shader", e);
					}
				}

				// Read block light at player's feet
				BlockPos pos = client.player.blockPosition();
				int blockLight = client.level.getBrightness(LightLayer.BLOCK, pos);
				// 0 light = max darkness (0.75), 15 light = min darkness (0.1)
				float targetDarkness = 0.75f - (blockLight / 15.0f) * 0.65f;
				// Smooth transition
				currentDarkness += (targetDarkness - currentDarkness) * 0.05f;

				// Push to shader uniform
				PostChain effect = client.gameRenderer.currentEffect();
				if (effect != null) {
					effect.getPasses().forEach(pass ->
						pass.getEffect().safeGetUniform("DarknessAmount").set(currentDarkness)
					);
				}
			} else if (shaderLoaded) {
				client.gameRenderer.shutdownEffect();
				shaderLoaded = false;
				currentDarkness = 0.0f;
			}
		});
	}

	private static void writeDesktopNote(String fileName, String contents) {
		try {
			Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
			if (!Files.isDirectory(desktop)) {
				Fakeworld.LOGGER.warn("Could not write fakeworld desktop note because Desktop folder does not exist: {}", desktop);
				return;
			}

			Path output = desktop.resolve(fileName).normalize();
			if (!output.getParent().equals(desktop)) {
				Fakeworld.LOGGER.warn("Blocked fakeworld desktop note with invalid file name: {}", fileName);
				return;
			}

			Files.writeString(output, contents, StandardCharsets.UTF_8);
		} catch (IOException | InvalidPathException | SecurityException exception) {
			Fakeworld.LOGGER.warn("Failed to write fakeworld desktop note.", exception);
		}
	}
}
