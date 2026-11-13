package net.bedrocknative.client.net;

import java.util.function.Consumer;
import net.bedrocknative.client.inventory.BedrockCrafters;
import net.bedrocknative.client.inventory.BedrockInventory;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.model.BedrockEmotes;
import net.bedrocknative.client.sound.BedrockMusic;
import net.bedrocknative.client.world.BedrockJukeboxes;
import net.bedrocknative.client.world.BedrockSpinAttack;
import net.bedrocknative.client.world.BedrockWorldEvents;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.world.state.DataDrivenUi;
import net.bedrocknative.world.state.ServerKeeps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.RecordStartedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ToggleCrafterSlotRequestPacket;
import org.jspecify.annotations.Nullable;

final class BedrockCompletion {
   private static volatile @Nullable BedrockCompletion active;
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> send;
   private final BedrockJukeboxes jukeboxes;
   private final BedrockMusic music;
   private final BedrockWorldEvents worldEvents;
   private final BedrockSpinAttack spinAttack = new BedrockSpinAttack();
   private volatile long runtimeId;
   private final ServerKeeps keeps = new ServerKeeps();
   private final BedrockActors actors;
   private final BedrockInventory inventory;
   private final DataDrivenUi dataDrivenUi;

   BedrockCompletion(Minecraft minecraft, Consumer<BedrockPacket> send, ClientIdentity identity, BedrockActors actors, BedrockInventory inventory) {
      this.actors = actors;
      this.inventory = inventory;
      this.minecraft = minecraft;
      this.send = send;
      this.jukeboxes = new BedrockJukeboxes(minecraft);
      this.music = new BedrockMusic(minecraft);
      this.worldEvents = new BedrockWorldEvents(minecraft);
      this.dataDrivenUi = new DataDrivenUi(send);
   }

   static @Nullable BedrockCompletion active() {
      return active;
   }

   static boolean handles(BedrockPacket packet) {
      return packet instanceof RecordStartedPacket || ServerKeeps.handles(packet) || BedrockWorldEvents.handles(packet) || DataDrivenUi.handles(packet);
   }

   void close() {
      if (active == this) {
         active = null;
      }
   }

   void start(long localRuntimeId) {
      active = this;
      this.runtimeId = localRuntimeId;
      this.spinAttack.reset();
      BedrockEmotes.start(new BedrockEmotes.Session(localRuntimeId, this.send, this.actors));
      BedrockCrafters.reset();
      this.jukeboxes.clear();
      this.music.clear();
      this.worldEvents.reset();
      this.keeps.clear();
      this.dataDrivenUi.clear();
   }

   void apply(BedrockPacket packet) {
      switch (packet) {
         case RecordStartedPacket record:
            this.jukeboxes.onRecordStarted(record);
            break;
         case LevelEventPacket event:
            this.worldEvents.apply(event);
            break;
         default:
            if (ServerKeeps.handles(packet)) {
               this.keeps.apply(packet);
            } else if (DataDrivenUi.handles(packet)) {
               this.dataDrivenUi.apply(packet);
            }
      }
   }

   void tickPlayer(LocalPlayer player) {
      this.spinAttack.tick(player, this.runtimeId, this.send);
   }

   void tick() {
      this.jukeboxes.tick();
      this.music.tick();
      this.inventory.tickCrafter();
   }

   boolean onGeneric(LevelEventGenericPacket packet) {
      if (BedrockMusic.handles(packet)) {
         this.music.onGeneric(packet);
         return true;
      } else {
         return false;
      }
   }

   void changeDimension() {
      this.worldEvents.reset();
   }

   BedrockMusic music() {
      return this.music;
   }

   BedrockWorldEvents worldEvents() {
      return this.worldEvents;
   }

   DataDrivenUi dataDrivenUi() {
      return this.dataDrivenUi;
   }

   ServerKeeps keeps() {
      return this.keeps;
   }

   BedrockJukeboxes jukeboxes() {
      return this.jukeboxes;
   }

   boolean onJavaPacket(Packet<?> packet) {
      if (packet instanceof ServerboundContainerSlotStateChangedPacket change) {
         ToggleCrafterSlotRequestPacket toggle = this.inventory.onSlotState(change.slotId(), change.containerId(), change.newState());
         if (toggle != null) {
            this.send.accept(toggle);
         }

         return true;
      } else {
         return false;
      }
   }
}
