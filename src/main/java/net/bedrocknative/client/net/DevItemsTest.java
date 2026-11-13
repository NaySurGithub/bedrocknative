package net.bedrocknative.client.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.inventory.BedrockCreativeTabs;
import net.bedrocknative.client.inventory.BedrockInventory;
import net.bedrocknative.client.inventory.BedrockSlot;
import net.bedrocknative.client.inventory.StackRequests;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemCategory;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;

final class DevItemsTest {
   private static final long STEP = 600L;
   private static final Set<String> DATA_ITEMS = Set.of(
      "minecraft:potion",
      "minecraft:splash_potion",
      "minecraft:lingering_potion",
      "minecraft:arrow",
      "minecraft:enchanted_book",
      "minecraft:goat_horn",
      "minecraft:suspicious_stew",
      "minecraft:firework_rocket",
      "minecraft:firework_star",
      "minecraft:banner",
      "minecraft:ominous_bottle",
      "minecraft:bed",
      "minecraft:spawn_egg"
   );
   private static final String[] MAP_DESTINATIONS = new String[]{
      "monument",
      "mansion",
      "buriedtreasure",
      "village_snowy",
      "village_taiga",
      "village_plains",
      "village_savanna",
      "village_desert",
      "jungle_temple",
      "swamp_hut",
      "trial_chambers",
      "buried_ancient_city",
      "buried_mineshaft",
      "desert_pyramid",
      "warm_ocean_ruins",
      "abandoned_camp_dappled_forest",
      "abandoned_camp_bamboo_jungle",
      "abandoned_camp_birch_forest",
      "abandoned_camp_cherry_grove",
      "abandoned_camp_extreme_hills_plus_trees",
      "abandoned_camp_flower_forest",
      "abandoned_camp_pale_garden",
      "abandoned_camp_swampland"
   };
   private final Minecraft minecraft;
   private final StackRequests requests;
   private final BedrockInventory inventory;
   private int checks;

   DevItemsTest(Minecraft minecraft, StackRequests requests) {
      this.minecraft = minecraft;
      this.requests = requests;
      this.inventory = requests.inventory();
   }

   void run() {
      this.run(() -> this.minecraft.options.pauseOnLostFocus = false);
      this.commands("time set noon", "weather clear", "kill @e[family=monster]");
      if (this.onClient(() -> this.minecraft.player.isDeadOrDying())) {
         log("the player is dead; respawning");
         this.run(() -> this.minecraft.player.respawn());
         sleep(4000L);
      }

      this.commands("gamemode survival", "clear @s", "effect @s clear", "kill @e[family=monster]", "effect @s regeneration 600 255 true");
      sleep(1000L);
      if ("items-shulker".equals(System.getProperty("bedrocknative.devTest"))) {
         this.shulkerContents();
         log("done (shulker only)");
      } else if ("items-smooth".equals(System.getProperty("bedrocknative.devTest"))) {
         this.itemSmoothness();
         log("done (item smoothness only)");
      } else if ("items-maps".equals(System.getProperty("bedrocknative.devTest"))) {
         this.explorerMaps();
         log("done (explorer maps only)");
      } else {
         this.creative();
         this.javaCreative();
         if ("items-coverage".equals(System.getProperty("bedrocknative.devTest"))) {
            log("done (coverage only)");
         } else {
            this.stewRecipes();
            this.givenData();
            this.lockedItems();
            this.mapAndCompass();
            this.explorerMaps();
            this.convertedOnly();
            this.shulkerContents();
            this.armor();
            this.dyedArmor();
            this.offhand();
            this.durability();
            this.run(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            this.commands("clear @s", "effect @s clear");
            log("done, {} server checks sent; requests accepted {}, rejected {}", this.checks, this.requests.accepted(), this.requests.rejected());
         }
      }
   }

   private void itemSmoothness() {
      Vec3 start = this.onClient(() -> this.minecraft.player.position());
      int bx = (int)Math.floor(start.x);
      int by = 200;
      int bz = (int)Math.floor(start.z);
      this.commands(
         "gamemode survival",
         "clear @s",
         "give @s dirt 64",
         "fill " + (bx - 9) + " " + (by - 1) + " " + (bz - 9) + " " + (bx + 9) + " " + (by - 1) + " " + (bz + 9) + " stone",
         "fill " + (bx - 9) + " " + by + " " + (bz - 9) + " " + (bx + 9) + " " + (by + 6) + " " + (bz + 9) + " air",
         "tp @s " + (bx + 0.5) + " " + by + " " + (bz + 0.5) + " 0 -15"
      );
      sleep(800L);
      this.run(() -> {
         this.select(Items.DIRT);
         this.minecraft.player.setXRot(-15.0F);
         this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      });
      sleep(500L);
      Map<Integer, TreeMap<Integer, double[]>> paths = new ConcurrentHashMap<>();
      boolean[] running = new boolean[]{true};
      boolean[] fromChest = new boolean[]{false};
      List<String> csv = new CopyOnWriteArrayList<>();
      Thread sampler = new Thread(
         () -> {
            long lastFrame = 0L;
            long stalledUntil = 0L;

            while (running[0]) {
               List<double[]> frame;
               try {
                  boolean chestx = fromChest[0];
                  frame = this.onClient(
                     () -> {
                        List<double[]> seen = new ArrayList<>();
                        long now = System.nanoTime();
                        float partial = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);

                        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
                           if (entity instanceof ItemEntity item && item.getItem().is(Items.DIRT)) {
                              seen.add(
                                 new double[]{
                                    item.getId(),
                                    item.tickCount,
                                    item.xo,
                                    item.yo,
                                    item.zo,
                                    item.getX(),
                                    item.getY(),
                                    item.getZ(),
                                    chestx ? 1.0 : 0.0,
                                    now,
                                    partial,
                                    0.0
                                 }
                              );
                           }
                        }

                        return seen;
                     }
                  );
               } catch (RuntimeException e) {
                  break;
               }

               long returned = System.nanoTime();
               if (lastFrame != 0L && returned - lastFrame > 100000000L) {
                  stalledUntil = returned + 250000000L;
               }

               lastFrame = returned;

               for (double[] atx : frame) {
                  atx[11] = returned < stalledUntil ? 1.0 : 0.0;
                  TreeMap<Integer, double[]> path = paths.computeIfAbsent((int)atx[0], id -> new TreeMap<>());
                  double[] old = path.get((int)atx[1]);
                  if (old != null && old[11] == 1.0) {
                     atx[11] = 1.0;
                  }

                  csv.add(Arrays.stream(atx).mapToObj(Double::toString).collect(Collectors.joining(",")));
                  path.put((int)atx[1], atx);
               }

               sleep(2L);
            }
         },
         "dev-item-sampler"
      );
      sampler.setDaemon(true);
      sampler.start();

