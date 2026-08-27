import org.jetbrains.kotlin.formver.plugin.AlwaysVerify
import org.jetbrains.kotlin.formver.plugin.postconditions
import org.jetbrains.kotlin.formver.plugin.verify

@AlwaysVerify
internal fun smokeIncrement(x: Int): Int {
    val result = x + 1
    postconditions<Int> { r -> verify(r > x) }
    return result
}
