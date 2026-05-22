package com.example.fakeworld.client;

import com.example.fakeworld.StalkerEntity;
import com.example.fakeworld.Fakeworld;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class StalkerModel extends GeoModel<StalkerEntity> {

    @Override
    public ResourceLocation getModelResource(StalkerEntity object) {
        return new ResourceLocation(Fakeworld.MOD_ID, "geo/stalker.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(StalkerEntity object) {
        return new ResourceLocation(Fakeworld.MOD_ID, "textures/entity/stalker.png");
    }

    @Override
    public ResourceLocation getAnimationResource(StalkerEntity animatable) {
        return new ResourceLocation(Fakeworld.MOD_ID, "animations/stalker.animation.json");
    }
}