      for (int drop = 0; drop < 4; drop++) {
         this.run(() -> this.minecraft.gameMode.dropItem(this.minecraft.player, false));
         if (drop == 0) {
            sleep(150L);
            this.run(() -> this.minecraft.player.setYRot(90.0F));
            this.burst("items-smooth-hotbar");
         }

         sleep(400L);
      }

      sleep(3000L);
      BlockPos[] chest = new BlockPos[1];
      this.run(() -> chest[0] = this.minecraft.player.blockPosition().offset(2, 0, 0));
      String at = chest[0].getX() + " " + chest[0].getY() + " " + chest[0].getZ();
      this.commands(
         "setblock " + at + " chest", "replaceitem block " + at + " slot.container 0 dirt 64", "replaceitem block " + at + " slot.container 1 dirt 64"
      );
      sleep(1000L);
      this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(chest[0])));
      sleep(300L);
      this.run(
         () -> this.minecraft
            .gameMode
            .useItemOn(
               this.minecraft.player,
               InteractionHand.MAIN_HAND,
               new BlockHitResult(Vec3.atCenterOf(chest[0]).add(-0.5, 0.0, 0.0), Direction.WEST, chest[0], false)
            )
      );
      sleep(1500L);
      boolean opened = this.onClient(() -> this.minecraft.player.containerMenu != this.minecraft.player.inventoryMenu);
      log("chest open: {}", opened);
      fromChest[0] = true;
      this.run(() -> {
         this.minecraft.player.setYRot(90.0F);
         this.minecraft.player.setXRot(-15.0F);
      });
      sleep(300L);

      for (int drop = 0; drop < 4; drop++) {
         int number = drop;
         this.run(() -> {
            AbstractContainerMenu menu = this.minecraft.player.containerMenu;
            this.minecraft.gameMode.handleContainerInput(menu.containerId, number % 2, number < 2 ? 0 : 1, ContainerInput.THROW, this.minecraft.player);
         });
         if (drop == 3) {
            sleep(150L);
            this.run(() -> {
               this.minecraft.player.closeContainer();
               this.minecraft.player.setYRot(180.0F);
            });
            this.burst("items-smooth-chest");
         }

         sleep(400L);
      }

      sleep(3000L);
      this.run(() -> this.minecraft.player.closeContainer());
      sleep(500L);
      running[0] = false;
      sleep(100L);

      try {
         Files.write(Path.of("items-smooth-samples.csv"), csv);
      } catch (IOException e) {
         log("could not write the samples: {}", e.toString());
      }

      for (int group = 0; group < 2; group++) {
         this.smoothnessVerdict(paths, group);
      }

      this.commands(
         "clear @s",
         "kill @e[type=item]",
         "setblock " + at + " air",
         "fill " + (bx - 9) + " " + (by - 1) + " " + (bz - 9) + " " + (bx + 9) + " " + (by - 1) + " " + (bz + 9) + " air",
         "tp @s " + start.x + " " + start.y + " " + start.z
      );
      this.run(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));
      sleep(1500L);
   }

   private void burst(String name) {
      for (int shot = 0; shot < 16; shot++) {
         sleep(70L);
         String file = String.format("%s-%02d.png", name, shot);
         this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, file, this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      }
   }

   private void smoothnessVerdict(Map<Integer, TreeMap<Integer, double[]>> paths, int group) {
      int items = 0;
      int pairs = 0;
      int broken = 0;
      int hitches = 0;
      int jumps = 0;
      int skipped = 0;
      double gapMax = 0.0;
      double stepMax = 0.0;
      double changeMax = 0.0;

      for (TreeMap<Integer, double[]> path : paths.values()) {
         if ((int)path.firstEntry().getValue()[8] == group) {
            items++;
            double[] before = null;
            double[] beforeStep = null;
            int beforeTick = 0;
            double[] lengths = new double[]{0.0, 0.0};

            for (Entry<Integer, double[]> entry : path.entrySet()) {
               double[] now = entry.getValue();
               if (now[11] == 1.0) {
                  skipped++;
                  before = null;
                  lengths[0] = 0.0;
                  lengths[1] = 0.0;
               } else {
                  double[] step = new double[]{now[5] - now[2], now[6] - now[3], now[7] - now[4]};
                  double length = Math.sqrt(step[0] * step[0] + step[1] * step[1] + step[2] * step[2]);
                  stepMax = Math.max(stepMax, length);
                  if (before != null && entry.getKey() == beforeTick + 1) {
                     pairs++;
                     double gap = Math.sqrt(Math.pow(now[2] - before[5], 2.0) + Math.pow(now[3] - before[6], 2.0) + Math.pow(now[4] - before[7], 2.0));
                     gapMax = Math.max(gapMax, gap);
                     if (gap > 1.0E-4) {
                        broken++;
                     }

                     double change = Math.sqrt(
                        Math.pow(step[0] - beforeStep[0], 2.0) + Math.pow(step[1] - beforeStep[1], 2.0) + Math.pow(step[2] - beforeStep[2], 2.0)
                     );
                     changeMax = Math.max(changeMax, change);
                     if (change > 0.35 || length > 0.9) {
                        jumps++;
                     }

                     if (lengths[0] > 0.05 && lengths[1] < 1.0E-4 && length > 0.05) {
                        hitches++;
                     }
                  } else {
                     lengths[0] = 0.0;
                     lengths[1] = 0.0;
                  }

                  lengths[0] = lengths[1];
                  lengths[1] = length;
                  before = now;
                  beforeStep = step;
                  beforeTick = entry.getKey();
               }
            }
         }
      }

      boolean pass = items >= 2 && pairs > 20 && broken == 0 && hitches == 0 && jumps == 0;
      log(
         "{}: item smoothness ({}): {} items, {} tick pairs, continuity gap {}, largest step {} blocks, largest step change {}, {} broken, {} hitches, {} jumps ({} ticks skipped after frame stalls)",
         pass ? "PASS" : "FAIL",
         group == 0 ? "hotbar" : "chest",
         items,
         pairs,
         String.format("%.5f", gapMax),
         String.format("%.3f", stepMax),
         String.format("%.3f", changeMax),
         broken,
         hitches,
         jumps,
         skipped
      );
   }

   private void creative() {
      List<ItemStack> shown = new ArrayList<>();
      this.run(() -> {
         List<CreativeItemData> contents = this.requests.creative().contents();
         List<String> barriers = new ArrayList<>();
         int data = 0;

         for (CreativeItemData entry : contents) {
            ItemData item = entry.getItem();
            ItemStack java = ItemMappings.toJava(item);
            String identifier = item.getDefinition().getIdentifier();
            if (java.is(Items.BARRIER) && !identifier.equals("minecraft:barrier")) {
               barriers.add(identifier + ":" + item.getDamage());
            }

            if (DATA_ITEMS.contains(identifier) || item.getTag() != null) {
               data++;
               log("creative {}:{} {} -> {}", identifier, item.getDamage(), item.getTag(), this.describe(java));
               if (shown.size() < 12 && (item.getDamage() != 0 || item.getTag() != null) && shown.stream().noneMatch(stack -> stack.is(java.getItem()))) {
                  shown.add(java);
               }
            }
         }

         log("creative: {} entries, {} with data, shown as a barrier: {}", contents.size(), data, barriers);
      });
      this.tooltips("creative data items", shown);
   }

   private void javaCreative() {
      this.run(
         () -> {
            CreativeModeTabs.tryRebuildTabContents(this.minecraft.player.connection.enabledFeatures(), true, this.minecraft.level.registryAccess());
            int headers = 0;
            int shown = 0;
            int same = 0;
            int other = 0;
            Map<String, Integer> missing = new TreeMap<>();

            for (CreativeItemCategory category : BedrockCreativeTabs.categories()) {
               headers += (int)BedrockCreativeTabs.javaTab(category).getDisplayItems().stream().filter(BedrockCreativeTabs::isHeader).count();

               for (ItemStack stack : BedrockCreativeTabs.entries(category)) {
                  CreativeItemData entry = BedrockCreativeTabs.entry(stack);
                  CreativeItemData found = this.requests.creative().find(stack);
                  shown++;
                  if (found == null) {
                     missing.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), 1, Integer::sum);
                  } else if (found == entry) {
                     same++;
                  } else {
                     other++;
                  }
               }
            }

            log(
               "creative tabs: {} entries in {} groups; the Java-stack lookup finds the same entry for {}, another entry of the same Java stack for {}, none for {}: {}",
               shown,
               headers,
               same,
               other,
               missing.values().stream().mapToInt(Integer::intValue).sum(),
               missing
            );
         }
      );
   }

   private void stewRecipes() {
      this.run(
         () -> {
            for (ShapelessRecipeData recipe : this.requests.crafting().shapeless()) {
               for (ItemData result : recipe.getResults()) {
                  if (result.getDefinition().getIdentifier().equals("minecraft:suspicious_stew")) {
                     log(
                        "stew recipe: {} -> data {}",
                        recipe.getIngredients().stream().map(ItemDescriptorWithCount::toString).collect(Collectors.joining(", ")),
                        result.getDamage()
                     );
                  }
               }
            }
         }
      );
   }

   private void givenData() {
      this.commands(
         "clear @s",
         "give @s diamond_pickaxe 1 0 {\"can_destroy\":{\"blocks\":[\"stone\",\"dirt\"]},\"can_place_on\":{\"blocks\":[\"grass_block\"]}}",
         "give @s apple 3 0 {\"item_lock\":{\"mode\":\"lock_in_slot\"}}",
         "give @s bread 2 0 {\"item_lock\":{\"mode\":\"lock_in_inventory\"},\"keep_on_death\":{}}",
         "loot give @s loot \"bedrocknative/items\"",
         "scriptevent bedrocknative:items",
         "give @s lodestone_compass",
         "give @s player_head",
         "give @s crossbow"
      );
      sleep(2000L);
      List<ItemStack> shown = new ArrayList<>();
      this.run(() -> {
         Inventory items = this.minecraft.player.getInventory();

         for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = items.getItem(slot);
            if (!stack.isEmpty()) {
               ItemData bedrock = this.inventory.item(slot);
               log("given slot {}: {}:{} {} -> {}", slot, bedrock.getDefinition().getIdentifier(), bedrock.getDamage(), bedrock.getTag(), this.describe(stack));
               shown.add(stack.copy());
            }
         }
      });
      this.tooltips("given items 1", shown.subList(0, Math.min(6, shown.size())));
      if (shown.size() > 6) {
         this.tooltips("given items 2", shown.subList(6, Math.min(12, shown.size())));
      }

      if (shown.size() > 12) {
         this.tooltips("given items 3", shown.subList(12, Math.min(18, shown.size())));
      }

      this.chargeCrossbow();
   }

   private void explorerMaps() {
      Object[] last = new Object[]{null};

      for (String destination : MAP_DESTINATIONS) {
         this.commands("clear @s", "loot give @s loot \"bedrocknative/maps/" + destination + "\"");

         for (int wait = 0;
            wait < 60 && this.onClient(() -> this.inventory.item(0).isNull() || Objects.equals(this.inventory.item(0).getTag(), last[0]));
            wait++
         ) {
            sleep(250L);
         }

         last[0] = this.onClient(() -> this.inventory.item(0).getTag());
         this.run(
            () -> {
               ItemData bedrock = this.inventory.item(0);
               log(
                  "explorer map {}: {}:{} {} -> {}",
                  destination,
                  bedrock.isNull() ? "nothing" : bedrock.getDefinition().getIdentifier(),
                  bedrock.getDamage(),
                  bedrock.getTag(),
                  this.describe(this.minecraft.player.getInventory().getItem(0))
               );
            }
         );
      }
   }

   private void convertedOnly() {
      this.commands("clear @s", "give @s fishing_rod 1 192");
      sleep(1000L);
      this.run(
         () -> {
            ItemData rod = this.inventory.item(0);
            ItemStack java = this.minecraft.player.getInventory().getItem(0);
            log(
               "fishing rod {}:{} {} -> damage {}/{} (Bedrock 192/384)",
               rod.getDefinition().getIdentifier(),
               rod.getDamage(),
               rod.getTag(),
               java.getDamageValue(),
               java.getMaxDamage()
            );
            ItemData pot = ItemData.builder()
               .definition(
                  this.requests
                     .creative()
                     .contents()
                     .stream()
                     .map(CreativeItemData::getItem)
                     .filter(item -> item.getDefinition().getIdentifier().equals("minecraft:decorated_pot"))
                     .findFirst()
                     .orElseThrow()
                     .getDefinition()
               )
               .count(1)
               .tag(
                  NbtMap.builder()
                     .putList(
                        "sherds",
                        NbtType.STRING,
                        List.of("minecraft:angler_pottery_sherd", "minecraft:brick", "minecraft:heart_pottery_sherd", "minecraft:skull_pottery_sherd")
                     )
                     .build()
               )
               .build();
            log("decorated pot {} -> {}", pot.getTag(), this.describe(ItemMappings.toJava(pot)));
         }
      );
   }

   private void chargeCrossbow() {
      this.commands("clear @s", "give @s crossbow", "give @s arrow 4");
      sleep(600L);
      this.run(() -> {
         this.select(Items.CROSSBOW);
         this.minecraft.player.setXRot(-60.0F);
      });
      sleep(600L);
      this.run(() -> this.minecraft.options.keyUse.setDown(true));
      sleep(1800L);
      this.run(() -> this.minecraft.options.keyUse.setDown(false));
      sleep(1500L);
      this.run(
         () -> {
            ItemData bedrock = this.inventory.held();
            log(
               "crossbow after charging: {} {} -> {}",
               bedrock.getDefinition().getIdentifier(),
               bedrock.getTag(),
               this.describe(this.minecraft.player.getMainHandItem())
            );
         }
      );
   }

   private void lockedItems() {
      this.commands(
         "clear @s",
         "give @s apple 3 0 {\"item_lock\":{\"mode\":\"lock_in_slot\"}}",
         "give @s bread 2 0 {\"item_lock\":{\"mode\":\"lock_in_inventory\"},\"keep_on_death\":{}}"
      );
      sleep(1500L);
      List<ItemStack> shown = new ArrayList<>();
      this.run(() -> {
         shown.add(this.minecraft.player.getInventory().getItem(0).copy());
         shown.add(this.minecraft.player.getInventory().getItem(1).copy());
      });
      this.tooltips("locked items", shown);
      this.openInventory();
      int rejected = this.onClient(this.requests::rejected);
      this.clickItem(Items.APPLE, 0, ContainerInput.PICKUP, "pick up the slot-locked apples");
      this.clickFirstEmpty(9, "put them elsewhere");
      this.clickItem(Items.BREAD, 0, ContainerInput.THROW, "Q on the inventory-locked bread");
      this.clickItem(Items.BREAD, 0, ContainerInput.PICKUP, "pick up the inventory-locked bread");
      this.clickFirstEmpty(9, "move it inside the inventory");
      this.closeInventory();
      log("locked items: {} requests rejected", this.onClient(this.requests::rejected) - rejected);
      this.check("locked apple stayed in hotbar slot 0", has("apple", "slot.hotbar", 0, 3));
      this.check("locked bread not dropped", has("bread", null, -1, 2));
   }

   private void shulkerContents() {
      BlockPos[] box = new BlockPos[1];
      this.run(() -> box[0] = this.minecraft.player.blockPosition().offset(2, 0, 0));
      String at = box[0].getX() + " " + box[0].getY() + " " + box[0].getZ();
      this.commands(
         "clear @s",
         "setblock " + at + " red_shulker_box",
         "replaceitem block " + at + " slot.container 0 diamond 5",
         "replaceitem block " + at + " slot.container 4 golden_apple 2",
         "setblock " + at + " air destroy"
      );
      sleep(1000L);
      this.command("tp @e[type=item,r=6] @s");

      for (int wait = 0;
         wait < 20 && !this.onClient(() -> this.minecraft.player.getInventory().contains(stack -> stack.is(Items.DYED_SHULKER_BOX.red())));
         wait++
      ) {
         sleep(500L);
      }

      List<ItemStack> shown = new ArrayList<>();
      this.run(() -> {
         Inventory items = this.minecraft.player.getInventory();

         for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = items.getItem(slot);
            if (!this.inventory.item(slot).isNull() || !stack.isEmpty()) {
               ItemData bedrock = this.inventory.item(slot);
               log("shulker slot {}: {} {} -> {}", slot, bedrock.getDefinition().getIdentifier(), bedrock.getTag(), this.describe(stack));
               shown.add(stack.copy());
            }
         }
      });
      this.tooltips("shulker box with contents", shown);
      this.check("shulker box picked up", has("red_shulker_box", null, -1, 1));
   }

   private void mapAndCompass() {
      this.commands("clear @s", "loot give @s loot \"bedrocknative/items\"", "give @s compass");
      sleep(1500L);
      this.run(() -> {
         this.select(Items.OCEAN_MONUMENT_MAP);
         this.minecraft.player.setXRot(40.0F);
      });
      sleep(3000L);
      this.run(() -> {
         ItemStack map = this.minecraft.player.getMainHandItem();
         MapId id = map.get(DataComponents.MAP_ID);
         MapItemSavedData data = id == null ? null : this.minecraft.level.getMapData(id);
         int painted = 0;
         if (data != null) {
            for (byte color : data.colors) {
               if (color != 0) {
                  painted++;
               }
            }
         }

         log("map {} in hand: id {}, data {}, {} painted pixels", map, id, data != null, painted);
         Screenshot.grab(this.minecraft, false);
      });
      BlockPos[] lodestone = new BlockPos[1];
      this.run(() -> lodestone[0] = this.minecraft.player.blockPosition().offset(2, 0, 0));
      this.command("setblock " + lodestone[0].getX() + " " + lodestone[0].getY() + " " + lodestone[0].getZ() + " lodestone");
      sleep(800L);
      this.run(() -> {
         this.select(Items.COMPASS);
         this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(lodestone[0]));
      });
      sleep(600L);
      this.run(
         () -> this.minecraft
            .gameMode
            .useItemOn(
               this.minecraft.player,
               InteractionHand.MAIN_HAND,
               new BlockHitResult(Vec3.atCenterOf(lodestone[0]).add(-0.5, 0.0, 0.0), Direction.WEST, lodestone[0], false)
            )
      );
      sleep(2500L);
      this.run(
         () -> {
            ItemStack compass = this.minecraft.player.getMainHandItem();
            ItemData bedrock = this.inventory.held();
            log(
               "compass after use on the lodestone at {}: {} {} -> {}",
               lodestone[0],
               bedrock.getDefinition().getIdentifier(),
               bedrock.getTag(),
               compass.get(DataComponents.LODESTONE_TRACKER)
            );
         }
      );
      this.check("lodestone compass server-side", has("lodestone_compass", null, -1, 1));
      this.command("setblock " + lodestone[0].getX() + " " + lodestone[0].getY() + " " + lodestone[0].getZ() + " air");
   }

   private void armor() {
      this.commands(
         "clear @s",
         "give @s diamond_helmet",
         "give @s diamond_chestplate",
         "give @s iron_leggings",
         "give @s golden_boots",
         "give @s turtle_helmet",
         "give @s elytra",
         "give @s carved_pumpkin",
         "give @s zombie_head",
         "give @s chainmail_boots"
      );
      sleep(1500L);
      this.openInventory();
      this.clickItem(Items.DIAMOND_HELMET, 0, ContainerInput.PICKUP, "pick up the diamond helmet");
      this.click(5, 0, ContainerInput.PICKUP, "put it into the head slot");
      this.clickItem(Items.DIAMOND_CHESTPLATE, 0, ContainerInput.QUICK_MOVE, "shift-click the diamond chestplate");
      this.closeInventory();
      this.check("click: diamond helmet worn", worn("diamond_helmet", "head"));
      this.check("shift-click: diamond chestplate worn", worn("diamond_chestplate", "chest"));
      this.use(Items.IRON_LEGGINGS, "use iron leggings from the hotbar");
      this.use(Items.GOLDEN_BOOTS, "use golden boots from the hotbar");
      this.check("use: iron leggings worn", worn("iron_leggings", "legs"));
      this.check("use: golden boots worn", worn("golden_boots", "feet"));
      this.armorBar("diamond helmet, diamond chestplate, iron leggings, golden boots", 17);
      this.thirdPerson("armor worn");
      this.use(Items.TURTLE_HELMET, "use the turtle helmet: swaps with the diamond helmet");
      this.check("swap: turtle helmet worn", worn("turtle_helmet", "head"));
      this.check("swap: diamond helmet back in the inventory", has("diamond_helmet", null, -1, 1));
      this.use(Items.ELYTRA, "use the elytra: swaps with the diamond chestplate");
      this.check("swap: elytra worn", worn("elytra", "chest"));
      this.check("swap: diamond chestplate back in the inventory", has("diamond_chestplate", null, -1, 1));
      this.use(Items.CHAINMAIL_BOOTS, "use chainmail boots: swaps with the golden boots");
      this.check("swap: chainmail boots worn", worn("chainmail_boots", "feet"));
      this.armorBar("turtle helmet, elytra, iron leggings, chainmail boots", 8);
      this.openInventory();
      this.click(5, 0, ContainerInput.QUICK_MOVE, "shift-click the turtle helmet off");
      this.clickItem(Items.CARVED_PUMPKIN, 0, ContainerInput.PICKUP, "pick up the carved pumpkin");
      this.click(5, 0, ContainerInput.PICKUP, "put it into the head slot");
      this.closeInventory();
      this.check("carved pumpkin worn", worn("carved_pumpkin", "head"));
      this.check("turtle helmet taken off", has("turtle_helmet", null, -1, 1));
      this.thirdPerson("carved pumpkin and elytra");
      this.openInventory();
      this.clickItem(Items.ZOMBIE_HEAD, 0, ContainerInput.PICKUP, "pick up the zombie head");
      this.click(5, 0, ContainerInput.PICKUP, "swap it with the carved pumpkin");
      this.clickFirstEmpty(9, "put the pumpkin down");
      this.click(7, 0, ContainerInput.PICKUP, "take the iron leggings off onto the cursor");
      this.clickFirstEmpty(9, "put them into the inventory");
      this.closeInventory();
      this.check("zombie head worn", worn("zombie_head", "head"));
      this.check("leggings taken off", worn("iron_leggings", "legs").replace("quantity=1", "quantity=0"));
      this.check("leggings in the inventory", has("iron_leggings", null, -1, 1));
      this.thirdPerson("zombie head");
      this.report("after armor");
   }

   private void dyedArmor() {
      this.commands("clear @s", "loot give @s loot \"bedrocknative/dyed_armor\"");
      sleep(1500L);
      List<ItemStack> shown = new ArrayList<>();
      this.run(() -> {
         for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
               ItemData bedrock = this.inventory.item(slot);
               log("dyed armor slot {}: {} {} -> {}", slot, bedrock.getDefinition().getIdentifier(), bedrock.getTag(), this.describe(stack));
               shown.add(stack.copy());
            }
         }
      });
      this.tooltips("dyed and trimmed armor", shown);

      for (Item item : List.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS)) {
         this.use(item, "wear " + BuiltInRegistries.ITEM.getKey(item).getPath());
      }

      this.check("dyed helmet worn", worn("leather_helmet", "head"));
      this.check("dyed boots worn", worn("leather_boots", "feet"));
      this.armorBar("leather armor", 7);
      this.thirdPerson("dyed and trimmed leather armor");
      this.run(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
      sleep(1000L);
      this.run(() -> Screenshot.grab(this.minecraft, false));
      sleep(300L);
      this.run(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));
   }

   private void offhand() {
      this.commands(
         "clear @s",
         "give @s shield",
         "give @s totem_of_undying",
         "give @s arrow 8",
         "give @s firework_rocket 3",
         "give @s nautilus_shell",
         "give @s empty_map",
         "give @s spyglass",
         "give @s dirt 5",
         "give @s torch 5",
         "give @s compass",
         "give @s filled_map",
         "give @s arrow 2 10"
      );
      sleep(1500L);

      for (Item item : List.of(
         Items.SHIELD,
         Items.TOTEM_OF_UNDYING,
         Items.ARROW,
         Items.TIPPED_ARROW,
         Items.FIREWORK_ROCKET,
         Items.NAUTILUS_SHELL,
         Items.FILLED_MAP,
         Items.MAP,
         Items.SPYGLASS,
         Items.DIRT,
         Items.TORCH,
         Items.COMPASS
      )) {
         String name = BuiltInRegistries.ITEM.getKey(item).getPath();
         this.openInventory();
         int rejectedBefore = this.onClient(this.requests::rejected);
         this.clickItem(item, 0, ContainerInput.PICKUP, "pick up " + name);
         this.click(45, 0, ContainerInput.PICKUP, "put " + name + " into the offhand");
         sleep(600L);
         String inOffhand = this.onClient(() -> BuiltInRegistries.ITEM.getKey(this.minecraft.player.getOffhandItem().getItem()).getPath());
         int rejected = this.onClient(this.requests::rejected) - rejectedBefore;
         log("offhand {}: client shows {}, rejected {}", name, inOffhand, rejected);
         this.onClient(() -> {
            AbstractContainerMenu menu = this.minecraft.player.containerMenu;
            if (!menu.getCarried().isEmpty()) {
               this.clickNow(this.firstEmpty(9), 0, ContainerInput.PICKUP, "put the cursor back");
            }

            return null;
         });
         sleep(600L);
         this.closeInventory();
         this.check("offhand holds " + name, has(item == Items.TIPPED_ARROW ? "arrow" : (item == Items.MAP ? "empty_map" : name), "slot.weapon.offhand", 0, -1));
         if (!inOffhand.equals("air")) {
            this.openInventory();
            this.click(45, 0, ContainerInput.QUICK_MOVE, "shift-click " + inOffhand + " out of the offhand");
            this.closeInventory();
         }
      }

      this.commands("clear @s", "give @s shield", "give @s filled_map");
      sleep(600L);
      this.openInventory();
      this.clickItem(Items.SHIELD, 0, ContainerInput.PICKUP, "pick up the shield");
      this.click(45, 0, ContainerInput.PICKUP, "shield into the offhand");
      this.closeInventory();
      this.check("offhand shield", has("shield", "slot.weapon.offhand", 0, 1));
      this.thirdPerson("shield in the offhand");
   }

   private void durability() {
      this.commands("kill @e[family=monster]", "clear @s", "give @s diamond_chestplate", "loot give @s loot \"bedrocknative/worn_boots\"");
      sleep(1500L);
      this.use(Items.DIAMOND_CHESTPLATE, "wear the diamond chestplate");
      this.use(Items.LEATHER_BOOTS, "wear the worn leather boots");
      this.armorBar("diamond chestplate, leather boots", 9);
      this.reportArmor("before damage");

      for (int hit = 0; hit < 7; hit++) {
         this.command("damage @s 4 entity_attack");
         sleep(800L);
         this.command("effect @s instant_health 1 3 true");
         sleep(800L);
         this.reportArmor("after hit " + (hit + 1));
      }

      this.check("worn boots broke", worn("leather_boots", "feet").replace("quantity=1", "quantity=0"));
      this.check("chestplate still worn", worn("diamond_chestplate", "chest"));
      this.armorBar("diamond chestplate after the boots broke", 8);
   }

   private void reportArmor(String when) {
      this.run(
         () -> {
            List<String> worn = new ArrayList<>();

            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
               ItemStack stack = this.minecraft.player.getItemBySlot(slot);
               ItemData bedrock = this.inventory.get(BedrockSlot.armor(3 - slot.getIndex()));
               worn.add(
                  slot.getName()
                     + "="
                     + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                     + " damage "
                     + stack.getDamageValue()
                     + "/"
                     + stack.getMaxDamage()
                     + " (server "
                     + bedrock.getDefinition().getIdentifier()
                     + " "
                     + bedrock.getTag()
                     + ")"
               );
            }

            log("armor {}: {}; armor bar {}", when, worn, this.minecraft.player.getArmorValue());
         }
      );
   }

   private void armorBar(String what, int expected) {
      sleep(300L);
      int shown = this.onClient(() -> this.minecraft.player.getArmorValue());
      log("armor bar with {}: {} (Bedrock {}) {}", what, shown, expected, shown == expected ? "OK" : "MISMATCH");
   }

   private void use(Item item, String what) {
      this.run(() -> {
         this.select(item);
         this.minecraft.player.setXRot(-80.0F);
      });
      sleep(600L);
      this.run(() -> {
         InteractionResult result = this.minecraft.gameMode.useItem(this.minecraft.player, InteractionHand.MAIN_HAND);
         log("{}: {}; now holding {}", what, result, this.minecraft.player.getMainHandItem());
      });
      sleep(1000L);
   }

   private void select(Item item) {
      Inventory items = this.minecraft.player.getInventory();

      for (int slot = 0; slot < 9; slot++) {
         if (items.getItem(slot).is(item)) {
            items.setSelectedSlot(slot);
            return;
         }
      }

      log("no {} in the hotbar", item);
   }

   private void thirdPerson(String what) {
      this.run(() -> {
         this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
         this.minecraft.player.setXRot(10.0F);
      });
      sleep(1200L);
      this.run(() -> Screenshot.grab(this.minecraft, false));
      sleep(300L);
      this.run(() -> {
         this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
         log("screenshot: {}", what);
      });
   }

   private void tooltips(String what, List<ItemStack> stacks) {
      if (!stacks.isEmpty()) {
         this.run(() -> this.minecraft.gui.setScreen(new DevItemsTest.TooltipScreen(List.copyOf(stacks))));
         sleep(1000L);
         this.run(() -> {
            Screenshot.grab(this.minecraft, false);
            log("screenshot: tooltips of {}", what);
         });
         sleep(500L);
         this.run(() -> this.minecraft.gui.setScreen(null));
         sleep(300L);
      }
   }

   private String describe(ItemStack stack) {
      List<Component> lines = Screen.getTooltipFromItem(this.minecraft, stack);
      return stack.getCount()
         + "x"
         + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
         + " "
         + stack.getComponentsPatch()
         + " tooltip ["
         + lines.stream().map(Component::getString).collect(Collectors.joining(" | "))
         + "]";
   }

   private void report(String when) {
      this.run(() -> {
         List<String> items = new ArrayList<>();

         for (int slot = 0; slot < this.minecraft.player.inventoryMenu.slots.size(); slot++) {
            ItemStack stack = this.minecraft.player.inventoryMenu.getSlot(slot).getItem();
            if (!stack.isEmpty()) {
               items.add(slot + "=" + stack.getCount() + "x" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
            }
         }

         log("inventory {}: {}; armor bar {}", when, items, this.minecraft.player.getArmorValue());
      });
   }

   private void openInventory() {
      this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(600L);
   }

   private void closeInventory() {
      sleep(600L);
      this.run(() -> this.minecraft.player.closeContainer());
      sleep(600L);
   }

   private void clickItem(Item item, int button, ContainerInput input, String what) {
      this.run(() -> {
         int slot = this.find(stack -> stack.is(item), 9);
         if (slot < 0) {
            log("no {} in the inventory for '{}'", item, what);
         } else {
            this.clickNow(slot, button, input, what);
         }
      });
      sleep(600L);
   }

   private void clickFirstEmpty(int from, String what) {
      this.run(() -> this.clickNow(this.firstEmpty(from), 0, ContainerInput.PICKUP, what));
      sleep(600L);
   }

   private int firstEmpty(int from) {
      return this.find(ItemStack::isEmpty, from);
   }

   private int find(Predicate<ItemStack> predicate, int from) {
      AbstractContainerMenu menu = this.minecraft.player.containerMenu;

      for (int slot = from; slot < menu.slots.size(); slot++) {
         if (slot != 45 && predicate.test(menu.getSlot(slot).getItem())) {
            return slot;
         }
      }

      return -1;
   }

   private void click(int slot, int button, ContainerInput input, String what) {
      this.run(() -> this.clickNow(slot, button, input, what));
      sleep(600L);
   }

   private void clickNow(int slot, int button, ContainerInput input, String what) {
      AbstractContainerMenu menu = this.minecraft.player.containerMenu;
      this.minecraft.gameMode.handleContainerInput(menu.containerId, slot, button, input, this.minecraft.player);
      log(
         "click: {} (slot {} {}): slot now {}, cursor {}; requests accepted {}, rejected {}",
         what,
         slot,
         input,
         slot >= 0 ? menu.getSlot(slot).getItem() : "-",
         menu.getCarried(),
         this.requests.accepted(),
         this.requests.rejected()
      );
   }

   private static String worn(String item, String part) {
      return has(item, "slot.armor." + part, 0, 1);
   }

   private static String has(String item, String location, int slot, int quantity) {
      String where = location == null ? "" : ",location=" + location + (slot >= 0 ? ",slot=" + slot : "");
      return "testfor @s[hasitem={item=" + item + where + (quantity >= 0 ? ",quantity=" + quantity : "") + "}]";
   }

   private void check(String what, String command) {
      this.checks++;
      log("check {}: {}", this.checks, what);
      this.command(command);
      sleep(300L);
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.command(command);
      }
   }

   private void command(String command) {
      this.run(() -> this.minecraft.getConnection().sendCommand(command));
      sleep(400L);
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
      BedrockNative.LOGGER.info("Dev items test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private static final class TooltipScreen extends Screen {
      private final List<ItemStack> stacks;

      TooltipScreen(List<ItemStack> stacks) {
         super(Component.literal("Item tooltips"));
         this.stacks = stacks;
      }

      @Override
      public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
         super.extractRenderState(graphics, mouseX, mouseY, a);
         int columns = 3;
         int width = this.width / columns;

         for (int i = 0; i < this.stacks.size(); i++) {
            ItemStack stack = this.stacks.get(i);
            int x = i % columns * width + 4;
            int y = i / columns * (this.height / 2) + 4;
            graphics.item(stack, x, y);
            graphics.itemDecorations(this.font, stack, x, y);
            List<ClientTooltipComponent> lines = getTooltipFromItem(this.minecraft, stack)
               .stream()
               .map(Component::getVisualOrderText)
               .map(ClientTooltipComponent::create)
               .collect(Collectors.toList());
            stack.getTooltipImage().ifPresent(image -> lines.add(Math.min(1, lines.size()), ClientTooltipComponent.create(image)));
            graphics.tooltip(this.font, lines, x + 8, y + 12, DefaultTooltipPositioner.INSTANCE, stack.get(DataComponents.TOOLTIP_STYLE), true);
            graphics.nextStratum();
         }
      }
   }
}
