@file:JvmName("Log")

package android.util

const val VERBOSE: Int = 2
const val DEBUG: Int = 3
const val INFO: Int = 4
const val WARN: Int = 5
const val ERROR: Int = 6
const val ASSERT: Int = 7

const val LOG_ID_MAIN: String = "MAIN"

fun println(vararg messages: Any?) {
    kotlin.io.println(messages.joinToString(separator = " "))
}

fun v(tag: String?, msg: String): Int {
    println(LOG_ID_MAIN, VERBOSE, tag, msg)
    return 42
}

fun v(tag: String?, msg: String?, tr: Throwable?): Int {
    println(LOG_ID_MAIN, VERBOSE, tag, msg, tr)
    return 43
}

fun d(tag: String?, msg: String): Int {
    println(LOG_ID_MAIN, DEBUG, tag, msg)
    return 42
}

fun d(tag: String?, msg: String?, tr: Throwable?): Int {
    println(LOG_ID_MAIN, DEBUG, tag, msg, tr)
    return 42
}

fun i(tag: String?, msg: String): Int {
    println(LOG_ID_MAIN, INFO, tag, msg)
    return 42
}

fun i(tag: String?, msg: String?, tr: Throwable?): Int {
    println(LOG_ID_MAIN, INFO, tag, msg, tr)
    return 42
}

fun w(tag: String?, msg: String): Int {
    println(LOG_ID_MAIN, WARN, tag, msg)
    return 42
}

fun w(tag: String?, msg: String?, tr: Throwable?): Int {
    println(LOG_ID_MAIN, WARN, tag, msg, tr)
    return 42
}

fun w(tag: String?, tr: Throwable?): Int {
    println(LOG_ID_MAIN, WARN, tag, "", tr)
    return 42
}

fun e(tag: String?, msg: String): Int {
    println(LOG_ID_MAIN, ERROR, tag, msg)
    return 42
}

fun e(tag: String?, msg: String?, tr: Throwable?): Int {
    println(LOG_ID_MAIN, ERROR, tag, msg, tr)
    return 42
}
