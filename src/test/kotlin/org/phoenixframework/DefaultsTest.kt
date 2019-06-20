package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class DefaultsTest {

  @Test
  internal fun `default timeout is 10_000`() {
    assertThat(Defaults.TIMEOUT).isEqualTo(10_000)
  }

  @Test
  internal fun `default heartbeat is 30_000`() {
    assertThat(Defaults.HEARTBEAT).isEqualTo(30_000)
  }

  @Test
  internal fun `default reconnectAfterMs returns all values`() {
    val reconnect = Defaults.reconnectSteppedBackOff

    assertThat(reconnect(1)).isEqualTo(10)
    assertThat(reconnect(2)).isEqualTo(50)
    assertThat(reconnect(3)).isEqualTo(100)
    assertThat(reconnect(4)).isEqualTo(150)
    assertThat(reconnect(5)).isEqualTo(200)
    assertThat(reconnect(6)).isEqualTo(250)
    assertThat(reconnect(7)).isEqualTo(500)
    assertThat(reconnect(8)).isEqualTo(1_000)
    assertThat(reconnect(9)).isEqualTo(2_000)
    assertThat(reconnect(10)).isEqualTo(5_000)
    assertThat(reconnect(11)).isEqualTo(5_000)
  }

  @Test
  internal fun `default rejoinAfterMs returns all values`() {
    val reconnect = Defaults.rejoinSteppedBackOff

    assertThat(reconnect(1)).isEqualTo(1_000)
    assertThat(reconnect(2)).isEqualTo(2_000)
    assertThat(reconnect(3)).isEqualTo(5_000)
    assertThat(reconnect(4)).isEqualTo(10_000)
    assertThat(reconnect(5)).isEqualTo(10_000)
  }
}