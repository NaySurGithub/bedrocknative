package net.bedrocknative.client.sound;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.random.RandomGenerator;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.mixin.sound.AbstractSoundInstanceAccessor;
import net.bedrocknative.sound.SoundCatalog;
import net.bedrocknative.sound.SoundEventTable;
import net.bedrocknative.sound.SoundPlanner;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.sound.JavaBlockSounds;
import net.bedrocknative.world.sound.SoundMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.SoundEngine.PlayResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class JavaSoundBridge {
   private static final Map<SoundInstance, BedrockSoundInstance> BRIDGED = new WeakHashMap<>();
   private static final Map<BlockState, String> NAMES = new IdentityHashMap<>();
   @Nullable
   private static SoundCatalog namesOf;
   private static boolean direct;
   @Nullable
   private static BlockPos breakingAt;
   @Nullable
   private static BlockState breaking;
   private static int bridged;
   private static int silenced;
   private static boolean failed;

   private JavaSoundBridge() {
   }

   public static void direct(Runnable play) {
      boolean was = direct;
      direct = true;

      try {
         play.run();
      } finally {
         direct = was;
      }
   }

   public static void breaking(@Nullable BlockPos pos, @Nullable BlockState state) {
      breakingAt = pos;
      breaking = state;
   }

   @Nullable
   public static PlayResult play(SoundInstance instance) {
      if (!direct
         && !(instance instanceof TickableSoundInstance)
         && instance instanceof AbstractSoundInstance
         && !instance.isLooping()
         && instance.getDelay() <= 0
         && instance.canPlaySound()) {
         try {
            return bridge(instance);
         } catch (RuntimeException var2) {
            if (!failed) {
               failed = true;
               BedrockNative.LOGGER
                  .warn("Java sound {} couldn't go the Bedrock way; Java's sounds play as Java's where this happens", instance.getIdentifier(), var2);
            }

            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static PlayResult bridge(SoundInstance instance) {
      SoundCatalog catalog = BedrockSoundEngine.catalog();
      Identifier id = instance.getIdentifier();
      Minecraft minecraft = Minecraft.getInstance();
      if (catalog != null && "minecraft".equals(id.getNamespace()) && minecraft.isSameThread()) {
         boolean global = instance.isRelative() || instance.getAttenuation() == Attenuation.NONE;
         Vec3 position = global ? minecraft.getSoundManager().getListenerTransform().position() : new Vec3(instance.getX(), instance.getY(), instance.getZ());
         RandomGenerator random = BedrockSoundEngine.random();
         SoundPlanner.Play play = null;
         boolean customBlock = false;
         JavaBlockSounds.BlockSound block = global ? null : JavaBlockSounds.of(id);
         if (block != null) {
            BlockState state = blockOf(block, id, position, minecraft.level);
            customBlock = state != null && state.getBlock() instanceof CarrierBlock;
            String name = bedrockName(state != null ? state : block.sample(), catalog);
            SoundEventTable.Resolved resolved = name == null
               ? null
               : JavaBlockSounds.resolve(catalog, block.action(), name, state != null ? CarrierBlock.data(state) : null, random);
            if (resolved != null && resolved.silent()) {
               if (!BedrockSoundEngine.vanilla() && !customBlock && !resolved.serverPack()) {
                  return null;
               }

               silenced++;
               return PlayResult.NOT_STARTED;
            }

            if (resolved != null) {
               play = SoundPlanner.definition(catalog.definitions(), resolved.sound(), resolved.volume(), resolved.pitch(), resolved.serverPack(), random);
            }
         }

         if (play == null) {
            customBlock = false;
            String namex = SoundMappings.get().bedrockName(id.getPath());
            AbstractSoundInstanceAccessor raw = (AbstractSoundInstanceAccessor)instance;
            play = namex == null
               ? null
               : SoundPlanner.definition(catalog.definitions(), namex, raw.bedrocknative$volume(), raw.bedrocknative$pitch(), false, random);
         }

         if (play != null && (BedrockSoundEngine.vanilla() || play.serverContent() || customBlock)) {
            BedrockSoundEngine.Started started = BedrockSoundEngine.start(play, position, new BedrockSoundEngine.Options(null, 0, 0.0F, false, global));

            return switch (started.result()) {
               case NOT_PLAYABLE -> null;
               case OUT_OF_RANGE -> PlayResult.NOT_STARTED;
               case PLAYED -> {
                  BRIDGED.put(instance, started.instance());
                  bridged++;
                  yield instance.getSource() == SoundSource.MUSIC ? PlayResult.STARTED_SILENTLY : PlayResult.STARTED;
               }
            };
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   public static BedrockSoundInstance standIn(SoundInstance instance) {
      return !BRIDGED.isEmpty() && Minecraft.getInstance().isSameThread() ? BRIDGED.get(instance) : null;
   }

   public static boolean stop(SoundInstance instance) {
      BedrockSoundInstance standIn = standIn(instance);
      if (standIn == null) {
         return false;
      } else {
         BRIDGED.remove(instance);
         BedrockSoundEngine.stop(standIn);
         return true;
      }
   }

   static void release() {
      BRIDGED.clear();
      NAMES.clear();
      namesOf = null;
      breakingAt = null;
      breaking = null;
   }

   public static int bridgedCount() {
      return bridged;
   }

   public static int silencedCount() {
      return silenced;
   }

   @Nullable
   private static BlockState blockOf(JavaBlockSounds.BlockSound sound, Identifier id, Vec3 position, @Nullable ClientLevel level) {
      if (sound.action() != JavaBlockSounds.Action.BREAK) {
         if (level == null) {
            return null;
         } else {
            BlockState at = level.getBlockState(BlockPos.containing(position));
            if (!at.isAir() && sound.madeBy(at, id)) {
               return at;
            } else {
               if (sound.action().underfoot()) {
                  BlockState below = level.getBlockState(BlockPos.containing(position.x, position.y - 0.2, position.z));
                  if (!below.isAir() && sound.madeBy(below, id)) {
                     return below;
                  }
               }

               return null;
            }
         }
      } else {
         BlockState broken = breaking;
         return broken != null && BlockPos.containing(position).equals(breakingAt) && sound.madeBy(broken, id) ? broken : null;
      }
   }

   @Nullable
   private static String bedrockName(BlockState state, SoundCatalog catalog) {
      BlockMappings mappings = BlockMappings.get();
      if (state.getBlock() instanceof CarrierBlock) {
         return mappings.bedrockName(mappings.toBedrock(state));
      } else {
         if (namesOf != catalog) {
            NAMES.clear();
            namesOf = catalog;
         }

         if (NAMES.containsKey(state)) {
            return NAMES.get(state);
         } else {
            String name = mappings.bedrockName(mappings.toBedrock(state));
            NAMES.put(state, name);
            return name;
         }
      }
   }
}
