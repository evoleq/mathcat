package org.evoleq.mathcat.api.fixtures.users

import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.api.EmptyParams

class ReadUser : EmptyParams()
class User
class CreateUser

val usersApi = Api {
    get<ReadUser, User>(ReadUser::class, "{userId}", parameters = setOf("expand"))
    post<CreateUser, User>(CreateUser::class, "create")
}
