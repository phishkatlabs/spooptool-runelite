package com.phishkatlabs.spooptool;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SpoopToolConfig.GROUP)
public interface SpoopToolConfig extends Config
{
	String GROUP = "spooptool";
	String LINK_CODE_KEY = "linkCode";

	@ConfigItem(
		keyName = LINK_CODE_KEY,
		name = "Link code",
		description = "The code from the SpoopTool app (Settings, then Link the RuneLite plugin)."
			+ " Log in to the account you want to link first. The code is used straight away and then cleared.",
		position = 0
	)
	default String linkCode()
	{
		return "";
	}
}
