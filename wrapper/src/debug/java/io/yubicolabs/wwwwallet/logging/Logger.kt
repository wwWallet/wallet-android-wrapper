package io.yubicolabs.wwwwallet.logging

import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * You Only Log Once Logger
 */
object YOLOLogger {
    private val messages: MutableList<String> = mutableListOf()

    fun append(
        message: String,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        messages += "$timestamp: $message"
    }

    fun messages(): List<String> = ArrayList(messages)

    fun e(
        tag: String,
        message: String,
    ) = append("$tag: $message")

    fun e(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = append("$tag: $message (${throwable.stackTraceToString()})")

    fun w(
        tag: String,
        message: String,
    ) = append("$tag: $message")

    fun i(
        tag: String,
        message: String,
    ) = append("$tag: $message")

    fun i(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = append("$tag: $message (${throwable.stackTraceToString()})")

    fun d(
        tag: String,
        message: String,
    ) = append("$tag: $message")
}

class Logger : LogcatAppender() {
    override fun append(event: ILoggingEvent?) {
        super.append(event)

        event?.let {
            YOLOLogger.append(
                message = it.formattedMessage,
                timestamp = it.timeStamp,
            )
        }
    }
}
