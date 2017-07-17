package net.corda.core.node.services

import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceEntry

/**
 * Holds information about a [Party], which may refer to either a specific node or a service.
 */
sealed class PartyInfo {
    abstract val identity: PartyAndCertificate<Party>
    val name
        get() = identity.party.name

    data class Node(val node: NodeInfo) : PartyInfo() {
        override val identity get() = node.legalIdentityAndCert
    }

    data class Service(val service: ServiceEntry) : PartyInfo() {
        override val identity get() = service.identity
    }
}