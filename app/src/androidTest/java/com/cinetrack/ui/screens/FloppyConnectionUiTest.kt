package com.cinetrack.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.FloppyConnectionStage
import com.cinetrack.domain.FloppyConnectionUiState
import com.cinetrack.ui.components.PrimaryAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class FloppyConnectionUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun floppyConnectTapInvokesCallbackAndPublishesImmediateCheckingStage() {
        var connectionState by mutableStateOf(FloppyConnectionUiState())
        var callbackCount = 0

        composeRule.setContent {
            MaterialTheme {
                FloppyConnectionSettingsForm(
                    state = AppUiState(loading = false),
                    connectionUiState = connectionState,
                    onConnectRequested = { _, _, _ ->
                        callbackCount += 1
                        connectionState = FloppyConnectionUiState(FloppyConnectionStage.CHECKING_SERVER)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("floppy_server_url_field").performTextInput("https://floppy.example")
        composeRule.onNodeWithTag("floppy_api_key_field").performTextInput("secret-token")
        composeRule.onNodeWithTag("floppy_connect_button").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, callbackCount)
            assertEquals(FloppyConnectionStage.CHECKING_SERVER, connectionState.stage)
        }
    }

    @Test
    fun primaryActionInvokesEnabledClickAndSuppressesDisabledClick() {
        var clickCount = 0
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                PrimaryAction(
                    text = "Connect",
                    modifier = Modifier.testTag("primary_action_test"),
                    enabled = enabled,
                    onClick = { clickCount += 1 },
                )
            }
        }
        composeRule.onNodeWithTag("primary_action_test").performClick()
        composeRule.runOnIdle {
            assertEquals(1, clickCount)
            enabled = false
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("primary_action_test").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }
}

