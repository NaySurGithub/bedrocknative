package net.bedrocknative.client.net;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.form.NpcDialogueScreen;
import net.bedrocknative.client.world.BedrockBlocks;
import net.bedrocknative.client.world.BedrockFog;
import net.bedrocknative.client.world.BedrockStructureBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity.Mode;
import net.minecraft.world.level.block.entity.StructureBlockEntity.UpdateType;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.ControlScheme;
import org.cloudburstmc.protocol.bedrock.data.GraphicsOverrideParameterType;
import org.cloudburstmc.protocol.bedrock.data.SimpleEventType;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugArrow;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugBox;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugLine;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugShape;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugSphere;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugText;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddVolumeEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AwardAchievementPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundControlSchemeSetPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.DebugDrawerPacket;
import org.cloudburstmc.protocol.bedrock.packet.GraphicsParameterOverridePacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.MotionPredictionHintsPacket;
import org.cloudburstmc.protocol.bedrock.packet.OnScreenTextureAnimationPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerLocationPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveVolumeEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SimpleEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.SpawnExperienceOrbPacket;
import org.cloudburstmc.protocol.bedrock.packet.TickingAreasLoadStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateEquipPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerLocationPacket.Type;

final class DevGapsTest {
   private static final int FLOOR_RADIUS = 4;
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> inject;
   private BedrockExtras extras;
   private BlockPos origin;

   DevGapsTest(Minecraft minecraft, Consumer<BedrockPacket> inject) {
      this.minecraft = minecraft;
      this.inject = inject;
   }

   void run() {
      this.extras = BedrockExtras.active();
      if (this.extras == null) {
         BedrockNative.LOGGER.info("Dev gaps test: no Bedrock session");
      } else {
         this.commands("gamemode creative", "weather clear", "time set noon", "clear @s");
         this.origin = this.supply(() -> this.minecraft.player.blockPosition());
         this.commands(
            "fill "
               + (this.origin.getX() - 4)
               + " "
               + this.origin.getY()
               + " "
               + (this.origin.getZ() - 4)
               + " "
               + (this.origin.getX() + 4)
               + " "
               + (this.origin.getY() + 8)
               + " "
               + (this.origin.getZ() + 4)
               + " air",
            "fill "
               + (this.origin.getX() - 4)
               + " "
               + (this.origin.getY() - 1)
               + " "
               + (this.origin.getZ() - 4)
               + " "
               + (this.origin.getX() + 4)
               + " "
               + (this.origin.getY() - 1)
               + " "
               + (this.origin.getZ() + 4)
               + " stone"
         );
         this.step("join state", this::joinState);
         this.step("fog", this::fog);
         this.step("scoreboard identity", this::scoreboardIdentity);
         this.step("ticking areas", this::tickingAreas);
         this.step("falling sand and piston", this::syncedUpdates);
         this.step("synthetic packets", this::synthetic);
         this.step("debug shapes", this::debugShapes);
         this.step("locator bar", this::locatorBar);
         this.step("npc dialogue", this::npcDialogue);
         this.step("mount inventory (synthetic)", this::mountInventory);
         this.step("command block", this::commandBlock);
         this.step("structure block", this::structureBlock);
         this.step("permissions after a dimension change and a respawn", this::permissionsKept);
         this.step("simple event", this::simpleEvent);
         this.commands("gamemode creative");
         BedrockNative.LOGGER.info("Dev gaps test: done");
      }
   }

   private void step(String name, Runnable body) {
      try {
         body.run();
      } catch (RuntimeException var4) {
         BedrockNative.LOGGER.error("Dev gaps test {} failed", name, var4);
      }
   }

   private void joinState() {
      this.report(
         "join state",
         () -> "recipes "
            + this.extras.recipes().size()
            + ", trim patterns "
            + this.extras.trims().patterns().size()
            + ", trim materials "
            + this.extras.trims().materials().size()
            + ", inventory options "
            + this.extras.inventoryOptions()
      );
   }

