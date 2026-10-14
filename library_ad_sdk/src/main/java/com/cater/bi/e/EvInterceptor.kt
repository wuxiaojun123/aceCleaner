package com.cater.bi.e

/**
 *
 * created on 2025/8/31
 * @author holmes
 */
interface EvInterceptor {

    /**
     * @return success or failure, if failure, the message of exception will be logged,
     * and the event will not be sent
     */
    fun onSend(ev: String, prop: MutableMap<String, Any>): Result<Boolean>

}