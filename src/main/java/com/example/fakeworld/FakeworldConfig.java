package com.example.fakeworld;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class FakeworldConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "fakeworld.json";

    // General
    public boolean ambientDirectorEnabled = true;
    public boolean ambientDebugMessages = false;

    // Timing
    public int bleedingTreeChancePercent = 10;
    public int ambientEventMinDelaySeconds = 45;
    public int ambientEventMaxDelaySeconds = 140;
    public int ambientQuietWeight = 160;

    // Event weights
    public int belongingMessageWeight = 120;
    public int fakeAdvancementWeight = 35;
    public int journalHouseWeight = 30;
    public int stalkerWeight = 50;
    public int darknessWeight = 60;
    public int inventoryShuffleWeight = 40;
    public int footstepsWeight = 60;
    public int fakeJoinWeight = 25;
    public int phantomMiningWeight = 35;
    public int skyObjectWeight = 24;
    public int animalAttentionWeight = 50;
    public int animalDoppelgangerWeight = 30;
    public int tameDogWeight = 25;
    public int mimicVillagerWeight = 35;
    public int creepyVillageWeight = 18;
    public int abandonedHomeSignWeight = 14;
    public int castleStructureWeight = 10;
    public int campsiteStructureWeight = 60;
    public int emptyStructureWeight = 60;
    public int towerStructureWeight = 100;
    public int soundeventWeight = 20;

    // Cooldowns (minutes)
    public int belongingMessageCooldownMinutes = 20;
    public int fakeAdvancementCooldownMinutes = 7;
    public int journalHouseCooldownMinutes = 20;
    public int stalkerCooldownMinutes = 5;
    public int darknessCooldownMinutes = 6;
    public int inventoryShuffleCooldownMinutes = 6;
    public int footstepsCooldownMinutes = 4;
    public int fakeJoinCooldownMinutes = 14;
    public int phantomMiningCooldownMinutes = 8;
    public int skyObjectCooldownMinutes = 16;
    public int animalAttentionCooldownMinutes = 5;
    public int animalDoppelgangerCooldownMinutes = 10;
    public int tameDogCooldownMinutes = 12;
    public int mimicVillagerCooldownMinutes = 9;
    public int creepyVillageCooldownMinutes = 25;
    public int abandonedHomeSignCooldownMinutes = 12;
    public int castleStructureCooldownMinutes = 30;
    public int campsiteStructureCooldownMinutes = 8;
    public int emptyStructureCooldownMinutes = 8;
    public int towerStructureCooldownMinutes = 5;
    public int soundeventCooldownMinutes = 10;

    public static FakeworldConfig load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                FakeworldConfig loaded = GSON.fromJson(reader, FakeworldConfig.class);
                if (loaded != null) {
                    loaded.save(configPath);
                    return loaded;
                }
            } catch (IOException e) {
                Fakeworld.LOGGER.error("Failed to load fakeworld config, using defaults", e);
            }
        }
        FakeworldConfig defaults = new FakeworldConfig();
        defaults.save(configPath);
        return defaults;
    }

    private void save(Path path) {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            Fakeworld.LOGGER.error("Failed to save fakeworld config", e);
        }
    }
}
