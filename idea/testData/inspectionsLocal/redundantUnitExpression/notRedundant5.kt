// PROBLEM: none
// WITH_RUNTIME
fun nonUnit(p: Int): Int = p

fun <T> doIt(p: () -> T): T = TODO()

fun g(p: String?) {
    println(1)
    nonUnit(2)

    p?.let {
        println(3)
        nonUnit(4)
        Unit
    }
}

fun h() {
    println(5)
    nonUnit(6)
}

fun x() = doIt {
    println(7)
    nonUnit(8)
    Unit<caret>
}

