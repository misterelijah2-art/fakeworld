package com.example.fakeworld.client;

import com.example.fakeworld.StalkerEntity;
import com.example.fakeworld.Fakeworld;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class StalkerModel extends AnimatedGeoModel<StalkerEntity> {
    @Override
    public ResourceLocation getModelLocation(StalkerEntity object) {
        return new ResourceLocation(Fakeworld.MOD_ID, "geo/stalker.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(StalkerEntity object) {
        return new ResourceLocation(Fakeworld.MOD_ID, "textures/entity/stalker.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(StalkerEntity animatable) {
        return new ResourceLocation(Fakeworld.MOD_ID, "animations/stalker.animation.json");
    }
}

