package com.phishkatlabs.spooptool;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SpoopToolConfig.GROUP)
public interface SpoopToolConfig extends Config
{
	String GROUP = "spooptool";
	String LINK_CODE_KEY = "linkCode";

	@ConfigItem(
		keyName = LINK_CODE_KEY,
		name = "Link code (from the SpoopTool app)",
		description = "The code from the SpoopTool app (Settings, then Link the RuneLite plugin)."
			+ " Log in to the account you want to link first. The code is used straight away and then cleared.",
		position = 0
	)
	default String linkCode()
	{
		return "";
	}

	@ConfigSection(
		name = "Feed",
		description = "What this plugin shares with the SpoopTool app once linked. It shows in your clans' feeds, and drops count toward their weekly loot rankings.",
		position = 1
	)
	String FEED = "feed";

	@ConfigItem(
		keyName = "minDropValue",
		name = "Drops worth at least",
		description = "Share a kill's drop, everything in it, when it's worth at least this many coins at Grand Exchange prices. 0 shares no drops.",
		section = FEED,
		position = 2
	)
	default int minDropValue()
	{
		return 100_000;
	}

	@ConfigItem(keyName = "sharePets", name = "Pets", description = "Share pets you get, duplicates included.", section = FEED, position = 3)
	default boolean sharePets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareCollectionLog",
		name = "Collection log items",
		description = "Share each new collection log item. Needs the game's collection log notification setting on.",
		section = FEED,
		position = 4
	)
	default boolean shareCollectionLog()
	{
		return true;
	}

	@ConfigItem(keyName = "shareDiaries", name = "Achievement diaries", description = "Share finishing a diary tier.", section = FEED, position = 5)
	default boolean shareDiaries()
	{
		return true;
	}

	@ConfigItem(keyName = "shareCombatTasks", name = "Combat achievements", description = "Share combat tasks you complete.", section = FEED, position = 6)
	default boolean shareCombatTasks()
	{
		return true;
	}

	@ConfigItem(keyName = "shareDeaths", name = "Deaths", description = "Share your deaths.", section = FEED, position = 7)
	default boolean shareDeaths()
	{
		return false;
	}
}
