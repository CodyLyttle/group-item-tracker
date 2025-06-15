package com.groupitemtracker;

import com.google.inject.Provides;
import java.util.Objects;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(name = "Group Item Tracker")
public class GroupItemTrackerPlugin extends Plugin
{
	private static final String BANK_SEARCH_KEYWORD = "/g";
	private static final String BANK_SEARCH_KEYWORD_HINT = "<br>" + "Use " + BANK_SEARCH_KEYWORD + " to filter by group item tracker";
	private static final String MENU_OPTION_ADD = "Add to GIM item tracker";
	private static final String MENU_OPTION_REMOVE = "Remove from GIM item tracker";

	@Inject
	private Client client;

	@Inject
	private GroupItemTrackerConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ItemTracker itemTracker;

	@Inject
	private OverlayManager overlayManager;

	private BankItemOverlay bankItemOverlay;

	@Provides
	GroupItemTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroupItemTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		bankItemOverlay = new BankItemOverlay(itemManager, itemTracker);
		eventBus.register(bankItemOverlay);
		overlayManager.add(bankItemOverlay);
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(bankItemOverlay);
		overlayManager.remove(bankItemOverlay);
		bankItemOverlay = null;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		TrackedContainer.fromItemContainer(event.getItemContainer())
			.ifPresent(container -> itemTracker.refreshContainer(container));
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

				ItemContainer bankContainer = Objects.requireNonNullElse(
					client.getItemContainer(InventoryID.BANK),
					client.getItemContainer(InventoryID.INV_GROUP_TEMP)
				);
				bankItemOverlay.refreshItemCache(bankContainer);
			});
		}
	}

	@Subscribe(priority = -1) // Force callback to run after other plugins, specifically bank-tags.
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		final String eventName = event.getEventName();
		final Object[] stringStack = client.getObjectStack();
		final int stringStackSize = client.getObjectStackSize();

		switch (eventName)
		{
			// Append bank search keyword hint.
			case "setSearchBankInputText":
			case "setSearchBankInputTextFound":
				stringStack[stringStackSize - 1] = stringStack[stringStackSize - 1] + BANK_SEARCH_KEYWORD_HINT;
				break;
			// Bank search keyword overrides filter to display tracked items.
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
}