package net.bedrocknative.net.client;

import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

final class HashedBlockDefinitions implements DefinitionRegistry<BlockDefinition> {
   static final HashedBlockDefinitions INSTANCE = new HashedBlockDefinitions();

   public BlockDefinition getDefinition(int runtimeId) {
      return new HashedBlockDefinitions.HashedBlock(runtimeId);
   }

   public boolean isRegistered(BlockDefinition definition) {
      return true;
   }

   private record HashedBlock(int getRuntimeId) implements BlockDefinition {
   }
}
