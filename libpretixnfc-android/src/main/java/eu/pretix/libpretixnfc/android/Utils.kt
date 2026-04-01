package eu.pretix.libpretixnfc.android

import io.sentry.Sentry
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

val crashLogger: (CoroutineContext, Throwable) -> Unit = { _, throwable: Throwable ->
    throwable.printStackTrace()
    if (BuildConfig.DEBUG) {
        exitProcess(1)
    } else {
        Sentry.captureException(throwable)
    }
}

fun CoroutineScope.launchWithSentry(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    exceptionHandler: ((CoroutineContext, Throwable) -> Unit) = crashLogger,
    task: suspend () -> Unit
): Job {
    return launch(dispatcher + CoroutineExceptionHandler(exceptionHandler)) {
        task()
    }
}