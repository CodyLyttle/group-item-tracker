package com.groupitemtracker.sidebar;

import com.groupitemtracker.GroupItemTrackerPlugin;
import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.ProfileManager;
import com.groupitemtracker.TrackedItemSnapshot;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.SwingUtil;

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

	private static final String HELP_URL = "https://runelite.net/plugin-hub/show/group-item-tracker";
	private static final String LOGGED_OUT_HINT = "Logged out";
	private static final String BANK_SYNC_HINT = "Visit bank to finalize";
	private static final String PLUGIN_NAME = "Group Item Tracker";
	private static final ImageIcon EXPORT_ICON;
	private static final ImageIcon IMPORT_ICON;
	private static final ImageIcon HELP_ICON;

	static
	{
		int sz = 24;

		EXPORT_ICON = new ImageIcon(
			ImageUtil.loadImageResource(GroupItemTrackerPlugin.class, "export_icon.png")
				.getScaledInstance(sz, sz, Image.SCALE_SMOOTH));

		IMPORT_ICON = new ImageIcon(
			ImageUtil.loadImageResource(GroupItemTrackerPlugin.class, "import_icon.png")
				.getScaledInstance(sz, sz, Image.SCALE_SMOOTH));

		HELP_ICON = new ImageIcon(
			ImageUtil.loadImageResource(GroupItemTrackerPlugin.class, "help_icon.png")
				.getScaledInstance(sz, sz, Image.SCALE_SMOOTH));
	}

	// Claimed items in alphabetical order, followed by unclaimed items in alphabetical order.
	private static final Comparator<ItemPanelEntry> ENTRY_COMPARER = Comparator
		.comparing((ItemPanelEntry entry) -> entry.snapshot.locationMask == 0)
		.thenComparing((entry -> entry.snapshot.name));

	private final List<ItemPanelEntry> sortedEntries = new ArrayList<>();
	private final ClientThread clientThread;
	private final ProfileManager profileManager;
	private final ItemManager itemManager;
	private final ItemTracker itemTracker;
	private final JLabel hintLabel;
	private final JPanel itemsGrid;
	private final JButton importButton;
	private final JButton exportButton;

	public SidebarPanel(ClientThread clientThread, ItemManager itemManager, ItemTracker itemTracker, ProfileManager profileManager)
	{
		// Disable scrolling of the top level panel.
		super(false);

		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.itemTracker = itemTracker;
		this.profileManager = profileManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		var headerPanelColor = ColorScheme.DARKER_GRAY_COLOR;
		var headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 7));
		headerPanel.setBackground(headerPanelColor);

		var textPanel = new JPanel(new BorderLayout());
		textPanel.setBackground(headerPanelColor);
		var titleLabel = new JLabel(PLUGIN_NAME);
		titleLabel.setFont(FontManager.getRunescapeFont());
		titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
		this.hintLabel = new JLabel(LOGGED_OUT_HINT);
		hintLabel.setFont(FontManager.getRunescapeSmallFont());
		hintLabel.setHorizontalAlignment(SwingConstants.LEFT);
		textPanel.add(titleLabel, BorderLayout.NORTH);
		textPanel.add(hintLabel, BorderLayout.SOUTH);

		var buttonsGrid = new JPanel(new GridLayout(1, 3));
		buttonsGrid.setMinimumSize(new Dimension(Integer.MAX_VALUE, 0));
		buttonsGrid.setBackground(headerPanelColor);
		this.exportButton = createFooterButton("Export to clipboard", EXPORT_ICON, this::exportItemsToClipboard);
		this.importButton = createFooterButton("Import from clipboard", IMPORT_ICON, this::importItemsFromClipboard);
		var helpButton = createFooterButton("Open help page", HELP_ICON, (ActionEvent e) -> LinkBrowser.browse(HELP_URL));
		// Begin in logged-out state.
		importButton.setEnabled(false);
		exportButton.setEnabled(false);
		buttonsGrid.add(exportButton);
		buttonsGrid.add(importButton);
		buttonsGrid.add(helpButton);

		headerPanel.add(textPanel, BorderLayout.CENTER);
		headerPanel.add(buttonsGrid, BorderLayout.EAST);

		// Vertical stack panel of tracked items.
		this.itemsGrid = new JPanel(new GridLayout(0, 1, 0, 1));
		// Prevent scroll pane from vertically stretching grid items.
		var wrapper = new JPanel(new BorderLayout());
		wrapper.add(itemsGrid, BorderLayout.NORTH);
		// Vertical scrolling for overflowing items.
		var scrollPane = new JScrollPane(wrapper);
		scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		add(headerPanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
	}

	private JButton createFooterButton(String text, ImageIcon icon, ActionListener onClick)
	{
		var button = new JButton(icon);
		button.setToolTipText(text);
		button.addActionListener(onClick);
		button.setPreferredSize(new Dimension(30, 30));
		SwingUtil.removeButtonDecorations(button);
		return button;
	}

	private void exportItemsToClipboard(ActionEvent event)
	{
		var selection = new StringSelection(profileManager.readItemIDsAsJson());
		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(selection, null);

		// hintLabel as parent puts the messagebox in a convenient position.
		JOptionPane.showMessageDialog(
			hintLabel, "Successfully exported items to the clipboard.",
			PLUGIN_NAME, JOptionPane.INFORMATION_MESSAGE);
	}

	private void importItemsFromClipboard(ActionEvent event)
	{
		var result = JOptionPane.showConfirmDialog(
			hintLabel, "Imported items will replace all existing items, are you sure?",
			PLUGIN_NAME, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

		if (result != JOptionPane.YES_OPTION)
		{
			return;
		}

		try
		{
			String text = Toolkit.getDefaultToolkit()
				.getSystemClipboard()
				.getData(DataFlavor.stringFlavor)
				.toString()
				.trim();

			if (profileManager.tryWriteItemIDsFromJson(text))
			{
				int[] ids = profileManager.readItemIDs();
				clientThread.invokeLater(() -> itemTracker.loadItems(ids));

				JOptionPane.showMessageDialog(
					hintLabel, "Successfully imported items from the clipboard.",
					PLUGIN_NAME, JOptionPane.INFORMATION_MESSAGE);

				return;
			}
			// Failed.
		}
		catch (IOException | UnsupportedFlavorException ignored)
		{
			// Failed.
		}

		JOptionPane.showMessageDialog(
			hintLabel, "Import failed: invalid format.",
			PLUGIN_NAME, JOptionPane.ERROR_MESSAGE);
	}

	public void login()
	{
		final boolean isSynced = itemTracker.isSyncedWithBank();
		SwingUtilities.invokeLater(() -> {
			importButton.setEnabled(true);
			exportButton.setEnabled(true);
			if (!isSynced)
			{
				hintLabel.setText(BANK_SYNC_HINT);
			}
		});
	}

	public void logout()
	{
		SwingUtilities.invokeLater(() -> {
			importButton.setEnabled(false);
			exportButton.setEnabled(false);
			hintLabel.setText(LOGGED_OUT_HINT);
		});
	}

	public void syncWithItemTracker()
	{
		// Copy the collection before crossing the thread-boundary to avoid ConcurrentModificationException.
		final var items = itemTracker.getItems().toArray(TrackedItemSnapshot[]::new);

		SwingUtilities.invokeLater(() -> {
			for (var item : items)
			{
				var entry = createItemPanelEntry(item);
				sortedEntries.add(entry);
			}

			sortedEntries.sort(ENTRY_COMPARER);
			for (var entry : sortedEntries)
			{
				itemsGrid.add(entry.panel);
			}

			updateLoggedInHint();
			refreshSidebar();
		});
	}

	@Subscribe
	private void onSyncedWithBank(ItemTracker.SyncedWithBank event)
	{
		SwingUtilities.invokeLater(() -> hintLabel.setText(createItemCountString()));
	}

	@Subscribe
	private void onInvalidated(ItemTracker.Invalidated event)
	{
		SwingUtilities.invokeLater(() -> {
			sortedEntries.clear();
			for (var item : event.getItems())
			{
				var entry = createItemPanelEntry(item);
				sortedEntries.add(entry);
			}

			sortedEntries.sort(ENTRY_COMPARER);
			itemsGrid.removeAll();
			for (var entry : sortedEntries)
			{
				itemsGrid.add(entry.panel);
			}

			updateLoggedInHint();
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

			updateLoggedInHint();
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

			sortedEntries.remove(index);
			itemsGrid.remove(index);

			updateLoggedInHint();
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

	private String createItemCountString()
	{
		int n = sortedEntries.size();
		return "Tracking " + n + (n == 1 ? " item" : " items");
	}

	private void updateLoggedInHint()
	{
		var hint = itemTracker.isSyncedWithBank()
			? createItemCountString()
			: BANK_SYNC_HINT;

		hintLabel.setText(hint);
	}

	private void refreshSidebar()
	{
		revalidate();
		repaint();
	}
}