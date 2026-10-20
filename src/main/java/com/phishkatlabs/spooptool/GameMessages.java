package com.phishkatlabs.spooptool;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * The game messages the plugin reads for the feed. They're the same messages
 * Dink (pajlads/DinkPlugin) reads, so the patterns follow its. Pure, so they're
 * tested without a client.
 */
final class GameMessages
{
	/** "You have a funny feeling like you're being followed.", "...would have been followed...", "You feel something weird sneaking into your backpack." */
	private static final Pattern PET = Pattern.compile("You (?:have a funny feeling like you|feel something weird sneaking).*");
	/** Needs the game's collection log notification setting turned on. */
	private static final Pattern COLLECTION_LOG = Pattern.compile("New item added to your collection log: (.+)");
	/** Needs the game's untradeable loot notification setting; it's how a duplicate pet gets its name. */
	private static final Pattern UNTRADEABLE_DROP = Pattern.compile("Untradeable drop: (.+)");
	private static final Pattern DIARY = Pattern.compile("Congratulations! You have completed all of the (.+?) tasks in the (.+?) area");
	/** "...an easy combat task: Noxious Foe (1 point)." */
	private static final Pattern COMBAT_TASK = Pattern.compile("Congratulations, you've completed an? (\\w+) combat task: (.+?)(?:\\s+\\(\\d+ points?\\))?\\.$");

	private GameMessages()
	{
	}

	/** A message as it reads on screen: colour tags gone, non-breaking spaces made plain. */
	static String clean(String message)
	{
		return Text.removeTags(message).replace(' ', ' ').trim();
	}

	static boolean isPet(String message)
	{
		return PET.matcher(message).matches();
	}

	static boolean isDuplicatePet(String message)
	{
		return isPet(message) && message.contains("would have been");
	}

	/** The item just added to the collection log, or null. */
	static String collectionLogItem(String message)
	{
		Matcher m = COLLECTION_LOG.matcher(message);
		return m.matches() ? m.group(1).trim() : null;
	}

	/** The untradeable item just dropped, or null. */
	static String untradeableDrop(String message)
	{
		Matcher m = UNTRADEABLE_DROP.matcher(message);
		return m.matches() ? m.group(1).trim() : null;
	}

	/** {tier, area} of a finished diary tier, or null. */
	static String[] diary(String message)
	{
		Matcher m = DIARY.matcher(message);
		return m.find() ? new String[]{m.group(1).toLowerCase(), m.group(2)} : null;
	}

	/** {tier, task} of a finished combat task, or null. */
	static String[] combatTask(String message)
	{
		Matcher m = COMBAT_TASK.matcher(message);
		return m.matches() ? new String[]{m.group(1).toLowerCase(), m.group(2)} : null;
	}
}
