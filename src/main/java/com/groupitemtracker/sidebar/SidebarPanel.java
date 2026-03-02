package com.groupitemtracker.sidebar;

import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.TrackedItem;
import com.groupitemtracker.events.ItemAdded;
import com.groupitemtracker.events.ItemRemoved;
import com.groupitemtracker.events.ItemUpdated;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.TreeMap;
import javax.swing.BorderFactory;
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
	private static final String DEFAULT_TRACKED_ITEMS_TEXT = "Tracked Items";
	private static final Comparator<TrackedItem> ITEM_COMPARER = Comparator.comparing(TrackedItem::getItemName);

	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ItemTracker itemTracker;
	private final TreeMap<TrackedItem, TrackedItemPanel> claimedItems = new TreeMap<>(ITEM_COMPARER);
	private final TreeMap<TrackedItem, TrackedItemPanel> unclaimedItems = new TreeMap<>(ITEM_COMPARER);
	private final JLabel hintLabel;
	private final JPanel itemsPanel;
	private final JLabel itemsHeaderLabel;
	private final JPanel itemsGrid;

	private boolean isSyncedWithBank;

	public SidebarPanel(ClientThread clientThread, ItemManager itemManager, ItemTracker itemTracker)
	{
		// Disable scrolling of the top level panel.
		super(false);
		
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.itemTracker = itemTracker;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		final var titlePanel = createHeaderPanel();
		final var titleLabel = createHeaderLabel("Group Item Tracker", FontManager.getRunescapeFont());
		hintLabel = createHeaderLabel(LOGIN_HINT_LABEL, FontManager.getRunescapeSmallFont());
		titlePanel.add(titleLabel, BorderLayout.NORTH);
		titlePanel.add(hintLabel, BorderLayout.SOUTH);

		itemsPanel = new JPanel(new BorderLayout());
		itemsPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		final var itemsHeaderPanel = createHeaderPanel();
		itemsHeaderLabel = createHeaderLabel(DEFAULT_TRACKED_ITEMS_TEXT, FontManager.getRunescapeFont());
		itemsHeaderPanel.add(itemsHeaderLabel);
		// Grid holds all TrackedItemPanel elements.
		itemsGrid = new JPanel(new GridLayout(0, 1, 0, 1));
		// Wrapper prevents scroll pane from stretching grid elements vertically.
		final var gridWrapper = new JPanel(new BorderLayout());
		gridWrapper.add(itemsGrid, BorderLayout.NORTH);
		// Scrollable grid overflow when elements exceed available sidebar height.
		final var itemsScrollPane = new JScrollPane(gridWrapper);
		itemsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		itemsScrollPane.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR));

		itemsPanel.add(itemsHeaderPanel, BorderLayout.NORTH);
		itemsPanel.add(itemsScrollPane, BorderLayout.CENTER);

		add(titlePanel, BorderLayout.NORTH);
		add(itemsPanel, BorderLayout.CENTER);

		// Hidden until logged in.
		itemsPanel.setVisible(false);
	}

	private JPanel createHeaderPanel()
	{
		final var panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		panel.setBackground(ColorScheme.BORDER_COLOR);
		return panel;
	}

	private JLabel createHeaderLabel(String text, Font font)
	{
		final var label = new JLabel(text);
		label.setFont(font);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		return label;
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

			itemsPanel.setVisible(true);
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
			itemsGrid.removeAll();
			itemsPanel.setVisible(false);
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
			final TrackedItem item = event.getItem();
			final TreeMap<TrackedItem, TrackedItemPanel> itemCollection = getCollectionForItem(item);
			final TrackedItemPanel trackedItemPanel = itemCollection.remove(item);
			itemsGrid.remove(trackedItemPanel);

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
			final TreeMap<TrackedItem, TrackedItemPanel> itemCollection = getCollectionForItem(item);
			final TrackedItemPanel trackedItemPanel = itemCollection.get(item);

			// This is always updated.
			trackedItemPanel.refresh();

			if (itemCollection == claimedItems && item.getTotalCount() == 0)
			{
				claimedItems.remove(item);
				unclaimedItems.put(item, trackedItemPanel);
				final int sortedIndex = claimedItems.size() + unclaimedItems.headMap(item).size();
				itemsGrid.remove(trackedItemPanel);
				itemsGrid.add(trackedItemPanel, sortedIndex);
				refreshTrackedItemsPanel();
			}
			else if (itemCollection == unclaimedItems && item.getTotalCount() > 0)
			{
				unclaimedItems.remove(item);
				claimedItems.put(item, trackedItemPanel);
				final int sortedIndex = claimedItems.headMap(item).size();
				itemsGrid.remove(trackedItemPanel);
				itemsGrid.add(trackedItemPanel, sortedIndex);
				refreshTrackedItemsPanel();
			}
		});
	}

	private void addItemPanel(TrackedItem item)
	{
		assert !(claimedItems.containsKey(item) || unclaimedItems.containsKey(item));

		final AsyncBufferedImage image = itemManager.getImage(item.getItemID(), Integer.MAX_VALUE, false);
		TrackedItemPanel trackedItemPanel = new TrackedItemPanel(clientThread, itemTracker, item, image);

		final TreeMap<TrackedItem, TrackedItemPanel> itemCollection = item.getTotalCount() > 0 ? claimedItems : unclaimedItems;
		itemCollection.put(item, trackedItemPanel);

		int sortedIndex = itemCollection.headMap(item).size();
		if (itemCollection == unclaimedItems)
		{
			// Claimed items come first.
			sortedIndex += claimedItems.size();
		}

		itemsGrid.add(trackedItemPanel, sortedIndex);
	}

	// Note: Only call this for items that are guaranteed to be in a collection.
	private TreeMap<TrackedItem, TrackedItemPanel> getCollectionForItem(TrackedItem item)
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
		final int claimedItemCount = claimedItems.size();
		if (claimedItemCount == 0)
		{
			itemsHeaderLabel.setText(DEFAULT_TRACKED_ITEMS_TEXT);
			itemsHeaderLabel.setForeground(ColorScheme.TEXT_COLOR);
		}
		else
		{
			itemsHeaderLabel.setText(DEFAULT_TRACKED_ITEMS_TEXT + " (" + claimedItemCount + ")");
			itemsHeaderLabel.setForeground(ColorScheme.BRAND_ORANGE);
		}

		itemsPanel.revalidate();
		itemsPanel.repaint();
	}
}