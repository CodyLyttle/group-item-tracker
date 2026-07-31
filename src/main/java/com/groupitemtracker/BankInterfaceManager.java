package com.groupitemtracker;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

public class BankInterfaceManager extends WidgetItemOverlay
{
	private static final String BANK_SEARCH_KEYWORD = "/g";
	private static final String BANK_SEARCH_KEYWORD_HINT = "<br>" + "Type " + BANK_SEARCH_KEYWORD + " to show tracked items";
	private static final String MENU_OPTION_ADD = "Start-tracking";
	private static final String MENU_OPTION_REMOVE = "Stop-tracking";

	private final Client client;
	private final GroupItemTrackerConfig config;
	private final ItemIdentifier itemIdentifier;
	private final ItemManager itemManager;
	private final ItemTracker itemTracker;

	private final Set<Integer> itemCache = new HashSet<>();
	private boolean useItemHighlights;
	private boolean useSearchFilter;
	private Color highlightColor;

	@Inject
	public BankInterfaceManager(Client client, GroupItemTrackerConfig config, ItemIdentifier itemIdentifier, ItemManager itemManager, ItemTracker itemTracker)
	{
		this.client = client;
		this.config = config;
		this.itemIdentifier = itemIdentifier;
		this.itemManager = itemManager;
		this.itemTracker = itemTracker;
		showOnBank();
	}

	public void startup()
	{
		useSearchFilter = config.useBankFilter();
		useItemHighlights = config.useBankHighlights();
		highlightColor = config.bankHighlightColor();
	}

	public void shutdown()
	{
		useSearchFilter = false;
		useItemHighlights = false;
		highlightColor = null;
		itemCache.clear();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (useItemHighlights && itemCache.contains(itemId))
		{
			Rectangle bounds = widgetItem.getCanvasBounds();
			BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), highlightColor);
			graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
		}
	}

	@Subscribe
	private void onInvalidated(ItemTracker.Invalidated event)
	{
		refreshItemCache();
	}

	@Subscribe
	private void onItemAdded(ItemTracker.ItemAdded event)
	{
		refreshItemCache();
	}

	@Subscribe
	private void onItemRemoved(ItemTracker.ItemRemoved event)
	{
		refreshItemCache();
	}

	@Subscribe
	private void onItemsUpdated(ItemTracker.ItemsUpdated event)
	{
		refreshItemCache();
	}

	@Subscribe
	private void onWidgetLoaded(WidgetLoaded event)
	{
		var interfaceID = event.getGroupId();
		if (interfaceID == InterfaceID.BANKMAIN || interfaceID == InterfaceID.SHARED_BANK)
		{
			refreshItemCache();
		}
	}

	private void refreshItemCache()
	{
		var bank = client.getItemContainer(InventoryID.BANK);
		if (bank != null)
		{
			itemCache.clear();
			addContainerToCache(bank);
			return;
		}

		var groupStorage = client.getItemContainer(InventoryID.INV_GROUP_TEMP);
		if (groupStorage != null)
		{
			itemCache.clear();
			addContainerToCache(groupStorage);

			// ItemTracker doesn't monitor group storage so we can't rely on its events to keep the cache up-to-date.
			// We can work around this by supplementing the cache with the tracked items in our inventory.
			// The cache is cleared whenever the bank is loaded, so we don't have to worry about reverting this.
			var inventory = client.getItemContainer(InventoryID.INV);
			if (inventory != null)
			{
				addContainerToCache(inventory);
			}
		}
	}

	private void addContainerToCache(ItemContainer container)
	{
		for (var item : container.getItems())
		{
			int itemID = item.getId();
			if (itemTracker.isTracking(itemID) && !itemIdentifier.isPlaceholder(itemID))
			{
				itemCache.add(itemID);
			}
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(GroupItemTrackerConfig.GROUP))
		{
			switch (event.getKey())
			{
				case GroupItemTrackerConfig.KEY_BANK_FILTER:
					useSearchFilter = config.useBankFilter();
					break;
				case GroupItemTrackerConfig.KEY_BANK_HIGHLIGHTS:
					useItemHighlights = config.useBankHighlights();
					break;
				case GroupItemTrackerConfig.KEY_BANK_HIGHLIGHTS_COLOR:
					highlightColor = config.bankHighlightColor();
					break;
			}
		}
	}

	@Subscribe
	private void onMenuEntryAdded(MenuEntryAdded event)
	{
		int param = event.getActionParam1();
		boolean isBankItemMenu = param == InterfaceID.Bankmain.ITEMS || param == InterfaceID.SharedBank.ITEMS;

		// Add custom menu option after (above) Examine.
		if (isBankItemMenu && event.getOption().equals("Examine"))
		{
			final int itemID = event.getItemId();
			boolean isTracked = itemTracker.isTracking(itemID);

			MenuEntry entry = client.getMenu().createMenuEntry(-1);
			entry.setItemId(itemID);
			entry.setOption(isTracked ? MENU_OPTION_REMOVE : MENU_OPTION_ADD);
			entry.setTarget(event.getTarget());

			if (isTracked)
			{
				entry.onClick(e -> itemTracker.stopTracking(itemID));
			}
			else
			{
				entry.onClick(e -> itemTracker.startTracking(itemID));
			}
		}
	}

	@Subscribe(priority = -1) // Force callback to run after other plugins, specifically bank-tags.
	private void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!useSearchFilter)
		{
			return;
		}

		Object[] stringStack = client.getObjectStack();
		int stringStackSize = client.getObjectStackSize();
		switch (event.getEventName())
		{
			// Append bank search keyword hint.
			// Shared storage quickly overwrites our message, not sure if we can prevent this.
			case "setSearchBankInputText":
			case "setSearchBankInputTextFound":
				stringStack[stringStackSize - 1] = stringStack[stringStackSize - 1] + BANK_SEARCH_KEYWORD_HINT;
				break;
			// Bank search keyword overrides filter to display tracked items.
			// This works for both bank and shared storage.
			case "bankSearchFilter":
				String searchFilter = (String) stringStack[stringStackSize - 1];
				if (searchFilter.equals(BANK_SEARCH_KEYWORD))
				{
					int[] intStack = client.getIntStack();
					int intStackSize = client.getIntStackSize();
					int itemID = intStack[intStackSize - 1];

					// Whether the item should be included in the search results.
					intStack[intStackSize - 2] = !itemIdentifier.isPlaceholder(itemID) &&
						itemTracker.isTracking(itemID) ? 1 : 0;
				}
				break;
		}
	}
}