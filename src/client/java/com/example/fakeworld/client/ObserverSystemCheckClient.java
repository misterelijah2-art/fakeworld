package com.example.fakeworld.client;

import com.example.fakeworld.Fakeworld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ObserverSystemCheckClient {

    // ~3 seconds at 20 TPS
    private static final int TOTAL_DURATION_TICKS = 60;
    // First 0.5 seconds are the hard "freeze"
    private static final int FREEZE_DURATION_TICKS = 10;

    private static boolean active = false;
    private static int timer = 0;

    private static boolean previousHitboxState = false;
    private static int glitchEntityId = -1;
    private static int glitchTicks = 0;

    public static void init() {
        // Tick handler – runs effect while active
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active) {
                return;
            }
            tick(client);
        });

        // HUD overlay – grid + fake OBS text
        HudRenderCallback.EVENT.register(ObserverSystemCheckClient::renderOverlay);
    }

    public static void startCheck() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }

        // Only run inside fakeworld:fake_overworld
        if (!client.level.dimension().location().equals(Fakeworld.FAKE_OVERWORLD.location())) {
            return;
        }

        if (active) {
            // If already running, just refresh duration
            timer = TOTAL_DURATION_TICKS;
            return;
        }

        active = true;
        timer = TOTAL_DURATION_TICKS;

        // Remember and override hitbox debug state
        previousHitboxState = client.getEntityRenderDispatcher().shouldRenderHitBoxes();
        client.getEntityRenderDispatcher().setRenderHitBoxes(true);

        LocalPlayer player = client.player;

        // Scan sound: high‑pitched bell
        client.level.playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.NOTE_BLOCK_BELL.value(),
                SoundSource.AMBIENT,
                0.7F,
                2.0F,
                false
        );

        // Pick a nearby entity to glitch
        List<Entity> nearby = client.level.getEntities(
                player,
                new AABB(player.blockPosition()).inflate(8.0D)
        );
        if (!nearby.isEmpty()) {
            Entity e = nearby.get(client.level.random.nextInt(nearby.size()));
            glitchEntityId = e.getId();
            glitchTicks = 20; // ~1 second of weird pose
            e.setYRot(e.getYRot() + 90.0F);
        } else {
            glitchEntityId = -1;
            glitchTicks = 0;
        }
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            stop(client);
            return;
        }

        // If we somehow left the dimension, cancel
        if (!client.level.dimension().location().equals(Fakeworld.FAKE_OVERWORLD.location())) {
            stop(client);
            return;
        }

        // First 0.5 seconds: freeze movement
        if (timer >= TOTAL_DURATION_TICKS - FREEZE_DURATION_TICKS) {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
        }

        // Handle glitch entity lifetime
        if (glitchTicks > 0) {
            glitchTicks--;
            if (glitchTicks == 0 && glitchEntityId != -1) {
                Entity e = client.level.getEntity(glitchEntityId);
                if (e != null) {
                    e.setYRot(e.getYRot() + 90.0F); // snap again; server will correct later
                }
                glitchEntityId = -1;
            }
        }

        timer--;
        if (timer <= 0) {
            stop(client);
        }
    }

    private static void stop(Minecraft client) {
        if (!active) {
            return;
        }

        active = false;
        timer = 0;
        glitchEntityId = -1;
        glitchTicks = 0;

        // Restore hitbox debug state
        client.getEntityRenderDispatcher().setRenderHitBoxes(previousHitboxState);
    }

    private static void renderOverlay(GuiGraphics guiGraphics, float tickDelta) {
        if (!active) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();

        // ----- Grid overlay -----
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        int spacing = 16;
        int gridColor = 0x40FFFFFF; // ARGB – ~25% white

        for (int x = 0; x < width; x += spacing) {
            guiGraphics.fill(x, 0, x + 1, height, gridColor);
        }
        for (int y = 0; y < height; y += spacing) {
            guiGraphics.fill(0, y, width, y + 1, gridColor);
        }

        RenderSystem.disableBlend();

        // ----- Fake OBS console text -----
        int margin = 4;
        int lineHeight = client.font.lineHeight + 2;

        guiGraphics.drawString(
                client.font,
                Component.literal("OBS[INFO]: SUBJECT VITALS: STABLE"),
                margin,
                margin,
                0x6BFF8A,
                false
        );

        guiGraphics.drawString(
                client.font,
                Component.literal("OBS[WARN]: INTRUSIVE THOUGHTS DETECTED"),
                margin,
                margin + lineHeight,
                0xFFE15E,
                false
        );
    }
}