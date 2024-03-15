/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */
package io.airbyte.cdk.integrations.util.concurrent

import com.fasterxml.jackson.databind.node.IntNode
import com.google.common.collect.Lists
import io.airbyte.commons.util.AutoCloseableIterator
import io.airbyte.commons.util.AutoCloseableIterators
import io.airbyte.protocol.models.AirbyteStreamNameNamespacePair
import io.airbyte.protocol.models.v0.AirbyteMessage
import io.airbyte.protocol.models.v0.AirbyteRecordMessage
import java.util.List
import java.util.function.Consumer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

/** Test suite for the [ConcurrentStreamConsumer] class. */
internal class ConcurrentStreamConsumerTest {
    @Test
    fun testAcceptMessage() {
        val stream: AutoCloseableIterator<AirbyteMessage> =
            Mockito.mock(AutoCloseableIterator::class.java)
        val streamConsumer: Consumer<AutoCloseableIterator<AirbyteMessage>> =
            Mockito.mock(Consumer::class.java)

        val concurrentStreamConsumer = ConcurrentStreamConsumer(streamConsumer, 1)

        Assertions.assertDoesNotThrow { concurrentStreamConsumer.accept(List.of(stream)) }

        Mockito.verify(streamConsumer, Mockito.times(1)).accept(stream)
    }

    @Test
    fun testAcceptMessageWithException() {
        val stream: AutoCloseableIterator<AirbyteMessage> =
            Mockito.mock(AutoCloseableIterator::class.java)
        val streamConsumer: Consumer<AutoCloseableIterator<AirbyteMessage>> =
            Mockito.mock(Consumer::class.java)
        val e: Exception = NullPointerException("test")

        Mockito.doThrow(e).`when`(streamConsumer).accept(ArgumentMatchers.any())

        val concurrentStreamConsumer = ConcurrentStreamConsumer(streamConsumer, 1)

        Assertions.assertDoesNotThrow { concurrentStreamConsumer.accept(List.of(stream)) }

        Mockito.verify(streamConsumer, Mockito.times(1)).accept(stream)
        Assertions.assertTrue(concurrentStreamConsumer.exception.isPresent)
        Assertions.assertEquals(e, concurrentStreamConsumer.exception.get())
        Assertions.assertEquals(1, concurrentStreamConsumer.exceptions.size)
        Assertions.assertTrue(concurrentStreamConsumer.exceptions.contains(e))
    }

    @Test
    fun testAcceptMessageWithMultipleExceptions() {
        val stream1: AutoCloseableIterator<AirbyteMessage> =
            Mockito.mock(AutoCloseableIterator::class.java)
        val stream2: AutoCloseableIterator<AirbyteMessage> =
            Mockito.mock(AutoCloseableIterator::class.java)
        val stream3: AutoCloseableIterator<AirbyteMessage> =
            Mockito.mock(AutoCloseableIterator::class.java)
        val streamConsumer: Consumer<AutoCloseableIterator<AirbyteMessage>> =
            Mockito.mock(Consumer::class.java)
        val e1: Exception = NullPointerException("test1")
        val e2: Exception = NullPointerException("test2")
        val e3: Exception = NullPointerException("test3")

        Mockito.doThrow(e1).`when`(streamConsumer).accept(stream1)
        Mockito.doThrow(e2).`when`(streamConsumer).accept(stream2)
        Mockito.doThrow(e3).`when`(streamConsumer).accept(stream3)

        val concurrentStreamConsumer = ConcurrentStreamConsumer(streamConsumer, 1)

        Assertions.assertDoesNotThrow {
            concurrentStreamConsumer.accept(List.of(stream1, stream2, stream3))
        }

        Mockito.verify(streamConsumer, Mockito.times(3))
            .accept(ArgumentMatchers.any(AutoCloseableIterator::class.java))
        Assertions.assertTrue(concurrentStreamConsumer.exception.isPresent)
        Assertions.assertEquals(e1, concurrentStreamConsumer.exception.get())
        Assertions.assertEquals(3, concurrentStreamConsumer.exceptions.size)
        Assertions.assertTrue(concurrentStreamConsumer.exceptions.contains(e1))
        Assertions.assertTrue(concurrentStreamConsumer.exceptions.contains(e2))
        Assertions.assertTrue(concurrentStreamConsumer.exceptions.contains(e3))
    }

    @Test
    fun testMoreStreamsThanAvailableThreads() {
        val baseData = listOf(2, 4, 6, 8, 10, 12, 14, 16, 18, 20)
        val streams: MutableList<AutoCloseableIterator<AirbyteMessage>> = ArrayList()
        for (i in 0..19) {
            val airbyteStreamNameNamespacePair =
                AirbyteStreamNameNamespacePair(String.format("%s_%d", NAME, i), NAMESPACE)
            val messages: MutableList<AirbyteMessage> = ArrayList()
            for (d in baseData) {
                val airbyteMessage = Mockito.mock(AirbyteMessage::class.java)
                val recordMessage = Mockito.mock(AirbyteRecordMessage::class.java)
                Mockito.`when`(recordMessage.data).thenReturn(IntNode(d * i))
                Mockito.`when`(airbyteMessage.record).thenReturn(recordMessage)
                messages.add(airbyteMessage)
            }
            streams.add(
                AutoCloseableIterators.fromIterator(
                    messages.iterator(),
                    airbyteStreamNameNamespacePair
                )
            )
        }
        val streamConsumer: Consumer<AutoCloseableIterator<AirbyteMessage>> =
            Mockito.mock(Consumer::class.java)

        val concurrentStreamConsumer = ConcurrentStreamConsumer(streamConsumer, streams.size)
        val partitionSize = concurrentStreamConsumer.parallelism
        val partitions = Lists.partition(streams.stream().toList(), partitionSize)

        for (partition in partitions) {
            Assertions.assertDoesNotThrow { concurrentStreamConsumer.accept(partition) }
        }

        Mockito.verify(streamConsumer, Mockito.times(streams.size))
            .accept(ArgumentMatchers.any(AutoCloseableIterator::class.java))
    }

    companion object {
        private const val NAME = "name"
        private const val NAMESPACE = "namespace"
    }
}
