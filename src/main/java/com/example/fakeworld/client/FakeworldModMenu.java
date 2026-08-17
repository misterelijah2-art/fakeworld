package com.example.fakeworld.client;

import com.example.fakeworld.Fakeworld;
import com.example.fakeworld.FakeworldConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FakeworldModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return this::buildConfigScreen;
    }

    private Screen buildConfigScreen(Screen parent) {
        FakeworldConfig cfg = Fakeworld.CONFIG;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Fakeworld Config"))
                .setSavingRunnable(() -> {
                    // Config is already mutated directly; save by reloading from fields.
                    // Re-save to disk by constructing a fresh load call is not needed here
                    // because Cloth Config mutates the existing CONFIG instance.
                });

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ── General ──────────────────────────────────────────────────────────
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        general.addEntry(eb.startBooleanToggle(Component.literal("Fakeworld Enabled"), cfg.fakeworldEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> cfg.fakeworldEnabled = v)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Ambient Director Enabled"), cfg.ambientDirectorEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> cfg.ambientDirectorEnabled = v)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Ambient Debug Messages"), cfg.ambientDebugMessages)
                .setDefaultValue(false)
                .setSaveConsumer(v -> cfg.ambientDebugMessages = v)
                .build());

        // ── Timing ───────────────────────────────────────────────────────────
        ConfigCategory timing = builder.getOrCreateCategory(Component.literal("Timing"));

        timing.addEntry(eb.startIntSlider(Component.literal("Bleeding Tree Chance %"), cfg.bleedingTreeChancePercent, 0, 100)
                .setDefaultValue(10)
                .setSaveConsumer(v -> cfg.bleedingTreeChancePercent = v)
                .build());

        timing.addEntry(eb.startIntField(Component.literal("Ambient Event Min Delay (seconds)"), cfg.ambientEventMinDelaySeconds)
                .setDefaultValue(45)
                .setSaveConsumer(v -> cfg.ambientEventMinDelaySeconds = v)
                .build());

        timing.addEntry(eb.startIntField(Component.literal("Ambient Event Max Delay (seconds)"), cfg.ambientEventMaxDelaySeconds)
                .setDefaultValue(140)
                .setSaveConsumer(v -> cfg.ambientEventMaxDelaySeconds = v)
                .build());

        timing.addEntry(eb.startIntSlider(Component.literal("Quiet Weight"), cfg.ambientQuietWeight, 0, 300)
                .setDefaultValue(160)
                .setSaveConsumer(v -> cfg.ambientQuietWeight = v)
                .build());

        // ── Event Weights ────────────────────────────────────────────────────
        ConfigCategory weights = builder.getOrCreateCategory(Component.literal("Event Weights"));

        weights.addEntry(eb.startIntSlider(Component.literal("Belonging Message"), cfg.belongingMessageWeight, 0, 300).setDefaultValue(120).setSaveConsumer(v -> cfg.belongingMessageWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Fake Advancement"), cfg.fakeAdvancementWeight, 0, 300).setDefaultValue(35).setSaveConsumer(v -> cfg.fakeAdvancementWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Journal House"), cfg.journalHouseWeight, 0, 300).setDefaultValue(30).setSaveConsumer(v -> cfg.journalHouseWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Stalker"), cfg.stalkerWeight, 0, 300).setDefaultValue(50).setSaveConsumer(v -> cfg.stalkerWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Darkness"), cfg.darknessWeight, 0, 300).setDefaultValue(60).setSaveConsumer(v -> cfg.darknessWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Inventory Shuffle"), cfg.inventoryShuffleWeight, 0, 300).setDefaultValue(40).setSaveConsumer(v -> cfg.inventoryShuffleWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Footsteps"), cfg.footstepsWeight, 0, 300).setDefaultValue(60).setSaveConsumer(v -> cfg.footstepsWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Fake Join"), cfg.fakeJoinWeight, 0, 300).setDefaultValue(25).setSaveConsumer(v -> cfg.fakeJoinWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Phantom Mining"), cfg.phantomMiningWeight, 0, 300).setDefaultValue(35).setSaveConsumer(v -> cfg.phantomMiningWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Sky Object"), cfg.skyObjectWeight, 0, 300).setDefaultValue(24).setSaveConsumer(v -> cfg.skyObjectWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Animal Attention"), cfg.animalAttentionWeight, 0, 300).setDefaultValue(50).setSaveConsumer(v -> cfg.animalAttentionWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Animal Doppelganger"), cfg.animalDoppelgangerWeight, 0, 300).setDefaultValue(30).setSaveConsumer(v -> cfg.animalDoppelgangerWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Tame Dog"), cfg.tameDogWeight, 0, 300).setDefaultValue(25).setSaveConsumer(v -> cfg.tameDogWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Mimic Villager"), cfg.mimicVillagerWeight, 0, 300).setDefaultValue(35).setSaveConsumer(v -> cfg.mimicVillagerWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Creepy Village"), cfg.creepyVillageWeight, 0, 300).setDefaultValue(18).setSaveConsumer(v -> cfg.creepyVillageWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Abandoned Home Sign"), cfg.abandonedHomeSignWeight, 0, 300).setDefaultValue(14).setSaveConsumer(v -> cfg.abandonedHomeSignWeight = v).build());
        weights.addEntry(eb.startIntSlider(Component.literal("Sound Event"), cfg.soundeventWeight, 0, 300).setDefaultValue(15).setSaveConsumer(v -> cfg.soundeventWeight = v).build());

        // ── Cooldowns ────────────────────────────────────────────────────────
        ConfigCategory cooldowns = builder.getOrCreateCategory(Component.literal("Cooldowns (minutes)"));

        cooldowns.addEntry(eb.startIntField(Component.literal("Belonging Message"), cfg.belongingMessageCooldownMinutes).setDefaultValue(20).setSaveConsumer(v -> cfg.belongingMessageCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Fake Advancement"), cfg.fakeAdvancementCooldownMinutes).setDefaultValue(7).setSaveConsumer(v -> cfg.fakeAdvancementCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Journal House"), cfg.journalHouseCooldownMinutes).setDefaultValue(20).setSaveConsumer(v -> cfg.journalHouseCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Stalker"), cfg.stalkerCooldownMinutes).setDefaultValue(5).setSaveConsumer(v -> cfg.stalkerCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Darkness"), cfg.darknessCooldownMinutes).setDefaultValue(6).setSaveConsumer(v -> cfg.darknessCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Inventory Shuffle"), cfg.inventoryShuffleCooldownMinutes).setDefaultValue(6).setSaveConsumer(v -> cfg.inventoryShuffleCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Footsteps"), cfg.footstepsCooldownMinutes).setDefaultValue(4).setSaveConsumer(v -> cfg.footstepsCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Fake Join"), cfg.fakeJoinCooldownMinutes).setDefaultValue(14).setSaveConsumer(v -> cfg.fakeJoinCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Phantom Mining"), cfg.phantomMiningCooldownMinutes).setDefaultValue(8).setSaveConsumer(v -> cfg.phantomMiningCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Sky Object"), cfg.skyObjectCooldownMinutes).setDefaultValue(16).setSaveConsumer(v -> cfg.skyObjectCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Animal Attention"), cfg.animalAttentionCooldownMinutes).setDefaultValue(5).setSaveConsumer(v -> cfg.animalAttentionCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Animal Doppelganger"), cfg.animalDoppelgangerCooldownMinutes).setDefaultValue(10).setSaveConsumer(v -> cfg.animalDoppelgangerCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Tame Dog"), cfg.tameDogCooldownMinutes).setDefaultValue(12).setSaveConsumer(v -> cfg.tameDogCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Mimic Villager"), cfg.mimicVillagerCooldownMinutes).setDefaultValue(9).setSaveConsumer(v -> cfg.mimicVillagerCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Creepy Village"), cfg.creepyVillageCooldownMinutes).setDefaultValue(25).setSaveConsumer(v -> cfg.creepyVillageCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Abandoned Home Sign"), cfg.abandonedHomeSignCooldownMinutes).setDefaultValue(12).setSaveConsumer(v -> cfg.abandonedHomeSignCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Sound Event"), cfg.soundeventCooldownMinutes).setDefaultValue(40).setSaveConsumer(v -> cfg.soundeventCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Castle Structure"), cfg.castleStructureCooldownMinutes).setDefaultValue(30).setSaveConsumer(v -> cfg.castleStructureCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Campsite Structure"), cfg.campsiteStructureCooldownMinutes).setDefaultValue(8).setSaveConsumer(v -> cfg.campsiteStructureCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Empty Structure"), cfg.emptyStructureCooldownMinutes).setDefaultValue(8).setSaveConsumer(v -> cfg.emptyStructureCooldownMinutes = v).build());
        cooldowns.addEntry(eb.startIntField(Component.literal("Tower Structure"), cfg.towerStructureCooldownMinutes).setDefaultValue(5).setSaveConsumer(v -> cfg.towerStructureCooldownMinutes = v).build());

        return builder.build();
    }
}
