package com.flipperdevices.bridge.service.impl.provider

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flipperdevices.bridge.service.api.FlipperServiceApi
import com.flipperdevices.bridge.service.api.provider.FlipperBleServiceConsumer
import com.flipperdevices.bridge.service.impl.FlipperService
import com.flipperdevices.bridge.service.impl.FlipperServiceBinder
import com.flipperdevices.core.test.TimberRule
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config

@Config(sdk = [30])
@RunWith(AndroidJUnit4::class)
class FlipperServiceProviderTest {
    private val applicationContext = mock<Context>() {
        on { packageName } doReturn "com.flipperdevices.bridge.service.impl.provider"
    }
    private lateinit var subject: FlipperServiceProviderImpl

    @Before
    fun setUp() {
        subject = FlipperServiceProviderImpl(applicationContext)
    }

    @Test
    fun `Start service when exist consumer`() {
        val consumer = mock<FlipperBleServiceConsumer>()
        val lifecycleOwner = mock<LifecycleOwner> {
            on { lifecycle } doReturn (mock())
        }
        subject.provideServiceApi(consumer, lifecycleOwner)

        verify(applicationContext).startService(
            argThat {
                component?.className == "com.flipperdevices.bridge.service.impl.FlipperService"
            }
        )
        verify(applicationContext).bindService(any(), any(), any())
    }

    @Test
    fun `Stop service when consumer destroy`() {
        val consumer = mock<FlipperBleServiceConsumer>()
        val lifecycleOwner = TestLifecycleOwner()
        subject.provideServiceApi(consumer, lifecycleOwner)

        verify(applicationContext).bindService(any(), any(), any())

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        verify(applicationContext).unbindService(any())
        verify(applicationContext).startService(
            argThat {
                action == FlipperService.ACTION_STOP
            }
        )
    }

    @Test
    fun `Notify consumers when binder available`() {
        val consumer = mock<FlipperBleServiceConsumer>()
        val lifecycleOwner = TestLifecycleOwner()
        val serverApi = mock<FlipperServiceApi>()
        val binder = FlipperServiceBinder(serverApi, mock())

        subject.provideServiceApi(consumer, lifecycleOwner)
        subject.onServiceConnected(mock(), binder)

        verify(consumer).onServiceApiReady(serverApi)
    }

    @Test
    fun `If we already start notify consumer`() {
        val secondConsumer = mock<FlipperBleServiceConsumer>()
        val lifecycleOwner = TestLifecycleOwner()
        val serverApi = mock<FlipperServiceApi>()
        val binder = FlipperServiceBinder(serverApi, mock())

        subject.provideServiceApi(mock(), lifecycleOwner)
        subject.onServiceConnected(mock(), binder)
        subject.provideServiceApi(secondConsumer, lifecycleOwner)

        verify(secondConsumer).onServiceApiReady(serverApi)
    }

    @Test
    fun `Not request start service twice`() {
        val lifecycleOwner = TestLifecycleOwner()

        subject.provideServiceApi(mock(), lifecycleOwner)
        subject.provideServiceApi(mock(), lifecycleOwner)

        verify(applicationContext, times(1)).startService(any())
    }

    @Test
    fun `Request start service after stop`() {
        val firstConsumer = mock<FlipperBleServiceConsumer>()
        val secondConsumer = mock<FlipperBleServiceConsumer>()
        val lifecycleOwner = TestLifecycleOwner()
        val firstServerApi = mock<FlipperServiceApi>()
        val secondServerApi = mock<FlipperServiceApi>()
        assertNotEquals(firstServerApi, secondServerApi)

        // Starting
        subject.provideServiceApi(firstConsumer, lifecycleOwner)
        verify(applicationContext).bindService(any(), any(), any())
        subject.onServiceConnected(mock(), FlipperServiceBinder(firstServerApi, mock()))
        verify(firstConsumer).onServiceApiReady(firstServerApi)

        // Stopping..
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        verify(applicationContext).unbindService(any())
        clearInvocations(applicationContext)
        // Now service stopped

        // Starting...
        subject.provideServiceApi(secondConsumer, lifecycleOwner)
        verify(applicationContext).bindService(any(), any(), any())
        subject.onServiceConnected(mock(), FlipperServiceBinder(secondServerApi, mock()))

        verify(secondConsumer).onServiceApiReady(secondServerApi)
    }

    @Test
    fun `Notify about new service api`() {
        val consumer = mock<FlipperBleServiceConsumer>()
        val lifecycleOwner = TestLifecycleOwner()
        val serverApi = mock<FlipperServiceApi>()

        // Starting
        subject.provideServiceApi(consumer, lifecycleOwner)
        verify(applicationContext).bindService(any(), any(), any())
        subject.onServiceConnected(mock(), FlipperServiceBinder(serverApi, mock()))
        verify(consumer).onServiceApiReady(serverApi)

        // Notify about service disconnected
        subject.onServiceDisconnected(mock())

        verify(applicationContext, times(2)).bindService(any(), any(), any())
    }

    companion object {
        @get:ClassRule
        @JvmStatic
        var timberRule = TimberRule()
    }
}