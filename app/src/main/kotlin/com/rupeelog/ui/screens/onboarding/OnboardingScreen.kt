package com.rupeelog.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val lottieUrl: String,
    val isSignInPage: Boolean = false,
    val isPermissionPage: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onRequestPermission: () -> Unit,
    onSignIn: suspend () -> Boolean,
    isSignedIn: Boolean = false
) {
    val pages = listOf(
        OnboardingPage(
            title = "Welcome to RupeeLog",
            description = "Your smart companion for tracking expenses automatically. No manual entry needed!",
            lottieUrl = "https://assets3.lottiefiles.com/packages/lf20_06a6pf9i.json"
        ),
        OnboardingPage(
            title = "AI-Powered Categorization",
            description = "We automatically categorize your transactions using AI. Food, shopping, bills - all sorted instantly.",
            lottieUrl = "https://assets9.lottiefiles.com/packages/lf20_xyadoh9h.json"
        ),
        OnboardingPage(
            title = "Your Data, Your Device",
            description = "Privacy first! All your transactions stay on your device. We never upload your financial data to any server.",
            lottieUrl = "https://assets2.lottiefiles.com/packages/lf20_ky24lkyk.json",
        ),
        OnboardingPage(
            title = "Sign in to Continue",
            description = "Sign in with Google to enable secure backup and sync across devices. Your transaction data stays private.",
            lottieUrl = "https://assets10.lottiefiles.com/packages/lf20_mjlh3hcy.json",
            isSignInPage = true
        ),
        OnboardingPage(
            title = "Enable Notifications",
            description = "Allow notification access to capture UPI payments from PhonePe, Google Pay, Paytm & more.",
            lottieUrl = "https://assets2.lottiefiles.com/packages/lf20_sdgprfve.json",
            isPermissionPage = true
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val currentPage = pages[pagerState.currentPage]

    var isSigningIn by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var hasSignedIn by remember { mutableStateOf(isSignedIn) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Skip button - only show on non-critical pages and before sign-in page
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                val canSkip = !currentPage.isSignInPage &&
                              !currentPage.isPermissionPage &&
                              pagerState.currentPage < 3
                if (canSkip) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                // Skip to sign-in page (index 3)
                                pagerState.animateScrollToPage(3)
                            }
                        }
                    ) {
                        Text("Skip", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = !currentPage.isSignInPage || hasSignedIn
            ) { page ->
                OnboardingPageContent(
                    page = pages[page],
                    hasSignedIn = hasSignedIn
                )
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                    )
                }
            }

            // Bottom buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                when {
                    // Sign-in page
                    currentPage.isSignInPage -> {
                        if (hasSignedIn) {
                            // Already signed in - show continue button
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Continue",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            // Show sign-in button
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isSigningIn = true
                                        signInError = null
                                        try {
                                            val success = onSignIn()
                                            if (success) {
                                                hasSignedIn = true
                                                // Auto-advance to next page
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            } else {
                                                signInError = "Sign-in failed. Please try again."
                                            }
                                        } catch (e: Exception) {
                                            signInError = e.message ?: "Sign-in failed"
                                        } finally {
                                            isSigningIn = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                enabled = !isSigningIn,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isSigningIn) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    // Google icon placeholder
                                    Text(
                                        "G",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Sign in with Google",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Error message
                            signInError?.let { error ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Privacy note
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Your financial data never leaves your device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // Permission page (last page)
                    currentPage.isPermissionPage -> {
                        Button(
                            onClick = {
                                onRequestPermission()
                                onComplete()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                "Enable & Get Started",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Skip for now option
                        TextButton(
                            onClick = onComplete,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "I'll do this later",
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // Regular pages
                    else -> {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                "Next",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    hasSignedIn: Boolean = false
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Url(page.lottieUrl)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lottie Animation
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier
                .size(280.dp)
                .padding(bottom = 32.dp)
        )

        // Title with checkmark for sign-in page if signed in
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (page.isSignInPage && hasSignedIn) "Signed In!" else page.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (page.isSignInPage && hasSignedIn)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onBackground
            )
            if (page.isSignInPage && hasSignedIn) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Signed in",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = if (page.isSignInPage && hasSignedIn)
                "You're all set! Your account is connected for secure backup."
            else
                page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            lineHeight = 24.sp
        )

        // Extra info for permission page
        if (page.isPermissionPage) {
            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Text(
                    text = "Supported: PhonePe, Google Pay, Paytm, Amazon Pay, BHIM & Bank SMS",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Privacy highlights for the privacy page
        if (page.title == "Your Data, Your Device") {
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrivacyFeature("100% offline transaction storage")
                PrivacyFeature("On-device AI categorization")
                PrivacyFeature("Optional encrypted backup")
                PrivacyFeature("No ads, no data selling")
            }
        }
    }
}

@Composable
private fun PrivacyFeature(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
    }
}
