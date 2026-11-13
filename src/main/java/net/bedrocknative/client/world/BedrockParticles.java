package net.bedrocknative.client.world;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.particle.BedrockParticleEngine;
import net.bedrocknative.client.particle.EntityParticleAnchor;
import net.bedrocknative.particle.LegacyParticleEffects;
import net.bedrocknative.particle.MolangVariablesJson;
import net.bedrocknative.particle.ParticleAnchor;
import net.bedrocknative.particle.ParticleEffect;
import net.bedrocknative.particle.ParticleEffects;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.particle.ParticleMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket;

public final class BedrockParticles {
   private static final boolean VERBOSE = Boolean.getBoolean("bedrocknative.devScreenshot");
   private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
   private final Minecraft minecraft;
   private final Predicate<BlockPos> brokenLocally;
   private final LongFunction<Entity> byUniqueId;
   private int shown;
   private int shownBedrock;
   private int unmapped;

   public BedrockParticles(Minecraft minecraft, Predicate<BlockPos> brokenLocally, LongFunction<Entity> byUniqueId) {
      this.minecraft = minecraft;
      this.brokenLocally = brokenLocally;
      this.byUniqueId = byUniqueId;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof SpawnParticleEffectPacket
         || packet instanceof LevelEventPacket event
            && (
               event.getType() instanceof org.cloudburstmc.protocol.bedrock.data.ParticleType
                  || event.getType() instanceof LevelEvent type && type.name().startsWith("PARTICLE_")
            );
   }

   public void apply(BedrockPacket packet) {
      ClientLevel level = this.minecraft.level;
      if (level != null) {
         if (packet instanceof SpawnParticleEffectPacket effect) {
            Vec3 pos = new Vec3(effect.getPosition().getX(), effect.getPosition().getY(), effect.getPosition().getZ());
            if (this.bedrockEffect(effect, pos)) {
               return;
            }

            ParticleOptions particle = simple(ParticleMappings.get().byBedrockId(effect.getIdentifier()));
            if (particle == null
               && BedrockParticleEngine.effects() == null
               && BedrockParticleEngine.whenLoaded(effect.getIdentifier(), () -> this.apply(packet))) {
               return;
            }

            this.report("particle " + effect.getIdentifier(), particle, pos);
            if (particle != null) {
               this.burst(level, particle, pos, effect.getIdentifier().endsWith("emitter") ? 8 : 1, 0.25);
            }
         } else if (packet instanceof LevelEventPacket event) {
            Vec3 posx = new Vec3(event.getPosition().getX(), event.getPosition().getY(), event.getPosition().getZ());
            if (event.getType() instanceof org.cloudburstmc.protocol.bedrock.data.ParticleType type) {
               if (this.bedrockLegacy(type, posx)) {
                  return;
               }

               ParticleOptions particlex = this.typed(type, event.getData());
               this.report("particle event " + type + " data " + event.getData(), particlex, posx);
               if (particlex != null) {
                  level.addParticle(particlex, posx.x, posx.y, posx.z, 0.0, 0.0, 0.0);
               }
            } else {
               this.onParticleEvent(level, (LevelEvent)event.getType(), posx, event.getData());
            }
         }
      }
   }

   public int shownCount() {
      return this.shown;
   }

   public int shownBedrockCount() {
      return this.shownBedrock;
   }

   private boolean bedrockEffect(SpawnParticleEffectPacket packet, Vec3 pos) {
      ParticleEffects effects = BedrockParticleEngine.effects();
      ParticleEffect effect = effects != null ? effects.get(packet.getIdentifier()) : null;
      if (effect != null && (effect.serverPack() || BedrockParticleEngine.vanilla()) && !effect.texture().startsWith("atlas.")) {
         String what = "particle " + packet.getIdentifier() + (effect.serverPack() ? " (server)" : "");
         boolean started;
         if (effect.emitter().rate() == ParticleEffect.Rate.MANUAL) {
            started = BedrockParticleEngine.emitManual(effect, pos.x, pos.y, pos.z);
         } else {
            Entity entity = packet.getUniqueEntityId() != -1L ? this.byUniqueId.apply(packet.getUniqueEntityId()) : null;
            ParticleAnchor anchor = entity != null
               ? new EntityParticleAnchor(entity, null, pos.x - entity.getX(), pos.y - entity.getY(), pos.z - entity.getZ())
               : null;
            String variables = packet.getMolangVariablesJson() != null ? packet.getMolangVariablesJson().orElse(null) : null;
            started = BedrockParticleEngine.spawn(
                  effect, pos.x, pos.y, pos.z, anchor, null, variables != null ? store -> MolangVariablesJson.apply(variables, store) : null
               )
               != null;
         }

         this.shownBedrock++;
         if (VERBOSE) {
            BedrockNative.LOGGER
               .info(
                  "Bedrock {} at {} -> bedrock {} ({}, {} particles alive){}",
                  new Object[]{what, pos, effect.identifier(), effect.texture(), BedrockParticleEngine.particleCount(), started ? "" : ", over the caps"}
               );
         }

         return true;
      } else {
         return false;
      }
   }

