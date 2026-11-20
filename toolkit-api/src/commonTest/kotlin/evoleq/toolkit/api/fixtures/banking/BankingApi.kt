package evoleq.toolkit.api.fixtures.banking

import evoleq.toolkit.api.Api
import evoleq.toolkit.api.EmptyParams

class ReadAccount
class Account

val bankingApi = Api {
    get<EmptyParams, Account>(ReadAccount::class, "accounts/{accountId}")
}
