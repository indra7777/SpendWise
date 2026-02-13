package com.spendwise.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReferralRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val auth: Auth
) {

    suspend fun getMyReferralCode(): String? {
        val userId = auth.currentUserOrNull()?.id ?: return null
        return try {
            val profile = supabase.postgrest["profiles"]
                .select {
                    filter {
                        eq("id", userId)
                    }
                }.decodeSingleOrNull<ProfileDto>()
            profile?.referralCode
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun redeemReferralCode(code: String): Result<Boolean> {
        val userId = auth.currentUserOrNull()?.id ?: return Result.failure(Exception("Not logged in"))
        
        return try {
            // 1. Find the referrer
            val referrer = supabase.postgrest["profiles"]
                .select {
                    filter {
                        eq("referral_code", code)
                    }
                }.decodeSingleOrNull<ProfileDto>() ?: return Result.failure(Exception("Invalid code"))

            if (referrer.id == userId) return Result.failure(Exception("Cannot refer yourself"))

            // 2. Update my profile
            supabase.postgrest["profiles"].update(
                {
                    set("referred_by", referrer.id)
                }
            ) {
                filter {
                    eq("id", userId)
                }
            }
            
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("referral_code") val referralCode: String?,
    @SerialName("referred_by") val referredBy: String?
)
