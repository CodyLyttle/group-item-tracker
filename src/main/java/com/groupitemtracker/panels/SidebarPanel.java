package com.groupitemtracker.panels;

import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.TrackedItem;
import com.groupitemtracker.events.ItemAdded;
import com.groupitemtracker.events.ItemRemoved;
import com.groupitemtracker.events.ItemUpdated;
import java.awt.BorderLayout;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
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

	private final ItemPanelContainer claimedItemsContainer = new ItemPanelContainer("Claimed Items");
	private final ItemPanelContainer unclaimedItemsContainer = new ItemPanelContainer("Unclaimed Items");
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
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(headerPanel, BorderLayout.NORTH);
		add(claimedItemsContainer);
		add(unclaimedItemsContainer);
	}

	public void login()
	{
		hintLabel.setText(TUTORIAL_HINT_LABEL);

		for (TrackedItem item : itemTracker.getItems())
		{
			final ItemPanel itemPanel = createItemPanel(item);
			final var itemContainer = item.getTotalCount() > 0 ? claimedItemsContainer : unclaimedItemsContainer;
			itemContainer.addItemPanel(itemPanel);
		}

		refreshItemContainer(claimedItemsContainer);
		refreshItemContainer(unclaimedItemsContainer);
	}

	public void logout()
	{
		hintLabel.setText(LOGIN_HINT_LABEL);
		claimedItemsContainer.clearItems();
		unclaimedItemsContainer.clearItems();
		refreshItemContainer(claimedItemsContainer);
		refreshItemContainer(unclaimedItemsContainer);
	}

	@Subscribe
	public void onItemAdded(ItemAdded event)
	{
		final TrackedItem item = event.getItem();
		final ItemPanel itemPanel = createItemPanel(item);
		final var itemContainer = item.getTotalCount() > 0 ? claimedItemsContainer : unclaimedItemsContainer;
		itemContainer.addItemPanel(itemPanel);
		refreshItemContainer(itemContainer);
	}

	@Subscribe
	private void onItemRemoved(ItemRemoved event)
	{
		final TrackedItem item = event.getItem();
		final var itemContainer = getItemContainerOrThrow(item);
		final var itemPanel = itemContainer.getItemPanel(item);
		itemContainer.removeItemPanel(itemPanel);
		refreshItemContainer(itemContainer);
	}

	// TODO: Batch refresh containers.
	@Subscribe
	private void onItemUpdated(ItemUpdated event)
	{
		final TrackedItem item = event.getItem();
		final var itemContainer = getItemContainerOrThrow(item);
		final ItemPanel itemPanel = itemContainer.getItemPanel(item);
		itemPanel.refresh();

		if (itemContainer == claimedItemsContainer && item.getTotalCount() == 0)
		{
			claimedItemsContainer.removeItemPanel(itemPanel);
			unclaimedItemsContainer.addItemPanel(itemPanel);
			refreshItemContainer(claimedItemsContainer);
			refreshItemContainer(unclaimedItemsContainer);
		}
		else if (itemContainer == unclaimedItemsContainer && item.getTotalCount() > 0)
		{
			unclaimedItemsContainer.removeItemPanel(itemPanel);
			claimedItemsContainer.addItemPanel(itemPanel);
			refreshItemContainer(claimedItemsContainer);
			refreshItemContainer(unclaimedItemsContainer);
		}
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

	private void refreshItemContainer(ItemPanelContainer container)
	{
		container.setVisible(container.getItemCount() > 0);
		container.revalidate();
		container.repaint();
	}
}