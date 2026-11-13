package net.bedrocknative.client.mixin.hud;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Hud.class)
public interface HudStateAccessor {
   @Accessor("contextualInfoBar")
   Pair<?, ContextualBar> bedrocknative$contextualInfoBar();

   @Accessor("lastBubblePopSoundPlayed")
   void bedrocknative$setLastBubblePopSoundPlayed(int var1);

   @Invoker("playAirBubblePoppedSound")
   void bedrocknative$playAirBubblePoppedSound(int var1, Player var2, int var3);
}
