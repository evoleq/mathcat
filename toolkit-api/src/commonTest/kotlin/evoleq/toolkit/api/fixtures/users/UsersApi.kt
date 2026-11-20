package evoleq.toolkit.api.fixtures.users

import evoleq.toolkit.api.Api
import evoleq.toolkit.api.EmptyParams

class ReadUser : EmptyParams()
class User
class CreateUser

val usersApi = Api {
    get<ReadUser, User>(ReadUser::class, "{userId}", parameters = setOf("expand"))
    post<CreateUser, User>(CreateUser::class, "create")
}
