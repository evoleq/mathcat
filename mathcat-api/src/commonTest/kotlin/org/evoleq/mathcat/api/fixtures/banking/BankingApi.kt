package org.evoleq.mathcat.api.fixtures.banking

import org.evoleq.mathcat.api.Api
import org.evoleq.mathcat.api.EmptyParams

class ReadAccount
class Account

val bankingApi = Api {
    get<EmptyParams, Account>(ReadAccount::class, "accounts/{accountId}")
}
