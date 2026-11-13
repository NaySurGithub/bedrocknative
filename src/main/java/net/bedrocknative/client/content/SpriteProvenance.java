package net.bedrocknative.client.content;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.renderer.texture.atlas.SpriteSource.DiscardableLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource.Output;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;

public final class SpriteProvenance implements Output {
   private final Output output;
   private final Map<Identifier, Resource> files = new HashMap<>();

   public SpriteProvenance(Output output) {
      this.output = output;
   }

   @Override
   public void add(Identifier id, Resource resource) {
      this.files.put(id, resource);
      this.output.add(id, resource);
   }

   @Override
   public void add(Identifier id, DiscardableLoader sprite) {
      this.files.remove(id);
      this.output.add(id, sprite);
   }

   @Override
   public void removeAll(Predicate<Identifier> predicate) {
      this.files.keySet().removeIf(predicate);
      this.output.removeAll(predicate);
   }

   @Nullable Resource file(Identifier id) {
      return this.files.get(id);
   }
}
