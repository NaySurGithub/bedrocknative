package net.bedrocknative.client.lobby;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Base64;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.net.BedrockConnector;
import net.bedrocknative.net.auth.BedrockSkin;
import net.bedrocknative.ui.PaperDoll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.model.Model.Simple;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

final class LobbyDoll {
   private static final Identifier SKIN = Identifier.fromNamespaceAndPath("bedrocknative", "lobby/skin");
   private static final float MODEL_HEIGHT = 2.125F;
   private static final float FILL = 0.97F;
   private static final float PIVOT_Y = -1.0625F;
   private static final float STANDING_HEIGHT = 2.0625F;
   private static final float ROTATION_X = -10.0F;
   private static final float START_ROTATION = 30.0F;
   private static final int FACE_SIZE = 8;
   private final Minecraft minecraft;
   private @Nullable Simple wide;
   private @Nullable Simple slim;
   private @Nullable Identifier texture;
   private boolean slimModel;
   private boolean resolved;
   private float rotation = 30.0F;
   private float x0 = Float.NaN;
   private float y0;
   private float x1;
   private float y1;

   LobbyDoll(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void draw(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
      this.resolve();
      Identifier skin = this.texture;
      if (skin != null && !(w <= 0.0F) && !(h <= 0.0F)) {
         if (this.wide == null) {
            this.wide = new Simple(this.minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
            this.slim = new Simple(this.minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
         }

         PaperDoll.Stand stand = PaperDoll.stand(x, y, w, h);
         float scale = stand.height() / 2.0625F;
         float boxHeight = scale * 2.125F / 0.97F;
         float centreY = stand.feetY() + scale * -1.0625F;
         float boxWidth = boxHeight * 0.75F;
         this.x0 = stand.centreX() - boxWidth / 2.0F;
         this.y0 = centreY - boxHeight / 2.0F;
         this.x1 = this.x0 + boxWidth;
         this.y1 = this.y0 + boxHeight;
         graphics.skin(
            this.slimModel && this.slim != null ? this.slim : this.wide,
            skin,
            0.97F * (Math.round(this.y1) - Math.round(this.y0)) / 2.125F,
            -10.0F,
            this.rotation,
            -1.0625F,
            Math.round(this.x0),
            Math.round(this.y0),
            Math.round(this.x1),
            Math.round(this.y1)
         );
      }
   }

   void face(GuiGraphicsExtractor graphics, float x, float y, float size) {
      this.resolve();
      Identifier skin = this.texture;
      if (skin != null && !(size <= 0.0F)) {
         graphics.pose().pushMatrix();

         try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(size / 8.0F, size / 8.0F);
            PlayerFaceExtractor.extractRenderState(graphics, skin, 0, 0, 8, true, false, -1);
         } finally {
            graphics.pose().popMatrix();
         }
      }
   }

   boolean hasSkin() {
      this.resolve();
      return this.texture != null;
   }

   boolean contains(double x, double y) {
      return !Float.isNaN(this.x0) && x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
   }

   void turn(double dx) {
      this.rotation += (float)dx * 2.5F;
   }

   private void resolve() {
      if (!this.resolved) {
         this.resolved = true;

         try {
            NativeImage image = this.classic(BedrockConnector.skin());
            if (image != null) {
               this.minecraft.getTextureManager().register(SKIN, new DynamicTexture(() -> "Bedrock lobby skin", image));
               this.texture = SKIN;
               return;
            }
         } catch (RuntimeException e) {
            BedrockNative.LOGGER.debug("Bedrock lobby: the Bedrock skin can't be shown, Java's is", e);
         }

         PlayerSkin java = this.minecraft.getSkinManager().createLookup(this.minecraft.getGameProfile(), false).get();
         this.texture = java.body().texturePath();
         this.slimModel = java.model() == PlayerModelType.SLIM;
      }
   }

   private @Nullable NativeImage classic(BedrockSkin skin) {
      if (skin.persona()) {
         return null;
      }

      JsonObject claims = skin.claims();
      int width = number(claims.get("SkinImageWidth"));
      int height = number(claims.get("SkinImageHeight"));
      if (width == 64 && height == 64 && claims.get("SkinData") instanceof JsonElement data && data.isJsonPrimitive()) {
         byte[] rgba = Base64.getDecoder().decode(data.getAsString());
         if (rgba.length != width * height * 4) {
            return null;
         }

         this.slimModel = claims.get("ArmSize") instanceof JsonElement arms && arms.isJsonPrimitive() && "slim".equals(arms.getAsString());
         NativeImage image = new NativeImage(width, height, false);
         int y = 0;
         int i = 0;

         while (y < height) {
            for (int x = 0; x < width; i += 4) {
               int argb = (rgba[i + 3] & 255) << 24 | (rgba[i] & 255) << 16 | (rgba[i + 1] & 255) << 8 | rgba[i + 2] & 255;
               image.setPixel(x, y, argb);
               x++;
            }

            y++;
         }

         return image;
      } else {
         return null;
      }
   }

   private static int number(@Nullable JsonElement element) {
      return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsInt() : 0;
   }

   void release() {
      if (this.texture == SKIN) {
         this.minecraft.getTextureManager().release(SKIN);
      }

      this.texture = null;
      this.resolved = false;
   }
}
