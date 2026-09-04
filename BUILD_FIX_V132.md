# v1.3.2 build fix

Removed the explicit `androidx.compose.foundation.layout.weight` import.

With the Compose libraries used by this project, `Modifier.weight(...)` is supplied by `RowScope`/`ColumnScope`. The explicit import can resolve to the internal `RowColumnParentData.weight` property under the AGP 9 / Kotlin 2.3 toolchain and fail compilation.

No runtime behavior or live-home logic was changed.
