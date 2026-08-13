package com.groupitemtracker;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GroupItemTrackerConfig.GROUP)
public interface GroupItemTrackerConfig extends Config
{
	public static enum BankHighlightMode
	{
		NEVER,
		EDIT_MODE_ONLY,
		ALWAYS
	}

	String GROUP = "group-item-tracker";
	String KEY_BANK_FILTER = "bank-filter";
	String KEY_BANK_OUTLINE_MODE = "bank-outline-mode";
	String KEY_BANK_OUTLINE_COLOR = "bank-outline-color";
	String KEY_EDIT_MODE_ACTIVE = "edit_mode_active";
	String KEY_SHOW_SIDEBAR = "show-sidebar";
	String KEY_SIDEBAR_PRIORITY = "sidebar_priority";

	@ConfigItem(
		keyName = KEY_BANK_OUTLINE_MODE,
		name = "Draw outlines",
		description = "Draw outlines around tracked items in the bank and shared storage interfaces.")
	default BankHighlightMode bankOutlineMode()
	{
		return BankHighlightMode.EDIT_MODE_ONLY;
	}

	@ConfigItem(
		keyName = KEY_BANK_OUTLINE_COLOR,
		name = "Outline color",
		description = "The outline color of tracked items in the bank and shared storage interfaces.")
	default Color bankOutlineColor()
	{
		return Color.CYAN;
	}

	@ConfigItem(
		keyName = KEY_BANK_FILTER,
		name = "Bank search filter",
		description = "Filter tracked items in the bank or shared storage interface by typing '/g' in the search box.")
	default boolean useBankFilter()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_EDIT_MODE_ACTIVE,
		name = "Edit mode",
		description = "TODO: Description")
	default boolean editModeActive()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHOW_SIDEBAR,
		name = "Show sidebar",
		description = "TODO: Description")
	default boolean showSidebar()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_SIDEBAR_PRIORITY,
		name = "Sidebar priority",
		description = "TODO: Description")
	default int sidebarPriority()
	{
		return 1;
	}
}
