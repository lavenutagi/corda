package net.corda.testing

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.ResolvableTransactionData
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.utilities.UntrustworthyData

// Flow to start data vending without sending transaction.
 class DataVendingFlow(otherSide: Party) : SendTransactionFlow(otherSide, dummyData) {
    companion object {
        val dummyData = object : ResolvableTransactionData {
            override val dependencies: Set<SecureHash> = setOf(SecureHash.randomSHA256())
        }
    }

    @Suspendable
    override fun sendPayloadAndReceiveDataRequest(otherSide: Party, payload: Any): UntrustworthyData<FetchDataFlow.Request> {
        return if (payload == dummyData) {
            receive<FetchDataFlow.Request>(otherSide)
        } else {
            super.sendPayloadAndReceiveDataRequest(otherSide, payload)
        }
    }
}