   private void fog() {
      this.report("fog before", () -> this.fogProbe());
      this.commands("fog @s push minecraft:fog_hell bn_hell");
      sleep(600L);
      this.report("fog stack after push hell", () -> this.extras.fog().stack().identifiers().toString());
      this.report("fog hell", () -> this.fogProbe());
      this.screenshot("gaps-fog-hell");
      this.commands("fog @s push minecraft:fog_default bn_default");
      sleep(600L);
      this.report("fog stack after push default", () -> this.extras.fog().stack().identifiers().toString());
      this.report("fog default on top", () -> this.fogProbe());
      this.commands("fog @s pop bn_default");
      sleep(600L);
      this.report("fog stack after pop", () -> this.extras.fog().stack().identifiers().toString());
      this.commands("fog @s remove bn_hell");
      sleep(600L);
      this.report("fog stack after remove", () -> this.extras.fog().stack().identifiers() + " " + this.fogProbe());
   }

   private String fogProbe() {
      FogData data = new FogData();
      data.environmentalStart = -1.0F;
      data.environmentalEnd = -1.0F;
      BedrockFog.modify(data, this.minecraft.gameRenderer.mainCamera(), 160.0F, this.minecraft.level);
      int rgb = Math.round(data.color.x() * 255.0F) << 16 | Math.round(data.color.y() * 255.0F) << 8 | Math.round(data.color.z() * 255.0F);
      return String.format("start %.1f, end %.1f, colour %06X", data.environmentalStart, data.environmentalEnd, rgb);
   }

   private void scoreboardIdentity() {
      this.commands(
         "scoreboard objectives remove bn_gaps",
         "scoreboard objectives add bn_gaps dummy \"§6BN gaps\"",
         "scoreboard objectives setdisplay sidebar bn_gaps",
         "scoreboard players set @s bn_gaps 7"
      );
      this.report("scoreboard player line", this::sidebar);
      this.screenshot("gaps-scoreboard");
      this.commands("scoreboard players reset @s bn_gaps");
      this.report("scoreboard after reset", this::sidebar);
      this.commands("scoreboard objectives remove bn_gaps");
   }

   private String sidebar() {
      Scoreboard scoreboard = this.minecraft.level.getScoreboard();
      Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
      return objective == null
         ? "no sidebar"
         : scoreboard.listPlayerScores(objective)
            .stream()
            .sorted(Comparator.comparing(PlayerScoreEntry::owner))
            .map(entry -> entry.ownerName().getString() + ":" + entry.value())
            .toList()
            .toString();
   }

   private void tickingAreas() {
      this.commands(
         "tickingarea add "
            + this.origin.getX()
            + " "
            + this.origin.getY()
            + " "
            + (this.origin.getZ() + 30)
            + " "
            + (this.origin.getX() + 2)
            + " "
            + this.origin.getY()
            + " "
            + (this.origin.getZ() + 32)
            + " bn_gaps_area true"
      );
      this.report("ticking area just added", () -> "holding the loading screen " + this.extras.holdsLoadingScreen());
      sleep(3000L);
      this.report("ticking area after 3 s", () -> "holding the loading screen " + this.extras.holdsLoadingScreen());
      this.commands("tickingarea remove bn_gaps_area");
   }

   private void syncedUpdates() {
      BlockPos landing = this.origin.offset(2, 0, 0);
      int before = BedrockBlocks.syncedCount();
      this.commands("setblock " + at(landing.above(6)) + " sand");
      sleep(3000L);
      this.report(
         "falling sand",
         () -> "block at landing "
            + this.minecraft.level.getBlockState(landing).getBlock().getName().getString()
            + ", synced updates "
            + (BedrockBlocks.syncedCount() - before)
            + ", falling blocks "
            + this.count(EntityTypes.FALLING_BLOCK)
      );
      this.commands("setblock " + at(landing) + " air");
      BlockPos piston = this.origin.offset(3, 0, -2);
      int beforePiston = BedrockBlocks.syncedCount();
      this.commands(
         "setblock " + at(piston) + " piston [\"facing_direction\"=1]",
         "setblock " + at(piston.above()) + " stone",
         "setblock " + at(piston.east()) + " redstone_block"
      );
      sleep(1500L);
      this.report(
         "piston extended",
         () -> "stone moved to "
            + this.minecraft.level.getBlockState(piston.above(2)).getBlock().getName().getString()
            + ", piston head "
            + this.minecraft.level.getBlockState(piston.above()).getBlock().getName().getString()
            + ", synced updates "
            + (BedrockBlocks.syncedCount() - beforePiston)
      );
      this.screenshot("gaps-piston");
      this.commands("setblock " + at(piston.east()) + " air");
      sleep(1000L);
      this.commands("fill " + at(piston) + " " + at(piston.above(3)) + " air");
   }

