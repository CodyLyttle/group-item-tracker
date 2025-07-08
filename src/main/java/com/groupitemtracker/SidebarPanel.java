package com.groupitemtracker;

import com.groupitemtracker.events.ItemAdded;
import com.groupitemtracker.events.ItemRemoved;
import com.groupitemtracker.events.ItemUpdated;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.TreeMap;
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

	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ItemTracker itemTracker;
	private final JPanel itemContainer;
	private final JLabel hintLabel;
	private final TreeMap<TrackedItem, SidebarItemPanel> itemPanelLookup = new TreeMap<>(Comparator.comparing(TrackedItem::getItemName));

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

		itemContainer = new JPanel(new GridLayout(0, 1, 0, 4));

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setLayout(new DynamicGridLayout(2, 1, 0, 4));
		add(headerPanel, BorderLayout.NORTH);
		add(itemContainer, BorderLayout.CENTER);
	}

	public void login()
	{
		hintLabel.setText(TUTORIAL_HINT_LABEL);
		for (TrackedItem item : itemTracker.getItems())
		{
			addItem(item);
		}

		itemContainer.revalidate();
		itemContainer.repaint();
	}

	public void logout()
	{
		hintLabel.setText(LOGIN_HINT_LABEL);
		itemContainer.removeAll();
		itemPanelLookup.clear();
	}

	private void addItem(TrackedItem item)
	{
		final AsyncBufferedImage image = itemManager.getImage(item.getItemID(), Integer.MAX_VALUE, false);
		final var itemPanel = new SidebarItemPanel(clientThread, itemTracker, item, image);
		itemPanelLookup.put(item, itemPanel);
		final int sortedIndex = itemPanelLookup.headMap(item).size();
		itemContainer.add(itemPanel, sortedIndex);
	}

	@Subscribe
	public void onItemAdded(ItemAdded event)
	{
		TrackedItem item = event.getItem();
		if (itemPanelLookup.containsKey(item))
		{
			throw new IllegalArgumentException("Panel already exists for item: " + item.getItemName());
		}

		addItem(item);
		itemContainer.revalidate();
		itemContainer.repaint();
	}

	@Subscribe
	private void onItemRemoved(ItemRemoved event)
	{
		TrackedItem item = event.getItem();
		final SidebarItemPanel removed = itemPanelLookup.remove(item);
		if (removed == null)
		{
			throw new IllegalArgumentException("Panel doesn't exist for item: " + item.getItemName());
		}

		itemContainer.remove(removed);
		itemContainer.revalidate();
		itemContainer.repaint();
	}

	@Subscribe
	private void onItemUpdated(ItemUpdated event)
	{
		TrackedItem item = event.getItem();
		final SidebarItemPanel itemPanel = itemPanelLookup.get(item);
		if (itemPanel == null)
		{
			throw new IllegalArgumentException("Panel doesn't exist for item: " + item.getItemName());
		}

		itemPanel.refresh();
	}
}