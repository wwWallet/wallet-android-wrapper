package io.yubicolabs.funke_explorer

/**
 * @warn pollutes all classes.
 * @return name for this class, or a placeholder.
 */
inline val Any?.tagForLog: String
    get() = try {
        this?.javaClass?.simpleName ?: "<|>"
    } catch (th: Throwable) {
        "<||>"
    }