   private void synthetic() {
      LocalPlayer player = this.supply(() -> this.minecraft.player);
      Vec3 at = this.supply(player::position);
      SpawnExperienceOrbPacket orb = new SpawnExperienceOrbPacket();
      orb.setPosition(Vector3f.from(at.x, at.y + 0.5, at.z));
      orb.setAmount(17);
      this.inject.accept(orb);
      sleep(1500L);
      this.report(
         "experience orb",
         () -> "collected "
            + this.extras.orbs().collectedCount()
            + ", still flying "
            + this.extras.orbs().count()
            + ", orb entities "
            + this.count(EntityTypes.EXPERIENCE_ORB)
      );
      this.commands("summon pig " + (this.origin.getX() + 3) + " " + this.origin.getY() + " " + this.origin.getZ());
      sleep(1000L);
      Entity pig = this.supply(() -> first(this.minecraft.level.entitiesForRendering(), EntityTypes.PIG));
      if (pig != null) {
         MotionPredictionHintsPacket hint = new MotionPredictionHintsPacket();
         hint.setRuntimeEntityId(this.supply(() -> this.extras.entities().runtimeIdOf(pig.getId())));
         hint.setMotion(Vector3f.from(0.1F, 0.0F, 0.0F));
         hint.setOnGround(false);
         this.inject.accept(hint);
         sleep(100L);
         this.report(
            "motion hint",
            () -> "pig motion x " + String.format("%.2f", pig.getDeltaMovement().x) + " (a hint of 0.10 gravity-adjusted), on ground " + pig.onGround()
         );
      }

      this.commands("kill @e[type=pig]");
      AddVolumeEntityPacket volume = new AddVolumeEntityPacket();
      volume.setId(3);
      volume.setDimension(0);
      volume.setIdentifier("bedrocknative:test_volume");
      volume.setMinBounds(Vector3i.from(0, 0, 0));
      volume.setMaxBounds(Vector3i.from(4, 4, 4));
      this.inject.accept(volume);
      sleep(200L);
      this.report("volume entity added", () -> "volumes " + this.extras.volumes().size());
      RemoveVolumeEntityPacket removeVolume = new RemoveVolumeEntityPacket();
      removeVolume.setId(3);
      removeVolume.setDimension(0);
      this.inject.accept(removeVolume);
      GraphicsParameterOverridePacket graphics = new GraphicsParameterOverridePacket();
      graphics.setParameterType(GraphicsOverrideParameterType.SKY_ZENITH_COLOR);
      graphics.setBiomeIdentifier("minecraft:plains");
      graphics.setValues(Map.of(0.0F, Vector3f.from(1.0F, 0.0F, 0.0F)));
      this.inject.accept(graphics);
      ClientboundControlSchemeSetPacket scheme = new ClientboundControlSchemeSetPacket();
      scheme.setScheme(ControlScheme.CAMERA_RELATIVE);
      this.inject.accept(scheme);
      sleep(300L);
      this.report(
         "state packets",
         () -> "volumes after remove "
            + this.extras.volumes().size()
            + ", graphics overrides "
            + this.extras.graphics().size()
            + ", control scheme "
            + this.extras.controlScheme()
      );
      AwardAchievementPacket achievement = new AwardAchievementPacket();
      achievement.setAchievementId(5);
      this.inject.accept(achievement);
      sleep(700L);
      this.screenshot("gaps-toast");
      this.report("achievement", () -> "toasts " + this.extras.achievementCount());
      OnScreenTextureAnimationPacket animation = new OnScreenTextureAnimationPacket();
      animation.setEffectId(28L);
      this.inject.accept(animation);
      sleep(350L);
      this.screenshot("gaps-screen-animation");
      this.report("screen animation", () -> "played " + this.extras.screenAnimations().playedCount());
      sleep(1500L);
   }

