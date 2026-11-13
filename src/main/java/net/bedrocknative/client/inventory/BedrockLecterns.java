package net.bedrocknative.client.inventory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.item.ItemMappings;
import net.bedrocknative.world.state.LecternPages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.LecternUpdatePacket;
import org.jspecify.annotations.Nullable;

public final class BedrockLecterns {
   private static final Map<Long, LecternPages.State> STATES = new ConcurrentHashMap<>();
   private static final Map<Long, Integer> SERVER_SPREADS = new ConcurrentHashMap<>();
   private static volatile @Nullable BedrockLecterns current;
   private static volatile int updatesSent;
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> send;
   private Consumer<BlockPos> punch = pos -> {};
   private @Nullable BlockPos openAt;
   private int containerId;
   private int javaPage;
   private int knownSpread;
   private int bookPages;

   public BedrockLecterns(Minecraft minecraft, Consumer<BedrockPacket> send) {
      this.minecraft = minecraft;
      this.send = send;
      current = this;
   }

   public void takeBookWith(Consumer<BlockPos> punch) {
      this.punch = punch;
   }

   public static void clear() {
      STATES.clear();
      SERVER_SPREADS.clear();
   }

   public void sessionEnded() {
      if (current == this) {
         current = null;
      }
   }

   public static int updatesSent() {
      return updatesSent;
   }

   public static boolean hasBook(BlockPos pos) {
      return STATES.getOrDefault(pos.asLong(), LecternPages.State.EMPTY).hasBook();
   }

   public static int serverSpread(BlockPos pos) {
      return SERVER_SPREADS.getOrDefault(pos.asLong(), -1);
   }

   public static BlockState adjust(BlockPos pos, BlockState state) {
      if (!state.is(Blocks.LECTERN)) {
         STATES.remove(pos.asLong());
         SERVER_SPREADS.remove(pos.asLong());
         return state;
      } else {
         return state.setValue(BlockStateProperties.HAS_BOOK, hasBook(pos));
      }
   }

   public static void load(ClientLevel level, BlockPos pos, NbtMap tag) {
      LecternPages.State state = record(pos, tag);
      BlockState block = level.getBlockState(pos);
      if (block.is(Blocks.LECTERN) && block.getValue(BlockStateProperties.HAS_BOOK) != state.hasBook()) {
         level.setServerVerifiedBlockState(pos, block.setValue(BlockStateProperties.HAS_BOOK, state.hasBook()), 19);
      }

      BedrockLecterns open = current;
      if (open != null) {
         open.changed(pos, state);
      }
   }

   public static LecternPages.State record(BlockPos pos, NbtMap tag) {
      LecternPages.State state = LecternPages.State.of(tag);
      STATES.put(pos.asLong(), state);
      SERVER_SPREADS.put(pos.asLong(), state.spread());
      return state;
   }

   public void open(ContainerOpenPacket packet) {
      Vector3i at = packet.getBlockPosition();
      BlockPos pos = new BlockPos(at.getX(), at.getY(), at.getZ());
      LecternPages.State state = STATES.getOrDefault(pos.asLong(), LecternPages.State.EMPTY);
      ClientPacketListener connection = this.minecraft.getConnection();
      if (state.hasBook() && connection != null && this.minecraft.player != null) {
         ItemStack book = ItemMappings.fromNbt(state.book());
         this.containerId = packet.getId() > 0 && packet.getId() <= 100 ? packet.getId() : 1;
         this.openAt = pos;
         this.bookPages = pageCount(book, state);
         this.knownSpread = state.spread();
         this.javaPage = LecternPages.clamp(LecternPages.firstPageOf(this.knownSpread), this.bookPages);
         connection.handleOpenScreen(new ClientboundOpenScreenPacket(this.containerId, MenuType.LECTERN, Component.translatable("container.lectern")));
         if (this.minecraft.player.containerMenu instanceof LecternMenu && this.minecraft.player.containerMenu.containerId == this.containerId) {
            connection.handleContainerSetSlot(new ClientboundContainerSetSlotPacket(this.containerId, 0, 0, book));
            connection.handleContainerSetData(new ClientboundContainerSetDataPacket(this.containerId, 0, this.javaPage));
            BedrockNative.LOGGER
               .debug("Lectern reader opened at {}: {} pages, page {} (spread {})", new Object[]{pos, this.bookPages, this.javaPage, this.knownSpread});
         } else {
            BedrockNative.LOGGER.warn("Lectern reader did not open for the container {} (menu {})", this.containerId, this.minecraft.player.containerMenu);
            this.openAt = null;
         }
      } else {
         BedrockNative.LOGGER.info("Lectern container opened at {} without a book to read (block entity data: {}); not shown", pos, state);
      }
   }

