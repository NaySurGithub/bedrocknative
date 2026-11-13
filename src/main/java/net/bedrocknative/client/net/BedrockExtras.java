package net.bedrocknative.client.net;

import java.util.List;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.camera.BedrockCamera;
import net.bedrocknative.client.form.BedrockNpcDialogue;
import net.bedrocknative.client.inventory.BedrockInventory;
import net.bedrocknative.client.movement.FreezeDiagnostics;
import net.bedrocknative.client.world.BedrockCommandBlocks;
import net.bedrocknative.client.world.BedrockDebugShapes;
import net.bedrocknative.client.world.BedrockEntities;
import net.bedrocknative.client.world.BedrockFog;
import net.bedrocknative.client.world.BedrockJigsawBlocks;
import net.bedrocknative.client.world.BedrockJukeboxDiscs;
import net.bedrocknative.client.world.BedrockScreenAnimations;
import net.bedrocknative.client.world.BedrockStructureBlocks;
import net.bedrocknative.client.world.BedrockWaypoints;
import net.bedrocknative.client.world.ExperienceOrbs;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.world.state.GraphicsOverrides;
import net.bedrocknative.world.state.InventoryOptions;
import net.bedrocknative.world.state.RecipeUnlocks;
import net.bedrocknative.world.state.TrimRegistry;
import net.bedrocknative.world.state.VolumeEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.ControlScheme;
import org.cloudburstmc.protocol.bedrock.packet.AddVolumeEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AwardAchievementPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundControlSchemeSetPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerRegistryCleanupPacket;
import org.cloudburstmc.protocol.bedrock.packet.DebugDrawerPacket;
import org.cloudburstmc.protocol.bedrock.packet.GraphicsParameterOverridePacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket;
import org.cloudburstmc.protocol.bedrock.packet.MotionPredictionHintsPacket;
import org.cloudburstmc.protocol.bedrock.packet.NpcDialoguePacket;
import org.cloudburstmc.protocol.bedrock.packet.OnScreenTextureAnimationPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerFogPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerLocationPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveVolumeEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerInventoryOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.SpawnExperienceOrbPacket;
import org.cloudburstmc.protocol.bedrock.packet.TickingAreasLoadStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.TrimDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.UnlockedRecipesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateEquipPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket.Action;
import org.cloudburstmc.protocol.bedrock.packet.UnlockedRecipesPacket.ActionType;
import org.jspecify.annotations.Nullable;

public final class BedrockExtras {
   private static final long TICKING_AREA_WAIT_LIMIT_MS = 60000L;
   private static final float MAX_HINT_MOTION = 10.0F;
   private static final long TOAST_INTERVAL_MS = 250L;
   private static volatile @Nullable BedrockExtras active;
   private final Minecraft minecraft;
   private final BedrockEntities entities;
   private final BedrockInventory inventory;
   private final Consumer<BedrockPacket> send;
   private final BedrockFog fog = new BedrockFog();
   private final BedrockWaypoints waypoints;
   private final BedrockDebugShapes debugShapes;
   private final BedrockNpcDialogue npc;
   private final BedrockScreenAnimations screenAnimations;
   private final ExperienceOrbs orbs;
   private final RecipeUnlocks recipes = new RecipeUnlocks();
   private final TrimRegistry trims = new TrimRegistry();
   private final VolumeEntities volumes = new VolumeEntities();
   private final GraphicsOverrides graphics = new GraphicsOverrides();
   private InventoryOptions inventoryOptions = InventoryOptions.DEFAULT;
   private ControlScheme controlScheme = ControlScheme.LOCKED_PLAYER_RELATIVE_STRAFE;
   private boolean waitingForTickingAreas;
   private long waitingSince;
   private long localRuntimeId;
   private int achievements;
   private long lastToast;
   private Consumer<List<String>> newlyUnlocked = ids -> {};

   public BedrockExtras(Minecraft minecraft, BedrockEntities entities, BedrockInventory inventory, Consumer<BedrockPacket> send) {
      this.minecraft = minecraft;
      this.entities = entities;
      this.inventory = inventory;
      this.send = send;
      this.waypoints = new BedrockWaypoints(minecraft);
      this.debugShapes = new BedrockDebugShapes(minecraft, entities::byRuntimeId);
      this.npc = new BedrockNpcDialogue(minecraft, send, entities::runtimeIdOfUnique);
      this.screenAnimations = new BedrockScreenAnimations(minecraft);
      this.orbs = new ExperienceOrbs(minecraft, entities);
      inventory.useEntities(entities::byUniqueId);
      this.waypoints.follow(entities::byUniqueId);
   }

