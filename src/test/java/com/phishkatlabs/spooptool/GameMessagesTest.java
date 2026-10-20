package com.phishkatlabs.spooptool;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GameMessagesTest
{
	@Test
	public void cleansColourTagsAndNonBreakingSpaces()
	{
		assertEquals("New item added to your collection log: Dragon pickaxe",
			GameMessages.clean("New item added to your collection log: <col=ef1020>Dragon pickaxe</col>"));
	}

	@Test
	public void knowsPets()
	{
		assertTrue(GameMessages.isPet("You have a funny feeling like you're being followed."));
		assertTrue(GameMessages.isPet("You feel something weird sneaking into your backpack."));
		assertTrue(GameMessages.isDuplicatePet("You have a funny feeling like you would have been followed..."));
		assertFalse(GameMessages.isDuplicatePet("You have a funny feeling like you're being followed."));
		assertFalse(GameMessages.isPet("You feel refreshed."));
	}

	@Test
	public void readsCollectionLogAndUntradeableDrops()
	{
		assertEquals("Giant squirrel", GameMessages.collectionLogItem("New item added to your collection log: Giant squirrel"));
		assertEquals("Vorki", GameMessages.untradeableDrop("Untradeable drop: Vorki"));
		assertNull(GameMessages.collectionLogItem("Your collection log is full."));
	}

	@Test
	public void readsDiariesAndCombatTasks()
	{
		assertArrayEquals(new String[]{"hard", "Varrock"},
			GameMessages.diary("Congratulations! You have completed all of the hard tasks in the Varrock area. Speak to Toby to claim your reward."));
		assertArrayEquals(new String[]{"easy", "Noxious Foe"},
			GameMessages.combatTask("Congratulations, you've completed an easy combat task: Noxious Foe (1 point)."));
		assertArrayEquals(new String[]{"elite", "Perfect Zulrah"},
			GameMessages.combatTask("Congratulations, you've completed an elite combat task: Perfect Zulrah."));
		assertNull(GameMessages.diary("Well done! You have completed an easy task in the Varrock area."));
	}
}
