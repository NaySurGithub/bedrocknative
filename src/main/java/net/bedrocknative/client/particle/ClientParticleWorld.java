package net.bedrocknative.client.particle;

import java.util.IdentityHashMap;
import java.util.Map;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.sound.BedrockSoundEngine;
import net.bedrocknative.particle.ParticleEffect;
import net.bedrocknative.particle.ParticleEffects;
import net.bedrocknative.particle.ParticleWorld;
import net.bedrocknative.sound.SoundCatalog;
import net.bedrocknative.sound.SoundEventTable;
import net.bedrocknative.sound.SoundPlanner;
import net.bedrocknative.world.block.BlockMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes.DoubleLineConsumer;
import org.jspecify.annotations.Nullable;

final class ClientParticleWorld implements ParticleWorld {
   private static final int[] AXES = new int[]{1, 0, 2};
   private final MutableBlockPos cursor = new MutableBlockPos();
   private final Map<BlockState, String> names = new IdentityHashMap<>();
   private final ClientParticleWorld.Clip clip = new ClientParticleWorld.Clip();

   @Override
   public int collide(double x, double y, double z, double radius, double[] motion) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return 0;
      } else {
         this.clip.minX = x - radius;
         this.clip.minY = y - radius;
         this.clip.minZ = z - radius;
         this.clip.maxX = x + radius;
         this.clip.maxY = y + radius;
         this.clip.maxZ = z + radius;
         int blocked = 0;

         for (int axis : AXES) {
            double wanted = motion[axis];
            if (wanted != 0.0) {
               double allowed = this.sweep(level, axis, wanted);
               motion[axis] = allowed;
               if (Math.abs(allowed - wanted) > 1.0E-7) {
                  blocked |= axis == 0 ? 1 : (axis == 1 ? 2 : 4);
               }

               switch (axis) {
                  case 0:
                     this.clip.minX += allowed;
                     this.clip.maxX += allowed;
                     break;
                  case 1:
                     this.clip.minY += allowed;
                     this.clip.maxY += allowed;
                     break;
                  default:
                     this.clip.minZ += allowed;
                     this.clip.maxZ += allowed;
               }
            }
         }

         return blocked;
      }
   }

   private double sweep(ClientLevel level, int axis, double motion) {
      this.clip.axis = axis;
      this.clip.motion = motion;
      int fromX = floor(Math.min(this.clip.minX, this.clip.minX + (axis == 0 ? motion : 0.0)) - 1.0E-7);
      int toX = floor(Math.max(this.clip.maxX, this.clip.maxX + (axis == 0 ? motion : 0.0)) + 1.0E-7);
      int fromY = floor(Math.min(this.clip.minY, this.clip.minY + (axis == 1 ? motion : 0.0)) - 1.0E-7) - 1;
      int toY = floor(Math.max(this.clip.maxY, this.clip.maxY + (axis == 1 ? motion : 0.0)) + 1.0E-7);
      int fromZ = floor(Math.min(this.clip.minZ, this.clip.minZ + (axis == 2 ? motion : 0.0)) - 1.0E-7);
      int toZ = floor(Math.max(this.clip.maxZ, this.clip.maxZ + (axis == 2 ? motion : 0.0)) + 1.0E-7);

      for (int bx = fromX; bx <= toX; bx++) {
         for (int by = fromY; by <= toY; by++) {
            for (int bz = fromZ; bz <= toZ; bz++) {
               this.cursor.set(bx, by, bz);
               if (level.hasChunkAt(this.cursor)) {
                  BlockState state = level.getBlockState(this.cursor);
                  if (!state.isAir()) {
                     VoxelShape shape = state.getCollisionShape(level, this.cursor);
                     if (!shape.isEmpty()) {
                        if (shape == Shapes.block()) {
                           this.clip.box(bx, by, bz, bx + 1, by + 1, bz + 1);
                        } else {
                           this.clip.offsetX = bx;
                           this.clip.offsetY = by;
                           this.clip.offsetZ = bz;
                           shape.forAllBoxes(this.clip);
                        }
                     }
                  }
               }
            }
         }
      }

      return this.clip.motion;
   }

   private static int floor(double value) {
      int floor = (int)value;
      return value < floor ? floor - 1 : floor;
   }

   @Override
   public String blockName(double x, double y, double z) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return "minecraft:air";
      } else {
         this.cursor.set(x, y, z);
         BlockState state = level.getBlockState(this.cursor);
         String name = this.names.get(state);
         if (name == null) {
            name = bedrockName(state);
            this.names.put(state, name);
         }

         return name;
      }
   }

   private static String bedrockName(BlockState state) {
      if (state.isAir()) {
         return "minecraft:air";
      } else if (state.getFluidState().isSource() && state.getBlock() == Blocks.WATER) {
         return "minecraft:water";
      } else {
         String name = BlockMappings.get().bedrockName(BlockMappings.get().toBedrock(state));
         return name != null ? name : "minecraft:air";
      }
   }

   @Override
   public void playSound(String event, double x, double y, double z, ParticleEffect source) {
      SoundCatalog catalog = BedrockSoundEngine.catalog();
      if (catalog != null) {
         SoundEventTable.Resolved resolved = SoundPlanner.levelSound(
            catalog, event, "", null, false, SoundEventTable.VariantKeys.NONE, BedrockSoundEngine.random()
         );
         if (resolved != null && !resolved.silent()) {
            SoundPlanner.Play play = SoundPlanner.definition(
               catalog.definitions(),
               resolved.sound(),
               resolved.volume(),
               resolved.pitch(),
               resolved.serverPack() || source.serverPack(),
               BedrockSoundEngine.random()
            );
            if (play != null && (BedrockSoundEngine.vanilla() || play.serverContent())) {
               BedrockSoundEngine.play(play, new Vec3(x, y, z), BedrockSoundEngine.Options.DEFAULT);
            }
         }
      }
   }

   @Nullable
   @Override
   public ParticleEffect effect(String identifier) {
      ParticleEffects effects = BedrockParticleEngine.effects();
      return effects != null ? effects.get(identifier) : null;
   }

   @Override
   public void log(ParticleEffect effect, String message, double x, double y, double z) {
      BedrockNative.LOGGER.info("Bedrock particle {} at {} {} {}: {}", new Object[]{effect.identifier(), x, y, z, message});
   }

   void clear() {
      this.names.clear();
   }

   private static final class Clip implements DoubleLineConsumer {
      double minX;
      double minY;
      double minZ;
      double maxX;
      double maxY;
      double maxZ;
      double offsetX;
      double offsetY;
      double offsetZ;
      int axis;
      double motion;

      public void consume(double x1, double y1, double z1, double x2, double y2, double z2) {
         this.box(x1 + this.offsetX, y1 + this.offsetY, z1 + this.offsetZ, x2 + this.offsetX, y2 + this.offsetY, z2 + this.offsetZ);
      }

      void box(double x1, double y1, double z1, double x2, double y2, double z2) {
         switch (this.axis) {
            case 0:
               if (this.maxY <= y1 || this.minY >= y2 || this.maxZ <= z1 || this.minZ >= z2) {
                  return;
               }

               if (this.motion > 0.0 && this.maxX <= x1) {
                  this.motion = Math.min(this.motion, x1 - this.maxX);
               } else if (this.motion < 0.0 && this.minX >= x2) {
                  this.motion = Math.max(this.motion, x2 - this.minX);
               }
               break;
            case 1:
               if (this.maxX <= x1 || this.minX >= x2 || this.maxZ <= z1 || this.minZ >= z2) {
                  return;
               }

               if (this.motion > 0.0 && this.maxY <= y1) {
                  this.motion = Math.min(this.motion, y1 - this.maxY);
               } else if (this.motion < 0.0 && this.minY >= y2) {
                  this.motion = Math.max(this.motion, y2 - this.minY);
               }
               break;
            default:
               if (this.maxX <= x1 || this.minX >= x2 || this.maxY <= y1 || this.minY >= y2) {
                  return;
               }

               if (this.motion > 0.0 && this.maxZ <= z1) {
                  this.motion = Math.min(this.motion, z1 - this.maxZ);
               } else if (this.motion < 0.0 && this.minZ >= z2) {
                  this.motion = Math.max(this.motion, z2 - this.minZ);
               }
         }
      }
   }
}
