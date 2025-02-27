package fr.epicanard.globalmarketchest.gui.shops.interfaces;

import fr.epicanard.globalmarketchest.GlobalMarketChest;
import fr.epicanard.globalmarketchest.auctions.AuctionInfo;
import fr.epicanard.globalmarketchest.gui.InterfacesLoader;
import fr.epicanard.globalmarketchest.gui.InventoryGUI;
import fr.epicanard.globalmarketchest.gui.TransactionKey;
import fr.epicanard.globalmarketchest.gui.actions.NextInterface;
import fr.epicanard.globalmarketchest.gui.actions.PreviousInterface;
import fr.epicanard.globalmarketchest.gui.shops.Droppable;
import fr.epicanard.globalmarketchest.gui.shops.baseinterfaces.ShopInterface;
import fr.epicanard.globalmarketchest.shops.ShopInfo;
import fr.epicanard.globalmarketchest.utils.*;
import fr.epicanard.globalmarketchest.utils.reflection.VersionSupportUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreateAuctionItem extends ShopInterface implements Droppable {
  private Integer maxAuctions = 0;
  private Boolean acceptDamagedItems;
  private final Boolean oneByOne;

  public CreateAuctionItem(InventoryGUI inv) {
    super(inv);
    this.isTemp = true;
    this.actions.put(22, i -> this.unsetItem());
    this.actions.put(0, new PreviousInterface());

    final YamlConfiguration config = ConfigUtils.get();

    this.acceptDamagedItems = config.getBoolean("Options.AcceptDamagedItems", true);

    final boolean max = config.getBoolean("Options.EnableMaxRepeat", true);
    final boolean one = config.getBoolean("Options.EnableMaxInOne", true);
    this.oneByOne = config.getBoolean("Options.EnableRepeatOneByOne", true);

    if (one) {
      this.actions.put(48, i -> this.defineMaxInOne());
      this.togglers.get(48).set();
    }
    if (max) {
      this.actions.put(50, i -> this.defineMaxRepeat());
      this.togglers.get(50).set();
    }
    if (this.oneByOne) {
      this.actions.put(25, i -> this.defineAuctionNumber(true));
      this.togglers.get(25).set();
      this.actions.put(34, i -> this.defineAuctionNumber(false));
      this.togglers.get(34).set();
    }

    this.actions.put(53, new NextInterface("CreateAuctionPrice", this::checkItem));
  }

  @Override
  public void load() {
    super.load();

    final ItemStack item = this.inv.getTransactionValue(TransactionKey.TEMP_ITEM);
    this.defineMaxAuctions();
    if (item != null) {
      this.inv.getInv().setItem(22, item);
      this.updateItem();
    } else {
      this.unsetItem();
    }
  }

  /**
   * Define the maximum number of auctions a player can create
   */
  private void defineMaxAuctions() {
    final Integer maxAuctionsByPlayer = this.inv.getPlayerRankProperties().getMaxAuctionByPlayer();
    final ShopInfo shop = this.inv.getTransactionValue(TransactionKey.SHOP_INFO);

    GlobalMarketChest.plugin.auctionManager.getAuctionNumber(shop.getGroup(), inv.getPlayer(),
            num -> this.maxAuctions = maxAuctionsByPlayer - num);
  }

  /**
   * Set the item in dropzone when drop
   *
   * @param item ItemStack to set in drop zone
   */
  private void setItem(ItemStack item) {
    final AuctionInfo auction = this.inv.getTransactionValue(TransactionKey.AUCTION_INFO);

    auction.setAmount(item.getAmount());
    auction.setItemStack(item);
    this.inv.getTransaction().put(TransactionKey.AUCTION_NUMBER, 1);
    this.inv.getTransaction().put(TransactionKey.TEMP_ITEM, item.clone());
    this.inv.getTransaction().put(TransactionKey.AUCTION_AMOUNT, item.getAmount());
    this.updateItem();
    this.togglers.forEach((k, v) -> {
      if (k == 22 || k == 53)
        v.set();
    });
  }

  /**
   * Update lore of repeat one by one items
   *
   * @param lore Lore to set on items
   */
  private void updateRepeatLore(List<String> lore) {
    ItemUtils.updateLore(this.inv.getInv(), 25, lore);
    ItemUtils.updateLore(this.inv.getInv(), 34, lore);
  }

  /**
   * Remove the item from drop zone
   */
  private void unsetItem() {
    this.inv.getWarn().stopWarn();
    this.inv.getTransaction().remove(TransactionKey.TEMP_ITEM);
    this.inv.getTransaction().remove(TransactionKey.AUCTION_AMOUNT);
    InterfacesLoader.getInstance().getInterface("CreateAuctionItem")
        .ifPresent(items -> this.inv.getInv().setItem(22, items[22]));
    this.togglers.forEach((k, v) -> {
      if (k == 22 || k == 53)
        v.unset();
    });
    if (this.oneByOne) {
      this.updateRepeatLore(Collections.emptyList());
    }
  }

  /**
   * Check if TEMP_ITEM is set (item dropped in interface)
   *
   * @return false if TEMP_ITEM is not set else true
   */
  private Boolean checkItem() {
    final ItemStack item = this.inv.getTransactionValue(TransactionKey.TEMP_ITEM);
    if (item != null && ConfigUtils.getBoolean("Options.UseLastPrice", true)) {
      final AuctionInfo auction = this.inv.getTransactionValue(TransactionKey.AUCTION_INFO);
      GlobalMarketChest.plugin.auctionManager.getLastPrice(auction, auction::setPrice);
    }
    return item != null;
  }

  /**
   * Get lore with quantity and price for current auction item
   *
   * @return the lore completed
   */
  private void updateItem() {
    final ItemStack item = this.inv.getTransactionValue(TransactionKey.TEMP_ITEM);
    final AuctionInfo auction = this.inv.getTransactionValue(TransactionKey.AUCTION_INFO);
    final List<String> lore = new ArrayList<>();

    lore.add("&7" + LangUtils.get("Divers.Quantity") + " : &6" + auction.getAmount());
    lore.add("&7" + LangUtils.get("Divers.AuctionNumber") + " : &6" + this.inv.getTransactionValue(TransactionKey.AUCTION_NUMBER));

    if (this.oneByOne) {
      this.updateRepeatLore(lore);
    }

    lore.add(GlobalMarketChest.plugin.getCatHandler().getDisplayCategory(item));
    this.inv.getInv().setItem(22, VersionSupportUtils.getInstance().setNbtTag(ItemStackUtils.setItemStackLore(item.clone(), lore)));
  }

  /**
   * Put all items matching with droppped item in one auction
   */
  private void defineMaxInOne() {
    this.inv.getWarn().stopWarn();
    final ItemStack item = this.inv.getTransactionValue(TransactionKey.TEMP_ITEM);
    final AuctionInfo auction = this.inv.getTransactionValue(TransactionKey.AUCTION_INFO);

    if (item == null || auction == null)
      return;
    this.inv.getTransaction().put(TransactionKey.AUCTION_NUMBER, 1);
    final ItemStack[] items = this.inv.getPlayer().getInventory().getContents();
    final Integer max = PlayerUtils.countMatchingItem(items, item);

    item.setAmount(ItemStackUtils.getMaxStack(item, max));
    auction.setItemStack(item);
    auction.setAmount(max);

    this.updateItem();
  }

  /**
   * Repeat the item dropped in as many auctions as possible.
   * The auction number is limited by config
   */
  private void defineMaxRepeat() {
    final Integer maxAuctionNumber = this.resetAndGetMaxAuctionNumber();
    if (maxAuctionNumber == null)
      return;
    this.inv.getTransaction().put(TransactionKey.AUCTION_NUMBER, (maxAuctionNumber > this.maxAuctions) ? this.maxAuctions : maxAuctionNumber);

    this.updateItem();
  }

  /**
   * Increase or decrease the number of auction
   *
   * @param add Define if increase or decrease
   */
  private void defineAuctionNumber(Boolean add) {
    final Integer maxAuctionNumber = this.resetAndGetMaxAuctionNumber();
    if (maxAuctionNumber == null)
      return;
    Integer auctionNumber = this.inv.getTransactionValue(TransactionKey.AUCTION_NUMBER);

    if (add && auctionNumber + 1 <= maxAuctionNumber && auctionNumber + 1 <= this.maxAuctions) {
      auctionNumber += 1;
    }

    if (!add && auctionNumber - 1 > 0) {
      auctionNumber = (auctionNumber <= maxAuctionNumber) ? auctionNumber - 1 : maxAuctionNumber;
    }

    this.inv.getTransaction().put(TransactionKey.AUCTION_NUMBER, auctionNumber);
    this.updateItem();
  }

  /**
   * Reset auction quantity and get max auction number possible with original quantity
   *
   * @return Max auction number possible
   */
  private Integer resetAndGetMaxAuctionNumber() {
    this.inv.getWarn().stopWarn();

    final ItemStack item = this.inv.getTransactionValue(TransactionKey.TEMP_ITEM);
    final AuctionInfo auction = this.inv.getTransactionValue(TransactionKey.AUCTION_INFO);
    if (item == null || auction == null)
      return null;

    auction.setAmount(this.inv.getTransactionValue(TransactionKey.AUCTION_AMOUNT));
    item.setAmount(auction.getAmount());
    auction.setItemStack(item);

    final ItemStack[] items = this.inv.getPlayer().getInventory().getContents();
    return PlayerUtils.countMatchingItem(items, item) / auction.getAmount();
  }

  /**
   * Called when a mouse drop event is done inside inventory
   *
   * @param event
   */
  @Override
  public void onDrop(InventoryClickEvent event, InventoryGUI inv) {
    ItemStack item = null;
    if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
      item = event.getCurrentItem();
    } else if (event.getSlot() == 22) {
      item = event.getCursor();
      event.getWhoClicked().setItemOnCursor(null);
      event.getWhoClicked().getInventory().addItem(item.clone());
    }
    if (item != null) {
      this.inv.getWarn().stopWarn();
      if (ItemStackUtils.isBlacklisted(item))
        this.inv.getWarn().warn("BlacklistedItem", 40);
      else if (!this.acceptDamagedItems && ItemStackUtils.isDamaged(item))
        this.inv.getWarn().warn("DamagedItem", 40);
      else
        this.setItem(item);
    }
  }

  @Override
  public void destroy() {
    super.destroy();
    this.unsetItem();
    this.inv.getTransaction().remove(TransactionKey.TEMP_ITEM);
    this.inv.getTransaction().remove(TransactionKey.AUCTION_INFO);
    this.inv.getTransaction().remove(TransactionKey.AUCTION_NUMBER);
    this.inv.getTransaction().remove(TransactionKey.AUCTION_AMOUNT);
  }
}
