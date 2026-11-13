package net.bedrocknative.client.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.bedrocknative.client.ui.JsonUiHud;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.HudData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BossEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.Action;

public final class BedrockBossBars {
   private static final long UUID_MARKER = -5740682150013829120L;
   private static final BossBarColor[] COLORS = new BossBarColor[]{
      BossBarColor.PINK,
      BossBarColor.BLUE,
      BossBarColor.RED,
      BossBarColor.GREEN,
      BossBarColor.YELLOW,
      BossBarColor.PURPLE,
      BossBarColor.PURPLE,
      BossBarColor.WHITE
   };
   private final Minecraft minecraft;
   private final BedrockEntities entities;
   private final Consumer<BedrockPacket> send;
   private final Map<Long, BossEvent> bars = new LinkedHashMap<>();
   private final Map<Long, String> titles = new HashMap<>();
   private final Map<Long, Integer> colors = new HashMap<>();
   private final Set<Long> attached = new HashSet<>();

   public BedrockBossBars(Minecraft minecraft, BedrockEntities entities, Consumer<BedrockPacket> send) {
      this.minecraft = minecraft;
      this.entities = entities;
      this.send = send;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof BossEventPacket;
   }

   public void apply(BedrockPacket packet) {
      if (packet instanceof BossEventPacket event && this.minecraft.getConnection() != null) {
         long id = event.getBossUniqueEntityId();
         BossEvent bar = this.bars.get(id);
         switch (event.getAction()) {
            case CREATE:
            case UPDATE_NAME:
               this.titles.put(id, String.valueOf(event.getTitle()));
            default:
               switch (event.getAction()) {
                  case CREATE:
                  case UPDATE_PROPERTIES:
                  case UPDATE_STYLE:
                     this.colors.put(id, event.getColor());
                  case UPDATE_NAME:
                  default:
                     switch (event.getAction()) {
                        case CREATE:
                           if (bar == null) {
                              bar = new BossEvent(new UUID(-5740682150013829120L, id), title(event), color(event.getColor()), overlay(event.getOverlay())) {};
                              bar.setProgress(progress(event.getHealthPercentage()));
                              this.bars.put(id, bar);
                              this.update(ClientboundBossEventPacket.createAddPacket(bar));
                              this.registerPlayer(event);
                           } else {
                              bar.setName(title(event));
                              bar.setProgress(progress(event.getHealthPercentage()));
                              bar.setColor(color(event.getColor()));
                              bar.setOverlay(overlay(event.getOverlay()));
                              this.update(ClientboundBossEventPacket.createUpdateNamePacket(bar));
                              this.update(ClientboundBossEventPacket.createUpdateProgressPacket(bar));
                              this.update(ClientboundBossEventPacket.createUpdateStylePacket(bar));
                           }
                           break;
                        case UPDATE_NAME:
                           if (bar != null) {
                              bar.setName(title(event));
                              this.update(ClientboundBossEventPacket.createUpdateNamePacket(bar));
                           }
                           break;
                        case UPDATE_PROPERTIES:
                        case UPDATE_STYLE:
                           if (bar != null) {
                              bar.setColor(color(event.getColor()));
                              bar.setOverlay(overlay(event.getOverlay()));
                              this.update(ClientboundBossEventPacket.createUpdateStylePacket(bar));
                           }
                           break;
                        case REMOVE:
                           this.attached.remove(id);
                           this.titles.remove(id);
                           this.colors.remove(id);
                           if (this.bars.remove(id) != null) {
                              this.update(ClientboundBossEventPacket.createRemovePacket(bar.getId()));
                           }
                           break;
                        case UPDATE_PERCENTAGE:
                           if (bar != null) {
                              bar.setProgress(progress(event.getHealthPercentage()));
                              this.update(ClientboundBossEventPacket.createUpdateProgressPacket(bar));
                           }
                     }

                     this.publish();
               }
         }
      }
   }

   private void publish() {
      List<HudData.BossBar> list = new ArrayList<>(this.bars.size());
      this.bars
         .forEach(
            (id, bar) -> {
               String title = this.titles.getOrDefault(id, "");
               list.add(
                  new HudData.BossBar(
                     id, title.startsWith("{") ? BedrockText.rawTextString(title) : title, bar.getProgress(), this.colors.getOrDefault(id, 5), true
                  )
               );
            }
         );
      JsonUiHud.data().bossBars(list);
   }

   private void registerPlayer(BossEventPacket event) {
      BossEventPacket answer = new BossEventPacket();
      answer.setBossUniqueEntityId(event.getBossUniqueEntityId());
      answer.setAction(Action.REGISTER_PLAYER);
      answer.setTitle(event.getTitle());
      answer.setColor(1);
      this.send.accept(answer);
   }

   public void tick() {
      boolean[] changed = new boolean[]{false};
      this.bars.entrySet().removeIf(entry -> {
         boolean gone = this.attached.contains(entry.getKey()) && this.entities.byUniqueId(entry.getKey()) == null;
         if (gone) {
            this.attached.remove(entry.getKey());
            this.update(ClientboundBossEventPacket.createRemovePacket(entry.getValue().getId()));
            changed[0] = true;
         }

         return gone;
      });
      this.bars.forEach((id, bar) -> {
         Entity boss = this.entities.byUniqueId(id);
         if (boss != null) {
            this.attached.add(id);
         }

         if (boss instanceof LivingEntity living && living != this.minecraft.player && living.getMaxHealth() > 0.0F) {
            float progress = progress(living.getHealth() / living.getMaxHealth());
            if (progress != bar.getProgress()) {
               bar.setProgress(progress);
               this.update(ClientboundBossEventPacket.createUpdateProgressPacket(bar));
               changed[0] = true;
            }
         }
      });
      if (changed[0]) {
         this.publish();
      }
   }

   public int count() {
      return this.bars.size();
   }

   public void reset() {
      this.bars.clear();
      this.attached.clear();
      this.titles.clear();
      this.colors.clear();
      this.minecraft.gui.hud.getBossOverlay().reset();
   }

   private void update(ClientboundBossEventPacket packet) {
      this.minecraft.gui.hud.getBossOverlay().update(packet);
   }

   private static Component title(BossEventPacket event) {
      return BedrockText.ui(String.valueOf(event.getTitle()));
   }

   private static float progress(float value) {
      return Math.clamp(value, 0.0F, 1.0F);
   }

   private static BossBarColor color(int color) {
      return color >= 0 && color < COLORS.length ? COLORS[color] : BossBarColor.PURPLE;
   }

   private static BossBarOverlay overlay(int overlay) {
      BossBarOverlay[] values = BossBarOverlay.values();
      return overlay >= 0 && overlay < values.length ? values[overlay] : BossBarOverlay.PROGRESS;
   }
}
