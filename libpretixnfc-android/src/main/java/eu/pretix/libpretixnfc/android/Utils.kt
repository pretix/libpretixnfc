package eu.pretix.libpretixnfc.android

import io.sentry.Sentry
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.system.exitProcess

val crashLogger: (Throwable) -> Unit = { throwable: Throwable ->
    throwable.printStackTrace()
    if (BuildConfig.DEBUG) {
        exitProcess(1)
    } else {
        Sentry.captureException(throwable)
    }
}

class AsyncContext<T>(val weakRef: WeakReference<T>)

fun <T> T.doAsyncSentry(
        exceptionHandler: ((Throwable) -> Unit)? = crashLogger,
        task: AsyncContext<T>.() -> Unit
): Future<Unit> {
    val context = AsyncContext(WeakReference(this))
    return BackgroundExecutor.submit {
        return@submit try {
            context.task()
        } catch (thr: Throwable) {
            val result = exceptionHandler?.invoke(thr)
            if (result != null) {
                result
            } else {
                Unit
            }
        }
    }
}

internal object BackgroundExecutor {
    var executor: ExecutorService =
            Executors.newScheduledThreadPool(2 * Runtime.getRuntime().availableProcessors())

    fun <T> submit(task: () -> T): Future<T> = executor.submit(task)
}