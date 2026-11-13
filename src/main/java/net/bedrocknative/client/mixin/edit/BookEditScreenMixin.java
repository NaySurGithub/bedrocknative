package net.bedrocknative.client.mixin.edit;

import java.util.Collections;
import java.util.List;
import net.bedrocknative.client.edit.BedrockBooks;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.world.item.BookEdits;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BookEditScreen.class})
public abstract class BookEditScreenMixin extends Screen {
   private static final int BUTTON_WIDTH = 78;
   private static final int BOOK_WIDTH = 192;
   @Shadow
   @Final
   private List<String> pages;
   @Shadow
   private int currentPage;
   @Shadow
   private MultiLineEditBox page;

   protected BookEditScreenMixin(Component title) {
      super(title);
   }

   @Shadow
   protected abstract void updatePageContent();

   @Shadow
   protected abstract void updateButtonVisibility();

   @Shadow
   protected abstract void saveChanges();

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$begin(Player owner, ItemStack book, InteractionHand hand, WritableBookContent content, CallbackInfo ci) {
      if (BedrockMovement.active()) {
         BedrockBooks.begin(content.getPages(false).toList());
      }
   }

   @Inject(
      method = {"appendPageToBook"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$pageLimit(CallbackInfo ci) {
      if (BedrockMovement.active() && this.pages.size() >= 50) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$pageButtons(CallbackInfo ci) {
      if (BedrockMovement.active()) {
         this.page.setCharacterLimit(256);
         int left = Math.max(2, (this.width - 192) / 2 - 78 - 4);
         int right = Math.min(this.width - 78 - 2, (this.width + 192) / 2 + 4);
         int top = 30;
         this.addRenderableWidget(
            Button.builder(Component.translatable("bedrocknative.book.insert_page"), button -> this.bedrocknative$insert()).bounds(left, top, 78, 20).build()
         );
         this.addRenderableWidget(
            Button.builder(Component.translatable("bedrocknative.book.delete_page"), button -> this.bedrocknative$delete())
               .bounds(left, top + 24, 78, 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.translatable("bedrocknative.book.move_back"), button -> this.bedrocknative$move(-1)).bounds(right, top, 78, 20).build()
         );
         this.addRenderableWidget(
            Button.builder(Component.translatable("bedrocknative.book.move_forward"), button -> this.bedrocknative$move(1))
               .bounds(right, top + 24, 78, 20)
               .build()
         );
      }
   }

   public void onClose() {
      if (BedrockMovement.active()) {
         this.saveChanges();
      }

      super.onClose();
   }

   @Unique
   private void bedrocknative$insert() {
      if (this.pages.size() < 50) {
         BedrockBooks.record(new BookEdits.Op.Insert(this.currentPage, List.copyOf(this.pages)));
         this.pages.add(this.currentPage, "");
         this.updatePageContent();
         this.updateButtonVisibility();
      }
   }

   @Unique
   private void bedrocknative$delete() {
      BedrockBooks.record(new BookEdits.Op.Delete(this.currentPage, List.copyOf(this.pages)));
      this.pages.remove(this.currentPage);
      if (this.pages.isEmpty()) {
         this.pages.add("");
      }

      this.currentPage = Math.min(this.currentPage, this.pages.size() - 1);
      this.updatePageContent();
      this.updateButtonVisibility();
   }

   @Unique
   private void bedrocknative$move(int direction) {
      int other = this.currentPage + direction;
      if (other >= 0 && other < this.pages.size()) {
         BedrockBooks.record(new BookEdits.Op.Swap(Math.min(this.currentPage, other), Math.max(this.currentPage, other), List.copyOf(this.pages)));
         Collections.swap(this.pages, this.currentPage, other);
         this.currentPage = other;
         this.updatePageContent();
         this.updateButtonVisibility();
      }
   }
}
