package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.world.BedrockParticles;
import net.bedrocknative.client.world.BedrockSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class DevEffectsTest {
   private static final List<String> SETUP = List.of(
      "gamemode survival",
      "clear @s",
      "give @s diamond_pickaxe",
      "give @s dirt 4",
      "fill ~-1 ~-1 ~-5 ~3 ~-1 ~2 stone",
      "fill ~-1 ~ ~-5 ~3 ~2 ~2 air",
      "setblock ~2 ~ ~ stone",
      "setblock ~2 ~ ~1 oak_trapdoor",
      "kill @e[type=cow]",
      "kill @e[type=drowned]",
      "time set noon",
      "weather clear"
   );
   private static final List<String> COMMANDS = List.of(
      "playsound random.levelup @s",
      "playsound mob.zombie.say @s",
      "playsound note.harp @s ~ ~ ~ 1 2",
      "particle minecraft:heart_particle ~ ~2 ~",
      "particle minecraft:critical_hit_emitter ~1 ~1 ~",
      "particle minecraft:villager_happy ~1 ~1.5 ~1",
      "particle minecraft:basic_flame_particle ~ ~1 ~1",
      "summon cow ~2 ~ ~2",
      "damage @e[type=cow,c=1] 1",
      "setblock ~2 ~ ~-1 oak_planks",
      "setblock ~2 ~ ~-1 air destroy",
      "kill @e[type=cow]"
   );
   private final Minecraft minecraft;
   private final BedrockSounds sounds;
   private final BedrockParticles particles;

   DevEffectsTest(Minecraft minecraft, BedrockSounds sounds, BedrockParticles particles) {
      this.minecraft = minecraft;
      this.sounds = sounds;
      this.particles = particles;
   }

   void run() {
      for (String command : SETUP) {
         this.command(command, 500L);
      }

      sleep(1500L);
      this.report("after setup");

      for (String command : COMMANDS) {
         this.command(command, 700L);
      }

      this.minecraft.execute(() -> {
         this.minecraft.player.lookAt(Anchor.EYES, this.minecraft.player.position().add(2.0, 0.5, 0.0));
         Screenshot.grab(this.minecraft, false);
      });
      sleep(1500L);
      this.report("after commands");
      BlockPos target = this.minecraft.player.blockPosition().offset(2, 0, 0);
      this.minecraft.execute(() -> {
         this.select(Items.DIAMOND_PICKAXE);
         this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(target));
         BedrockNative.LOGGER.info("Dev test effects: breaking {} at {}", this.minecraft.level.getBlockState(target), target);
         this.minecraft.options.keyAttack.setDown(true);
      });
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < 4000L) {
         sleep(50L);
         if (Boolean.TRUE.equals(this.onClient(() -> this.minecraft.level.getBlockState(target).isAir()))) {
            break;
         }
      }

      this.minecraft.execute(() -> this.minecraft.options.keyAttack.setDown(false));
      sleep(1500L);
      this.report("after breaking");
      this.minecraft
         .execute(
            () -> {
               this.select(Items.DIRT);
               BlockPos support = target.below();
               this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(support).add(0.0, 0.5, 0.0));
               this.minecraft
                  .gameMode
                  .useItemOn(
                     this.minecraft.player,
                     InteractionHand.MAIN_HAND,
                     new BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.5, 0.0), Direction.UP, support, false)
                  );
               this.minecraft.player.swing(InteractionHand.MAIN_HAND, this.minecraft.player.getMainHandItem().getInteractAnimation(), false);
            }
         );
      sleep(1500L);
      this.report("after placing");
      BlockPos trapdoor = target.south();
      this.minecraft
         .execute(
            () -> {
               this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(trapdoor));
               BedrockNative.LOGGER.info("Dev test effects: using {} at {}", this.minecraft.level.getBlockState(trapdoor), trapdoor);
               this.minecraft
                  .gameMode
                  .useItemOn(
                     this.minecraft.player,
                     InteractionHand.MAIN_HAND,
                     new BlockHitResult(Vec3.atCenterOf(trapdoor).add(-0.5, 0.0, 0.0), Direction.WEST, trapdoor, false)
                  );
            }
         );
      sleep(1500L);
      this.report("after using a trapdoor");
      this.walk(180.0F);
      this.walk(0.0F);
      sleep(1000L);
      this.report("after walking");
      this.command("setblock ~2 ~ ~ air", 500L);
      this.command("setblock ~2 ~ ~1 air", 500L);
   }

   private void walk(float yaw) {
      this.minecraft.execute(() -> {
         this.minecraft.player.setYRot(yaw);
         this.minecraft.options.keyUp.setDown(true);
      });
      sleep(700L);
      this.minecraft.execute(() -> this.minecraft.options.keyUp.setDown(false));
      sleep(500L);
   }

   private void report(String when) {
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info(
                  "Dev test effects ({}): {} sounds played, {} skipped, {} unmapped; {} particle events shown, {} unmapped",
                  new Object[]{
                     when,
                     this.sounds.playedCount(),
                     this.sounds.skippedCount(),
                     this.sounds.unmappedCount(),
                     this.particles.shownCount(),
                     this.particles.unmappedCount()
                  }
               )
         );
   }

   private void command(String command, long pause) {
      this.minecraft.execute(() -> {
         BedrockNative.LOGGER.info("Dev test effects: /{}", command);
         this.minecraft.getConnection().sendCommand(command);
      });
      sleep(pause);
   }

   private void select(Item item) {
      Inventory inventory = this.minecraft.player.getInventory();
      List<String> held = new ArrayList<>();

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (stack.is(item)) {
            inventory.setSelectedSlot(slot);
            return;
         }

         held.add(stack.toString());
      }

      BedrockNative.LOGGER.warn("Dev test effects: no {} in the hotbar {}", item, held);
   }

   private <T> T onClient(Supplier<T> supplier) {
      return this.minecraft.submit(supplier).join();
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
      }
   }
}
