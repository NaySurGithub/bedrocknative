package net.bedrocknative.client.ui;

import java.util.HashSet;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.net.BedrockExtras;
import net.bedrocknative.client.text.BedrockNameTag;
import net.bedrocknative.ui.PaperDoll;
import net.bedrocknative.ui.UiActorRenderers;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDrawCommand;
import net.bedrocknative.ui.UiTreeDump;
import net.bedrocknative.ui.UiValue;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class JsonUiActors {
   private static final String LOOK_AT_CURSOR = "#look_at_cursor";
   private static final String SCISSOR = "enable_scissor_test";
   private static final float DOLL_OFFSET_Y = 0.0625F;
   private static final float PLAYER_HEIGHT = 1.875F;
   private static final float DOLL_MIDDLE = 0.9F;
   private static final float MARGIN = 0.75F;
   private static final int MAX_PICTURE = 1024;
   private static final int MAX_LOGGED = 64;
   private static final Set<String> LOGGED = new HashSet<>();

   private JsonUiActors() {
   }

   public static synchronized void reset() {
      LOGGED.clear();
   }

   static boolean draw(Minecraft minecraft, GuiGraphicsExtractor graphics, UiDrawCommand.Custom renderer) {
      String kind = renderer.renderer();
      if (UiActorRenderers.drawsActor(kind) && !(renderer.w() <= 0.0F) && !(renderer.h() <= 0.0F)) {
         UiControl control = renderer.control();
         Entity entity = "live_horse_renderer".equals(kind) ? actor(control, renderer) : minecraft.player;
         if (entity == null) {
            return false;
         }

         JsonUiActors.Picture picture = switch (kind) {
            case "live_horse_renderer" -> standing(entity, UiActorRenderers.stand(renderer.x(), renderer.y(), renderer.w(), renderer.h()));
            case "paper_doll_renderer" -> pauseDoll(PaperDoll.stand(renderer.x(), renderer.y(), renderer.w(), renderer.h()));
            default -> inventoryDoll(PaperDoll.of(renderer.x(), renderer.y(), renderer.w(), renderer.h()));
         };
         if (picture.scale() >= 1 && picture.x1() > picture.x0() && picture.y1() > picture.y0()) {
            UiValue scissor = control.prop("enable_scissor_test");
            boolean clip = scissor != null && scissor.truthy();
            if (clip) {
               graphics.enableScissor(
                  Math.round(renderer.x()), Math.round(renderer.y()), Math.round(renderer.x() + renderer.w()), Math.round(renderer.y() + renderer.h())
               );
            }

            try {
               if (entity instanceof LivingEntity living) {
                  float gazeX = (picture.x0() + picture.x1()) / 2.0F;
                  float gazeY = (picture.y0() + picture.y1()) / 2.0F;
                  UiValue look = control.bag("#look_at_cursor");
                  if (look != null && look.truthy()) {
                     gazeX = (float)minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
                     gazeY = (float)minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
                  }

                  InventoryScreen.extractEntityInInventoryFollowsMouse(
                     graphics, picture.x0(), picture.y0(), picture.x1(), picture.y1(), picture.scale(), picture.yOffset(), gazeX, gazeY, living
                  );
               } else {
                  still(minecraft, graphics, entity, picture);
               }
            } finally {
               if (clip) {
                  graphics.disableScissor();
               }
            }

            return true;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private static JsonUiActors.Picture standing(Entity entity, UiActorRenderers.Stand stand) {
      float scale = stand.scale();
      float height = entity.getBbHeight();
      float middleY = stand.feetY() - height / 2.0F * scale;
      float halfW = Math.min(512.0F, (Math.max(entity.getBbWidth(), height) / 2.0F + 0.75F) * scale);
      float halfH = Math.min(512.0F, (height / 2.0F + 0.75F) * scale);
      return new JsonUiActors.Picture(
         Math.round(stand.centreX() - halfW),
         Math.round(middleY - halfH),
         Math.round(stand.centreX() + halfW),
         Math.round(middleY + halfH),
         Math.round(scale),
         0.0F
      );
   }

   private static JsonUiActors.Picture inventoryDoll(PaperDoll.Box box) {
      return new JsonUiActors.Picture(Math.round(box.x0()), Math.round(box.y0()), Math.round(box.x1()), Math.round(box.y1()), Math.round(box.scale()), 0.0625F);
   }

   private static JsonUiActors.Picture pauseDoll(PaperDoll.Stand stand) {
      float scale = stand.height() / 1.875F;
      float middle = stand.feetY() - 0.9625F * scale;
      float half = 1.1F * scale;
      return new JsonUiActors.Picture(
         Math.round(stand.centreX() - half),
         Math.round(middle - half),
         Math.round(stand.centreX() + half),
         Math.round(middle + half),
         Math.round(scale),
         0.0625F
      );
   }

   private static void still(Minecraft minecraft, GuiGraphicsExtractor graphics, Entity entity, JsonUiActors.Picture picture) {
      EntityRenderer<Entity, ?> renderer = (EntityRenderer<Entity, ?>) (EntityRenderer<?, ?>) minecraft.getEntityRenderDispatcher().getRenderer(entity);
      EntityRenderState state = renderer.createRenderState(entity, 1.0F);
      state.shadowPieces.clear();
      state.outlineColor = 0;
      Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + picture.yOffset(), 0.0F);
      graphics.entity(
         state,
         picture.scale(),
         translation,
         new Quaternionf().rotateZ((float) Math.PI),
         new Quaternionf(),
         picture.x0(),
         picture.y0(),
         picture.x1(),
         picture.y1()
      );
   }

   private static @Nullable Entity actor(UiControl control, UiDrawCommand.Custom renderer) {
      UiValue named = control.bag("#entity_id");
      Long id = UiActorRenderers.entityId(named);
      if (id == null) {
         once(
            "none " + named,
            () -> BedrockNative.LOGGER
               .info(
                  "JSON UI {} names no actor (#entity_id {}): drawn as nothing",
                  renderer.renderer(),
                  named == null ? "unset" : UiTreeDump.escape(String.valueOf(named))
               )
         );
         return null;
      } else {
         Entity entity = BedrockExtras.actor(id);
         if (entity == null) {
            once(
               "missing " + id,
               () -> BedrockNative.LOGGER.info("JSON UI {} names actor {}, which this client doesn't know: drawn as nothing", renderer.renderer(), id)
            );
            return null;
         } else {
            once(
               "drawn " + id,
               () -> BedrockNative.LOGGER
                  .info(
                     "JSON UI {} draws actor {} ({}) in a {}x{} GUI px control",
                     new Object[]{renderer.renderer(), id, describe(entity), Math.round(renderer.w()), Math.round(renderer.h())}
                  )
            );
            return entity;
         }
      }
   }

   private static String describe(Entity entity) {
      String type = entity instanceof BedrockActorEntity actor ? actor.bedrockIdentifier() : String.valueOf(EntityType.getKey(entity.getType()));
      BedrockNameTag tag = BedrockNameTag.of(entity);
      return tag != null && !tag.name().isEmpty() ? type + " \"" + UiTreeDump.escape(tag.name()) + "\"" : type;
   }

   private static void once(String key, Runnable log) {
      boolean first;
      synchronized (JsonUiActors.class) {
         first = LOGGED.size() < 64 && LOGGED.add(key);
      }

      if (first) {
         log.run();
      }
   }

   private record Picture(int x0, int y0, int x1, int y1, int scale, float yOffset) {
   }
}
