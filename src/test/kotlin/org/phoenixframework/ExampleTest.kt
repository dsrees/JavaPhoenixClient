package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ExampleTest {
  @Test
  internal fun `should fail`() {
//    assertThat(true).isFalse()
    assertThat(true).isTrue()
  }

  @Nested
  @DisplayName("nested test")
  inner class NestedTest {

    @Test
    internal fun `does fail`() {
      assertThat(false).isFalse()
    }
  }
}

