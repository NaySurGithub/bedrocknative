package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.bedrocknative.ui.TradeData;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiItemPath;
import net.bedrocknative.ui.UiType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.cloudburstmc.nbt.NbtMap;
import org.jspecify.annotations.Nullable;

public final class JsonUiTrading implements TradeData.Actions {
   public static final int MAX_OFFERS = 24;
   private static final int FIRST_PAYMENT = 0;
   private static final int SECOND_PAYMENT = 1;
   private static final int RESULT_SLOT = 2;
   private final Minecraft minecraft;
   private final JsonUiContainerScreen screen;
   private final MerchantMenu menu;
   private final TradeData data;
   private final Workstations trades;
   private Workstations.TradeView shown;
   private ItemStack[][] items = new ItemStack[0][];
   private boolean[] affordable = new boolean[0];
   private final ItemStack[] seen;
   private final int[] seenCounts;
   private @Nullable MerchantOffers seenOffers;
   private int seenSelected = -2;

   JsonUiTrading(Minecraft minecraft, JsonUiContainerScreen screen, MerchantMenu menu, TradeData data, Workstations trades, Workstations.TradeView view) {
      this.minecraft = minecraft;
      this.screen = screen;
      this.menu = menu;
      this.data = data;
      this.trades = trades;
      this.seen = new ItemStack[menu.slots.size()];
      this.seenCounts = new int[menu.slots.size()];
      this.shown = view;
      this.rebuild(view);
      data.actions(this);
   }

   static void fill(TradeData data, Workstations.TradeView view, int experience) {
      List<TradeData.Offer> offers = new ArrayList<>(view.offers().size());

      for (TradeOffer offer : view.offers()) {
         TradeData.Item first = item(offer.buyA(), offer.priceA(), offer.buyCountA());
         TradeData.Item second = offer.hasB() ? item(offer.buyB(), offer.priceB(), offer.buyCountB()) : TradeData.Item.NONE;
         TradeData.Item sell = item(offer.sell(), offer.sellCount(), offer.sellCount());
         offers.add(new TradeData.Offer(offer.tier(), first, second, sell, offer.outOfStock(), offer.traderExp()));
      }

      data.trader(view.name(), view.levels(), view.tierExp(), view.tier(), offers);
      data.experience(experience);
   }

   private static TradeData.Item item(NbtMap nbt, int count, int baseCount) {
      ItemStack stack = Workstations.javaItem(nbt);
      return new TradeData.Item(count, baseCount > 0 ? baseCount : count, stack.isEmpty() ? "" : stack.getHoverName().getString());
   }

   private void rebuild(Workstations.TradeView view) {
      this.items = new ItemStack[view.offers().size()][];

      for (int i = 0; i < this.items.length; i++) {
         TradeOffer offer = view.offers().get(i);
         this.items[i] = new ItemStack[]{
            Workstations.javaItem(offer.buyA()), offer.hasB() ? Workstations.javaItem(offer.buyB()) : ItemStack.EMPTY, Workstations.javaItem(offer.sell())
         };
      }

      this.affordable = new boolean[this.items.length];
      this.seenOffers = null;
   }

   public Workstations.TradeView view() {
      return this.shown;
   }

   public TradeData data() {
      return this.data;
   }

   public List<UiControl> buttons() {
      List<UiControl> found = new ArrayList<>();
      UiControl root = this.screen.ui().instance().root();
      if (root != null) {
         root.walk(control -> {
            if (control.type() == UiType.TOGGLE && control.contextual() && this.data.offerAt(control.itemPath()) >= 0) {
               found.add(control);
            }
         });
      }

      found.sort(Comparator.comparingInt(control -> this.data.offerAt(control.itemPath())));
      return found;
   }

   public int offerOf(int javaOffer) {
      for (int offer = 0; offer < this.shown.offers().size(); offer++) {
         if (this.data.javaOffer(offer) == javaOffer) {
            return offer;
         }
      }

      return -1;
   }

