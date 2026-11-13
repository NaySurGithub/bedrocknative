package net.bedrocknative.client.inventory;

import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockBlockEditors {
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> send;
   private int windowId = -1;
   private @Nullable Class<? extends Screen> screenType;
   private int opened;

   public BedrockBlockEditors(Minecraft minecraft, Consumer<BedrockPacket> send) {
      this.minecraft = minecraft;
      this.send = send;
   }

   public static boolean handles(ContainerType type) {
      return type == ContainerType.JIGSAW_EDITOR || type == ContainerType.STRUCTURE_EDITOR;
   }

   public int openedCount() {
      return this.opened;
   }

   public void reset() {
      this.windowId = -1;
      this.screenType = null;
   }

   public void open(ContainerOpenPacket packet) {
      Vector3i at = packet.getBlockPosition();
      BlockPos pos = new BlockPos(at.getX(), at.getY(), at.getZ());
      LocalPlayer player = this.minecraft.player;
      BlockEntity entity = this.minecraft.level == null ? null : this.minecraft.level.getBlockEntity(pos);
      Class<? extends Screen> wanted = null;
      if (player != null && packet.getType() == ContainerType.JIGSAW_EDITOR && entity instanceof JigsawBlockEntity jigsaw) {
         wanted = JigsawBlockEditScreen.class;
         if (!wanted.isInstance(this.minecraft.gui.screen())) {
            player.openJigsawBlock(jigsaw);
         }
      } else if (player != null && packet.getType() == ContainerType.STRUCTURE_EDITOR && entity instanceof StructureBlockEntity structure) {
         wanted = StructureBlockEditScreen.class;
         if (!wanted.isInstance(this.minecraft.gui.screen())) {
            player.openStructureBlock(structure);
         }
      }

      if (wanted == null) {
         BedrockNative.LOGGER
            .info(
               "Container {} (id {}) opened at {} without a block entity to edit ({}); closing it", new Object[]{packet.getType(), packet.getId(), pos, entity}
            );
         this.sendClose(packet.getId());
      } else {
         this.opened++;
         this.windowId = packet.getId();
         this.screenType = wanted;
         BedrockNative.LOGGER.debug("Editor {} for the {} window {} at {}", new Object[]{wanted.getSimpleName(), packet.getType(), this.windowId, pos});
      }
   }

   public boolean onServerClose(int id) {
      if (this.windowId >= 0 && id == this.windowId) {
         Class<? extends Screen> type = this.screenType;
         this.windowId = -1;
         this.screenType = null;
         if (type != null && type.isInstance(this.minecraft.gui.screen())) {
            this.minecraft.gui.setScreen(null);
         }

         return true;
      } else {
         return false;
      }
   }

   public void tick() {
      if (this.windowId >= 0 && this.screenType != null && !this.screenType.isInstance(this.minecraft.gui.screen())) {
         this.sendClose(this.windowId);
         this.windowId = -1;
         this.screenType = null;
      }
   }

   private void sendClose(int id) {
      ContainerClosePacket close = new ContainerClosePacket();
      close.setId((byte)id);
      close.setType(ContainerType.NONE);
      close.setServerInitiated(false);
      this.send.accept(close);
   }
}
