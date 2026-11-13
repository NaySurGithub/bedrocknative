package net.bedrocknative.sound;

import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

public final class SoundPlanner {
   private static final int MAX_EVENT_DEPTH = 4;

   private SoundPlanner() {
   }

   public static SoundPlanner.@Nullable Play definition(
      SoundDefinitions definitions, String name, float volume, float pitch, boolean serverEvent, RandomGenerator random
   ) {
      return definition(definitions, name, volume, pitch, serverEvent, random, 0);
   }

   private static SoundPlanner.@Nullable Play definition(
      SoundDefinitions definitions, String name, float volume, float pitch, boolean serverEvent, RandomGenerator random, int depth
   ) {
      SoundDefinitions.Definition definition = definitions.get(name);
      if (definition != null && depth <= 4) {
         SoundDefinitions.Variant variant = definition.pick(random);
         if (variant == null) {
            return null;
         }

         boolean server = serverEvent || definition.serverPack();
         if (variant.event()) {
            return definition(
               definitions,
               variant.path(),
               volume * definition.volume() * variant.volume(),
               pitch * definition.pitch() * variant.pitch(),
               server,
               random,
               depth + 1
            );
         }

         float range = SoundRange.range(definition, volume);
         return new SoundPlanner.Play(
            definition,
            variant,
            volume,
            SoundRange.gain(volume, definition, variant),
            pitch * definition.pitch() * variant.pitch(),
            range,
            SoundRange.start(definition, range),
            SoundRange.positional(definition, variant),
            variant.stream(),
            server
         );
      } else {
         return null;
      }
   }

   public static SoundEventTable.@Nullable Resolved levelSound(
      SoundCatalog catalog, String event, String identifier, @Nullable String blockName, boolean baby, SoundEventTable.VariantKeys keys, RandomGenerator random
   ) {
      return levelSoundOfType(catalog, event, identifier, blockName != null, blockName != null ? catalog.blockSound(blockName) : null, baby, keys, random);
   }

   public static SoundEventTable.@Nullable Resolved levelSoundOfType(
      SoundCatalog catalog,
      String event,
      String identifier,
      boolean aboutBlock,
      @Nullable String blockType,
      boolean baby,
      SoundEventTable.VariantKeys keys,
      RandomGenerator random
   ) {
      SoundEventTable table = catalog.events();
      if (!identifier.isEmpty()) {
         SoundEventTable.Resolved own = baby && !event.endsWith(".baby") ? table.entity(identifier, event + ".baby", null, keys, random) : null;
         if (own == null) {
            own = table.entity(identifier, event, blockType, keys, random);
         }

         if (own != null) {
            return own;
         }
      }

      if (aboutBlock) {
         String type = blockType != null ? blockType : "normal";
         SoundEventTable.Resolved block = table.block(type, event, random);
         if (block != null && !block.silent()) {
            return block;
         }

         SoundEventTable.Resolved interactive = table.interactiveBlock(type, event, random);
         if (interactive != null && !interactive.silent()) {
            return interactive;
         }
      }

      return table.individual(event, random);
   }

   public record Play(
      SoundDefinitions.Definition definition,
      SoundDefinitions.Variant variant,
      float volume,
      float gain,
      float pitch,
      float range,
      float start,
      boolean positional,
      boolean stream,
      boolean serverContent
   ) {
      public String category() {
         return this.definition.category();
      }
   }
}
