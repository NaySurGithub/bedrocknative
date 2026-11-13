package net.bedrocknative.form;

public record FormImage(FormImage.Type type, String data) {
   public static enum Type {
      PATH,
      URL;
   }
}
