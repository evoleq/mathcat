package evoleq.toolkit.ksp.fixture

import kotlinx.serialization.Serializable

/** Additional source file: generator must collect the *whole compilation*. */
@Serializable
data class BillingEntry(val amount: Int)