   private static int pageCount(ItemStack book, LecternPages.State state) {
      WrittenBookContent written = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
      if (written != null) {
         return written.pages().size();
      }

      WritableBookContent writable = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
      return writable != null ? writable.pages().size() : state.totalPages();
   }

   private void changed(BlockPos pos, LecternPages.State state) {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (this.openAt != null && this.openAt.equals(pos) && connection != null) {
         if (!state.hasBook()) {
            this.close();
         } else {
            ItemStack book = ItemMappings.fromNbt(state.book());
            this.bookPages = pageCount(book, state);
            connection.handleContainerSetSlot(new ClientboundContainerSetSlotPacket(this.containerId, 0, 0, book));
            if (state.spread() != this.knownSpread) {
               this.knownSpread = state.spread();
               this.javaPage = LecternPages.clamp(LecternPages.firstPageOf(this.knownSpread), this.bookPages);
               connection.handleContainerSetData(new ClientboundContainerSetDataPacket(this.containerId, 0, this.javaPage));
            }
         }
      }
   }

   public boolean isOpen(int menuId) {
      return this.openAt != null && menuId == this.containerId;
   }

   public void onButton(int button) {
      BlockPos pos = this.openAt;
      ClientPacketListener connection = this.minecraft.getConnection();
      if (pos != null && connection != null) {
         if (button == 3) {
            BedrockNative.LOGGER.debug("Lectern at {}: the book is taken by hitting the block", pos);
            this.punch.accept(pos);
            this.close();
         } else if (LecternPages.turnsPage(button)) {
            int page = LecternPages.target(button, this.javaPage, this.bookPages);
            if (page != this.javaPage) {
               this.javaPage = page;
               connection.handleContainerSetData(new ClientboundContainerSetDataPacket(this.containerId, 0, this.javaPage));
               int spread = LecternPages.spreadOf(page);
               if (spread != this.knownSpread) {
                  this.knownSpread = spread;
                  this.sendUpdate(pos, spread);
                  LecternPages.State state = STATES.get(pos.asLong());
                  if (state != null) {
                     STATES.put(pos.asLong(), new LecternPages.State(state.hasBook(), spread, state.totalPages(), state.book()));
                  }
               }
            }
         }
      }
   }

   public boolean onJavaClose(int menuId) {
      BlockPos pos = this.openAt;
      if (pos != null && menuId == this.containerId) {
         this.openAt = null;
         this.sendUpdate(pos, this.knownSpread);
         return true;
      } else {
         return false;
      }
   }

   public boolean onServerClose(int id) {
      if (this.openAt != null && (id == this.containerId || id == -1)) {
         this.close();
         return true;
      } else {
         return false;
      }
   }

   private void close() {
      this.openAt = null;
      LocalPlayer player = this.minecraft.player;
      if (player != null && player.containerMenu instanceof LecternMenu && player.containerMenu.containerId == this.containerId) {
         player.clientSideCloseContainer();
      }
   }

   public void tick() {
      LocalPlayer player = this.minecraft.player;
      if (this.openAt != null
         && (player == null || player.containerMenu.containerId != this.containerId || !(player.containerMenu instanceof LecternMenu))
         && !(this.minecraft.gui.screen() instanceof LecternScreen)) {
         this.openAt = null;
      }
   }

   private void sendUpdate(BlockPos pos, int spread) {
      LecternUpdatePacket update = new LecternUpdatePacket();
      update.setPage(spread);
      LecternPages.State known = STATES.get(pos.asLong());
      update.setTotalPages(known != null && known.totalPages() > 0 ? known.totalPages() : this.bookPages);
      update.setBlockPosition(Vector3i.from(pos.getX(), pos.getY(), pos.getZ()));
      update.setDroppingBook(false);
      updatesSent++;
      this.send.accept(update);
   }
}
