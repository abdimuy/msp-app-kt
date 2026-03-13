package com.example.msp_app.`test-fixtures`

import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
abstract class RobolectricTestBase {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
}
