# SpoopTool RuneLite plugin

Links your Old School RuneScape account to **SpoopTool**, the clan tracker app by PhishKat Labs, and tells
SpoopTool when you log out so your stats update straight away instead of within a few hours.

## What it sends, and when

Nothing at all until you type a link code from the app. After that:

| When | Sent to SpoopTool's server |
|---|---|
| You enter a link code | The code, your in-game name and RuneLite's account hash |
| You log out | Your in-game name and account hash |

The account hash is how SpoopTool keeps your history together through a name change. Like any website, the server
also sees your IP address.

SpoopTool's server is not controlled or verified by the RuneLite developers. Nothing else is read or sent: no other
players, no chat, no inventory, no location.

## Linking

1. In the SpoopTool app, open **Settings**, then **Link the RuneLite plugin**. It shows a code like `KX7-PQM`.
2. Log in to the account you want to link.
3. Open this plugin's settings and type the code into **Link code (from the SpoopTool app)**, then press Enter.

The chatbox confirms the link. Each account you play links separately, because the link is stored in that account's
RuneLite profile.

## Running it from source

You need Java 11 or newer to compile it. The Gradle wrapper (8.10) runs on Java 21 or older, not 25.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # or the openjdk@21 path from Homebrew
./gradlew run                                      # RuneLite in developer mode, with this plugin loaded
```

**Jagex accounts** can't type a password into a developer build. You have to do this once:

1. In the RuneLite launcher, open **Configure** and add `--insecure-write-credentials` to the client arguments.
2. Start RuneLite once from the **Jagex Launcher**. That saves a login to `~/.runelite/credentials.properties`, which
   `./gradlew run` then uses.
3. Remove the argument afterwards if you like. Delete that file to sign out.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
