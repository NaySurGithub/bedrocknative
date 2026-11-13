package net.bedrocknative.client.net;

import com.mojang.brigadier.tree.CommandNode;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BedrockBlockModels;
import net.bedrocknative.client.content.BedrockItemIcons;
import net.bedrocknative.client.content.PackDownloadScreen;
import net.bedrocknative.client.form.FormScreen;
import net.bedrocknative.client.form.JsonUiFormScreen;
import net.bedrocknative.client.form.PackPromptScreen;
import net.bedrocknative.client.form.SimpleFormScreen;
import net.bedrocknative.client.inventory.BedrockCreativeTabs;
import net.bedrocknative.client.inventory.BedrockSlot;
import net.bedrocknative.client.inventory.ContainerTrace;
import net.bedrocknative.client.inventory.StackRequests;
import net.bedrocknative.client.lobby.BedrockDisconnects;
import net.bedrocknative.client.model.ActorState;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.model.BedrockModels;
import net.bedrocknative.client.model.WornAttachables;
import net.bedrocknative.client.text.BedrockGlyphs;
import net.bedrocknative.client.text.BedrockNameTag;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.block.custom.BlockProblems;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockSet;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.bedrocknative.world.item.CustomItem;
import net.bedrocknative.world.item.ItemMappings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerData.ServerPackStatus;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemCategory;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.jspecify.annotations.Nullable;

public final class DevPrismaTest {
   private static final KeyEvent ESCAPE = new KeyEvent(41, 0, 0);
   private static final KeyEvent ENTER = new KeyEvent(40, 0, 0);
   private static final List<String> FORM_COMMANDS = List.of("menu", "stats");
   private static final List<String> JSON_UI_FORMS = List.of("menu", "shop", "hdv", "boutique", "daily");
   private static final int MOVE_FROM = 37;
   private static final int MOVE_TO = 9;
   private static final List<String> CUSTOM_ITEMS = List.of(
      "prisma:diamond_leggings",
      "prisma:diamond_boots",
      "prisma:diamond_sword",
      "prisma:soup",
      "prisma:diamond_helmet",
      "prisma:diamond_chestplate",
      "prisma:ruby_ingot",
      "prisma:pickaxeofgod1",
      "prisma:meteorite_ore"
   );
   private static volatile @Nullable StackRequests requests;
   private final Minecraft minecraft;
   private final String variant;
   private static final String BDS_TEST_PACK = "bd1ccd95-4246-4ee9-b0d7-f27b2fcae7de";

   private DevPrismaTest(Minecraft minecraft, String variant) {
      this.minecraft = minecraft;
      this.variant = variant;
   }

   static void attach(StackRequests sessionRequests) {
      requests = sessionRequests;
   }

   public static void start(String variant) {
      Thread.ofVirtual().name("Dev prisma test").start(() -> {
         Minecraft minecraft;
         while ((minecraft = Minecraft.getInstance()) == null) {
            sleep(200L);
         }

         DevPrismaTest test = new DevPrismaTest(minecraft, variant);

         try {
            test.run();
            log("done");
         } catch (RuntimeException e) {
            BedrockNative.LOGGER.error("Dev prisma test failed", e);
            log("done (failed: {})", e.getMessage());
         }
      });
   }

   private void run() {
      if (!this.variant.contains("cached") && !this.variant.contains("blocks")) {
         this.deleteCache();
         this.askAgain();
      }

      long started = System.currentTimeMillis();
      this.joinWorld(started);
      sleep(10000L);
      this.report("spawn");
      this.screenshot("spawn");
      this.run(() -> this.minecraft.player.setYRot(this.minecraft.player.getYRot() + 180.0F));
      sleep(2000L);
      this.screenshot("spawn-behind");
      if (this.variant.contains("creative")) {
         new DevCreativeTest(this.minecraft, requests, false).run();
         this.stopIfAsked();
      } else if (this.variant.contains("chests")) {
         this.chests();
         this.stopIfAsked();
      } else if (this.variant.contains("forms")) {
         log("forms: JSON UI mode {}", JsonUi.mode());

         for (String command : JSON_UI_FORMS) {
            this.form(command);
         }

         this.stopIfAsked();
      } else if (this.variant.contains("items")) {
         this.customItems();
         this.stopIfAsked();
      } else if (this.variant.contains("blocks")) {
         this.customBlocks();
         this.stopIfAsked();
      } else if (this.variant.contains("text")) {
         this.text();
         this.stopIfAsked();
      } else if (this.variant.contains("models")) {
         this.models();
         this.stopIfAsked();
      } else if (this.variant.contains("attachables")) {
         this.attachables();
         this.stopIfAsked();
      } else {
         this.run(() -> this.minecraft.player.setYRot(this.minecraft.player.getYRot() + 180.0F));
         this.walk();
         this.inventory();
         this.commands();

         for (String command : FORM_COMMANDS) {
            this.form(command);
         }

         this.report("end");
         this.stopIfAsked();
      }
   }

   private void stopIfAsked() {
      if (this.variant.endsWith("-stop")) {
         log("stopping the server");
         this.run(() -> this.minecraft.getConnection().sendCommand("stop"));
         sleep(5000L);
      }
   }