   private void debugShapes() {
      LocalPlayer player = this.supply(() -> this.minecraft.player);
      Vec3 base = this.supply(() -> player.position().add(player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(6.0)));
      List<DebugShape> shapes = new ArrayList<>();
      shapes.add(new DebugBox(1L, 0, vec(base.add(-2.0, 1.5, 0.0)), 1.0F, null, 6.0F, Color.RED, Vector3f.from(1.0F, 2.0F, 1.0F)));
      shapes.add(new DebugSphere(2L, 0, vec(base.add(0.0, 1.5, 0.0)), 1.0F, null, 6.0F, Color.GREEN, 24));
      shapes.add(new DebugLine(3L, 0, vec(base.add(-3.0, 0.2, 2.0)), 1.0F, null, 6.0F, Color.YELLOW, vec(base.add(3.0, 3.0, 2.0))));
      shapes.add(new DebugArrow(4L, 0, vec(base.add(2.0, 0.5, 0.0)), 1.0F, null, 6.0F, Color.CYAN, vec(base.add(2.0, 3.0, 0.0)), null, null, null));
      shapes.add(new DebugText(5L, 0, vec(base.add(0.0, 3.5, 0.0)), 1.0F, null, 6.0F, Color.WHITE, "BedrockNative debug text"));
      DebugDrawerPacket packet = new DebugDrawerPacket();
      packet.getShapes().addAll(shapes);
      this.inject.accept(packet);
      sleep(800L);
      this.report("debug shapes", () -> "live " + this.extras.debugShapes().live().size());
      this.screenshot("gaps-debug-shapes");
      DebugDrawerPacket remove = new DebugDrawerPacket();
      remove.getShapes().add(new DebugShape(2L, 0));
      this.inject.accept(remove);
      sleep(300L);
      this.report("debug shape removed", () -> "live " + this.extras.debugShapes().live().size());
      sleep(6000L);
      this.report("debug shapes expired", () -> "live " + this.extras.debugShapes().live().size());
   }

   private void locatorBar() {
      LocalPlayer player = this.supply(() -> this.minecraft.player);
      Vec3 ahead = this.supply(() -> player.position().add(player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(25.0)));
      PlayerLocationPacket location = new PlayerLocationPacket();
      location.setType(Type.COORDINATES);
      location.setTargetEntityId(987654L);
      location.setPosition(vec(ahead));
      this.inject.accept(location);
      sleep(800L);
      this.report(
         "locator bar",
         () -> "waypoints " + this.extras.waypoints().count() + ", java has waypoints " + this.minecraft.getConnection().getWaypointManager().hasWaypoints()
      );
      this.screenshot("gaps-locator-bar");
      PlayerLocationPacket hide = new PlayerLocationPacket();
      hide.setType(Type.HIDE);
      hide.setTargetEntityId(987654L);
      this.inject.accept(hide);
      sleep(400L);
      this.report(
         "locator bar hidden",
         () -> "waypoints " + this.extras.waypoints().count() + ", java has waypoints " + this.minecraft.getConnection().getWaypointManager().hasWaypoints()
      );
   }

   private void npcDialogue() {
      this.commands(
         "tag @s remove bn_npc_opened",
         "tag @s remove bn_npc_first",
         "tag @s remove bn_npc_closed",
         "kill @e[type=npc]",
         "summon npc " + (this.origin.getX() + 2) + " " + this.origin.getY() + " " + this.origin.getZ()
      );
      sleep(1000L);
      this.commands("dialogue open @e[type=npc,c=1] @s bn_scene");
      this.waitFor(() -> this.extras.npc().screen() != null, 4000L);
      this.report(
         "npc dialogue open",
         () -> {
            NpcDialogueScreen screen = this.extras.npc().screen();
            return screen == null
               ? "no screen"
               : "screen "
                  + screen.getTitle().getString()
                  + ", text '"
                  + screen.dialogue().text()
                  + "', buttons "
                  + screen.dialogue().buttons().stream().map(button -> button.index() + "=" + button.name()).toList()
                  + ", requests "
                  + this.extras.npc().requestCount();
         }
      );
      this.screenshot("gaps-npc-dialogue");
      this.commands("testfor @s[tag=bn_npc_opened]");
      this.minecraft.execute(() -> {
         NpcDialogueScreen screen = this.extras.npc().screen();
         if (screen != null) {
            this.extras.npc().submit(screen, Integer.toString(screen.dialogue().buttons().getFirst().index()));
         }
      });
      sleep(1500L);
      this.report(
         "npc after the first button",
         () -> "screen "
            + (this.extras.npc().screen() != null && this.minecraft.gui.screen() == this.extras.npc().screen() ? "still open" : "closed")
            + ", requests "
            + this.extras.npc().requestCount()
      );
      this.commands("testfor @s[tag=bn_npc_first]");
      this.minecraft.execute(() -> {
         if (this.minecraft.gui.screen() instanceof NpcDialogueScreen screen) {
            screen.onClose();
         }
      });
      sleep(800L);
      this.commands("testfor @s[tag=bn_npc_closed]");
      this.report(
         "npc closed",
         () -> "screen "
            + (this.minecraft.gui.screen() == null ? "none" : this.minecraft.gui.screen().getClass().getSimpleName())
            + ", requests "
            + this.extras.npc().requestCount()
      );
      this.commands("kill @e[type=npc]", "tag @s remove bn_npc_opened", "tag @s remove bn_npc_first", "tag @s remove bn_npc_closed");
   }

