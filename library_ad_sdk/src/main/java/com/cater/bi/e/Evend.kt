package com.cater.bi.e

/** A destination that receives app analytics events. */
interface Evend {
    fun name(): String

    fun send(event: String, properties: Map<String, Any>)
}
