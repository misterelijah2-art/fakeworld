package com.example.fakeworld.client;

import com.example.fakeworld.Fakeworld;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class FakeworldClient implements ClientModInitializer {

    private static final ResourceLocation DARKNESS_SHADER =
            new ResourceLocation(Fakeworld.MOD_ID, "shaders/post/darkness.json");

    private boolean shaderLoaded = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                unloadShader(client);
                return;
            }

            boolean inFakeOverworld = client.level.dimension()
                    .equals(Fakeworld.FAKE_OVERWORLD);

            if (inFakeOverworld && !shaderLoaded) {
                client.gameRenderer.loadEffect(DARKNESS_SHADER);
                shaderLoaded = true;
            } else if (!inFakeOverworld && shaderLoaded) {
                unloadShader(client);
            }
        });
    }

    private void unloadShader(Minecraft client) {
        if (shaderLoaded) {
            client.gameRenderer.shutdownEffect();
            shaderLoaded = false;
        }
    }
}
