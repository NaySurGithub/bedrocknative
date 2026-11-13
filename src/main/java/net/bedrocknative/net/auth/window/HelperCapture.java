package net.bedrocknative.net.auth.window;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class HelperCapture implements RedirectCapture {
   static final int HANDED_OVER = 0;
   static final int CLOSED_BY_PLAYER = 1;
   static final int WEBVIEW_FAILED = 4;
   static final int CLOSED_BY_GAME = 5;
   static final int TIMED_OUT = 6;
   static final long LIMIT_MS = 960000L;
   static final String OUT_FILE = "redirect.url";
   static final String USER_DATA = "webview";
   private final boolean windows;
   private final Supplier<Boolean> runtime;
   private final HelperInstall install;
   private final ProcessStarter starter;
   private final Path tempRoot;
   private final long processId;
   private final long limitMillis;
   private final boolean allowLoopback;

   public HelperCapture(
      boolean windows,
      Supplier<Boolean> runtime,
      HelperInstall install,
      ProcessStarter starter,
      Path tempRoot,
      long processId,
      long limitMillis,
      boolean allowLoopback
   ) {
      this.windows = windows;
      this.runtime = runtime;
      this.install = install;
      this.starter = starter;
      this.tempRoot = tempRoot;
      this.processId = processId;
      this.limitMillis = limitMillis;
      this.allowLoopback = allowLoopback;
   }

   @Override
   public String name() {
      return "the sign-in window (WebView2)";
   }

   @Nullable
   @Override
   public String unavailable() {
      if (!this.windows) {
         return "the sign-in window is Windows only";
      } else if (!this.install.built()) {
         return "this build has no sign-in window";
      } else {
         return Boolean.FALSE.equals(this.runtime.get()) ? "the WebView2 Runtime is not installed" : null;
      }
   }

   @Override
   public RedirectCapture.Outcome capture(String url, RedirectCapture.Control control) throws InterruptedException {
      HelperInstall.Result helper = this.install.install();
      if (helper.exe() == null) {
         return RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.FAILED, "the helper could not be installed: " + helper.problem());
      } else {
         SignInFolder folder;
         try {
            folder = SignInFolder.create(this.tempRoot, this.processId);
         } catch (IOException var22) {
            return RedirectCapture.Outcome.of(
               RedirectCapture.Outcome.Kind.FAILED, "no private folder for the sign-in (" + var22.getClass().getSimpleName() + ")"
            );
         }

         SignInFolder e = folder;

         RedirectCapture.Outcome ex;
         label191: {
            RedirectCapture.Outcome var28;
            label192: {
               label193: {
                  try {
                     Path out = folder.path().resolve("redirect.url");
                     List<String> command = new ArrayList<>(
                        List.of(
                           helper.exe().toString(),
                           url,
                           "ms-xal-0000000048183522:",
                           out.toString(),
                           folder.path().resolve("webview").toString(),
                           "--watch-stdin",
                           "--title",
                           plain(control.windowTitle())
                        )
                     );
                     String language = control.language();
                     if (language != null && language.matches("[A-Za-z0-9-]{2,35}")) {
                        command.add("--lang");
                        command.add(language);
                     }

                     if (this.allowLoopback) {
                        command.add("--allow-loopback");
                     }

                     Process process;
                     try {
                        process = this.starter.start(command);
                     } catch (RuntimeException | IOException var23) {
                        ex = RedirectCapture.Outcome.of(
                           RedirectCapture.Outcome.Kind.FAILED, "the sign-in window did not start (" + var23.getClass().getSimpleName() + ")"
                        );
                        break label191;
                     }

                     AutoCloseable closer = () -> close(process);
                     SignInCleanup.add(closer);

                     try {
                        control.opened(RedirectCapture.Window.HELPER);
                        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(this.limitMillis);

                        while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                           if (control.codeInstead()) {
                              close(process);
                              var28 = RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.CODE_INSTEAD, "the player chose a code instead");
                              break label192;
                           }

                           if (System.nanoTime() - deadline > 0L) {
                              close(process);
                              var28 = RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.TIMED_OUT, "the sign-in window stayed open too long");
                              break label193;
                           }
                        }
                        var28 = switch (process.exitValue()) {
                           case 0 -> Files.isRegularFile(out, LinkOption.NOFOLLOW_LINKS)
                              ? RedirectCapture.Outcome.redirect(read(out))
                              : RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.FAILED, "the sign-in window handed nothing over");
                           case 1, 5 -> RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.CLOSED, "the player closed the sign-in window");
                           default -> RedirectCapture.Outcome.of(
                              RedirectCapture.Outcome.Kind.FAILED, "the sign-in window ended with code " + process.exitValue()
                           );
                           case 4 -> RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.FAILED, "WebView2 is missing or failed in the sign-in window");
                           case 6 -> RedirectCapture.Outcome.of(RedirectCapture.Outcome.Kind.TIMED_OUT, "the sign-in window stayed open too long");
                        };
                     } catch (InterruptedException var24) {
                        close(process);
                        throw var24;
                     } finally {
                        SignInCleanup.remove(closer);
                     }
                  } catch (Throwable var26) {
                     if (folder != null) {
                        try {
                           e.close();
                        } catch (Throwable var21) {
                           var26.addSuppressed(var21);
                        }
                     }

                     throw var26;
                  }

                  if (folder != null) {
                     folder.close();
                  }

                  return var28;
               }

               if (folder != null) {
                  folder.close();
               }

               return var28;
            }

            if (folder != null) {
               folder.close();
            }

            return var28;
         }

         if (folder != null) {
            folder.close();
         }

         return ex;
      }
   }

   @Nullable
   private static String read(Path out) {
      try {
         String var3;
         try (InputStream in = Files.newInputStream(out, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(8193);
            var3 = bytes.length > 8192 ? null : new String(bytes, StandardCharsets.UTF_8).strip();
         }

         return var3;
      } catch (RuntimeException | IOException var6) {
         return null;
      }
   }

   static void close(Process process) {
      boolean interrupted = Thread.interrupted();

      try {
         process.getOutputStream().close();
      } catch (RuntimeException | IOException var10) {
      }

      try {
         if (!interrupted && process.waitFor(3L, TimeUnit.SECONDS)) {
            return;
         }
      } catch (InterruptedException var9) {
         interrupted = true;
      }

      process.destroyForcibly();

      try {
         if (!interrupted) {
            process.waitFor(2L, TimeUnit.SECONDS);
         }
      } catch (InterruptedException var8) {
         interrupted = true;
      } finally {
         if (interrupted) {
            Thread.currentThread().interrupt();
         }
      }
   }

   static String plain(String title) {
      String plain = title.replaceAll("[\\p{Cntrl}\"]", "").strip();
      return plain.isEmpty() ? "Sign In" : (plain.length() > 100 ? plain.substring(0, 100) : plain);
   }
}
