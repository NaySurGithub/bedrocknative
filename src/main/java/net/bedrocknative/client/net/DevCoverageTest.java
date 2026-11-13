package net.bedrocknative.client.net;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;

final class DevCoverageTest {
   private static volatile boolean transferred;
   private final Minecraft minecraft;
   private final MovementStats movement;
   private final IntSupplier blockEvents;
   private final IntSupplier bossBars;
   private final Consumer<BedrockPacket> inject;

   DevCoverageTest(Minecraft minecraft, MovementStats movement, IntSupplier blockEvents, IntSupplier bossBars, Consumer<BedrockPacket> inject) {
      this.minecraft = minecraft;
      this.movement = movement;
      this.blockEvents = blockEvents;
      this.bossBars = bossBars;
      this.inject = inject;
   }

   void run() {
      if (transferred) {
         this.report(
            "after transfer",
            () -> "position "
               + this.minecraft.player.position()
               + ", tab list "
               + this.minecraft.getConnection().getListedOnlinePlayers().size()
               + ", helmet "
               + this.minecraft.player.getItemBySlot(EquipmentSlot.HEAD).getItem()
         );
         this.screenshot("coverage-transfer");
         this.commands("replaceitem entity @s slot.armor.head 0 air");
         BedrockNative.LOGGER.info("Dev coverage test: done");
      } else {
         this.report(
            "join", () -> "tab list " + this.minecraft.getConnection().getListedOnlinePlayers().size() + ", air " + this.minecraft.player.getAirSupply()
         );
         this.commands("gamemode creative", "clear @s", "gamerule mobgriefing false", "weather clear");
         BlockPos origin = this.supply(() -> this.minecraft.player.blockPosition());
         int x = origin.getX();
         int y = origin.getY();
         int z = origin.getZ();
         this.commands(
            "fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x + 4) + " " + (y + 5) + " " + (z + 3) + " air",
            "fill " + (x - 3) + " " + (y - 1) + " " + (z - 3) + " " + (x + 4) + " " + (y - 1) + " " + (z + 3) + " stone"
         );
         this.localData(x, y, z);
         this.chest(new BlockPos(x + 2, y, z));
         this.painting(x, y, z);
         this.riding(x, y, z);
         this.bossBar(x, y, z);
         this.cooldown();
         this.dimension(x, y, z);
         this.commands(
            "gamerule mobgriefing true",
            "gamemode creative",
            "fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x + 4) + " " + (y + 5) + " " + (z + 3) + " air"
         );
         this.commands("replaceitem entity @s slot.armor.head 0 diamond_helmet");
         this.report(
            "before transfer",
            () -> this.movement.count()
               + " corrections in the session "
               + this.movement.since(0L)
               + ", helmet "
               + this.minecraft.player.getItemBySlot(EquipmentSlot.HEAD).getItem()
         );
         transferred = true;
         TransferPacket transfer = new TransferPacket();
         transfer.setAddress("127.0.0.1");
         String joined = this.supply(() -> this.minecraft.getCurrentServer() == null ? "" : this.minecraft.getCurrentServer().ip);
         transfer.setPort(joined.contains(":") ? Integer.parseInt(joined.substring(joined.lastIndexOf(58) + 1)) : 19132);
         this.inject.accept(transfer);
      }
   }

   private void localData(int x, int y, int z) {
      this.commands("gamemode survival", "effect @s regeneration 30 4 true", "setblock " + x + " " + y + " " + z + " fire");
      this.waitFor(() -> this.minecraft.player.isOnFire(), 4000L);
      this.report("fire", () -> "on fire " + this.minecraft.player.isOnFire());
      this.screenshot("coverage-fire");
      this.commands("fill " + (x - 1) + " " + y + " " + (z - 1) + " " + (x + 1) + " " + (y + 2) + " " + (z + 1) + " water");
      sleep(4000L);
      this.report(
         "underwater",
         () -> "air " + this.minecraft.player.getAirSupply() + "/" + this.minecraft.player.getMaxAirSupply() + ", on fire " + this.minecraft.player.isOnFire()
      );
      this.screenshot("coverage-air");
      this.commands(
         "fill " + (x - 1) + " " + y + " " + (z - 1) + " " + (x + 1) + " " + (y + 2) + " " + (z + 1) + " air",
         "effect @s clear",
         "tp @s " + x + " " + y + " " + z,
         "gamemode creative"
      );
      sleep(3000L);
      this.report("out of the water", () -> "air " + this.minecraft.player.getAirSupply());
   }

