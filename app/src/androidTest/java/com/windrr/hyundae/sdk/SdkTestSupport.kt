package com.windrr.hyundae.sdk

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.fail

internal const val TAG = "PleosSdkTest"

internal val targetContext: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

/** SDK 초기화처럼 메인 스레드를 기대하는 호출용 */
internal fun onMain(block: () -> Unit) =
    InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

/**
 * 비동기 콜백 하나를 기다린다.
 * block 안에서 success(값) 또는 failure(메시지)를 한 번 호출하면 된다.
 * 실패·타임아웃이면 원인을 logcat(PleosSdkTest)과 테스트 실패 메시지로 남긴다.
 */
internal fun <T> awaitCallback(
    name: String,
    timeoutSec: Long = 10,
    block: (success: (T) -> Unit, failure: (String) -> Unit) -> Unit,
): T {
    val latch = CountDownLatch(1)
    var result: Result<T>? = null
    block(
        { value -> if (result == null) { result = Result.success(value); latch.countDown() } },
        { message -> if (result == null) { result = Result.failure(IllegalStateException(message)); latch.countDown() } },
    )
    if (!latch.await(timeoutSec, TimeUnit.SECONDS)) {
        Log.e(TAG, "[$name] TIMEOUT (${timeoutSec}s)")
        fail("[$name] ${timeoutSec}초 안에 콜백이 오지 않음")
    }
    val r = result!!
    r.onSuccess { Log.i(TAG, "[$name] OK: $it") }
    r.onFailure {
        Log.e(TAG, "[$name] FAILED: ${it.message}")
        fail("[$name] ${it.message}")
    }
    return r.getOrThrow()
}

/** 서비스 바인딩 직후엔 실패할 수 있어 짧게 재시도한다 */
internal fun <T> awaitWithRetry(
    name: String,
    attempts: Int = 3,
    block: (success: (T) -> Unit, failure: (String) -> Unit) -> Unit,
): T {
    repeat(attempts - 1) { attempt ->
        runCatching { return awaitCallback(name, block = block) }
            .onFailure { Log.w(TAG, "[$name] 재시도 ${attempt + 1}/$attempts") }
        Thread.sleep(1_000)
    }
    return awaitCallback(name, block = block)
}
