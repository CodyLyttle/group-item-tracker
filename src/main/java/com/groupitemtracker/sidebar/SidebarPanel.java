package com.groupitemtracker.sidebar;

import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.TrackedItem;
import com.groupitemtracker.events.ItemAdded;
import com.groupitemtracker.events.ItemRemoved;
import com.groupitemtracker.events.ItemUpdated;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

public class SidebarPanel extends PluginPanel
{
	private static final String LOGIN_HINT_LABEL = "Login to view your tracked items";
	private static final String TUTORIAL_HINT_LABEL = "Right-click bank item to track";
	private static final String INITIAL_SYNC_HINT_LABEL = "Open bank to finish syncing";

	private final ItemPanelContainer claimedItemsContainer;
	private final ItemPanelContainer unclaimedItemsContainer;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ItemTracker itemTracker;
	private final JLabel hintLabel;

	private boolean isSyncedWithBank;

	public SidebarPanel(ClientThread clientThread, ItemManager itemManager, ItemTracker itemTracker)
	{
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.itemTracker = itemTracker;

		final var headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		headerPanel.setBackground(ColorScheme.BORDER_COLOR);
		final var header = new JLabel("Group Item Tracker");
		header.setFont(FontManager.getRunescapeFont());
		header.setHorizontalAlignment(SwingConstants.CENTER);
		hintLabel = new JLabel(LOGIN_HINT_LABEL);
		hintLabel.setFont(FontManager.getRunescapeSmallFont());
		hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
		headerPanel.add(header, BorderLayout.NORTH);
		headerPanel.add(hintLabel, BorderLayout.SOUTH);

		claimedItemsContainer = new ItemPanelContainer("Claimed");
		claimedItemsContainer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		claimedItemsContainer.setVisible(false);
		unclaimedItemsContainer = new ItemPanelContainer("Unclaimed");
		unclaimedItemsContainer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		unclaimedItemsContainer.setVisible(false);

		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(headerPanel);
		add(claimedItemsContainer);
		add(unclaimedItemsContainer);
	}

	public void login(boolean isBankOpen)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (isBankOpen)
			{
				isSyncedWithBank = true;
				hintLabel.setText(TUTORIAL_HINT_LABEL);
			}
			else
			{
				isSyncedWithBank = false;
				hintLabel.setText(INITIAL_SYNC_HINT_LABEL);
			}

			for (TrackedItem item : itemTracker.getItems())
			{
				final ItemPanel itemPanel = createItemPanel(item);
				final var itemContainer = item.getTotalCount() > 0 ? claimedItemsContainer : unclaimedItemsContainer;
				itemContainer.addItemPanel(itemPanel);
			}

			claimedItemsContainer.refresh();
			unclaimedItemsContainer.refresh();
		});
	}

	public void logout()
	{
		SwingUtilities.invokeLater(() ->
		{
			hintLabel.setText(LOGIN_HINT_LABEL);

			claimedItemsContainer.clearItems();
			unclaimedItemsContainer.clearItems();

			claimedItemsContainer.refresh();
			unclaimedItemsContainer.refresh();
		});
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!isSyncedWithBank && event.getContainerId() == InventoryID.BANK)
		{
			isSyncedWithBank = true;
			SwingUtilities.invokeLater(() -> hintLabel.setText(TUTORIAL_HINT_LABEL));
		}
	}

	@Subscribe
	public void onItemAdded(ItemAdded event)
	{
		SwingUtilities.invokeLater(() ->
		{
			final TrackedItem item = event.getItem();
			final ItemPanel itemPanel = createItemPanel(item);
			final var itemContainer = item.getTotalCount() > 0 ? claimedItemsContainer : unclaimedItemsContainer;
			itemContainer.addItemPanel(itemPanel);
			itemContainer.refresh();
		});
	}

	@Subscribe
	private void onItemRemoved(ItemRemoved event)
	{
		SwingUtilities.invokeLater(() ->
		{
			final TrackedItem item = event.getItem();
			final var itemContainer = getItemContainerOrThrow(item);
			final var itemPanel = itemContainer.getItemPanel(item);
			itemContainer.removeItemPanel(itemPanel);
			itemContainer.refresh();
		});
	}

	// TODO: Check if initial bank sync with many claimed items results in performance issues.
	// Does Swing batch nearby calls to revalidate and repaint, or will updating 10x items at once result in 10x work?
	@Subscribe
	private void onItemUpdated(ItemUpdated event)
	{
		SwingUtilities.invokeLater(() ->
		{
			final TrackedItem item = event.getItem();
			final var itemContainer = getItemContainerOrThrow(item);
			final ItemPanel itemPanel = itemContainer.getItemPanel(item);

			// Guaranteed to change, regardless of whether it moves containers or not.
			itemPanel.refresh();

			if (itemContainer == claimedItemsContainer && item.getTotalCount() == 0)
			{
				claimedItemsContainer.removeItemPanel(itemPanel);
				unclaimedItemsContainer.addItemPanel(itemPanel);
				claimedItemsContainer.refresh();
				unclaimedItemsContainer.refresh();
			}
			else if (itemContainer == unclaimedItemsContainer && item.getTotalCount() > 0)
			{
				claimedItemsContainer.addItemPanel(itemPanel);
				unclaimedItemsContainer.removeItemPanel(itemPanel);
				claimedItemsContainer.refresh();
				unclaimedItemsContainer.refresh();
			}
		});
	}

	private ItemPanel createItemPanel(TrackedItem item)
	{
		final AsyncBufferedImage image = itemManager.getImage(item.getItemID(), Integer.MAX_VALUE, false);
		return new ItemPanel(clientThread, itemTracker, item, image);
	}

	private ItemPanelContainer getItemContainerOrThrow(TrackedItem item)
	{
		if (claimedItemsContainer.containsItem(item))
		{
			return claimedItemsContainer;
		}

		if (unclaimedItemsContainer.containsItem(item))
		{
			return unclaimedItemsContainer;
		}

		throw new IllegalArgumentException("An item panel doesn't exist for item: " + item.toString());
	}
}