package com.phishkatlabs.spooptool;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The SpoopTool server, which is the only thing this plugin talks to.
 *
 * Requests go through the OkHttpClient and Gson that RuneLite injects; the
 * Plugin Hub doesn't allow any other HTTP client, or a client of our own.
 * Every call is asynchronous, so the game never waits on the network.
 *
 * What's sent, and only after the player has typed a link code from the app:
 * their in-game name and RuneLite's account hash, and the feed events their
 * settings allow (drops over a value, pets, collection log items, diaries,
 * combat tasks, and deaths if they turn that on).
 */
@Slf4j
@Singleton
class SpoopToolApi
{
	private static final HttpUrl BASE = HttpUrl.get("https://spooptool-api.ben-068.workers.dev/");
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	@Inject
	private OkHttpClient http;

	@Inject
	private Gson gson;

	static class Account
	{
		String name;
		String type;
	}

	static class LinkResult
	{
		String token;
		Account account;
	}

	private static class ErrorBody
	{
		String error;
	}

	/**
	 * Trade a link code for a token. One of the callbacks is called, on an
	 * OkHttp thread.
	 */
	void link(String code, long accountHash, String name, Consumer<LinkResult> onLinked, Consumer<String> onError)
	{
		JsonObject body = new JsonObject();
		body.addProperty("code", code);
		// A Java long, sent as a string: JavaScript numbers can't hold all of one.
		body.addProperty("accountHash", Long.toString(accountHash));
		body.addProperty("name", name);

		Request request = new Request.Builder()
			.url(BASE.resolve("plugin/link"))
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("SpoopTool link failed", e);
				onError.accept("couldn't reach SpoopTool. Check your connection and try a new code.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody responseBody = response.body())
				{
					String text = responseBody == null ? "" : responseBody.string();
					if (!response.isSuccessful())
					{
						onError.accept(errorMessage(text, response.code()));
						return;
					}
					LinkResult result = gson.fromJson(text, LinkResult.class);
					if (result == null || result.token == null || result.account == null)
					{
						onError.accept("SpoopTool sent something unexpected.");
						return;
					}
					onLinked.accept(result);
				}
				catch (IOException | JsonSyntaxException e)
				{
					log.debug("SpoopTool link response unreadable", e);
					onError.accept("SpoopTool sent something unexpected.");
				}
			}
		});
	}

	/**
	 * Tell SpoopTool the player logged out, so it looks them up on the hiscores
	 * now. Fire and forget: there's nothing to show the player either way.
	 */
	void logout(String token, long accountHash, String name)
	{
		JsonObject body = new JsonObject();
		body.addProperty("accountHash", Long.toString(accountHash));
		body.addProperty("name", name);

		Request request = new Request.Builder()
			.url(BASE.resolve("plugin/logout"))
			.header("Authorization", "Bearer " + token)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("SpoopTool logout ping failed", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody responseBody = response.body())
				{
					if (!response.isSuccessful())
					{
						String text = responseBody == null ? "" : responseBody.string();
						log.debug("SpoopTool logout ping refused: {}", errorMessage(text, response.code()));
					}
				}
				catch (IOException e)
				{
					log.debug("SpoopTool logout response unreadable", e);
				}
			}
		});
	}

	/**
	 * Send feed events, each `{kind, data}`. Fire and forget, like the logout
	 * ping: a lost event is a missing feed line, not something to bother the
	 * player about.
	 */
	void sendEvents(String token, List<JsonObject> events)
	{
		if (events.isEmpty())
		{
			return;
		}
		JsonArray list = new JsonArray();
		events.forEach(list::add);
		JsonObject body = new JsonObject();
		body.add("events", list);

		Request request = new Request.Builder()
			.url(BASE.resolve("plugin/events"))
			.header("Authorization", "Bearer " + token)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("SpoopTool events failed", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody responseBody = response.body())
				{
					if (!response.isSuccessful())
					{
						String text = responseBody == null ? "" : responseBody.string();
						log.debug("SpoopTool events refused: {}", errorMessage(text, response.code()));
					}
				}
				catch (IOException e)
				{
					log.debug("SpoopTool events response unreadable", e);
				}
			}
		});
	}

	/** The server's own message for a refusal, or the status code if it didn't send one. */
	private String errorMessage(String text, int status)
	{
		try
		{
			ErrorBody error = gson.fromJson(text, ErrorBody.class);
			if (error != null && error.error != null)
			{
				return error.error;
			}
		}
		catch (JsonSyntaxException e)
		{
			// Fall through to the status code.
		}
		return "SpoopTool answered " + status + ".";
	}
}
