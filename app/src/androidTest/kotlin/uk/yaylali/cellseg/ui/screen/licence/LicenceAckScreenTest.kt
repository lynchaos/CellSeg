package uk.yaylali.cellseg.ui.screen.licence

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import uk.yaylali.cellseg.R

/**
 * UI test for [LicenceAckScreen] — verifies the checkbox-gates-button logic
 * without a real ViewModel (lambda substitution).
 */
class LicenceAckScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun acknowledgeButton_isDisabledUntilCheckboxChecked() {
        var acknowledged = false
        rule.setContent {
            // Use the bare composable without Hilt by providing a minimal UI
            // that mimics the screen structure under test.
            LicenceAckScreenContent(
                onAcknowledged = { acknowledged = true },
                onAcknowledge = {},
            )
        }

        // Button should start disabled
        rule.onNodeWithTag("ack_button").assertIsNotEnabled()

        // Tick the checkbox
        rule.onNodeWithTag("ack_checkbox").performClick()

        // Now the button should be enabled
        rule.onNodeWithTag("ack_button").assertIsEnabled()
    }

    @Test
    fun acknowledgeButton_invokesCallback_whenClicked() {
        var acknowledged = false
        rule.setContent {
            LicenceAckScreenContent(
                onAcknowledged = { acknowledged = true },
                onAcknowledge = {},
            )
        }

        rule.onNodeWithTag("ack_checkbox").performClick()
        rule.onNodeWithTag("ack_button").performClick()

        assert(acknowledged) { "onAcknowledged callback should have been invoked" }
    }
}
