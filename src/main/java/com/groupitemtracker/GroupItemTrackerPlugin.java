package com.groupitemtracker;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.groupitemtracker.sidebar.SidebarPanel;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.events.GameStateChanged;
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
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private GroupItemTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private Gson gson;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ItemTracker itemTracker;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ProfileManager profileManager;

	private BankInterfaceManager bankItemOverlay;
	private NavigationButton sidebarNavButton;
	private SidebarPanel sidebarPanel;
	private boolean isProfileLoaded;

	@Provides
	GroupItemTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroupItemTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		bankItemOverlay = new BankInterfaceManager(client, config, itemManager, itemTracker);
		overlayManager.add(bankItemOverlay);

		sidebarPanel = new SidebarPanel(clientThread, itemManager, itemTracker);
		clientThread.invokeLater(() ->
		{
			final BufferedImage sidebarIcon = itemManager.getImage(ItemID.LEAGUE_BANKERS_NOTE);
			sidebarNavButton = NavigationButton.builder()
				.tooltip("Group Item Tracker")
				.icon(sidebarIcon)
				.panel(sidebarPanel)
				.build();
			clientToolbar.addNavigation(sidebarNavButton);

			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadProfile();
			}
		});

		eventBus.register(bankItemOverlay);
		eventBus.register(itemTracker);
		eventBus.register(profileManager);
		eventBus.register(sidebarPanel);
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(bankItemOverlay);
		eventBus.unregister(itemTracker);
		eventBus.unregister(profileManager);
		eventBus.unregister(sidebarPanel);

		overlayManager.remove(bankItemOverlay);
		bankItemOverlay = null;

		unloadProfile();
		clientToolbar.removeNavigation(sidebarNavButton);
		sidebarPanel = null;
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		if (isProfileLoaded && event.getGameState() == GameState.LOGIN_SCREEN)
		{
			unloadProfile();
		}
		else if (!isProfileLoaded && event.getGameState() == GameState.LOADING)
		{
			loadProfile();
		}
	}

	private void loadProfile()
	{
		isProfileLoaded = true;
		itemTracker.loadProfile(profileManager);
		
		boolean isBankOpen = client.getItemContainer(InventoryID.BANK) != null;
		sidebarPanel.login(isBankOpen);
	}

	private void unloadProfile()
	{
		itemTracker.reset();
		sidebarPanel.logout();
		isProfileLoaded = false;
	}
}