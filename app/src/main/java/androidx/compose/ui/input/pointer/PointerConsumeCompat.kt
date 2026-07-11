@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package androidx.compose.ui.input.pointer

/**
 * Compatibility bridge for Compose versions where PointerInputChange.consume()
 * is a member function but the former importable extension is no longer shipped.
 */
fun PointerInputChange.consume() {
    this.consume()
}
