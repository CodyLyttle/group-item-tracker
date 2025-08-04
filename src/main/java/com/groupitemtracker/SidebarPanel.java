package com.groupitemtracker;

import com.groupitemtracker.events.ItemAdded;
import com.groupitemtracker.events.ItemRemoved;
import com.groupitemtracker.events.ItemUpdated;
import java.awt.BorderLayout;
import java.util.Comparator;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

public class SidebarPanel extends PluginPanel
{
	private static final String LOGIN_HINT_LABEL = "Login to view your tracked items";
	private static final String TUTORIAL_HINT_LABEL = "Right-click bank item to track";

	private final Comparator<TrackedItem> itemComparer = Comparator.comparing(TrackedItem::getItemName);
	private final SortedItemsContainer<TrackedItem, SidebarItemPanel> claimedItemsContainer = new SortedItemsContainer<>(itemComparer);
	private final SortedItemsContainer<TrackedItem, SidebarItemPanel> unclaimedItemsContainer = new SortedItemsContainer<>(itemComparer);
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ItemTracker itemTracker;
	private final JLabel hintLabel;

	public SidebarPanel(ClientThread clientThread, ItemManager itemManager, ItemTracker itemTracker)
	{
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.itemTracker = itemTracker;

		final var header = new JLabel("Group Item Tracker");
		header.setFont(FontManager.getRunescapeFont());
		header.setHorizontalAlignment(SwingConstants.CENTER);
		hintLabel = new JLabel(LOGIN_HINT_LABEL);
		hintLabel.setFont(FontManager.getRunescapeSmallFont());
		hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
		final var headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerPanel.add(header, BorderLayout.NORTH);
		headerPanel.add(hintLabel, BorderLayout.SOUTH);

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setLayout(new DynamicGridLayout(3, 1, 0, 4));
		add(headerPanel, BorderLayout.NORTH);
		add(claimedItemsContainer, BorderLayout.CENTER);
		add(unclaimedItemsContainer, BorderLayout.CENTER);
	}

	public void login()
	{
		hintLabel.setText(TUTORIAL_HINT_LABEL);

		for (TrackedItem item : itemTracker.getItems())
		{
			final SidebarItemPanel itemPanel = createItemPanel(item);
			final var itemContainer = item.getTotalCount() > 0 ? claimedItemsContainer : unclaimedItemsContainer;
			itemContainer.addItem(item, itemPanel);
		}

		refreshItemContainers();
	}

	public void logout()
	{
		hintLabel.setText(LOGIN_HINT_LABEL);
		claimedItemsContainer.clearItems();
		unclaimedItemsContainer.clearItems();
		refreshItemContainers();
	}

	@Subscribe
	public void onItemAdded(ItemAdded event)
	{
		final TrackedItem item = event.getItem();
		final SidebarItemPanel itemPanel = createItemPanel(item);
		final var itemContainer = item.getTotalCount() > 0 ? claimedItemsContainer : unclaimedItemsContainer;
		itemContainer.addItem(item, itemPanel);
		itemContainer.revalidate();
		itemContainer.repaint();
	}

	@Subscribe
	private void onItemRemoved(ItemRemoved event)
	{
		final TrackedItem item = event.getItem();
		final var itemContainer = getItemContainerOrThrow(item);
		itemContainer.removeItem(item);
		itemContainer.revalidate();
		itemContainer.repaint();
	}

	// TODO: Batch refresh containers.
	@Subscribe
	private void onItemUpdated(ItemUpdated event)
	{
		final TrackedItem item = event.getItem();
		final var itemContainer = getItemContainerOrThrow(item);
		SidebarItemPanel itemPanel = itemContainer.getItemPanel(item);
		itemPanel.refresh();

		if (itemContainer == claimedItemsContainer && item.getTotalCount() == 0)
		{
			claimedItemsContainer.removeItem(item);
			unclaimedItemsContainer.addItem(item, itemPanel);
			refreshItemContainers();
		}
		else if (itemContainer == unclaimedItemsContainer && item.getTotalCount() > 0)
		{
			unclaimedItemsContainer.removeItem(item);
			claimedItemsContainer.addItem(item, itemPanel);
			refreshItemContainers();
		}
	}

	private SidebarItemPanel createItemPanel(TrackedItem item)
	{
		final AsyncBufferedImage image = itemManager.getImage(item.getItemID(), Integer.MAX_VALUE, false);
		return new SidebarItemPanel(clientThread, itemTracker, item, image);
	}

	private SortedItemsContainer<TrackedItem, SidebarItemPanel> getItemContainerOrThrow(TrackedItem item)
	{
		if (claimedItemsContainer.containsKey(item))
		{
			return claimedItemsContainer;
		}

		if (unclaimedItemsContainer.containsKey(item))
		{
			return unclaimedItemsContainer;
		}

		throw new IllegalArgumentException("An item panel doesn't exist for item: " + item.toString());
	}

	private void refreshItemContainers()
	{
		claimedItemsContainer.revalidate();
		claimedItemsContainer.repaint();
		unclaimedItemsContainer.revalidate();
		unclaimedItemsContainer.repaint();
	}
}