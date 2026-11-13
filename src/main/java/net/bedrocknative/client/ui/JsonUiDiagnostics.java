package net.bedrocknative.client.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.ui.UiScreenInstance;
import net.bedrocknative.ui.UiTextures;
import net.bedrocknative.ui.UiTreeDump;

public final class JsonUiDiagnostics {
   private static final int SETTLED_FRAME = 3;
   private static final int MAX_DUMPS = 16;
   private static final int DUMP_LINES = 300;
   private static final int MAX_LINES = 256;
   private static final long RECHECK_MILLIS = 2000L;
   private static final Set<String> LOGGED = new HashSet<>();
   private static final Set<String> DUMPED = new HashSet<>();
   private static int dumps;
   private final String what;
   private final String key;
   private final long settleMillis;
   private final long created = System.currentTimeMillis();
   private int frames;
   private boolean dumpDone;
   private long lastChanges = -1L;
   private long lastCheck;

   public JsonUiDiagnostics(String what, String key, long settleMillis) {
      this.what = what;
      this.key = key;
      this.settleMillis = settleMillis;
   }

   public static synchronized void reset() {
      LOGGED.clear();
      DUMPED.clear();
      dumps = 0;
   }

   public void frame(UiScreenInstance instance, Supplier<UiTextures> textures) {
      this.frames++;
      long now = System.currentTimeMillis();
      if (this.frames >= 3 && now - this.created >= this.settleMillis && instance.serverContributed()) {
         if (!this.dumpDone) {
            this.dumpDone = true;
            this.dump(instance, textures);
         }

         long changes = instance.gaps().changes();
         if (changes != this.lastChanges && (now - this.lastCheck >= 2000L || this.lastChanges < 0L)) {
            this.lastChanges = changes;
            this.lastCheck = now;
            String line = instance.gapsLine();
            if (line != null && remember(this.key + " " + line)) {
               BedrockNative.LOGGER.info("JSON UI {} engine gaps: {}", this.what, line);
            }

            String fired = instance.gaps().firedLine();
            if (fired != null && BedrockNative.LOGGER.isDebugEnabled() && remember(this.key + " " + fired)) {
               BedrockNative.LOGGER.debug("JSON UI {} server controls {}", this.what, fired);
            }

            String read = instance.gaps().readLine();
            if (read != null && BedrockNative.LOGGER.isDebugEnabled() && remember(this.key + " " + read)) {
               BedrockNative.LOGGER.debug("JSON UI {} server controls {}", this.what, read);
            }

            String base = instance.baseGameGapsLine();
            if (base != null && BedrockNative.LOGGER.isDebugEnabled() && remember(this.key + " " + base)) {
               BedrockNative.LOGGER.debug("JSON UI {} server controls: {}", this.what, base);
            }
         }
      }
   }

   private static synchronized boolean remember(String line) {
      return LOGGED.size() < 256 && LOGGED.add(line);
   }

   private void dump(UiScreenInstance instance, Supplier<UiTextures> textures) {
      if (BedrockNative.LOGGER.isDebugEnabled()) {
         synchronized (JsonUiDiagnostics.class) {
            if (dumps >= 16 || !DUMPED.add(this.key)) {
               return;
            }

            dumps++;
         }

         List<String> lines = UiTreeDump.lines(instance, textures.get(), 300);
         BedrockNative.LOGGER
            .debug(
               "JSON UI layout of {} ({} visible controls listed, {} controls, problems {}):",
               new Object[]{this.what, lines.size(), instance.controlCount(), instance.problems()}
            );
         lines.forEach(line -> BedrockNative.LOGGER.debug("JSON UI layout | {}", line));
      }
   }
}
