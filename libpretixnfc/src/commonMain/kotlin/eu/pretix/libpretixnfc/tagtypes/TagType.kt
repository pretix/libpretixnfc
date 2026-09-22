package eu.pretix.libpretixnfc.tagtypes

/**
 * Marker for tag types that can be passed between application components. On the JVM this is
 * `java.io.Serializable`.
 */
expect interface Serializable

open class TagType(
    val chipName: String,
    val userDataSize: Int,
) : Serializable