   public static @Nullable BedrockExtras active() {
      return active;
   }

   public static @Nullable Entity actor(long uniqueId) {
      BedrockExtras extras = active;
      return extras == null ? null : extras.entities.byUniqueId(uniqueId);
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof PlayerFogPacket
         || packet instanceof PlayerLocationPacket
         || packet instanceof LocatorBarPacket
         || packet instanceof DebugDrawerPacket
         || packet instanceof NpcDialoguePacket
         || packet instanceof UpdateEquipPacket
         || packet instanceof SpawnExperienceOrbPacket
         || packet instanceof MotionPredictionHintsPacket
         || packet instanceof AwardAchievementPacket
         || packet instanceof OnScreenTextureAnimationPacket
         || packet instanceof ContainerRegistryCleanupPacket
         || packet instanceof UnlockedRecipesPacket
         || packet instanceof TrimDataPacket
         || packet instanceof SetPlayerInventoryOptionsPacket
         || packet instanceof ClientboundControlSchemeSetPacket
         || packet instanceof AddVolumeEntityPacket
         || packet instanceof RemoveVolumeEntityPacket
         || packet instanceof TickingAreasLoadStatusPacket
         || packet instanceof GraphicsParameterOverridePacket;
   }

   public void start(long localRuntimeId, long localUniqueId) {
      this.localRuntimeId = localRuntimeId;
      active = this;
      BedrockCommandBlocks.clear();
      BedrockJigsawBlocks.clear();
      BedrockJukeboxDiscs.clear();
      BedrockStructureBlocks.start(localUniqueId);
      this.recipes.clear();
      this.trims.clear();
      this.volumes.clear();
      this.graphics.clear();
      this.inventoryOptions = InventoryOptions.DEFAULT;
      this.controlScheme = ControlScheme.LOCKED_PLAYER_RELATIVE_STRAFE;
      this.waitingForTickingAreas = false;
      this.achievements = 0;
      this.debugShapes.clear();
      this.orbs.clear();
      this.fog.start();
      this.screenAnimations.start();
      this.waypoints.start(localUniqueId);
   }

   public void changeDimension() {
      this.orbs.clear();
      this.npc.close();
   }

   public void close() {
      if (active == this) {
         active = null;
      }

      this.fog.close();
      this.screenAnimations.close();
      this.npc.close();
      this.waypoints.clear();
      this.debugShapes.clear();
      this.orbs.clear();
   }

   public void tick() {
      this.debugShapes.tick();
      this.orbs.tick();
      this.waypoints.tick();
   }

   public boolean holdsLoadingScreen() {
      return this.waitingForTickingAreas && System.currentTimeMillis() - this.waitingSince < 60000L;
   }

   public void openMountInventory() {
      InteractPacket interact = new InteractPacket();
      interact.setAction(Action.OPEN_INVENTORY);
      interact.setRuntimeEntityId(this.localRuntimeId);
      this.send.accept(interact);
   }

