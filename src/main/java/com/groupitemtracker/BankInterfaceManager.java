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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import static com.groupitemtracker.GroupItemTrackerConfig.BankHighlightMode;

public class BankInterfaceManager extends WidgetItemOverlay
{
	private static final String BANK_SEARCH_KEYWORD = "/g";
	private static final String BANK_SEARCH_KEYWORD_HINT = "<br>" + "Type " + BANK_SEARCH_KEYWORD + " to show tracked items";
	private static final String MENU_OPTION_ADD = "Start-tracking";
	private static final String MENU_OPTION_REMOVE = "Stop-tracking";
	private static final String MENU_OPTION_EDIT_MODE_ENTER = "Enter edit mode";
	private static final String MENU_OPTION_EDIT_MODE_EXIT = "Exit edit mode";

	private final Client client;
	private final ConfigManager configManager;
	private final ItemIdentifier itemIdentifier;
	private final ItemManager itemManager;
	private final ItemTracker itemTracker;

	private Set<Integer> itemCache = new HashSet<>();
	private BankHighlightMode outlineMode = BankHighlightMode.NEVER;
	private Color outlineColor = new Color(0, 0, 0, 0);
	private boolean useSearchFilter = false;
	private boolean editModeEnabled = false;

	@Inject
	public BankInterfaceManager(Client client, ConfigManager configManager, ItemIdentifier itemIdentifier, ItemManager itemManager, ItemTracker itemTracker)
	{
		this.client = client;
		this.configManager = configManager;
		this.itemIdentifier = itemIdentifier;
		this.itemManager = itemManager;
		this.itemTracker = itemTracker;
		showOnBank();
	}

	public void refreshConfig(GroupItemTrackerConfig config)
	{
		useSearchFilter = config.useBankFilter();
		outlineMode = config.bankOutlineMode();
		outlineColor = config.bankOutlineColor();
		editModeEnabled = config.editModeActive();
	}

	public void freeExcessMemory()
	{
		itemCache = new HashSet<>(0);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		boolean drawOutlines = outlineMode == BankHighlightMode.ALWAYS || outlineMode == BankHighlightMode.EDIT_MODE_ONLY && editModeEnabled;
		if (drawOutlines && itemCache.contains(itemId))
		{
			Rectangle bounds = widgetItem.getCanvasBounds();
			BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), outlineColor);
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
	private void onMenuEntryAdded(MenuEntryAdded event)
	{
		boolean doToggleEditMode = false;
		boolean doToggleTrackedItem = false;

		switch (event.getActionParam1())
		{
			case InterfaceID.Bankmain.GIM_STORAGE:
				doToggleEditMode = event.getOption().equals("Group Storage");
				break;
			case InterfaceID.SharedBank.MAIN_BANK:
				doToggleEditMode = event.getOption().equals("Back to bank");
				break;
			case InterfaceID.Bankmain.ITEMS:
			case InterfaceID.SharedBank.ITEMS:
				doToggleTrackedItem = editModeEnabled && event.getOption().equals("Examine");
				break;
		}

		if (doToggleEditMode)
		{
			MenuEntry entry = client.getMenu().createMenuEntry(-1);
			entry.setOption(editModeEnabled ? MENU_OPTION_EDIT_MODE_EXIT : MENU_OPTION_EDIT_MODE_ENTER);
			entry.onClick(e -> configManager.setConfiguration(
				GroupItemTrackerConfig.GROUP, GroupItemTrackerConfig.KEY_EDIT_MODE_ACTIVE, !editModeEnabled));
		}

		if (doToggleTrackedItem)
		{
			final int itemID = event.getItemId();
			boolean isTracked = itemTracker.isTracking(itemID);
			MenuEntry entry = client.getMenu().createMenuEntry(-1);
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

		switch (event.getEventName())
		{
			case "setSearchBankInputText":
			case "setSearchBankInputTextFound":
			{
				// Append search filter hint.
				Object[] objectStack = client.getObjectStack();
				int lastIdx = client.getObjectStackSize() - 1;
				Object existingHint = objectStack[lastIdx];
				objectStack[lastIdx] = existingHint + BANK_SEARCH_KEYWORD_HINT;
				break;
			}
			case "bankSearchFilter":
			{
				// Filter search results by tracked items.
				Object lastObject = client.getObjectStack()[client.getObjectStackSize() - 1];
				if (lastObject.equals(BANK_SEARCH_KEYWORD))
				{
					int[] intStack = client.getIntStack();
					int intStackSize = client.getIntStackSize();
					int itemID = intStack[intStackSize - 1];

					int isSearchResult = !itemIdentifier.isPlaceholder(itemID) && itemTracker.isTracking(itemID) ? 1 : 0;
					intStack[intStackSize - 2] = isSearchResult;
				}
				break;
			}
		}
	}
}