package com.phishkatlabs.spooptool;

import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * SpoopTool: links a game account to the SpoopTool app, tells SpoopTool when
 * the player logs out so their stats update straight away (v0.1), and shares
 * what only the client sees to their feed (v0.2): drops over a value, pets,
 * collection log items, diaries, combat tasks, and deaths if they want.
 *
 * Linking: the player types a code from the app into this plugin's settings
 * while logged in. The plugin trades it for a token, which it keeps in the
 * RuneLite profile of that game account, so each account has its own link.
 * Nothing is sent before a code is entered.
 */
@Slf4j
@PluginDescriptor(
	name = "SpoopTool",
	description = "Links your account to the SpoopTool clan tracker app, updates it when you log out, and shares drops, pets and more to your feed",
	tags = {"clan", "tracker", "hiscores", "xp", "loot", "pets", "collection log"}
)
public class SpoopToolPlugin extends Plugin
{
	/** Where the token lives, per game account (RuneLite profile). */
	static final String TOKEN_KEY = "token";

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SpoopToolConfig config;

	@Inject
	private SpoopToolApi api;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ItemManager itemManager;

	/** The server takes at most this many events in one call. */
	private static final int MAX_EVENTS_PER_CALL = 20;
	/**
	 * How long to wait for a pet's name. The game's pet message doesn't say
	 * which pet; the collection log line (a new pet) or the untradeable-drop
	 * line (a duplicate) that follows it does, if those settings are on.
	 */
	private static final int PET_NAME_TICKS = 5;
	private int petTicksLeft;
	private boolean petDuplicate;

	/*
	 * Who's logged in, captured while they are: by the time the client reports
	 * the logout, the local player is gone. Null name means nobody yet.
	 * Volatile because they're written on the client thread and read on the
	 * settings and network threads.
	 */
	private volatile String playerName;
	private volatile long accountHash = -1;
	private volatile String token;

	/** A link request is out, so typing the code twice doesn't send it twice. */
	private volatile boolean linking;

	@Override
	protected void shutDown()
	{
		forgetPlayer();
	}