   private void joinWorld(long started) {
      boolean prompted = false;
      boolean downloading = false;
      long pressed = 0L;

      while (System.currentTimeMillis() - started < 300000L) {
         Screen screen = this.onClient(this::screen);
         if (screen instanceof PackPromptScreen prompt && !prompted) {
            prompted = true;
            log(
               "pack prompt after {} ms: buttons {}",
               System.currentTimeMillis() - started,
               this.onClient(() -> prompt.buttons().stream().map(button -> button.getMessage().getString()).toList())
            );
            this.screenshot("prompt");
            sleep(1000L);
            pressed = System.currentTimeMillis();
            AbstractButton download = prompt.buttons().getFirst();
            this.run(() -> download.keyPressed(ENTER));
         }

         if (screen instanceof PackDownloadScreen && !downloading) {
            downloading = true;
            this.screenshot("download");
         }

         if (BedrockDisconnects.isDisconnectScreen(screen)) {
            throw new IllegalStateException("Disconnected: " + this.onClient(() -> BedrockDisconnects.describe(screen) + " / " + this.describe(screen)));
         }

         if (this.onClient(
            () -> this.minecraft.player != null
               && this.minecraft.level != null
               && !(this.screen() instanceof PackDownloadScreen)
               && !(this.screen() instanceof PackPromptScreen)
               && this.minecraft.getConnection() != null
         )) {
            long now = System.currentTimeMillis();
            log(
               "in the world after {} ms (prompt {}, download screen {}, {} ms after the prompt answer); server packs {}, cache {}",
               now - started,
               prompted,
               downloading,
               pressed == 0L ? 0L : now - pressed,
               this.onClient(PackStack::hasServerPacks),
               this.cacheFiles()
            );
            return;
         }

         sleep(100L);
      }

      throw new IllegalStateException("Not in the world after 300 s, screen " + this.onClient(() -> this.describe(this.screen())));
   }

   private void report(String label) {
      this.run(
         () -> {
            BlockPos center = this.minecraft.player.blockPosition();
            Map<String, Integer> blocks = new TreeMap<>();
            int loaded = 0;

            for (int x = -24; x <= 24; x++) {
               for (int z = -24; z <= 24; z++) {
                  if (this.minecraft.level.hasChunk(center.getX() + x >> 4, center.getZ() + z >> 4)) {
                     loaded++;

                     for (int y = -12; y <= 12; y++) {
                        BlockState state = this.minecraft.level.getBlockState(center.offset(x, y, z));
                        if (!state.isAir()) {
                           blocks.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), 1, Integer::sum);
                        }
                     }
                  }
               }
            }

            List<Entry<String, Integer>> top = new ArrayList<>(blocks.entrySet());
            top.sort(Entry.<String, Integer>comparingByValue().reversed());
            Map<String, Integer> entities = new TreeMap<>();
            List<String> named = new ArrayList<>();

            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
               entities.merge(entity.typeHolder().getRegisteredName(), 1, Integer::sum);
               if (entity instanceof Interaction) {
                  named.add(
                     String.format(
                        Locale.ROOT,
                        "interaction box %.2fx%.2f at %.1f blocks",
                        entity.getBbWidth(),
                        entity.getBbHeight(),
                        entity.distanceTo(this.minecraft.player)
                     )
                  );
               }

               if (entity instanceof BedrockActorEntity carrier) {
                  ActorState state = BedrockActors.state(carrier);
                  named.add(
                     String.format(
                        Locale.ROOT,
                        "custom actor %s %s %.2fx%.2f at %.1f blocks",
                        carrier.bedrockIdentifier(),
                        state != null && state.instance() != null ? "drawn" : "without a definition",
                        entity.getBbWidth(),
                        entity.getBbHeight(),
                        entity.distanceTo(this.minecraft.player)
                     )
                  );
               }

               if (entity.hasCustomName() && named.size() < 12) {
                  named.add(entity.getCustomName().getString().replace('\n', '|'));
               }
            }

            log(
               "{}: position {}, chunks {}, columns loaded {}/2401, top blocks {}",
               label,
               this.minecraft.player.position(),
               this.minecraft.level.getChunkSource().getLoadedChunksCount(),
               loaded,
               top.subList(0, Math.min(15, top.size()))
            );
            log(
               "{}: unknown block ids {}, custom block states seen {}, Bedrock-only blocks seen {}",
               label,
               BlockMappings.get().unknownSeen().size(),
               BlockMappings.get().customSeen(),
               BlockMappings.get().bedrockOnlySeen()
            );
            log("{}: entities {}, named {}", label, entities, named);
            Scoreboard scoreboard = this.minecraft.level.getScoreboard();

