package com.groupitemtracker.sidebar;

import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.TrackedContainer;
import com.groupitemtracker.TrackedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.HashMap;
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
	private static final HashMap<Integer, String> cachedLocationStrings;

	static
	{
		// Use bitmask enumeration to generate a string for every possible combination of locations. 
		// Each location is represented by a single bit in an integer.
		// All possible combinations are covered by iterating the bitmask from 1 to 2^n-1.
		// The set bits of the mask indicate which locations are included in the combination.
		
		final String[] locationNames = Arrays.stream(TrackedContainer.values())
			.map((value) -> value.description)
			.toArray(String[]::new);

		final int n = locationNames.length;
		final int combinations = (1 << n) - 1;
		cachedLocationStrings = new HashMap<>();

		for (int bitmask = 1; bitmask <= combinations; bitmask++)
		{
			var sb = new StringBuilder();

			for (int i = 0; i < n; i++)
			{
				if ((bitmask & (1 << i)) != 0)
				{
					if (sb.length() > 0)
					{
						sb.append(", ");
					}

					sb.append(locationNames[i]);
				}
			}

			cachedLocationStrings.put(bitmask, sb.toString());
		}
	}

	private final JLabel nameLabel;
	private final JLabel locationsLabel;

	@Getter
	private final TrackedItem item;

	public ItemPanel(ClientThread clientThread, ItemTracker itemTracker, TrackedItem item, AsyncBufferedImage itemIcon)
	{
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
		removeButton.addActionListener((e) -> clientThread.invoke(() -> itemTracker.removeItem(item.getItemID())));
		removeButton.setIcon(DELETE_ICON);
		removeButton.setToolTipText(DELETE_TOOLTIP);
		SwingUtil.removeButtonDecorations(removeButton);
		buttonPanel.add(removeButton);

		setLayout(new BorderLayout());
		setBackground(BACKGROUND_COLOR);
		add(icon, BorderLayout.WEST);
		add(infoPanel, BorderLayout.CENTER);
		add(buttonPanel, BorderLayout.EAST);
		refresh();
	}

	public void refresh()
	{
		final boolean isClaimed = item.getTotalCount() > 0;

		if (isClaimed)
		{
			nameLabel.setForeground(TEXT_COLOR_CLAIMED);
			locationsLabel.setText(getLocationString());
			locationsLabel.setVisible(true);
		}
		else
		{
			nameLabel.setForeground(TEXT_COLOR_UNCLAIMED);
			locationsLabel.setVisible(false);
		}

		revalidate();
		repaint();
	}

	private String getLocationString()
	{
		int bitmask = 0;
		TrackedContainer[] containers = TrackedContainer.values();

		for (int i = 0; i < containers.length; i++)
		{
			if (item.getContainerCount(containers[i]) > 0)
			{
				bitmask |= (1 << i);
			}
		}

		return cachedLocationStrings.get(bitmask);
	}
}