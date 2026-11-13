package net.bedrocknative.molang;

final class MolangException extends RuntimeException {
   final int position;

   MolangException(String message, int position) {
      super(message, null, false, false);
      this.position = position;
   }
}
