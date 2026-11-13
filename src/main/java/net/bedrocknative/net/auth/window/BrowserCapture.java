package net.bedrocknative.net.auth.window;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class BrowserCapture implements RedirectCapture {
   static final long LIMIT_MS = 600000L;
   static final int MAX_FOREIGN = 8;
   private final boolean windows;
   private final SchemeHandler handler;
   private final BrowserCapture.HandlerCommand command;
   private final Supplier<@Nullable Path> appBrowser;
   private final ProcessStarter starter;
   private final Path tempRoot;
   private final long processId;
   private final long limitMillis;
   private final Consumer<String> log;

   public BrowserCapture(
      boolean windows,
      SchemeHandler handler,
      BrowserCapture.HandlerCommand command,
      Supplier<@Nullable Path> appBrowser,
      ProcessStarter starter,
      Path tempRoot,
      long processId,
      long limitMillis,
      Consumer<String> log
   ) {
      this.windows = windows;
      this.handler = handler;
      this.command = command;
      this.appBrowser = appBrowser;
      this.starter = starter;
      this.tempRoot = tempRoot;
      this.processId = processId;
      this.limitMillis = limitMillis;
      this.log = log;
   }

   @Override
   public String name() {
      return "the browser";
   }

   @Override
   public @Nullable String unavailable() {
      return !this.windows ? "the browser way needs a URI handler (Windows only)" : this.handler.blocked();
   }

   @Override
   public RedirectCapture.Outcome capture(String url, RedirectCapture.Control control) throws InterruptedException {
      SignInFolder folder;
      try {
         folder = SignInFolder.create(this.tempRoot, this.processId);
         Files.createFile(folder.path().resolve("waiting"));
      } catch (IOException e) {
         return RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.FAILED, "no private folder for the sign-in (" + e.getClass().getSimpleName() + ")");
      }

      try (folder) {
         SchemeHandler.Registration registration;
         try {
            String handlerCommand = this.command.forFolder(folder.path());
            registration = this.handler.register(handlerCommand);
         } catch (IOException | RuntimeException e) {
            return RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.FAILED, "the URI handler could not be registered: " + e.getMessage());
         }

         SignInCleanup.add(registration);

         try {
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }

            RedirectCapture.Window window = this.open(url, control);
            if (window == null) {
               return RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.FAILED, "no browser could be opened");
            }

            control.opened(window);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(this.limitMillis);
            int foreign = 0;

            while (true) {
               for (Path file : handedOver(folder.path())) {
                  String redirect = take(file);
                  if (redirect != null && control.ours(redirect)) {
                     return RedirectCapture.Outcome.redirect(redirect);
                  }

                  this.log.accept("Xbox sign-in: ignored an address that does not belong to this sign-in");
                  if (++foreign >= 8) {
                     return RedirectCapture.Outcome.redirect(redirect);
                  }
               }

               if (control.codeInstead()) {
                  return RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.CODE_INSTEAD, "the player chose a code instead");
               }

               if (System.nanoTime() - deadline > 0L) {
                  return RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.TIMED_OUT, "the browser page stayed open too long");
               }

               Thread.sleep(150L);
            }
         } finally {
            registration.close();
            SignInCleanup.remove(registration);
         }
      }
   }

   private RedirectCapture.@Nullable Window open(String url, RedirectCapture.Control control) {
      Path browser = this.appBrowser.get();
      if (browser != null) {
         try {
            Process process = this.starter.start(Browsers.appWindow(browser, url));
            process.getOutputStream().close();
            return RedirectCapture.Window.BROWSER_APP;
         } catch (IOException | RuntimeException e) {
            this.log.accept("Xbox sign-in: " + browser.getFileName() + " did not start (" + e.getClass().getSimpleName() + "); trying the default browser");
         }
      }

      try {
         return control.openInBrowser(url) ? RedirectCapture.Window.BROWSER : null;
      } catch (RuntimeException e) {
         return null;
      }
   }

   private static List<Path> handedOver(Path folder) {
      List<Path> files = new ArrayList<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.url")) {
         stream.forEach(files::add);
      } catch (IOException | RuntimeException var7) {
      }

      files.sort(null);
      return files;
   }

   private static @Nullable String take(Path file) {
      try {
         if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return null;
         }

         try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(8193);
            return bytes.length > 8192 ? null : new String(bytes, StandardCharsets.US_ASCII).strip();
         }
      } catch (IOException | RuntimeException e) {
         return null;
      } finally {
         try {
            Files.deleteIfExists(file);
         } catch (IOException | RuntimeException var15) {
         }
      }
   }

   @FunctionalInterface
   public interface HandlerCommand {
      String forFolder(Path var1) throws IOException;
   }
}
