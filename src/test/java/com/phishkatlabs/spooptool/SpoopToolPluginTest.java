package com.phishkatlabs.spooptool;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Runs RuneLite in developer mode with this plugin loaded: `./gradlew run`. */
public class SpoopToolPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SpoopToolPlugin.class);
		RuneLite.main(args);
	}
}
