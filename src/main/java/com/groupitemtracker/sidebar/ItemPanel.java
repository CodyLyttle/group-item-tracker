package com.groupitemtracker.sidebar;

import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.TrackedContainer;
import com.groupitemtracker.TrackedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class ItemPanel extends PluginPanel
{
	private static final Color BACKGROUND_COLOR = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color TEXT_COLOR_CLAIMED = ColorScheme.BRAND_ORANGE;
	private static final Color TEXT_COLOR_UNCLAIMED = ColorScheme.TEXT_COLOR;
	private static final Color TEXT_COLOR_LOCATION_HINT = Color.gray;
	private static final String DELETE_TOOLTIP = "Remove from GIM item tracker";
	private static final ImageIcon DELETE_ICON = new ImageIcon(ImageUtil.loadImageResource(PluginPanel.class, "/delete_icon.png"));

	private final JLabel nameLabel;
	private final JLabel locationsLabel;
	private final ClientThread clientThread;
	private final ItemTracker itemTracker;

	@Getter
	private final TrackedItem item;

	public ItemPanel(ClientThread clientThread, ItemTracker itemTracker, TrackedItem item, AsyncBufferedImage itemIcon)
	{
		this.clientThread = clientThread;
		this.itemTracker = itemTracker;
		this.item = item;

		final var icon = new JLabel();
		icon.setMinimumSize(new Dimension(Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT));
		itemIcon.addTo(icon);

		final var infoPanel = new JPanel();
		infoPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 0));
		infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
		infoPanel.setOpaque(false);
		nameLabel = new JShadowedLabel(item.getItemName());
		nameLabel.setFont(FontManager.getRunescapeFont());
		locationsLabel = new JLabel();
		locationsLabel.setFont(FontManager.getRunescapeSmallFont());
		locationsLabel.setForeground(TEXT_COLOR_LOCATION_HINT);
		// Vertical glue ensures visible labels stay vertically centered.
		infoPanel.add(Box.createVerticalGlue());
		infoPanel.add(nameLabel);
		infoPanel.add(locationsLabel);
		infoPanel.add(Box.createVerticalGlue());

		final var buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		buttonPanel.setPreferredSize(new Dimension(20, 20));
		buttonPanel.setBackground(BACKGROUND_COLOR);
		final var removeButton = new JButton();
		removeButton.setIcon(DELETE_ICON);
		removeButton.setToolTipText(DELETE_TOOLTIP);
		removeButton.addActionListener(this::removeFromItemTracker);
		SwingUtil.removeButtonDecorations(removeButton);
		buttonPanel.add(removeButton);

		setLayout(new BorderLayout());
		setBackground(BACKGROUND_COLOR);
		add(icon, BorderLayout.WEST);
		add(infoPanel, BorderLayout.CENTER);
		add(buttonPanel, BorderLayout.EAST);
		refresh();
	}

	private void removeFromItemTracker(ActionEvent e)
	{
		clientThread.invoke(() -> itemTracker.removeItem(item.getItemID()));
	}

	public void refresh()
	{
		final boolean isClaimed = item.getTotalCount() > 0;

		if (isClaimed)
		{
			nameLabel.setForeground(TEXT_COLOR_CLAIMED);
			locationsLabel.setText(buildLocationsString());
			locationsLabel.setVisible(true);
		}
		else
		{
			nameLabel.setForeground(TEXT_COLOR_UNCLAIMED);
			locationsLabel.setText("");
			locationsLabel.setVisible(false);
		}

		revalidate();
		repaint();
	}

	// TODO: Should we cache location strings to minimize allocations?
	private String buildLocationsString()
	{
		final StringBuilder sb = new StringBuilder();
		boolean firstLocation = true;

		for (var container : TrackedContainer.values())
		{
			if (item.getContainerCount(container) == 0)
			{
				continue;
			}

			if (!firstLocation)
			{
				sb.append(", ");
			}

			sb.append(container.description);
			firstLocation = false;
		}

		return sb.toString();
	}
}