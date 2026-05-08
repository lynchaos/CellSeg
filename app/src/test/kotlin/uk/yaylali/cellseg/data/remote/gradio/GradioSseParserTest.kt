package uk.yaylali.cellseg.data.remote.gradio

import com.squareup.moshi.Moshi
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GradioSseParserTest {

    private lateinit var parser: GradioSseParser

    @BeforeEach
    fun setUp() {
        parser = GradioSseParser(Moshi.Builder().build())
    }

    @Test
    fun `parse estimation event returns Estimation`() {
        val json = """{"msg":"estimation","rank":3,"queue_size":10,"avg_event_process_time":5.0}"""
        val event = parser.parse(json)
        assertTrue(event is SseEvent.Estimation)
        assertEquals(3, (event as SseEvent.Estimation).rank)
        assertEquals(10, event.queueSize)
    }

    @Test
    fun `parse process_starts returns ProcessStarts`() {
        val json = """{"msg":"process_starts","eta":2.5}"""
        val event = parser.parse(json)
        assertTrue(event is SseEvent.ProcessStarts)
        assertEquals(2.5, (event as SseEvent.ProcessStarts).eta)
    }

    @Test
    fun `parse queue_full returns QueueFull`() {
        val json = """{"msg":"queue_full"}"""
        val event = parser.parse(json)
        assertEquals(SseEvent.QueueFull, event)
    }

    @Test
    fun `parse unexpected_error returns UnexpectedError with message`() {
        val json = """{"msg":"unexpected_error","message":"Something went wrong"}"""
        val event = parser.parse(json)
        assertTrue(event is SseEvent.UnexpectedError)
        assertEquals("Something went wrong", (event as SseEvent.UnexpectedError).message)
    }

    @Test
    fun `parse heartbeat returns Heartbeat`() {
        val json = """{"msg":"heartbeat"}"""
        val event = parser.parse(json)
        assertTrue(event is SseEvent.Heartbeat)
    }

    @Test
    fun `parse malformed json returns null`() {
        val event = parser.parse("{ not json {{")
        assertNull(event)
    }

    @Test
    fun `parse unknown msg type returns null`() {
        val json = """{"msg":"unknown_type"}"""
        val event = parser.parse(json)
        assertNull(event)
    }

    @Test
    fun `extractArtifacts extracts urls from process_completed output`() {
        val data: List<Any?> = listOf(
            mapOf("url" to "https://space.hf.space/file=outlines.png"),
            mapOf("url" to "https://space.hf.space/file=flows.png"),
            mapOf("url" to "https://space.hf.space/file=masks.tiff"),
            mapOf("url" to "https://space.hf.space/file=outlines_dl.png"),
        )
        val output = CompletedOutput(data = data, error = null)
        val artifacts = parser.extractArtifacts(output, "https://space.hf.space")
        assertEquals("https://space.hf.space/file=outlines.png", artifacts.outlinesImageUrl)
        assertEquals("https://space.hf.space/file=flows.png", artifacts.flowsImageUrl)
        assertEquals("https://space.hf.space/file=masks.tiff", artifacts.masksTiffUrl)
    }

    @Test
    fun `extractArtifacts with empty data returns all nulls`() {
        val output = CompletedOutput(data = emptyList(), error = null)
        val artifacts = parser.extractArtifacts(output, "https://space.hf.space")
        assertNull(artifacts.outlinesImageUrl)
        assertNull(artifacts.flowsImageUrl)
        assertNull(artifacts.masksTiffUrl)
        assertNull(artifacts.outlinesPngUrl)
    }
}
