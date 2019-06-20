package org.phoenixframework.queue

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

internal class ManualDispatchQueueTest {


  private lateinit var queue: ManualDispatchQueue

  @BeforeEach
  internal fun setUp() {
    queue = ManualDispatchQueue()
  }

  @AfterEach
  internal fun tearDown() {
    queue.reset()
  }

  @Test
  internal fun `reset the queue`() {
    var task100Called = false
    var task200Called = false
    var task300Called = false

    queue.queue(100, TimeUnit.MILLISECONDS) {
      task100Called = true
    }
    queue.queue(200, TimeUnit.MILLISECONDS) {
      task200Called = true
    }
    queue.queue(300, TimeUnit.MILLISECONDS) {
      task300Called = true
    }

    queue.tick(250)

    assertThat(queue.tickTime).isEqualTo(250)
    assertThat(queue.workItems).hasSize(1)

    queue.reset()
    assertThat(queue.tickTime).isEqualTo(0)
    assertThat(queue.workItems).isEmpty()
  }

  @Test
  internal fun `triggers work that is passed due`() {
    var task100Called = false
    var task200Called = false
    var task300Called = false

    queue.queue(100, TimeUnit.MILLISECONDS) {
      task100Called = true
    }
    queue.queue(200, TimeUnit.MILLISECONDS) {
      task200Called = true
    }
    queue.queue(300, TimeUnit.MILLISECONDS) {
      task300Called = true
    }

    queue.tick(100)
    assertThat(task100Called).isTrue()

    queue.tick(100)
    assertThat(task200Called).isTrue()

    queue.tick(50)
    assertThat(task300Called).isFalse()
  }

  @Test
  internal fun `triggers all work that is passed due`() {
    var task100Called = false
    var task200Called = false
    var task300Called = false

    queue.queue(100, TimeUnit.MILLISECONDS) {
      task100Called = true
    }
    queue.queue(200, TimeUnit.MILLISECONDS) {
      task200Called = true
    }
    queue.queue(300, TimeUnit.MILLISECONDS) {
      task300Called = true
    }

    queue.tick(250)
    assertThat(task100Called).isTrue()
    assertThat(task200Called).isTrue()
    assertThat(task300Called).isFalse()
  }

  @Test
  internal fun `triggers work that is scheduled for a time that is after tick`() {
    var task100Called = false
    var task200Called = false
    var task300Called = false

    queue.queue(100, TimeUnit.MILLISECONDS) {
      task100Called = true

      queue.queue(100, TimeUnit.MILLISECONDS) {
        task200Called = true
      }

    }

    queue.queue(300, TimeUnit.MILLISECONDS) {
      task300Called = true
    }

    queue.tick(250)
    assertThat(task100Called).isTrue()
    assertThat(task200Called).isTrue()
    assertThat(task300Called).isFalse()

    assertThat(queue.tickTime).isEqualTo(250)
  }

  @Test
  internal fun `triggers work in order of deadline`() {
    var task200Called = false
    var task100Called = false


    val task200 = queue.queue(200, TimeUnit.MILLISECONDS) {
      task200Called = true
    }

    queue.queue(100, TimeUnit.MILLISECONDS) {
      task100Called = true
      task200.cancel()
    }


    queue.tick(300)
    assertThat(task100Called).isTrue()
    assertThat(task200Called).isFalse()
  }

  @Test
  internal fun `triggers inserted work in order of deadline`() {
    var task500Called = false
    var task200Called = false
    var task100Called = false

    val task500 = queue.queue(500, TimeUnit.MILLISECONDS) {
      task500Called = true
    }

    queue.queue(200, TimeUnit.MILLISECONDS) {
      task200Called = true

      queue.queue(100, TimeUnit.MILLISECONDS) {
        task100Called = true
        task500.cancel()
      }
    }

    queue.tick(600)
    assertThat(task100Called).isTrue()
    assertThat(task200Called).isTrue()
    assertThat(task500Called).isFalse()
  }



  @Test
  internal fun `does not triggers nested work that is scheduled outside of the tick`() {
    var task100Called = false
    var task200Called = false
    var task300Called = false

    queue.queue(100, TimeUnit.MILLISECONDS) {
      task100Called = true

      queue.queue(100, TimeUnit.MILLISECONDS) {
        task200Called = true

        queue.queue(100, TimeUnit.MILLISECONDS) {
          task300Called = true
        }
      }
    }

    queue.tick(250)
    assertThat(task100Called).isTrue()
    assertThat(task200Called).isTrue()
    assertThat(task300Called).isFalse()
  }

  @Test
  internal fun `queueAtFixedRate repeats work`() {
    var repeatTaskCallCount = 0

    queue.queueAtFixedRate(100, 100, TimeUnit.MILLISECONDS) {
      repeatTaskCallCount += 1
    }

    queue.tick(500)
    assertThat(repeatTaskCallCount).isEqualTo(5)
  }
}