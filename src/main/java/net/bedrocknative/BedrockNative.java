package net.bedrocknative;

import java.util.function.Consumer;
import net.bedrocknative.content.base.EmbeddedBase;
import net.bedrocknative.perf.StartupClock;
import net.bedrocknative.world.block.custom.CustomBlocks;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.bedrocknative.world.item.BedrockItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BedrockNative implements ModInitializer {
   public static final String MOD_ID = "bedrocknative";
   public static final Logger LOGGER = FileTeeLogger.wrap(LoggerFactory.getLogger("bedrocknative"));
   @Nullable
   static volatile Consumer<String> logObserver;

   public static void observeLog(@Nullable Consumer<String> observer) {
      logObserver = observer;
   }

   public void onInitialize() {
      LOGGER.info("BedrockNative targeting Bedrock {} (network protocol {})", "1.26.63", 2225);
      if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
         StartupClock.step("embedded base", EmbeddedBase::prepare);
      }

      StartupClock.step("items", BedrockItems::register);
      StartupClock.step("custom blocks", CustomBlocks::register);
      StartupClock.step("actor entity", BedrockActorEntity::register);
      StartupClock.mainInitDone(StartupClock.sinceJvmStart());
   }
}
