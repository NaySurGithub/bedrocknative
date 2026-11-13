package net.bedrocknative.form;

public record FormImage(FormImage.Type type, String data) {
   public enum Type {
      PATH,
      URL;
   }
}
