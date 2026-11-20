package evoleq.toolkit.ksp.fixture

import kotlinx.serialization.Serializable
import evoleq.toolkit.serialization.SkipAutoSerializer

@Serializable
data class Login(val username: String, val password: String)

@Serializable
data class LoggedIn(val accessToken: String)

/** @Serializable but deliberately not included in the generated registry. */
@SkipAutoSerializer
@Serializable
data class SecretInternalDto(val value: String)

/** KClass cannot express generic argument types; these are registered explicitly. */
@Serializable
data class GenericBox<T>(val value: T)
