package com.groupitemtracker.sidebar;

import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.TrackedItemSnapshot;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

// Profiled 100 tracked items (70% claimed).
// Memory dump:
// - 88kB shallow (explicit plugin allocations).
// - 1.3MB retained (primarily Swing components).
// System.nanoTime() measurements:
// - onInvalidated ~30ms to build entire list of item panels.
// - onItemAdded <1ms via right click menu.
// - onItemRemoved <1ms via right-click menu, ~1ms via delete button with occasional spike of ~10ms.
// - onItemsUpdated <1ms when depositing a full inventory of tracked items into the shared bank.
// Updating the Swing hierarchy seems slow, maybe try allocating items in batches and hiding them by default?
public final class SidebarPanel extends PluginPanel
{
	private static final class ItemPanelEntry
	{
		public TrackedItemSnapshot snapshot;
		public final TrackedItemPanel panel;

		public ItemPanelEntry(TrackedItemSnapshot snapshot, TrackedItemPanel panel)
		{
			this.snapshot = snapshot;
			this.panel = panel;
		}
	}

	private static final String LOGIN_HINT_LABEL = "Login to view your tracked items";
	private static final String TUTORIAL_HINT_LABEL = "Right-click bank item to track";
	private static final String INITIAL_SYNC_HINT_LABEL = "Open bank to finish syncing";

	// Claimed items in alphabetical order, followed by unclaimed items in alphabetical order.
	private static final Comparator<ItemPanelEntry> ENTRY_COMPARER = Comparator
		.comparing((ItemPanelEntry entry) -> entry.snapshot.locationMask == 0)
		.thenComparing((entry -> entry.snapshot.name));

	private final List<ItemPanelEntry> sortedEntries = new ArrayList<>();
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ItemTracker itemTracker;
	private final JLabel hintLabel;
	private final JPanel itemsGrid;

	public SidebarPanel(ClientThread clientThread, ItemManager itemManager, ItemTracker itemTracker)
	{
		// Disable scrolling of the top level panel.
		super(false);

		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.itemTracker = itemTracker;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		JPanel titlePanel = createHeaderPanel();
		JLabel titleLabel = createHeaderLabel("Group Item Tracker", FontManager.getRunescapeFont());
		this.hintLabel = createHeaderLabel(LOGIN_HINT_LABEL, FontManager.getRunescapeSmallFont());
		titlePanel.add(titleLabel, BorderLayout.NORTH);
		titlePanel.add(hintLabel, BorderLayout.SOUTH);

		// Vertical stack panel of tracked items.
		this.itemsGrid = new JPanel(new GridLayout(0, 1, 0, 1));
		// Prevent scroll pane from vertically stretching grid items.
		var wrapper = new JPanel(new BorderLayout());
		wrapper.add(itemsGrid, BorderLayout.NORTH);
		// Vertical scrolling for overflowing items.
		var scrollPane = new JScrollPane(wrapper);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR));

		add(titlePanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
	}

	private JPanel createHeaderPanel()
	{
		var panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		panel.setBackground(ColorScheme.BORDER_COLOR);
		return panel;
	}

	private JLabel createHeaderLabel(String text, Font font)
	{
		var label = new JLabel(text);
		label.setFont(font);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		return label;
	}

	public void login()
	{
		SwingUtilities.invokeLater(() -> hintLabel.setText(INITIAL_SYNC_HINT_LABEL));
	}

	public void logout()
	{
		SwingUtilities.invokeLater(() -> hintLabel.setText(LOGIN_HINT_LABEL));
	}

	@Subscribe
	private void onSyncedWithBank(ItemTracker.SyncedWithBank event)
	{
		SwingUtilities.invokeLater(() -> hintLabel.setText(TUTORIAL_HINT_LABEL));
	}

	@Subscribe
	private void onInvalidated(ItemTracker.Invalidated event)
	{
		SwingUtilities.invokeLater(() -> {
			sortedEntries.clear();
			for (var info : event.getItems())
			{
				var entry = createItemPanelEntry(info);
				sortedEntries.add(entry);
			}
			sortedEntries.sort(ENTRY_COMPARER);

			itemsGrid.removeAll();
			for (var entry : sortedEntries)
			{
				itemsGrid.add(entry.panel);
			}

			refreshSidebar();
		});
	}

	@Subscribe
	private void onItemAdded(ItemTracker.ItemAdded event)
	{
		SwingUtilities.invokeLater(() ->
		{
			var entry = createItemPanelEntry(event.getItem());
			sortedEntries.add(entry);
			sortedEntries.sort(ENTRY_COMPARER);
			itemsGrid.add(entry.panel, sortedEntries.indexOf(entry));

			refreshSidebar();
		});
	}

	@Subscribe
	private void onItemRemoved(ItemTracker.ItemRemoved event)
	{
		SwingUtilities.invokeLater(() ->
		{
			var itemID = event.getItem().itemID;
			int index = getIndexByItemID(itemID);
			assert (index != -1);

			sortedEntries.remove(index);
			itemsGrid.remove(index);
			refreshSidebar();
		});
	}

	@Subscribe
	private void onItemsUpdated(ItemTracker.ItemsUpdated event)
	{
		SwingUtilities.invokeLater(() ->
		{
			for (var item : event.getItems())
			{
				var index = getIndexByItemID(item.itemID);
				var entry = sortedEntries.get(index);
				var wasClaimed = entry.snapshot.locationMask != 0;
				var isClaimed = item.locationMask != 0;

				entry.snapshot = item;
				entry.panel.updateState(item);

				// Items only require sorting when changing between claimed and unclaimed states.
				// This is an uncommon event caused by:
				// - Depositing or withdrawing tracked items from group storage.
				// - Depositing tracked items via deposit box.
				// - Dropping or picking up tracked items.
				if (wasClaimed != isClaimed)
				{
					// List.sort is optimized for nearly sorted lists, making this very cheap.
					sortedEntries.sort(ENTRY_COMPARER);
					index = getIndexByItemID(item.itemID);
					itemsGrid.setComponentZOrder(entry.panel, index);
				}
			}
			refreshSidebar();
		});
	}

	private int getIndexByItemID(int itemID)
	{
		for (int i = 0; i < sortedEntries.size(); i++)
		{
			if (sortedEntries.get(i).snapshot.itemID == itemID)
			{
				return i;
			}
		}

		// We should never be querying an entry that doesn't exist.
		assert (false);
		return -1;
	}

	private ItemPanelEntry createItemPanelEntry(TrackedItemSnapshot item)
	{
		int id = item.itemID;
		var image = itemManager.getImage(id, Integer.MAX_VALUE, false);
		var panel = new TrackedItemPanel(item, image, (e) -> clientThread.invoke(() -> itemTracker.stopTracking(id)));
		return new ItemPanelEntry(item, panel);
	}

	private void refreshSidebar()
	{
		revalidate();
		repaint();
	}
}