   void read() {
      Workstations.TradeView view = this.trades.tradeView();
      if (view != null && view != this.shown && view.containerId() == this.menu.containerId) {
         this.shown = view;
         this.rebuild(view);
         fill(this.data, view, this.trades.traderExperience(view.trader()));
      } else {
         this.data.experience(this.trades.traderExperience(this.shown.trader()));
      }

      MerchantOffers offers = this.menu.getOffers();
      boolean changed = offers != this.seenOffers || this.data.selected() != this.seenSelected;

      for (int slot = 0; slot < this.seen.length; slot++) {
         ItemStack stack = this.menu.getSlot(slot).getItem();
         if (stack != this.seen[slot] || stack.getCount() != this.seenCounts[slot]) {
            this.seen[slot] = stack;
            this.seenCounts[slot] = stack.getCount();
            changed = true;
         }
      }

      if (changed) {
         this.seenOffers = offers;
         this.seenSelected = this.data.selected();

         for (int offer = 0; offer < this.affordable.length; offer++) {
            int java = this.data.javaOffer(offer);
            this.affordable[offer] = java >= 0 && java < offers.size() && this.canPay(offers.get(java));
         }

         this.data.affordable(this.affordable);
         int chosen = this.data.javaOffer(this.data.selected());
         MerchantOffer offer = chosen >= 0 && chosen < offers.size() ? offers.get(chosen) : null;
         this.data.slots(takes(offer, this.menu.getSlot(0).getItem()), takes(offer, this.menu.getSlot(1).getItem()), this.menu.getSlot(2).hasItem());
      }
   }

   private boolean canPay(MerchantOffer offer) {
      ItemCost first = offer.getItemCostA();
      ItemCost second = offer.getItemCostB().orElse(null);
      int needFirst = offer.getCostA().getCount();
      int needSecond = second == null ? 0 : offer.getCostB().getCount();
      int haveFirst = 0;
      int haveSecond = 0;

      for (int slot = 0; slot < this.seen.length; slot++) {
         ItemStack stack = this.menu.getSlot(slot).getItem();
         if (slot != 2 && !stack.isEmpty()) {
            if (first.test(stack)) {
               haveFirst += stack.getCount();
            } else if (second != null && second.test(stack)) {
               haveSecond += stack.getCount();
            }
         }
      }

      return haveFirst >= needFirst && haveSecond >= needSecond;
   }

   private static boolean takes(@Nullable MerchantOffer offer, ItemStack stack) {
      if (!stack.isEmpty() && offer != null) {
         ItemCost second = offer.getItemCostB().orElse(null);
         return offer.getItemCostA().test(stack) || second != null && second.test(stack);
      } else {
         return true;
      }
   }

   ItemStack stack(UiControl renderer) {
      UiItemPath path = renderer.itemPath();
      int offer = this.data.offerAt(path);
      String cell = TradeData.cellAt(path);
      if (offer >= 0 && offer < this.items.length && cell != null) {
         ItemStack[] var10000 = this.items[offer];

         return var10000[switch (cell) {
            case "trade_item_1" -> 0;
            case "trade_item_2" -> 1;
            default -> 2;
         }];
      } else {
         return ItemStack.EMPTY;
      }
   }

   void tooltip(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
      UiControl hover = this.screen.ui().input().hoveredThrough();
      if (hover != null && hover.visible()) {
         ItemStack stack = this.stack(hover);
         if (!stack.isEmpty()) {
            graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
         }
      }
   }

   @Override
   public void select(int javaOffer) {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (connection != null && javaOffer >= 0 && javaOffer < this.menu.getOffers().size()) {
         this.menu.setSelectionHint(javaOffer);
         this.menu.tryMoveItems(javaOffer);
         connection.send(new ServerboundSelectTradePacket(javaOffer));
      }
   }

   @Override
   public void trade() {
      if (this.menu.getSlot(2).hasItem()) {
         this.screen.quickMove(2);
      }
   }
}
