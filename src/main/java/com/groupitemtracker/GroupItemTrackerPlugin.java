package com.groupitemtracker;

import com.google.inject.Provides;
import com.groupitemtracker.sidebar.SidebarPanel;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
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
import net.runelite.client.util.ImageUtil;

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
	private EventBus eventBus;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BankInterfaceManager bankInterfaceManager;

	@Inject
	private ItemTracker itemTracker;

	@Inject
	private ProfileManager profileManager;

	private NavigationButton navButton;
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
		// Icon from cache dump: sprite 3553.
		sidebarPanel = new SidebarPanel(clientThread, itemManager, itemTracker);
		BufferedImage sidebarIcon = ImageUtil.loadImageResource(getClass(), "sidebar_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Group Item Tracker")
			.icon(sidebarIcon)
			.panel(sidebarPanel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(bankInterfaceManager);
		eventBus.register(bankInterfaceManager);
		eventBus.register(itemTracker);
		eventBus.register(profileManager);
		eventBus.register(sidebarPanel);

		clientThread.invokeLater(() ->
		{
			bankInterfaceManager.startup();
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadProfile();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(bankInterfaceManager);
		eventBus.unregister(itemTracker);
		eventBus.unregister(profileManager);
		eventBus.unregister(sidebarPanel);

		overlayManager.remove(bankInterfaceManager);
		clientToolbar.removeNavigation(navButton);

		clientThread.invokeLater(() -> {
			bankInterfaceManager.shutdown();
			unloadProfile();
			sidebarPanel = null;
		});
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
		int[] trackedItemIDs = profileManager.readTrackedItemIDs();

		// login before loadItems to preserve the correct sidebar hint order in the event that the bank is already open.
		// The SyncWithBank message indirectly updates the hint message, which login would then overwrite.
		sidebarPanel.login();
		itemTracker.loadItems(trackedItemIDs);
		isProfileLoaded = true;
	}

	private void unloadProfile()
	{
		itemTracker.reset();
		sidebarPanel.logout();
		isProfileLoaded = false;
	}
}