package net.bedrocknative.client.world;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.model.ActorState;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.sound.BedrockSoundEngine;
import net.bedrocknative.client.sound.JavaSoundBridge;
import net.bedrocknative.molang.MolangActor;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.protocol.NamedLevelSoundEventPacket;
import net.bedrocknative.sound.LevelEventSounds;
import net.bedrocknative.sound.NoteSounds;
import net.bedrocknative.sound.SoundCatalog;
import net.bedrocknative.sound.SoundEventTable;
import net.bedrocknative.sound.SoundPlanner;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.bedrocknative.world.entity.EntityMappings;
import net.bedrocknative.world.sound.JavaBlockSounds;
import net.bedrocknative.world.sound.SoundMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundUpdateSoundDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.cloudburstmc.protocol.bedrock.packet.StopSoundPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockSounds {
   private static final boolean VERBOSE = Boolean.getBoolean("bedrocknative.devScreenshot");
   private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
   private static final Set<String> PREDICTED_BY_JAVA = Set.of("STEP", "SWIM", "SPLASH", "EAT", "DRINK", "AMBIENT_UNDERWATER_ENTER", "AMBIENT_UNDERWATER_EXIT");
   private static final Set<String> SILENT = Set.of("SPAWN", "DISAPPEARED", "REAPPEARED", "MOB_WARNING", "AMBIENT_IN_WATER");
   private final Minecraft minecraft;
   private final BedrockEntities entities;
   private final Predicate<BlockPos> actedLocally;
   private final MolangEnv env = new MolangEnv();
   private int played;
   private int playedBedrock;
   private int unmapped;
   private int skipped;

   public BedrockSounds(Minecraft minecraft, BedrockEntities entities, Predicate<BlockPos> actedLocally) {
      this.minecraft = minecraft;
      this.entities = entities;
      this.actedLocally = actedLocally;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof LevelSoundEventPacket
         || packet instanceof PlaySoundPacket
         || packet instanceof StopSoundPacket
         || packet instanceof ClientboundUpdateSoundDataPacket
         || packet instanceof LevelEventPacket event
            && event.getType() instanceof LevelEvent type
            && (type.name().startsWith("SOUND_") || LevelEventSounds.of(type) != null);
   }

   public void apply(BedrockPacket packet) {
      if (this.minecraft.level != null) {
         switch (packet) {
            case LevelSoundEventPacket event:
               this.onSoundEvent(event);
               break;
            case PlaySoundPacket sound:
               this.onPlaySound(sound);
               break;
            case StopSoundPacket stop:
               this.onStopSound(stop);
               break;
            case ClientboundUpdateSoundDataPacket update:
               if (!BedrockSoundEngine.hasWaiting() || !BedrockSoundEngine.whenLoaded("", () -> BedrockSoundEngine.update(update))) {
                  BedrockSoundEngine.update(update);
               }
               break;
            case LevelEventPacket event:
               this.onLevelEvent(event);
               break;
            default:
         }
      }
   }

   public int playedCount() {
      return this.played;
   }

   public int playedBedrockCount() {
      return this.playedBedrock;
   }

   public int unmappedCount() {
      return this.unmapped;
   }

   public int skippedCount() {
      return this.skipped;
   }

   private void onStopSound(StopSoundPacket stop) {
      if (stop.isStoppingAllSound()) {
         BedrockSoundEngine.unqueueAll();
         BedrockSoundEngine.stopAll();
         this.minecraft.getSoundManager().stop();
      } else {
         if (stop.isStopMusicLegacy()) {
            BedrockSoundEngine.stopMusic();
            this.minecraft.getMusicManager().stopPlaying();
         }

         if (!stop.getSoundName().isEmpty()) {
            BedrockSoundEngine.unqueue(stop.getSoundName());
            BedrockSoundEngine.stop(stop.getSoundName());
            if (SoundMappings.get().playSound(stop.getSoundName()) instanceof SoundEvent sound) {
               this.minecraft.getSoundManager().stop(sound.location(), null);
            }
         }
      }
   }

   private void onLevelEvent(LevelEventPacket event) {
      LevelEvent type = (LevelEvent)event.getType();
      Vec3 pos = vec(event.getPosition());
      if (this.actedLocally.test(BlockPos.containing(pos))) {
         this.skip("level event " + type, "own block action, Java played it", pos);
      } else {
         LevelEventSounds.Sound bedrock = LevelEventSounds.of(type);
         SoundCatalog catalog = BedrockSoundEngine.catalog();
         if (bedrock != null && catalog != null) {
            SoundPlanner.Play play = SoundPlanner.definition(
               catalog.definitions(), bedrock.name(), 1.0F, bedrock.pitch(event.getData()), false, BedrockSoundEngine.random()
            );
            if (play != null
               && (BedrockSoundEngine.vanilla() || play.serverContent())
               && this.playBedrock("level event " + type, play, pos, BedrockSoundEngine.Options.DEFAULT)) {
               return;
            }
         }

         SoundEvent sound = SoundMappings.get().levelEvent(type);
         float pitch = event.getData() > 0 ? event.getData() / 1000.0F : 1.0F;
         this.report("level event " + type, sound, pos, false);
         this.play(sound, SoundSource.BLOCKS, pos, 1.0F, pitch);
      }
   }

   private void onSoundEvent(LevelSoundEventPacket packet) {
      org.cloudburstmc.protocol.bedrock.data.SoundEvent event = packet.getSound();
      String name = NamedLevelSoundEventPacket.name(packet);
      Vec3 pos = vec(packet.getPosition());
      boolean local = this.entities.isLocal(packet.getEntityUniqueId());
      String what = "sound event "
         + (name.isEmpty() ? "?" : name)
         + " data "
         + packet.getExtraData()
         + (packet.getIdentifier().isEmpty() ? "" : " " + packet.getIdentifier())
         + (local ? " (self)" : "");
      if (local && event != null && PREDICTED_BY_JAVA.contains(event.name())) {
         this.skip(what, "Java plays it for the local player", pos);
      } else if (packet.getEntityUniqueId() == -1L && this.actedLocally.test(BlockPos.containing(pos))) {
         this.skip(what, "own block action, Java played it", pos);
      } else if (!isServerActor(packet.getIdentifier())
         || BedrockSoundEngine.catalog() != null
         || !BedrockSoundEngine.whenLoaded(name, () -> this.apply(packet))) {
         if (!this.bedrockSoundEvent(packet, name, pos, what)) {
            if (event == null) {
               this.report(what, null, pos, local);
            } else if (event == org.cloudburstmc.protocol.bedrock.data.SoundEvent.NOTE) {
               SoundEvent sound = SoundMappings.get().note(packet.getExtraData() >> 8);
               this.report(what, sound, pos, local);
               this.play(sound, SoundSource.RECORDS, pos, 3.0F, (float)Math.pow(2.0, ((packet.getExtraData() & 0xFF) - 12) / 12.0));
            } else {
               Entity entity = this.entities.byUniqueId(packet.getEntityUniqueId());
               Optional<EntityType<?>> type = !packet.getIdentifier().isEmpty()
                  ? EntityMappings.toJava(packet.getIdentifier())
                  : (entity == null ? Optional.empty() : Optional.of(entity.getType()));
               if (type.isPresent()) {
                  SoundEvent sound = entitySound(type.get(), event.name(), packet.isBabySound());
                  if (sound != null) {
                     this.report(what, sound, pos, local);
                     this.play(sound, sourceOf(entity, type.get()), pos, 1.0F, this.voicePitch(packet.isBabySound()));
                     return;
                  }
               }

               if (!this.playBlockSound(event, packet.getExtraData(), pos, what, local)) {
                  SoundEvent sound = type.isPresent() && type.get() != EntityTypes.PLAYER ? null : SoundMappings.get().generic(event.name());
                  if (sound != null || !type.isPresent() && !SILENT.contains(event.name())) {
                     this.report(what, sound, pos, local);
                     this.play(
                        sound,
                        sound != null && sound.location().getPath().startsWith("entity.player.") ? SoundSource.PLAYERS : SoundSource.NEUTRAL,
                        packet.isRelativeVolumeDisabled() ? this.minecraft.gameRenderer.mainCamera().position() : pos,
                        1.0F,
                        1.0F
                     );
                  } else {
                     this.skip(what, "no Java sound for it", pos);
                  }
               }
            }
         }
      }
   }

   private boolean bedrockSoundEvent(LevelSoundEventPacket packet, String name, Vec3 pos, String what) {
      SoundCatalog catalog = BedrockSoundEngine.catalog();
      if (catalog != null && !name.isEmpty()) {
         RandomGenerator random = BedrockSoundEngine.random();
         BedrockSoundEngine.Options options = new BedrockSoundEngine.Options(null, 0, 0.0F, false, packet.isRelativeVolumeDisabled());
         if (name.equals("note")) {
            String instrument = NoteSounds.sound(packet.getExtraData());
            SoundEventTable.Resolved note = catalog.events().individual(name, random);
            SoundPlanner.Play play = instrument == null
               ? null
               : SoundPlanner.definition(
                  catalog.definitions(),
                  instrument,
                  note != null ? note.volume() : 3.0F,
                  NoteSounds.pitch(packet.getExtraData()),
                  note != null && note.serverPack(),
                  random
               );
            return play != null && (BedrockSoundEngine.vanilla() || play.serverContent()) && this.playBedrock(what, play, pos, options);
         }

         String identifier = actorIdentifier(packet.getIdentifier());
         Entity entity = this.entities.byUniqueId(packet.getEntityUniqueId());
         String blockName = catalog.events().blockEvent(name) ? BlockMappings.get().bedrockName(packet.getExtraData()) : null;
         boolean customBlock = blockName != null && BlockMappings.get().customBlock(blockName) != null;
         CustomBlockState custom = customBlock ? CarrierBlock.data(BlockMappings.get().toJava(packet.getExtraData())) : null;
         String blockType = custom != null
            ? JavaBlockSounds.customType(catalog, custom)
            : (blockName != null ? JavaBlockSounds.bedrockType(catalog, blockName) : null);
         SoundEventTable.Resolved resolved = SoundPlanner.levelSoundOfType(
            catalog, name, identifier, blockName != null, blockType, packet.isBabySound(), this.keys(entity, identifier, packet.isBabySound()), random
         );
         boolean serverEntity = !identifier.isEmpty() && catalog.events().serverEntity(identifier);
         if (resolved == null) {
            if (!serverEntity && (!BedrockSoundEngine.vanilla() || !catalog.baseGame())) {
               return false;
            }

            this.skip(what, "no sound in sounds.json", pos);
            return true;
         } else {
            boolean server = resolved.serverPack() || serverEntity;
            if (!resolved.silent()) {
               SoundPlanner.Play play = SoundPlanner.definition(catalog.definitions(), resolved.sound(), resolved.volume(), resolved.pitch(), server, random);
               if (play == null) {
                  if (!server && (!BedrockSoundEngine.vanilla() || !catalog.baseGame())) {
                     return false;
                  }

                  this.skip(what, "no definition " + resolved.sound(), pos);
                  return true;
               } else {
                  return !BedrockSoundEngine.vanilla() && !play.serverContent() && !customBlock
                     ? false
                     : this.playBedrock(what + " -> " + resolved.sound(), play, pos, options) || play.serverContent();
               }
            } else {
               if (!server && !BedrockSoundEngine.vanilla()) {
                  return false;
               }

               this.skip(what, "silent in sounds.json", pos);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private boolean playBedrock(String what, SoundPlanner.Play play, Vec3 pos, BedrockSoundEngine.Options options) {
      BedrockSoundEngine.Result result = BedrockSoundEngine.play(play, pos, options);
      if (result != BedrockSoundEngine.Result.NOT_PLAYABLE) {
         this.playedBedrock++;
         if (VERBOSE) {
            BedrockNative.LOGGER
               .info(
                  "Bedrock {} at {} -> bedrock {} ({}, gain {}, pitch {}, range {}, {}){}",
                  new Object[]{
                     what,
                     pos,
                     play.definition().name(),
                     play.variant().path(),
                     round(play.gain()),
                     round(play.pitch()),
                     round(play.range()),
                     play.category(),
                     result == BedrockSoundEngine.Result.OUT_OF_RANGE ? ", out of range" : ""
                  }
               );
         }

         return true;
      } else {
         if (VERBOSE || LOGGED.add(what + " not playable")) {
            BedrockNative.LOGGER.info("Bedrock {} at {}: {} ({}) not playable", new Object[]{what, pos, play.definition().name(), play.variant().path()});
         }

         return false;
      }
   }

   public void onEntityEvent(EntityEventPacket packet) {
      String event = switch (packet.getType()) {
         case HURT, HURT_WITHOUT_RECEIVING_DAMAGE -> "HURT";
         case DEATH -> "DEATH";
         case PLAY_AMBIENT -> "AMBIENT";
         default -> null;
      };
      Entity entity = event != null && this.minecraft.level != null ? this.entities.byRuntimeId(packet.getRuntimeEntityId()) : null;
      if (entity != null) {
         boolean baby = entity instanceof LivingEntity living && living.isBaby();
         String what = "entity event " + packet.getType() + " " + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
         if (!this.bedrockEntityEvent(entity, event.toLowerCase(Locale.ROOT), baby, what)) {
            SoundEvent sound = entitySound(entity.getType(), event, baby);
            this.report(what, sound, entity.position(), false);
            this.play(sound, entity.getSoundSource(), entity.position(), 1.0F, this.voicePitch(baby));
         }
      }
   }

   private boolean bedrockEntityEvent(Entity entity, String event, boolean baby, String what) {
      SoundCatalog catalog = BedrockSoundEngine.catalog();
      if (catalog == null) {
         return false;
      } else {
         ActorState state = BedrockActors.state(entity);
         String identifier = state != null ? state.data().identifier() : EntityMappings.toBedrock(entity.getType());
         boolean serverEntity = catalog.events().serverEntity(identifier);
         if (!BedrockSoundEngine.vanilla() && !serverEntity) {
            return false;
         } else {
            RandomGenerator random = BedrockSoundEngine.random();
            SoundEventTable.Resolved resolved = SoundPlanner.levelSound(catalog, event, identifier, null, baby, this.keys(entity, identifier, baby), random);
            if (resolved != null && !resolved.silent()) {
               SoundPlanner.Play play = SoundPlanner.definition(
                  catalog.definitions(), resolved.sound(), resolved.volume(), resolved.pitch(), resolved.serverPack() || serverEntity, random
               );
               return play == null
                  ? serverEntity || catalog.baseGame()
                  : this.playBedrock(what + " -> " + resolved.sound(), play, entity.position(), BedrockSoundEngine.Options.DEFAULT) || play.serverContent();
            } else {
               return serverEntity || catalog.baseGame();
            }
         }
      }
   }

   private SoundEventTable.VariantKeys keys(@Nullable Entity entity, String identifier, boolean baby) {
      ActorState state = entity != null ? BedrockActors.state(entity) : null;
      MolangActor actor = state != null ? state.view() : new BedrockSounds.PacketActor(identifier, baby);
      return key -> {
         this.env.actor = actor;
         Object result = key.evaluateObject(this.env);
         this.env.actor = MolangActor.NONE;
         return result instanceof String text ? text : null;
      };
   }

   private static String actorIdentifier(String identifier) {
      return !identifier.isBlank() && !identifier.equals(":") ? identifier : "";
   }

   private static boolean isServerActor(String identifier) {
      String actor = actorIdentifier(identifier);
      return !actor.isEmpty() && !actor.startsWith("minecraft:");
   }

   private static SoundEvent entitySound(EntityType<?> type, String event, boolean baby) {
      SoundMappings mappings = SoundMappings.get();
      SoundEvent sound = mappings.entitySound(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(), event, baby);
      if (sound == null && type.getCategory() == MobCategory.MONSTER) {
         sound = mappings.entitySound("hostile", event, false);
      }

      return sound != null ? sound : mappings.entitySound("generic", event, false);
   }

   private float voicePitch(boolean baby) {
      RandomSource random = this.minecraft.level.getRandom();
      return (random.nextFloat() - random.nextFloat()) * 0.2F + (baby ? 1.5F : 1.0F);
   }

   private boolean playBlockSound(org.cloudburstmc.protocol.bedrock.data.SoundEvent event, int blockHash, Vec3 pos, String what, boolean local) {
      if (!BlockMappings.get().isKnown(blockHash)) {
         return false;
      }

      BlockState state = BlockMappings.get().toJava(blockHash);
      SoundType type = state.getSoundType();
      SoundEvent sound;
      float volume;
      float pitch;
      switch (event) {
         case PLACE:
         case ITEM_USE_ON:
            sound = type.getPlaceSound();
            volume = (type.getVolume() + 1.0F) / 2.0F;
            pitch = type.getPitch() * 0.8F;
            break;
         case BREAK:
         case BREAK_BLOCK:
            sound = type.getBreakSound();
            volume = (type.getVolume() + 1.0F) / 2.0F;
            pitch = type.getPitch() * 0.8F;
            break;
         case HIT:
            sound = type.getHitSound();
            volume = (type.getVolume() + 1.0F) / 8.0F;
            pitch = type.getPitch() * 0.5F;
            break;
         case STEP:
            sound = type.getStepSound();
            volume = type.getVolume() * 0.15F;
            pitch = type.getPitch();
            break;
         case FALL:
         case LAND:
            sound = type.getFallSound();
            volume = type.getVolume() * 0.5F;
            pitch = type.getPitch() * 0.75F;
            break;
         default:
            return false;
      }

      this.report(what + " " + state, sound, pos, local);
      this.play(sound, SoundSource.BLOCKS, pos, volume, pitch);
      return true;
   }

   private void onPlaySound(PlaySoundPacket packet) {
      Vec3 pos = vec(packet.getPosition());
      SoundCatalog catalog = BedrockSoundEngine.catalog();
      String what = "playsound " + packet.getSound();
      if (catalog != null
         || SoundMappings.get().playSound(packet.getSound()) != null
         || !BedrockSoundEngine.whenLoaded(packet.getSound(), () -> this.apply(packet))) {
         if (catalog != null) {
            SoundPlanner.Play play = SoundPlanner.definition(
               catalog.definitions(), packet.getSound(), packet.getVolume(), packet.getPitch(), false, BedrockSoundEngine.random()
            );
            if (play != null && (BedrockSoundEngine.vanilla() || play.serverContent())) {
               BedrockSoundEngine.Options options = new BedrockSoundEngine.Options(
                  packet.getServerSoundHandle(),
                  packet.getLoopCount(),
                  packet.getPlaybackPositionSeconds() != null ? Math.max(0.0F, packet.getPlaybackPositionSeconds()) : 0.0F,
                  packet.isBypassListenerRangeCheck(),
                  false
               );
               if (this.playBedrock(what, play, pos, options) || play.serverContent()) {
                  return;
               }
            }
         }

         SoundEvent sound = SoundMappings.get().playSound(packet.getSound());
         this.report(what, sound, pos, false);
         this.play(sound, sourceOf(packet.getSound()), pos, packet.getVolume(), packet.getPitch());
      }
   }

   private void play(SoundEvent sound, SoundSource source, Vec3 pos, float volume, float pitch) {
      ClientLevel level = this.minecraft.level;
      if (sound != null && level != null) {
         this.played++;
         JavaSoundBridge.direct(() -> level.playLocalSound(pos.x, pos.y, pos.z, sound, source, volume, pitch, false));
      }
   }

   private void report(String what, SoundEvent sound, Vec3 pos, boolean local) {
      if (sound == null) {
         this.unmapped++;
         if (VERBOSE || LOGGED.add(what)) {
            BedrockNative.LOGGER.info("Bedrock {} at {}: unmapped", what, pos);
         }
      } else if (VERBOSE) {
         BedrockNative.LOGGER.info("Bedrock {} at {} -> {}", new Object[]{what, pos, sound.location()});
      }
   }

   private void skip(String what, String why, Vec3 pos) {
      this.skipped++;
      if (VERBOSE) {
         BedrockNative.LOGGER.info("Bedrock {} at {}: skipped, {}", new Object[]{what, pos, why});
      }
   }

   private static SoundSource sourceOf(Entity entity, EntityType<?> type) {
      if (entity != null) {
         return entity.getSoundSource();
      } else {
         return type == EntityTypes.PLAYER ? SoundSource.PLAYERS : (type.getCategory() == MobCategory.MONSTER ? SoundSource.HOSTILE : SoundSource.NEUTRAL);
      }
   }

   private static SoundSource sourceOf(String bedrockName) {
      String group = bedrockName.substring(0, Math.max(0, bedrockName.indexOf(46)));

      return switch (group) {
         case "music" -> SoundSource.MUSIC;
         case "record" -> SoundSource.RECORDS;
         case "ambient" -> SoundSource.AMBIENT;
         case "mob" -> SoundSource.NEUTRAL;
         case "dig", "step", "place", "hit", "fall", "block" -> SoundSource.BLOCKS;
         case "weather" -> SoundSource.WEATHER;
         default -> SoundSource.MASTER;
      };
   }

   private static float round(float value) {
      return Math.round(value * 100.0F) / 100.0F;
   }

   private static Vec3 vec(Vector3f position) {
      return new Vec3(position.getX(), position.getY(), position.getZ());
   }

   private record PacketActor(String identifier, boolean baby) implements MolangActor {
      @Override
      public boolean flag(EntityFlag flag) {
         return flag == EntityFlag.BABY && this.baby;
      }
   }
}