	@Provides
	SpoopToolConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SpoopToolConfig.class);
	}

	/**
	 * The local player's name isn't there on the login event itself, so it's
	 * picked up on the first tick after. A code typed before logging in is used
	 * then.
	 */
	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// No name arrived for the pet in time: share it nameless.
		if (petTicksLeft > 0 && --petTicksLeft == 0)
		{
			sharePet(null);
		}
		if (playerName != null)
		{
			return;
		}
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}
		playerName = local.getName();
		accountHash = client.getAccountHash();
		token = configManager.getRSProfileConfiguration(SpoopToolConfig.GROUP, TOKEN_KEY);

		String pending = config.linkCode().trim();
		if (!pending.isEmpty())
		{
			link(pending);
		}
	}

	/**
	 * Back at the login screen means a logout (or a lost connection), not a
	 * world hop: hopping goes through its own states and back to logged in.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGIN_SCREEN)
		{
			return;
		}
		if (playerName != null && token != null)
		{
			api.logout(token, accountHash, playerName);
		}
		forgetPlayer();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SpoopToolConfig.GROUP.equals(event.getGroup()) || !SpoopToolConfig.LINK_CODE_KEY.equals(event.getKey()))
		{
			return;
		}
		String code = event.getNewValue() == null ? "" : event.getNewValue().trim();
		if (code.isEmpty())
		{
			return;
		}
		if (playerName == null)
		{
			say("log in to the account you want to link, and the code will be used then.");
			return;
		}
		link(code);
	}

	private void link(String code)
	{
		if (linking)
		{
			return;
		}
		linking = true;
		api.link(code, accountHash, playerName,
			result ->
			{
				linking = false;
				configManager.setRSProfileConfiguration(SpoopToolConfig.GROUP, TOKEN_KEY, result.token);
				token = result.token;
				configManager.setConfiguration(SpoopToolConfig.GROUP, SpoopToolConfig.LINK_CODE_KEY, "");
				say("linked " + result.account.name + ". It'll update each time you log out.");
			},
			error ->
			{
				linking = false;
				// Cleared either way, so a bad code isn't retried at every login.
				configManager.setConfiguration(SpoopToolConfig.GROUP, SpoopToolConfig.LINK_CODE_KEY, "");
				say("couldn't link: " + error);
			});
	}

	private void forgetPlayer()
	{
		playerName = null;
		accountHash = -1;
		token = null;
		petTicksLeft = 0;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (token == null || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM))
		{
			return;
		}
		String message = GameMessages.clean(event.getMessage());

		if (config.sharePets() && GameMessages.isPet(message))
		{
			petTicksLeft = PET_NAME_TICKS;
			petDuplicate = GameMessages.isDuplicatePet(message);
			return;
		}

		String logged = GameMessages.collectionLogItem(message);
		if (logged != null)
		{
			// Straight after a pet message, this line is the pet itself.
			if (petTicksLeft > 0)
			{
				sharePet(logged);
			}
			else if (config.shareCollectionLog())
			{
				send(feedEvent("clog_item", data("item", logged)));
			}
			return;
		}

		// A duplicate pet doesn't reach the collection log; with untradeable drop
		// messages on, this names it.
		String untradeable = GameMessages.untradeableDrop(message);
		if (untradeable != null && petTicksLeft > 0 && petDuplicate)
		{
			sharePet(untradeable);
			return;
		}

		String[] diary = GameMessages.diary(message);
		if (diary != null && config.shareDiaries())
		{
			JsonObject d = data("tier", diary[0]);
			d.addProperty("area", diary[1]);
			send(feedEvent("diary", d));
			return;
		}

		String[] task = GameMessages.combatTask(message);
		if (task != null && config.shareCombatTasks())
		{
			JsonObject d = data("tier", task[0]);
			d.addProperty("name", task[1]);
			send(feedEvent("combat_achievement", d));
		}
	}

	/** Kills, from RuneLite's loot manager. */
	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		shareLoot(event.getNpc().getName(), event.getItems());
	}

	/**
	 * Raids, clues, chests and pickpockets, from the loot tracker. Its NPC (and
	 * player-kill) records are skipped: kills already came through
	 * NpcLootReceived, and PvP loot isn't shared.
	 */
	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.PLAYER)
		{
			return;
		}
		shareLoot(event.getName(), event.getItems());
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (config.shareDeaths() && event.getActor() == client.getLocalPlayer())
		{
			send(feedEvent("death", new JsonObject()));
		}
	}

	/**
	 * Share each item in a drop worth at least the player's threshold, at Grand
	 * Exchange prices. Stacks of one item are added up, noted and unnoted alike.
	 * Runs on the client thread, where item names can be looked up.
	 */
	private void shareLoot(String source, Collection<ItemStack> items)
	{
		int minimum = config.minDropValue();
		if (token == null || minimum <= 0)
		{
			return;
		}
		Map<Integer, Integer> quantities = new LinkedHashMap<>();
		for (ItemStack stack : items)
		{
			quantities.merge(itemManager.canonicalize(stack.getId()), stack.getQuantity(), Integer::sum);
		}

		List<JsonObject> drops = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : quantities.entrySet())
		{
			long value = (long) itemManager.getItemPrice(entry.getKey()) * entry.getValue();
			if (value < minimum)
			{
				continue;
			}
			JsonObject d = data("item", itemManager.getItemComposition(entry.getKey()).getName());
			d.addProperty("quantity", entry.getValue());
			d.addProperty("value", (int) Math.min(value, Integer.MAX_VALUE));
			if (source != null)
			{
				d.addProperty("source", source);
			}
			drops.add(feedEvent("drop", d));
		}
		if (!drops.isEmpty())
		{
			api.sendEvents(token, drops.subList(0, Math.min(drops.size(), MAX_EVENTS_PER_CALL)));
		}
	}

	private void sharePet(String name)
	{
		petTicksLeft = 0;
		JsonObject d = new JsonObject();
		if (name != null)
		{
			d.addProperty("name", name);
		}
		if (petDuplicate)
		{
			d.addProperty("duplicate", 1);
		}
		send(feedEvent("pet", d));
	}

	private void send(JsonObject event)
	{
		if (token != null)
		{
			api.sendEvents(token, Collections.singletonList(event));
		}
	}

	private static JsonObject data(String key, String value)
	{
		JsonObject d = new JsonObject();
		d.addProperty(key, value);
		return d;
	}

	private static JsonObject feedEvent(String kind, JsonObject data)
	{
		JsonObject e = new JsonObject();
		e.addProperty("kind", kind);
		e.add("data", data);
		return e;
	}

	/** A line in the chatbox, from the plugin. Safe from any thread: the manager queues it for the client. */
	private void say(String message)
	{
		String text = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("SpoopTool: ")
			.append(ChatColorType.NORMAL)
			.append(message)
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(text)
			.build());
	}
}
