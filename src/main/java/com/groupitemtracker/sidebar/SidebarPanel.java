package com.groupitemtracker.sidebar;

import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.TrackedItem;
import com.groupitemtracker.events.ItemAdded;
import com.groupitemtracker.events.ItemRemoved;
import com.groupitemtracker.events.ItemUpdated;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
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
	private static final Comparator<TrackedItem> ITEM_COMPARER = Comparator.comparing(TrackedItem::getItemName);
	private static final Color TEXT_COLOR_CLAIMED = ColorScheme.BRAND_ORANGE;
	private static final Color TEXT_COLOR_UNCLAIMED = ColorScheme.TEXT_COLOR;

	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ItemTracker itemTracker;
	private final TreeMap<TrackedItem, ItemPanel> claimedItems = new TreeMap<>(ITEM_COMPARER);
	private final TreeMap<TrackedItem, ItemPanel> unclaimedItems = new TreeMap<>(ITEM_COMPARER);

	private final JLabel hintLabel;
	private final JPanel trackedItemsPanel;
	private final JLabel trackedItemsLabel;
	private final JPanel itemPanelContainer;

	private boolean isSyncedWithBank;

	public SidebarPanel(ClientThread clientThread, ItemManager itemManager, ItemTracker itemTracker)
	{
		super(false);
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

		trackedItemsPanel = new JPanel(new BorderLayout());
		trackedItemsPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		final var trackedItemsHeaderPanel = new JPanel(new BorderLayout());
		trackedItemsHeaderPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		trackedItemsHeaderPanel.setBackground(ColorScheme.BORDER_COLOR);
		trackedItemsLabel = new JLabel();
		trackedItemsLabel.setHorizontalAlignment(SwingConstants.CENTER);
		trackedItemsLabel.setFont(FontManager.getRunescapeFont());
		trackedItemsHeaderPanel.add(trackedItemsLabel);
		itemPanelContainer = new JPanel(new GridLayout(0, 1, 0, 1));
		final var itemPanelContainerWrapper = new JPanel(new BorderLayout());
		itemPanelContainerWrapper.add(itemPanelContainer, BorderLayout.NORTH);
		final var itemScrollPane = new JScrollPane(itemPanelContainerWrapper);
		itemScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		itemScrollPane.setBackground(ColorScheme.BORDER_COLOR);
		trackedItemsPanel.add(trackedItemsHeaderPanel, BorderLayout.NORTH);
		trackedItemsPanel.add(itemScrollPane, BorderLayout.CENTER);

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		add(headerPanel, BorderLayout.NORTH);
		add(trackedItemsPanel, BorderLayout.CENTER);
		
		// Hidden until logged in.
		trackedItemsPanel.setVisible(false);

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
				addItemPanel(item);
			}

			trackedItemsPanel.setVisible(true);
			refreshTrackedItemsPanel();
		});
	}

	public void logout()
	{
		SwingUtilities.invokeLater(() ->
		{
			hintLabel.setText(LOGIN_HINT_LABEL);

			claimedItems.clear();
			unclaimedItems.clear();
			itemPanelContainer.removeAll();
			trackedItemsPanel.setVisible(false);
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
			addItemPanel(event.getItem());

			refreshTrackedItemsPanel();
		});
	}

	@Subscribe
	private void onItemRemoved(ItemRemoved event)
	{
		SwingUtilities.invokeLater(() ->
		{
			TrackedItem item = event.getItem();
			TreeMap<TrackedItem, ItemPanel> itemCollection = getCollectionForItem(item);
			ItemPanel itemPanel = itemCollection.remove(item);
			itemPanelContainer.remove(itemPanel);

			refreshTrackedItemsPanel();
		});
	}

	// TODO: Check if initial bank sync with many claimed items results in performance issues.
	// Does Swing batch nearby calls to revalidate and repaint, or will updating 10 items at once result in 10x work?
	@Subscribe
	private void onItemUpdated(ItemUpdated event)
	{
		SwingUtilities.invokeLater(() ->
		{
			final TrackedItem item = event.getItem();
			final TreeMap<TrackedItem, ItemPanel> itemCollection = getCollectionForItem(item);
			final ItemPanel itemPanel = itemCollection.get(item);

			// This is always updated.
			itemPanel.refresh();

			if (itemCollection == claimedItems && item.getTotalCount() == 0)
			{
				claimedItems.remove(item);
				unclaimedItems.put(item, itemPanel);
				int sortedIndex = claimedItems.size() + unclaimedItems.headMap(item).size();
				itemPanelContainer.remove(itemPanel);
				itemPanelContainer.add(itemPanel, sortedIndex);

				refreshTrackedItemsPanel();
			}
			else if (itemCollection == unclaimedItems && item.getTotalCount() > 0)
			{
				unclaimedItems.remove(item);
				claimedItems.put(item, itemPanel);
				int sortedIndex = claimedItems.headMap(item).size();
				itemPanelContainer.remove(itemPanel);
				itemPanelContainer.add(itemPanel, sortedIndex);

				refreshTrackedItemsPanel();
			}
		});
	}

	private void addItemPanel(TrackedItem item)
	{
		assert !(claimedItems.containsKey(item) || unclaimedItems.containsKey(item));

		final AsyncBufferedImage image = itemManager.getImage(item.getItemID(), Integer.MAX_VALUE, false);
		ItemPanel itemPanel = new ItemPanel(clientThread, itemTracker, item, image);

		TreeMap<TrackedItem, ItemPanel> itemCollection = item.getTotalCount() > 0 ? claimedItems : unclaimedItems;
		itemCollection.put(item, itemPanel);

		int sortedIndex = itemCollection.headMap(item).size();
		if (itemCollection == unclaimedItems)
		{
			// Claimed items come first.
			sortedIndex += claimedItems.size();
		}

		itemPanelContainer.add(itemPanel, sortedIndex);
	}

	private TreeMap<TrackedItem, ItemPanel> getCollectionForItem(TrackedItem item)
	{
		if (claimedItems.containsKey(item))
		{
			return claimedItems;
		}

		assert unclaimedItems.containsKey(item);
		return unclaimedItems;
	}

	private void refreshTrackedItemsPanel()
	{
		int claimed = claimedItems.size();
		int total = claimed + unclaimedItems.size();
		trackedItemsLabel.setText("Claimed (" + claimed + "/" + total + ")");
		trackedItemsLabel.setForeground(claimed > 0 ? TEXT_COLOR_CLAIMED : TEXT_COLOR_UNCLAIMED);

		trackedItemsPanel.revalidate();
		trackedItemsPanel.repaint();
	}
}