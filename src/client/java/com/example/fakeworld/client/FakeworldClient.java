package com.example.fakeworld.client;

import com.example.fakeworld.Fakeworld;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class FakeworldClient implements ClientModInitializer {

	private static final ResourceLocation DARKNESS_SHADER =
			new ResourceLocation(Fakeworld.MOD_ID, "shaders/post/darkness.json");
	private static boolean shaderLoaded = false;

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

		// Darkness shader for fake overworld
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				if (shaderLoaded) {
					client.gameRenderer.shutdownEffect();
					shaderLoaded = false;
				}
				return;
			}
			boolean inFakeOverworld = client.level.dimension().equals(Fakeworld.FAKE_OVERWORLD);
			if (inFakeOverworld && !shaderLoaded) {
				client.gameRenderer.loadEffect(DARKNESS_SHADER);
				shaderLoaded = true;
			} else if (!inFakeOverworld && shaderLoaded) {
				client.gameRenderer.shutdownEffect();
				shaderLoaded = false;
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
