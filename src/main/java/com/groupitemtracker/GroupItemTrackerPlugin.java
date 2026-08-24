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
import net.runelite.client.events.ConfigChanged;
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
	private GroupItemTrackerConfig config;

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

	@Inject
	private ConfigManager configManager;

	private NavigationButton navButton;
	private SidebarPanel sidebar;
	private BufferedImage sidebarIcon;
	private boolean isProfileLoaded;

	@Provides
	GroupItemTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroupItemTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		sidebarIcon = ImageUtil.loadImageResource(getClass(), "sidebar_icon.png");
		if (config.showSidebar())
		{
			initSidebar();
		}

		overlayManager.add(bankInterfaceManager);
		eventBus.register(bankInterfaceManager);
		eventBus.register(itemTracker);

		clientThread.invokeLater(() ->
		{
			bankInterfaceManager.refreshConfig(config);
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadProfile();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		isProfileLoaded = false;
		sidebarIcon = null;
		tryDestroySidebar();

		overlayManager.remove(bankInterfaceManager);
		eventBus.unregister(bankInterfaceManager);
		eventBus.unregister(itemTracker);

		clientThread.invokeLater(() -> {
			bankInterfaceManager.freeExcessMemory();
			itemTracker.reset();
			itemTracker.freeExcessMemory();
		});
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		if (isProfileLoaded && event.getGameState() == GameState.LOGIN_SCREEN)
		{
			itemTracker.reset();
			isProfileLoaded = false;
			if (sidebar != null)
			{
				sidebar.logout();
			}
		}
		else if (!isProfileLoaded && event.getGameState() == GameState.LOADING)
		{
			loadProfile();
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(GroupItemTrackerConfig.GROUP))
		{
			return;
		}

		switch (event.getKey())
		{
			case GroupItemTrackerConfig.KEY_BANK_FILTER:
			case GroupItemTrackerConfig.KEY_BANK_OUTLINE_COLOR:
			case GroupItemTrackerConfig.KEY_BANK_OUTLINE_MODE:
			case GroupItemTrackerConfig.KEY_EDIT_MODE_ACTIVE:
				bankInterfaceManager.refreshConfig(config);
				break;
			case GroupItemTrackerConfig.KEY_SHOW_TUTORIAL:
			{
				if (sidebar != null)
				{
					sidebar.setTutorialPanelVisible(config.showTutorial());
				}
				break;
			}
			case GroupItemTrackerConfig.KEY_SIDEBAR_PRIORITY:
			{
				// This setting is likely to be tinkered with repeatedly, keep the sidebar to avoid unnecessary work.
				if (sidebar != null)
				{
					clientToolbar.removeNavigation(navButton);
					navButton = buildNavButton(sidebarIcon, sidebar, config.sidebarPriority());
					clientToolbar.addNavigation(navButton);
				}
				break;
			}
			case GroupItemTrackerConfig.KEY_SHOW_SIDEBAR:
			{
				// The user shouldn't incur the cost of something they aren't using, alloc/dealloc instead of hiding.
				// Parse value so that null == false, rather than null == config.showSidebar default value.
				// This prevents sidebar panel duplication upon resetting the config value.
				var showSidebar = Boolean.parseBoolean(event.getNewValue());
				if (showSidebar)
				{
					initSidebar();
					if (client.getGameState() == GameState.LOGGED_IN)
					{
						sidebar.loginAndSyncWithItemTracker();
					}
				}
				else
				{
					tryDestroySidebar();
				}
				break;
			}
		}
	}

	@Subscribe
	private void onItemAdded(ItemTracker.ItemAdded event)
	{
		int[] ids = itemTracker.exportItemIDs();
		profileManager.writeItemIDs(ids);
	}

	@Subscribe
	private void onItemRemoved(ItemTracker.ItemRemoved event)
	{
		int[] ids = itemTracker.exportItemIDs();
		profileManager.writeItemIDs(ids);
	}

	private void loadProfile()
	{
		int[] trackedItemIDs = profileManager.readItemIDs();
		itemTracker.loadItems(trackedItemIDs);
		isProfileLoaded = true;
		if (sidebar != null)
		{
			sidebar.login();
		}
	}

	private NavigationButton buildNavButton(BufferedImage icon, SidebarPanel panel, int priority)
	{
		return NavigationButton.builder()
			.tooltip("Group Item Tracker")
			.icon(icon)
			.panel(panel)
			.priority(priority)
			.build();
	}

	private void initSidebar()
	{
		sidebar = new SidebarPanel(configManager, profileManager, clientThread, itemManager, itemTracker, sidebarIcon);
		sidebar.setTutorialPanelVisible(config.showTutorial());
		eventBus.register(sidebar);
		navButton = buildNavButton(sidebarIcon, sidebar, config.sidebarPriority());
		clientToolbar.addNavigation(navButton);
	}

	private void tryDestroySidebar()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}

		if (sidebar != null)
		{
			eventBus.unregister(sidebar);
			sidebar = null;
		}
	}
}