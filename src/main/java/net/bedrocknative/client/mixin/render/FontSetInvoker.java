package net.bedrocknative.client.mixin.render;

import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({FontSet.class})
public interface FontSetInvoker {
   @Invoker("resetTextures")
   void bedrocknative$resetTextures();
}
