package net.bedrocknative.client.text;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.text.NameTagLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription.Resource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.jspecify.annotations.Nullable;

public final class BedrockNameTag {
   private static final Style PAD = Style.EMPTY.withFont(new Resource(Identifier.fromNamespaceAndPath("bedrocknative", "pad")));
   private static final boolean TRACE = Boolean.getBoolean("bedrocknative.traceNameTags");
   private static int traced;
   static final int MAX_SCORE_LINES = 16;
   static final int MAX_SCORE_CHARS = 512;
   private String name = "";
   private List<MutableComponent> lines = List.of();
   @Nullable
   private Component joined;
   private String score = "";
   private List<MutableComponent> scoreLines = List.of();
   private List<Component> scoreBelow = List.of();
   @Nullable
   private Boolean alwaysShow;
   @Nullable
   private Boolean alwaysShowFlag;
   @Nullable
   private Boolean canShowFlag;
   private boolean invisible;
   private float height = Float.NaN;
   private float scale = 1.0F;
   @Nullable
   private List<Component> padded;
   private int paddedFor = -1;

   @Nullable
   public static BedrockNameTag of(Entity entity) {
      return ((BedrockNameTag.Holder)entity).bedrocknative$nameTag();
   }

   public static BedrockNameTag getOrCreate(Entity entity) {
      BedrockNameTag.Holder holder = (BedrockNameTag.Holder)entity;
      BedrockNameTag tag = holder.bedrocknative$nameTag();
      if (tag == null) {
         tag = new BedrockNameTag();
         holder.bedrocknative$setNameTag(tag);
      }

      return tag;
   }

   public static void trace(Entity entity, String via, @Nullable String username, @Nullable EntityDataMap data) {
      if (TRACE && traced < 80 && entity instanceof Player player) {
         StringBuilder line = new StringBuilder();
         if (username != null) {
            line.append(" username=").append(escape(username));
         }

         if (data != null) {
            for (EntityDataType<CharSequence> type : List.of(EntityDataTypes.NAME, EntityDataTypes.SCORE, EntityDataTypes.FILTERED_NAME)) {
               if (data.get(type) instanceof CharSequence text) {
                  line.append(' ').append(type).append('=').append(escape(text.toString()));
               }
            }

            if (data.get(EntityDataTypes.NAMETAG_ALWAYS_SHOW) instanceof Byte show) {
               line.append(" always=").append(show);
            }

            Map<EntityFlag, Boolean> flags = data.getFlags();
            if (flags != null) {
               line.append(" can_show=")
                  .append(flags.get(EntityFlag.CAN_SHOW_NAME))
                  .append(" always_flag=")
                  .append(flags.get(EntityFlag.ALWAYS_SHOW_NAME))
                  .append(" invisible=")
                  .append(flags.get(EntityFlag.INVISIBLE));
            }
         }

         if (!line.isEmpty()) {
            traced++;
            BedrockNative.LOGGER.info("Name tag trace {} {} (id {}):{}", new Object[]{via, escape(player.getGameProfile().name()), entity.getId(), line});
         }
      }
   }