   private void chest(BlockPos pos) {
      int before = this.blockEvents.getAsInt();
      this.commands("setblock " + at(pos) + " chest");
      sleep(500L);
      this.look(pos);
      this.minecraft
         .execute(
            () -> this.minecraft
               .gameMode
               .useItemOn(
                  this.minecraft.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(-0.5, 0.0, 0.0), Direction.WEST, pos, false)
               )
         );
      sleep(1500L);
      this.report(
         "chest open",
         () -> "block events "
            + (this.blockEvents.getAsInt() - before)
            + ", lid "
            + this.lid(pos)
            + ", screen "
            + (this.minecraft.gui.screen() == null ? "none" : this.minecraft.gui.screen().getClass().getSimpleName())
      );
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(1500L);
      this.report("chest closed", () -> "block events " + (this.blockEvents.getAsInt() - before) + ", lid " + this.lid(pos));
      this.commands("setblock " + at(pos) + " air");
   }

   private float lid(BlockPos pos) {
      return this.minecraft.level.getBlockEntity(pos) instanceof ChestBlockEntity chest ? chest.getOpenNess(1.0F) : -1.0F;
   }

   private void painting(int x, int y, int z) {
      BlockPos clicked = new BlockPos(x + 3, y + 1, z);
      this.commands("fill " + (x + 3) + " " + y + " " + (z - 2) + " " + (x + 3) + " " + (y + 4) + " " + (z + 2) + " stone");
      this.hold(Items.PAINTING, "painting");
      this.look(clicked);
      this.minecraft
         .execute(
            () -> this.minecraft
               .gameMode
               .useItemOn(
                  this.minecraft.player,
                  InteractionHand.MAIN_HAND,
                  new BlockHitResult(Vec3.atCenterOf(clicked).add(-0.5, 0.0, 0.0), Direction.WEST, clicked, false)
               )
         );
      sleep(1500L);
      this.report(
         "painting",
         () -> {
            List<Painting> paintings = this.paintings();
            return paintings.isEmpty()
               ? "none"
               : paintings.stream()
                  .map(
                     painting -> painting.getVariant().getRegisteredName()
                        + " at "
                        + at(painting.getPos())
                        + " facing "
                        + painting.getDirection()
                        + " (placed against "
                        + at(clicked)
                        + ", expected anchor "
                        + at(clicked.relative(Direction.WEST))
                        + ")"
                  )
                  .toList()
                  .toString();
         }
      );
      this.screenshot("coverage-painting");
      this.commands("kill @e[type=painting]");
      sleep(800L);
      this.report("painting removed", () -> this.paintings().size() + " left");
      this.commands("fill " + (x + 3) + " " + y + " " + (z - 2) + " " + (x + 3) + " " + (y + 4) + " " + (z + 2) + " air");
   }

   private List<Painting> paintings() {
      return this.minecraft.level.getEntitiesOfClass(Painting.class, this.minecraft.player.getBoundingBox().inflate(8.0));
   }

   private void riding(int x, int y, int z) {
      this.commands(
         "kill @e[type=pig]", "kill @e[type=boat]", "summon boat " + (x + 2) + " " + y + " " + (z + 2), "summon pig " + (x + 2) + " " + y + " " + (z - 2)
      );
      sleep(500L);
      this.commands("ride @e[type=pig,c=1] start_riding @e[type=boat,c=1] teleport_rider");
      sleep(1000L);
      this.report(
         "riding",
         () -> {
            Entity pig = this.minecraft
               .level
               .getEntitiesOfClass(Entity.class, this.minecraft.player.getBoundingBox().inflate(8.0), entity -> entity.getType() == EntityTypes.PIG)
               .stream()
               .findFirst()
               .orElse(null);
            return pig == null ? "no pig" : "pig vehicle " + (pig.getVehicle() == null ? "none" : pig.getVehicle().typeHolder().getRegisteredName());
         }
      );
      this.screenshot("coverage-riding");
      this.commands("ride @e[type=pig] stop_riding");
      sleep(800L);
      this.report(
         "dismounted",
         () -> this.minecraft.level.getEntitiesOfClass(Entity.class, this.minecraft.player.getBoundingBox().inflate(8.0), Entity::isPassenger).size()
            + " passengers"
      );
      this.commands("kill @e[type=pig]", "kill @e[type=boat]");
      this.localRiding(x, y, z);
   }

   private void localRiding(int x, int y, int z) {
      this.commands("gamemode survival", "summon boat " + (x + 2) + " " + y + " " + (z + 1));
      sleep(800L);
      this.board(EntityTypes.OAK_BOAT);
      int vehicleCorrections = this.movement.vehicleCount();
      this.report("in the boat", this::seat);
      this.screenshot("coverage-boat");
      this.minecraft.execute(() -> this.minecraft.options.keyUp.setDown(true));
      sleep(1500L);
      this.minecraft.execute(() -> this.minecraft.options.keyUp.setDown(false));
      sleep(1000L);
      this.report("rowed", () -> this.seat() + ", " + (this.movement.vehicleCount() - vehicleCorrections) + " vehicle corrections");
      this.leave();
      this.commands("kill @e[type=boat]", "setblock " + (x + 2) + " " + y + " " + (z - 2) + " rail", "summon minecart " + (x + 2) + " " + y + " " + (z - 2));
      sleep(800L);
      this.board(EntityTypes.MINECART);
      this.report("in the minecart", this::seat);
      this.screenshot("coverage-minecart");
      this.leave();
      this.commands(
         "kill @e[type=minecart]",
         "setblock " + (x + 2) + " " + y + " " + (z - 2) + " air",
         "summon pig " + (x - 2) + " " + y + " " + z + " 0 0 minecraft:on_saddled"
      );
      sleep(800L);
      this.board(EntityTypes.PIG);
      this.report("on the pig", this::seat);
      this.screenshot("coverage-pig");
      this.leave();
      this.commands("kill @e[type=pig]", "gamemode creative");
   }

   private void board(EntityType<?> type) {
      this.minecraft
         .execute(
            () -> this.minecraft
               .level
               .getEntitiesOfClass(Entity.class, this.minecraft.player.getBoundingBox().inflate(8.0), entity -> entity.getType() == type)
               .stream()
               .findFirst()
               .ifPresent(
                  vehicle -> {
                     this.minecraft.player.lookAt(Anchor.EYES, vehicle.position());
                     this.minecraft
                        .gameMode
                        .interact(
                           this.minecraft.player, vehicle, new EntityHitResult(vehicle, vehicle.position().add(0.0, 0.3, 0.0)), InteractionHand.MAIN_HAND
                        );
                  }
               )
         );
      this.waitFor(() -> this.minecraft.player.isPassenger(), 3000L);
   }

   private void leave() {
      this.minecraft.execute(() -> this.minecraft.options.keyShift.setDown(true));
      sleep(150L);
      this.minecraft.execute(() -> this.minecraft.options.keyShift.setDown(false));
      sleep(1500L);
      this.report("left", () -> this.seat() + ", " + this.movement.count() + " corrections in the session");
   }

   private String seat() {
      Entity vehicle = this.minecraft.player.getVehicle();
      return (vehicle == null ? "not riding" : "riding " + vehicle.typeHolder().getRegisteredName() + " at " + vehicle.position())
         + ", player at "
         + this.minecraft.player.position();
   }

   private void bossBar(int x, int y, int z) {
      this.commands("summon wither " + x + " " + (y + 2) + " " + (z + 3));
      sleep(2000L);
      this.report("wither", () -> this.bossBars.getAsInt() + " boss bars");
      this.screenshot("coverage-boss");
      this.commands("kill @e[type=wither]");
      this.waitFor(() -> this.bossBars.getAsInt() == 0, 15000L);
      this.report("wither killed", () -> this.bossBars.getAsInt() + " boss bars");
      this.commands("tp @e[type=wither] ~ -100 ~");
   }

   private void cooldown() {
      this.commands("gamemode survival");
      this.hold(Items.ENDER_PEARL, "ender_pearl 4");
      this.minecraft.execute(() -> {
         this.minecraft.player.setXRot(-60.0F);
         this.minecraft.gameMode.useItem(this.minecraft.player, InteractionHand.MAIN_HAND);
         this.minecraft.player.getCooldowns().removeCooldown(Identifier.withDefaultNamespace("ender_pearl"));
      });
      sleep(400L);
      this.report("ender pearl", () -> "on cooldown " + this.minecraft.player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL)));
      this.screenshot("coverage-cooldown");
      sleep(2500L);
      this.hold(Items.BOW, "bow");
      this.commands("give @s arrow 4");
      this.minecraft.execute(() -> {
         this.minecraft.player.setXRot(-30.0F);
         this.minecraft.options.keyUse.setDown(true);
      });
      sleep(1200L);
      this.report("bow drawn", () -> "using " + this.minecraft.player.isUsingItem());
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(false));
      sleep(1000L);
      this.commands("gamemode creative", "clear @s", "kill @e[type=arrow]");
   }

   private void dimension(int x, int y, int z) {
      this.commands(
         "gamemode creative",
         "clear @s",
         "give @s diamond_sword",
         "give @s stone 5",
         "replaceitem entity @s slot.armor.chest 0 iron_chestplate",
         "replaceitem entity @s slot.weapon.offhand 0 shield",
         "effect @s jump_boost 300 1 true",
         "xp 5L @s"
      );
      this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(1));
      this.doubleJump();
      this.report("before the nether", this::keptState);
      int corrections = this.movement.count();
      long tick = this.movement.lastTick();
      this.commands("execute in nether run tp @s 0 100 0");
      this.waitFor(() -> this.minecraft.level.dimension() == Level.NETHER && this.minecraft.getConnection().hasClientLoaded(), 60000L);
      sleep(1000L);
      this.report("kept in the nether", this::keptState);
      this.commands("fill -2 99 -2 2 103 2 air", "fill -2 98 -2 2 98 2 glass", "testfor @s[hasitem={item=stone,location=slot.weapon.mainhand}]");
      this.doubleJump();
      sleep(3000L);
      this.report(
         "nether",
         () -> this.minecraft.level.dimension().identifier()
            + " at "
            + this.minecraft.player.position()
            + ", chunk loaded "
            + this.minecraft.level.hasChunk(this.minecraft.player.getBlockX() >> 4, this.minecraft.player.getBlockZ() >> 4)
      );
      this.screenshot("coverage-nether");
      this.walk();
      this.report(
         "walked in the nether",
         () -> "at " + this.minecraft.player.position() + ", " + (this.movement.count() - corrections) + " corrections since the change"
      );
      this.commands("execute in overworld run tp @s " + x + " " + y + " " + z);
      this.waitFor(() -> this.minecraft.level.dimension() == Level.OVERWORLD && this.minecraft.getConnection().hasClientLoaded(), 60000L);
      sleep(4000L);
      this.report("overworld", () -> this.minecraft.level.dimension().identifier() + " at " + this.minecraft.player.position());
      this.report("kept in the overworld", this::keptState);
      this.commands("testfor @s[hasitem={item=stone,location=slot.weapon.mainhand}]");
      this.screenshot("coverage-overworld");
      this.walk();
      this.report(
         "walked in the overworld",
         () -> "at "
            + this.minecraft.player.position()
            + ", "
            + (this.movement.count() - corrections)
            + " corrections since the change "
            + this.movement.since(tick)
      );
      this.commands("effect @s clear", "xp -5L @s");
   }

   private String keptState() {
      LocalPlayer player = this.minecraft.player;
      return "slot "
         + player.getInventory().getSelectedSlot()
         + " "
         + player.getMainHandItem().getItem()
         + ", hotbar 0 "
         + player.getInventory().getItem(0).getItem()
         + ", chest "
         + player.getItemBySlot(EquipmentSlot.CHEST).getItem()
         + ", offhand "
         + player.getOffhandItem().getItem()
         + ", flying "
         + player.getAbilities().flying
         + ", mayfly "
         + player.getAbilities().mayfly
         + ", jump boost "
         + player.hasEffect(MobEffects.JUMP_BOOST)
         + ", level "
         + player.experienceLevel;
   }

   private void doubleJump() {
      for (int press = 0; press < 2; press++) {
         this.minecraft.execute(() -> this.minecraft.options.keyJump.setDown(true));
         sleep(120L);
         this.minecraft.execute(() -> this.minecraft.options.keyJump.setDown(false));
         sleep(120L);
      }

      sleep(1000L);
   }

   private void walk() {
      this.minecraft.execute(() -> this.minecraft.options.keyUp.setDown(true));
      sleep(1000L);
      this.minecraft.execute(() -> this.minecraft.options.keyUp.setDown(false));
      sleep(1000L);
   }

   private void hold(Item item, String give) {
      this.commands("clear @s", "give @s " + give);
      sleep(500L);
      this.minecraft.execute(() -> {
         for (int slot = 0; slot < 9; slot++) {
            if (this.minecraft.player.getInventory().getItem(slot).is(item)) {
               this.minecraft.player.getInventory().setSelectedSlot(slot);
               return;
            }
         }
      });
      sleep(500L);
   }

   private void look(BlockPos pos) {
      this.minecraft.execute(() -> this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(pos)));
      sleep(300L);
   }

   private void waitFor(BooleanSupplier condition, long timeout) {
      long end = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < end && !this.supply(condition::getAsBoolean)) {
         sleep(250L);
      }
   }

   private void report(String label, Supplier<String> state) {
      BedrockNative.LOGGER.info("Dev coverage test {}: {}", label, this.supply(state));
   }

   private <T> T supply(Supplier<T> value) {
      return CompletableFuture.supplyAsync(value, this.minecraft::execute).join();
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.minecraft.execute(() -> {
            BedrockNative.LOGGER.info("Dev test command: /{}", command);
            this.minecraft.getConnection().sendCommand(command);
         });
         sleep(400L);
      }

      sleep(400L);
   }

   private void screenshot(String name) {
      this.minecraft
         .execute(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
   }

   private static String at(BlockPos pos) {
      return pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
