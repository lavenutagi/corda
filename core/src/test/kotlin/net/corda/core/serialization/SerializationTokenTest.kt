package net.corda.core.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.io.Output
import com.nhaarman.mockito_kotlin.mock
import net.corda.core.node.ServiceHub
import net.corda.core.utilities.OpaqueBytes
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.nodeapi.serialization.KryoHeaderV0_1
import net.corda.nodeapi.serialization.SerializationContextImpl
import net.corda.nodeapi.serialization.SerializationFactoryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class SerializationTokenTest {

    lateinit var factory: SerializationFactory
    lateinit var context: SerializationContext

    @Before
    fun setup() {
        factory = SerializationFactoryImpl().apply { registerScheme(KryoServerSerializationScheme()) }
        context = SerializationContextImpl(KryoHeaderV0_1,
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                SerializationContext.UseCase.P2P)
    }

    // Large tokenizable object so we can tell from the smaller number of serialized bytes it was actually tokenized
    private class LargeTokenizable : SingletonSerializeAsToken() {
        val bytes = OpaqueBytes(ByteArray(1024))

        val numBytes: Int
            get() = bytes.size

        override fun hashCode() = bytes.size

        override fun equals(other: Any?) = other is LargeTokenizable && other.bytes.size == this.bytes.size
    }

    private fun serializeAsTokenContext(toBeTokenized: Any) = SerializeAsTokenContext(toBeTokenized, factory, context, mock<ServiceHub>())

    @Test
    fun `write token and read tokenizable`() {
        val tokenizableBefore = LargeTokenizable()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)

        val serializedBytes = tokenizableBefore.serialize(factory, testContext)
        assertThat(serializedBytes.size).isLessThan(tokenizableBefore.numBytes)
        val tokenizableAfter = serializedBytes.deserialize(factory, testContext)
        assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
    }

    private class UnitSerializeAsToken : SingletonSerializeAsToken()

    @Test
    fun `write and read singleton`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)
        val serializedBytes = tokenizableBefore.serialize(factory, testContext)
        val tokenizableAfter = serializedBytes.deserialize(factory, testContext)
            assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `new token encountered after context init`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(emptyList<Any>())
        val testContext = this.context.withTokenContext(context)
        tokenizableBefore.serialize(factory, testContext)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `deserialize unregistered token`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(emptyList<Any>())
        val testContext = this.context.withTokenContext(context)
        val serializedBytes = tokenizableBefore.toToken(serializeAsTokenContext(emptyList<Any>())).serialize(factory, testContext)
        serializedBytes.deserialize(factory, testContext)
    }

    @Test(expected = KryoException::class)
    fun `no context set`() {
        val tokenizableBefore = UnitSerializeAsToken()
        tokenizableBefore.serialize(factory, context)
    }

    @Test(expected = KryoException::class)
    fun `deserialize non-token`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)

        val kryo: Kryo = DefaultKryoCustomizer.customize(CordaKryo(makeNoWhitelistClassResolver()))
        val stream = ByteArrayOutputStream()
            Output(stream).use {
                it.write(KryoHeaderV0_1.bytes)
                kryo.writeClass(it, SingletonSerializeAsToken::class.java)
                kryo.writeObject(it, emptyList<Any>())
            }
        val serializedBytes = SerializedBytes<Any>(stream.toByteArray())
        serializedBytes.deserialize(factory, testContext)
    }

    private class WrongTypeSerializeAsToken : SerializeAsToken {
        override fun toToken(context: SerializeAsTokenContext): SerializationToken {
            return object : SerializationToken {
                override fun fromToken(context: SerializeAsTokenContext): Any = UnitSerializeAsToken()
            }
        }
    }

    @Test(expected = KryoException::class)
    fun `token returns unexpected type`() {
        val tokenizableBefore = WrongTypeSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)
        val serializedBytes = tokenizableBefore.serialize(factory, testContext)
        serializedBytes.deserialize(factory, testContext)
    }
}
