package com.rupeelog.parser.di

import com.rupeelog.parser.bank.*
import com.rupeelog.parser.core.GenericParser
import com.rupeelog.parser.core.ParserRegistry
import com.rupeelog.parser.core.TransactionParser
import com.rupeelog.parser.upi.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing transaction parser dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object ParserModule {

    /**
     * Provides all available transaction parsers.
     * Add new parsers here as they are implemented.
     */
    @Provides
    @Singleton
    fun provideTransactionParsers(): List<TransactionParser> = listOf(
        // Indian Banks
        HDFCParser(),
        ICICIParser(),
        SBIParser(),
        AxisParser(),
        KotakParser(),
        CanaraParser(),
        AirtelParser(),

        // UPI Apps
        PhonePeParser(),
        GooglePayParser(),
        AmazonPayParser(),
        PaytmParser(),
        WhatsAppPayParser(),

        // Generic fallback (must be last)
        GenericParser()
    )

    /**
     * Provides the parser registry for sender-based parser selection.
     */
    @Provides
    @Singleton
    fun provideParserRegistry(
        parsers: List<@JvmSuppressWildcards TransactionParser>
    ): ParserRegistry {
        return ParserRegistry(parsers)
    }
}