            for (DisplaySlot slot : List.of(DisplaySlot.SIDEBAR, DisplaySlot.LIST, DisplaySlot.BELOW_NAME)) {
               Objective objective = scoreboard.getDisplayObjective(slot);
               if (objective != null) {
                  log(
                     "{}: scoreboard {} '{}': {}",
                     label,
                     slot.getSerializedName(),
                     objective.getDisplayName().getString(),
                     scoreboard.listPlayerScores(objective)
                        .stream()
                        .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed())
                        .map(entry -> entry.ownerName().getString() + "=" + entry.formatValue(StyledFormat.SIDEBAR_DEFAULT).getString())
                        .toList()
                  );
               }
            }

            log(
               "{}: health {}, food {}, game mode {}, screen {}",
               label,
               this.minecraft.player.getHealth(),
               this.minecraft.player.getFoodData().getFoodLevel(),
               this.minecraft.gameMode.getPlayerMode(),
               this.describe(this.screen())
            );
         }
      );
   }

   private void walk() {
      int teleports = MovementStats.teleports();
      Vec3 start = this.onClient(
         () -> {
            Vec3 position = this.minecraft.player.position();
            this.minecraft
               .level
               .getEntities(this.minecraft.player, this.minecraft.player.getBoundingBox().inflate(32.0), Entity::hasCustomName)
               .stream()
               .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(position)))
               .ifPresent(
                  hologram -> this.minecraft.player.setYRot((float)Math.toDegrees(Math.atan2(hologram.getX() - position.x, position.z - hologram.getZ())))
               );
            this.minecraft.player.setXRot(0.0F);
            return position;
         }
      );
      this.hold("w", 30);
      this.hold("wj", 20);
      this.hold("", 10);
      Vec3 far = this.onClient(() -> this.minecraft.player.position());
      this.hold("s", 50);
      this.hold("", 20);
      Vec3 end = this.onClient(() -> this.minecraft.player.position());
      log(
         "walk: start {}, farthest {} ({} blocks), end {} ({} from the start), server position resets {}",
         start,
         far,
         String.format(Locale.ROOT, "%.2f", far.distanceTo(start)),
         end,
         String.format(Locale.ROOT, "%.2f", end.distanceTo(start)),
         MovementStats.teleports() - teleports
      );
   }

   private void hold(String keys, int ticks) {
      for (int tick = 0; tick < ticks; tick++) {
         this.run(() -> {
            Options options = this.minecraft.options;
            options.keyUp.setDown(keys.contains("w"));
            options.keyDown.setDown(keys.contains("s"));
            options.keyJump.setDown(keys.contains("j"));
         });
         sleep(50L);
      }
   }

   private void inventory() {
      this.run(
         () -> {
            List<String> slots = new ArrayList<>();
            Inventory items = this.minecraft.player.getInventory();

            for (int slot = 0; slot < items.getContainerSize(); slot++) {
               ItemStack stack = items.getItem(slot);
               if (!stack.isEmpty()) {
                  slots.add(
                     slot
                        + "="
                        + stack.getCount()
                        + "x"
                        + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                        + (!stack.has(DataComponents.CUSTOM_NAME) && !stack.has(DataComponents.ITEM_NAME) ? "" : " '" + stack.getHoverName().getString() + "'")
                  );
               }
            }

            log("inventory: {} stacks {}", slots.size(), slots);
         }
      );
      this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(2000L);
      this.screenshot("inventory");
      int accepted = StackRequests.acceptedTotal();
      int rejected = StackRequests.rejectedTotal();
      String moved = this.onClient(() -> this.slotName(37));
      if (this.onClient(
         () -> this.minecraft.player.inventoryMenu.getSlot(9).getItem().isEmpty() && !this.minecraft.player.inventoryMenu.getSlot(37).getItem().isEmpty()
      )) {
         this.click(37);
         this.click(9);
         sleep(1000L);
         String there = this.onClient(() -> this.slotName(9));
         this.click(9);
         this.click(37);
         sleep(1000L);
         log(
            "inventory ({}): moved '{}' to slot {} ('{}' there) and back ('{}' in slot {}); requests accepted {}, rejected {}",
            this.onClient(() -> this.describe(this.screen())),
            moved,
            9,
            there,
            this.onClient(() -> this.slotName(37)),
            37,
            StackRequests.acceptedTotal() - accepted,
            StackRequests.rejectedTotal() - rejected
         );
      } else {
         log("inventory: no move (slot {} '{}', slot {} '{}')", 37, moved, 9, this.onClient(() -> this.slotName(9)));
      }

      this.run(() -> {
         if (this.screen() != null) {
            this.screen().onClose();
         }
      });
      sleep(500L);
   }

   private void customItems() {
      String player = this.onClient(() -> this.minecraft.player.getGameProfile().name());
      List<String> added = this.giveMissing(player, CUSTOM_ITEMS);
      if (added.contains("prisma:ruby_ingot")) {
         this.command("give " + player + " prisma:ruby_ingot 70");
      }

      sleep(2000L);
      this.run(
         () -> {
            Inventory items = this.minecraft.player.getInventory();

            for (int slot = 0; slot < items.getContainerSize(); slot++) {
               ItemStack stack = items.getItem(slot);
               CustomItem custom = ItemMappings.custom(stack);
               if (custom != null) {
                  Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
                  StackRequests session = requests;
                  CreativeItemData entry = session == null ? null : session.creative().find(stack);
                  log(
                     "custom item slot {}: '{}' {} x{} max stack {} (Bedrock {}), max damage {} (Bedrock {}), worn {}, armor {}, icon {}, creative entry {}",
                     slot,
                     stack.getHoverName().getString(),
                     custom.identifier(),
                     stack.getCount(),
                     stack.getMaxStackSize(),
                     custom.maxStackSize(),
                     stack.getMaxDamage(),
                     custom.maxDurability(),
                     equippable == null ? "-" : equippable.slot().getName(),
                     armorOf(stack, equippable),
                     BedrockItemIcons.icon(custom.identifier()) != null ? "yes" : "NO",
                     entry == null ? "none" : entry.getItem().getDefinition().getIdentifier()
                  );
               }
            }
         }
      );
      this.select("prisma:diamond_sword");
      this.run(() -> this.minecraft.player.setXRot(10.0F));
      sleep(1500L);
      this.screenshot("items-hand");
      this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(1500L);
      this.screenshot("items-inventory");

      for (CreativeItemCategory category : BedrockCreativeTabs.categories()) {
         this.run(() -> this.showTab(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(BedrockCreativeTabs.javaTab(category)).getPath()));
         sleep(800L);
         this.run(
            () -> log(
               "creative {}: {} server-defined items of {} entries",
               category,
               BedrockCreativeTabs.entries(category).stream().filter(stack -> ItemMappings.custom(stack) != null).count(),
               BedrockCreativeTabs.entries(category).size()
            )
         );
         this.screenshot("items-creative-" + category.name().toLowerCase(Locale.ROOT));
      }

      int accepted = StackRequests.acceptedTotal();
      int rejected = StackRequests.rejectedTotal();
      Integer helmet = added.contains("prisma:diamond_helmet") ? this.onClient(() -> this.menuSlot("prisma:diamond_helmet")) : null;
      if (helmet != null) {
         this.click(helmet);
         this.click(5);
      }

      Integer chestplate = added.contains("prisma:diamond_chestplate") ? this.onClient(() -> this.menuSlot("prisma:diamond_chestplate")) : null;
      if (chestplate != null) {
         this.quickMove(chestplate);
      }

      sleep(1000L);
      log(
         "armor by click and shift-click: requests accepted {}, rejected {}",
         StackRequests.acceptedTotal() - accepted,
         StackRequests.rejectedTotal() - rejected
      );
      this.reportArmor();
      this.closeScreen();

      for (String piece : List.of("prisma:diamond_leggings", "prisma:diamond_boots")) {
         if (added.contains(piece)) {
            this.select(piece);
            this.run(() -> this.minecraft.player.setXRot(-80.0F));
            sleep(600L);
            this.run(() -> log("use {}: {}", piece, this.minecraft.gameMode.useItem(this.minecraft.player, InteractionHand.MAIN_HAND)));
            sleep(1500L);
         }
      }

      this.reportArmor();
      this.run(() -> {
         this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player));
         this.showInventoryTab();
      });
      sleep(1500L);
      this.screenshot("items-worn");
      this.closeScreen();
      this.run(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
      sleep(1500L);
      this.screenshot("items-third-person");
      this.run(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));
      added.forEach(item -> this.command("clear " + player + " " + item));
      sleep(1500L);
      this.reportArmor();
   }

   private List<String> giveMissing(String player, List<String> identifiers) {
      List<String> held = new ArrayList<>();
      int free = this.onClient(() -> {
         Inventory inventory = this.minecraft.player.getInventory();
         int empty = 0;

         for (ItemStack stack : inventory.getNonEquipmentItems()) {
            empty += stack.isEmpty() ? 1 : 0;
         }

         for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            CustomItem custom = ItemMappings.custom(inventory.getItem(slot));
            if (custom != null) {
               held.add(custom.identifier());
            }
         }

         return empty;
      });
      List<String> added = new ArrayList<>();

      for (String identifier : identifiers) {
         if (!held.contains(identifier) && added.size() < free) {
            added.add(identifier);
            this.command("give " + player + " " + identifier);
         } else {
            log("not giving {}: {}", identifier, held.contains(identifier) ? "the player holds it" : "no free slot");
         }
      }

      return added;
   }

   private void customBlocks() {
      CustomBlockSet set = CustomBlockSet.current();
      log("custom blocks: {} states in slots, {} past the pool, blocks {}", set.size(), set.overflow(), set.blocks());
      this.run(() -> {
         List<BlockProblems.Problem> problems = BedrockBlockModels.problems();
         Map<BlockProblems.Reason, Long> byReason = new TreeMap<>();
         problems.forEach(problem -> byReason.merge(problem.reason(), 1L, Long::sum));
         log("custom block problems: {} blocks and reasons {} (each also logged once as a 'Bedrock block problem' line)", problems.size(), byReason);
         log("custom block flipbooks playing: {}", BedrockBlockModels.animations());
      });
      Map<String, BlockPos> nearest = new TreeMap<>();
      this.run(
         () -> {
            BlockPos center = this.minecraft.player.blockPosition();
            Map<String, Integer> counts = new TreeMap<>();

            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-32, -12, -32), center.offset(32, 12, 32))) {
               CustomBlockState custom = CarrierBlock.data(this.minecraft.level.getBlockState(pos));
               if (custom != null) {
                  String name = custom.toString();
                  counts.merge(name, 1, Integer::sum);
                  BlockPos best = nearest.get(name);
                  if (best == null || pos.distSqr(center) < best.distSqr(center)) {
                     nearest.put(name, pos.immutable());
                  }
               }
            }

            log("custom blocks within 32: {}", counts);
            nearest.forEach(
               (namex, posx) -> {
                  BlockState state = this.minecraft.level.getBlockState(posx);
                  CustomBlockState customx = CarrierBlock.data(state);
                  log(
                     "nearest {} at {}: geometry {}, collision {}, selection {}, light {}, model {} ({} quads), sound {}",
                     namex,
                     posx,
                     customx.geometry().identifier(),
                     state.getCollisionShape(this.minecraft.level, posx).isEmpty() ? "none" : state.getCollisionShape(this.minecraft.level, posx).bounds(),
                     state.getShape(this.minecraft.level, posx).isEmpty() ? "none" : state.getShape(this.minecraft.level, posx).bounds(),
                     state.getLightEmission(),
                     BedrockBlockModels.model(state) != null ? "yes" : "NO",
                     BedrockBlockModels.quadCount(state),
                     state.getSoundType().getBreakSound().location().getPath()
                  );
               }
            );
         }
      );
      int shots = 0;

      for (Entry<String, BlockPos> entry : nearest.entrySet()) {
         if (shots++ >= 6) {
            break;
         }

         this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(entry.getValue())));
         sleep(1200L);
         this.screenshot("blocks-" + entry.getKey().replaceAll("[^a-z0-9_]+", "_"));
      }

      String player = this.onClient(() -> this.minecraft.player.getGameProfile().name());
      List<String> items = List.of(
         "prisma:box", "prisma:meteorite_ore", "prisma:amelioration_table", "prisma:farming_chest", "prisma:totem", "prisma:prism_crop"
      );
      List<String> added = this.giveMissing(player, items);
      sleep(1500L);
      this.run(
         () -> {
            Inventory inventory = this.minecraft.player.getInventory();

            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
               CustomItem custom = ItemMappings.custom(inventory.getItem(slot));
               if (custom != null && items.contains(custom.identifier())) {
                  log(
                     "block item {} in slot {}: block {}, block model {}, icon {}",
                     custom.identifier(),
                     slot,
                     custom.block(),
                     custom.block() != null && BedrockBlockModels.item(custom.block()) != null ? "yes" : "NO",
                     custom.icon()
                  );
               }
            }

            this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player));
         }
      );
      sleep(1500L);
      this.screenshot("blocks-inventory");
      this.closeScreen();
      added.forEach(item -> this.command("clear " + player + " " + item));
   }

   private void showInventoryTab() {
      this.showTab("inventory");
   }

   private void showTab(String name) {
      if (this.screen() instanceof CreativeModeInventoryScreen creative) {
         try {
            Method select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            select.setAccessible(true);
            select.invoke(
               creative,
               BuiltInRegistries.CREATIVE_MODE_TAB
                  .stream()
                  .filter(tab -> BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).getPath().equals(name))
                  .findFirst()
                  .orElseThrow()
            );
         } catch (ReflectiveOperationException e) {
            log("no {} tab: {}", name, e.toString());
         }
      }
   }

   private void closeScreen() {
      this.run(() -> {
         if (this.screen() != null) {
            this.screen().onClose();
         }
      });
      sleep(500L);
   }

   private static String armorOf(ItemStack stack, @Nullable Equippable equippable) {
      if (equippable == null) {
         return "-";
      }

      double[] armor = new double[]{0.0};
      stack.forEachModifier(equippable.slot(), (attribute, modifier) -> {
         if (attribute.is(Attributes.ARMOR)) {
            armor[0] += modifier.amount();
         }
      });
      return String.valueOf((int)armor[0]);
   }

   private void reportArmor() {
      this.run(
         () -> {
            StackRequests session = requests;
            List<String> worn = new ArrayList<>();

            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
               ItemData server = session == null ? ItemData.AIR : session.inventory().get(BedrockSlot.armor(3 - slot.getIndex()));
               ItemStack stack = this.minecraft.player.getItemBySlot(slot);
               CustomItem java = ItemMappings.custom(stack);
               worn.add(
                  slot.getName()
                     + "="
                     + (java != null ? java.identifier() : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())
                     + " (server "
                     + (server != null && !server.isNull() ? server.getDefinition().getIdentifier() : "empty")
                     + ")"
               );
            }

            log("worn armor: {}; armor bar {}", worn, this.minecraft.player.getArmorValue());
         }
      );
   }

   private @Nullable Integer menuSlot(String identifier) {
      InventoryMenu menu = this.minecraft.player.inventoryMenu;

      for (int slot = 9; slot < 45; slot++) {
         CustomItem custom = ItemMappings.custom(menu.getSlot(slot).getItem());
         if (custom != null && custom.identifier().equals(identifier)) {
            return slot;
         }
      }

      return null;
   }

   private void select(String identifier) {
      this.run(() -> {
         Inventory items = this.minecraft.player.getInventory();

         for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            CustomItem custom = ItemMappings.custom(items.getItem(slot));
            if (custom != null && custom.identifier().equals(identifier)) {
               items.setSelectedSlot(slot);
               return;
            }
         }

         log("{} is not in the hotbar", identifier);
      });
      sleep(500L);
   }

   private void quickMove(int slot) {
      this.run(
         () -> {
            if (this.screen() instanceof CreativeModeInventoryScreen) {
               this.minecraft.player.inventoryMenu.clicked(slot, 0, ContainerInput.QUICK_MOVE, this.minecraft.player);
               this.minecraft.player.inventoryMenu.broadcastChanges();
            } else {
               this.minecraft
                  .gameMode
                  .handleContainerInput(this.minecraft.player.inventoryMenu.containerId, slot, 0, ContainerInput.QUICK_MOVE, this.minecraft.player);
            }
         }
      );
      sleep(500L);
   }

   private void command(String command) {
      this.run(() -> this.minecraft.getConnection().sendCommand(command));
      sleep(1200L);
   }

   private String slotName(int slot) {
      ItemStack stack = this.minecraft.player.inventoryMenu.getSlot(slot).getItem();
      return stack.isEmpty() ? "" : stack.getHoverName().getString();
   }

   private void click(int slot) {
      this.run(
         () -> {
            if (this.screen() instanceof CreativeModeInventoryScreen) {
               this.minecraft.player.inventoryMenu.clicked(slot, 0, ContainerInput.PICKUP, this.minecraft.player);
               this.minecraft.player.inventoryMenu.broadcastChanges();
            } else {
               this.minecraft
                  .gameMode
                  .handleContainerInput(this.minecraft.player.inventoryMenu.containerId, slot, 0, ContainerInput.PICKUP, this.minecraft.player);
            }
         }
      );
      sleep(500L);
   }

   private void commands() {
      this.run(() -> {
         List<String> names = this.minecraft.getConnection().getCommands().getRoot().getChildren().stream().<String>map(CommandNode::getName).sorted().toList();
         log("commands: {} root commands {}", names.size(), names);
      });
   }

   private void chests() {
      ContainerTrace.enable();
      int accepted0 = requests == null ? 0 : requests.accepted();
      int rejected0 = requests == null ? 0 : requests.rejected();
      int chestsBefore = this.onClient(this::chestBlocksAround);
      log("chests: {} chest blocks around the player before", chestsBefore);
      ItemStack movedStack = null;
      boolean[] opened = new boolean[2];
      boolean[] kept = new boolean[2];
      boolean[] restored = new boolean[2];
      boolean moved = false;
      boolean persisted = false;
      boolean returned = false;

      for (int round = 0; round < 2; round++) {
         log("chests: round {} sends /pv 1", round + 1);
         this.run(() -> this.minecraft.getConnection().sendCommand("pv 1"));
         long started = System.currentTimeMillis();
         String last = "";
         int changes = 0;
         int duringBlocks = 0;

         while (System.currentTimeMillis() - started < 6000L) {
            String now = this.onClient(
               () -> {
                  Screen shown = this.screen();
                  return (shown == null ? "none" : shown.getClass().getSimpleName())
                     + " window "
                     + this.minecraft.player.containerMenu.containerId
                     + " slots "
                     + this.minecraft.player.containerMenu.slots.size();
               }
            );
            if (!now.equals(last)) {
               changes++;
               log("chests: round {} +{} ms: {}", round + 1, System.currentTimeMillis() - started, now);
               last = now;
            }

            duringBlocks = Math.max(duringBlocks, this.onClient(this::chestBlocksAround));
            sleep(50L);
         }

         boolean showing = this.onClient(() -> this.screen() instanceof AbstractContainerScreen && this.minecraft.player.containerMenu.slots.size() == 90);
         opened[round] = showing;
         kept[round] = showing && changes <= 4;
         log(
            "chests: round {}: window {} after 6 s ({} state changes, up to {} chest blocks around)",
            round + 1,
            showing ? "STAYS OPEN" : "is NOT open",
            changes,
            duringBlocks
         );
         this.screenshot("chests-" + (round + 1));
         if (showing) {
            AbstractContainerMenu menu = this.onClient(() -> this.minecraft.player.containerMenu);
            if (round == 0) {
               int slot = this.onClient(() -> this.firstItemSlot(54));
               if (slot >= 0) {
                  movedStack = this.onClient(() -> menu.getSlot(slot).getItem().copy());
                  int requestsBefore = requests == null ? 0 : requests.accepted();
                  this.quickMove(slot, menu);
                  sleep(1500L);
                  ItemStack wanted = movedStack;
                  moved = this.onClient(() -> this.findVaultSlot(wanted) >= 0);
                  log(
                     "chests: moved '{}' x{} from inventory slot {} into the vault: in the vault {}, requests accepted {} -> {}",
                     movedStack.getHoverName().getString(),
                     movedStack.getCount(),
                     slot,
                     moved,
                     requestsBefore,
                     requests == null ? 0 : requests.accepted()
                  );
                  this.screenshot("chests-1-moved");
               } else {
                  log("chests: no item in the inventory to move");
               }
            } else if (movedStack != null) {
               ItemStack wanted = movedStack;
               int vaultSlot = this.onClient(() -> this.findVaultSlot(wanted));
               persisted = vaultSlot >= 0;
               log("chests: round 2 finds '{}' in the vault (kept by the server): {}", wanted.getHoverName().getString(), persisted);
               if (persisted) {
                  this.quickMove(vaultSlot, menu);
                  sleep(1500L);
                  returned = this.onClient(() -> this.findVaultSlot(wanted) < 0);
                  log("chests: moved it back out: vault slot {} now '{}'", vaultSlot, this.onClient(() -> this.slotNameAt(vaultSlot)));
               }
            }

            this.closeScreen();
            sleep(3000L);
         } else {
            log("chests: the window is gone; screen now {}", this.describe(this.onClient(this::screen)));
         }

         int after = this.onClient(this::chestBlocksAround);
         restored[round] = after == chestsBefore;
         log(
            "chests: round {} after closing: {} chest blocks around (before {}): {}",
            round + 1,
            after,
            chestsBefore,
            restored[round] ? "restored" : "NOT RESTORED"
         );
         sleep(1500L);
      }

      log(
         "chests: summary opened {}/{}, steady (no flicker) {}/{}, blocks restored {}/{}, stack moved in {}, kept by the server {}, moved back {}; requests accepted {} rejected {}",
         opened[0],
         opened[1],
         kept[0],
         kept[1],
         restored[0],
         restored[1],
         moved,
         persisted,
         returned,
         requests == null ? 0 : requests.accepted() - accepted0,
         requests == null ? 0 : requests.rejected() - rejected0
      );
      log("chests: {}", opened[0] && opened[1] && moved && persisted && restored[0] && restored[1] ? "PASS" : "FAIL");
   }

   private int chestBlocksAround() {
      BlockPos center = this.minecraft.player.blockPosition();
      int count = 0;

      for (int x = -6; x <= 6; x++) {
         for (int y = -3; y <= 3; y++) {
            for (int z = -6; z <= 6; z++) {
               if (this.minecraft.level.getBlockState(center.offset(x, y, z)).getBlock() instanceof ChestBlock) {
                  count++;
               }
            }
         }
      }

      return count;
   }

   private int firstItemSlot(int from) {
      return this.firstItemSlot(from, this.minecraft.player.containerMenu.slots.size());
   }

   private int firstItemSlot(int from, int to) {
      AbstractContainerMenu menu = this.minecraft.player.containerMenu;

      for (int slot = from; slot < to && slot < menu.slots.size(); slot++) {
         if (!menu.getSlot(slot).getItem().isEmpty()) {
            return slot;
         }
      }

      return -1;
   }

   private int findVaultSlot(ItemStack stack) {
      AbstractContainerMenu menu = this.minecraft.player.containerMenu;

      for (int slot = 0; slot < 54 && slot < menu.slots.size(); slot++) {
         if (ItemStack.isSameItemSameComponents(menu.getSlot(slot).getItem(), stack) && menu.getSlot(slot).getItem().getCount() == stack.getCount()) {
            return slot;
         }
      }

      return -1;
   }

   private String slotNameAt(int slot) {
      ItemStack stack = this.minecraft.player.containerMenu.getSlot(slot).getItem();
      return stack.isEmpty() ? "empty" : stack.getHoverName().getString() + " x" + stack.getCount();
   }

   private void quickMove(int slot, AbstractContainerMenu menu) {
      this.run(() -> this.minecraft.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, this.minecraft.player));
      sleep(500L);
   }

   private void form(String command) {
      this.run(() -> this.minecraft.getConnection().sendCommand(command));
      long started = System.currentTimeMillis();

      FormScreen form;
      for (form = null; System.currentTimeMillis() - started < 8000L && form == null; sleep(100L)) {
         if (this.onClient(this::screen) instanceof FormScreen shown) {
            form = shown;
         }
      }

      if (form == null) {
         log("form /{}: none within 8 s", command);
      } else {
         FormScreen shown = form;
         sleep(2500L);
         this.screenshot("form-" + command);
         log(
            "form /{}: {} '{}' {}",
            command,
            shown.getClass().getSimpleName(),
            this.onClient(() -> shown.getTitle().getString()),
            shown instanceof SimpleFormScreen simple
               ? this.onClient(() -> simple.buttons().stream().map(button -> button.getMessage().getString()).toList())
               : ""
         );
         if (shown instanceof JsonUiFormScreen json) {
            log("form /{}: JSON UI layouts shown {}, {} controls, problems {}", command, this.onClient(() -> {
               UiControl router = json.ui().instance().find("long_form_router");
               return router == null ? List.of() : router.children().stream().filter(UiControl::visible).map(UiControl::name).toList();
            }), this.onClient(() -> json.ui().instance().controlCount()), this.onClient(() -> json.ui().instance().problems().toString()));
         }

         this.run(() -> shown.keyPressed(ESCAPE));
         sleep(1000L);
      }
   }

   private String describe(@Nullable Screen screen) {
      if (screen == null) {
         return "none";
      }

      StringBuilder text = new StringBuilder(screen.getClass().getSimpleName());
      if (screen instanceof DisconnectedScreen) {
         screen.children().forEach(child -> text.append(' ').append(child));
      }

      return text.toString();
   }

   private void askAgain() {
      ServerList servers = new ServerList(this.minecraft);
      servers.load();
      ServerData server = servers.get("127.0.0.1");
      if (server != null) {
         server.setResourcePackStatus(ServerPackStatus.PROMPT);
         servers.save();
      }
   }

   private void deleteCache() {
      Path cache = FabricLoader.getInstance().getConfigDir().resolve("bedrocknative").resolve("packs");

      try (Stream<Path> files = Files.exists(cache) ? Files.list(cache) : Stream.empty()) {
         for (Path file : files.toList()) {
            if (!file.getFileName().toString().startsWith("bd1ccd95-4246-4ee9-b0d7-f27b2fcae7de")) {
               Files.deleteIfExists(file);
            }
         }
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
   }

   private List<String> cacheFiles() {
      Path cache = FabricLoader.getInstance().getConfigDir().resolve("bedrocknative").resolve("packs");

      try (Stream<Path> files = Files.exists(cache) ? Files.list(cache) : Stream.empty()) {
         return files.sorted(Comparator.naturalOrder()).map(file -> {
            try {
               return file.getFileName() + " " + Files.size(file);
            } catch (IOException e) {
               return file.getFileName().toString();
            }
         }).toList();
      } catch (IOException e) {
         return List.of();
      }
   }

   private @Nullable Screen screen() {
      return this.minecraft.gui == null ? null : this.minecraft.gui.screen();
   }

   private void attachables() {
      boolean loaded = false;

      for (int wait = 0; wait < 300 && !loaded; wait++) {
         loaded = this.onClient(() -> BedrockModels.registry() != null);
         if (!loaded) {
            sleep(100L);
         }
      }

      ModelRegistry registry = this.onClient(BedrockModels::registry);
      if (registry == null) {
         log("attachables: the registry never loaded");
      } else {
         log(
            "attachables: prisma armour attachables in the stack: {}",
            this.onClient(() -> CUSTOM_ITEMS.stream().filter(id -> registry.attachableByIdentifier(id) != null).toList())
         );
         this.run(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
         sleep(2000L);

         for (Player player : this.onClient(() -> List.copyOf(this.minecraft.level.players()))) {
            if (!(player.distanceTo(this.minecraft.player) > 32.0F)) {
               List<String> slots = this.onClient(
                  () -> {
                     List<String> running = new ArrayList<>();

                     for (WornAttachables.Slot slot : WornAttachables.Slot.values()) {
                        String definition = WornAttachables.definition(player, slot, false);
                        String item = BuiltInRegistries.ITEM.getKey(player.getItemBySlot(slot.equipment()).getItem()).toString();
                        CustomItem custom = ItemMappings.custom(player.getItemBySlot(slot.equipment()));
                        running.add(
                           slot.bedrock()
                              + "="
                              + (custom != null ? custom.identifier() : item)
                              + (definition != null ? " (attachable " + definition + ")" : "")
                        );
                     }

                     return running;
                  }
               );
               log("attachables: {}{}: {}", player.getName().getString(), player == this.minecraft.player ? " (you)" : "", slots);
            }
         }

         this.screenshot("attachables-front");
         this.run(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
         sleep(1500L);
         this.screenshot("attachables-back");
         this.run(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));
         sleep(1000L);
         this.screenshot("attachables-first-person");
      }
   }

   private void models() {
      boolean loaded = false;

      for (int wait = 0; wait < 300 && !loaded; wait++) {
         loaded = this.onClient(() -> BedrockModels.registry() != null);
         if (!loaded) {
            sleep(100L);
         }
      }

      ModelRegistry registry = this.onClient(BedrockModels::registry);
      if (registry == null) {
         log("models: the registry never loaded");
      } else {
         log(
            "models: {} files, {} client entities; goblin king {}, goblin minion {}, claim box {}, player override from a server pack {}",
            registry.files(),
            registry.entities().size(),
            registry.entity("prisma:goblin_king") != null,
            registry.entity("prisma:goblin_minion") != null,
            registry.entity("prisma:claim_box") != null,
            registry.entity("minecraft:player") != null && registry.entity("minecraft:player").serverPack()
         );
         BedrockActors actors = this.onClient(BedrockActors::current);
         log(
            "models: server actor identifiers outside minecraft: {}",
            this.onClient(() -> actors.identifiers().keySet().stream().filter(id -> !id.startsWith("minecraft:")).toList())
         );
         Map<String, Long> before = this.modelReport("models start");
         sleep(5000L);
         Map<String, Long> after = this.modelReport("models after 5 s");
         after.forEach((actor, frames) -> log("models: {} evaluated {} frames in 5 s", actor, frames - before.getOrDefault(actor, frames)));
         this.screenshot("models");
         BedrockActorEntity claim = this.onClient(() -> this.nearestCarrier("prisma:claim_box"));
         if (claim != null) {
            long framesBefore = this.onClient(() -> BedrockActors.state(claim) != null ? BedrockActors.state(claim).evaluations() : 0L);
            this.run(() -> {
               Vec3 eye = this.minecraft.player.getEyePosition();
               this.minecraft.player.setYRot((float)Math.toDegrees(Math.atan2(claim.getZ() - eye.z, claim.getX() - eye.x)) - 90.0F);
               this.minecraft.player.setXRot(-75.0F);
            });
            sleep(2000L);
            long framesAfter = this.onClient(() -> BedrockActors.state(claim) != null ? BedrockActors.state(claim).evaluations() : 0L);
            log(
               "models: claim box at {} ({} blocks), looking up its bounds: {} frames drawn in 2 s, texture {}",
               claim.position(),
               String.format(Locale.ROOT, "%.1f", this.onClient(() -> claim.distanceTo(this.minecraft.player))),
               framesAfter - framesBefore,
               this.onClient(() -> {
                  ActorState state = BedrockActors.state(claim);
                  return state != null && state.lastSnapshot() != null ? state.lastSnapshot().texturePath(0) : null;
               })
            );
            this.screenshot("models-claim-up");
            this.run(() -> this.minecraft.player.setXRot(0.0F));
         } else {
            log("models: no claim box in range");
         }

         BedrockActorEntity goblin = this.onClient(() -> {
            BedrockActorEntity king = this.nearestCarrier("prisma:goblin_king");
            return king != null ? king : this.nearestCarrier("prisma:goblin_minion");
         });
         if (goblin != null) {
            this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, goblin.getEyePosition()));

            for (int sample = 0; sample < 5; sample++) {
               sleep(1000L);
               log(
                  "models: goblin {} at {} blocks: ground speed {}, runtime animations {}",
                  goblin.bedrockIdentifier(),
                  String.format(Locale.ROOT, "%.1f", this.onClient(() -> goblin.distanceTo(this.minecraft.player))),
                  this.onClient(() -> goblin.motion().groundSpeed()),
                  this.onClient(() -> {
                     ActorState state = BedrockActors.state(goblin);
                     return state != null && state.instance() != null ? state.instance().runtimeAnimations() : List.of();
                  })
               );
            }

            this.screenshot("models-goblin");
         } else {
            log("models: no goblin in range (they come with a raid)");
         }
      }
   }

   private void text() {
      log("text: glyph pages in Java's font {}", this.onClient(() -> BedrockGlyphs.pages().stream().sorted().map(page -> String.format("%02X", page)).toList()));
      log("text: name tags {}", this.onClient(() -> DevTextTest.describeTags(this.minecraft, 64.0)));
      List<Entity> floating = this.onClient(
         () -> {
            List<Entity> shown = new ArrayList<>();

            for (Entity entityx : this.minecraft.level.entitiesForRendering()) {
               BedrockNameTag tagx = BedrockNameTag.of(entityx);
               if (tagx != null
                  && tagx.joined() != null
                  && tagx.alwaysShown(entityx)
                  && entityx != this.minecraft.player
                  && entityx.distanceTo(this.minecraft.player) < 64.0) {
                  shown.add(entityx);
               }
            }

            shown.sort(Comparator.comparingDouble(entityxx -> entityxx.distanceTo(this.minecraft.player)));
            return shown.subList(0, Math.min(3, shown.size()));
         }
      );

      for (int i = 0; i < floating.size(); i++) {
         Entity entity = floating.get(i);
         BedrockNameTag tag = BedrockNameTag.of(entity);
         this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, new Vec3(entity.getX(), entity.getY() + tag.boxHeight(entity) + 0.6, entity.getZ())));
         sleep(1500L);
         this.screenshot("text-floating-" + i);
      }

      this.run(() -> this.minecraft.gui.setScreen(new ChatScreen("", false)));
      sleep(1000L);
      this.screenshot("text-chat");
      this.run(() -> this.minecraft.gui.setScreen(null));
      log(
         "text: sidebar {}",
         this.onClient(
            () -> {
               Scoreboard scoreboard = this.minecraft.level.getScoreboard();
               Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
               return objective == null
                  ? "none"
                  : objective.getDisplayName().getString()
                     + " "
                     + scoreboard.listPlayerScores(objective).stream().map(entry -> entry.ownerName().getString()).toList();
            }
         )
      );
   }

   private Map<String, Long> modelReport(String label) {
      return this.onClient(
         () -> {
            Map<String, Long> frames = new TreeMap<>();
            List<String> lines = new ArrayList<>();

            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
               if (entity instanceof BedrockActorEntity carrier) {
                  ActorState state = BedrockActors.state(carrier);
                  String key = carrier.bedrockIdentifier() + "#" + carrier.getId();
                  frames.put(key, state != null ? state.evaluations() : 0L);
                  lines.add(
                     String.format(
                        Locale.ROOT,
                        "%s %s, %.1f blocks, box %.2fx%.2f, texture %s",
                        key,
                        state != null ? state : "?",
                        carrier.distanceTo(this.minecraft.player),
                        carrier.getBbWidth(),
                        carrier.getBbHeight(),
                        state != null && state.lastSnapshot() != null ? state.lastSnapshot().texturePath(0) : "-"
                     )
                  );
               }
            }

            log("{}: {} custom actors in range {}", label, lines.size(), lines);
            return frames;
         }
      );
   }

   private @Nullable BedrockActorEntity nearestCarrier(String identifier) {
      BedrockActorEntity nearest = null;

      for (Entity entity : this.minecraft.level.entitiesForRendering()) {
         if (entity instanceof BedrockActorEntity carrier
            && carrier.bedrockIdentifier().equals(identifier)
            && (nearest == null || entity.distanceToSqr(this.minecraft.player) < nearest.distanceToSqr(this.minecraft.player))) {
            nearest = carrier;
         }
      }

      return nearest;
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, "prisma-" + name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void run(Runnable task) {
      this.onClient(() -> {
         task.run();
         return null;
      });
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev prisma test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
