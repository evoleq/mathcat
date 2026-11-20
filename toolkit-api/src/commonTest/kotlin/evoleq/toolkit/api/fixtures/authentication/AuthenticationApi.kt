package evoleq.toolkit.api.fixtures.authentication

import evoleq.toolkit.api.Api

class Login
class LoggedIn
class RefreshToken

val authenticationApi = Api {
    post<Login, LoggedIn>(Login::class, "login")
    post<RefreshToken, LoggedIn>(RefreshToken::class, "refresh")
}
