package oj.scanner

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(JUnitPlatform::class)
class HelloTest: Spek({
    describe("Basic test to check framework") {
        it ("check") {
            assertEquals(1, 1)
        }
    }
})