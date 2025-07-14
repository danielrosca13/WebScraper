package md.aichat.scraper.config

import ch.qos.logback.core.AppenderBase
import ch.qos.logback.classic.spi.ILoggingEvent
import org.slf4j.MDC

object JobLogBufferRegistry {
    private val buffers = mutableMapOf<String, MutableList<String>>()
    @Synchronized
    fun append(jobId: String, message: String) {
        val buf = buffers.getOrPut(jobId) { mutableListOf() }
        buf.add(message)
        if (buf.size > 100) buf.removeAt(0)
    }
    @Synchronized
    fun get(jobId: String): List<String> = buffers[jobId]?.toList() ?: emptyList()
    @Synchronized
    fun clear(jobId: String) { buffers.remove(jobId) }
}

class JobBufferLogAppender : AppenderBase<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
        val jobId = event.mdcPropertyMap["jobId"]
        if (jobId != null) {
            JobLogBufferRegistry.append(jobId, event.formattedMessage)
        }
    }
}