   private boolean bedrockLegacy(org.cloudburstmc.protocol.bedrock.data.ParticleType type, Vec3 pos) {
      if (!BedrockParticleEngine.vanilla()) {
         return false;
      } else {
         ParticleEffects effects = BedrockParticleEngine.effects();
         String identifier = LegacyParticleEffects.of(type);
         ParticleEffect effect = effects != null && identifier != null ? effects.get(identifier) : null;
         if (effect != null && !effect.texture().startsWith("atlas.")) {
            if (effect.emitter().rate() == ParticleEffect.Rate.MANUAL) {
               BedrockParticleEngine.emitManual(effect, pos.x, pos.y, pos.z);
            } else {
               BedrockParticleEngine.spawn(effect, pos.x, pos.y, pos.z, null, null, null);
            }

            this.shownBedrock++;
            if (VERBOSE) {
               BedrockNative.LOGGER.info("Bedrock particle event {} at {} -> bedrock {}", new Object[]{type, pos, identifier});
            }

            return true;
         } else {
            return false;
         }
      }
   }

   public int unmappedCount() {
      return this.unmapped;
   }

   private void onParticleEvent(ClientLevel level, LevelEvent type, Vec3 pos, int data) {
      BlockPos block = BlockPos.containing(pos);
      String what = "level event " + type + " data " + data;
      switch (type) {
         case PARTICLE_BREAK_BLOCK_DOWN:
         case PARTICLE_BREAK_BLOCK_NORTH:
         case PARTICLE_BREAK_BLOCK_SOUTH:
         case PARTICLE_BREAK_BLOCK_WEST:
         case PARTICLE_BREAK_BLOCK_EAST:
         case PARTICLE_CRACK_BLOCK:
         case PARTICLE_BREAK_BLOCK_UP:
            Direction face = switch (type) {
               case PARTICLE_BREAK_BLOCK_DOWN -> Direction.DOWN;
               case PARTICLE_BREAK_BLOCK_NORTH -> Direction.NORTH;
               case PARTICLE_BREAK_BLOCK_SOUTH -> Direction.SOUTH;
               case PARTICLE_BREAK_BLOCK_WEST -> Direction.WEST;
               case PARTICLE_BREAK_BLOCK_EAST -> Direction.EAST;
               default -> Direction.UP;
            };
            if (level.getBlockState(block).isAir() || this.brokenLocally.test(block)) {
               this.report(what + " (own or gone)", "skipped", pos);
               return;
            }

            this.report(what, "crack particles", pos);
            level.addBreakingBlockEffects(block, face, false);
            break;
         case PARTICLE_DESTROY_BLOCK:
         case PARTICLE_DESTROY_BLOCK_NO_SOUND:
            BlockState statex = block(data);
            if (this.brokenLocally.test(block)) {
               this.report(what + " (own break, Java showed it)", "skipped", pos);
               return;
            }

            this.report(what + " " + statex, statex == null ? null : "java level event", pos);
            if (statex != null) {
               int javaEvent = type == LevelEvent.PARTICLE_DESTROY_BLOCK ? 2001 : 2014;
               level.levelEvent(null, javaEvent, block, Block.getId(statex));
            }
            break;
         case PARTICLE_POTION_SPLASH:
            this.report(what, "java level event", pos);
            level.levelEvent(null, 2002, block, data & 16777215);
            break;
         case PARTICLE_EYE_OF_ENDER_DEATH:
            this.javaEvent(level, what, 2003, block, 0, ParticleTypes.PORTAL, pos);
            break;
         case PARTICLE_MOB_BLOCK_SPAWN:
            this.javaEvent(level, what, 2004, block, 0, ParticleTypes.FLAME, pos);
            break;
         case PARTICLE_CROP_GROWTH:
            this.javaEvent(level, what, 1505, block, Math.max(data, 15), ParticleTypes.HAPPY_VILLAGER, pos);
            break;
         case PARTICLE_EVAPORATE_WATER:
            this.javaEvent(level, what, 2009, block, 0, ParticleTypes.LARGE_SMOKE, pos);
            break;
         case PARTICLE_WAX_ON:
            this.javaEvent(level, what, 3003, block, 0, ParticleTypes.WAX_ON, pos);
            break;
         case PARTICLE_WAX_OFF:
            this.javaEvent(level, what, 3004, block, 0, ParticleTypes.WAX_OFF, pos);
            break;
         case PARTICLE_SCRAPE:
            this.javaEvent(level, what, 3005, block, 0, ParticleTypes.SCRAPE, pos);
            break;
         case PARTICLE_ELECTRIC_SPARK:
            this.javaEvent(level, what, 3002, block, -1, ParticleTypes.ELECTRIC_SPARK, pos);
            break;
         case PARTICLE_SHOOT:
            this.javaEvent(level, what, 2000, block, shootDirection(data), ParticleTypes.SMOKE, pos);
            break;
         case PARTICLE_SHOOT_WHITE_SMOKE:
            this.javaEvent(level, what, 2010, block, shootDirection(data), ParticleTypes.WHITE_SMOKE, pos);
            break;
         case PARTICLE_CRIT:
            this.report(what, ParticleTypes.CRIT, pos);
            this.burst(level, ParticleTypes.CRIT, pos, Math.clamp((long)data, 1, 30), 0.5);
            break;
         case PARTICLE_DEATH_SMOKE:
         case PARTICLE_GENERIC_SPAWN:
            this.report(what, ParticleTypes.POOF, pos);
            this.burst(level, ParticleTypes.POOF, pos, 20, 0.5);
            break;
         case PARTICLE_TELEPORT:
            this.report(what, ParticleTypes.PORTAL, pos);
            this.burst(level, ParticleTypes.PORTAL, pos, 32, 0.5);
            break;
         case PARTICLE_EXPLOSION:
         case PARTICLE_BLOCK_EXPLOSION:
            this.report(what, ParticleTypes.EXPLOSION, pos);
            level.addParticle(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1.0, 0.0, 0.0);
            break;
         case PARTICLE_BUBBLES:
            this.report(what, ParticleTypes.BUBBLE, pos);
            this.burst(level, ParticleTypes.BUBBLE, pos, 8, 0.5);
            break;
         case PARTICLE_DENY_BLOCK:
            this.shown(level, what, ParticleTypes.ANGRY_VILLAGER, pos, 3, 0.25);
            break;
         case PARTICLE_DRAGON_EGG:
         case PARTICLE_TELEPORT_TRAIL:
            this.shown(level, what, ParticleTypes.PORTAL, pos, 32, 0.5);
            break;
         case PARTICLE_CROP_EATEN:
            BlockState state = block(data);
            if (state == null) {
               this.shown(level, what, ParticleTypes.HAPPY_VILLAGER, pos, 6, 0.3);
            } else {
               this.shown(level, what, new BlockParticleOption(ParticleTypes.BLOCK, state), pos, 12, 0.3);
            }
            break;
         case PARTICLE_EVAPORATE:
         case PARTICLE_FIZZ_EFFECT:
            this.shown(level, what, ParticleTypes.LARGE_SMOKE, pos, 8, 0.3);
            break;
         case PARTICLE_DESTROY_ARMOR_STAND:
            this.shown(level, what, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), pos, 16, 0.3);
            break;
         case PARTICLE_BREAKING_EGG:
         case PARTICLE_DESTROY_EGG:
            this.shown(level, what, new ItemParticleOption(ParticleTypes.ITEM, Items.EGG), pos, 12, 0.2);
            break;
         case PARTICLE_KNOCKBACK_ROAR:
            this.shown(level, what, ParticleTypes.POOF, pos, 40, 1.5);
            break;
         case PARTICLE_POINT_CLOUD:
            this.shown(level, what, ParticleTypes.CLOUD, pos, 8, 0.4);
            break;
         case PARTICLE_DRIPSTONE_DRIP:
            this.shown(level, what, ParticleTypes.DRIPPING_DRIPSTONE_WATER, pos, 1, 0.0);
            break;
         case PARTICLE_TURTLE_EGG:
            this.shown(level, what, ParticleTypes.HAPPY_VILLAGER, pos, 6, 0.3);
            break;
         case PARTICLE_SCULK_SHRIEK:
            this.shown(level, what, new ShriekParticleOption(0), pos, 1, 0.0);
            break;
         case PARTICLE_WIND_EXPLOSION:
            this.shown(level, what, ParticleTypes.GUST_EMITTER_SMALL, pos, 1, 0.0);
            break;
         case PARTICLE_TRIAL_SPAWNER_DETECTION:
         case PARTICLE_TRIAL_SPAWNER_DETECTION_CHARGED:
            this.shown(level, what, ParticleTypes.SMALL_FLAME, pos, 12, 0.4);
            break;
         case PARTICLE_TRIAL_SPAWNER_SPAWNING:
            this.shown(level, what, ParticleTypes.SMOKE, pos, 12, 0.3);
            break;
         case PARTICLE_TRIAL_SPAWNER_EJECTING:
            this.shown(level, what, ParticleTypes.SMALL_FLAME, pos, 16, 0.3);
            break;
         case PARTICLE_TRIAL_SPAWNER_BECOME_CHARGED:
         case PARTICLE_CREAKING_HEART_TRIAL:
            this.shown(level, what, ParticleTypes.TRIAL_OMEN, pos, 12, 0.4);
            break;
         case PARTICLE_VIBRATION_SIGNAL:
            this.report(what + " (a signal's path needs its endpoints; Java's vibration shows from the sculk sensor's own event)", "consumed", pos);
            break;
         default:
            ParticleOptions particle = simple(ParticleMappings.get().byEventType(type.name()));
            this.report(what, particle, pos);
            if (particle != null) {
               this.burst(level, particle, pos, 8, 0.5);
            }
      }
   }

   private void shown(ClientLevel level, String what, ParticleOptions particle, Vec3 pos, int count, double spread) {
      this.report(what, particle, pos);
      this.burst(level, particle, pos, count, spread);
   }

   private void javaEvent(ClientLevel level, String what, int javaEvent, BlockPos block, int data, Object shown, Vec3 pos) {
      this.report(what, shown, pos);
      level.levelEvent(null, javaEvent, block, data);
   }

   private ParticleOptions typed(org.cloudburstmc.protocol.bedrock.data.ParticleType type, int data) {
      return (ParticleOptions)(switch (type) {
         case TERRAIN -> {
            BlockState var5 = block(data);
            yield var5 instanceof BlockState ? new BlockParticleOption(ParticleTypes.BLOCK, var5) : null;
         }
         case RED_DUST -> new DustParticleOptions(data & 16777215, 1.0F);
         case MOB_SPELL, MOB_SPELL_AMBIENT -> ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, data);
         case FALLING_DUST -> {
            BlockState var4 = block(data);
            yield var4 instanceof BlockState ? new BlockParticleOption(ParticleTypes.FALLING_DUST, var4) : null;
         }
         default -> simple(ParticleMappings.get().byEventType(type.name()));
      });
   }

   private void burst(ClientLevel level, ParticleOptions particle, Vec3 pos, int count, double spread) {
      RandomSource random = level.getRandom();

      for (int i = 0; i < count; i++) {
         double dx = count == 1 ? 0.0 : random.nextGaussian() * spread;
         double dy = count == 1 ? 0.0 : random.nextGaussian() * spread;
         double dz = count == 1 ? 0.0 : random.nextGaussian() * spread;
         level.addParticle(particle, pos.x + dx, pos.y + dy, pos.z + dz, dx * 0.1, dy * 0.1, dz * 0.1);
      }
   }

   private void report(String what, Object particle, Vec3 pos) {
      if (particle != null) {
         this.shown++;
         if (VERBOSE) {
            BedrockNative.LOGGER
               .info(
                  "Bedrock {} at {} -> {}",
                  new Object[]{what, pos, particle instanceof ParticleOptions options ? BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()) : particle}
               );
         }
      } else {
         this.unmapped++;
         if (VERBOSE || LOGGED.add(what.replaceAll(" data -?\\d+", ""))) {
            BedrockNative.LOGGER.info("Bedrock {} at {}: unmapped", what, pos);
         }
      }
   }

   private static ParticleOptions simple(ParticleType<?> type) {
      return type instanceof SimpleParticleType simple ? simple : null;
   }

   private static BlockState block(int hash) {
      return BlockMappings.get().isKnownOrCustom(hash) ? BlockMappings.get().toJava(hash) : null;
   }

   private static int shootDirection(int data) {
      return switch (data % 9) {
         case 0, 3 -> Direction.WEST.ordinal();
         default -> Direction.NORTH.ordinal();
         case 4 -> Direction.UP.ordinal();
         case 5, 8 -> Direction.EAST.ordinal();
         case 6, 7 -> Direction.SOUTH.ordinal();
      };
   }
}
