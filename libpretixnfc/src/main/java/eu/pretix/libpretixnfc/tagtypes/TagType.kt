package eu.pretix.libpretixnfc.tagtypes

import java.io.Serializable

open class TagType(
    val chipName: String,
    val userDataSize: Int,
) : Serializable
