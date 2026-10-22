package com.example.scratched.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class MainAppScreenTest : MainAppScreenContract()
