package net.bedrocknative.client.net;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.inventory.GrindstoneVariant;
import net.bedrocknative.client.inventory.JsonUiContainerScreen;
import net.bedrocknative.client.inventory.JsonUiTrading;
import net.bedrocknative.client.inventory.StackRequests;
import net.bedrocknative.client.inventory.Workstations;
import net.bedrocknative.client.movement.BedrockEffects;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiScreen;
import net.bedrocknative.client.world.BedrockBeacons;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.jspecify.annotations.Nullable;

final class DevWorkstationsTest {
   private static final long STEP = 600L;
   private static final int EMPTY_HOTBAR = 8;
   private static final int SHIFT = 1;
   private static final int CONTROL = 64;
   private final Minecraft minecraft;
   private final StackRequests requests;
   private final Path log;
   private int checks;
   private int failed;
   private int known;
   private String station = "";
   private @Nullable String knownProblem;
   private final List<String> notes = new ArrayList<>();
   private int stationFailures;
   private int stationKnown;
   private int acceptedAtStart;
   private int rejectedAtStart;
   private int pendingAtStart;
   private final List<String> verdicts = new ArrayList<>();
   private boolean jsonUi;
   private int jsonUiClicks;
   private int jsonUiMisses;
   private int jsonUiAround;
   private @Nullable Holder<MobEffect> beaconPrimary;
   private @Nullable Holder<MobEffect> beaconSecondary;

   DevWorkstationsTest(Minecraft minecraft, StackRequests requests) {
      this.minecraft = minecraft;
      this.requests = requests;
      this.log = minecraft.gameDirectory.toPath().resolve("logs/latest.log");
   }

