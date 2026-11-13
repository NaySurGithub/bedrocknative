package net.bedrocknative.molang;

import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.BedrockNative;

public final class MolangDiagnostics {
   private final String source;
   private final List<String> errors = new ArrayList<>();
   private int runtimeErrors;
   private boolean logged;
   private boolean aborted;
   private int exceptions;

   MolangDiagnostics(String source) {
      this.source = source;
   }

   void error(String message, int position) {
      this.errors.add(message + " (at " + position + ")");
      this.log(message);
   }

   void aborted(String message, int position) {
      this.aborted = true;
      this.error(message, position);
   }

   public boolean aborted() {
      return this.aborted;
   }

   void absorb(MolangDiagnostics entry) {
      this.errors.addAll(entry.errors);
   }

   void runtime(String what) {
      this.runtimeErrors++;
      this.log(what);
   }

   void exception(Throwable error) {
      this.exceptions++;
      this.runtime(error.toString());
   }

   public int exceptions() {
      return this.exceptions;
   }

   private void log(String what) {
      if (!this.logged) {
         this.logged = true;
         BedrockNative.LOGGER.debug("Molang '{}': {}", this.source, what);
      }
   }

   public List<String> errors() {
      return List.copyOf(this.errors);
   }

   public boolean hasErrors() {
      return !this.errors.isEmpty();
   }

   public int runtimeErrors() {
      return this.runtimeErrors;
   }
}