   public void apply(BedrockPacket packet) {
      switch (packet) {
         case PlayerFogPacket fogStack:
            this.fog.apply(fogStack);
            break;
         case PlayerLocationPacket location:
            this.waypoints.apply(location);
            break;
         case LocatorBarPacket locator:
            this.waypoints.apply(locator);
            break;
         case DebugDrawerPacket shapes:
            this.debugShapes.apply(shapes);
            break;
         case NpcDialoguePacket dialogue:
            this.npc.apply(dialogue);
            break;
         case UpdateEquipPacket equip:
            this.inventory.onUpdateEquip(equip);
            break;
         case SpawnExperienceOrbPacket orb:
            this.orbs.apply(orb);
            break;
         case MotionPredictionHintsPacket hint:
            this.onMotionHint(hint);
            break;
         case AwardAchievementPacket achievement:
            this.onAchievement(achievement);
            break;
         case OnScreenTextureAnimationPacket animation:
            this.screenAnimations.apply(animation);
            break;
         case ContainerRegistryCleanupPacket cleanup:
            this.inventory.removeDynamic(cleanup.getContainers());
            break;
         case UnlockedRecipesPacket unlocked:
            List<String> added = this.recipes.apply(unlocked);
            BedrockNative.LOGGER.debug("Unlocked recipes {}: {} new, {} unlocked", new Object[]{unlocked.getAction(), added.size(), this.recipes.size()});
            if (unlocked.getAction() == ActionType.NEWLY_UNLOCKED && !added.isEmpty()) {
               this.newlyUnlocked.accept(added);
            }
            break;
         case TrimDataPacket trim:
            this.trims.apply(trim);
            break;
         case SetPlayerInventoryOptionsPacket options:
            this.inventoryOptions = InventoryOptions.of(options);
            break;
         case ClientboundControlSchemeSetPacket scheme:
            if (scheme.getScheme() != null) {
               this.controlScheme = scheme.getScheme();
               BedrockCamera.controlScheme(this.controlScheme);
            }
            break;
         case AddVolumeEntityPacket add:
            this.volumes.add(add);
            break;
         case RemoveVolumeEntityPacket remove:
            this.volumes.remove(remove);
            break;
         case TickingAreasLoadStatusPacket status:
            if (status.isWaitingForPreload() && !this.waitingForTickingAreas) {
               this.waitingSince = System.currentTimeMillis();
            }

            this.waitingForTickingAreas = status.isWaitingForPreload();
            break;
         case GraphicsParameterOverridePacket override:
            this.graphics.apply(override);
            break;
         default:
      }
   }

   private void onMotionHint(MotionPredictionHintsPacket hint) {
      Entity entity = this.entities.byRuntimeId(hint.getRuntimeEntityId());
      Vector3f motion = hint.getMotion();
      if (entity != null && entity == this.minecraft.player) {
         FreezeDiagnostics.motion("MotionPredictionHints", motion, 0L, "ignored (the local player's motion is predicted)");
      }

      if (entity != null
         && entity != this.minecraft.player
         && motion != null
         && !entity.isLocalInstanceAuthoritative()
         && Float.isFinite(motion.getX())
         && Float.isFinite(motion.getY())
         && Float.isFinite(motion.getZ())) {
         entity.lerpMotion(
            new Vec3(Math.clamp(motion.getX(), -10.0F, 10.0F), Math.clamp(motion.getY(), -10.0F, 10.0F), Math.clamp(motion.getZ(), -10.0F, 10.0F))
         );
         entity.setOnGround(hint.isOnGround());
      }
   }

   private void onAchievement(AwardAchievementPacket packet) {
      this.achievements++;
      long now = System.currentTimeMillis();
      if (now - this.lastToast >= 250L) {
         this.lastToast = now;
         SystemToast.add(
            this.minecraft.gui.toastManager(),
            new SystemToastId(),
            BedrockText.ui("achievement.notification.description"),
            Component.literal("#" + packet.getAchievementId())
         );
      }
   }

   public BedrockEntities entities() {
      return this.entities;
   }

   public BedrockFog fog() {
      return this.fog;
   }

   public BedrockWaypoints waypoints() {
      return this.waypoints;
   }

   public BedrockDebugShapes debugShapes() {
      return this.debugShapes;
   }

   public BedrockNpcDialogue npc() {
      return this.npc;
   }

   public BedrockScreenAnimations screenAnimations() {
      return this.screenAnimations;
   }

   public ExperienceOrbs orbs() {
      return this.orbs;
   }

   public RecipeUnlocks recipes() {
      return this.recipes;
   }

   public TrimRegistry trims() {
      return this.trims;
   }

   public VolumeEntities volumes() {
      return this.volumes;
   }

   public GraphicsOverrides graphics() {
      return this.graphics;
   }

   public InventoryOptions inventoryOptions() {
      return this.inventoryOptions;
   }

   public void changeInventoryOptions(InventoryOptions options) {
      if (!options.equals(this.inventoryOptions)) {
         this.inventoryOptions = options;
         this.send.accept(options.toPacket());
      }
   }

   public void onNewlyUnlocked(Consumer<List<String>> listener) {
      this.newlyUnlocked = listener;
   }

   public ControlScheme controlScheme() {
      return this.controlScheme;
   }

   public int achievementCount() {
      return this.achievements;
   }
}
