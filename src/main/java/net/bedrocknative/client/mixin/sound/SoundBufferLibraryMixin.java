package net.bedrocknative.client.mixin.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.bedrocknative.client.sound.BedrockAudio;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SoundBufferLibrary.class})
public abstract class SoundBufferLibraryMixin implements BedrockAudio.BufferCache {
   @Shadow
   @Final
   private Map<Identifier, CompletableFuture<SoundBuffer>> cache;

   @Inject(
      method = {"getCompleteBuffer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$bedrockBuffer(Identifier location, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
      if (BedrockAudio.owns(location)) {
         BedrockAudio.attach(this);
         cir.setReturnValue(this.cache.computeIfAbsent(location, BedrockAudio::buffer));
      }
   }

   @Inject(
      method = {"getStream"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$bedrockStream(Identifier location, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
      if (BedrockAudio.owns(location)) {
         cir.setReturnValue(BedrockAudio.stream(location, looping));
      }
   }

   @Inject(
      method = {"clear"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$discardReleased(CallbackInfo ci) {
      BedrockAudio.discardPendingNow();
   }

   @Override
   public List<CompletableFuture<SoundBuffer>> bedrocknative$forget(Predicate<Identifier> which) {
      List<CompletableFuture<SoundBuffer>> removed = new ArrayList<>();
      Iterator<Entry<Identifier, CompletableFuture<SoundBuffer>>> entries = this.cache.entrySet().iterator();

      while (entries.hasNext()) {
         Entry<Identifier, CompletableFuture<SoundBuffer>> entry = entries.next();
         if (which.test(entry.getKey())) {
            removed.add(entry.getValue());
            entries.remove();
         }
      }

      return removed;
   }
}
