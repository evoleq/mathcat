package org.evoleq.mathcat.api.fixtures.authentication

import org.evoleq.mathcat.api.Api

class Login
class LoggedIn
class RefreshToken

val authenticationApi = Api {
    post<Login, LoggedIn>(Login::class, "login")
    post<RefreshToken, LoggedIn>(RefreshToken::class, "refresh")
}
