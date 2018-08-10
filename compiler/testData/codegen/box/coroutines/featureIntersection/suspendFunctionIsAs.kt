// IGNORE_BACKEND: JS_IR, JS
// IGNORE_BACKEND: JVM_IR
// WITH_RUNTIME
// WITH_COROUTINES
// LANGUAGE_VERSION: 1.3

import helpers.*
import kotlin.coroutines.*

fun fn1(x: Any) {}
suspend fun suspendFn0() {}

val lambda1 = { x: Any -> } as (Any) -> Unit
val suspendLambda0: suspend () -> Unit = {}

fun Any.extFun() {}
suspend fun Any.suspendExtFun() {}

class A {
    fun foo() {}
    suspend fun suspendFoo() {}
}

fun box(): String {
//    val f1 = ::fn1 as Any
//    val suspendF0 = ::suspendFn0
//
//    val ef = Any::extFun as Any
//
//    val afoo = A::foo
//
//    fun local0() {}
//    fun local1(x: Any) {}
//
//    val localFun0 = ::local0 as Any
//    val localFun1 = ::local1 as Any

//    assert(f1 !is SuspendFunction0<*>) { "Failed: f1 !is SuspendFunction0<*>" }
//    assert(suspendF0 is SuspendFunction0<*>) { "Failed: f1 is SuspendFunction0<*>" }
//    assert(suspendF0 !is Function1<*, *>) { "Failed: suspendF0 !is Function1<*, *>" }

    assert(lambda1 !is SuspendFunction0<*>) { "Failed: lambda1 !is SuspendFunction0<*>" }
    assert(suspendLambda0 is Function1<*, *>) { "Failed: suspendLambda0 is Function1<*, *>" }
    assert(suspendLambda0 is SuspendFunction0<*>) { "Failed: suspendLambda0 is SuspendFunction0<*>" }

//    assert(localFun0 is Function0<*>) { "Failed: localFun0 is Function0<*>" }
//    assert(localFun1 is Function1<*, *>) { "Failed: localFun1 is Function1<*, *>" }
//    assert(localFun0 !is Function1<*, *>) { "Failed: localFun0 !is Function1<*, *>" }
//    assert(localFun1 !is Function0<*>) { "Failed: localFun1 !is Function0<*>" }

//    assert(ef is Function1<*, *>) { "Failed: ef is Function1<*, *>" }
//
//    assert(afoo is Function1<*, *>) { "afoo is Function1<*, *>" }

    return "OK"
}