package net.bedrocknative.client.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.rules.BedrockGameRules;
import net.bedrocknative.client.rules.BedrockNameTags;
import net.bedrocknative.client.ui.JsonUiHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import org.cloudburstmc.protocol.bedrock.data.GameRuleData;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.clock.InitializeRegistryData;
import org.cloudburstmc.protocol.bedrock.data.clock.SyncStateData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTimePacket;
import org.cloudburstmc.protocol.bedrock.packet.SyncWorldClocksPacket;

public final class BedrockTimeWeather {
   private static final float WEATHER_STEP = 0.01F;
   private static final float FULL_INTENSITY = 65535.0F;
   private final Minecraft minecraft;
   private final Long2ObjectMap<String> clockNames = new Long2ObjectOpenHashMap();
   private final Set<String> unknownClocks = new HashSet<>();
   private boolean daylightCycle = true;
   private float rain;
   private float thunder;
   private float shownRain;
   private float shownThunder;

   public BedrockTimeWeather(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof SetTimePacket
         || packet instanceof SyncWorldClocksPacket
         || packet instanceof GameRulesChangedPacket
         || packet instanceof LevelEventPacket event && isWeather(event);
   }

   public void start(List<GameRuleData<?>> gameRules, float rainLevel, float lightningLevel) {
      this.daylightCycle = true;
      this.applyGameRules(gameRules);
      this.rain = this.shownRain = rainLevel;
      this.thunder = this.shownThunder = lightningLevel;
      this.tick();
   }

   public void apply(BedrockPacket packet) {
      if (this.minecraft.level != null) {
         switch (packet) {
            case SetTimePacket time:
               this.setDayTime(time.getTime());
               break;
            case SyncWorldClocksPacket clocks:
               this.syncClocks(clocks);
               break;
            case GameRulesChangedPacket rules:
               this.applyGameRules(rules.getGameRules());
               break;
            case LevelEventPacket event:
               float intensity = event.getData() > 0 ? Math.min(1.0F, event.getData() / 65535.0F) : 1.0F;
               if (event.getType() == LevelEvent.START_RAINING) {
                  this.rain = intensity;
               } else if (event.getType() == LevelEvent.STOP_RAINING) {
                  this.rain = 0.0F;
               } else if (event.getType() == LevelEvent.START_THUNDERSTORM) {
                  this.thunder = intensity;
               } else if (event.getType() == LevelEvent.STOP_THUNDERSTORM) {
                  this.thunder = 0.0F;
               }
               break;
            default:
         }
      }
   }

   public void tick() {
      ClientLevel level = this.minecraft.level;
      if (level != null) {
         this.shownRain = approach(this.shownRain, this.rain);
         this.shownThunder = approach(this.shownThunder, this.thunder);
         level.setRainLevel(this.shownRain);
         level.setThunderLevel(this.shownThunder);
      }
   }

   private void setDayTime(long dayTime) {
      ClientLevel level = this.minecraft.level;
      Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
      if (clock.isPresent() && this.minecraft.getConnection() != null) {
         this.minecraft
            .getConnection()
            .handleSetTime(
               new ClientboundSetTimePacket(level.getGameTime(), Map.of(clock.get(), new ClockNetworkState(dayTime, 0.0F, this.daylightCycle ? 1.0F : 0.0F)))
            );
      }
   }

   private void syncClocks(SyncWorldClocksPacket packet) {
      ClientLevel level = this.minecraft.level;
      Map<Holder<WorldClock>, ClockNetworkState> updates = new HashMap<>();
      if (packet.getData() instanceof InitializeRegistryData registry) {
         this.clockNames.clear();
         registry.getClockData().forEach(clock -> {
            this.clockNames.put(clock.getId(), clock.getName());
            this.javaClock(level, clock.getName()).ifPresent(java -> updates.put((Holder<WorldClock>)java, this.clockState(clock.getTime(), clock.isPaused())));
         });
      } else if (packet.getData() instanceof SyncStateData state) {
         state.getClockData()
            .forEach(
               clock -> this.javaClock(level, (String)this.clockNames.get(clock.getClockId()))
                  .ifPresent(java -> updates.put((Holder<WorldClock>)java, this.clockState(clock.getTime(), clock.isPaused())))
            );
      }

      if (!updates.isEmpty() && this.minecraft.getConnection() != null) {
         this.minecraft.getConnection().handleSetTime(new ClientboundSetTimePacket(level.getGameTime(), updates));
      }
   }

   private ClockNetworkState clockState(long time, boolean paused) {
      return new ClockNetworkState(time, 0.0F, !paused && this.daylightCycle ? 1.0F : 0.0F);
   }

   private Optional<Holder<WorldClock>> javaClock(ClientLevel level, String bedrockName) {
      if (bedrockName == null) {
         return Optional.empty();
      } else {
         String path = bedrockName.toLowerCase(Locale.ROOT);
         path = path.substring(path.indexOf(58) + 1);
         Identifier id = Identifier.tryParse(path);
         Optional<Holder<WorldClock>> clock = id == null
            ? Optional.empty()
            : level.registryAccess()
               .lookupOrThrow(Registries.WORLD_CLOCK)
               .get(ResourceKey.create(Registries.WORLD_CLOCK, id))
               .map(holder -> (Holder<WorldClock>)holder);
         if (clock.isEmpty() && this.unknownClocks.add(bedrockName)) {
            BedrockNative.LOGGER.warn("Bedrock world clock {} has no Java counterpart", bedrockName);
         }

         return clock;
      }
   }

   private void applyGameRules(List<GameRuleData<?>> gameRules) {
      for (GameRuleData<?> rule : gameRules) {
         BedrockNameTags.onGameRule(rule.getName(), rule.getValue());
         BedrockGameRules.onGameRule(rule.getName(), rule.getValue());
         JsonUiHud.onGameRule(rule.getName(), rule.getValue());
         if (rule.getName().equalsIgnoreCase("dodaylightcycle") && rule.getValue() instanceof Boolean cycle && cycle != this.daylightCycle) {
            this.daylightCycle = cycle;
            ClientLevel level = this.minecraft.level;
            if (level != null) {
               level.dimensionType().defaultClock().ifPresent(clock -> this.setDayTime(level.clockManager().getInstance(clock).totalTicks()));
            }
         }
      }
   }

   private static boolean isWeather(LevelEventPacket event) {
      return event.getType() == LevelEvent.START_RAINING
         || event.getType() == LevelEvent.STOP_RAINING
         || event.getType() == LevelEvent.START_THUNDERSTORM
         || event.getType() == LevelEvent.STOP_THUNDERSTORM;
   }

   private static float approach(float current, float target) {
      return current < target ? Math.min(target, current + 0.01F) : Math.max(target, current - 0.01F);
   }
}
