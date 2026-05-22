package com.example.fakeworld.client;

import com.example.fakeworld.StalkerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class StalkerRenderer extends GeoEntityRenderer<StalkerEntity> {

    public StalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new StalkerModel());
    }
}
