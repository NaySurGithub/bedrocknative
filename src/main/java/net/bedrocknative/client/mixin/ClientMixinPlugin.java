package net.bedrocknative.client.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class ClientMixinPlugin implements IMixinConfigPlugin {
   static final String SODIUM_PROBE = "net/caffeinemc/mods/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask.class";
   static final List<String> SODIUM_MIXINS = List.of("render.SodiumMeshingTaskTimeMixin", "render.SodiumRenderSectionManagerTimeMixin");

   public void onLoad(String mixinPackage) {
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      return true;
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return sodiumPresent(ClientMixinPlugin.class.getClassLoader()) ? SODIUM_MIXINS : List.of();
   }

   static boolean sodiumPresent(ClassLoader loader) {
      return loader.getResource("net/caffeinemc/mods/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask.class") != null;
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
