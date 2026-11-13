package net.bedrocknative.net.auth.window;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class WindowSignIns {
   private static final String RUNTIME_CLIENT = "Microsoft\\EdgeUpdate\\Clients\\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}";

   private WindowSignIns() {
   }

   public static List<RedirectCapture> system(Consumer<String> log) {
      boolean windows = windows();
      long processId = ProcessHandle.current().pid();
      Path temp = tempRoot();
      Registry registry = (Registry)(windows ? new WindowsRegistry() : WindowSignIns.NoRegistry.INSTANCE);
      BrowserCapture browser = new BrowserCapture(
         windows,
         new SchemeHandler(registry, processId, WindowSignIns::alive),
         WindowSignIns::handlerCommand,
         () -> Browsers.find(System::getenv, x$0 -> Files.isRegularFile(x$0)),
         ProcessStarter.SYSTEM,
         temp,
         processId,
         600000L,
         log
      );
      return List.of(helper(registry, false), browser);
   }

   public static HelperCapture devHelper() {
      return helper((Registry)(windows() ? new WindowsRegistry() : WindowSignIns.NoRegistry.INSTANCE), true);
   }

   private static HelperCapture helper(Registry registry, boolean allowLoopback) {
      boolean windows = windows();
      Path temp = tempRoot();
      return new HelperCapture(
         windows,
         () -> windows ? runtimeInstalled(registry) : Boolean.FALSE,
         HelperInstall.standard(System::getenv, temp),
         ProcessStarter.SYSTEM,
         temp,
         ProcessHandle.current().pid(),
         960000L,
         allowLoopback
      );
   }

   public static void cleanUpAtStart(Consumer<String> log) {
      long processId = ProcessHandle.current().pid();
      if (windows() && new SchemeHandler(new WindowsRegistry(), processId, WindowSignIns::alive).removeLeftover()) {
         log.accept("Xbox sign-in: removed the URI handler a closed game had left registered");
      }

      int folders = SignInFolder.removeLeftovers(tempRoot(), WindowSignIns::alive);
      if (folders > 0) {
         log.accept("Xbox sign-in: removed " + folders + " sign-in folder(s) a closed game had left");
      }
   }

   @Nullable
   static Boolean runtimeInstalled(Registry registry) {
      try {
         for (String key : List.of(
            "SOFTWARE\\WOW6432Node\\Microsoft\\EdgeUpdate\\Clients\\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}",
            "SOFTWARE\\Microsoft\\EdgeUpdate\\Clients\\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}"
         )) {
            if (version(registry.getMachine(key, "pv"))) {
               return true;
            }
         }

         return version(registry.get("Software\\Microsoft\\EdgeUpdate\\Clients\\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}", "pv"));
      } catch (RuntimeException | IOException var3) {
         return null;
      }
   }

   private static boolean version(@Nullable String version) {
      return version != null && !version.isBlank() && !version.strip().equals("0.0.0.0");
   }

   static String handlerCommand(Path folder) throws IOException {
      Path java = CallbackJar.java();
      if (java == null) {
         throw new IOException("the game's Java executable is unknown");
      } else {
         List<Path> roots = new ArrayList<>();
         String local = System.getenv("LOCALAPPDATA");
         if (local != null && !local.isBlank()) {
            roots.add(Path.of(local, "BedrockNative", "cb"));
         }

         roots.add(tempRoot().resolve("bncb"));
         String command = CallbackJar.command(java, CallbackJar.install(roots), folder);
         if (command == null) {
            throw new IOException("a path of the command holds a quote or a percent sign");
         } else {
            return command;
         }
      }
   }

   static boolean alive(long processId) {
      return ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false);
   }

   static boolean windows() {
      return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
   }

   static Path tempRoot() {
      return Path.of(System.getProperty("java.io.tmpdir"));
   }

   private static enum NoRegistry implements Registry {
      INSTANCE;

      @Override
      public boolean keyExists(String key) throws IOException {
         throw new IOException("no registry");
      }

      @Nullable
      @Override
      public String get(String key, String name) throws IOException {
         throw new IOException("no registry");
      }

      @Override
      public void set(String key, String name, String value) throws IOException {
         throw new IOException("no registry");
      }

      @Override
      public void deleteKey(String key) throws IOException {
         throw new IOException("no registry");
      }

      @Nullable
      @Override
      public String getMachine(String key, String name) throws IOException {
         throw new IOException("no registry");
      }
   }
}
