package net.bedrocknative.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.bedrocknative.content.base.BaseTable;
import net.bedrocknative.content.base.DownloadedBase;
import net.bedrocknative.content.base.EmbeddedBase;
import net.bedrocknative.content.own.OwnUi;
import org.jspecify.annotations.Nullable;

public final class BaseContent {
   private static volatile BaseContent.@Nullable Snapshot snapshot;

   private BaseContent() {
   }

   public static BaseContent.Snapshot current() {
      BaseContent.Snapshot known = snapshot;
      if (known == null) {
         known = compute();
         snapshot = known;
      }

      return known;
   }

   private static BaseContent.Snapshot compute() {
      Optional<Path> install = BedrockInstall.vanillaResourcePack();
      if (install.isPresent()) {
         return new BaseContent.Snapshot(BaseContent.Kind.INSTALL, install.get(), hasUi(install.get()), null);
      }

      Optional<Path> folder = DownloadedBase.override();
      if (folder.isPresent()) {
         return new BaseContent.Snapshot(BaseContent.Kind.EMBEDDED, folder.get(), hasUi(folder.get()), null);
      }

      Optional<EmbeddedBase.Opened> embedded = EmbeddedBase.ready();
      return embedded.isPresent()
         ? new BaseContent.Snapshot(BaseContent.Kind.EMBEDDED, embedded.get().root(), embedded.get().pack().has("ui/_ui_defs.json"), embedded.get().pack())
         : new BaseContent.Snapshot(BaseContent.Kind.NONE, null, false, null);
   }

   static boolean hasUi(Path vanilla) {
      return new FolderPack(vanilla).has("ui/_ui_defs.json");
   }

   public static void refresh() {
      DownloadedBase.invalidate();
      snapshot = null;
   }

   public static BaseContent.Kind kind() {
      return current().kind();
   }

   public static List<Path> basePacks(@Nullable String baseGameVersion, Collection<String> experiments) {
      BaseContent.Snapshot base = current();

      return switch (base.kind()) {
         case INSTALL -> BedrockInstall.basePacks(baseGameVersion, experiments);
         case EMBEDDED -> {
            if (base.pack() == null) {
               yield List.of(base.vanilla());
            } else {
               Path extras = DownloadedBase.folder();
               yield extras == null ? List.of() : List.of(extras);
            }
         }
         case NONE -> List.of();
      };
   }

   public static Optional<PackSource> embeddedPack() {
      return Optional.ofNullable(current().pack());
   }

   public static Optional<Path> vanillaPack() {
      return Optional.ofNullable(current().vanilla());
   }

   public static Optional<Path> textsFolder() {
      return vanillaPack().map(vanilla -> vanilla.resolve("texts")).filter(x$0 -> Files.isDirectory(x$0));
   }

   public static boolean hasUi() {
      return current().hasUi() || ownUi();
   }

   public static BaseContent.UiSource uiSource() {
      BaseContent.Snapshot base = current();
      if (base.hasUi()) {
         return base.kind() == BaseContent.Kind.INSTALL ? BaseContent.UiSource.INSTALL : BaseContent.UiSource.EMBEDDED;
      } else {
         return ownUi() ? BaseContent.UiSource.OWN : BaseContent.UiSource.NONE;
      }
   }

   public static boolean ownUi() {
      return current().kind() == BaseContent.Kind.NONE && OwnUi.enabled();
   }

   public static String describe() {
      BaseContent.Snapshot base = current();

      return switch (base.kind()) {
         case INSTALL -> "install";
         case EMBEDDED -> {
            BaseTable table = BaseTable.bundled();
            yield "embedded " + (table != null ? table.folderName() : "base") + (base.pack() == null ? " folder" : "");
         }
         case NONE -> "none";
      };
   }

   public static String line() {
      BaseContent.Snapshot base = current();

      return switch (base.kind()) {
         case INSTALL -> "Base content: install (" + BedrockInstall.dataFolder() + ")";
         case EMBEDDED -> {
            BaseTable table = BaseTable.bundled();
            String pin = table != null ? table.pin() : "base";
            yield base.pack() == null
               ? "Base content: " + describe() + " (" + pin + " from the folder " + base.vanilla() + ", -Dbedrocknative.baseFolder)"
               : "Base content: " + describe() + " (" + pin + ", read from the mod jar; extras in " + DownloadedBase.folder() + ")";
         }
         case NONE -> {
            String problem = EmbeddedBase.problem();
            yield "Base content: none (no Bedrock install"
               + (BedrockInstall.enabled() ? "" : ": hidden by bedrocknative.bedrockInstall=off")
               + ", no embedded base"
               + (problem != null ? ": " + problem : "")
               + "): Java's look, server packs still apply"
               + (ownUi() ? "; menus from the mod's own set" : "");
         }
      };
   }

   public enum Kind {
      INSTALL,
      EMBEDDED,
      NONE;
   }

   public record Snapshot(BaseContent.Kind kind, @Nullable Path vanilla, boolean hasUi, @Nullable PackSource pack) {
   }

   public enum UiSource {
      INSTALL,
      EMBEDDED,
      OWN,
      NONE;
   }
}
