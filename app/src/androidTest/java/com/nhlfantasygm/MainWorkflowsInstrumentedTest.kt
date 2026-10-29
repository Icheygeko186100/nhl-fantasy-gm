package com.nhlfantasygm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainWorkflowsInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationWorkflowsLaunchAndSwitchScreensCleanly() {
        composeRule.onNodeWithText("Home").assertIsDisplayed()

        composeRule.onNodeWithText("Draft").performClick()
        composeRule.onNodeWithText("NHL Fantasy Draft Room").assertIsDisplayed()

        composeRule.onNodeWithText("Marketplace").performClick()
        composeRule.onNodeWithText("NHL Player Marketplace").assertIsDisplayed()

        composeRule.onNodeWithText("My Team").performClick()
        composeRule.onNodeWithText("Remaining Budget").assertIsDisplayed()

        composeRule.onNodeWithText("League").performClick()
        composeRule.onNodeWithText("Matchups").assertIsDisplayed()
    }
}
