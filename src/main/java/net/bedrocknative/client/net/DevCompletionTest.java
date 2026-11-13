package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.edit.BedrockBooks;
import net.bedrocknative.client.edit.BedrockSigns;
import net.bedrocknative.client.model.ActorState;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.model.BedrockEmotes;
import net.bedrocknative.client.rules.BedrockGameRules;
import net.bedrocknative.client.rules.BedrockLevelRules;
import net.bedrocknative.client.ui.JsonUiHud;
import net.bedrocknative.client.world.BedrockParticles;
import net.bedrocknative.client.world.BedrockSleeping;
import net.bedrocknative.client.world.BedrockSounds;
import net.bedrocknative.client.world.BedrockWorldEvents;
import net.bedrocknative.model.skin.EmoteLibrary;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.GameRuleData;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.LocatorBarWaypoint;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.LocatorBarWaypoint.WorldPosition;
import org.cloudburstmc.protocol.bedrock.data.definitions.FeatureDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChangeMobPropertyPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundDataDrivenUIShowScreenPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.FeatureRegistryPacket;
import org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RecordStartedPacket;
import org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket.Payload;

final class DevCompletionTest {
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> inject;
   private final BedrockSounds sounds;
   private final BedrockParticles particles;
   private final String part;
   private BedrockExtras extras;
   private BedrockCompletion completion;
   private BlockPos origin;
   private int checks;
   private int failed;

   DevCompletionTest(Minecraft minecraft, Consumer<BedrockPacket> inject, BedrockSounds sounds, BedrockParticles particles) {
      this.minecraft = minecraft;
      this.inject = inject;
      this.sounds = sounds;
      this.particles = particles;
      String test = System.getProperty("bedrocknative.devTest", "completion");
      this.part = test.length() > "completion-".length() ? test.substring("completion-".length()) : "";
   }

   private boolean runs(String name) {
      return this.part.isEmpty() ? !name.equals("ddui") : this.part.equals(name);
   }

   void run() {
      this.extras = BedrockExtras.active();
      this.completion = BedrockCompletion.active();
      if (this.extras != null && this.completion != null) {
         this.commands("gamemode creative", "weather clear", "time set noon", "clear @s");
         this.origin = this.supply(() -> this.minecraft.player.blockPosition());
         this.commands(
            "fill "
               + (this.origin.getX() - 5)
               + " "
               + this.origin.getY()
               + " "
               + (this.origin.getZ() - 5)
               + " "
               + (this.origin.getX() + 5)
               + " "
               + (this.origin.getY() + 8)
               + " "
               + (this.origin.getZ() + 5)
               + " air",
            "fill "
               + (this.origin.getX() - 5)
               + " "
               + (this.origin.getY() - 1)
               + " "
               + (this.origin.getZ() - 5)
               + " "
               + (this.origin.getX() + 5)
               + " "
               + (this.origin.getY() - 1)
               + " "
               + (this.origin.getZ() + 5)
               + " stone"
         );

         for (String[] step : new String[][]{
            {"sleeping"}, {"signs"}, {"books"}, {"crafter"}, {"rules"}, {"events"}, {"music"}, {"synthetic"}, {"sprint"}, {"emote"}, {"ddui"}
         }) {
            if (this.runs(step[0])) {
               this.step(step[0]);
            }
         }

         this.commands("gamemode creative", "time set noon");
         BedrockNative.LOGGER.info("Dev completion test: done: {} checks, {} failed", this.checks, this.failed);
      } else {
         BedrockNative.LOGGER.info("Dev completion test: no Bedrock session");
      }
   }

