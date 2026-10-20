package com.phishkatlabs.spooptool;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * SpoopTool v0.1: links a game account to the SpoopTool app, and tells
 * SpoopTool when the player logs out so their stats update straight away.
 *
 * Linking: the player types a code from the app into this plugin's settings
 * while logged in. The plugin trades it for a token, which it keeps in the
 * RuneLite profile of that game account, so each account has its own link.
 * Nothing is sent before a code is entered.
 */
@Slf4j
@PluginDescriptor(
	name = "SpoopTool",
	description = "Links your account to the SpoopTool clan tracker app and updates it when you log out",
	tags = {"clan", "tracker", "hiscores", "xp"}
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
