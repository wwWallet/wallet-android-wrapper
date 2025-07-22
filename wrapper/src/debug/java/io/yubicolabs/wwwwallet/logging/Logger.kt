package io.yubicolabs.wwwwallet.logging

import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * You Only Log Once Logger
 */
object YOLOLogger {
    private val messages: MutableList<String> = mutableListOf()

    fun append(
        timestamp: Long,
        message: String,
    ) {
        messages += "$timestamp: $message"
    }

    // TODO: Move Log.e to sl4j: All logs in one place.
    fun messages(): List<String> = ArrayList(messages)
}

class Logger : LogcatAppender() {
    override fun append(event: ILoggingEvent?) {
        super.append(event)

        event?.let {
            YOLOLogger.append(it.timeStamp, it.formattedMessage)
        }
    }
}
