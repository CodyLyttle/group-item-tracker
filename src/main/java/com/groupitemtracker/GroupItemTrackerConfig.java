package com.groupitemtracker;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("group-item-tracker")
public interface GroupItemTrackerConfig extends Config
{
	@ConfigItem(
		keyName = "bank-filter",
		name = "Bank search filter",
		description = "Enter '/g' in the bank search interface to display all tracked items.")
	default boolean useBankFilter()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bank-highlights",
		name = "Highlight bank items",
		description = "Highlights all tracked items in the bank.")
	default boolean useBankHighlights()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bank-highlights-color",
		name = "Highlight color",
		description = "The color used to highlight tracked items in the bank.")
	default Color bankHighlightColor()
	{
		return Color.YELLOW;
	}
}
