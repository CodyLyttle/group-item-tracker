package com.groupitemtracker;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(GroupItemTrackerConfig.GROUP)
public interface GroupItemTrackerConfig extends Config
{
	enum BankHighlightMode
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
	String KEY_SIDEBAR_PRIORITY = "sidebar-priority";
	String KEY_SHOW_TUTORIAL = "sidebar-tutorial";


	@ConfigItem(
		keyName = KEY_EDIT_MODE_ACTIVE,
		name = "Edit mode",
		description = "Edit mode adds Start-tracking/Stop-tracking menu options for items in the bank and group storage.",
		position = 0)
	default boolean editModeActive()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHOW_TUTORIAL,
		name = "Show tutorial",
		description = "Whether to show the tutorial panel in the sidebar.",
		position = 2)
	default boolean showTutorial()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_BANK_FILTER,
		name = "Search filter",
		description = "Type /g in the bank or group storage search interface to show all tracked items.",
		position = 1)
	default boolean useBankFilter()
	{
		return true;
	}

	@ConfigSection(
		name = "Tracked item outlines",
		description = "Configuration for drawing item outlines in the bank and group storage interfaces.",
		position = 20
	)
	String outlineSection = "outline";

	@ConfigItem(
		keyName = KEY_BANK_OUTLINE_MODE,
		name = "Outline mode",
		description = "When to draw outlines.",
		section = outlineSection)
	default BankHighlightMode bankOutlineMode()
	{
		return BankHighlightMode.EDIT_MODE_ONLY;
	}

	@ConfigItem(
		keyName = KEY_BANK_OUTLINE_COLOR,
		name = "Outline color",
		description = "The outline color.",
		section = outlineSection)
	default Color bankOutlineColor()
	{
		return Color.CYAN;
	}

	@ConfigSection(
		name = "Sidebar",
		description = "Configuration for sidebar visibility and order.",
		position = 10)
	String sidebarSection = "sidebar";

	@ConfigItem(
		keyName = KEY_SHOW_SIDEBAR,
		name = "Show sidebar",
		description = "Whether to show the sidebar.",
		section = sidebarSection,
		position = 0)
	default boolean showSidebar()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_SIDEBAR_PRIORITY,
		name = "Icon priority",
		description = "The order in which the sidebar icon appears. This is not a 1:1 mapping between number and order, and depends on the priority of other plugins.",
		section = sidebarSection,
		position = 1)
	default int sidebarPriority()
	{
		return 1;
	}
}
