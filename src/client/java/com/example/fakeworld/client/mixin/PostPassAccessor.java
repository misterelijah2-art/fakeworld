package com.example.fakeworld.client.mixin;

import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostPass.class)
public interface PostPassAccessor {
    // In Mojang 1.20.1 mappings the ShaderInstance field on PostPass is named "shader",
    // this accessor exposes it so we can tweak uniforms per-pass.
    @Accessor("shader")
    ShaderInstance getShader();
}