   public static String escape(String text) {
      StringBuilder out = new StringBuilder(text.length() + 8);

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == '\n') {
            out.append("\\n");
         } else if ((c < ' ' || c >= 127) && c != 167) {
            out.append(String.format("\\u%04X", Integer.valueOf(c)));
         } else {
            out.append(c);
         }
      }

      return out.toString();
   }

   public boolean update(EntityDataMap data) {
      boolean renamed = false;
      if (data.get(EntityDataTypes.NAME) instanceof CharSequence text && !text.toString().equals(this.name)) {
         this.setName(text.toString());
         renamed = true;
      }

      if (data.get(EntityDataTypes.SCORE) instanceof CharSequence text) {
         String next = capScore(text.toString());
         if (!next.equals(this.score)) {
            this.setScore(next);
         }
      }

      if (data.get(EntityDataTypes.NAMETAG_ALWAYS_SHOW) instanceof Byte show) {
         this.alwaysShow = show != 0;
      }

      if (data.get(EntityDataTypes.HEIGHT) instanceof Float boxHeight) {
         this.height = boxHeight;
      }

      if (data.get(EntityDataTypes.SCALE) instanceof Float actorScale) {
         this.scale = actorScale;
      }

      Map<EntityFlag, Boolean> flags = data.getFlags();
      if (flags != null) {
         if (flags.get(EntityFlag.INVISIBLE) != null) {
            this.invisible = flags.get(EntityFlag.INVISIBLE);
         }

         if (flags.get(EntityFlag.ALWAYS_SHOW_NAME) != null) {
            this.alwaysShowFlag = flags.get(EntityFlag.ALWAYS_SHOW_NAME);
         }

         if (flags.get(EntityFlag.CAN_SHOW_NAME) != null) {
            this.canShowFlag = flags.get(EntityFlag.CAN_SHOW_NAME);
         }
      }

      return renamed;
   }

   public void setName(String text) {
      this.name = text;
      this.lines = text.isEmpty() ? List.of() : BedrockText.lines(text);
      this.joined = text.isEmpty() ? null : BedrockText.format(text);
      this.padded = null;
   }

   public String name() {
      return this.name;
   }

   public void setScore(String text) {
      this.score = capScore(text);
      List<MutableComponent> lines = this.score.isEmpty() ? List.of() : BedrockText.lines(this.score);
      this.scoreLines = lines.size() > 16 ? List.copyOf(lines.subList(0, 16)) : lines;
      this.scoreBelow = List.copyOf(this.scoreLines);
   }

   private static String capScore(String text) {
      return text.length() > 512 ? text.substring(0, 512) : text;
   }

   public String score() {
      return this.score;
   }

   public List<MutableComponent> scoreLines() {
      return this.scoreLines;
   }

   @Nullable
   public Component joined() {
      return this.joined;
   }

   public List<MutableComponent> lines() {
      return this.lines;
   }

   public float scale() {
      return this.scale;
   }

   public boolean alwaysShown(Entity entity) {
      return this.alwaysShown(entity instanceof Player);
   }

   boolean alwaysShown(boolean player) {
      return alwaysShown(player, this.alwaysShow, this.alwaysShowFlag);
   }

   public boolean shows(Entity entity, boolean lookedAt) {
      return this.shows(entity instanceof Player, lookedAt);
   }

   boolean shows(boolean player, boolean lookedAt) {
      return shows(this.joined != null, player, this.invisible, lookedAt, this.alwaysShow, this.alwaysShowFlag, this.canShowFlag);
   }

   static boolean alwaysShown(boolean player, @Nullable Boolean alwaysByte, @Nullable Boolean alwaysFlag) {
      if (Boolean.TRUE.equals(alwaysFlag)) {
         return true;
      } else {
         return alwaysByte != null ? alwaysByte : player;
      }
   }

   static boolean shows(
      boolean named, boolean player, boolean invisible, boolean lookedAt, @Nullable Boolean alwaysByte, @Nullable Boolean alwaysFlag, @Nullable Boolean canShow
   ) {
      if (!named) {
         return false;
      } else {
         return alwaysShown(player, alwaysByte, alwaysFlag) ? !invisible || !player : lookedAt && !invisible && !Boolean.FALSE.equals(canShow);
      }
   }

   public boolean textOnly(Entity entity) {
      return NameTagLayout.textOnly(this.scale, this.joined != null, this.alwaysShown(entity));
   }

   public double boxHeight(Entity entity) {
      return Float.isNaN(this.height) ? entity.getBbHeight() : NameTagLayout.boxHeight(this.height, this.scale);
   }

   public List<Component> paddedLines(Font font) {
      int generation = BedrockGlyphs.generation();
      if (this.padded == null || this.paddedFor != generation) {
         int widest = 0;

         for (Component line : this.lines) {
            widest = Math.max(widest, font.width(line));
         }

         List<Component> result = new ArrayList<>(this.lines.size());

         for (Component line : this.lines) {
            int[] padding = NameTagLayout.padding(font.width(line), widest);
            result.add(Component.empty().append(pad(padding[0])).append(line).append(pad(padding[1])));
         }

         this.padded = List.copyOf(result);
         this.paddedFor = generation;
      }

      return this.padded;
   }

   private static Component pad(int pixels) {
      return pixels <= 0 ? Component.empty() : Component.literal(" ".repeat(pixels)).withStyle(PAD);
   }

   public List<Component> below(boolean shown, @Nullable Component javaScore) {
      boolean score = shown && !this.scoreBelow.isEmpty();
      if (javaScore == null) {
         return score ? this.scoreBelow : List.of();
      } else if (!score) {
         return List.of(javaScore);
      } else {
         List<Component> result = new ArrayList<>(this.scoreBelow.size() + 1);
         result.addAll(this.scoreBelow);
         result.add(javaScore);
         return result;
      }
   }

   public static void submitLines(
      List<Component> lines,
      List<Component> below,
      EntityRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CameraRenderState camera,
      int offset
   ) {
      if (!lines.isEmpty() || !below.isEmpty()) {
         poseStack.pushPose();
         poseStack.scale(1.1312F, 1.1312F, 1.1312F);
         int top = offset + NameTagLayout.firstLineTop(lines.size());

         for (int i = 0; i < lines.size(); i++) {
            collector.submitNameTag(poseStack, state.nameTagAttachment, top + i * 10, lines.get(i), !state.isDiscrete, state.lightCoords, camera);
         }

         for (int i = 0; i < below.size(); i++) {
            collector.submitNameTag(
               poseStack, state.nameTagAttachment, top + (lines.size() + i) * 10, below.get(i), !state.isDiscrete, state.lightCoords, camera
            );
         }

         poseStack.popPose();
      }
   }

   public record Drawn(List<Component> lines, boolean textOnly, List<Component> below, boolean ownPath) {
   }

   public interface Holder {
      @Nullable
      BedrockNameTag bedrocknative$nameTag();

      void bedrocknative$setNameTag(@Nullable BedrockNameTag var1);
   }

   public interface StateHolder {
      @Nullable
      BedrockNameTag.Drawn bedrocknative$drawn();

      void bedrocknative$setDrawn(@Nullable BedrockNameTag.Drawn var1);
   }
}
