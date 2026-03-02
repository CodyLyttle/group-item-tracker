package com.groupitemtracker.sidebar;

import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.TrackedContainer;
import com.groupitemtracker.TrackedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.GrayFilter;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.Constants;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

public class TrackedItemPanel extends JPanel 
{
	private static final ImageIcon DELETE_ICON;
	private static final ImageIcon DELETE_ICON_DIMMED;
	private static final String[] cachedLocationStrings;

	static
	{
		// Scale down icon so the 30x30 button has some padding.
		DELETE_ICON = new ImageIcon(ImageUtil.loadImageResource(PluginPanel.class, "/delete_icon.png")
			.getScaledInstance(24, 24, Image.SCALE_SMOOTH));

		// Alternate icon for when the button isn't hovered.
		final float alpha = 0.16f;
		final Image grayImage = GrayFilter.createDisabledImage(DELETE_ICON.getImage());
		DELETE_ICON_DIMMED = new ImageIcon(ImageUtil.alphaOffset(grayImage, alpha));

		// Preallocate all location strings.
		// The set bits of the mask indicate which locations are included in the combination.
		// 0 = empty string, 2^n-1 = all locations.
		final String[] locationNames = Arrays.stream(TrackedContainer.values())
			.map((value) -> value.description)
			.toArray(String[]::new);

		final int n = locationNames.length;
		final int combinations = (1 << n);
		cachedLocationStrings = new String[combinations];

		final var sb = new StringBuilder();
		for (int mask = 0; mask < combinations; mask++)
		{
			for (int bit = 0; bit < n; bit++)
			{
				if ((mask & (1 << bit)) != 0)
				{
					if (sb.length() > 0)
					{
						sb.append(", ");
					}

					sb.append(locationNames[bit]);
				}
			}

			cachedLocationStrings[mask] = sb.toString();
			sb.setLength(0);
		}
	}

	private final TrackedItem item;
	private final JLabel nameLabel;
	private final JLabel locationsLabel;

	public TrackedItemPanel(ClientThread clientThread, ItemTracker itemTracker, TrackedItem item, AsyncBufferedImage itemIcon)
	{
		this.item = item;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

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
		locationsLabel.setForeground(Color.gray);
		// Vertical glue ensures visible labels stay vertically centered.
		infoPanel.add(Box.createVerticalGlue());
		infoPanel.add(nameLabel);
		infoPanel.add(locationsLabel);
		infoPanel.add(Box.createVerticalGlue());

		final var removeButton = new JButton();
		removeButton.addActionListener((e) -> clientThread.invoke(() -> itemTracker.removeItem(item.getItemID())));
		removeButton.setIcon(DELETE_ICON_DIMMED);
		removeButton.setPressedIcon(DELETE_ICON);
		removeButton.setRolloverIcon(DELETE_ICON);
		removeButton.setRolloverEnabled(true);
		removeButton.setPreferredSize(new Dimension(30, 30));
		SwingUtil.removeButtonDecorations(removeButton);

		add(icon, BorderLayout.WEST);
		add(infoPanel, BorderLayout.CENTER);
		add(removeButton, BorderLayout.EAST);

		refresh();
	}

	public void refresh()
	{
		final boolean isClaimed = item.getTotalCount() > 0;
		if (isClaimed)
		{
			nameLabel.setForeground(ColorScheme.BRAND_ORANGE);
			locationsLabel.setText(getLocationString());
			locationsLabel.setVisible(true);
		}
		else
		{
			nameLabel.setForeground(ColorScheme.TEXT_COLOR);
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

		return cachedLocationStrings[bitmask];
	}
}