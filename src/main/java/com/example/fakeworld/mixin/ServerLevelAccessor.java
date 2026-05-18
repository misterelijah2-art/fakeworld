package com.example.fakeworld.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {
	@Invoker("wakeUpAllPlayers")
	void fakeworld$wakeUpAllPlayers();
}
