package com.groupitemtracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.EnumMap;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
public class SidebarItemPanel extends PluginPanel
{
	private static final Color BACKGROUND_COLOR = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color TEXT_COLOR_PRIMARY = ColorScheme.BRAND_ORANGE;
	private static final Color TEXT_COLOR_SECONDARY = Color.gray;

	private final JLabel nameLabel;
	private final JLabel locationsLabel;
	private final TrackedItem trackedItem;
	private final EnumMap<TrackedContainer, Boolean> itemLocationsSnapshot;

	public SidebarItemPanel(TrackedItem trackedItem, String itemName, AsyncBufferedImage itemIcon)
	{
		this.trackedItem = trackedItem;
		itemLocationsSnapshot = new EnumMap<>(TrackedContainer.class);
		for (var container : TrackedContainer.values())
		{
			boolean isPresent = trackedItem.getContainerCount(container) > 0;
			itemLocationsSnapshot.put(container, isPresent);
		}

		final var icon = new JLabel();
		icon.setMinimumSize(new Dimension(Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT));
		itemIcon.addTo(icon);

		nameLabel = new JShadowedLabel(itemName);
		nameLabel.setFont(FontManager.getRunescapeFont());
		locationsLabel = new JLabel();
		locationsLabel.setFont(FontManager.getRunescapeSmallFont());
		locationsLabel.setForeground(TEXT_COLOR_SECONDARY);
		final var infoPanel = new JPanel(new GridLayout(2, 0));
		infoPanel.setBorder(new EmptyBorder(2, 4, 2, 0));
		infoPanel.setBackground(BACKGROUND_COLOR);
		infoPanel.add(nameLabel);      // top row
		infoPanel.add(locationsLabel); // bottom row

		setLayout(new BorderLayout());
		setBackground(BACKGROUND_COLOR);
		this.add(icon, BorderLayout.WEST);
		this.add(infoPanel, BorderLayout.CENTER);
		updateStyle();
	}

	public void refresh()
	{
		if (tryUpdateSnapshot())
		{
			updateStyle();
			repaint();
		}
	}

	private boolean tryUpdateSnapshot()
	{
		boolean wasUpdated = false;

		for (var container : TrackedContainer.values())
		{
			Boolean wasPresent = itemLocationsSnapshot.get(container);
			boolean isPresent = trackedItem.getContainerCount(container) > 0;
			if (wasPresent != isPresent)
			{
				itemLocationsSnapshot.put(container, isPresent);
				wasUpdated = true;
			}
		}

		return wasUpdated;
	}

	private void updateStyle()
	{
		final String locations = buildLocationsString();
		final Color nameColor = locations.isEmpty()
			? TEXT_COLOR_SECONDARY
			: TEXT_COLOR_PRIMARY;

		locationsLabel.setText(locations);
		nameLabel.setForeground(nameColor);
	}

	private String buildLocationsString()
	{
		StringBuilder sb = new StringBuilder();
		boolean firstLocation = true;

		for (var container : TrackedContainer.values())
		{
			if (itemLocationsSnapshot.get(container))
			{
				if (!firstLocation)
				{
					sb.append(", ");
				}

				sb.append(container.description);
				firstLocation = false;
			}
		}

		return sb.toString();
	}
}