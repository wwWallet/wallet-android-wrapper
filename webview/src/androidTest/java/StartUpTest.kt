package com.yubico.ykbrowser

import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.yubicolabs.funke_explorer.BuildConfig
import io.yubicolabs.funke_explorer.MainActivity
import io.yubicolabs.funke_explorer.bridging.JSCodeSnippet
import io.yubicolabs.funke_explorer.bridging.WalletJsBridge.Companion.JAVASCRIPT_BRIDGE_NAME
import io.yubicolabs.funke_explorer.credentials.NavigatorCredentialsContainer
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class StartUpTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val timeoutRule = Timeout(2000, TimeUnit.SECONDS)

    @Test
    fun appHasCorrectPackage() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.yubicolabs.funke_explorer", appContext.packageName)
    }

    @Test
    fun javascriptInjectionIsCorrect() {
        waitForNavigatorCredentials()

        val latch = CountDownLatch(1)
        var error: String? = null
        Thread {
            try {
                onView(withClassName(containsString("WebView"))).check { view: View?, exception: NoMatchingViewException? ->
                    assertNull(exception)
                    assertTrue(view is WebView)
                    val webView = view as WebView

                    activityRule.scenario.onActivity {
                        val injectionSnippet = JSCodeSnippet.fromRawResource(
                            context = webView.context,
                            resource = "injectjs.js",
                            replacements = listOf(
                                "JAVASCRIPT_BRIDGE" to JAVASCRIPT_BRIDGE_NAME,
                                "JAVASCRIPT_VISUALIZE_INJECTION" to "${BuildConfig.VISUALIZE_INJECTION}"
                            )
                        )

                        // add syntax exception handler
                        webView.evaluateJavascript("err = '';window.onerror = (e) => err += String(e);") {}

                        // execute snipped injection, and catch errors
                        webView.evaluateJavascript("try {\n${injectionSnippet.code}\n} catch (e) {\nerr += 'Exception: ' + JSON.stringify(e);\n}") {}

                        webView.evaluateJavascript("err") {
                            error = it
                            latch.countDown()
                        }
                    }
                }
            } catch (th: Throwable) {
                error = "Error while Testing: $th"
                latch.countDown()
            }
        }.start()

        latch.await()
        assertEquals("JS bridge error.", "\"\"", error)
    }

    private fun waitForNavigatorCredentials() {
        var webNavigator: String? = null
        while (webNavigator == null) {
            val latch = CountDownLatch(1)
            webNavigator = null
            Thread {
                try {
                    onView(withClassName(containsString("WebView"))).check { view: View?, exception: NoMatchingViewException? ->
                        assertNull(exception)
                        assertTrue(view is WebView)

                        val webView = view as WebView
                        activityRule.scenario.onActivity {
                            webView.evaluateJavascript("navigator.credentials") {
                                webNavigator = if (it == "null") {
                                    null
                                } else {
                                    it
                                }
                                latch.countDown()
                            }
                        }
                    }
                } catch (th: Throwable) {
                    webNavigator = null
                    latch.countDown()
                }
            }.start()
            latch.await()
        }
    }
}
