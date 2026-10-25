# Donuts Auctions

A client-side Fabric mod for **DonutSMP auction research**: local price history, deal discovery, item trends, seller statistics, watchlists, and basic investment/spawner calculations.

> **Important:** This is an experimental port for Minecraft 26.2. A successful build does not mean every feature has been verified in-game. Price estimates are not guarantees, and the mod does not automatically buy listings.

## Opening the menu

- **Default key:** Numpad * (Multiply).
- If your keyboard has no numpad or that key does not work: open **Options → Controls → Key Binds**, search for **DonutsAuctions**, and change **Open Donuts Auctions** to a key you can use.
- You can also open **Options → Mods → DonutsAuctions → Config** (the exact path depends on your Mod Menu screen) to adjust the mod's configuration.

If the key does nothing, check that the mod appears in the Mods list, that there are no keybind conflicts, and that you installed the JAR built for your exact Minecraft version.

## Quick start

1. Join DonutSMP and open the auction house normally.
2. Use the server's normal auction/search commands, such as /ah or /order, and browse listings. The mod can record auction data it successfully observes; it cannot reconstruct listings it has never seen.
3. Press the menu key to open Donuts Auctions.
4. Search for an item by its name or item ID. Select a result to inspect its details.
5. Keep playing and browsing over time. The first run may show little or no history; more observations are needed before trends and deal scores become useful.

**Note:** Page navigation is manual in this port. Browse pages yourself; the mod does not automatically click through auction pages. Idle/background scanning is disabled by default.

## Main menu controls

- **Search:** Search collected item data.
- **Search / Deals mode:** Switch between item search and deal-focused results.
- **Refresh:** Reload displayed data.
- **Mod Config / Config:** Open configuration.
- **Sort:** Change result ordering.
- **Filter:** Show all results or watchlist items.
- **Lookback:** Change the time window used for relevant results.
- **Stack size:** Change the quantity used for price comparisons.
- **Overview:** View the selected item's summary and available listing statistics.
- **Trends:** Inspect recorded price history. Available windows include 25 hours, 7 days, and 30 days.
- **Sellers:** View seller-related statistics when enough data exists.
- **☆ Watch:** Add or remove the selected item from your watchlist.
- **CSV / JSON:** Export collected data where available.
- **Dev:** Toggle developer tools. Use Reindex/Vacuum only if you know why you need them.

Some panels will be empty until the database has enough observations.

## Data and files

The mod stores data locally in SQLite. Default paths (relative to your Minecraft game directory):

- Database: config/donutsauctions/auctions.db
- Exports: config/donutsauctions/exports/
- Configuration: config/donutsauctions.properties

The database is intended to persist across game restarts. Back it up before deleting or replacing it. Do not share your database if it contains information you consider private.

## Understanding prices and estimates

- Historical prices reflect listings the mod actually captured, not the entire server economy.
- Low sample counts can be misleading. Wait for more observations before relying on a trend.
- A recent median or trend is a rough reference, **not a guaranteed future price**.
- Expected profit does not account for every server fee, competition, market changes, or the time it takes to sell.
- Verify the item, quantity, and total price in-game before buying or selling. Be especially careful with suffixes such as k and m.

## Dependencies

Use versions compatible with **Minecraft 26.2**:

- Fabric Loader
- Fabric API
- Fabric Language Kotlin
- Mod Menu
- Cloth Config
- Java 25 or newer

The mod's metadata declares minimum dependency versions. If Fabric reports a dependency mismatch, update the named dependency in your launcher instance rather than installing the same mod JAR again.

## Troubleshooting

**The menu key does nothing**
- Check **Options → Controls → Key Binds** and search for DonutsAuctions.
- Assign a free key and check for conflicts.
- Confirm you installed the built JAR in the correct instance's mods folder.

**The menu opens but has no data**
- Browse the auction house and search pages normally so the mod can observe listings.
- Give it time to collect multiple observations.
- Use Refresh in the mod menu.

**Fabric says a dependency is incompatible**
- Update the dependency named in the error to a version compatible with Minecraft 26.2.
- Remove duplicate/older copies of the same dependency from the instance's mods folder.

**Something crashes or looks wrong**
- Reproduce it with the current JAR and share the Minecraft version, loader version, mod versions, and relevant part of logs/latest.log when opening a GitHub issue.

## Project status

This is an experimental community fork/port. Features may be incomplete, and the Minecraft 26.2 port has not been fully verified on a live DonutSMP session. Please report reproducible bugs through [GitHub Issues](https://github.com/fandougdoug-cpu/Donuts-Auctions/issues).

## License

MIT. See [LICENSE](LICENSE).
