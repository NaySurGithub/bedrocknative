package net.bedrocknative.molang;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class MolangCache {
   private final ConcurrentHashMap<MolangCache.Key, MolangProgram> programs = new ConcurrentHashMap<>();

   public MolangProgram compile(String source, MolangVersion version) {
      return this.compile(source, version, false);
   }

   public MolangProgram compile(String source, MolangVersion version, boolean lowerCaseStrings) {
      return this.programs
         .computeIfAbsent(
            new MolangCache.Key(source, version, lowerCaseStrings), key -> MolangCompiler.compile(key.source(), key.version(), key.lowerCaseStrings())
         );
   }

   public MolangProgram script(List<String> entries, MolangVersion version, boolean lowerCaseStrings) {
      String joined = String.join("\n", entries);
      MolangCache.Key key = new MolangCache.Key(joined, version, lowerCaseStrings);
      MolangProgram cached = this.programs.get(key);
      if (cached != null) {
         return cached;
      } else {
         MolangProgram program = MolangCompiler.compile(joined, version, lowerCaseStrings);
         if (entries.size() > 1 && program.diagnostics().aborted()) {
            program = MolangProgram.sequence(joined, entries.stream().map(entry -> this.compile(entry, version, lowerCaseStrings)).toList());
         }

         MolangProgram previous = this.programs.putIfAbsent(key, program);
         return previous != null ? previous : program;
      }
   }

   public int size() {
      return this.programs.size();
   }

   public Collection<MolangProgram> programs() {
      return Collections.unmodifiableCollection(this.programs.values());
   }

   public int errors() {
      return (int)this.programs.values().stream().filter(program -> program.diagnostics().hasErrors()).count();
   }

   private record Key(String source, MolangVersion version, boolean lowerCaseStrings) {
   }
}
