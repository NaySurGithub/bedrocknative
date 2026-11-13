package net.bedrocknative.client.net;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.oreui.OreUi;
import net.bedrocknative.net.BedrockCrypto;
import net.bedrocknative.perf.ClassPreload;
import net.bedrocknative.perf.StartupClock;
import net.bedrocknative.world.block.BlockMappings;
import net.minecraft.client.Minecraft;

public final class JoinWarmUp {
   static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.joinWarmUp", "on").trim());
   static final String LIST = "/bedrocknative/join-classes.txt";
   private static final long AFTER_TITLE_MS = 2000L;
   private static final AtomicBoolean STARTED = new AtomicBoolean();
   private static final AtomicBoolean BEDROCK_STARTED = new AtomicBoolean();
   private static volatile boolean done;

   private JoinWarmUp() {
   }

   public static void tick(Minecraft minecraft) {
      if (ENABLED && !STARTED.get()) {
         long title = StartupClock.titleMillis();
         if (title >= 0L && StartupClock.sinceJvmStart() - title >= 2000L) {
            start("title screen");
         }
      }
   }

   public static void start(String why) {
      if (ENABLED && STARTED.compareAndSet(false, true)) {
         Thread thread = new Thread(() -> run(why), "Bedrock join warm-up");
         thread.setDaemon(true);
         thread.setPriority(1);
         thread.start();
      }
   }

   public static void startBedrock(String why) {
      BedrockUse.markUsed();
      if (ENABLED && !BEDROCK_STARTED.get()) {
         Thread thread = new Thread(() -> bedrockCosts(why, -1L), "Bedrock join warm-up (connect)");
         thread.setDaemon(true);
         thread.setPriority(1);
         thread.start();
      }
   }

   public static boolean done() {
      return done;
   }

   private static String loginCrypto() {
      long started = System.nanoTime();

      try {
         KeyPair pair = BedrockCrypto.generateSessionKeyPair();
         BedrockCrypto.verifySelfSignedJwt(BedrockCrypto.signJwt(pair, "{}"));
         BedrockCrypto.deriveKey(pair.getPrivate(), pair.getPublic(), BedrockCrypto.randomBytes(16));
         return "login cryptography ready in " + (System.nanoTime() - started) / 1000000L + " ms";
      } catch (GeneralSecurityException | RuntimeException e) {
         return "login cryptography left to the join (" + e.getClass().getSimpleName() + ")";
      }
   }

   private static String gameProtocol(String why) {
      Minecraft minecraft = Minecraft.getInstance();
      if (why.equals("title screen") && minecraft != null && minecraft.getConnection() == null && minecraft.level == null) {
         long started = System.nanoTime();

         try {
            Class.forName("net.minecraft.network.protocol.game.GameProtocols", true, JoinWarmUp.class.getClassLoader());
            return "game protocol initialized in " + (System.nanoTime() - started) / 1000000L + " ms";
         } catch (ClassNotFoundException e) {
            return "no game protocol class in this game";
         }
      } else {
         return "game protocol left to the join";
      }
   }

   private static void run(String why) {
      try {
         long textsStarted = System.nanoTime();
         OreUi.prewarmTexts();
         long textsMillis = (System.nanoTime() - textsStarted) / 1000000L;
         BedrockUse.detect();
         if (!BedrockUse.known()) {
            BedrockNative.LOGGER
               .info("Join warm-up ({}): Ore UI texts ready in {} ms; no Bedrock server joined yet, the join's costs wait for the first one", why, textsMillis);
            return;
         }

         bedrockCosts(why, textsMillis);
      } catch (Throwable e) {
         BedrockNative.LOGGER.warn("Join warm-up ({}) stopped: {}", why, e.toString());
      }
   }

   private static void bedrockCosts(String why, long textsMillis) {
      if (BEDROCK_STARTED.compareAndSet(false, true)) {
         try {
            ClassPreload.Result classes = null;

            try (InputStream raw = JoinWarmUp.class.getResourceAsStream("/bedrocknative/join-classes.txt")) {
               if (raw == null) {
                  BedrockNative.LOGGER.warn("Join warm-up: {} is not in the jar; the first join loads its classes itself", "/bedrocknative/join-classes.txt");
               } else {
                  classes = ClassPreload.preload(
                     JoinWarmUp.class.getClassLoader(), new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8)), () -> false
                  );
               }
            }

            long blocksStarted = System.nanoTime();
            BlockMappings.get();
            long blocksMillis = (System.nanoTime() - blocksStarted) / 1000000L;
            String protocol = gameProtocol(why);
            String crypto = loginCrypto();
            done = true;
            BedrockNative.LOGGER
               .info(
                  "Join warm-up ({}): {}{}; block mappings ready in {} ms; {}; {}",
                  new Object[]{
                     why,
                     textsMillis < 0L ? "" : "Ore UI texts ready in " + textsMillis + " ms; ",
                     classes == null
                        ? "no class list"
                        : classes.loaded()
                           + " classes loaded ahead in "
                           + classes.nanos() / 1000000L
                           + " ms ("
                           + classes.missing()
                           + " not in this game, "
                           + classes.failed()
                           + " failed)",
                     blocksMillis,
                     protocol,
                     crypto
                  }
               );
         } catch (Exception | LinkageError e) {
            BedrockNative.LOGGER.warn("Join warm-up ({}) stopped: {}", why, e.toString());
         }
      }
   }
}
