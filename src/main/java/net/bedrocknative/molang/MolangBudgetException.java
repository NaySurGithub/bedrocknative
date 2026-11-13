package net.bedrocknative.molang;

final class MolangBudgetException extends RuntimeException {
   MolangBudgetException() {
      super("Molang loop budget exceeded", null, false, false);
   }
}
