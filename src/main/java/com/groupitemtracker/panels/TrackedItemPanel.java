package com.groupitemtracker.panels;

import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.TrackedContainer;
import com.groupitemtracker.TrackedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
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
public class TrackedItemPanel extends PluginPanel
{
	private static final Color BACKGROUND_COLOR = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color TEXT_COLOR_PRIMARY = ColorScheme.BRAND_ORANGE;
	private static final Color TEXT_COLOR_SECONDARY = Color.gray;
	private static final String DELETE_TOOLTIP = "Remove from GIM item tracker";
	private static final ImageIcon DELETE_ICON = new ImageIcon(ImageUtil.loadImageResource(PluginPanel.class, "/delete_icon.png"));

	private final JLabel nameLabel;
	private final JLabel locationsLabel;

	private final ClientThread clientThread;
	private final ItemTracker itemTracker;
	private final TrackedItem trackedItem;

	public TrackedItemPanel(ClientThread clientThread, ItemTracker itemTracker, TrackedItem trackedItem, AsyncBufferedImage itemIcon)
	{
		this.clientThread = clientThread;
		this.itemTracker = itemTracker;
		this.trackedItem = trackedItem;

		final var icon = new JLabel();
		icon.setMinimumSize(new Dimension(Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT));
		itemIcon.addTo(icon);

		nameLabel = new JShadowedLabel(trackedItem.getItemName());
		nameLabel.setFont(FontManager.getRunescapeFont());
		locationsLabel = new JLabel();
		locationsLabel.setFont(FontManager.getRunescapeSmallFont());
		locationsLabel.setForeground(TEXT_COLOR_SECONDARY);
		final var infoPanel = new JPanel(new GridLayout(2, 0));
		infoPanel.setBorder(new EmptyBorder(2, 4, 2, 0));
		infoPanel.setBackground(BACKGROUND_COLOR);
		infoPanel.add(nameLabel);      // top row
		infoPanel.add(locationsLabel); // bottom row

		final var removeButton = new JButton();
		removeButton.setIcon(DELETE_ICON);
		removeButton.setToolTipText(DELETE_TOOLTIP);
		removeButton.addActionListener(this::removeFromItemTracker);
		SwingUtil.removeButtonDecorations(removeButton);
		final var buttonContainer = new JPanel(new BorderLayout());
		buttonContainer.setBorder(new EmptyBorder(6, 6, 6, 6));
		buttonContainer.setPreferredSize(new Dimension(20, 20));
		buttonContainer.setBackground(BACKGROUND_COLOR);
		buttonContainer.add(removeButton);

		setLayout(new BorderLayout());
		setBackground(BACKGROUND_COLOR);
		this.add(icon, BorderLayout.WEST);
		this.add(buttonContainer, BorderLayout.EAST);
		this.add(infoPanel, BorderLayout.CENTER);
		refresh();
	}

	private void removeFromItemTracker(ActionEvent e)
	{
		clientThread.invoke(() -> itemTracker.removeItem(trackedItem.getItemID()));
	}

	public void refresh()
	{
		nameLabel.setForeground(trackedItem.getTotalCount() > 0 ? TEXT_COLOR_PRIMARY : TEXT_COLOR_SECONDARY);
		locationsLabel.setText(buildLocationsString());
		repaint();
	}

	// TODO: Should we cache location strings to minimize allocations?
	private String buildLocationsString()
	{
		final StringBuilder sb = new StringBuilder();
		boolean firstLocation = true;

		for (var container : TrackedContainer.values())
		{
			if (trackedItem.getContainerCount(container) == 0)
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