   private void mountInventory() {
      this.mount("minecraft:donkey", -7001L, 7001L, 16, true);
      this.mount("minecraft:horse", -7002L, 7002L, 2, true);
      this.mount("minecraft:llama", -7003L, 7003L, 10, true);
      this.mount("minecraft:donkey", -7004L, 7004L, 16, false);
      this.mount("minecraft:llama", -7005L, 7005L, 10, false);
   }

   private void mount(String identifier, long unique, long runtime, int contentSize, boolean containerOpen) {
      Vec3 at = this.supply(() -> this.minecraft.player.position());
      AddEntityPacket add = new AddEntityPacket();
      add.setUniqueEntityId(unique);
      add.setRuntimeEntityId(runtime);
      add.setIdentifier(identifier);
      add.setPosition(Vector3f.from(at.x + 2.0, at.y, at.z));
      add.setMotion(Vector3f.ZERO);
      add.setRotation(Vector2f.ZERO);
      this.inject.accept(add);
      sleep(300L);
      UpdateEquipPacket equip = new UpdateEquipPacket();
      equip.setWindowId((short)9);
      equip.setWindowType((short)ContainerType.HORSE.getId());
      equip.setSize(contentSize);
      equip.setUniqueEntityId(unique);
      equip.setTag(NbtMap.EMPTY);
      this.inject.accept(equip);
      if (containerOpen) {
         ContainerOpenPacket open = new ContainerOpenPacket();
         open.setId((byte)9);
         open.setType(ContainerType.HORSE);
         open.setBlockPosition(Vector3i.ZERO);
         open.setUniqueEntityId(unique);
         this.inject.accept(open);
      }

      InventoryContentPacket content = new InventoryContentPacket();
      content.setContainerId(9);

      for (int slot = 0; slot < contentSize; slot++) {
         content.getContents().add(ItemData.AIR);
      }

      this.inject.accept(content);
      sleep(800L);
      this.report(
         "mount " + identifier + (containerOpen ? "" : " without ContainerOpen"),
         () -> {
            Screen screen = this.minecraft.gui.screen();
            return "content "
               + contentSize
               + ", screen "
               + (screen == null ? "none" : screen.getClass().getSimpleName())
               + ", menu slots "
               + this.minecraft.player.containerMenu.slots.size()
               + " (own "
               + (this.minecraft.player.containerMenu.slots.size() - 36)
               + ")";
         }
      );
      this.screenshot("gaps-mount-" + identifier.substring(identifier.indexOf(58) + 1) + (containerOpen ? "" : "-equip-only"));
      this.minecraft.execute(() -> this.minecraft.player.closeContainer());
      sleep(300L);
      RemoveEntityPacket remove = new RemoveEntityPacket();
      remove.setUniqueEntityId(unique);
      this.inject.accept(remove);
      sleep(200L);
   }

   private void commandBlock() {
      BlockPos pos = this.origin.offset(0, 0, 2);
      this.useCommandBlock("command block use", pos);
      this.screenshot("gaps-command-block");
      this.minecraft.execute(() -> {
         this.minecraft.gui.setScreen(null);
         this.minecraft.getConnection().send(new ServerboundSetCommandBlockPacket(pos, "say bn_command_block_ok", Mode.REDSTONE, true, false, true));
      });
      sleep(1500L);
      this.report(
         "command block after Done",
         () -> this.minecraft.level.getBlockEntity(pos) instanceof CommandBlockEntity block
            ? "command '" + block.getCommandBlock().getCommand() + "', always active " + block.isAutomatic() + ", mode " + block.getMode()
            : "no block entity"
      );
      this.commands("setblock " + at(pos) + " air");
   }

