package com.example.fakeworld;

import net.minecraftforge.common.ForgeConfigSpec;

public class FakeworldConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue AMBIENT_DIRECTOR_ENABLED;
    public static final ForgeConfigSpec.BooleanValue AMBIENT_DEBUG_MESSAGES;

    public static final ForgeConfigSpec.IntValue BLEEDING_TREE_CHANCE_PERCENT;
    public static final ForgeConfigSpec.IntValue AMBIENT_EVENT_MIN_DELAY_SECONDS;
    public static final ForgeConfigSpec.IntValue AMBIENT_EVENT_MAX_DELAY_SECONDS;
    public static final ForgeConfigSpec.IntValue AMBIENT_QUIET_WEIGHT;

    public static final ForgeConfigSpec.IntValue BELONGING_MESSAGE_WEIGHT;
    public static final ForgeConfigSpec.IntValue FAKE_ADVANCEMENT_WEIGHT;
    public static final ForgeConfigSpec.IntValue JOURNAL_HOUSE_WEIGHT;
    public static final ForgeConfigSpec.IntValue STALKER_WEIGHT;
    public static final ForgeConfigSpec.IntValue DARKNESS_WEIGHT;
    public static final ForgeConfigSpec.IntValue INVENTORY_SHUFFLE_WEIGHT;
    public static final ForgeConfigSpec.IntValue FOOTSTEPS_WEIGHT;
    public static final ForgeConfigSpec.IntValue FAKE_JOIN_WEIGHT;
    public static final ForgeConfigSpec.IntValue PHANTOM_MINING_WEIGHT;
    public static final ForgeConfigSpec.IntValue SKY_OBJECT_WEIGHT;
    public static final ForgeConfigSpec.IntValue ANIMAL_ATTENTION_WEIGHT;
    public static final ForgeConfigSpec.IntValue ANIMAL_DOPPELGANGER_WEIGHT;
    public static final ForgeConfigSpec.IntValue TAME_DOG_WEIGHT;
    public static final ForgeConfigSpec.IntValue MIMIC_VILLAGER_WEIGHT;
    public static final ForgeConfigSpec.IntValue CREEPY_VILLAGE_WEIGHT;
    public static final ForgeConfigSpec.IntValue ABANDONED_HOME_SIGN_WEIGHT;

    public static final ForgeConfigSpec.IntValue CASTLE_STRUCTURE_WEIGHT;
    public static final ForgeConfigSpec.IntValue CAMPSITE_STRUCTURE_WEIGHT;
    public static final ForgeConfigSpec.IntValue EMPTY_STRUCTURE_WEIGHT;
    public static final ForgeConfigSpec.IntValue TOWER_STRUCTURE_WEIGHT;

    public static final ForgeConfigSpec.IntValue BELONGING_MESSAGE_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue FAKE_ADVANCEMENT_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue JOURNAL_HOUSE_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue STALKER_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue DARKNESS_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue INVENTORY_SHUFFLE_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue FOOTSTEPS_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue FAKE_JOIN_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue PHANTOM_MINING_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue SKY_OBJECT_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue ANIMAL_ATTENTION_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue ANIMAL_DOPPELGANGER_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue TAME_DOG_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue MIMIC_VILLAGER_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue CREEPY_VILLAGE_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue ABANDONED_HOME_SIGN_COOLDOWN_MINUTES;

    public static final ForgeConfigSpec.IntValue CASTLE_STRUCTURE_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue CAMPSITE_STRUCTURE_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue EMPTY_STRUCTURE_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue TOWER_STRUCTURE_COOLDOWN_MINUTES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("General settings").push("general");
        AMBIENT_DIRECTOR_ENABLED = builder.comment("Enable the ambient event director.").define("ambientDirectorEnabled", true);
        AMBIENT_DEBUG_MESSAGES   = builder.comment("Show debug messages for ambient events.").define("ambientDebugMessages", false);
        builder.pop();

        builder.comment("Timing").push("timing");
        BLEEDING_TREE_CHANCE_PERCENT      = builder.defineInRange("bleedingTreeChancePercent",      10,   0, 100);
        AMBIENT_EVENT_MIN_DELAY_SECONDS   = builder.defineInRange("ambientEventMinDelaySeconds",    45,  30, 3600);
        AMBIENT_EVENT_MAX_DELAY_SECONDS   = builder.defineInRange("ambientEventMaxDelaySeconds",   140,  30, 7200);
        AMBIENT_QUIET_WEIGHT              = builder.defineInRange("ambientQuietWeight",            160,   0, 300);
        builder.pop();

        builder.comment("Event weights").push("weights");
        BELONGING_MESSAGE_WEIGHT     = builder.defineInRange("belongingMessageWeight",    120, 0, 300);
        FAKE_ADVANCEMENT_WEIGHT      = builder.defineInRange("fakeAdvancementWeight",      35, 0, 300);
        JOURNAL_HOUSE_WEIGHT         = builder.defineInRange("journalHouseWeight",         30, 0, 300);
        STALKER_WEIGHT               = builder.defineInRange("stalkerWeight",               50, 0, 300);
        DARKNESS_WEIGHT              = builder.defineInRange("darknessWeight",              60, 0, 300);
        INVENTORY_SHUFFLE_WEIGHT     = builder.defineInRange("inventoryShuffleWeight",      40, 0, 300);
        FOOTSTEPS_WEIGHT             = builder.defineInRange("footstepsWeight",             60, 0, 300);
        FAKE_JOIN_WEIGHT             = builder.defineInRange("fakeJoinWeight",              25, 0, 300);
        PHANTOM_MINING_WEIGHT        = builder.defineInRange("phantomMiningWeight",         35, 0, 300);
        SKY_OBJECT_WEIGHT            = builder.defineInRange("skyObjectWeight",             24, 0, 300);
        ANIMAL_ATTENTION_WEIGHT      = builder.defineInRange("animalAttentionWeight",       50, 0, 300);
        ANIMAL_DOPPELGANGER_WEIGHT   = builder.defineInRange("animalDoppelgangerWeight",    30, 0, 300);
        TAME_DOG_WEIGHT              = builder.defineInRange("tameDogWeight",               25, 0, 300);
        MIMIC_VILLAGER_WEIGHT        = builder.defineInRange("mimicVillagerWeight",         35, 0, 300);
        CREEPY_VILLAGE_WEIGHT        = builder.defineInRange("creepyVillageWeight",         18, 0, 300);
        ABANDONED_HOME_SIGN_WEIGHT   = builder.defineInRange("abandonedHomeSignWeight",     14, 0, 300);
        CASTLE_STRUCTURE_WEIGHT      = builder.defineInRange("castleStructureWeight",       10, 0, 300);
        CAMPSITE_STRUCTURE_WEIGHT    = builder.defineInRange("campsiteStructureWeight",     60, 0, 300);
        EMPTY_STRUCTURE_WEIGHT       = builder.defineInRange("emptyStructureWeight",        60, 0, 300);
        TOWER_STRUCTURE_WEIGHT       = builder.defineInRange("towerStructureWeight",       100, 0, 300);
        builder.pop();

        builder.comment("Cooldowns (minutes)").push("cooldowns");
        BELONGING_MESSAGE_COOLDOWN_MINUTES   = builder.defineInRange("belongingMessageCooldownMinutes",   20, 0, 240);
        FAKE_ADVANCEMENT_COOLDOWN_MINUTES    = builder.defineInRange("fakeAdvancementCooldownMinutes",     7, 0, 240);
        JOURNAL_HOUSE_COOLDOWN_MINUTES       = builder.defineInRange("journalHouseCooldownMinutes",       20, 0, 240);
        STALKER_COOLDOWN_MINUTES             = builder.defineInRange("stalkerCooldownMinutes",             5, 0, 240);
        DARKNESS_COOLDOWN_MINUTES            = builder.defineInRange("darknessCooldownMinutes",            6, 0, 240);
        INVENTORY_SHUFFLE_COOLDOWN_MINUTES   = builder.defineInRange("inventoryShuffleCooldownMinutes",    6, 0, 240);
        FOOTSTEPS_COOLDOWN_MINUTES           = builder.defineInRange("footstepsCooldownMinutes",           4, 0, 240);
        FAKE_JOIN_COOLDOWN_MINUTES           = builder.defineInRange("fakeJoinCooldownMinutes",           14, 0, 240);
        PHANTOM_MINING_COOLDOWN_MINUTES      = builder.defineInRange("phantomMiningCooldownMinutes",       8, 0, 240);
        SKY_OBJECT_COOLDOWN_MINUTES          = builder.defineInRange("skyObjectCooldownMinutes",          16, 0, 240);
        ANIMAL_ATTENTION_COOLDOWN_MINUTES    = builder.defineInRange("animalAttentionCooldownMinutes",     5, 0, 240);
        ANIMAL_DOPPELGANGER_COOLDOWN_MINUTES = builder.defineInRange("animalDoppelgangerCooldownMinutes", 10, 0, 240);
        TAME_DOG_COOLDOWN_MINUTES            = builder.defineInRange("tameDogCooldownMinutes",            12, 0, 240);
        MIMIC_VILLAGER_COOLDOWN_MINUTES      = builder.defineInRange("mimicVillagerCooldownMinutes",       9, 0, 240);
        CREEPY_VILLAGE_COOLDOWN_MINUTES      = builder.defineInRange("creepyVillageCooldownMinutes",      25, 0, 240);
        ABANDONED_HOME_SIGN_COOLDOWN_MINUTES = builder.defineInRange("abandonedHomeSignCooldownMinutes",  12, 0, 240);
        CASTLE_STRUCTURE_COOLDOWN_MINUTES    = builder.defineInRange("castleStructureCooldownMinutes",    30, 0, 240);
        CAMPSITE_STRUCTURE_COOLDOWN_MINUTES  = builder.defineInRange("campsiteStructureCooldownMinutes",   8, 0, 240);
        EMPTY_STRUCTURE_COOLDOWN_MINUTES     = builder.defineInRange("emptyStructureCooldownMinutes",      8, 0, 240);
        TOWER_STRUCTURE_COOLDOWN_MINUTES     = builder.defineInRange("towerStructureCooldownMinutes",      5, 0, 240);
        builder.pop();

        SPEC = builder.build();
    }

    public static int cooldownTicks(ForgeConfigSpec.IntValue configValue) {
        return configValue.get() * 60 * 20;
    }
}
