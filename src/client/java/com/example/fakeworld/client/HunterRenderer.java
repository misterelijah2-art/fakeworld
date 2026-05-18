package com.example.fakeworld.client;

import com.example.fakeworld.Fakeworld;
import com.example.fakeworld.HunterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class HunterRenderer extends MobRenderer<HunterEntity, HumanoidModel<HunterEntity>> {
	private static final ResourceLocation TEXTURE = new ResourceLocation(Fakeworld.MOD_ID, "textures/entity/stalker.png");

	public HunterRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.45F);
	}

	@Override
	protected boolean shouldShowName(HunterEntity entity) {
		return false;
	}

	@Override
	public ResourceLocation getTextureLocation(HunterEntity entity) {
		return entity.getTargetPlayerUuid()
				.map(uuid -> Minecraft.getInstance().getConnection() == null ? null : Minecraft.getInstance().getConnection().getPlayerInfo(uuid))
				.map(PlayerInfo::getSkinLocation)
				.orElse(TEXTURE);
	}
}
