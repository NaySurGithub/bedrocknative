package net.bedrocknative;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;

final class FileTeeLogger extends LegacyAbstractLogger {
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
   private final Logger delegate;
   private final LogFileWriter file;

   private FileTeeLogger(Logger delegate) {
      this.delegate = delegate;
      this.name = delegate.getName();
      this.file = new LogFileWriter(
         () -> FabricLoader.getInstance().getConfigDir().resolve("bedrocknative").resolve("bedrocknative.log"),
         failure -> delegate.warn("Could not write the mod's own log file ({}); the game log keeps the lines", failure)
      );
   }

   static Logger wrap(Logger delegate) {
      return new FileTeeLogger(delegate);
   }

   public boolean isTraceEnabled() {
      return this.delegate.isTraceEnabled();
   }

   public boolean isDebugEnabled() {
      return this.delegate.isDebugEnabled();
   }

   public boolean isInfoEnabled() {
      return true;
   }

   public boolean isWarnEnabled() {
      return true;
   }

   public boolean isErrorEnabled() {
      return true;
   }

   protected String getFullyQualifiedCallerName() {
      return null;
   }

   protected void handleNormalizedLoggingCall(
      Level level, @Nullable Marker marker, String pattern, Object @Nullable [] arguments, @Nullable Throwable throwable
   ) {
      String message = MessageFormatter.basicArrayFormat(pattern, arguments);
      Consumer<String> observer = BedrockNative.logObserver;
      if (observer != null) {
         observer.accept(message + (throwable != null ? " " + throwable : ""));
      }

      switch (level) {
         case TRACE:
            this.delegate.trace(message, throwable);
            break;
         case DEBUG:
            this.delegate.debug(message, throwable);
            break;
         case INFO:
            this.delegate.info(message, throwable);
            break;
         case WARN:
            this.delegate.warn(message, throwable);
            break;
         case ERROR:
            this.delegate.error(message, throwable);
      }

      this.write(level, message, throwable);
   }

   private void write(Level level, String message, @Nullable Throwable throwable) {
      String trace = null;
      if (throwable != null) {
         StringWriter text = new StringWriter();
         throwable.printStackTrace(new PrintWriter(text));
         trace = text.toString();
      }

      this.file.add(LogFileWriter.entry("[" + LocalTime.now().format(TIME) + "] [" + Thread.currentThread().getName() + "/" + level + "] " + message, trace));
   }
}