   void run(String devTest) {
      String only = devTest.contains("-") ? devTest.substring(devTest.indexOf(45) + 1) : "";
      this.awaitJsonUi();
      this.commands(
         "gamemode survival",
         "clear @s",
         "xp -1000L @s",
         "xp 100L @s",
         "fill ~1 ~ ~-3 ~3 ~2 ~4 air",
         "fill ~2 ~-1 ~-3 ~2 ~-1 ~4 stone",
         "kill @e[type=villager,r=10]"
      );
      sleep(1500L);
      switch (only) {
         case "":
            this.run("enchanting", null, this::enchanting);
            this.run("anvil", null, this::anvil);
            this.run("grindstone", null, () -> this.grindstone(2));
            this.run("stonecutter", null, this::stonecutter);
            this.run("loom", null, this::loom);
            this.run("smithing", null, this::smithing);
            this.run("cartography", null, this::cartography);
            this.beacon();
            this.run("beacon-payment", null, this::beaconPayment);
            this.hurt();
            this.run("trading", null, this::trading);
            break;
         case "beacon":
            this.beacon();
            this.run("beacon-payment", null, this::beaconPayment);
            break;
         case "beacon-payment":
            this.run("beacon-payment", null, this::beaconPayment);
            break;
         case "hurt":
            this.hurt();
            break;
         case "grindstone-search":
            this.grindstoneSearch();
            break;
         case "grindstone":
            this.commands("give @s iron_sword");
            this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(1));
            sleep(400L);
            this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(0));
            sleep(400L);
            this.commands("enchant @s sharpness 1");
            this.run("grindstone", null, () -> this.grindstone(1));
            break;
         case "enchanting":
            this.run("enchanting", null, this::enchanting);
            break;
         case "stonecutter":
            this.run("stonecutter", null, this::stonecutter);
            break;
         case "loom":
            this.run("loom", null, this::loom);
            break;
         case "smithing":
            this.run("smithing", null, this::smithing);
            break;
         case "cartography":
            this.run("cartography", null, this::cartography);
            break;
         case "trading":
            this.run("trading", null, this::trading);
            break;
         default:
            BedrockNative.LOGGER.warn("Dev test workstations: no station called {}", only);
      }

      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info(
                  "Dev test workstations: stack requests accepted {}, rejected {}, pending {}",
                  new Object[]{this.requests.accepted(), this.requests.rejected(), this.requests.pendingCount()}
               )
         );
      this.commands("fill ~1 ~ ~-3 ~3 ~2 ~4 air", "kill @e[type=villager,r=10]");
      BedrockNative.LOGGER.info("Dev workstations test: {} checks, {} failed", this.checks, this.failed);
      if (this.known > 0) {
         BedrockNative.LOGGER
            .info(
               "Dev workstations test: {} checks of known problems did not pass: {}",
               this.known,
               this.verdicts.stream().filter(verdict -> verdict.startsWith("KNOWN")).toList()
            );
      }

      BedrockNative.LOGGER.info("Dev workstations test: done");
   }

   private void run(String name, @Nullable String problem, Runnable steps) {
      this.station = name;
      this.knownProblem = problem;
      this.notes.clear();
      this.stationFailures = 0;
      this.stationKnown = 0;
      this.acceptedAtStart = StackRequests.acceptedTotal();
      this.rejectedAtStart = StackRequests.rejectedTotal();
      this.pendingAtStart = this.onClient(this.requests::pendingCount);
      steps.run();
      int accepted = StackRequests.acceptedTotal() - this.acceptedAtStart;
      int rejected = StackRequests.rejectedTotal() - this.rejectedAtStart;
      int pending = this.onClient(this.requests::pendingCount) - this.pendingAtStart;
      this.check("stack requests: accepted " + accepted + ", rejected " + rejected + ", unanswered " + pending, accepted > 0 && rejected == 0 && pending <= 0);
      String verdict = this.stationFailures > 0 ? "FAIL" : (this.stationKnown > 0 ? "KNOWN" : "PASS");
      String line = verdict + " " + name + ": " + String.join("; ", this.notes);
      if (problem != null) {
         line = line + (this.stationKnown > 0 ? " [known problem: " + problem + "]" : " [the known problem did not show: drop the KNOWN mark of " + name + "]");
      }

      this.verdicts.add(line);
      BedrockNative.LOGGER.info("Dev workstations test {}", line);
      this.station = "";
      this.knownProblem = null;
   }

   private void check(String what, boolean ok) {
      this.checks++;
      if (!ok) {
         if (this.knownProblem != null) {
            this.known++;
            this.stationKnown++;
         } else {
            this.failed++;
            this.stationFailures++;
         }
      }

      this.notes.add(what + (ok ? " ok" : (this.knownProblem != null ? " KNOWN" : " FAILED")));
      BedrockNative.LOGGER
         .info("Dev workstations check {} ({}): {}", new Object[]{ok ? "ok" : (this.knownProblem != null ? "KNOWN" : "FAILED"), this.station, what});
   }

   private void verify(String what, String command) {
      String answer = this.ask(command);
      String player = this.onClient(() -> this.minecraft.player.getGameProfile().name());
      this.check(what + " (" + command + ": " + (answer.isBlank() ? "no answer" : answer.trim()) + ")", answer.contains(player));
   }

   private String ask(String command) {
      long before = this.logSize();
      this.commands(command);
      sleep(400L);
      return this.chatSince(before);
   }

   private long logSize() {
      try {
         return Files.size(this.log);
      } catch (IOException e) {
         return 0L;
      }
   }

   private String chatSince(long offset) {
      try (RandomAccessFile file = new RandomAccessFile(this.log.toFile(), "r")) {
         file.seek(Math.min(offset, file.length()));
         byte[] bytes = new byte[(int)(file.length() - file.getFilePointer())];
         file.readFully(bytes);
         StringBuilder chat = new StringBuilder();

         for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            int index = line.indexOf("[CHAT]");
            if (index >= 0) {
               chat.append(line.substring(index + 7).trim()).append(" | ");
            }
         }

         return chat.toString();
      } catch (IOException e) {
         return "";
      }
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void enchanting() {
      this.commands("give @s iron_sword", "give @s lapis_lazuli 16");
      BlockPos table = this.place(-3, "enchanting_table");
      this.open(table, "enchanting table", "enchanting", "enchanting.enchanting_screen", 2);
      this.clickItem(stack -> stack.is(Items.IRON_SWORD), "pick up the sword");
      this.click(0, ContainerInput.PICKUP, "put it on the table");
      this.clickItem(stack -> stack.is(Items.LAPIS_LAZULI), "pick up the lapis");
      this.click(1, ContainerInput.PICKUP, "put it into the lapis slot");
      sleep(600L);
      this.minecraft
         .execute(
            () -> {
               if (this.minecraft.player.containerMenu instanceof EnchantmentMenu menu) {
                  BedrockNative.LOGGER
                     .info(
                        "Dev test workstations: enchanting costs {} {} {}, clues {} {} {}, levels {}",
                        new Object[]{
                           menu.costs[0],
                           menu.costs[1],
                           menu.costs[2],
                           menu.enchantClue[0],
                           menu.enchantClue[1],
                           menu.enchantClue[2],
                           this.minecraft.player.experienceLevel
                        }
                     );
                  this.checkEnchantingOptions(menu);
               }
            }
         );
      this.screenshot("workstations-enchanting");
      this.option(0, "the first enchanting option");
      sleep(1200L);
      this.minecraft
         .execute(
            () -> {
               this.report("enchanted");
               BedrockNative.LOGGER
                  .info("Dev test workstations: enchanted item {}", this.minecraft.player.containerMenu.getSlot(0).getItem().get(DataComponents.ENCHANTMENTS));
               Screenshot.grab(this.minecraft, false);
            }
         );
      this.jsonUiReport("enchanting table");
      this.close();
      this.verify("lapis left: 15", hasItem("lapis_lazuli", 15));
      this.verify("the sword is still there", hasItem("iron_sword", 1));
      this.verify("level 99 (100 minus the option's 1)", "testfor @s[lm=99,l=99]");
   }

   private void anvil() {
      this.commands("give @s iron_sword 1 100", "give @s iron_ingot 2");
      BlockPos anvil = this.place(-2, "anvil");
      this.open(anvil, "anvil", "anvil", "anvil.anvil_screen", 3);
      this.clickItem(stack -> stack.is(Items.IRON_SWORD) && !stack.isDamaged(), "pick up the enchanted sword");
      this.click(0, ContainerInput.PICKUP, "put it into the anvil");
      this.rename("Dev Blade");
      sleep(600L);
      this.minecraft
         .execute(
            () -> {
               BedrockNative.LOGGER
                  .info(
                     "Dev test workstations: rename result {}, cost {}",
                     this.minecraft.player.containerMenu.getSlot(2).getItem(),
                     ((AnvilMenu)this.minecraft.player.containerMenu).getCost()
                  );
               this.checkAnvil("Dev Blade");
            }
         );
      this.screenshot("workstations-anvil");
      this.click(2, ContainerInput.QUICK_MOVE, "take the renamed sword");
      sleep(600L);
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info("Dev test workstations: renamed sword shows as {}", this.findInInventory(stack -> stack.is(Items.IRON_SWORD) && !stack.isDamaged()))
         );
      this.clickItem(stack -> stack.is(Items.IRON_SWORD) && stack.isDamaged(), "pick up the damaged sword");
      this.click(0, ContainerInput.PICKUP, "put it into the anvil");
      this.clickItem(stack -> stack.is(Items.IRON_INGOT), "pick up the ingots");
      this.click(1, ContainerInput.PICKUP, "put them into the material slot");
      sleep(600L);
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info(
                  "Dev test workstations: repair result {} damage {}, cost {}",
                  new Object[]{
                     this.minecraft.player.containerMenu.getSlot(2).getItem(),
                     this.minecraft.player.containerMenu.getSlot(2).getItem().getDamageValue(),
                     ((AnvilMenu)this.minecraft.player.containerMenu).getCost()
                  }
               )
         );
      this.click(2, ContainerInput.PICKUP, "take the repaired sword");
      this.clickFirstEmpty("put it into the inventory");
      this.jsonUiReport("anvil");
      this.close();
      this.verify("both ingots used", hasItem("iron_ingot", 0));
      this.verify("two swords: the renamed one and the repaired one", hasItem("iron_sword", 2));
      this.verify("level 96 (99 minus 1 for the rename and 2 for the repair)", "testfor @s[lm=96,l=96]");
   }

   private void grindstoneSearch() {
      String forced = System.getProperty("bedrocknative.grindstoneVariant");
      List<GrindstoneVariant> tries = forced != null && !forced.isEmpty() ? List.of(GrindstoneVariant.active()) : GrindstoneVariant.SEARCH;
      BlockPos grindstone = this.place(-1, "grindstone");
      String accepted = null;

      try {
         for (GrindstoneVariant variant : tries) {
            BedrockNative.LOGGER.info("Dev test workstations: grindstone variant {}: sending", variant.name());
            if (forced == null || forced.isEmpty()) {
               GrindstoneVariant.override(variant);
            }

            this.commands("clear @s", "give @s iron_sword");
            this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(1));
            sleep(400L);
            this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(0));
            sleep(400L);
            this.commands("enchant @s sharpness 1");
            sleep(600L);
            this.open(grindstone, "grindstone");
            this.clickItem(
               stack -> stack.is(Items.IRON_SWORD) && stack.has(DataComponents.ENCHANTMENTS) && !stack.get(DataComponents.ENCHANTMENTS).isEmpty(),
               "pick up the enchanted sword"
            );
            this.click(0, ContainerInput.PICKUP, "put it into the grindstone");
            sleep(600L);
            this.minecraft
               .execute(
                  () -> BedrockNative.LOGGER
                     .info(
                        "Dev test workstations: grindstone result {} enchantments {}",
                        this.minecraft.player.containerMenu.getSlot(2).getItem(),
                        this.minecraft.player.containerMenu.getSlot(2).getItem().get(DataComponents.ENCHANTMENTS)
                     )
               );
            int[] beforeCraft = this.counters();
            this.click(2, ContainerInput.QUICK_MOVE, "take the disenchanted sword");
            sleep(1200L);
            int[] afterCraft = this.counters();
            String status = String.valueOf(this.minecraft.<ItemStackResponseStatus>submit(() -> this.requests.lastStatus()).join());
            boolean ok = afterCraft[0] > beforeCraft[0] && afterCraft[1] == beforeCraft[1];
            BedrockNative.LOGGER
               .info(
                  "Dev test workstations: grindstone variant {}: {} ({}), requests accepted {} -> {}, rejected {} -> {}",
                  new Object[]{variant.name(), ok ? "accepted" : "rejected", status, beforeCraft[0], afterCraft[0], beforeCraft[1], afterCraft[1]}
               );
            this.close();
            if (ok) {
               this.commands(hasItem("iron_sword", 1));
               this.minecraft
                  .execute(
                     () -> BedrockNative.LOGGER
                        .info("Dev test workstations: grindstone sword after the craft: {}", this.findInInventory(stack -> stack.is(Items.IRON_SWORD)))
                  );
               accepted = variant.name();
               break;
            }
         }
      } finally {
         GrindstoneVariant.override(null);
      }

      if (accepted != null) {
         BedrockNative.LOGGER.info("Dev test workstations: grindstone: accepted with {}", accepted);
      } else {
         BedrockNative.LOGGER.info("Dev test workstations: grindstone: no variant accepted");
      }
   }

   private int[] counters() {
      return this.minecraft.<int[]>submit(() -> new int[]{this.requests.accepted(), this.requests.rejected()}).join();
   }

   private void grindstone(int swords) {
      BlockPos grindstone = this.place(-1, "grindstone");
      this.open(grindstone, "grindstone", "grindstone", "grindstone.grindstone_screen", 3);
      this.clickItem(
         stack -> stack.is(Items.IRON_SWORD) && stack.has(DataComponents.ENCHANTMENTS) && !stack.get(DataComponents.ENCHANTMENTS).isEmpty(),
         "pick up the enchanted sword"
      );
      this.click(0, ContainerInput.PICKUP, "put it into the grindstone");
      sleep(600L);
      ItemStack shown = this.onClient(() -> {
         ItemStack result = this.minecraft.player.containerMenu.getSlot(2).getItem().copy();
         BedrockNative.LOGGER.info("Dev test workstations: grindstone result {} enchantments {}", result, result.get(DataComponents.ENCHANTMENTS));
         return result;
      });
      this.check("the grindstone shows a sword without enchantments (" + shown + ")", shown.is(Items.IRON_SWORD) && enchantments(shown) == 0);
      this.screenshot("workstations-grindstone");
      this.click(2, ContainerInput.QUICK_MOVE, "take the disenchanted sword");
      this.jsonUiReport("grindstone");
      this.close();
      int enchanted = this.onClient(() -> {
         int count = 0;

         for (int slot = 0; slot < this.minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(slot);
            if (stack.is(Items.IRON_SWORD) && enchantments(stack) > 0) {
               count++;
            }
         }

         return count;
      });
      this.check("no enchanted sword left in the inventory after the request (" + enchanted + ")", enchanted == 0);
      this.verify("the swords are all still there: " + swords, hasItem("iron_sword", swords));
   }

   private static int enchantments(ItemStack stack) {
      return stack.has(DataComponents.ENCHANTMENTS) ? stack.get(DataComponents.ENCHANTMENTS).size() : 0;
   }

   private void stonecutter() {
      this.commands("give @s stone 4");
      BlockPos cutter = this.place(0, "stonecutter_block");
      this.open(cutter, "stonecutter", "stonecutter", "stonecutter.stonecutter_screen", 2);
      this.clickItem(stack -> stack.is(Items.STONE), "pick up the stone");
      this.click(0, ContainerInput.PICKUP, "put it into the stonecutter");
      sleep(600L);
      int bricks = this.onClient(
         () -> {
            if (this.minecraft.player.containerMenu instanceof StonecutterMenu menu) {
               List<String> shown = new ArrayList<>();
               int found = -1;

               for (int i = 0; i < menu.getVisibleRecipes().size(); i++) {
                  Item item = menu.getVisibleRecipes().entries().get(i).recipe().optionDisplay() instanceof ItemStackSlotDisplay stack
                     ? stack.stack().item().value()
                     : Items.AIR;
                  shown.add(BuiltInRegistries.ITEM.getKey(item).getPath());
                  if (item == Items.STONE_BRICKS) {
                     found = i;
                  }
               }

               BedrockNative.LOGGER.info("Dev test workstations: stonecutter recipes for stone {}", shown);
               this.checkList("stones", menu.getVisibleRecipes().size(), "stonecutter");
               return found;
            } else {
               return -1;
            }
         }
      );
      if (bricks >= 0) {
         this.option(bricks, "the stone bricks recipe");
      }

      sleep(600L);
      this.minecraft
         .execute(() -> BedrockNative.LOGGER.info("Dev test workstations: stonecutter result {}", this.minecraft.player.containerMenu.getSlot(1).getItem()));
      this.screenshot("workstations-stonecutter");
      this.click(1, ContainerInput.QUICK_MOVE, "take one stone brick");
      this.jsonUiReport("stonecutter");
      this.close();
      this.verify("one stone brick", hasItem("stone_bricks", 1));
      this.verify("three stone left", hasItem("stone", 3));
   }

   private void loom() {
      this.commands("give @s banner 1 15", "give @s red_dye 1");
      BlockPos loom = this.place(1, "loom");
      this.open(loom, "loom", "loom", "loom.loom_screen", 4);
      this.clickItem(stack -> stack.is(Items.BANNER.white()), "pick up the banner");
      this.click(0, ContainerInput.PICKUP, "put it into the loom");
      this.clickItem(stack -> stack.is(Items.DYE.red()), "pick up the dye");
      this.click(1, ContainerInput.PICKUP, "put it into the dye slot");
      sleep(600L);
      int border = this.onClient(() -> {
         if (this.minecraft.player.containerMenu instanceof LoomMenu menu) {
            List<Holder<BannerPattern>> patterns = menu.getSelectablePatterns();
            this.checkList("patterns", patterns.size(), "loom");

            for (int i = 0; i < patterns.size(); i++) {
               if (patterns.get(i).unwrapKey().map(key -> key.identifier().getPath().equals("border")).orElse(false)) {
                  return i;
               }
            }
         }

         return -1;
      });
      if (border >= 0) {
         this.option(border, "the border pattern");
      }

      sleep(600L);
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info(
                  "Dev test workstations: loom result {} patterns {}",
                  this.minecraft.player.containerMenu.getSlot(3).getItem(),
                  this.minecraft.player.containerMenu.getSlot(3).getItem().get(DataComponents.BANNER_PATTERNS)
               )
         );
      this.screenshot("workstations-loom");
      this.click(3, ContainerInput.QUICK_MOVE, "take the bordered banner");
      this.jsonUiReport("loom");
      this.close();
      this.verify("the red dye is used up", hasItem("red_dye", 0));
      this.verify("one banner", hasItem("banner", 1));
   }

   private void smithing() {
      this.commands(
         "give @s diamond_sword",
         "give @s netherite_ingot",
         "give @s netherite_upgrade_smithing_template",
         "give @s iron_chestplate",
         "give @s quartz",
         "give @s sentry_armor_trim_smithing_template"
      );
      BlockPos table = this.place(2, "smithing_table");
      this.open(table, "smithing table", "smithing_table", "smithing_table.smithing_table_screen", 4);
      this.smith(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.DIAMOND_SWORD, Items.NETHERITE_INGOT, "upgrade");
      this.smith(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Items.IRON_CHESTPLATE, Items.QUARTZ, "trim");
      this.jsonUiReport("smithing table");
      this.close();
      this.verify("a netherite sword", hasItem("netherite_sword", 1));
      this.verify("the upgrade template is used up", hasItem("netherite_upgrade_smithing_template", 0));
      this.verify("the trimmed chestplate", hasItem("iron_chestplate", 1));
      this.verify("the quartz is used up", hasItem("quartz", 0));
      this.verify("the trim template is used up", hasItem("sentry_armor_trim_smithing_template", 0));
   }

   private void smith(Item template, Item base, Item addition, String what) {
      this.clickItem(stack -> stack.is(template), "pick up the template");
      this.click(0, ContainerInput.PICKUP, "put it into the template slot");
      this.clickItem(stack -> stack.is(base), "pick up the base");
      this.click(1, ContainerInput.PICKUP, "put it into the base slot");
      this.clickItem(stack -> stack.is(addition), "pick up the addition");
      this.click(2, ContainerInput.PICKUP, "put it into the addition slot");
      sleep(600L);
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info(
                  "Dev test workstations: smithing {} result {} trim {}",
                  new Object[]{
                     what,
                     this.minecraft.player.containerMenu.getSlot(3).getItem(),
                     this.minecraft.player.containerMenu.getSlot(3).getItem().get(DataComponents.TRIM)
                  }
               )
         );
      this.screenshot("workstations-smithing-" + what);
      this.click(3, ContainerInput.QUICK_MOVE, "take the " + what + " result");
   }

   private void cartography() {
      this.commands("give @s empty_map 2");
      sleep(600L);
      this.minecraft.execute(() -> {
         LocalPlayer player = this.minecraft.player;

         for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(Items.MAP)) {
               player.getInventory().setSelectedSlot(slot);
            }
         }
      });
      sleep(600L);
      this.minecraft.execute(() -> this.minecraft.gameMode.useItem(this.minecraft.player, InteractionHand.MAIN_HAND));
      sleep(1200L);
      BlockPos table = this.place(3, "cartography_table");
      this.open(table, "cartography table", "cartography", "cartography.cartography_screen", 3);
      this.clickItem(stack -> stack.is(Items.FILLED_MAP), "pick up the filled map");
      this.click(0, ContainerInput.PICKUP, "put it into the map slot");
      this.clickItem(stack -> stack.is(Items.MAP), "pick up the empty map");
      this.click(1, ContainerInput.PICKUP, "put it into the additional slot");
      sleep(600L);
      this.minecraft
         .execute(() -> BedrockNative.LOGGER.info("Dev test workstations: cartography result {}", this.minecraft.player.containerMenu.getSlot(2).getItem()));
      this.screenshot("workstations-cartography");
      this.click(2, ContainerInput.QUICK_MOVE, "take the copies");
      this.jsonUiReport("cartography table");
      this.close();
      this.verify("two filled maps", hasItem("filled_map", 2));
      this.verify("no empty map left", hasItem("empty_map", 0));
   }

   private void hurt() {
      if (!JsonUi.drawn("chest")) {
         BedrockNative.LOGGER
            .info("Dev workstations test: hurt closes the chest screen (JSON UI mode {} leaves the Java chest screen open): SKIP", JsonUi.mode());
      } else {
         BlockPos chest = this.place(4, "chest");
         this.open(chest, "chest");
         String before = this.onClient(() -> this.minecraft.gui.screen() == null ? "nothing" : this.minecraft.gui.screen().getClass().getSimpleName());
         this.command("damage @s 1");
         sleep(1500L);
         this.minecraft
            .execute(
               () -> {
                  LocalPlayer player = this.minecraft.player;
                  boolean closed = this.minecraft.gui.screen() == null && player.containerMenu == player.inventoryMenu;
                  BedrockNative.LOGGER
                     .info(
                        "Dev workstations test: hurt closes the chest screen ({} before, {} now, health {}): {}",
                        new Object[]{
                           before,
                           this.minecraft.gui.screen() == null ? "nothing" : this.minecraft.gui.screen().getClass().getSimpleName(),
                           player.getHealth(),
                           closed ? "PASS" : "FAIL"
                        }
                     );
               }
            );
         this.close();
         this.command("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " air");
      }
   }

   private void beacon() {
      BlockPos beacon = this.place(4, "beacon");
      this.open(beacon, "beacon", "beacon", "beacon.beacon_screen", 1);
      sleep(600L);
      this.minecraft
         .execute(
            () -> {
               if (this.minecraft.player.containerMenu instanceof BeaconMenu menu) {
                  BedrockNative.LOGGER
                     .info(
                        "Dev test workstations: beacon levels {}, primary {}, secondary {}",
                        new Object[]{menu.getLevels(), menu.getPrimaryEffect(), menu.getSecondaryEffect()}
                     );
                  this.checkBeacon(menu.getLevels());
               }
            }
         );
      this.screenshot("workstations-beacon");
      this.minecraft
         .execute(
            () -> {
               if (this.minecraft.gui.screen() instanceof JsonUiContainerScreen screen && screen.getMenu() == this.minecraft.player.containerMenu) {
                  UiControl cancel = screen.ui().instance().find("cancel_panel");
                  UiControl button = cancel == null ? null : cancel.find("active_button");
                  if (button == null || !button.visible()) {
                     this.missed("the beacon's cancel button", "no cancel button");
                     return;
                  }

                  this.jsonUiClicks++;
                  press(screen, centre(button));
                  BedrockNative.LOGGER
                     .info("Dev workstations test JSON UI: beacon cancel closes the screen: {}", this.minecraft.gui.screen() != screen ? "PASS" : "FAIL");
               }
            }
         );
      sleep(600L);
      this.jsonUiReport("beacon");
      this.close();
      this.command("setblock " + beacon.getX() + " " + beacon.getY() + " " + beacon.getZ() + " air");
   }

   private void beaconPayment() {
      this.commands("clear @s", "effect @s clear");
      BlockPos beacon = this.onClient(() -> this.minecraft.player.blockPosition().offset(2, 0, 4));
      String from = beacon.getX() - 4 + " " + (beacon.getY() - 4) + " " + (beacon.getZ() - 4);
      String to = beacon.getX() + 4 + " " + (beacon.getY() + 30) + " " + (beacon.getZ() + 4);
      this.commands("structure save bn_beacon_area " + from + " " + to + " false memory");

      try {
         this.beaconStage(beacon, 1, List.of(MobEffects.SPEED), "beacon-level1");
         this.commands("clear @s", "effect @s clear");
         this.beaconStage(beacon, 4, List.of(MobEffects.SPEED, MobEffects.REGENERATION), "beacon-level4");
      } finally {
         this.commands("structure load bn_beacon_area " + from, "structure delete bn_beacon_area", "effect @s clear");
         sleep(1000L);
         String beaconGone = this.ask("testforblock " + beacon.getX() + " " + beacon.getY() + " " + beacon.getZ() + " beacon");
         String ironGone = this.ask("testforblock " + beacon.getX() + " " + (beacon.getY() - 1) + " " + beacon.getZ() + " iron_block");
         BedrockNative.LOGGER.info("Dev workstations test: beacon area after the structure load: beacon {} | iron {}", beaconGone.trim(), ironGone.trim());
         this.check("the area is as it was before (no beacon, no pyramid iron)", !beaconGone.contains("Successfully") && !ironGone.contains("Successfully"));
      }
   }

   private void beaconStage(BlockPos beacon, int levels, List<Holder<MobEffect>> powers, String shot) {
      String stage = "level " + levels;

      for (int step = 1; step <= levels; step++) {
         this.command(
            "fill "
               + (beacon.getX() - step)
               + " "
               + (beacon.getY() - step)
               + " "
               + (beacon.getZ() - step)
               + " "
               + (beacon.getX() + step)
               + " "
               + (beacon.getY() - step)
               + " "
               + (beacon.getZ() + step)
               + " iron_block"
         );
      }

      this.commands("setblock " + beacon.getX() + " " + beacon.getY() + " " + beacon.getZ() + " air", "effect @s clear");
      sleep(1000L);
      this.commands(
         "fill "
            + beacon.getX()
            + " "
            + (beacon.getY() + 1)
            + " "
            + beacon.getZ()
            + " "
            + beacon.getX()
            + " "
            + (beacon.getY() + 30)
            + " "
            + beacon.getZ()
            + " air",
         "setblock " + beacon.getX() + " " + beacon.getY() + " " + beacon.getZ() + " beacon"
      );
      sleep(9000L);
      this.check(
         stage + ": a fresh beacon has no power before the payment",
         this.onClient(() -> BedrockBeacons.powersAt(this.minecraft.level, beacon)).equals(BedrockBeacons.Powers.NONE)
      );

      for (Holder<MobEffect> effect : powers) {
         this.check(
            stage + ": the player has no " + effect.getRegisteredName() + " before the payment", !this.onClient(() -> this.minecraft.player.hasEffect(effect))
         );
      }

      this.commands("give @s iron_ingot 1");
      this.open(beacon, "beacon (" + stage + ")", "beacon", "beacon.beacon_screen", 1);
      sleep(600L);
      int shown = this.onClient(() -> this.minecraft.player.containerMenu instanceof BeaconMenu menu ? menu.getLevels() : -1);
      this.check(stage + ": the screen counts " + shown + " pyramid levels", shown == levels);
      this.minecraft.execute(() -> this.checkBeacon(levels));
      sleep(200L);
      this.screenshot(shot);
      this.clickItem(stack -> stack.is(Items.IRON_INGOT), "pick up the ingot (" + stage + ")");
      this.click(0, ContainerInput.PICKUP, "put it into the payment slot (" + stage + ")");
      sleep(600L);
      boolean paid = this.onClient(() -> this.minecraft.player.containerMenu instanceof BeaconMenu menu && menu.hasPayment());
      this.check(stage + ": the payment slot holds the ingot", paid);
      this.beaconPrimary = null;
      this.beaconSecondary = null;
      this.beaconButton("speed");
      if (powers.size() > 1) {
         this.beaconButton("regen");
      }

      this.screenshot(shot + "-chosen");
      this.beaconButton("confirm");
      sleep(1800L);
      this.check(stage + ": the screen closed on confirm", this.onClient(() -> this.minecraft.player.containerMenu == this.minecraft.player.inventoryMenu));
      this.verify(stage + ": the iron ingot was used up", hasItem("iron_ingot", 0));
      int primary = BedrockEffects.idOf(powers.getFirst());
      int secondary = powers.size() > 1 ? BedrockEffects.idOf(powers.get(1)) : 0;
      DevWorkstationsTest.BeaconRulesPoll powersSeen = new DevWorkstationsTest.BeaconRulesPoll();

      for (int i = 0; i < 6 && !powersSeen.matches(primary, secondary); i++) {
         powersSeen.value = this.onClient(() -> BedrockBeacons.powersAt(this.minecraft.level, beacon));
         if (!powersSeen.matches(primary, secondary)) {
            sleep(500L);
         }
      }

      BedrockNative.LOGGER
         .info(
            "Dev workstations test: beacon ({}) block entity data after the payment: primary {}, secondary {}; expected primary {}, secondary {}{}",
            new Object[]{
               stage,
               powersSeen.value.primary(),
               powersSeen.value.secondary(),
               primary,
               secondary,
               powersSeen.matches(primary, secondary) ? " (as paid)" : " (not pushed by the server)"
            }
         );

      for (Holder<MobEffect> effect : powers) {
         boolean has = false;

         for (int i = 0; i < 24 && !has; i++) {
            has = this.onClient(() -> this.minecraft.player.hasEffect(effect));
            if (!has) {
               sleep(500L);
            }
         }

         this.check(stage + ": the player has " + effect.getRegisteredName(), has);
      }

      this.jsonUiReport("beacon payment " + stage);
      this.close();
   }

   private void beaconButton(String button) {
      this.minecraft
         .execute(
            () -> {
               if ("speed".equals(button)) {
                  this.beaconPrimary = MobEffects.SPEED;
               } else if ("regen".equals(button)) {
                  this.beaconSecondary = MobEffects.REGENERATION;
               }

               JsonUiContainerScreen screen = this.jsonUiScreen();
               if (screen != null) {
                  UiControl panel = screen.ui().instance().find(button + "_panel");
                  UiControl control = panel == null ? null : panel.find("confirm".equals(button) ? "active_button" : "toggle_button");
                  if (control == null || !control.visible()) {
                     if (panel != null) {
                        panel.walk(
                           c -> BedrockNative.LOGGER
                              .info(
                                 "Dev workstations test JSON UI: beacon {} panel control {} ({}, {}) visible {} own {} enabled {} size {}x{}",
                                 new Object[]{button, c.name(), c.type(), c.definition(), c.visible(), c.ownVisible(), c.enabled(), c.width(), c.height()}
                              )
                        );
                     }

                     this.missed("the beacon's " + button + " button", "no active " + button + " control");
                     return;
                  }

                  this.jsonUiClicks++;
                  press(screen, centre(control));
               } else if ("confirm".equals(button)) {
                  this.minecraft
                     .player
                     .connection
                     .send(new ServerboundSetBeaconPacket(Optional.ofNullable(this.beaconPrimary), Optional.ofNullable(this.beaconSecondary)));
                  this.minecraft.player.closeContainer();
               }

               BedrockNative.LOGGER.info("Dev test workstations beacon button: {}{}", button, screen != null ? " through the JSON UI" : "");
            }
         );
      sleep(600L);
   }

   private void trading() {
      this.commands("kill @e[type=villager,r=8]");

      try {
         this.trade();
      } finally {
         this.commands("kill @e[type=villager,r=8]");
      }
   }

   private void trade() {
      this.commands("summon villager ~-2 ~ ~ 180 0 minecraft:spawn_farmer", "effect @e[type=villager,r=4] slowness 120 255 true");
      sleep(2000L);
      Entity[] villager = new Entity[1];
      this.minecraft.execute(() -> {
         for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity instanceof Villager && entity.distanceTo(this.minecraft.player) < 5.0F) {
               villager[0] = entity;
            }
         }
      });
      sleep(200L);
      if (villager[0] == null) {
         BedrockNative.LOGGER.warn("Dev test workstations: no villager to trade with");
         this.check("a villager to trade with", false);
      } else {
         this.openTrade(villager[0]);
         this.checkTradeScreen();
         String[] cost = new String[3];
         this.minecraft
            .execute(
               () -> {
                  if (this.minecraft.player.containerMenu instanceof MerchantMenu menu && !menu.getOffers().isEmpty()) {
                     MerchantOffer offer = menu.getOffers().getFirst();
                     cost[0] = BuiltInRegistries.ITEM.getKey(offer.getCostA().getItem()).getPath();
                     cost[1] = String.valueOf(offer.getCostA().getCount());
                     cost[2] = BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).getPath() + " " + offer.getResult().getCount();
                     BedrockNative.LOGGER
                        .info(
                           "Dev test workstations: {} offers, first {} x{} (+{}) for {}, level {}, xp {}",
                           new Object[]{menu.getOffers().size(), cost[0], cost[1], offer.getCostB(), cost[2], menu.getTraderLevel(), menu.getTraderXp()}
                        );
                  }
               }
            );
         this.screenshot("trading");
         this.close();
         if (cost[0] == null) {
            BedrockNative.LOGGER.warn("Dev test workstations: the villager has no trades");
            this.check("a trade to take", false);
         } else {
            this.commands("give @s " + cost[0] + " " + cost[1]);
            this.openTrade(villager[0]);
            this.checkTradeScreen();
            this.chooseTrade(cost[0]);
            sleep(600L);
            this.minecraft.execute(() -> this.report("trade chosen"));
            this.screenshot("trading-chosen");
            this.click(2, ContainerInput.QUICK_MOVE, "take the bought item");
            this.jsonUiReport("trading");
            this.close();
            String[] result = cost[2].split(" ");
            this.verify("the bought item: " + cost[2], hasItem(result[0], Integer.parseInt(result[1])));
            this.verify("the payment is used up: " + cost[0], hasItem(cost[0], 0));
            if (this.jsonUi) {
               this.openTrade(villager[0]);
               this.command("damage @s 1");
               sleep(1500L);
               this.minecraft
                  .execute(
                     () -> {
                        LocalPlayer player = this.minecraft.player;
                        boolean closed = !(this.minecraft.gui.screen() instanceof JsonUiContainerScreen) && player.containerMenu == player.inventoryMenu;
                        BedrockNative.LOGGER
                           .info(
                              "Dev workstations test JSON UI: hurt closes the trading screen ({} now, health {}): {}",
                              new Object[]{
                                 this.minecraft.gui.screen() == null ? "nothing" : this.minecraft.gui.screen().getClass().getSimpleName(),
                                 player.getHealth(),
                                 closed ? "PASS" : "FAIL"
                              }
                           );
                     }
                  );
               this.close();
            }
         }
      }
   }

   private void checkTradeScreen() {
      this.minecraft
         .execute(
            () -> {
               this.jsonUi = JsonUi.drawn("trading");
               this.jsonUiClicks = 0;
               this.jsonUiMisses = 0;
               this.jsonUiAround = 0;
               Screen screen = this.minecraft.gui.screen();
               String shown = screen == null ? "nothing" : screen.getClass().getSimpleName();
               if (!this.jsonUi) {
                  BedrockNative.LOGGER.info("Dev test workstations: JSON UI mode {} leaves trading to Java, the villager shows {}", JsonUi.mode(), shown);
               } else if (screen instanceof JsonUiContainerScreen json && json.trading() != null) {
                  JsonUiTrading trading = json.trading();
                  Workstations.TradeView view = trading.view();
                  int offers = view.offers().size();
                  long locked = view.offers().stream().filter(offer -> offer.tier() > view.tier()).count();
                  int buttons = trading.buttons().size();
                  String name = trading.data().nameLabel();
                  List<String> labels = json.labels();
                  boolean pass = json.screen().equals("trade2.trade_screen")
                     && json.cellCount() == 39
                     && offers > 0
                     && buttons == offers
                     && labels.contains(name)
                     && json.ui().instance().problems().count("unresolved-base") == 0;
                  BedrockNative.LOGGER
                     .info(
                        "Dev workstations test JSON UI: trading screen {}, {} slot cells, name '{}', {} trade buttons for {} offers ({} locked above tier {}), labels {}, problems {}: {}",
                        new Object[]{
                           json.screen(),
                           json.cellCount(),
                           name,
                           buttons,
                           offers,
                           locked,
                           view.tier(),
                           labels,
                           json.ui().instance().problems(),
                           pass ? "PASS" : "FAIL"
                        }
                     );
               } else {
                  BedrockNative.LOGGER.warn("Dev workstations test JSON UI: trading shows {} instead of {}: FAIL", shown, "trade2.trade_screen");
               }
            }
         );
      sleep(200L);
   }

   private void chooseTrade(String costItem) {
      boolean[] scrolled = new boolean[1];
      this.minecraft
         .execute(
            () -> {
               JsonUiContainerScreen screen = this.jsonUiScreen();
               JsonUiTrading trading = screen == null ? null : screen.trading();
               int offer = trading == null ? -1 : trading.offerOf(0);
               UiControl button = offer < 0
                  ? null
                  : trading.buttons().stream().filter(control -> trading.data().offerAt(control.itemPath()) == offer).findFirst().orElse(null);
               if (button != null) {
                  scrolled[0] = screen.ui().input().reveal(button);
               }
            }
         );
      sleep(scrolled[0] ? 300L : 50L);
      int[] chosenOffer = new int[]{-1};
      UiControl[] chosenButton = new UiControl[]{null};
      this.minecraft.execute(() -> {
         JsonUiContainerScreen screen = this.jsonUiScreen();
         JsonUiTrading trading = screen == null ? null : screen.trading();
         if (trading != null) {
            this.jsonUiClicks++;
            int offer = trading.offerOf(0);
            UiControl button = trading.buttons().stream().filter(control -> trading.data().offerAt(control.itemPath()) == offer).findFirst().orElse(null);
            if (button != null && button.visible()) {
               float[] at = centre(button);
               UiControl hit = screen.ui().topmost(at[0], at[1]);
               if (hit != button) {
                  this.missed("the first trade's button", "it is not under the mouse (found " + hit + ")");
               } else {
                  press(screen, at);
                  chosenOffer[0] = offer;
                  chosenButton[0] = button;
                  BedrockNative.LOGGER.info("Dev test workstations: trade {} chosen through the JSON UI", offer);
               }
            } else {
               this.missed("the first trade's button", "no button for offer " + offer);
            }
         } else {
            if (this.minecraft.player.containerMenu instanceof MerchantMenu menu) {
               menu.setSelectionHint(0);
               menu.tryMoveItems(0);
               this.minecraft.getConnection().send(new ServerboundSelectTradePacket(0));
            }
         }
      });
      sleep(600L);
      if (chosenButton[0] != null) {
         this.minecraft
            .execute(
               () -> {
                  JsonUiContainerScreen screen = this.jsonUiScreen();
                  JsonUiTrading trading = screen == null ? null : screen.trading();
                  if (trading != null && this.minecraft.player.containerMenu instanceof MerchantMenu menu) {
                     boolean chosen = trading.data().selected() == chosenOffer[0]
                        && chosenButton[0].bag("#toggle_state") != null
                        && chosenButton[0].bag("#toggle_state").truthy();
                     ItemStack payment = menu.getSlot(0).getItem();
                     boolean moved = !payment.isEmpty() && BuiltInRegistries.ITEM.getKey(payment.getItem()).getPath().equals(costItem);
                     BedrockNative.LOGGER
                        .info(
                           "Dev workstations test JSON UI: trade chosen by its button (offer {}), button checked {}, payment moved in {} ({}): {}",
                           new Object[]{chosenOffer[0], chosen, moved, payment, chosen && moved ? "PASS" : "FAIL"}
                        );
                  }
               }
            );
         sleep(200L);
      }
   }

   private void openTrade(Entity villager) {
      this.minecraft.execute(() -> {
         this.minecraft.player.lookAt(Anchor.EYES, villager.getBoundingBox().getCenter());
         this.minecraft.player.getInventory().setSelectedSlot(8);
      });
      sleep(300L);
      this.minecraft
         .execute(
            () -> this.minecraft
               .gameMode
               .interact(this.minecraft.player, villager, new EntityHitResult(villager, villager.getBoundingBox().getCenter()), InteractionHand.MAIN_HAND)
         );
      sleep(2000L);
      this.minecraft.execute(() -> this.report("trade screen"));
   }

   private BlockPos place(int dz, String block) {
      BlockPos[] pos = new BlockPos[1];
      this.minecraft.execute(() -> pos[0] = this.minecraft.player.blockPosition().offset(2, 0, dz));
      sleep(100L);
      this.command("setblock " + pos[0].getX() + " " + pos[0].getY() + " " + pos[0].getZ() + " " + block);
      sleep(800L);
      return pos[0];
   }

   private void open(BlockPos pos, String what) {
      this.minecraft.execute(() -> {
         this.minecraft.player.getInventory().setSelectedSlot(8);
         this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(pos));
      });
      sleep(300L);
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info(
                  "Dev test workstations: use result {} on {} at {}",
                  new Object[]{
                     this.minecraft
                        .gameMode
                        .useItemOn(
                           this.minecraft.player,
                           InteractionHand.MAIN_HAND,
                           new BlockHitResult(Vec3.atCenterOf(pos).add(-0.5, 0.0, 0.0), Direction.WEST, pos, false)
                        ),
                     this.minecraft.level.getBlockState(pos),
                     pos
                  }
               )
         );
      sleep(1500L);
      this.minecraft.execute(() -> this.report("opened " + what));
   }

   private void open(BlockPos pos, String what, String key, String element, int own) {
      this.open(pos, what);
      this.minecraft
         .execute(
            () -> {
               this.jsonUi = JsonUi.drawn(key);
               this.jsonUiClicks = 0;
               this.jsonUiMisses = 0;
               this.jsonUiAround = 0;
               Screen screen = this.minecraft.gui.screen();
               String shown = screen == null ? "nothing" : screen.getClass().getSimpleName();
               if (!this.jsonUi) {
                  BedrockNative.LOGGER
                     .info("Dev test workstations: JSON UI mode {} leaves {} to Java, {} shows {}", new Object[]{JsonUi.mode(), key, what, shown});
               } else if (!(screen instanceof JsonUiContainerScreen json)) {
                  BedrockNative.LOGGER.warn("Dev workstations test JSON UI: {} shows {} instead of {}: FAIL", new Object[]{what, shown, element});
               } else {
                  List labels = json.labels();
                  boolean pass = json.screen().equals(element) && json.cellCount() == own + 36 && json.ui().instance().problems().count("unresolved-base") == 0;
                  BedrockNative.LOGGER
                     .info(
                        "Dev workstations test JSON UI: {} screen {}, {} slot cells, labels {}, problems {}: {}",
                        new Object[]{what, json.screen(), json.cellCount(), labels, json.ui().instance().problems(), pass ? "PASS" : "FAIL"}
                     );
               }
            }
         );
      sleep(200L);
   }

   private void close() {
      sleep(600L);
      this.minecraft.execute(() -> {
         if (this.minecraft.player.containerMenu != this.minecraft.player.inventoryMenu) {
            this.minecraft.player.closeContainer();
         }
      });
      sleep(1200L);
   }

   private void awaitJsonUi() {
      for (int i = 0; JsonUi.mode() != JsonUi.Mode.OFF && i < 40 && !this.onClient(() -> JsonUi.definitions() != null); i++) {
         sleep(500L);
      }
   }

   private @Nullable JsonUiContainerScreen jsonUiScreen() {
      return this.minecraft.gui.screen() instanceof JsonUiContainerScreen screen && screen.getMenu() == this.minecraft.player.containerMenu ? screen : null;
   }

   private void option(int index, String what) {
      boolean[] scrolled = new boolean[1];
      this.minecraft.execute(() -> {
         JsonUiContainerScreen screen = this.jsonUiScreen();
         UiControl button = screen == null ? null : optionButton(screen, index);
         if (button != null) {
            scrolled[0] = screen.ui().input().reveal(button);
         }
      });
      sleep(scrolled[0] ? 300L : 50L);
      this.minecraft.execute(() -> {
         LocalPlayer player = this.minecraft.player;
         AbstractContainerMenu menu = player.containerMenu;
         JsonUiContainerScreen screen = this.jsonUiScreen();
         boolean viaJsonUi = false;
         if (screen != null) {
            this.jsonUiClicks++;
            UiControl button = optionButton(screen, index);
            if (button != null && button.visible()) {
               float[] at = centre(button);
               UiControl hit = screen.ui().topmost(at[0], at[1]);
               if (hit != button) {
                  this.missed(what, "option " + index + " is not under the mouse (found " + hit + ")");
               } else {
                  press(screen, at);
                  viaJsonUi = true;
               }
            } else {
               this.missed(what, "no option button " + index);
            }
         }

         if (!viaJsonUi) {
            if (menu.clickMenuButton(player, index)) {
               this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
            } else {
               BedrockNative.LOGGER.warn("Dev test workstations: Java's screen refuses {}", what);
            }
         }

         BedrockNative.LOGGER.info("Dev test workstations option: {} ({}{})", new Object[]{what, index, viaJsonUi ? " through the JSON UI" : ""});
      });
      sleep(600L);
   }

   private static @Nullable UiControl optionButton(JsonUiContainerScreen screen, int index) {
      UiControl root = screen.ui().instance().root();
      if (root == null) {
         return null;
      } else if (!(screen.getMenu() instanceof EnchantmentMenu)) {
         String collection = screen.getMenu() instanceof StonecutterMenu ? "stones" : "patterns";
         UiControl[] found = new UiControl[]{null};
         root.walk(control -> {
            if (found[0] == null && control.type() == UiType.BUTTON && collection.equals(control.itemCollection()) && control.itemIndex() == index) {
               found[0] = control;
            }
         });
         return found[0];
      } else {
         UiControl grid = root.find("enchantments_grid");
         return grid != null && index < grid.generated().size() ? grid.generated().get(index).find("selectable_button") : null;
      }
   }

   private void rename(String name) {
      this.minecraft.execute(() -> {
         JsonUiContainerScreen screen = this.jsonUiScreen();
         if (screen == null) {
            if (this.minecraft.player.containerMenu instanceof AnvilMenu menu && menu.setItemName(name)) {
               this.minecraft.getConnection().send(new ServerboundRenameItemPacket(name));
            }
         } else {
            this.jsonUiClicks++;
            UiControl box = editBox(screen);
            if (box != null && box.enabled()) {
               press(screen, centre(box));
               if (screen.ui().input().focused() != box) {
                  this.missed("the anvil's name box", "the click didn't focus it");
               } else {
                  screen.keyPressed(new KeyEvent(4, 97, 64));
                  name.codePoints().forEach(codePoint -> screen.charTyped(new CharacterEvent(codePoint)));
                  BedrockNative.LOGGER.info("Dev test workstations: typed '{}' into the JSON UI name box, it shows '{}'", name, screen.ui().input().text(box));
               }
            } else {
               this.missed("the anvil's name box", box == null ? "no name box" : "the name box takes no text");
            }
         }
      });
      sleep(600L);
   }

   private static @Nullable UiControl editBox(JsonUiContainerScreen screen) {
      UiControl[] found = new UiControl[]{null};
      UiControl root = screen.ui().instance().root();
      if (root != null) {
         root.walk(control -> {
            if (found[0] == null && control.type() == UiType.EDIT_BOX && control.visible()) {
               found[0] = control;
            }
         });
      }

      return found[0];
   }

   private void checkAnvil(String name) {
      JsonUiContainerScreen screen = this.jsonUiScreen();
      if (this.jsonUi && screen != null && screen.getMenu() instanceof AnvilMenu menu) {
         UiControl box = editBox(screen);
         String shown = box == null ? "-" : screen.ui().input().text(box);
         String cost = "";

         for (String control : List.of("cost_label_1", "cost_label_0")) {
            UiControl label = screen.ui().instance().find(control);
            if (label != null && label.visible()) {
               cost = label.text();
            }
         }

         boolean pass = shown.equals(name) && cost.endsWith(" " + menu.getCost());
         BedrockNative.LOGGER
            .info(
               "Dev workstations test JSON UI: anvil name box '{}', cost line '{}' (menu cost {}): {}",
               new Object[]{shown, cost, menu.getCost(), pass ? "PASS" : "FAIL"}
            );
      }
   }

   private void checkEnchantingOptions(EnchantmentMenu menu) {
      JsonUiContainerScreen screen = this.jsonUiScreen();
      UiControl grid = screen == null ? null : screen.ui().instance().find("enchantments_grid");
      if (this.jsonUi && grid != null) {
         List<String> rows = new ArrayList<>();
         boolean pass = grid.generated().size() == 3;

         for (int i = 0; i < grid.generated().size(); i++) {
            UiControl row = grid.generated().get(i);
            UiControl selectable = row.find("selectable_button");
            UiControl unselectable = row.find("unselectable_button");
            boolean open = selectable != null && selectable.visible();
            boolean locked = unselectable != null && unselectable.visible();
            rows.add(open ? "takeable" : (locked ? "locked" : "none"));
            pass &= menu.costs[i] > 0 == (open || locked);
         }

         BedrockNative.LOGGER
            .info(
               "Dev workstations test JSON UI: enchanting rows {} (menu costs {} {} {}): {}",
               new Object[]{rows, menu.costs[0], menu.costs[1], menu.costs[2], pass ? "PASS" : "FAIL"}
            );
      }
   }

   private void checkList(String collection, int expected, String what) {
      JsonUiContainerScreen screen = this.jsonUiScreen();
      if (this.jsonUi && screen != null) {
         int[] cells = new int[]{0};
         UiControl root = screen.ui().instance().root();
         if (root != null) {
            root.walk(control -> {
               if (control.type() == UiType.BUTTON && collection.equals(control.itemCollection())) {
                  cells[0]++;
               }
            });
         }

         BedrockNative.LOGGER
            .info(
               "Dev workstations test JSON UI: {} list shows {} options (menu {}): {}",
               new Object[]{what, cells[0], expected, cells[0] == expected && expected > 0 ? "PASS" : "FAIL"}
            );
      }
   }

   private void checkBeacon(int levels) {
      JsonUiContainerScreen screen = this.jsonUiScreen();
      if (this.jsonUi && screen != null) {
         List<String> states = new ArrayList<>();
         boolean pass = true;
         int[] tiers = new int[]{1, 1, 2, 2, 3, 4};
         List<String> powers = List.of("speed", "haste", "resist", "jump", "strength", "regen");

         for (int i = 0; i < powers.size(); i++) {
            UiControl panel = screen.ui().instance().find(powers.get(i) + "_panel");
            boolean enabled = panel != null && panel.enabled();
            states.add(powers.get(i) + (enabled ? " on" : " locked"));
            pass &= panel != null && enabled == levels >= tiers[i];
         }

         BedrockNative.LOGGER.info("Dev workstations test JSON UI: beacon powers {} (levels {}): {}", new Object[]{states, levels, pass ? "PASS" : "FAIL"});
      }
   }

   private static void press(JsonUiContainerScreen screen, float[] at) {
      MouseButtonEvent event = new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(1, 0));
      screen.mouseMoved(at[0], at[1]);
      screen.mouseClicked(event, false);
      screen.mouseReleased(event);
   }

   private static float[] centre(UiControl control) {
      return JsonUiScreen.toGui(control.x() + control.width() / 2.0F, control.y() + control.height() / 2.0F);
   }

   private boolean jsonUiClick(JsonUiContainerScreen screen, int slot, ContainerInput input, String what) {
      this.jsonUiClicks++;
      float[] at = screen.cellCentre(slot);
      Slot under = at == null ? null : screen.slotAt(at[0], at[1]);
      if (under != null && under.index == slot) {
         if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE) {
            this.jsonUiAround++;
            BedrockNative.LOGGER.warn("Dev workstations test JSON UI: '{}' can't be made on the JSON UI, sent to the menu directly", what);
            return false;
         } else {
            MouseButtonEvent event = new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(1, input == ContainerInput.QUICK_MOVE ? 1 : 0));
            screen.mouseMoved(at[0], at[1]);
            screen.mouseClicked(event, false);
            screen.mouseReleased(event);
            return true;
         }
      } else {
         return this.missed(what, "no cell of slot " + slot + " under the mouse (found " + (under == null ? "none" : under.index) + ")");
      }
   }

   private boolean missed(String what, String why) {
      this.jsonUiMisses++;
      BedrockNative.LOGGER.warn("Dev workstations test JSON UI: FAIL, {} for '{}'", why, what);
      return false;
   }

   private void jsonUiReport(String what) {
      this.minecraft
         .execute(
            () -> {
               if (this.jsonUi) {
                  BedrockNative.LOGGER
                     .info(
                        "Dev workstations test JSON UI: {}: {} clicks, options and keys through the JSON UI, {} missed, {} around it: {}",
                        new Object[]{
                           what,
                           this.jsonUiClicks,
                           this.jsonUiMisses,
                           this.jsonUiAround,
                           this.jsonUiClicks > 0 && this.jsonUiMisses == 0 && this.jsonUiAround == 0 ? "PASS" : "FAIL"
                        }
                     );
               }
            }
         );
   }

   private void screenshot(String name) {
      this.minecraft
         .execute(
            () -> Screenshot.grab(
               this.minecraft.gameDirectory,
               name + (this.minecraft.gui.screen() instanceof JsonUiContainerScreen ? "-jsonui" : "") + ".png",
               this.minecraft.gameRenderer.mainRenderTarget(),
               1,
               message -> {}
            )
         );
      sleep(200L);
   }

   private String findInInventory(Predicate<ItemStack> wanted) {
      for (int slot = 0; slot < this.minecraft.player.getInventory().getContainerSize(); slot++) {
         ItemStack stack = this.minecraft.player.getInventory().getItem(slot);
         if (wanted.test(stack)) {
            return stack.getHoverName().getString() + " in slot " + slot;
         }
      }

      return "nothing";
   }

   private void clickItem(Predicate<ItemStack> wanted, String what) {
      this.minecraft.execute(() -> {
         AbstractContainerMenu menu = this.minecraft.player.containerMenu;

         for (int slot = menu.slots.size() - 1; slot >= 0; slot--) {
            if (!menu.getSlot(slot).getItem().isEmpty() && wanted.test(menu.getSlot(slot).getItem())) {
               this.clickNow(slot, ContainerInput.PICKUP, what);
               return;
            }
         }

         BedrockNative.LOGGER.warn("Dev test workstations: nothing to click for '{}'", what);
      });
      sleep(600L);
   }

   private void clickFirstEmpty(String what) {
      this.minecraft.execute(() -> {
         AbstractContainerMenu menu = this.minecraft.player.containerMenu;

         for (int slot = menu.slots.size() - 36; slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) {
               this.clickNow(slot, ContainerInput.PICKUP, what);
               return;
            }
         }
      });
      sleep(600L);
   }

   private void click(int slot, ContainerInput input, String what) {
      this.minecraft.execute(() -> this.clickNow(slot, input, what));
      sleep(600L);
   }

   private void clickNow(int slot, ContainerInput input, String what) {
      LocalPlayer player = this.minecraft.player;
      AbstractContainerMenu menu = player.containerMenu;
      JsonUiContainerScreen screen = this.jsonUiScreen();
      boolean viaJsonUi = screen != null && this.jsonUiClick(screen, slot, input, what);
      if (!viaJsonUi) {
         this.minecraft.gameMode.handleContainerInput(menu.containerId, slot, 0, input, player);
      }

      BedrockNative.LOGGER
         .info(
            "Dev test workstations click: {} (slot {} {}{}): slot now {}, cursor {}; requests accepted {}, rejected {}",
            new Object[]{
               what,
               slot,
               input,
               viaJsonUi ? " through the JSON UI" : "",
               slot >= 0 && slot < menu.slots.size() ? menu.getSlot(slot).getItem() : "-",
               menu.getCarried(),
               this.requests.accepted(),
               this.requests.rejected()
            }
         );
   }

   private void report(String when) {
      AbstractContainerMenu menu = this.minecraft.player.containerMenu;
      List<String> items = new ArrayList<>();

      for (int slot = 0; slot < menu.slots.size(); slot++) {
         ItemStack stack = menu.getSlot(slot).getItem();
         if (!stack.isEmpty()) {
            items.add(slot + "=" + stack.getCount() + "x" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
         }
      }

      BedrockNative.LOGGER
         .info(
            "Dev test workstations menu ({}): {} id {}, cursor {}: {}; requests accepted {}, rejected {}, pending {}",
            new Object[]{
               when,
               menu.getClass().getSimpleName(),
               menu.containerId,
               menu.getCarried(),
               items,
               this.requests.accepted(),
               this.requests.rejected(),
               this.requests.pendingCount()
            }
         );
   }

   private static String hasItem(String item, int quantity) {
      return "testfor @s[hasitem={item=" + item + ",quantity=" + quantity + "}]";
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.command(command);
      }
   }

   private void command(String command) {
      this.minecraft.execute(() -> {
         BedrockNative.LOGGER.info("Dev test command: /{}", command);
         this.minecraft.getConnection().sendCommand(command);
      });
      sleep(400L);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
      }
   }

   private static final class BeaconRulesPoll {
      BedrockBeacons.Powers value = BedrockBeacons.Powers.NONE;

      boolean matches(int primary, int secondary) {
         return this.value.primary() == primary && this.value.secondary() == secondary;
      }
   }
}
