package net.bedrocknative.client.world;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.bedrocknative.client.ui.JsonUiHud;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.HudData;
import net.bedrocknative.world.state.ScoreboardIdentities;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType;
import org.cloudburstmc.protocol.bedrock.data.ScoreInfo;
import org.cloudburstmc.protocol.bedrock.data.ScoreInfo.ScorerType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveObjectivePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetDisplayObjectivePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetScorePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetScoreboardIdentityPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockScoreboard {
   private final Minecraft minecraft;
   private final BedrockEntities entities;
   private final Map<Long, BedrockScoreboard.Entry> entries = new HashMap<>();
   private final Set<String> ascending = new HashSet<>();
   private final ScoreboardIdentities identities = new ScoreboardIdentities();
   @Nullable
   private String sidebarObjective;
   private String sidebarTitle = "";

   public BedrockScoreboard(Minecraft minecraft, BedrockEntities entities) {
      this.minecraft = minecraft;
      this.entities = entities;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof SetDisplayObjectivePacket
         || packet instanceof SetScorePacket
         || packet instanceof RemoveObjectivePacket
         || packet instanceof SetScoreboardIdentityPacket;
   }

   public void apply(BedrockPacket packet) {
      if (this.minecraft.level != null) {
         Scoreboard scoreboard = this.minecraft.level.getScoreboard();
         switch (packet) {
            case SetDisplayObjectivePacket display:
               this.onDisplay(scoreboard, display);
               break;
            case RemoveObjectivePacket remove:
               Objective objective = scoreboard.getObjective(remove.getObjectiveId());
               if (objective != null) {
                  scoreboard.removeObjective(objective);
               }

               this.ascending.remove(remove.getObjectiveId());
               this.entries.values().removeIf(entry -> entry.objective().equals(remove.getObjectiveId()));
               if (remove.getObjectiveId().equals(this.sidebarObjective)) {
                  this.sidebarObjective = null;
               }
               break;
            case SetScoreboardIdentityPacket identity:
               for (long scoreboardId : this.identities.apply(identity)) {
                  this.remove(scoreboard, scoreboardId);
               }
               break;
            case SetScorePacket scores:
               for (ScoreInfo info : scores.getInfos()) {
                  if (info.getType() == ScorerType.INVALID) {
                     this.remove(scoreboard, info.getScoreboardId());
                  } else {
                     this.set(scoreboard, info);
                  }
               }
               break;
            default:
         }

         this.publishSidebar();
      }
   }

   public void reset() {
      this.entries.clear();
      this.ascending.clear();
      this.identities.clear();
      this.sidebarObjective = null;
   }

   private void publishSidebar() {
      if (this.sidebarObjective == null) {
         JsonUiHud.data().sidebar(null, true, List.of());
      } else {
         List<HudData.SidebarEntry> lines = this.entries
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().objective().equals(this.sidebarObjective))
            .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
            .map(entry -> new HudData.SidebarEntry(entry.getKey(), entry.getValue().text(), entry.getValue().score()))
            .toList();
         JsonUiHud.data().sidebar(this.sidebarTitle, this.ascending.contains(this.sidebarObjective), lines);
      }
   }

   private void onDisplay(Scoreboard scoreboard, SetDisplayObjectivePacket packet) {
      String id = packet.getDisplaySlot();

      DisplaySlot slot = switch (id) {
         case "sidebar" -> DisplaySlot.SIDEBAR;
         case "list" -> DisplaySlot.LIST;
         case "belowname" -> DisplaySlot.BELOW_NAME;
         default -> null;
      };
      if (slot != null) {
         id = packet.getObjectiveId();
         if (slot == DisplaySlot.SIDEBAR) {
            this.sidebarObjective = id != null && !id.isEmpty() ? id : null;
            this.sidebarTitle = packet.getDisplayName() == null ? "" : packet.getDisplayName();
         }

         if (id != null && !id.isEmpty()) {
            Component name = BedrockText.format(packet.getDisplayName() == null ? "" : packet.getDisplayName());
            Objective objective = scoreboard.getObjective(id);
            if (objective == null) {
               objective = scoreboard.addObjective(id, ObjectiveCriteria.DUMMY, name, RenderType.INTEGER, false, null);
            } else {
               objective.setDisplayName(name);
            }

            boolean wasAscending = this.ascending.contains(id);
            boolean nowAscending = packet.getSortOrder() == 0;
            if (nowAscending) {
               this.ascending.add(id);
            } else {
               this.ascending.remove(id);
            }

            if (wasAscending != nowAscending) {
               for (BedrockScoreboard.Entry entry : this.entries.values()) {
                  if (entry.objective().equals(id)) {
                     this.write(scoreboard, objective, entry);
                  }
               }
            }

            scoreboard.setDisplayObjective(slot, objective);
         } else {
            scoreboard.setDisplayObjective(slot, null);
         }
      }
   }

   private void set(Scoreboard scoreboard, ScoreInfo info) {
      Objective objective = scoreboard.getObjective(info.getObjectiveId());
      if (objective != null) {
         String owner;
         Component display;
         String text;
         if (info.getType() == ScorerType.FAKE) {
            owner = fakeOwner(info.getScoreboardId());
            text = info.getName() == null ? "" : info.getName();
            display = BedrockText.format(text);
         } else {
            long entityId = info.getEntityId();
            Entity entity = this.entities.byUniqueId(entityId);
            long identified = this.identities.playerOf(info.getScoreboardId());
            if (entity == null && identified != Long.MIN_VALUE) {
               entityId = identified;
               entity = this.entities.byUniqueId(identified);
            }

            owner = entity == null ? fakeOwner(info.getScoreboardId()) : entity.getScoreboardName();
            String listed = entity == null ? this.entities.playerName(entityId) : null;
            display = (Component)(entity instanceof Player player
               ? BedrockText.format(player.getGameProfile().name())
               : (entity != null ? entity.getName() : BedrockText.format(listed != null ? listed : Long.toString(entityId))));
            text = display.getString();
         }

         BedrockScoreboard.Entry previous = this.entries.get(info.getScoreboardId());
         if (previous != null && (!previous.objective().equals(info.getObjectiveId()) || !previous.owner().equals(owner))) {
            this.remove(scoreboard, info.getScoreboardId());
         }

         BedrockScoreboard.Entry entry = new BedrockScoreboard.Entry(info.getObjectiveId(), owner, info.getScore(), display, text);
         this.entries.put(info.getScoreboardId(), entry);
         this.write(scoreboard, objective, entry);
      }
   }

   private void write(Scoreboard scoreboard, Objective objective, BedrockScoreboard.Entry entry) {
      ScoreAccess score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entry.owner()), objective, true);
      boolean reversed = this.ascending.contains(entry.objective());
      score.set(reversed ? -entry.score() : entry.score());
      score.display(entry.display());
      score.numberFormatOverride(reversed ? new FixedFormat(Component.literal(Integer.toString(entry.score())).withStyle(ChatFormatting.RED)) : null);
   }

   private void remove(Scoreboard scoreboard, long scoreboardId) {
      BedrockScoreboard.Entry entry = this.entries.remove(scoreboardId);
      if (entry != null) {
         Objective objective = scoreboard.getObjective(entry.objective());
         if (objective != null) {
            scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly(entry.owner()), objective);
         }
      }
   }

   private static String fakeOwner(long scoreboardId) {
      return String.format(Locale.ROOT, "bn%020d", scoreboardId);
   }

   private record Entry(String objective, String owner, int score, @Nullable Component display, String text) {
   }
}
