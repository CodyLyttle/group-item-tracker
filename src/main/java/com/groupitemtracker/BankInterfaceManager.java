package com.groupitemtracker;

import com.groupitemtracker.events.ItemRemoved;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptCallbackEvent;
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
	private static final String BANK_SEARCH_KEYWORD_HINT = "<br>" + "Use " + BANK_SEARCH_KEYWORD + " to filter by group item tracker";
	private static final String MENU_OPTION_ADD = "Add to GIM item tracker";
	private static final String MENU_OPTION_REMOVE = "Remove from GIM item tracker";

	private final ItemManager itemManager;
	private final ItemTracker itemTracker;
	private final Set<Integer> itemCache = new HashSet<>();
	private final Client client;
	private final GroupItemTrackerConfig config;
	private Color highlightColor;
	private boolean useItemHighlights;
	private boolean useSearchFilter;

	@Inject
	public BankInterfaceManager(Client client, GroupItemTrackerConfig config, ItemManager itemManager, ItemTracker itemTracker)
	{
		this.client = client;
		this.config = config;
		this.itemManager = itemManager;
		this.itemTracker = itemTracker;
		useSearchFilter = config.useBankFilter();
		useItemHighlights = config.useBankHighlights();
		highlightColor = config.bankHighlightColor();
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		// Only highlight cached, non-placeholder items.
		if (useItemHighlights && itemCache.contains(itemId) && widgetItem.getQuantity() > 0)
		{
			final Rectangle bounds = widgetItem.getCanvasBounds();
			final BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), highlightColor);
			graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
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
	private void onItemContainerChanged(ItemContainerChanged event)
	{
		final int containerID = event.getContainerId();
		if (containerID == InventoryID.BANK || containerID == InventoryID.INV_GROUP_TEMP)
		{
			refreshContainer(event.getItemContainer());
		}
	}

	@Subscribe
	private void onItemRemoved(ItemRemoved event)
	{
		ItemContainer bankContainer = getBankContainerOrNull();
		if (bankContainer != null)
		{
			refreshContainer(bankContainer);
		}
	}

	@Subscribe
	private void onMenuEntryAdded(MenuEntryAdded event)
	{
		int interfaceID = event.getActionParam1();
		boolean isBank = (interfaceID == InterfaceID.Bankmain.ITEMS || interfaceID == InterfaceID.SharedBank.ITEMS);

		// Insert tracked item menu option after the 'Examine' menu option.
		if (isBank && event.getOption().equals("Examine"))
		{
			int itemID = client.getWidget(interfaceID).getChild(event.getActionParam0()).getItemId();
			boolean isTracked = itemTracker.containsItem(itemID);

			Menu menu = client.getMenu();
			MenuEntry entry = menu.createMenuEntry(1);
			entry.setItemId(itemID);
			entry.setOption(isTracked ? MENU_OPTION_REMOVE : MENU_OPTION_ADD);
			entry.onClick(e ->
			{
				if (isTracked)
				{
					itemTracker.removeItem(itemID);
				}
				else
				{
					itemTracker.addItem(itemID);
				}

				final ItemContainer bankContainer = getBankContainerOrNull();
				if (bankContainer != null)
				{
					refreshContainer(bankContainer);
				}
			});
		}
	}

	@Subscribe(priority = -1) // Force callback to run after other plugins, specifically bank-tags.
	private void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!useSearchFilter)
		{
			return;
		}

		final Object[] stringStack = client.getObjectStack();
		final int stringStackSize = client.getObjectStackSize();
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
				final String searchFilter = (String) stringStack[stringStackSize - 1];
				if (searchFilter.equals(BANK_SEARCH_KEYWORD))
				{
					final int[] intStack = client.getIntStack();
					final int intStackSize = client.getIntStackSize();
					final int itemID = intStack[intStackSize - 1];

					// Whether the item should be included in the search results.
					intStack[intStackSize - 2] = itemTracker.containsItem(itemID) ? 1 : 0;
				}
				break;
		}
	}

	private ItemContainer getBankContainerOrNull()
	{
		ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
		return bankContainer != null ? bankContainer : client.getItemContainer(InventoryID.INV_GROUP_TEMP);
	}

	// Profiled: < 1ms.
	public void refreshContainer(ItemContainer bankContainer)
	{
		assert bankContainer.getId() == InventoryID.BANK || bankContainer.getId() == InventoryID.INV_GROUP_TEMP;

		itemCache.clear();
		for (Item item : bankContainer.getItems())
		{
			final int itemID = item.getId();
			if (itemTracker.containsItem(itemID))
			{
				itemCache.add(itemID);
			}
		}
	}
}