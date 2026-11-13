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
   private final Supplier<Path> appBrowser;
   private final ProcessStarter starter;
   private final Path tempRoot;
   private final long processId;
   private final long limitMillis;
   private final Consumer<String> log;

   public BrowserCapture(
      boolean windows,
      SchemeHandler handler,
      BrowserCapture.HandlerCommand command,
      Supplier<Path> appBrowser,
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

   @Nullable
   @Override
   public String unavailable() {
      return !this.windows ? "the browser way needs a URI handler (Windows only)" : this.handler.blocked();
   }

   @Override
   public RedirectCapture.Outcome capture(String url, RedirectCapture.Control control) throws InterruptedException {
      SignInFolder folder;
      try {
         folder = SignInFolder.create(this.tempRoot, this.processId);
         Files.createFile(folder.path().resolve("waiting"));
      } catch (IOException var22) {
         return RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.FAILED, "no private folder for the sign-in (" + var22.getClass().getSimpleName() + ")");
      }

      SignInFolder e = folder;

      RedirectCapture.Outcome deadline;
      label219: {
         label220: {
            RedirectCapture.Outcome var30;
            label221: {
               label222: {
                  RedirectCapture.Outcome var29;
                  label223: {
                     try {
                        SchemeHandler.Registration registration;
                        try {
                           String handlerCommand = this.command.forFolder(folder.path());
                           registration = this.handler.register(handlerCommand);
                        } catch (RuntimeException | IOException var23) {
                           deadline = RedirectCapture.Outcome.of(
                              RedirectCapture.Outcome.Kind.FAILED, "the URI handler could not be registered: " + var23.getMessage()
                           );
                           break label219;
                        }

                        SignInCleanup.add(registration);

                        try {
                           if (Thread.interrupted()) {
                              throw new InterruptedException();
                           }

                           RedirectCapture.Window window = this.open(url, control);
                           if (window == null) {
                              deadline = RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.FAILED, "no browser could be opened");
                              break label220;
                           }

                           control.opened(window);
                           long deadlinex = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(this.limitMillis);
                           int foreign = 0;

                           while (true) {
                              for (Path file : handedOver(folder.path())) {
                                 String redirect = take(file);
                                 if (redirect != null && control.ours(redirect)) {
                                    var30 = RedirectCapture.Outcome.redirect(redirect);
                                    break label221;
                                 }

                                 this.log.accept("Xbox sign-in: ignored an address that does not belong to this sign-in");
                                 if (++foreign >= 8) {
                                    var30 = RedirectCapture.Outcome.redirect(redirect);
                                    break label222;
                                 }
                              }

                              if (control.codeInstead()) {
                                 var29 = RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.CODE_INSTEAD, "the player chose a code instead");
                                 break label223;
                              }

                              if (System.nanoTime() - deadlinex > 0L) {
                                 var29 = RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.TIMED_OUT, "the browser page stayed open too long");
                                 break;
                              }

                              Thread.sleep(150L);
                           }
                        } finally {
                           registration.close();
                           SignInCleanup.remove(registration);
                        }
                     } catch (Throwable var25) {
                        if (folder != null) {
                           try {
                              e.close();
                           } catch (Throwable var21) {
                              var25.addSuppressed(var21);
                           }
                        }

                        throw var25;
                     }

                     if (folder != null) {
                        folder.close();
                     }

                     return var29;
                  }

                  if (folder != null) {
                     folder.close();
                  }

                  return var29;
               }

               if (folder != null) {
                  folder.close();
               }

               return var30;
            }

            if (folder != null) {
               folder.close();
            }

            return var30;
         }

         if (folder != null) {
            folder.close();
         }

         return deadline;
      }

      if (folder != null) {
         folder.close();
      }

      return deadline;
   }

   @Nullable
   private RedirectCapture.Window open(String url, RedirectCapture.Control control) {
      Path browser = this.appBrowser.get();
      if (browser != null) {
         try {
            Process process = this.starter.start(Browsers.appWindow(browser, url));
            process.getOutputStream().close();
            return RedirectCapture.Window.BROWSER_APP;
         } catch (RuntimeException | IOException var6) {
            this.log.accept("Xbox sign-in: " + browser.getFileName() + " did not start (" + var6.getClass().getSimpleName() + "); trying the default browser");
         }
      }

      try {
         return control.openInBrowser(url) ? RedirectCapture.Window.BROWSER : null;
      } catch (RuntimeException var5) {
         return null;
      }
   }

   private static List<Path> handedOver(Path folder) {
      List<Path> files = new ArrayList<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.url")) {
         stream.forEach(files::add);
      } catch (RuntimeException | IOException var7) {
      }

      files.sort(null);
      return files;
   }

   @Nullable
   private static String take(Path file) {
      Object e;
      try {
         if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
               byte[] bytes = in.readNBytes(8193);
               return bytes.length > 8192 ? null : new String(bytes, StandardCharsets.US_ASCII).strip();
            }
         }

         e = null;
      } catch (RuntimeException | IOException var18) {
         return null;
      } finally {
         try {
            Files.deleteIfExists(file);
         } catch (RuntimeException | IOException var15) {
         }
      }

      return (String)e;
   }

   @FunctionalInterface
   public interface HandlerCommand {
      String forFolder(Path var1) throws IOException;
   }
}
