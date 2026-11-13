package net.bedrocknative.net.auth.window;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

final class Browsers {
   private Browsers() {
   }

   static List<Path> candidates(Function<String, @Nullable String> environment) {
      List<Path> candidates = new ArrayList<>();
      add(candidates, environment.apply("ProgramFiles"), "Google\\Chrome\\Application\\chrome.exe");
      add(candidates, environment.apply("ProgramFiles(x86)"), "Google\\Chrome\\Application\\chrome.exe");
      add(candidates, environment.apply("LOCALAPPDATA"), "Google\\Chrome\\Application\\chrome.exe");
      add(candidates, environment.apply("ProgramFiles(x86)"), "Microsoft\\Edge\\Application\\msedge.exe");
      add(candidates, environment.apply("ProgramFiles"), "Microsoft\\Edge\\Application\\msedge.exe");
      return candidates;
   }

   static @Nullable Path find(Function<String, @Nullable String> environment, Predicate<Path> exists) {
      for (Path candidate : candidates(environment)) {
         if (exists.test(candidate)) {
            return candidate;
         }
      }

      return null;
   }

   static List<String> appWindow(Path browser, String url) {
      return List.of(browser.toString(), "--app=" + url, "--window-size=500,700");
   }

   private static void add(List<Path> candidates, @Nullable String folder, String relative) {
      if (folder != null && !folder.isBlank()) {
         try {
            candidates.add(Path.of(folder, relative));
         } catch (InvalidPathException var4) {
         }
      }
   }
}
