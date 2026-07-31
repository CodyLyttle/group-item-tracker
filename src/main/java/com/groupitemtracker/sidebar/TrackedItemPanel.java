package com.groupitemtracker.sidebar;

import com.groupitemtracker.GroupItemTrackerPlugin;
import com.groupitemtracker.TrackedContainer;
import com.groupitemtracker.TrackedItemSnapshot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.GrayFilter;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.Constants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

public class TrackedItemPanel extends JPanel
{
	private static final ImageIcon DELETE_ICON;
	private static final ImageIcon DELETE_ICON_HOT;
	private static final String[] CACHED_LOCATION_STRINGS;

	static
	{
		// Scale down icon so the 30x30 button has some padding.
		var img = ImageUtil.loadImageResource(GroupItemTrackerPlugin.class, "delete_icon.png")
			.getScaledInstance(24, 24, Image.SCALE_SMOOTH);

		DELETE_ICON = new ImageIcon(ImageUtil.alphaOffset(GrayFilter.createDisabledImage(img), 0.16f));
		DELETE_ICON_HOT = new ImageIcon(img);

		// Preallocate location strings indexed by bitmask.
		// e.g. 7 = "bank, equipment, inventory".
		var sb = new StringBuilder();
		var containers = TrackedContainer.values();
		var combinations = 1 << containers.length;
		CACHED_LOCATION_STRINGS = new String[combinations];

		for (int mask = 0; mask < combinations; mask++)
		{
			for (var container : containers)
			{
				// Is container included in the current mask?
				if ((mask & container.mask) != 0)
				{
					if (sb.length() > 0)
					{
						sb.append(", ");
					}

					sb.append(container.description);
				}
			}

			CACHED_LOCATION_STRINGS[mask] = sb.toString();
			sb.setLength(0);
		}
	}

	private final JLabel nameLabel;
	private final JLabel locationsLabel;

	public TrackedItemPanel(TrackedItemSnapshot snapshot, AsyncBufferedImage icon, ActionListener onDelete)
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		var iconLabel = new JLabel();
		iconLabel.setMinimumSize(new Dimension(Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT));
		icon.addTo(iconLabel);

		var infoPanel = new JPanel();
		infoPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 0));
		infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
		infoPanel.setOpaque(false);

		this.nameLabel = new JShadowedLabel(snapshot.name)
		{
			// Fixes an issue where long names resized the parent panel, pushing delete buttons offscreen.
			@Override
			public Dimension getPreferredSize()
			{
				return new Dimension(0, super.getPreferredSize().height);
			}
		};
		nameLabel.setFont(FontManager.getRunescapeFont());

		this.locationsLabel = new JLabel();
		locationsLabel.setForeground(Color.gray);
		locationsLabel.setFont(FontManager.getRunescapeSmallFont());

		// Glue keeps visible labels vertically centered.
		infoPanel.add(Box.createVerticalGlue());
		infoPanel.add(nameLabel);
		infoPanel.add(locationsLabel);
		infoPanel.add(Box.createVerticalGlue());

		var removeButton = new JButton();
		removeButton.addActionListener(onDelete);
		removeButton.setIcon(DELETE_ICON);
		removeButton.setPressedIcon(DELETE_ICON_HOT);
		removeButton.setRolloverIcon(DELETE_ICON_HOT);
		removeButton.setRolloverEnabled(true);
		removeButton.setPreferredSize(new Dimension(30, 30));
		SwingUtil.removeButtonDecorations(removeButton);

		add(iconLabel, BorderLayout.WEST);
		add(infoPanel, BorderLayout.CENTER);
		add(removeButton, BorderLayout.EAST);

		updateState(snapshot);
	}

	public void updateState(TrackedItemSnapshot snapshot)
	{
		var mask = snapshot.locationMask;
		var isClaimed = mask != 0;
		nameLabel.setForeground(isClaimed ? ColorScheme.BRAND_ORANGE : ColorScheme.TEXT_COLOR);
		locationsLabel.setText(CACHED_LOCATION_STRINGS[mask]);
	}
}