package com.spendwise.parser.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for transaction parsers.
 *
 * Uses the Factory pattern to select the appropriate parser
 * based on the sender ID (SMS sender or app package name).
 *
 * Usage:
 * ```
 * val result = parserRegistry.parse(smsBody, "HDFCBK", timestamp)
 * ```
 */
@Singleton
class ParserRegistry @Inject constructor(
    private val parsers: List<@JvmSuppressWildcards TransactionParser>
) {

    /**
     * Finds a parser that can handle the given sender.
     *
     * @param sender SMS sender ID (e.g., "HDFCBK") or package name
     * @return The first matching parser, or null if none found
     */
    fun getParser(sender: String): TransactionParser? {
        return parsers.firstOrNull { it.canHandle(sender) }
    }

    /**
     * Parses a message using the appropriate parser.
     *
     * @param body The SMS or notification text
     * @param sender The sender ID or package name
     * @param timestamp When the message was received
     * @return ParsedTransaction if successful, null otherwise
     */
    fun parse(body: String, sender: String, timestamp: Long): ParsedTransaction? {
        val parser = getParser(sender) ?: return null
        return parser.parse(body, sender, timestamp)
    }

    /**
     * Checks if any parser can handle the given sender.
     */
    fun canHandle(sender: String): Boolean {
        return parsers.any { it.canHandle(sender) }
    }

    /**
     * Returns all registered parsers.
     */
    fun getAllParsers(): List<TransactionParser> = parsers.toList()

    /**
     * Returns the names of all registered banks/apps.
     */
    fun getSupportedBanks(): List<String> = parsers.map { it.getBankName() }
}
