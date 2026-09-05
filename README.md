# HavocAuction

A player auction house for **Paper / Purpur 1.21.7+**, built entirely on Minecraft's native
**Dialog API**. Same architecture and conventions as HavocOrders — no chest GUIs anywhere.

Players list the item in their hand for a price, anyone can buy it instantly, and sellers
get paid, notified, and keep a full transaction history.

## Requirements

- **Server:** Paper or Purpur **1.21.7+**
- **Client:** Minecraft **1.21.6+** — dialogs do not render on older clients
- **Vault** plus an economy provider
- JDK 21 to build

## Screens

| Dialog | What it does |
| --- | --- |
| Auction | The board: paged, six sort modes, nine category filters, private search |
| Buy | Confirmation with price, per-item price, and your balance before/after |
| Container Preview | What's inside a shulker box, *before* you buy it |
| Your Items | Live listings, total value, and the way into everything else |
| Manage Listing | Pull a listing off the board |
| Sell | Lists your held item; price field with a per-item helper |
| Confirm Listing | Fee and payout before you commit |
| Collect | Items from cancelled and expired listings |
| History | Sales and purchases with lifetime earned/spent/net |

## Commands

| Command | Description |
| --- | --- |
| `/ah` | Open the auction board |
| `/ah sell <price>` | List the held item without opening anything |
| `/ah reload` | Reload config and dialogs |
| `/ah import [file]` | Import a legacy DonutAuction database |

Permissions: `havocauction.use` (default true), `havocauction.admin` (default op).

## Economy

- **No escrow.** The buyer pays at the moment of purchase and the seller is paid then.
- `LISTING-FEE-PERCENT` / `LISTING-FEE-FLAT` charge the seller up front, win or lose.
- `TAX-PERCENT` takes a cut of the sale before the seller is paid.
- `BROADCAST-PRICE-THRESHOLD` announces expensive listings server-wide.

A purchase claims the listing *before* any money or items move, and rolls the claim back if
the withdrawal fails. Two players clicking the same listing cannot both win it.

## Number shorthand

Prices accept `1k`, `2.5k`, `1m`, `3b`, `$1,250`. Display abbreviates to `1.23m`; turn that
off with `AUCTION.ABBREVIATE-NUMBERS: false` and input shorthand still works.

## Java and Bedrock

Geyser translates these dialogs into Bedrock forms, so Bedrock players get the real UI
rather than a fallback. Two things do not survive that translation, and both are handled:

