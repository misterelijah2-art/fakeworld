package com.example.fakeworld.client;

import com.example.fakeworld.Fakeworld;
import com.example.fakeworld.client.mixin.GameRendererInvoker;
import com.example.fakeworld.client.mixin.PostChainAccessor;
import com.example.fakeworld.client.mixin.PostPassAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

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

		// Darkness shader that only applies in dark caves in the fake overworld
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				inFakeOverworld = false;
				if (shaderLoaded) {
					client.gameRenderer.shutdownEffect();
					shaderLoaded = false;
					currentDarkness = 0.0f;
				}
				return;
			}

			inFakeOverworld = client.level.dimension().equals(Fakeworld.FAKE_OVERWORLD);

			// Only enable shader when the player is in a "dark cave": block light <= 3
			boolean inDarkCave = false;
			if (inFakeOverworld) {
				BlockPos pos = client.player.blockPosition();
				int blockLight = client.level.getBrightness(LightLayer.BLOCK, pos);
				inDarkCave = blockLight <= 3;
			}

			if (inFakeOverworld && inDarkCave) {
				if (!shaderLoaded) {
					try {
						((GameRendererInvoker) client.gameRenderer).invokeLoadEffect(DARKNESS_SHADER);
						shaderLoaded = true;
					} catch (Exception e) {
						Fakeworld.LOGGER.warn("Failed to load darkness shader", e);
					}
				}

				// Drive darkness amount just from cave-ness: caves are always fairly dark
				float targetDarkness = 0.75f;
				currentDarkness += (targetDarkness - currentDarkness) * 0.1f;

				// Push to shader uniform via accessor mixins
				PostChain effect = client.gameRenderer.currentEffect();
				if (effect != null) {
					List<PostPass> passes = ((PostChainAccessor) effect).getPasses();
					for (PostPass pass : passes) {
						ShaderInstance shader = ((PostPassAccessor) pass).getShader();
						if (shader != null && shader.safeGetUniform("DarknessAmount") != null) {
							shader.safeGetUniform("DarknessAmount").set(currentDarkness);
						}
					}
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
