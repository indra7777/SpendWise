package com.spendwise.parser.core

/**
 * Represents the type of financial transaction.
 */
enum class TransactionType {
    /** Money spent or withdrawn */
    DEBIT,

    /** Money received or deposited */
    CREDIT,

    /** Transfer between accounts */
    TRANSFER,

    /** Investment transactions (stocks, mutual funds, SIP) */
    INVESTMENT,

    /** Transaction type could not be determined */
    UNKNOWN
}
