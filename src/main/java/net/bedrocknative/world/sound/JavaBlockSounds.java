package net.bedrocknative.world.sound;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;
import net.bedrocknative.sound.SoundCatalog;
import net.bedrocknative.sound.SoundEventTable;
import net.bedrocknative.sound.SoundPlanner;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class JavaBlockSounds {
   private static final String PLAYER = "minecraft:player";
   private static final Map<String, String> RENAMED = Map.of("wool", "cloth", "slime_block", "slime", "nether_bricks", "nether_brick", "coral_block", "coral");
   private static volatile @Nullable Map<Identifier, JavaBlockSounds.BlockSound> index;
   private static volatile @Nullable Map<String, SoundType> javaTypes;

   private JavaBlockSounds() {
   }

   public static JavaBlockSounds.@Nullable BlockSound of(Identifier sound) {
      return index().get(sound);
   }

   public static SoundEventTable.@Nullable Resolved resolve(SoundCatalog catalog, JavaBlockSounds.Action action, String bedrockBlock, RandomGenerator random) {
      return resolve(catalog, action, bedrockBlock, null, random);
   }

   public static SoundEventTable.@Nullable Resolved resolve(
      SoundCatalog catalog, JavaBlockSounds.Action action, String bedrockBlock, @Nullable CustomBlockState custom, RandomGenerator random
   ) {
      String type = custom != null ? customType(catalog, custom) : bedrockType(catalog, bedrockBlock);
      return SoundPlanner.levelSoundOfType(
         catalog, action.event(), action.underfoot() ? "minecraft:player" : "", true, type, false, SoundEventTable.VariantKeys.NONE, random
      );
   }

   public static @Nullable String customType(SoundCatalog catalog, CustomBlockState custom) {
      return custom.soundType() != null ? custom.soundType() : catalog.blockSound(custom.state().name());
   }

   public static @Nullable String bedrockType(SoundCatalog catalog, String bedrockBlock) {
      String type = catalog.blockSound(bedrockBlock);
      if (type != null) {
         return type;
      }

      BlockState state = BlockMappings.get().byName(bedrockBlock);
      return state == null ? null : typeOf(catalog, state.getSoundType());
   }

   private static @Nullable String typeOf(SoundCatalog catalog, SoundType sound) {
      String type = bedrockName(sound);
      return type != null && catalog.events().hasBlockType(type) ? type : null;
   }

   static @Nullable String bedrockName(SoundType sound) {
      String step = sound.getStepSound().location().getPath();
      if (step.startsWith("block.") && step.endsWith(".step")) {
         String java = step.substring("block.".length(), step.length() - ".step".length());
         return RENAMED.getOrDefault(java, java);
      } else {
         return null;
      }
   }

   public static SoundType javaType(@Nullable String bedrockType) {
      Map<String, SoundType> types = javaTypes;
      if (types == null) {
         Map<String, SoundType> built = new HashMap<>();

         for (Field field : SoundType.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == SoundType.class) {
               try {
                  SoundType type = (SoundType)field.get(null);
                  String name = bedrockName(type);
                  String breakSound = type.getBreakSound().location().getPath();
                  String java = name == null
                     ? null
                     : (
                        breakSound.startsWith("block.") && breakSound.endsWith(".break")
                           ? breakSound.substring("block.".length(), breakSound.length() - ".break".length())
                           : null
                     );
                  if (name != null && java != null && RENAMED.getOrDefault(java, java).equals(name)) {
                     built.putIfAbsent(name, type);
                  }
               } catch (IllegalAccessException | RuntimeException var11) {
               }
            }
         }

         javaTypes = types = Map.copyOf(built);
      }

      SoundType type = bedrockType == null ? null : types.get(bedrockType.toLowerCase(Locale.ROOT));
      return type != null ? type : SoundType.STONE;
   }

   private static Map<Identifier, JavaBlockSounds.BlockSound> index() {
      Map<Identifier, JavaBlockSounds.BlockSound> map = index;
      if (map == null) {
         Map<Identifier, JavaBlockSounds.BlockSound> built = new HashMap<>();
         Identifier empty = SoundEvents.EMPTY.location();

         for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (!state.isAir() && !state.liquid() && !(block instanceof CarrierBlock)) {
               SoundType type = state.getSoundType();

               for (JavaBlockSounds.Action action : JavaBlockSounds.Action.values()) {
                  Identifier sound = action.of(type).location();
                  if (!sound.equals(empty)) {
                     built.putIfAbsent(sound, new JavaBlockSounds.BlockSound(action, state));
                  }
               }
            }
         }

         index = map = Map.copyOf(built);
      }

      return map;
   }

   public enum Action {
      STEP("step", true),
      PLACE("place", false),
      BREAK("break", false),
      HIT("hit", false),
      FALL("fall", true);

      private final String event;
      private final boolean underfoot;

      Action(String event, boolean underfoot) {
         this.event = event;
         this.underfoot = underfoot;
      }

      public String event() {
         return this.event;
      }

      public boolean underfoot() {
         return this.underfoot;
      }

      public SoundEvent of(SoundType type) {
         return switch (this) {
            case STEP -> type.getStepSound();
            case PLACE -> type.getPlaceSound();
            case BREAK -> type.getBreakSound();
            case HIT -> type.getHitSound();
            case FALL -> type.getFallSound();
         };
      }
   }

   public record BlockSound(JavaBlockSounds.Action action, BlockState sample) {
      public boolean madeBy(BlockState state, Identifier sound) {
         return this.action.of(state.getSoundType()).location().equals(sound);
      }
   }
}
