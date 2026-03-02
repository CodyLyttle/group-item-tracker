package com.groupitemtracker;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GroupItemTrackerConfig.GROUP)
public interface GroupItemTrackerConfig extends Config
{
	String GROUP = "group-item-tracker";
	String KEY_BANK_FILTER = "bank-filter";
	String KEY_BANK_HIGHLIGHTS = "bank-highlights";
	String KEY_BANK_HIGHLIGHTS_COLOR = "bank-highlights-color";

	@ConfigItem(
		keyName = GroupItemTrackerConfig.KEY_BANK_FILTER,
		name = "Bank search filter",
		description = "Filter tracked items in the bank or shared storage interface by typing '/g' in the search box.")
	default boolean useBankFilter()
	{
		return true;
	}

	@ConfigItem(
		keyName = GroupItemTrackerConfig.KEY_BANK_HIGHLIGHTS,
		name = "Highlight bank items",
		description = "Draw outlines around tracked items in the bank and shared storage interfaces.")
	default boolean useBankHighlights()
	{
		return true;
	}

	@ConfigItem(
		keyName = GroupItemTrackerConfig.KEY_BANK_HIGHLIGHTS_COLOR,
		name = "Highlight color",
		description = "The outline color of tracked items in the bank and shared storage interfaces.")
	default Color bankHighlightColor()
	{
		return Color.YELLOW;
	}
}
