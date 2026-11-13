package net.bedrocknative.client.content;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import net.bedrocknative.world.block.custom.SlotPlan;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.client.renderer.texture.atlas.SpriteSource.Output;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public record BlockSlotSource() implements SpriteSource {
   public static final Identifier TYPE = Identifier.fromNamespaceAndPath("bedrocknative", "bedrock_slots");
   public static final MapCodec<BlockSlotSource> MAP_CODEC = MapCodec.unit(BlockSlotSource::new);
   static final int[] SIZES = SlotPlan.SIZES;
   private static volatile int[] requested = (int[])SlotPlan.BASE.clone();
   private static volatile int[] stitched = (int[])SlotPlan.BASE.clone();

   public static void register() {
      SpriteSources.ID_MAPPER.put(TYPE, MAP_CODEC);
   }

   static Identifier slot(int size, int index) {
      return Identifier.fromNamespaceAndPath("bedrocknative", "bedrock_slot/" + size + "_" + index);
   }

   static synchronized boolean request(int[] counts) {
      requested = SlotPlan.max(requested, counts);
      return SlotPlan.exceeds(requested, stitched);
   }

   static boolean behind() {
      return SlotPlan.exceeds(requested, stitched);
   }

   static synchronized void reset() {
      requested = (int[])SlotPlan.BASE.clone();
   }

   static int[] requested() {
      return (int[])requested.clone();
   }

   public void run(ResourceManager resourceManager, Output output) {
      int[] counts = requested;
      stitched = counts;

      for (int sizeClass = 0; sizeClass < SIZES.length; sizeClass++) {
         int size = SIZES[sizeClass];

         for (int index = 0; index < counts[sizeClass]; index++) {
            Identifier id = slot(size, index);
            output.add(id, loader -> new SpriteContents(id, new FrameSize(size, size), new NativeImage(size, size, true)));
         }
      }
   }

   public MapCodec<BlockSlotSource> codec() {
      return MAP_CODEC;
   }
}
