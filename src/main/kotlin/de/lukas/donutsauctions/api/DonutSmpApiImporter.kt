package de.lukas.donutsauctions.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.lukas.donutsauctions.db.AuctionRepository
import de.lukas.donutsauctions.model.AuctionListingRecord
import de.lukas.donutsauctions.parser.ItemSignatureBuilder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale

class DonutSmpApiImporter(
    private val repository: AuctionRepository,
    private val apiKey: String,
    private val signatureBuilder: ItemSignatureBuilder = ItemSignatureBuilder()
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun importPages(pageCount: Int): Int {
        require(apiKey.isNotBlank()) { "DonutSMP API key is missing. Set apiKey in config/donutsauctions.properties." }
        var imported = 0
        val pages = pageCount.coerceIn(1, 100)

        for (page in 1..pages) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.donutsmp.net/v1/auction/list/$page"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .GET()
                .build()

            val response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            if (response.statusCode() !in 200..299) {
                throw IllegalStateException("DonutSMP API returned HTTP ${response.statusCode()} on page $page")
            }

            val root = JsonParser.parseString(response.body()).asJsonObject
            val results = root.getAsJsonArray("result") ?: break
            val records = results.mapIndexedNotNull { index, entry ->
                if (entry == null || entry.isJsonNull || !entry.isJsonObject) return@mapIndexedNotNull null
                toRecord(entry.asJsonObject, page, index)
            }

            if (records.isEmpty()) break
            val hash = sha256(records.joinToString("|") { "${it.itemId}:${it.amount}:${it.totalPrice}:${it.seller}" })
            val snapshotId = repository.insertSnapshot(
                searchContext = null,
                page = page,
                sortMode = "DONUTSMP_API",
                filterMode = null,
                snapshotHash = hash,
                rawTitle = "DonutSMP API page $page"
            )
            repository.insertListings(snapshotId, records.map { it.copy(snapshotHash = hash) })
            imported += records.size
            if (results.size() < 44) break
        }
        return imported
    }

    private fun toRecord(entry: JsonObject, page: Int, slot: Int): AuctionListingRecord? {
        val item = entry.getAsJsonObject("item") ?: return null
        val itemId = string(item, "id").ifBlank { return null }
            .lowercase(Locale.ROOT)
            .removePrefix("minecraft:")
        val name = string(item, "display_name").ifBlank { itemId.replace('_', ' ') }
        val amount = int(item, "count").coerceAtLeast(1)
        val price = long(entry, "price").takeIf { it > 0L }
        val seller = when (val value = entry.get("seller")) {
            null, isJsonNull -> null
            else -> if (value.isJsonObject) {
                string(value.asJsonObject, "name").ifBlank { string(value.asJsonObject, "username") }.ifBlank { null }
            } else {
                value.asStringOrNull()
            }
        }
        val lore = mutableListOf<String>()
        item.get("lore")?.let { value ->
            if (value.isJsonArray) value.asJsonArray.forEach { if (!it.isJsonNull) lore.add(it.asStringOrNull().orEmpty()) }
            else if (!value.isJsonNull) lore.add(value.asStringOrNull().orEmpty())
        }
        item.getAsJsonObject("enchants")?.entrySet()?.forEach { (key, value) ->
            lore.add("$key ${value.asStringOrNull() ?: value}")
        }
        val signature = signatureBuilder.build(itemId, name, lore)
        val rawLore = buildString {
            append(lore.joinToString("\n"))
            append("\nAPI time_left: ")
            append(string(entry, "time_left"))
        }

        return AuctionListingRecord(
            capturedAt = System.currentTimeMillis(),
            query = "",
            page = page,
            slot = slot,
            itemId = itemId,
            itemSignature = signature,
            name = name,
            amount = amount,
            totalPrice = price,
            unitPrice = price?.toDouble()?.div(amount),
            seller = seller,
            rawLore = rawLore,
            sortMode = "DONUTSMP_API",
            filterMode = null,
            snapshotHash = ""
        )
    }

    private fun string(obj: JsonObject, key: String): String =
        obj.get(key)?.asStringOrNull().orEmpty()

    private fun int(obj: JsonObject, key: String): Int =
        obj.get(key)?.asStringOrNull()?.toIntOrNull() ?: obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 1

    private fun long(obj: JsonObject, key: String): Long =
        obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
            ?: obj.get(key)?.asStringOrNull()?.replace(",", "")?.toLongOrNull()
            ?: 0L

    private fun JsonElement.asStringOrNull(): String? = try {
        if (isJsonNull) null else if (isJsonPrimitive) asString else toString()
    } catch (_: Exception) {
        null
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
