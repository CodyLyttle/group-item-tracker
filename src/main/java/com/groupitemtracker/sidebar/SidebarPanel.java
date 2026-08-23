package com.groupitemtracker.sidebar;

import com.groupitemtracker.GroupItemTrackerConfig;
import com.groupitemtracker.GroupItemTrackerPlugin;
import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.ProfileManager;
import com.groupitemtracker.TrackedItemSnapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ColorUtil;
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
	private static final String LOGGED_OUT_HINT = "<html>Log in to view<br/>tracked items</html>";
	private static final String BANK_SYNC_HINT = "<html>Open bank to<br/>finish syncing</html>";
	private static final String PLUGIN_NAME = "Group Item Tracker";
	private static final ImageIcon EXPORT_ICON;
	private static final ImageIcon IMPORT_ICON;
	private static final ImageIcon HELP_ICON;

	// Copied from Loot Tracker & XP Tracker.
	private static final String HTML_COLOR = ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR);
	private static final String HTML_TEMPLATE_TRACKER_INFO = "<html><body>" +
		"<span style='color:" + HTML_COLOR + "'>Tracked: </span><span style='color:white'>%s</span><br>" +
		"<span style='color:" + HTML_COLOR + "'>Claimed: </span><span style='color:white'>%s</span>" +
		"</body></html>";

	static
	{
		int sz = 22;

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
	private final ConfigManager configManager;
	private final ClientThread clientThread;
	private final ProfileManager profileManager;
	private final ItemManager itemManager;
	private final ItemTracker itemTracker;

	private final JLabel headerLabel = new JLabel();
	private final JButton importButton = new JButton();
	private final JButton exportButton = new JButton();
	private final JButton helpButton = new JButton();
	private final JPanel tutorialPanel;
	private final JPanel itemsGrid;


	public SidebarPanel(ConfigManager configManager, ProfileManager profileManager, ClientThread clientThread,
	                    ItemManager itemManager, ItemTracker itemTracker, BufferedImage sidebarIcon)
	{
		// Disable scrolling of the top level panel.
		super(false);
		this.configManager = configManager;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.itemTracker = itemTracker;
		this.profileManager = profileManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		var headerPanel = buildHeaderPanel(sidebarIcon);

		var contentPanel = new JPanel(new BorderLayout());
		contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

		// Wrap the tutorial with a bottom margin that disappears when hidden.
		tutorialPanel = new JPanel(new BorderLayout());
		tutorialPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
		tutorialPanel.add(buildTutorialPanel());

		// Vertical stack panel of tracked items.
		this.itemsGrid = new JPanel(new GridLayout(0, 1, 0, 1));
		// Prevent scroll pane from vertically stretching grid items.
		var wrapper = new JPanel(new BorderLayout());
		wrapper.add(itemsGrid, BorderLayout.NORTH);
		// Vertical scrolling for overflowing items.
		var scrollPane = new JScrollPane(wrapper);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		contentPanel.add(tutorialPanel, BorderLayout.NORTH);
		contentPanel.add(scrollPane, BorderLayout.CENTER);

		add(headerPanel, BorderLayout.NORTH);
		add(contentPanel, BorderLayout.CENTER);
	}

	private JPanel buildHeaderPanel(BufferedImage iconImage)
	{
		var panelColor = ColorScheme.DARKER_GRAY_COLOR;
		var headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 8));
		headerPanel.setBackground(panelColor);

		var headerIcon = new JLabel(new ImageIcon(iconImage.getScaledInstance(26, 26, Image.SCALE_SMOOTH)));

		headerLabel.setText(LOGGED_OUT_HINT);
		headerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
		headerLabel.setFont(FontManager.getRunescapeSmallFont());
		headerLabel.setHorizontalAlignment(SwingConstants.LEFT);

		var buttonsGrid = new JPanel(new GridLayout(1, 3));
		buttonsGrid.setMinimumSize(new Dimension(Integer.MAX_VALUE, 0));
		buttonsGrid.setBackground(panelColor);
		setupHeaderButton(exportButton, EXPORT_ICON, "Export to clipboard", this::exportItemsToClipboard);
		setupHeaderButton(importButton, IMPORT_ICON, "Import from clipboard", this::importItemsFromClipboard);
		setupHeaderButton(helpButton, HELP_ICON, "Open help page", (ActionEvent e) -> LinkBrowser.browse(HELP_URL));

		// Begin in logged-out state.
		importButton.setEnabled(false);
		exportButton.setEnabled(false);
		buttonsGrid.add(exportButton);
		buttonsGrid.add(importButton);
		buttonsGrid.add(helpButton);

		headerPanel.add(headerIcon, BorderLayout.WEST);
		headerPanel.add(headerLabel, BorderLayout.CENTER);
		headerPanel.add(buttonsGrid, BorderLayout.EAST);
		return headerPanel;
	}

	private JPanel buildTutorialPanel()
	{
		var tutorialPanel = new JPanel();
		tutorialPanel.setAlignmentX(0.5f);
		tutorialPanel.setLayout(new BoxLayout(tutorialPanel, BoxLayout.Y_AXIS));
		tutorialPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		var headerLabel = new JLabel("Getting Started");
		headerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		headerLabel.setFont(FontManager.getRunescapeBoldFont());

		var tutorialLabel = new JLabel();
		tutorialLabel.setBorder(BorderFactory.createEmptyBorder(9, 8, 11, 8));
		tutorialLabel.setAlignmentX(0.5f);
		tutorialLabel.setFont(FontManager.getRunescapeSmallFont());
		tutorialLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		tutorialLabel.setText("<html><div style='text-align:center'>" +
			"Toggle edit mode by right-clicking the <b>Group Storage</b> button in the bank, " +
			"or the <b>Back to bank</b> button in group storage." +
			"<br><br>" +
			"While in edit mode and in the bank or group storage interface, right-click an item and select <b>Start-tracking</b>." +
			"</div></html>");

		var hideButton = new JButton("Hide Tutorial");
		hideButton.setBackground(ColorScheme.BRAND_ORANGE);
		hideButton.setForeground(ColorScheme.BORDER_COLOR);
		hideButton.setFont(FontManager.getRunescapeBoldFont());
		hideButton.setPreferredSize(new Dimension(140, 28));
		hideButton.setMinimumSize(new Dimension(140, 28));
		hideButton.setMaximumSize(new Dimension(140, 28));
		hideButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		hideButton.addActionListener((e) -> {
			configManager.setConfiguration(GroupItemTrackerConfig.GROUP, GroupItemTrackerConfig.KEY_SHOW_TUTORIAL, false);
			this.tutorialPanel.setVisible(false);
		});

		tutorialPanel.add(Box.createVerticalStrut(12));
		tutorialPanel.add(headerLabel);
		tutorialPanel.add(tutorialLabel);
		tutorialPanel.add(hideButton);
		tutorialPanel.add(Box.createVerticalStrut(14));

		return tutorialPanel;
	}

	private void setupHeaderButton(JButton button, ImageIcon icon, String tooltip, ActionListener onClick)
	{
		button.setIcon(icon);
		button.setToolTipText(tooltip);
		button.addActionListener(onClick);
		button.setPreferredSize(new Dimension(28, 28));
		button.setContentAreaFilled(false);
		SwingUtil.removeButtonDecorations(button);
	}

	private void exportItemsToClipboard(ActionEvent event)
	{
		var selection = new StringSelection(profileManager.readItemIDsAsJson());
		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(selection, null);

		// headerText as parent puts the messagebox in a convenient position.
		JOptionPane.showMessageDialog(
			headerLabel, "Successfully exported items to the clipboard.",
			PLUGIN_NAME, JOptionPane.INFORMATION_MESSAGE);
	}

	private void importItemsFromClipboard(ActionEvent event)
	{
		var result = JOptionPane.showConfirmDialog(
			headerLabel, "Imported items will replace all existing items, are you sure?",
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
					headerLabel, "Successfully imported items from the clipboard.",
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
			headerLabel, "Import failed: invalid format.",
			PLUGIN_NAME, JOptionPane.ERROR_MESSAGE);
	}

	public void setTutorialPanelVisible(boolean visible)
	{
		tutorialPanel.setVisible(visible);
	}

	// Call on sidebar panels that share a lifetime with the item tracker.
	public void login()
	{
		SwingUtilities.invokeLater(() -> {
			importButton.setEnabled(true);
			exportButton.setEnabled(true);
			updateLoggedInHint();
		});
	}

	// Call on sidebar panels that were created after the item tracker.
	public void loginAndSyncWithItemTracker()
	{
		// Copy the collection before crossing the thread-boundary to avoid ConcurrentModificationException.
		final var items = itemTracker.getItems().toArray(TrackedItemSnapshot[]::new);

		SwingUtilities.invokeLater(() -> {
			importButton.setEnabled(true);
			exportButton.setEnabled(true);

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

	public void logout()
	{
		SwingUtilities.invokeLater(() -> {
			importButton.setEnabled(false);
			exportButton.setEnabled(false);
			headerLabel.setText(LOGGED_OUT_HINT);
		});
	}

	@Subscribe
	private void onSyncedWithBank(ItemTracker.SyncedWithBank event)
	{
		// Hint gets overwritten next game tick if the bank contains unclaimed tracked items.
		// This means we see a stale claimed counter for a game tick. Not ideal, but fixing it adds complexity.
		SwingUtilities.invokeLater(this::updateLoggedInHint);
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
			var claimedItemsChanged = false;

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
					claimedItemsChanged = true;

					// List.sort is optimized for nearly sorted lists, making this very cheap.
					sortedEntries.sort(ENTRY_COMPARER);
					index = getIndexByItemID(item.itemID);
					itemsGrid.setComponentZOrder(entry.panel, index);
				}
			}

			if (claimedItemsChanged)
			{
				updateLoggedInHint();
			}

			refreshSidebar();
		});
	}

	// O(n) but the data set is small enough that it doesn't matter.
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

	private void updateLoggedInHint()
	{
		if (!itemTracker.isSyncedWithBank())
		{
			headerLabel.setText(BANK_SYNC_HINT);
			return;
		}

		int trackedCount = sortedEntries.size();
		int claimedCount = 0;
		for (var entry : sortedEntries)
		{
			// Entries are ordered claimed first.
			if (entry.snapshot.locationMask == 0)
			{
				break;
			}

			claimedCount++;
		}

		headerLabel.setText(String.format(HTML_TEMPLATE_TRACKER_INFO, trackedCount, claimedCount));
	}

	private void refreshSidebar()
	{
		revalidate();
		repaint();
	}
}