   private void useCommandBlock(String label, BlockPos pos) {
      this.commands("setblock " + at(pos) + " command_block");
      sleep(800L);
      this.minecraft
         .execute(
            () -> {
               this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(pos));
               this.minecraft
                  .gameMode
                  .useItemOn(
                     this.minecraft.player,
                     InteractionHand.MAIN_HAND,
                     new BlockHitResult(Vec3.atCenterOf(pos).add(0.0, 0.0, -0.5), Direction.NORTH, pos, false)
                  );
            }
         );
      sleep(800L);
      this.report(
         label,
         () -> "screen "
            + (this.minecraft.gui.screen() == null ? "none" : this.minecraft.gui.screen().getClass().getSimpleName())
            + ", permissions game master "
            + this.minecraft.player.canUseGameMasterBlocks()
      );
   }

   private void permissionsKept() {
      BlockPos pos = this.origin.offset(0, 0, 2);
      this.report("permissions before", () -> "game master " + this.minecraft.player.canUseGameMasterBlocks());
      this.commands("execute in nether run tp @s 0 100 0");
      this.waitFor(() -> this.minecraft.level.dimension() == Level.NETHER && this.minecraft.getConnection().hasClientLoaded(), 60000L);
      sleep(1000L);
      this.report(
         "permissions in the nether", () -> this.minecraft.level.dimension().identifier() + ", game master " + this.minecraft.player.canUseGameMasterBlocks()
      );
      this.commands("execute in overworld run tp @s " + at(this.origin));
      this.waitFor(() -> this.minecraft.level.dimension() == Level.OVERWORLD && this.minecraft.getConnection().hasClientLoaded(), 60000L);
      sleep(4000L);
      this.report(
         "permissions back in the overworld",
         () -> this.minecraft.level.dimension().identifier() + ", game master " + this.minecraft.player.canUseGameMasterBlocks()
      );
      this.commands("gamemode creative");
      this.useCommandBlock("command block after the dimension change", pos);
      this.minecraft.execute(() -> this.minecraft.gui.setScreen(null));
      this.commands("setblock " + at(pos) + " air", "kill @s");
      this.waitFor(() -> this.minecraft.player.isDeadOrDying(), 10000L);
      this.minecraft.execute(() -> this.minecraft.player.respawn());
      this.waitFor(() -> !this.minecraft.player.isDeadOrDying() && this.minecraft.gui.screen() == null, 10000L);
      sleep(3000L);
      this.commands("gamemode creative", "tp @s " + at(this.origin));
      sleep(2000L);
      this.report("permissions after the respawn", () -> "game master " + this.minecraft.player.canUseGameMasterBlocks());
      this.useCommandBlock("command block after the respawn", pos);
      this.minecraft.execute(() -> this.minecraft.gui.setScreen(null));
      this.commands("setblock " + at(pos) + " air");
   }

   private void structureBlock() {
      BlockPos pos = this.origin.offset(-2, 0, 3);
      BlockPos target = this.origin.offset(-2, 5, 3);
      this.commands(
         "setblock " + at(pos) + " structure_block",
         "setblock " + at(pos.offset(1, 1, 0)) + " gold_block",
         "setblock " + at(pos.offset(1, 1, 1)) + " diamond_block"
      );
      sleep(800L);
      this.minecraft
         .execute(
            () -> {
               this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(pos));
               this.minecraft
                  .gameMode
                  .useItemOn(
                     this.minecraft.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0), Direction.UP, pos, false)
                  );
            }
         );
      sleep(800L);
      this.report(
         "structure block use", () -> "screen " + (this.minecraft.gui.screen() == null ? "none" : this.minecraft.gui.screen().getClass().getSimpleName())
      );
      this.screenshot("gaps-structure-block");
      this.minecraft
         .execute(
            () -> {
               this.minecraft.gui.setScreen(null);
               this.minecraft
                  .getConnection()
                  .send(
                     new ServerboundSetStructureBlockPacket(
                        pos,
                        UpdateType.SAVE_AREA,
                        StructureMode.SAVE,
                        "bn_gaps",
                        new BlockPos(1, 1, 0),
                        new Vec3i(1, 1, 2),
                        Mirror.NONE,
                        Rotation.NONE,
                        "",
                        true,
                        false,
                        true,
                        true,
                        1.0F,
                        0L
                     )
                  );
            }
         );
      sleep(2000L);
      this.report(
         "structure block after Save",
         () -> this.minecraft.level.getBlockEntity(pos) instanceof StructureBlockEntity block
            ? "server name '"
               + BedrockStructureBlocks.serverName(pos)
               + "', java name '"
               + block.getStructureName()
               + "', mode "
               + block.getMode()
               + ", offset "
               + block.getStructurePos()
               + ", size "
               + block.getStructureSize()
            : "no block entity"
      );
      this.minecraft
         .execute(
            () -> this.minecraft
               .getConnection()
               .send(
                  new ServerboundSetStructureBlockPacket(
                     pos,
                     UpdateType.LOAD_AREA,
                     StructureMode.LOAD,
                     "bn_gaps",
                     target.subtract(pos),
                     new Vec3i(1, 1, 2),
                     Mirror.NONE,
                     Rotation.NONE,
                     "",
                     true,
                     false,
                     true,
                     true,
                     1.0F,
                     0L
                  )
               )
         );
      sleep(2000L);
      this.commands("testforblock " + at(target) + " gold_block", "testforblock " + at(target.offset(0, 0, 1)) + " diamond_block");
      this.report(
         "structure loaded by the block",
         () -> "block at the target "
            + this.minecraft.level.getBlockState(target).getBlock().getName().getString()
            + ", next to it "
            + this.minecraft.level.getBlockState(target.offset(0, 0, 1)).getBlock().getName().getString()
      );
      this.commands("fill " + at(target) + " " + at(target.offset(1, 1, 1)) + " air", "structure load bn_gaps " + at(target));
      this.report(
         "structure loaded by the command",
         () -> "block at the target "
            + this.minecraft.level.getBlockState(target).getBlock().getName().getString()
            + ", next to it "
            + this.minecraft.level.getBlockState(target.offset(0, 0, 1)).getBlock().getName().getString()
      );
      this.commands("structure delete bn_gaps", "fill " + at(pos) + " " + at(target.offset(1, 1, 1)) + " air");
   }

   private void simpleEvent() {
      int before = this.supply(() -> this.minecraft.getConnection().getCommands().getRoot().getChildren().size());
      SimpleEventPacket event = new SimpleEventPacket();
      event.setEvent(SimpleEventType.DISABLE_COMMANDS);
      this.inject.accept(event);
      sleep(300L);
      int disabled = this.supply(() -> this.minecraft.getConnection().getCommands().getRoot().getChildren().size());
      SimpleEventPacket enable = new SimpleEventPacket();
      enable.setEvent(SimpleEventType.ENABLE_COMMANDS);
      this.inject.accept(enable);
      sleep(300L);
      int enabled = this.supply(() -> this.minecraft.getConnection().getCommands().getRoot().getChildren().size());
      this.report("simple event", () -> "commands " + before + " -> " + disabled + " with commands disabled -> " + enabled);
      TickingAreasLoadStatusPacket status = new TickingAreasLoadStatusPacket();
      status.setWaitingForPreload(true);
      this.inject.accept(status);
      sleep(300L);
      this.report("ticking area wait", () -> "holding " + this.extras.holdsLoadingScreen());
      TickingAreasLoadStatusPacket done = new TickingAreasLoadStatusPacket();
      done.setWaitingForPreload(false);
      this.inject.accept(done);
      sleep(300L);
      this.report("ticking area wait over", () -> "holding " + this.extras.holdsLoadingScreen());
   }

   private long count(EntityType<?> type) {
      return this.supply(() -> {
         long found = 0L;

         for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity.getType() == type) {
               found++;
            }
         }

         return found;
      });
   }

   private static Entity first(Iterable<Entity> entities, EntityType<?> type) {
      for (Entity entity : entities) {
         if (entity.getType() == type) {
            return entity;
         }
      }

      return null;
   }

   private static Vector3f vec(Vec3 v) {
      return Vector3f.from((float)v.x, (float)v.y, (float)v.z);
   }

   private void waitFor(BooleanSupplier condition, long timeout) {
      long end = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < end && !this.supply(condition::getAsBoolean)) {
         sleep(250L);
      }
   }

   private void report(String label, Supplier<String> state) {
      BedrockNative.LOGGER.info("Dev gaps test {}: {}", label, this.supply(state));
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
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