**Text fields can come back empty.** This is a confirmed Geyser bug
([GeyserMC/Geyser#6377](https://github.com/GeyserMC/Geyser/issues/6377)): `getText` returns
`""` on Bedrock while working on Java. Every field here now has a safe default instead of a
dead end:

| Field | Blank input does |
| --- | --- |
| Deliver amount | Delivers everything you can |
| Order amount / price | Keeps the current value |
| Auction price | Keeps the price already set, else points at `/ah sell <price>` |
| Search | Keeps the previous search, and tells Bedrock players the command form |

Nothing is reachable only by typing into a dialog. `/orders search <text>`,
`/ah search <text>` and `/ah sell <price>` all work from Bedrock chat, and the quick-amount
buttons on the deliver screen cover the common cases with no typing at all.

**Buttons have no hover text on Bedrock**, so tooltips are invisible there — which would
have hidden things that matter, like the renamed-item warning and durability. For Bedrock
players the tooltip is folded into the button label instead.

Bedrock's font also lacks the small-caps glyphs the configs use (`ᴏʀᴅᴇʀѕ`), so those are
rewritten to plain ASCII for those players only.

```yaml
BEDROCK:
  ASCII-LABELS: true
  INLINE-TOOLTIPS: true
```

Detection goes through Floodgate by reflection — no compile-time dependency, and a server
without Floodgate simply treats everyone as Java. Java players see no difference either
way; both adaptations are per-viewer.

## Search privacy

Search uses the dialog's own text field, so it goes client-to-server with the button click
and never appears in chat or in chat-logging plugins. Same for the history search and the
sell price field.

## Item previews

Every listing shows the item as a real icon, not just text: on the buy screen, your
listings, and the preview screen. Size is configurable.

The **Preview** button appears on any listing that has something more to show — a shulker,
a map, enchantments, or durability. Plain cobblestone does not get a button that would tell
you nothing. Preview shows:

- **Shulker boxes** — every stack inside, each as its own icon with a label
- **Filled maps** — map id, scale, world and locked state, plus a **View Map Art** button
- **Signed books** — title, who signed it, generation (original vs copy of a copy), page
  count, and a snippet of the first page
- **Enchanted items** — the full enchantment list, including stored enchants on books
- **Damaged items** — durability remaining

```yaml
DIALOG:
  PREVIEW-ITEM-SIZE: 96
  PREVIEW-CONTENT-SIZE: 40
  PREVIEW-MAX-CONTENTS: 27
  PREVIEW-BOOK-CHARS: 160
```

### Map art

A vanilla client only draws map art for a map it is actually holding. It cannot be drawn
in a dialog or an item tooltip — the client-side mods that add map tooltips exist precisely
because the game does not do it, and a server plugin cannot make the client render
something it has no code for.

So **View Map Art** lends you the map: it goes into your off hand for ten seconds and is
then taken back. That works on any client, Java or Bedrock, with no mods.

The loaned map is fenced in, because a borrowed item is a duplication risk:

- tagged in its item data, so it can always be identified
- cannot be dropped, moved, swapped or clicked
- removed from your drops if you die holding it
- returned on logout, and purged on login in case the server stopped mid-preview
- your original off-hand item is restored, or given back to your inventory if the slot got
  taken in the meantime

```yaml
DIALOG:
  MAP-PREVIEW-SECONDS: 10
```

If your players are on Fabric or Forge, the [Map Tooltip](https://modrinth.com/mod/map-tooltip)
client mod shows map art on hover with no borrowing at all. It is client-side, so it is
each player's choice, and this button remains the fallback for everyone else.

**On right-click:** dialog buttons only have one click action — the API has no separate
right-click, and Geyser could not map it to a Bedrock form button anyway. The Preview
button is that feature, one click instead of two.

## Searching

`/ah <anything>` searches straight from chat — no menu, no typing into a dialog field:

```
/ah elytra
/ah naxzyauxxy signed book
/ah sharpness 5
/ah map art
```

**Every word has to match**, so `naxzyauxxy signed book` finds books connected to that
player, not everything containing "book". Words are matched against:

- the real item type and material name
- the seller's name
- configured aliases
- enchantments, by name and level (`sharpness 5` and `sharpness v` both work)
- **a signed book's author** — who actually signed it, which the server sets and a player
  cannot fake

Custom item names and book titles are still excluded, because both are attacker-controlled
text. Author is not: you can only sign a book as yourself.

### Aliases

Players search for "signed book", not "Written Book". Aliases add extra handles without
changing what an item is called:

```yaml
AUCTION:
  SEARCH-ALIASES:
    WRITTEN_BOOK: [ "signed book", "book" ]
    FILLED_MAP: [ "map art", "mapart", "map" ]
    ENCHANTED_GOLDEN_APPLE: [ "god apple", "notch apple", "gapple" ]
```

Each listing bakes its aliases into a search index once when it loads, so searching never
walks item metadata. Editing aliases applies to new listings immediately and to existing
ones after a restart.

`/orders <anything>` works the same way, matching item type and the order owner.

## Renamed items and search

Search matches the **real item type**, never the custom display name. Otherwise anyone can
rename a block of dirt to "Elytra", list it for millions, and have it answer every elytra
search — the display name is attacker-controlled text, so it is not something to key a
search on.

Renamed listings are also flagged wherever they appear: the button label gets the real type
appended, and the tooltip carries a red warning line plus a `Type:` row.

```yaml
AUCTION:
  SEARCH-CUSTOM-NAMES: false
```

Turning it on makes search match custom names too. Only do that if you accept the above.
The seller's name is always searchable either way.

## Durability

Damaged items show their durability on the board, the buy screen, your listings and the
collect screen: `Durability: 384/432 (89%)`.

The row is a template in `dialogs.yml`, and lines that resolve to nothing are dropped, so
items without durability simply have no durability row rather than an empty gap:

```yaml
LINES:
  DURABILITY: "&7Durability: &f{durability} &8({durability_percent}%)"
  RENAMED: "&c! &7Renamed. Actually a &f{type}"
  RENAMED-TAG: " &8({type})"
```

Placeholders: `{durability}`, `{durability_percent}`, `{type}`, `{custom_name}`,
`{renamed}`, `{durability_line}`, `{renamed_line}`, `{renamed_tag}`.

## Fast mode

Carried over from the legacy `fast_auction` flag: toggling it on Your Items skips both the
purchase and listing confirmation screens. Off by default.

## Selling

Dialogs have no item slot, so the held item is the input — the same model as `/ah sell`.
That removes a whole class of duplication bugs that come with a deposit slot. The item only
leaves your hand once the listing is stored.

The Sell screen also has a **per-item** button: type `500`, hit it, and a stack of 64 is
priced at 32,000.

## Durability of data, and why writes are immediate

Anything that hands a player an item or moves money is written to the database straight
away, not on the periodic timer. The timer still exists for low-risk updates, and bursts
are collapsed into a single batch, but the window between "player has the item" and
"database knows" is now milliseconds rather than up to 30 seconds.

That gap was a real duplication bug: on a crash, `kill`, or a plugin-manager unload, the
unwritten records came back on restart while the player already had the goods.

`SAVE-INTERVAL-SECONDS` is now only a safety net. Lowering it is no longer how you protect
against duplication.

## Startup and load order

The plugin no longer disables itself when Vault has no economy provider yet. Economy
plugins register their Vault service during their own enable, so load order alone could
leave this plugin dead until it was reloaded by hand. It now waits, retrying once a second
for a minute, and logs when it hooks in. Commands report the missing economy until then.

## Live config reloading

`config.yml` and `dialogs.yml` are re-read when their timestamps change, so edits apply
without a restart or a reload command:

```yaml
RELOAD-WATCH-SECONDS: 5   # 0 disables
```

This covers settings and menu text only. It does not touch orders, listings or money, and
it is unrelated to duplication.

## Updating

New settings from a plugin update are written into your existing `config.yml` and
`dialogs.yml` on startup. Your values are never changed, nothing is removed, and the
previous file is saved as `config.yml.bak`. The console lists every key it added.

```yaml
AUTO-UPDATE-CONFIG: true   # set false to manage the files yourself
```

Two things it deliberately does not do: it will not change a value you have already set,
and it will not delete keys it does not recognise, since those are assumed to be yours.
So a *new* setting appears by itself, but a *changed default* is still yours to apply.

## Performance

- Every listing lives in memory; the database is only ever written to, in batches.
- Writes go into a dirty set flushed by one async transaction every 30s.
- Listings are indexed by seller and by buyer, so Your Items and History never scan.
- The board caches its filtered, sorted result per player behind a version counter — a page
  turn is a list slice.
- Item names, materials and stack sizes are computed once at load, so filtering and
  searching never decode an item.
- Drops release a few stacks per tick rather than all at once.
- `HISTORY-KEEP-DAYS` (default 30) purges old sold rows. Each carries a serialised item, so
  this is the memory dial — raise it for a longer log, lower it on a busy server.

## External settings menus (PlaceholderAPI)

Registers the `havocauction` expansion when PlaceholderAPI is installed, plus standalone
toggle commands, so a settings menu can drive both preferences directly.

| Placeholder | Value |
| --- | --- |
| `%havocauction_alerts_status%` | Styled ON / OFF |
| `%havocauction_fast_status%` | Styled ON / OFF for fast mode |
| `%havocauction_alerts_raw%` / `%havocauction_fast_raw%` | `true` / `false` |
| `%havocauction_listings%` | Live listings |
| `%havocauction_collectable%` | Listings waiting to collect |
| `%havocauction_listed_value%` | Asking value of your live listings |
| `%havocauction_total_made%` / `%havocauction_total_spent%` / `%havocauction_net%` | Lifetime totals |
| `%havocauction_sales%` / `%havocauction_purchases%` | Lifetime counts |
| `%havocauction_board_size%` | Listings on the board right now (no player needed) |

Commands: `/toggleauctionalerts` (alias `/ahalerts`), `/togglefastauction` (alias
`/fastauction`).

The ON/OFF text is config-driven, since it renders inside whatever menu plugin reads it:

```yaml
PLACEHOLDERS:
  ENABLED-TEXT: "<green>ON"
  DISABLED-TEXT: "<red>OFF"
```

Use `&a` / `&c` instead if your menu expects legacy colour codes, or plain `ON` / `OFF`.

## Importing from DonutAuction

Drop the old `auction.db` into `plugins/HavocAuction/` as `import.db` and start the server,
or run `/ah import`. Legacy ids are preserved, so re-running skips anything already there.

The old schema stores UUIDs as raw 16-byte blobs and items as `BukkitObjectOutputStream`
dumps; both are read directly.

| Legacy status | Becomes |
| --- | --- |
| `ACTIVE` | Back on the board |
| `CANCELLED` | Item waiting in the seller's collect screen |
| `SOLD` | History row — feeds the transaction log and lifetime totals |

`auction_profiles.alerts` and `fast_auction` come across as the two toggles on Your Items.

**The importer moves no money and hands out no items.** Sold rows are history only; the old
plugin already settled that money.

```yaml
IMPORT:
  IMPORT-HISTORY: true    # false for a clean start with no transaction log
  EXPIRY:
    MODE: EXTEND          # or KEEP: import as expired, item waits for collection
    EXTEND-DAYS: 7
```

Remove the old plugin first so the two are not running against one economy.

## Config files

- `config.yml` — database, economy, fees, limits, history retention, import, messages
- `dialogs.yml` — every title, body line, button label and tooltip, with `{placeholders}`,
  hex colours, and per-dialog `COLUMNS`

## Build

```
mvn clean package
```

Jar lands in `target/HavocAuction-1.0.0.jar`. CI is in `.github/workflows/build.yml`.
