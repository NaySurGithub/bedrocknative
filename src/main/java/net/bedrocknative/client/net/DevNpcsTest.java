package net.bedrocknative.client.net;

import com.google.gson.JsonObject;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.model.ActorSnapshot;
import net.bedrocknative.client.model.ActorState;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.model.BedrockModels;
import net.bedrocknative.client.model.BedrockPlayers;
import net.bedrocknative.client.model.PlayerSkins;
import net.bedrocknative.model.entity.BodyRotation;
import net.bedrocknative.model.geometry.Skeleton;
import net.bedrocknative.model.runtime.ActorInstance;
import net.bedrocknative.model.skin.PlayerSkin;
import net.bedrocknative.model.skin.SkinClaims;
import net.bedrocknative.model.skin.SkinModel;
import net.bedrocknative.net.auth.BedrockSkin;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Action;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Entry;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class DevNpcsTest {
   private static final Path SKIN_PACK = DevSkinPack.folder();
   private static final long FIRST_ID = 2113929216L;
   private static final String SKULL_GEOMETRY = "geometry.bedrocknative.test_skull";
   private static final float TOLERANCE = 2.0F;
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> inject;
   private final List<String> failures = new ArrayList<>();
   private final List<Long> injected = new ArrayList<>();
   private int checks;
   private int passed;
   private long nextId = 2113929216L;

   DevNpcsTest(Minecraft minecraft, Consumer<BedrockPacket> inject) {
      this.minecraft = minecraft;
      this.inject = inject;
   }

   void run() {
      CameraType camera = this.onClient(() -> this.minecraft.options.getCameraType());

      try {
         if (this.waitFor(30000L, () -> BedrockModels.registry() != null)) {
            this.commands("gamemode creative", "time set noon", "weather clear");
            Vec3 at = this.onClient(() -> this.minecraft.player.position());
            this.commands(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f 0 10", at.x, at.y, at.z));
            this.run(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            sleep(1000L);
            this.skins(this.onClient(() -> this.minecraft.player.position()));
            this.bodies(this.onClient(() -> this.minecraft.player.position()));
            this.statues();
            this.watchers();
            return;
         }

         this.fail("the model registry never loaded");
      } catch (RuntimeException e) {
         this.fail("exception: " + e);
         BedrockNative.LOGGER.error("Dev npcs test failed with an exception", e);
         return;
      } finally {
         for (long id : this.injected) {
            RemoveEntityPacket remove = new RemoveEntityPacket();
            remove.setUniqueEntityId(id);
            this.inject.accept(remove);
         }

         this.run(() -> this.minecraft.options.setCameraType(camera));
         this.finish();
      }
   }

   private void skins(Vec3 at) {
      PlayerSkins skins = this.onClient(() -> BedrockActors.current().skins());
      int keptBefore = this.onClient(skins::keptAfterRemoval);
      int explainedBefore = this.onClient(skins::explained);
      SerializedSkin kai = this.classic("kai.png", false);
      SerializedSkin efe = this.classic("efe.png", true);
      SerializedSkin zuri = this.classic("zuri.png", false);
      if (kai != null && efe != null && zuri != null) {
         SerializedSkin skull = skull();
         DevNpcsTest.Npc pmmp = this.npc("bn_npc_pmmp", at.add(-3.0, 0.0, 5.0));
         DevNpcsTest.Npc geyser = this.npc("bn_npc_geyser", at.add(-1.5, 0.0, 5.0));
         DevNpcsTest.Npc head = this.npc("", at.add(0.0, 0.0, 4.0));
         DevNpcsTest.Npc statue = this.npc("bn_npc_statue", at.add(1.5, 0.0, 6.0));
         DevNpcsTest.Npc bare = this.npc("bn_npc_bare", at.add(3.5, 0.0, 5.0));
         this.inject.accept(this.list(pmmp, kai));
         this.inject.accept(this.addPlayer(pmmp, null));
         this.inject.accept(this.unlist(pmmp));
         this.inject.accept(this.addPlayer(geyser, null));
         sleep(100L);
         this.inject.accept(this.list(geyser, efe));
         this.inject.accept(this.unlist(geyser));
         EntityDataMap skullData = new EntityDataMap();
         skullData.put(EntityDataTypes.SCALE, 1.08F);
         skullData.put(EntityDataTypes.WIDTH, 0.001F);
         skullData.put(EntityDataTypes.HEIGHT, 0.001F);
         skullData.setFlag(EntityFlag.INVISIBLE, true);
         this.inject.accept(this.addPlayer(head, skullData));
         this.inject.accept(this.list(head, skull));
         this.inject.accept(this.unlist(head));
         sleep(250L);
         EntityDataMap visible = new EntityDataMap();
         visible.setFlag(EntityFlag.INVISIBLE, false);
         this.inject.accept(this.setData(head, visible));
         EntityDataMap twice = new EntityDataMap();
         twice.put(EntityDataTypes.SCALE, 2.0F);
         this.inject.accept(this.list(statue, zuri));
         this.inject.accept(this.addPlayer(statue, twice));
         this.inject.accept(this.unlist(statue));
         this.inject.accept(this.addPlayer(bare, null));
         this.waitFor(
            15000L, () -> this.onClient(() -> this.model(pmmp) != null && this.model(geyser) != null && this.model(head) != null && this.model(statue) != null)
         );
         sleep(3000L);
         this.screenshot("npcs-skins");
         this.check("the PocketMine NPC keeps its skin after its REMOVE", this.onClient(() -> skins.entry(pmmp.uuid) != null));
         this.drawn("the PocketMine NPC", pmmp, PlayerSkin.of(kai).key());
         this.drawn("the Geyser NPC", geyser, PlayerSkin.of(efe).key());
         this.drawn("the Geyser custom head", head, PlayerSkin.of(skull).key());
         this.check(
            "the custom head draws its head-only geometry (" + this.onClient(() -> this.describe(head)) + ")",
            this.onClient(() -> this.model(head) != null && this.model(head).defaultGeometry().contains("geometry.bedrocknative.test_skull"))
         );
         this.drawn("the statue", statue, PlayerSkin.of(zuri).key());
         this.run(() -> {
            Player statuePlayer = this.player(statue);
            BedrockPlayers.watchScale(statuePlayer != null ? statuePlayer.getId() : Integer.MIN_VALUE);
         });
         this.waitFor(3000L, () -> !Float.isNaN(this.onClient(BedrockPlayers::watchedScale)));
         float drawnScale = this.onClient(BedrockPlayers::watchedScale);
         float frameScale = this.onClient(() -> {
            ActorSnapshot frame = this.player(statue) != null ? BedrockPlayers.lastFrame(this.player(statue)) : null;
            return frame != null ? frame.actorScale() : Float.NaN;
         });
         float javaScale = this.onClient(() -> this.player(statue) != null ? this.player(statue).getScale() : Float.NaN);
         log("statue: drawn at {}, frame SCALE {}, Java's scale attribute {}", drawnScale, frameScale, javaScale);
         this.check("the statue is drawn at its SCALE 2 once (drawn at " + drawnScale + ", 4 when applied twice)", Math.abs(drawnScale - 2.0F) < 0.01F);
         this.run(() -> BedrockPlayers.watchScale(Integer.MIN_VALUE));
         this.check(
            "four REMOVEs kept a player in the world with its skin (" + (this.onClient(skins::keptAfterRemoval) - keptBefore) + ")",
            this.onClient(skins::keptAfterRemoval) - keptBefore >= 4
         );
         this.check("the never listed player is the default Steve", this.onClient(() -> this.model(bare) != null && this.model(bare).drawn().isDefault()));
         this.check("the never listed player is explained in the log", this.waitFor(4000L, () -> this.onClient(skins::explained) > explainedBefore));

         for (DevNpcsTest.Npc npc : List.of(pmmp, geyser, head, statue, bare)) {
            RemoveEntityPacket remove = new RemoveEntityPacket();
            remove.setUniqueEntityId(npc.uniqueId);
            this.inject.accept(remove);
            this.injected.remove(npc.uniqueId);
         }

         sleep(500L);
         this.check(
            "RemoveActor takes the unlisted NPCs' skins with them",
            this.onClient(
               () -> skins.entry(pmmp.uuid) == null && skins.entry(geyser.uuid) == null && skins.entry(head.uuid) == null && skins.entry(statue.uuid) == null
            )
         );
      } else {
         this.fail("no vanilla skin pack in the install (" + SKIN_PACK + ")");
      }
   }

   private void bodies(Vec3 at) {
      if (this.onClient(() -> BedrockModels.registry().entity("bedrocknative:test_golem")) == null) {
         this.fail("the test pack's golem is not in the pack stack (testing/bds/install.sh)");
      } else {
         long blocked = this.addGolem(at.add(-2.0, 0.0, 6.0), 0.0F, 0.0F, 90.0F, EntityFlag.BODY_ROTATION_BLOCKED);
         long plain = this.addGolem(at.add(0.0, 0.0, 6.0), 0.0F, 0.0F, 90.0F, null);
         long axis = this.addGolem(at.add(2.0, 0.0, 6.0), 100.0F, 100.0F, 0.0F, EntityFlag.BODY_ROTATION_AXIS_ALIGNED);
         long head = this.addGolem(at.add(4.0, 0.0, 6.0), 0.0F, 135.0F, 0.0F, EntityFlag.BODY_ROTATION_ALWAYS_FOLLOWS_HEAD);
         sleep(1500L);
         this.check("blocked: the body stays at the server's 90 (" + this.body(blocked) + ")", near(this.body(blocked), 90.0F));
         this.check("axis-aligned: the body takes the facing's cardinal 90 (" + this.body(axis) + ")", near(this.body(axis), 90.0F));
         this.check("following the head: the body takes the head's 135 (" + this.body(head) + ")", near(this.body(head), 135.0F));
         log("no flag: body {} for a server body of 90 and a yaw of 0 (Java's head turn keeps it within 50 degrees of the yaw)", this.body(plain));
         this.move(blocked, at.add(-2.0, 0.0, 6.0), 180.0F);
         this.move(axis, at.add(2.0, 0.0, 6.0), -170.0F);
         this.move(plain, at.add(0.0, 0.0, 6.0), 180.0F);
         sleep(1500L);
         this.screenshot("npcs-bodies");
         this.check("blocked: a turn to 180 leaves the body at 90 (" + this.body(blocked) + ")", near(this.body(blocked), 90.0F));
         this.check(
            "axis-aligned: a turn to -170 snaps the body to -180 (" + this.body(axis) + ")",
            near(Mth.wrapDegrees(this.body(axis)), -180.0F) || near(Mth.wrapDegrees(this.body(axis)), 180.0F)
         );
         log("no flag after a turn to 180: body {}", this.body(plain));
      }
   }

   private void statues() {
      this.commands("kill @e[type=bedrocknative:test_statue]");
      Vec3 at = this.onClient(() -> this.minecraft.player.position());
      this.commands(
         String.format(
            Locale.ROOT, "summon bedrocknative:test_statue %.2f %.2f %.2f 90 0 bedrocknative:body_blocked bn_statue_blocked", at.x - 2.0, at.y, at.z + 7.0
         ),
         String.format(
            Locale.ROOT, "summon bedrocknative:test_statue %.2f %.2f %.2f 100 0 bedrocknative:body_axis_aligned bn_statue_axis", at.x + 2.0, at.y, at.z + 7.0
         )
      );
      sleep(2000L);
      ActorState statue = this.named("bn_statue_blocked");
      ActorState aligned = this.named("bn_statue_axis");
      if (statue != null && aligned != null) {
         this.check("the server's statue carries BODY_ROTATION_BLOCKED", this.onClient(() -> statue.data().flag(EntityFlag.BODY_ROTATION_BLOCKED)));
         this.check(
            "the axis-aligned statue carries BODY_ROTATION_AXIS_ALIGNED", this.onClient(() -> aligned.data().flag(EntityFlag.BODY_ROTATION_AXIS_ALIGNED))
         );
         if (!this.onClient(() -> statue.carrier() && statue.evaluations() > 0L && aligned.evaluations() > 0L)) {
            log(
               "SKIP statue bodies: the statues are not drawn (no client entity for bedrocknative:test_statue: run testing/bds/install.sh and restart the BDS)"
            );
            this.commands("kill @e[type=bedrocknative:test_statue]");
         } else {
            float before = this.onClient(statue::lastBodyYaw);
            log("server statues: blocked body {}, axis-aligned body {} (summoned at yaw 90 and 100)", before, this.onClient(aligned::lastBodyYaw));
            this.check(
               "the axis-aligned statue's body is on a cardinal direction (" + this.onClient(aligned::lastBodyYaw) + ")",
               near(this.onClient(aligned::lastBodyYaw), BodyRotation.cardinal(this.onClient(aligned::lastBodyYaw)))
            );
            this.commands("execute as @e[name=bn_statue_blocked] at @s run tp @s ~ ~ ~ -90 0");
            sleep(1500L);
            this.screenshot("npcs-statues");
            this.check(
               "the blocked statue's body did not follow a turn to -90 (" + before + " then " + this.onClient(statue::lastBodyYaw) + ")",
               near(this.onClient(statue::lastBodyYaw), before)
            );
            this.commands("kill @e[type=bedrocknative:test_statue]");
         }
      } else {
         log("SKIP statues: the server has no bedrocknative:test_statue (run testing/bds/install.sh and restart the BDS)");
      }
   }

   private void watchers() {
      this.commands("kill @e[type=bedrocknative:test_watcher]");
      Vec3 at = this.onClient(() -> this.minecraft.player.position());
      double z = at.z + 6.0;
      this.commands(
         String.format(Locale.ROOT, "summon bedrocknative:test_watcher %.2f %.2f %.2f 0 0 bedrocknative:watch_head bn_watch_head", at.x - 1.5, at.y, z),
         String.format(Locale.ROOT, "summon bedrocknative:test_watcher %.2f %.2f %.2f 0 0 bedrocknative:watch_root bn_watch_root", at.x + 1.5, at.y, z)
      );
      sleep(2000L);
      ActorState head = this.named("bn_watch_head");
      ActorState root = this.named("bn_watch_root");
      if (head != null && root != null) {
         double[] watcherX = new double[]{at.x - 1.5, at.x + 1.5};

         for (double dx : new double[]{0.0, -3.5, 3.5}) {
            String spot = dx == 0.0 ? "front" : (dx < 0.0 ? "west" : "east");

            for (int w = 0; w < 2; w++) {
               ActorState watcher = w == 0 ? head : root;
               double cx = at.x + dx;
               double cz = z + 5.0;
               float yaw = (float)Math.toDegrees(Math.atan2(-(watcherX[w] - cx), z - cz));
               float pitch = (float)(-Math.toDegrees(Math.atan2(0.06999999999999984, Math.hypot(watcherX[w] - cx, z - cz))));
               this.commands(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", cx, at.y, cz, yaw, pitch));
               sleep(800L);
               if (!this.onClient(() -> watcher.evaluations() > 0L)) {
                  log(
                     "SKIP watcher looks: the watchers are not drawn (no client entity for bedrocknative:test_watcher: run testing/bds/install.sh and restart the BDS)"
                  );
                  this.commands("kill @e[type=bedrocknative:test_watcher]");
                  return;
               }

               String what = w == 0 ? "the head turned by camera_rotation(2)" : "the root turned by rotation_to_camera(1)";
               this.screenshot("npcs-watchers-" + spot + (w == 0 ? "-head" : "-root"));
               float[] facing = this.onClient(() -> this.facing(watcher));
               log("watcher, {}, seen from the {}: drawn head yaw {}, toward the camera {}, body {}", what, spot, facing[1], facing[2], facing[3]);
               this.check(what + " faces the camera from the " + spot + " (" + facing[0] + " degrees off)", facing[0] <= 10.0F);
            }
         }

         this.commands("kill @e[type=bedrocknative:test_watcher]");
      } else {
         log("SKIP watchers: the server has no bedrocknative:test_watcher (run testing/bds/install.sh and restart the BDS)");
      }
   }

   private float[] facing(ActorState state) {
      ActorInstance instance = state.instance();
      Skeleton skeleton = instance != null ? instance.drawnSkeleton() : null;
      ActorSnapshot snapshot = state.lastSnapshot();
      Entity entity = this.minecraft.level.getEntity(state.javaId());
      int bone = skeleton != null ? skeleton.geometry().bone("head") : -1;
      if (bone >= 0 && snapshot != null && entity != null) {
         Vector3f center = Skeleton.toWorld(skeleton.transform(bone, new Vector3f(0.0F, 27.0F, 0.0F)), snapshot.bodyYaw(), 1.0F);
         Vector3f front = Skeleton.toWorld(skeleton.transform(bone, new Vector3f(0.0F, 27.0F, -3.0F)), snapshot.bodyYaw(), 1.0F).sub(center);
         Vec3 camera = this.minecraft.gameRenderer.mainCamera().position();
         double toX = camera.x - (entity.getX() + center.x);
         double toZ = camera.z - (entity.getZ() + center.z);
         float frontYaw = (float)Math.toDegrees(Math.atan2(-front.x, front.z));
         float cameraYaw = (float)Math.toDegrees(Math.atan2(-toX, toZ));
         return new float[]{Math.abs(Mth.wrapDegrees(frontYaw - cameraYaw)), frontYaw, cameraYaw, snapshot.bodyYaw()};
      } else {
         return new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
      }
   }

   private DevNpcsTest.Npc npc(String name, Vec3 position) {
      long id = this.nextId++;
      this.injected.add(id);
      return new DevNpcsTest.Npc(name, new UUID(-4693861455903064064L, id), id, position);
   }

   private PlayerListPacket list(DevNpcsTest.Npc npc, SerializedSkin skin) {
      Entry entry = new Entry(npc.uuid());
      entry.setAction(Action.ADD);
      entry.setEntityId(npc.uniqueId());
      entry.setName(npc.name());
      entry.setXuid("");
      entry.setPlatformChatId("");
      entry.setSkin(skin);
      entry.setTrustedSkin(true);
      entry.setColor(Color.LIGHT_GRAY);
      PlayerListPacket packet = new PlayerListPacket();
      packet.setAction(Action.ADD);
      packet.getEntries().add(entry);
      return packet;
   }

   private PlayerListPacket unlist(DevNpcsTest.Npc npc) {
      Entry entry = new Entry(npc.uuid());
      entry.setAction(Action.REMOVE);
      PlayerListPacket packet = new PlayerListPacket();
      packet.setAction(Action.REMOVE);
      packet.getEntries().add(entry);
      return packet;
   }

   private AddPlayerPacket addPlayer(DevNpcsTest.Npc npc, @Nullable EntityDataMap data) {
      AddPlayerPacket add = new AddPlayerPacket();
      add.setUuid(npc.uuid());
      add.setUsername(npc.name());
      add.setUniqueEntityId(npc.uniqueId());
      add.setRuntimeEntityId(npc.uniqueId());
      add.setPlatformChatId("");
      add.setPosition(org.cloudburstmc.math.vector.Vector3f.from(npc.position().x, npc.position().y, npc.position().z));
      add.setMotion(org.cloudburstmc.math.vector.Vector3f.ZERO);
      add.setRotation(org.cloudburstmc.math.vector.Vector3f.from(0.0F, 180.0F, 180.0F));
      add.setHand(ItemData.AIR);
      add.setDeviceId("");
      if (data != null) {
         add.getMetadata().putAll(data);
      }

      return add;
   }

   private SetEntityDataPacket setData(DevNpcsTest.Npc npc, EntityDataMap data) {
      SetEntityDataPacket packet = new SetEntityDataPacket();
      packet.setRuntimeEntityId(npc.uniqueId());
      packet.setMetadata(data);
      return packet;
   }

   private long addGolem(Vec3 position, float yaw, float headYaw, float bodyYaw, @Nullable EntityFlag flag) {
      long id = this.nextId++;
      this.injected.add(id);
      AddEntityPacket add = new AddEntityPacket();
      add.setIdentifier("bedrocknative:test_golem");
      add.setUniqueEntityId(id);
      add.setRuntimeEntityId(id);
      add.setPosition(org.cloudburstmc.math.vector.Vector3f.from(position.x, position.y, position.z));
      add.setMotion(org.cloudburstmc.math.vector.Vector3f.ZERO);
      add.setRotation(Vector2f.from(0.0F, yaw));
      add.setHeadRotation(headYaw);
      add.setBodyRotation(bodyYaw);
      EntityDataMap data = add.getMetadata();
      data.put(EntityDataTypes.WIDTH, 1.0F);
      data.put(EntityDataTypes.HEIGHT, 2.0F);
      if (flag != null) {
         data.setFlag(flag, true);
      }

      this.inject.accept(add);
      return id;
   }

   private void move(long id, Vec3 position, float yaw) {
      MoveEntityAbsolutePacket move = new MoveEntityAbsolutePacket();
      move.setRuntimeEntityId(id);
      move.setPosition(org.cloudburstmc.math.vector.Vector3f.from(position.x, position.y, position.z));
      move.setRotation(org.cloudburstmc.math.vector.Vector3f.from(0.0F, yaw, yaw));
      move.setOnGround(true);
      this.inject.accept(move);
   }

   private float body(long id) {
      return this.onClient(() -> {
         for (ActorState state : BedrockActors.current().states()) {
            if (state.runtimeId() == id) {
               return state.evaluations() > 0L ? state.lastBodyYaw() : Float.NaN;
            }
         }

         return Float.NaN;
      });
   }

   private @Nullable ActorState named(String name) {
      return this.onClient(() -> {
         for (ActorState state : BedrockActors.current().states()) {
            if (name.equals(state.data().value(EntityDataTypes.NAME))) {
               return state;
            }
         }

         return null;
      });
   }

   private static boolean near(float value, float expected) {
      return Float.isFinite(value) && Math.abs(Mth.wrapDegrees(value - expected)) <= 2.0F;
   }

   private @Nullable Player player(DevNpcsTest.Npc npc) {
      for (Entity entity : this.minecraft.level.entitiesForRendering()) {
         if (entity instanceof Player player && player.getUUID().equals(npc.uuid())) {
            return player;
         }
      }

      return null;
   }

   private @Nullable SkinModel model(DevNpcsTest.Npc npc) {
      Player player = this.player(npc);
      return player != null ? BedrockPlayers.model(player) : null;
   }

   private String describe(DevNpcsTest.Npc npc) {
      Player player = this.player(npc);
      String described = player != null ? BedrockPlayers.describe(player) : null;
      return described != null ? described : "not drawn";
   }

   private void drawn(String what, DevNpcsTest.Npc npc, String key) {
      this.check(what + " draws its own skin (" + this.onClient(() -> this.describe(npc)) + ")", this.onClient(() -> {
         SkinModel model = this.model(npc);
         return model != null && model.drawn().key().equals(key);
      }));
   }

   private @Nullable SerializedSkin classic(String texture, boolean slim) {
      if (!Files.isRegularFile(SKIN_PACK.resolve(texture))) {
         return null;
      }

      try {
         JsonObject claims = BedrockSkin.classicFromSkinPack(SKIN_PACK, texture, slim).claims();
         return SkinClaims.toSerialized(claims);
      } catch (Exception e) {
         this.fail("reading " + texture + " from the skin pack: " + e);
         return null;
      }
   }

   private static SerializedSkin skull() {
      byte[] rgba = new byte[16384];

      for (int y = 0; y < 64; y++) {
         for (int x = 0; x < 64; x++) {
            int p = (y * 64 + x) * 4;
            boolean hat = x >= 32;
            rgba[p] = (byte)(hat ? 48 : 64);
            rgba[p + 1] = (byte)(hat ? 48 : 192);
            rgba[p + 2] = (byte)(hat ? 48 : 80);
            rgba[p + 3] = (byte)(hat && y >= 16 ? 0 : 255);
         }
      }

      String geometry = "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.bedrocknative.test_skull\",\"texture_width\":64,\"texture_height\":64},\"bones\":[{\"name\":\"head\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[-4,0.5,-4],\"size\":[8,8,8],\"uv\":[0,0]}]},{\"name\":\"hat\",\"parent\":\"head\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[-4,0.5,-4],\"size\":[8,8,8],\"uv\":[32,0],\"inflate\":0.25}]}]}]}";
      return SerializedSkin.builder()
         .skinId("bn_test_skull_skull")
         .playFabId("")
         .skinResourcePatch("{\"geometry\":{\"default\":\"geometry.bedrocknative.test_skull\"}}")
         .skinData(ImageData.of(64, 64, rgba))
         .geometryData(geometry)
         .geometryDataEngineVersion("1.21.0")
         .armSize("wide")
         .fullSkinId("bn_test_skull_skull")
         .capeId("")
         .premium(true)
         .persona(false)
         .trusted(true)
         .build();
   }

   private void check(String what, boolean ok) {
      this.checks++;
      if (ok) {
         this.passed++;
         log("ok: {}", what);
      } else {
         this.fail(what);
      }
   }

   private void fail(String what) {
      this.failures.add(what);
      BedrockNative.LOGGER.warn("Dev npcs test FAILED: {}", what);
   }

   private void finish() {
      log("{} of {} checks passed{}", this.passed, this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures);
      log("done");
   }

   private static void log(String message, Object... arguments) {
      BedrockNative.LOGGER.info("Dev npcs test: " + message, arguments);
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(1000L);
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
   }

   private boolean waitFor(long millis, BooleanSupplier condition) {
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < millis) {
         if (condition.getAsBoolean()) {
            return true;
         }

         sleep(100L);
      }

      return condition.getAsBoolean();
   }

   private void run(Runnable task) {
      this.minecraft.execute(task);
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private record Npc(String name, UUID uuid, long uniqueId, Vec3 position) {
   }
}
