package com.example.scratched.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.scratched.*
import com.example.scratched.onboarding.*
import com.example.scratched.ui.theme.ScratchedTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

abstract class MainAppScreenContract {
    @get:Rule val compose = createComposeRule()

    @Test fun idleScreen_enablesStartAndDisablesStop() {
        var starts = 0
        compose.setContent { ScratchedTheme(dynamicColor = false) { MainAppScreen(onStartScan = { starts++ }) } }
        compose.onAllNodesWithText("Ready to scan").assertCountEquals(2)
        compose.onNodeWithText("Stop").assertIsNotEnabled()
        compose.onNodeWithText("Start scanning").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
    }
    @Test fun scanningScreen_disablesStartAndInvokesStop() {
        var stops = 0
        compose.setContent { ScratchedTheme(dynamicColor = false) {
            MainAppScreen(uiState = MainScreenUiState(scanStatus = BleScanStatus.SCANNING), onStopScan = { stops++ })
        } }
        compose.onNodeWithText("Start scanning").assertIsNotEnabled()
        compose.onNodeWithText("Stop").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, stops) }
    }
    @Test fun revokedPermissions_updateVisibleWarningAndStartAvailability() {
        val state = mutableStateOf(MainScreenUiState())
        compose.setContent { ScratchedTheme(dynamicColor = false) { MainAppScreen(uiState = state.value) } }
        compose.waitForIdle()
        compose.runOnIdle {
            state.value = state.value.copy(hasRequiredPermissions = false)
            // This synthetic external state write must reach the recomposer before advancing frames.
            Snapshot.sendApplyNotifications()
        }
        compose.onNodeWithText("Start scanning").assertIsNotEnabled()
        compose.onNodeWithText("Required Bluetooth permissions are missing. Grant them to scan for nearby devices.").assertExists()
    }
    @Test fun disabledBluetooth_disablesStartAndShowsWarning() {
        compose.setContent { ScratchedTheme(dynamicColor = false) {
            MainAppScreen(uiState = MainScreenUiState(isBluetoothEnabled = false))
        } }
        compose.onNodeWithText("Start scanning").assertIsNotEnabled()
        compose.onNodeWithText("Bluetooth is turned off. Enable Bluetooth to start scanning.").assertExists()
    }
    @Test fun errors_areVisible() {
        compose.setContent { ScratchedTheme(dynamicColor = false) {
            MainAppScreen(uiState = MainScreenUiState(scanStatus = BleScanStatus.ERROR, errorMessage = "Radio unavailable"))
        } }
        compose.onNodeWithText("Radio unavailable").assertExists()
    }
    @Test fun deviceCard_displaysResultAndPassesSelectedDevice() {
        val device = BleDeviceUiModel("AA:BB:CC:DD:EE:01", "PHOENIX", -45, -12,
            emptyList(), emptyMap(), false, 1000, 2000, 2)
        var selected: BleDeviceUiModel? = null
        compose.setContent { ScratchedTheme(dynamicColor = false) {
            MainAppScreen(uiState = MainScreenUiState(devices = listOf(device), discoveredDeviceCount = 1), onDeviceClick = { selected = it })
        } }
        compose.onNodeWithText(device.address).assertExists()
        compose.onNodeWithText("No").assertExists()
        compose.onNodeWithText("PHOENIX").performClick()
        compose.runOnIdle { assertEquals(device, selected) }
    }
    @Test fun deviceMetadata_formatsServiceAndManufacturerIdentifiers() {
        val uuid = "0000180f-0000-1000-8000-00805f9b34fb"
        val device = BleDeviceUiModel("AA:BB:CC:DD:EE:02", "Radio", -70, -10,
            listOf(uuid), mapOf(0x1234 to byteArrayOf(1)), true, 61000, 62000, 7)
        compose.setContent { ScratchedTheme(dynamicColor = false) {
            MainAppScreen(uiState = MainScreenUiState(devices = listOf(device), discoveredDeviceCount = 1))
        } }
        compose.onNodeWithText(uuid).assertExists()
        compose.onNodeWithText("0x1234").assertExists()
        compose.onNodeWithText("-10 dBm").assertExists()
        compose.onNodeWithText("Yes").assertExists()
        compose.onNodeWithText("01:01").assertExists()
        compose.onNodeWithText("01:02").assertExists()
        compose.onNodeWithText("7").assertExists()
    }
    @Test fun missingDeviceMetadata_hasReadableFallbacks() {
        val device = BleDeviceUiModel("AA:BB:CC:DD:EE:03", null, -85, null,
            emptyList(), emptyMap(), null, 0, 0, 1)
        compose.setContent { ScratchedTheme(dynamicColor = false) {
            MainAppScreen(uiState = MainScreenUiState(devices = listOf(device)))
        } }
        compose.onNodeWithText("Unknown device").assertExists()
        compose.onNodeWithText("Not advertised").assertExists()
        compose.onNodeWithText("Unknown").assertExists()
        compose.onAllNodesWithText("None", useUnmergedTree = true).assertCountEquals(2)
    }
    @Test fun stoppedScan_showsStatusAndAllowsNewScan() {
        compose.setContent { ScratchedTheme(dynamicColor = false) {
            MainAppScreen(uiState = MainScreenUiState(scanStatus = BleScanStatus.STOPPED))
        } }
        compose.onAllNodesWithText("Scan stopped").assertCountEquals(2)
        compose.onNodeWithText("Start scanning").assertIsEnabled()
        compose.onNodeWithText("Stop").assertIsNotEnabled()
    }
    @Test fun restartButton_invokesCallback() {
        var restarts = 0
        compose.setContent { ScratchedTheme(dynamicColor = false) { MainAppScreen(onRestartOnboarding = { restarts++ }) } }
        compose.onNodeWithText("Run onboarding again").performClick()
        compose.runOnIdle { assertEquals(1, restarts) }
    }
    @Test fun notificationSkip_isSeparateFromPermissionRequest() {
        var skips = 0
        var requests = 0
        compose.setContent { ScratchedTheme(dynamicColor = false) {
            NotificationPermissionScreen(onContinue = { requests++ }, onSkip = { skips++ })
        } }
        compose.onNodeWithText("Skip for now").performClick()
        compose.runOnIdle { assertEquals(1, skips); assertEquals(0, requests) }
    }
    @Test fun manualPermissionsScreen_hasSeparateSettingsAndRecheckActions() {
        var settings = 0
        var rechecks = 0
        compose.setContent { ScratchedTheme(dynamicColor = false) {
            GrantPermissionsManually(onContinue = { settings++ }, onCheck = { rechecks++ })
        } }
        compose.onNodeWithText("Grant permissions manually").performClick()
        compose.onNodeWithText("Check permissions").performClick()
        compose.runOnIdle { assertEquals(1, settings); assertEquals(1, rechecks) }
    }
}
