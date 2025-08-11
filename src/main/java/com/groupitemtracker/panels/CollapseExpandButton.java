package com.groupitemtracker.panels;

import java.awt.event.ActionEvent;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import lombok.Getter;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

public class CollapseExpandButton extends JButton
{
	private static final ImageIcon COLLAPSE_ICON = new ImageIcon(ImageUtil.loadImageResource(PluginPanel.class, "/collapse_icon.png"));
	private static final ImageIcon EXPAND_ICON = new ImageIcon(ImageUtil.loadImageResource(PluginPanel.class, "/expand_icon.png"));

	@Getter
	private boolean isExpanded;

	public CollapseExpandButton(boolean startExpanded, Runnable collapseCallback, Runnable expandCallback)
	{
		isExpanded = startExpanded;
		setIcon(isExpanded ? COLLAPSE_ICON : EXPAND_ICON);
		SwingUtil.removeButtonDecorations(this);

		addActionListener((ActionEvent e) ->
		{
			isExpanded = !isExpanded;
			if (isExpanded)
			{
				setIcon(COLLAPSE_ICON);
				expandCallback.run();
			}
			else
			{
				setIcon(EXPAND_ICON);
				collapseCallback.run();
			}
		});
	}
}