   private void step(String name) {
      try {
         switch (name) {
            case "sleeping":
               this.sleeping();
               break;
            case "signs":
               this.signs();
               break;
            case "books":
               this.books();
               break;
            case "crafter":
               this.crafter();
               break;
            case "rules":
               this.rules();
               break;
            case "events":
               this.events();
               break;
            case "music":
               this.music();
               break;
            case "synthetic":
               this.synthetic();
               break;
            case "sprint":
               this.sprint();
               break;
            case "emote":
               this.emote();
               break;
            case "ddui":
               this.ddui();
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         this.failed++;
         BedrockNative.LOGGER.error("Dev completion test FAIL {}: step threw", name, e);
      }
   }

   private void sleeping() {
      BlockPos foot = this.origin.offset(2, 0, 0);
      this.commands("time set 14000", "setblock " + at(foot) + " bed [\"direction\"=0,\"head_piece_bit\"=false]");
      sleep(800L);
      List<BlockPos> pieces = this.supply(() -> {
         List<BlockPos> found = new ArrayList<>();

         for (BlockPos around : new BlockPos[]{foot, foot.north(), foot.east(), foot.south(), foot.west()}) {
            if (this.minecraft.level.getBlockState(around).getBlock() instanceof AbstractBedBlock) {
               found.add(around);
            }
         }

         return found;
      });
      BlockPos head = pieces.isEmpty() ? foot : pieces.get(0);
      this.check("sleeping: both bed pieces are in the client level", pieces.size() == 2, "pieces " + pieces);
      this.use(head, Direction.UP, Vec3.atCenterOf(head));
      BedrockNative.LOGGER
         .info(
            "Dev completion test: after the bed use: {} | {} | {}",
            new Object[]{
               this.ask("monsters", "monsters="),
               this.ask("spawnpoint", "spawnpoint="),
               this.ask("block " + head.getX() + " " + head.getY() + " " + head.getZ(), "block=")
            }
         );
      boolean asleep = this.waitFor(5000L, () -> BedrockSleeping.asleep() && this.minecraft.player.isSleeping());
      this.check(
         "sleeping: the client player is in the bed",
         asleep,
         "asleep flag " + BedrockSleeping.asleep() + ", Java sleeping " + this.supply(() -> this.minecraft.player.isSleeping())
      );
      this.check(
         "sleeping: Java's in-bed screen with the Leave Bed button",
         this.waitFor(2000L, () -> this.minecraft.gui.screen() instanceof InBedChatScreen),
         "screen " + this.supply(() -> String.valueOf(this.minecraft.gui.screen()))
      );
      this.screenshot("completion-sleeping");
      String line = this.ask("sleeping", "sleeping=");
      this.check("sleeping: the server sees the player asleep", line.contains("sleeping=true"), line);
      this.check("sleeping: the sleeping notice", BedrockLevelRules.lastNotice() != null, "notice " + BedrockLevelRules.lastNotice());
      boolean woke = this.waitFor(20000L, () -> !BedrockSleeping.asleep() && !this.minecraft.player.isSleeping());
      this.check(
         "sleeping: the night skips and the player wakes",
         woke,
         "asleep flag " + BedrockSleeping.asleep() + ", Java sleeping " + this.supply(() -> this.minecraft.player.isSleeping())
      );
      line = this.ask("time", "time=");
      this.check("sleeping: it is day after the night", timeOf(line) >= 0 && timeOf(line) < 12000, line);
      line = this.ask("sleeping", "sleeping=");
      this.check("sleeping: the server sees the player awake", line.contains("sleeping=false"), line);
      this.commands("time set 14000");
      this.use(head, Direction.UP, Vec3.atCenterOf(head));
      asleep = this.waitFor(5000L, () -> BedrockSleeping.asleep() && this.minecraft.player.isSleeping());
      this.check("sleeping: asleep again", asleep, "asleep flag " + BedrockSleeping.asleep());
      this.minecraft.execute(() -> this.minecraft.player.connection.send(new ServerboundPlayerCommandPacket(this.minecraft.player, Action.STOP_SLEEPING)));
      boolean left = this.waitFor(4000L, () -> !BedrockSleeping.asleep() && !this.minecraft.player.isSleeping());
      this.check("sleeping: Leave Bed wakes the player", left, "asleep flag " + BedrockSleeping.asleep());
      line = this.ask("sleeping", "sleeping=");
      this.check("sleeping: the server agrees", line.contains("sleeping=false"), line);

      for (BlockPos piece : pieces) {
         this.commands("setblock " + at(piece) + " air");
      }

      this.commands("time set noon");
   }

   private static int timeOf(String line) {
      int at = line.indexOf("time=");

      try {
         return at < 0 ? -1 : (int)Double.parseDouble(line.substring(at + 5).trim());
      } catch (NumberFormatException e) {
         return -1;
      }
   }

   private void signs() throws ReflectiveOperationException {
      this.hold("oak_sign", 0);
      BlockPos floor = this.origin.offset(-2, -1, 0);
      BlockPos sign = floor.above();
      int before = BedrockSigns.sentCount();
      this.use(floor, Direction.UP, new Vec3(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5));
      boolean opened = this.waitFor(5000L, () -> this.minecraft.gui.screen() instanceof SignEditScreen);
      this.check("signs: OpenSign opened Java's sign editor", opened, "screen " + this.supply(() -> String.valueOf(this.minecraft.gui.screen())));
      if (opened) {
         this.screenshot("completion-sign");
         Screen screen = this.supply(() -> this.minecraft.gui.screen());
         Field messages = findField(screen.getClass(), "messages");
         String[] lines = (String[])messages.get(screen);
         lines[0] = "BedrockNative";
         lines[1] = "sign test";
         this.minecraft.execute(screen::onClose);
         sleep(1500L);
         this.check("signs: closing the editor sent the text", BedrockSigns.sentCount() > before, "sent " + (BedrockSigns.sentCount() - before));
         String line = this.ask("sign " + sign.getX() + " " + sign.getY() + " " + sign.getZ(), "sign=");
         this.check("signs: the server keeps the text", line.contains("BedrockNative") && line.contains("sign test"), line);
         String shown = this.supply(
            () -> this.minecraft.level.getBlockEntity(sign) instanceof SignBlockEntity entity
               ? entity.getText(SignTextSlot.FRONT).getMessages(false).get(0).getString()
               : "no sign"
         );
         this.check("signs: the sign shows it again from the server's data", shown.equals("BedrockNative"), shown);
         this.commands("setblock " + at(sign) + " air");
      }
   }

   private void books() throws ReflectiveOperationException {
      this.hold("writable_book", 1);
      int before = BedrockBooks.sentCount();
      this.minecraft.execute(() -> this.minecraft.gameMode.useItem(this.minecraft.player, InteractionHand.MAIN_HAND));
      boolean opened = this.waitFor(4000L, () -> this.minecraft.gui.screen() instanceof BookEditScreen);
      this.check("books: the book editor opens", opened, "screen " + this.supply(() -> String.valueOf(this.minecraft.gui.screen())));
      if (opened) {
         sleep(500L);
         Screen screen = this.supply(() -> this.minecraft.gui.screen());
         List<String> pages = (List<String>)findField(screen.getClass(), "pages").get(screen);
         pages.clear();
         pages.add("Page one by the dev test");
         pages.add("Page two");
         Method save = screen.getClass().getDeclaredMethod("saveChanges");
         save.setAccessible(true);
         this.minecraft.execute(() -> {
            this.minecraft.gui.setScreen(null);

            try {
               save.invoke(screen);
            } catch (ReflectiveOperationException e) {
               BedrockNative.LOGGER.error("Dev completion test: saveChanges failed", e);
            }
         });
         sleep(1500L);
         this.check("books: saving sent a BookEdit per new page", BedrockBooks.sentCount() - before == 2, "sent " + (BedrockBooks.sentCount() - before));
         this.check("books: the server did not object", this.supply(() -> this.minecraft.getConnection() != null), "still connected");
      }
   }

   private void crafter() {
      BlockPos crafter = this.origin.offset(0, 0, -3);
      this.commands("setblock " + at(crafter) + " crafter");
      sleep(800L);
      this.use(crafter, Direction.NORTH, Vec3.atCenterOf(crafter));
      boolean opened = this.waitFor(5000L, () -> this.minecraft.player.containerMenu instanceof CrafterMenu);
      this.check(
         "crafter: the crafter opens Java's crafter menu", opened, "menu " + this.supply(() -> this.minecraft.player.containerMenu.getClass().getSimpleName())
      );
      if (opened) {
         this.screenshot("completion-crafter");
         int container = this.supply(() -> this.minecraft.player.containerMenu.containerId);
         this.minecraft.execute(() -> this.minecraft.player.connection.send(new ServerboundContainerSlotStateChangedPacket(4, container, false)));
         boolean disabled = this.waitFor(4000L, () -> this.minecraft.player.containerMenu instanceof CrafterMenu menu && menu.isSlotDisabled(4));
         this.check(
            "crafter: a click disables the slot and the server's data keeps it",
            disabled,
            "slot 4 disabled " + this.supply(() -> this.minecraft.player.containerMenu instanceof CrafterMenu menu && menu.isSlotDisabled(4))
         );
         this.minecraft.execute(() -> this.minecraft.player.closeContainer());
         sleep(800L);
         this.use(crafter, Direction.NORTH, Vec3.atCenterOf(crafter));
         boolean again = this.waitFor(5000L, () -> this.minecraft.player.containerMenu instanceof CrafterMenu menu && menu.isSlotDisabled(4));
         this.check(
            "crafter: reopened, the slot is still disabled",
            again,
            "slot 4 disabled " + this.supply(() -> this.minecraft.player.containerMenu instanceof CrafterMenu menu && menu.isSlotDisabled(4))
         );
         this.minecraft.execute(() -> this.minecraft.player.closeContainer());
         this.commands("setblock " + at(crafter) + " air");
      }
   }

   private void rules() {
      this.commands("gamerule doimmediaterespawn true");
      this.check("rules: doimmediaterespawn is read", this.waitFor(3000L, BedrockGameRules::immediateRespawn), "flag " + BedrockGameRules.immediateRespawn());
      this.commands("kill @s");
      boolean respawned = this.waitFor(
         6000L, () -> this.minecraft.player != null && this.minecraft.player.isAlive() && !(this.minecraft.gui.screen() instanceof DeathScreen)
      );
      sleep(500L);
      this.check(
         "rules: dying respawns at once, no death screen",
         respawned && this.supply(() -> !(this.minecraft.gui.screen() instanceof DeathScreen)),
         "alive " + this.supply(() -> this.minecraft.player.isAlive()) + ", screen " + this.supply(() -> String.valueOf(this.minecraft.gui.screen()))
      );
      this.commands("gamerule doimmediaterespawn false");
      this.check("rules: and back", this.waitFor(3000L, () -> !BedrockGameRules.immediateRespawn()), "flag " + BedrockGameRules.immediateRespawn());
      this.origin = this.supply(() -> this.minecraft.player.blockPosition());
      UUID group = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
      this.inject.accept(gameRule("locatorbar", false));
      this.check("rules: locatorbar is read", this.waitFor(3000L, () -> !BedrockGameRules.locatorBar()), "flag " + BedrockGameRules.locatorBar());
      this.inject.accept(locator(org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket.Action.ADD, group, this.origin.offset(6, 0, 0)));
      sleep(300L);
      this.check(
         "rules: with the rule off a waypoint shows nothing",
         this.supply(() -> this.extras.waypoints().serverCount()) == 0,
         "waypoints " + this.supply(() -> this.extras.waypoints().serverCount())
      );
      this.inject.accept(gameRule("locatorbar", true));
      this.check("rules: and on again", this.waitFor(3000L, BedrockGameRules::locatorBar), "flag " + BedrockGameRules.locatorBar());
      this.inject.accept(locator(org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket.Action.ADD, group, this.origin.offset(6, 0, 0)));
      sleep(300L);
      this.check(
         "rules: a LocatorBar waypoint is tracked",
         this.supply(() -> this.extras.waypoints().serverCount()) == 1 && this.supply(() -> this.minecraft.getConnection().getWaypointManager().hasWaypoints()),
         "waypoints " + this.supply(() -> this.extras.waypoints().serverCount())
      );
      this.screenshot("completion-locator");
      this.inject.accept(locator(org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket.Action.REMOVE, group, this.origin));
      sleep(300L);
      this.check(
         "rules: and removed",
         this.supply(() -> this.extras.waypoints().serverCount()) == 0
            && !this.supply(() -> this.minecraft.getConnection().getWaypointManager().hasWaypoints()),
         "waypoints " + this.supply(() -> this.extras.waypoints().serverCount())
      );
      int old = this.supply(() -> this.minecraft.options.renderDistance().get());
      int updates = ClientBedrockSession.radiusUpdates();
      this.minecraft.execute(() -> this.minecraft.options.renderDistance().set(Math.max(4, old - 2)));
      this.check(
         "rules: a new render distance is requested and answered",
         this.waitFor(6000L, () -> ClientBedrockSession.radiusUpdates() > updates),
         "ChunkRadiusUpdated " + (ClientBedrockSession.radiusUpdates() - updates)
      );
      this.minecraft.execute(() -> this.minecraft.options.renderDistance().set(old));
      sleep(1500L);
      this.commands("globalpause true");
      sleep(1500L);
      boolean paused = this.completion.worldEvents().paused();
      this.known(
         "rules: /globalpause freezes the level and shows the pause screen",
         paused,
         "this server has no /globalpause",
         "paused " + paused + ", screen " + this.supply(() -> String.valueOf(this.minecraft.gui.screen()))
      );
      this.commands("globalpause false");
      sleep(1000L);
      this.check(
         "rules: and resumes",
         !this.completion.worldEvents().paused() && this.supply(() -> !(this.minecraft.gui.screen() instanceof BedrockWorldEvents.GlobalPauseScreen)),
         "paused " + this.completion.worldEvents().paused()
      );
   }

   private static GameRulesChangedPacket gameRule(String name, boolean value) {
      GameRulesChangedPacket packet = new GameRulesChangedPacket();
      packet.getGameRules().add(new GameRuleData<>(name, value));
      return packet;
   }

   private static LocatorBarPacket locator(org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket.Action action, UUID group, BlockPos at) {
      LocatorBarWaypoint waypoint = new LocatorBarWaypoint();
      waypoint.setVisible(true);
      waypoint.setWorldPosition(new WorldPosition(Vector3f.from(at.getX() + 0.5F, at.getY() + 0.5F, at.getZ() + 0.5F), 0));
      waypoint.setColor(new Color(43775));
      waypoint.setUpdateFlag(63);
      LocatorBarPacket packet = new LocatorBarPacket();
      packet.setWaypoints(List.of(new Payload(action, group, waypoint)));
      return packet;
   }

   private void events() {
      Vec3 at = this.supply(() -> this.minecraft.player.position());
      int soundsUnmapped = this.sounds.unmappedCount();
      int particlesUnmapped = this.particles.unmappedCount();
      int shown = this.particles.shownCount();
      LevelEvent[] particleEvents = new LevelEvent[]{
         LevelEvent.PARTICLE_DENY_BLOCK,
         LevelEvent.PARTICLE_DRAGON_EGG,
         LevelEvent.PARTICLE_CROP_EATEN,
         LevelEvent.PARTICLE_EVAPORATE,
         LevelEvent.PARTICLE_DESTROY_ARMOR_STAND,
         LevelEvent.PARTICLE_BREAKING_EGG,
         LevelEvent.PARTICLE_DESTROY_EGG,
         LevelEvent.PARTICLE_KNOCKBACK_ROAR,
         LevelEvent.PARTICLE_TELEPORT_TRAIL,
         LevelEvent.PARTICLE_POINT_CLOUD,
         LevelEvent.PARTICLE_DRIPSTONE_DRIP,
         LevelEvent.PARTICLE_FIZZ_EFFECT,
         LevelEvent.PARTICLE_TURTLE_EGG,
         LevelEvent.PARTICLE_SCULK_SHRIEK,
         LevelEvent.PARTICLE_WIND_EXPLOSION,
         LevelEvent.PARTICLE_TRIAL_SPAWNER_DETECTION,
         LevelEvent.PARTICLE_TRIAL_SPAWNER_SPAWNING,
         LevelEvent.PARTICLE_TRIAL_SPAWNER_EJECTING,
         LevelEvent.PARTICLE_TRIAL_SPAWNER_DETECTION_CHARGED,
         LevelEvent.PARTICLE_TRIAL_SPAWNER_BECOME_CHARGED,
         LevelEvent.PARTICLE_CREAKING_HEART_TRIAL
      };

      for (LevelEvent event : particleEvents) {
         this.level(event, at, 0);
      }

      sleep(500L);
      this.check(
         "events: " + particleEvents.length + " remaining particle events are shown, none unmapped",
         this.particles.unmappedCount() == particlesUnmapped && this.particles.shownCount() - shown == particleEvents.length,
         "shown " + (this.particles.shownCount() - shown) + ", unmapped " + (this.particles.unmappedCount() - particlesUnmapped)
      );
      this.screenshot("completion-particles");
      LevelEvent[] soundEvents = new LevelEvent[]{
         LevelEvent.CAULDRON_FILL_WATER,
         LevelEvent.CAULDRON_TAKE_WATER,
         LevelEvent.CAULDRON_FILL_POTION,
         LevelEvent.CAULDRON_ADD_DYE,
         LevelEvent.CAULDRON_FILL_LAVA,
         LevelEvent.ANIMATION_VAULT_ACTIVATE,
         LevelEvent.SCULK_CATALYST_BLOOM
      };
      int played = this.sounds.playedBedrockCount() + this.sounds.playedCount();

      for (LevelEvent event : soundEvents) {
         this.level(event, at, 0);
      }

      sleep(800L);
      this.known(
         "events: cauldron, vault and catalyst events play their sounds",
         this.sounds.unmappedCount() == soundsUnmapped && this.sounds.playedBedrockCount() + this.sounds.playedCount() - played >= soundEvents.length,
         "the Bedrock install's sounds are not loaded",
         "played "
            + (this.sounds.playedBedrockCount() + this.sounds.playedCount() - played)
            + " of "
            + soundEvents.length
            + ", unmapped "
            + (this.sounds.unmappedCount() - soundsUnmapped)
      );

      for (LevelEvent event : new LevelEvent[]{LevelEvent.ALL_PLAYERS_SLEEPING, LevelEvent.JUMP_PREVENTED, LevelEvent.ACTIVATE_BLOCK, LevelEvent.SET_DATA}) {
         this.level(event, at, 0);
      }

      this.check(
         "events: the consumed level events change nothing", !this.completion.worldEvents().paused(), "paused " + this.completion.worldEvents().paused()
      );
      this.commands(
         "summon pig " + (this.origin.getX() + 3) + " " + this.origin.getY() + " " + this.origin.getZ() + " 0 0 minecraft:entity_born",
         "summon wolf " + (this.origin.getX() - 3) + " " + this.origin.getY() + " " + this.origin.getZ()
      );
      sleep(1500L);
      Entity pig = this.supply(() -> this.first(EntityTypes.PIG));
      Entity wolf = this.supply(() -> this.first(EntityTypes.WOLF));
      if (pig instanceof AgeableMob baby) {
         boolean wasBaby = this.supply(baby::isBaby);
         this.actor(pig, EntityEventType.ENTITY_GROW_UP, 0);
         sleep(300L);
         this.check(
            "events: ENTITY_GROW_UP makes the baby an adult",
            wasBaby && this.supply(() -> !baby.isBaby()),
            "baby before " + wasBaby + ", after " + this.supply(baby::isBaby)
         );
      } else {
         this.known("events: ENTITY_GROW_UP", false, "no baby pig could be summoned", "pig " + pig);
      }

      if (wolf != null) {
         for (EntityEventType type : new EntityEventType[]{
            EntityEventType.SHAKE_WETNESS,
            EntityEventType.SHAKE_WETNESS_STOP,
            EntityEventType.ATTACK_START,
            EntityEventType.ATTACK_STOP,
            EntityEventType.BABY_ANIMAL_FEED,
            EntityEventType.ENTITY_SPAWN,
            EntityEventType.PLAY_AMBIENT,
            EntityEventType.DRINK_MILK
         }) {
            this.actor(wolf, type, 0);
         }

         sleep(500L);
         this.check("events: actor events for a wolf are taken without a fault", this.supply(() -> this.minecraft.getConnection() != null), "still connected");
      }

      this.commands("kill @e[type=pig]", "kill @e[type=wolf]");
   }

   private void level(LevelEvent type, Vec3 at, int data) {
      LevelEventPacket packet = new LevelEventPacket();
      packet.setType(type);
      packet.setPosition(Vector3f.from((float)at.x, (float)at.y + 1.0F, (float)at.z));
      packet.setData(data);
      this.inject.accept(packet);
   }

   private void actor(Entity entity, EntityEventType type, int data) {
      EntityEventPacket packet = new EntityEventPacket();
      packet.setRuntimeEntityId(this.supply(() -> this.extras.entities().runtimeIdOf(entity.getId())));
      packet.setType(type);
      packet.setData(data);
      this.inject.accept(packet);
   }

   private Entity first(EntityType<?> type) {
      for (Entity entity : this.minecraft.level.entitiesForRendering()) {
         if (entity.getType() == type) {
            return entity;
         }
      }

      return null;
   }

   private void music() {
      String line = this.ask("music record.pigstep", "music");
      if (line.contains("failed")) {
         BedrockNative.LOGGER.info("Dev completion test KNOWN music: the Script API of this BDS has no world.playMusic ({})", line);
      } else {
         this.check(
            "music: world.playMusic starts the track",
            this.waitFor(4000L, () -> "record.pigstep".equals(this.completion.music().playing())),
            "playing " + this.completion.music().playing()
         );
         this.ask("queue record.cat", "music");
         this.check("music: a queued track waits", this.completion.music().queued() == 1, "queued " + this.completion.music().queued());
         this.ask("musicstop", "music");
         this.check(
            "music: stopMusic ends it and clears the queue",
            this.waitFor(4000L, () -> this.completion.music().playing() == null && this.completion.music().queued() == 0),
            "playing " + this.completion.music().playing() + ", queued " + this.completion.music().queued()
         );
      }

      LevelEventGenericPacket play = new LevelEventGenericPacket();
      play.setType(LevelEvent.PLAY_CUSTOM_MUSIC);
      play.setTag(
         NbtMap.builder().putString("trackName", "record.cat").putFloat("volume", 0.5F).putFloat("fadeSeconds", 0.2F).putBoolean("repeatMode", true).build()
      );
      this.inject.accept(play);
      this.known(
         "music: PLAY_CUSTOM_MUSIC starts the track",
         this.waitFor(3000L, () -> "record.cat".equals(this.completion.music().playing())),
         "the sound catalog is not loaded or the track is missing",
         "playing " + this.completion.music().playing()
      );
      LevelEventGenericPacket stop = new LevelEventGenericPacket();
      stop.setType(LevelEvent.STOP_CUSTOM_MUSIC);
      stop.setTag(NbtMap.builder().putFloat("fadeSeconds", 0.2F).build());
      this.inject.accept(stop);
      this.check(
         "music: STOP_CUSTOM_MUSIC ends it",
         this.waitFor(3000L, () -> this.completion.music().playing() == null),
         "playing " + this.completion.music().playing()
      );
      BlockPos jukebox = this.origin.offset(0, 0, 3);
      this.commands("setblock " + at(jukebox) + " jukebox");
      sleep(600L);
      RecordStartedPacket started = new RecordStartedPacket();
      started.setBlockPos(Vector3i.from(jukebox.getX(), jukebox.getY(), jukebox.getZ()));
      started.setServerSoundHandle(7770001L);
      this.inject.accept(started);
      sleep(300L);
      this.check(
         "music: RecordStarted ties a record to the jukebox", this.completion.jukeboxes().count() == 1, "records " + this.completion.jukeboxes().count()
      );
      this.commands("setblock " + at(jukebox) + " air");
      this.check(
         "music: the record ends with its jukebox",
         this.waitFor(3000L, () -> this.completion.jukeboxes().count() == 0),
         "records " + this.completion.jukeboxes().count()
      );
   }

   private void synthetic() {
      this.commands("summon bedrocknative:test_golem " + (this.origin.getX() + 3) + " " + this.origin.getY() + " " + this.origin.getZ());
      sleep(1500L);
      Entity golem = this.supply(() -> this.first(BedrockActorEntity.TYPE));
      if (golem != null) {
         ChangeMobPropertyPacket change = new ChangeMobPropertyPacket();
         change.setUniqueEntityId(this.supply(() -> this.extras.entities().uniqueIdOf(golem)));
         change.setProperty("bedrocknative:posed");
         change.setBoolValue(true);
         change.setStringValue("");
         this.inject.accept(change);
         sleep(300L);
         ActorState state = this.supply(() -> BedrockActors.state(golem));
         this.check(
            "synthetic: ChangeMobProperty sets the golem's property by name",
            state != null && Boolean.TRUE.equals(state.data().property("bedrocknative:posed")),
            "posed " + (state == null ? "no state" : String.valueOf(state.data().property("bedrocknative:posed")))
         );
         this.commands("kill @e[type=bedrocknative:test_golem]");
      } else {
         this.known("synthetic: ChangeMobProperty", false, "the test behavior pack is not installed (testing/bds/install.sh)", "no golem");
      }

      FeatureRegistryPacket registry = new FeatureRegistryPacket();
      registry.getFeatures().add(new FeatureDefinition("bedrocknative:test_feature", "{}"));
      this.inject.accept(registry);
      sleep(300L);
      this.check("synthetic: FeatureRegistry is kept", this.completion.keeps().features().size() == 1, "features " + this.completion.keeps().features().size());
   }

   private void sprint() {
      KeyMapping key = this.minecraft.options.keySprint;
      Key bound = InputConstants.getKey(key.saveString());
      this.minecraft.execute(() -> KeyMapping.set(bound, true));
      sleep(250L);
      PlayerAuthInputPacket held = PlayerInputSender.lastBuilt;
      this.check(
         "sprint: the key held standing sends SPRINT_DOWN and SPRINTING",
         held != null && held.getInputData().contains(PlayerAuthInputData.SPRINT_DOWN) && held.getInputData().contains(PlayerAuthInputData.SPRINTING),
         String.valueOf(held == null ? null : held.getInputData())
      );
      this.minecraft.execute(() -> KeyMapping.set(bound, false));
      sleep(250L);
      PlayerAuthInputPacket after = PlayerInputSender.lastBuilt;
      this.check(
         "sprint: after the release SPRINTING stays alone",
         after != null && after.getInputData().contains(PlayerAuthInputData.SPRINTING) && !after.getInputData().contains(PlayerAuthInputData.SPRINT_DOWN),
         String.valueOf(after == null ? null : after.getInputData())
      );
      sleep(2500L);
      PlayerAuthInputPacket later = PlayerInputSender.lastBuilt;
      this.check(
         "sprint: and ends after about 35 ticks",
         later != null && !later.getInputData().contains(PlayerAuthInputData.SPRINTING),
         String.valueOf(later == null ? null : later.getInputData())
      );
   }

   private void emote() {
      Optional<EmoteLibrary.Emote> emote = BedrockEmotes.first();
      if (emote.isEmpty()) {
         this.known("emote: an emote from the install is sent", false, "the install's persona pack lists no emote", "none");
      } else {
         boolean sent = this.supply(() -> BedrockEmotes.play(emote.get()));
         sleep(1500L);
         this.check(
            "emote: " + emote.get().name() + " is sent and the server does not object",
            sent && this.supply(() -> this.minecraft.getConnection() != null),
            "sent " + sent
         );
      }
   }

   private void ddui() {
      ClientboundDataDrivenUIShowScreenPacket show = new ClientboundDataDrivenUIShowScreenPacket();
      show.setScreenId("minecraft:bn_test");
      show.setFormId(4242);
      this.inject.accept(show);
      sleep(2000L);
      this.check(
         "ddui: the answer INVALID_FORM was sent",
         this.completion.dataDrivenUi().shownScreens().contains("minecraft:bn_test"),
         String.valueOf(this.completion.dataDrivenUi().shownScreens())
      );
      this.check("ddui: and the server kept the connection", this.supply(() -> this.minecraft.getConnection() != null), "still connected");
   }

   private String ask(String what, String prefix) {
      this.supply(() -> {
         JsonUiHud.forgetLastChat();
         return null;
      });
      this.commands("scriptevent bedrocknative:completion " + what);
      long end = System.currentTimeMillis() + 5000L;

      while (System.currentTimeMillis() < end) {
         String line = JsonUiHud.lastChat();
         if (line.contains("[bnc]") && line.contains(prefix)) {
            return line;
         }

         sleep(200L);
      }

      return JsonUiHud.lastChat();
   }

   private void hold(String give, int slot) {
      this.commands("replaceitem entity @s slot.hotbar " + slot + " " + give + " 1");
      sleep(500L);
      this.minecraft.execute(() -> this.minecraft.player.getInventory().setSelectedSlot(slot));
      sleep(500L);
   }

   private void use(BlockPos pos, Direction face, Vec3 hit) {
      this.minecraft.execute(() -> this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(pos)));
      sleep(300L);
      this.minecraft
         .execute(() -> this.minecraft.gameMode.useItemOn(this.minecraft.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false)));
      sleep(700L);
   }

   private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
      for (Class<?> c = type; c != null; c = c.getSuperclass()) {
         try {
            Field field = c.getDeclaredField(name);
            field.setAccessible(true);
            return field;
         } catch (NoSuchFieldException var4) {
         }
      }

      throw new NoSuchFieldException(type.getName() + "." + name);
   }

   private void check(String name, boolean ok, String details) {
      this.checks++;
      if (!ok) {
         this.failed++;
      }

      BedrockNative.LOGGER.info("Dev completion test {} {}: {}", new Object[]{ok ? "PASS" : "FAIL", name, details});
   }

   private void known(String name, boolean ok, String why, String details) {
      this.checks++;
      if (ok) {
         BedrockNative.LOGGER.info("Dev completion test PASS {}: {}", name, details);
      } else {
         BedrockNative.LOGGER.info("Dev completion test KNOWN {}: {} ({})", new Object[]{name, why, details});
      }
   }

   private boolean waitFor(long timeout, BooleanSupplier condition) {
      long end = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < end) {
         if (this.supply(condition::getAsBoolean)) {
            return true;
         }

         sleep(200L);
      }

      return this.supply(condition::getAsBoolean);
   }

   private <T> T supply(Supplier<T> value) {
      return this.minecraft.isSameThread() ? value.get() : CompletableFuture.supplyAsync(value, this.minecraft::execute).join();
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
