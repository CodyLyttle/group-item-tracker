package com.groupitemtracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
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
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

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

	private NavigationButton sidebarNavButton;

	private SidebarPanel sidebarPanel;

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

		sidebarPanel = new SidebarPanel(this, itemManager);
		clientThread.invokeLater(() ->
		{
			final BufferedImage sidebarIcon = itemManager.getImage(ItemID.LEAGUE_BANKERS_NOTE);
			sidebarNavButton = NavigationButton.builder()
				.tooltip("Group Item Tracker")
				.icon(sidebarIcon)
				.panel(sidebarPanel)
				.build();
			clientToolbar.addNavigation(sidebarNavButton);
		});
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(bankItemOverlay);
		overlayManager.remove(bankItemOverlay);
		bankItemOverlay = null;

		clientToolbar.removeNavigation(sidebarNavButton);
		sidebarPanel = null;
	}

	@Subscribe
	private void onGameTick(GameTick event)
	{
		for (var item : itemTracker.getItems())
		{
			sidebarPanel.refreshItemPanel(item);
		}
	}

	@Subscribe
	private void onItemContainerChanged(ItemContainerChanged event)
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
					TrackedItem removedItem = itemTracker.removeItem(itemID);
					sidebarPanel.removeItemPanel(removedItem);
				}
				else
				{
					TrackedItem addedItem = itemTracker.addItem(itemID);
					sidebarPanel.addItemPanel(addedItem);
				}

				final ItemContainer bankContainer = tryGetBankContainer();
				if (bankContainer != null)
				{
					bankItemOverlay.refreshItemCache(bankContainer);
				}
			});
		}
	}

	@Subscribe(priority = -1) // Force callback to run after other plugins, specifically bank-tags.
	private void onScriptCallbackEvent(ScriptCallbackEvent event)
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

	public void removeItem(TrackedItem item)
	{
		// Called from UI thread.
		clientThread.invokeLater(() -> {
			itemTracker.removeItem(item.getItemID());
			ItemContainer bankContainer = tryGetBankContainer();
			if (bankContainer != null)
			{
				bankItemOverlay.refreshItemCache(bankContainer);
			}
		});
	}

	private ItemContainer tryGetBankContainer()
	{
		ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
		return bankContainer != null ? bankContainer : client.getItemContainer(InventoryID.INV_GROUP_TEMP);
	}
}