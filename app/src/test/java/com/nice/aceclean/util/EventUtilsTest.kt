package com.nice.aceclean.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventUtilsTest {
    private val clickEvents = EventUtils::class.java.fields
        .filter { it.name.startsWith("CLICK_") && it.type == String::class.java }
        .associate { it.name to it.get(null) as String }

    @Test
    fun `click event names satisfy analytics naming constraints`() {
        assertFalse("事件目录不能为空", clickEvents.isEmpty())
        clickEvents.forEach { (constant, event) ->
            assertTrue("$constant 的事件名称格式无效：$event", event.matches(Regex("click_[a-z0-9_]+")))
            assertTrue("$constant 的事件名称超过 40 个字符：$event", event.length <= 40)
        }
    }

    @Test
    fun `different event constants do not accidentally share a name`() {
        val duplicates = clickEvents.entries.groupBy { it.value }.filterValues { it.size > 1 }
        assertEquals("不同操作不应意外合并到同一个事件：$duplicates", emptyMap<String, Any>(), duplicates)
    }
}
