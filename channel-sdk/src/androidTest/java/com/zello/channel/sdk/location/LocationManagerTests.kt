package com.zello.channel.sdk.location

import android.location.Address
import android.location.Criteria
import android.location.LocationListener
import android.os.Looper
import android.test.mock.MockContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zello.channel.sdk.Location
import com.zello.channel.sdk.SendLocationError
import com.zello.channel.sdk.SentLocationCallback
import com.zello.channel.sdk.TestTransport
import com.zello.channel.sdk.platform.SystemWrapper
import junit.framework.Assert.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.Executor

internal class MockSystem : SystemWrapper {
	override var currentTimeMillis: Long = 0
}



internal class TestGeocoder(private val address: Address?) : Geocoder {
	override fun getFromLocation(latitude: Double, longitude: Double, maxResults: Int): List<Address> {
		if (address == null) {
			return emptyList()
		} else {
			return listOf(address)
		}
	}
}

internal class TestGeocoderFactory : GeocoderFactory {
	override var isGeocoderPresent: Boolean = true
	var address: Address? = null

	override fun geocoder(): Geocoder {
		return TestGeocoder(address)
	}

}

@RunWith(AndroidJUnit4::class)
class LocationManagerTests {
	private lateinit var geocoderFactory: TestGeocoderFactory
	private lateinit var platformLocationManager: TestLocationManager
	private lateinit var systemWrapper: MockSystem
	private lateinit var locationManager: LocationManager
	private val capitolAddress: Address by lazy {
		val reversed = Address(Locale.US)
		reversed.setAddressLine(0, "1100 Congress Ave")
		reversed.setAddressLine(1, "Austin, TX 78701")
		reversed.adminArea = "TX"
		reversed.countryCode = "US"
		reversed.locality = "Austin"
		reversed.postalCode = "78701"
		reversed.thoroughfare = "1100 Congress Ave"
		reversed
	}

	private lateinit var transport: TestTransport

	private val bogusLocation: android.location.Location by lazy {
		val location = android.location.Location("bogusProvider")
		location.latitude = 23.0
		location.longitude = 15.0
		location.accuracy = 100.0f
		location.time = 1234567
		location
	}
	/// nearbyLocation is within 100 meters of bogusLocation
	private val nearbyLocation: android.location.Location by lazy {
		val location = android.location.Location("bogusProvider")
		location.latitude = 23.0005
		location.longitude = 15.0005
		location.accuracy = 100.0f
		location.time = 1260000
		location
	}

	private val bogusLocation2: android.location.Location by lazy {
		val location = android.location.Location("bogusProvider")
		location.latitude = 45.0
		location.longitude = 11.0
		location.accuracy = 50.0f
		location
	}

	@Before
	fun setup() {
		geocoderFactory = TestGeocoderFactory()
		platformLocationManager = TestLocationManager()
		systemWrapper = MockSystem()
		locationManager = LocationManagerImpl(MockContext(), platformLocationManager, system = systemWrapper)

		transport = TestTransport()
	}

	fun setup(coroutineScope: CoroutineScope) {
		val immediateExecutor = Executor { p0 -> p0.run() }
		locationManager = LocationManagerImpl(MockContext(), platformLocationManager, system = systemWrapper, geocoderFactory = geocoderFactory, backgroundScope = coroutineScope, mainThreadDispatcher = immediateExecutor.asCoroutineDispatcher(), immediateMainThreadDispatcher = immediateExecutor.asCoroutineDispatcher())
	}

	// Verify that we report an error if no available location provider can be found
	@Test
	fun testSendLocation_NoProvider_SendsError() {
		var sendLocationError: SendLocationError? = null
		locationManager.sendLocation(transport, Criteria(), SentLocationCallback { location, error ->
			assertNull(location)
			sendLocationError = error
		})
		assertNotNull(sendLocationError)
		assertEquals("No providers for criteria", sendLocationError?.errorMessage)
	}

	// Verify that we reuse the last known location if one is available
	@Test
	fun testSendLocation_LastKnownLocationRecentNoReverseGeocoding_SendsLocation() {
		runBlocking {
			setup(this)

			platformLocationManager.lastKnownLocation = bogusLocation
			platformLocationManager.bestProvider = "bogusProvider"
			systemWrapper.currentTimeMillis = 1237567 // Three seconds later
			geocoderFactory.isGeocoderPresent = false

			var sentLocation: Location? = null
			locationManager.sendLocation(transport, Criteria(), SentLocationCallback { location, error ->
				assertNull(error)
				sentLocation = location
			})
			yield()

			val sentCommand = transport.sentCommand
			assertNotNull("No command sent.", sentCommand)
			if (sentCommand == null) { return@runBlocking }
			assertEquals("Sent wrong command", "send_location", sentCommand)
			val json = transport.sentJson
			assertNotNull("Missing command payload", json)
			if (json == null) return@runBlocking
			assertEquals("Wrong latitude", 23.0, json.optDouble("latitude"))
			assertEquals("Wrong longitude", 15.0, json.optDouble("longitude"))
			assertEquals("Wrong accuracy", 100.0, json.optDouble("accuracy"))
			assertNull("Address should be absent.", json.optString("formatted_address", null))

			assertFalse("Location should not have been requsted from system.", platformLocationManager.singleLocationRequested)
			assertNotNull("No location passed to callback.", sentLocation)
			assertEquals(23.0, sentLocation?.latitude)
			assertEquals(15.0, sentLocation?.longitude)
			assertEquals(100.0, sentLocation?.accuracy)
			assertNull(sentLocation?.address)
		}
	}

	// Verify that we include the recipient if one is specified
	@Test
	fun testSendLocation_LastKnownLocationRecentNoReverseGeocodingWithRecipient() {
		runBlocking {
			setup(this)

			platformLocationManager.lastKnownLocation = bogusLocation
			platformLocationManager.bestProvider = "bogusProvider"
			systemWrapper.currentTimeMillis = 1234567
			geocoderFactory.isGeocoderPresent = false

			locationManager.sendLocation(transport, Criteria(), "bogusRecipient", null)
			yield()

			val sentCommand = transport.sentCommand
			assertNotNull("No command sent.", sentCommand)
			assertEquals("Sent wrong command", "send_location", sentCommand)
			val json = transport.sentJson
			assertNotNull("Missing command payload.", json)
			if (json == null) return@runBlocking
			assertEquals("Wrong latitude.", 23.0, json.optDouble("latitude"))
			assertEquals("Wrong longitude.", 15.0, json.optDouble("longitude"))
			assertEquals("Wrong accuracy", 100.0, json.optDouble("accuracy"))
			assertNull(json.optString("formatted_address", null))
			assertEquals("Recipient missing or incorrect.", "bogusRecipient", json.optString("for", null))
		}
	}

	// Verify that we request location if the last known location is stale
	@Test
	fun testSendLocation_LastKnownStale_SendsLocation() {
		runBlocking {
			setup(this)

			platformLocationManager.lastKnownLocation = bogusLocation
			platformLocationManager.bestProvider = "bogusProvider"
			systemWrapper.currentTimeMillis = 1240567// Six seconds later

			var sentLocation: Location? = null
			locationManager.sendLocation(transport, Criteria(), SentLocationCallback { location, error ->
				assertNull(error)
				sentLocation = location
			})
			yield()

			assertTrue("Location not requested.", platformLocationManager.singleLocationRequested)
			assertEquals("Wrong location provider requested", "bogusProvider", platformLocationManager.requestedProvider)
			assertNotNull("No location listener in request", platformLocationManager.locationListener)
			val listener = platformLocationManager.locationListener ?: return@runBlocking
			listener.onLocationChanged(bogusLocation2)
			yield()

			val sentCommand = transport.sentCommand
			assertNotNull("No command sent.", sentCommand)
			if (sentCommand == null) return@runBlocking
			assertEquals("Sent wrong command.", "send_location", sentCommand)
			val json = transport.sentJson
			assertNotNull("Missing command payload", json)
			if (json == null) return@runBlocking
			assertEquals("Wrong latitude.", 45.0, json.optDouble("latitude"))
			assertEquals("Wrong longitude.", 11.0, json.optDouble("longitude"))
			assertEquals("Wrong accuracy.", 50.0, json.optDouble("accuracy"))
			assertNull("Address should be absent.", json.optString("formatted_address", null))

			assertNotNull("Callback not called.", sentLocation)
			assertEquals("Wrong latitude in callback.", 45.0, sentLocation?.latitude)
			assertEquals("Wrong longitude in callback.", 11.0, sentLocation?.longitude)
			assertEquals("Wrong accuracy in callback.", 50.0, sentLocation?.accuracy)
			assertNull("Address should be absent in callback.", sentLocation?.address)
		}
	}

	// Verify that we reuse the previous reverse geocoding result if the new location is near the previous one
	@Test
	fun testSendLocation_LocationOverlapsPrevious_ReusesGeocodingResult() {
		runBlocking {
			setup(this)

			// Set up previous geocoding result
			platformLocationManager.bestProvider = "bogusProvider"
			systemWrapper.currentTimeMillis = 1235567 // One second later
			geocoderFactory.address = capitolAddress
			locationManager.sendLocation(transport, Criteria(), null)
			yield()
			val listener = platformLocationManager.locationListener
			assertNotNull(listener)
			if (listener == null) return@runBlocking
			listener.onLocationChanged(bogusLocation)
			yield()

			// Now test to make sure we reuse that geocoding result
			systemWrapper.currentTimeMillis = 1261000 // Some time later
			geocoderFactory.address = null
			transport.sentCommand = null

			var sentLocation: Location? = null
			locationManager.sendLocation(transport, Criteria(), SentLocationCallback { location, error ->
				sentLocation = location
				assertNull(error)
			})
			yield()
			val newListener = platformLocationManager.locationListener
			newListener?.onLocationChanged(nearbyLocation)
			yield()

			val sentCommand = transport.sentCommand
			assertNotNull("No command sent.", sentCommand)
			if (sentCommand == null) return@runBlocking
			assertEquals("Sent wrong command.", "send_location", sentCommand)
			val sentJson = transport.sentJson
			assertNotNull("Missing command payload.", sentJson)
			if (sentJson == null) return@runBlocking
			assertEquals("Wrong latitude", 23.0005, sentJson.optDouble("latitude"))
			assertEquals("Wrong longitude", 15.0005, sentJson.optDouble("longitude"))
			assertEquals("Wrong accuracy.", 100.0, sentJson.optDouble("accuracy"))
			assertEquals("Wrong reverse geocoded address.", "1100 Congress Ave\nAustin, TX 78701", sentJson.optString("formatted_address", null))

			// Verify callback values
			assertNotNull("Callback not called.", sentLocation)
			assertEquals("Wrong latitude in callback.", 23.0005, sentLocation?.latitude)
			assertEquals("Wrong longitude in callback.", 15.0005, sentLocation?.longitude)
			assertEquals("Wrong accuracy in callback.", 100.0, sentLocation?.accuracy)
			assertEquals("Wrong address in callback.", "1100 Congress Ave\nAustin, TX 78701", sentLocation?.address)

		}
	}

	// Verify that we call back with an error if the provider is disabled during location request and no other provider is available
	@Test
	fun sendLocation_ProviderDisabledNoOtherProviderAvailable_ReportsError() {
		runBlocking {
			setup(this)

			platformLocationManager.bestProvider = "bogusProvider"
			var sendLocationError: SendLocationError? = null
			locationManager.sendLocation(transport, Criteria(), SentLocationCallback { location, error ->
				sendLocationError = error
				assertNull(location)
			})
			yield()

			platformLocationManager.bestProvider = null
			platformLocationManager.locationListener?.onProviderDisabled("bogusProvider")
			yield()

			assertNotNull("No error reported.", sendLocationError)
			assertEquals("Wrong error message.", "No providers for criteria", sendLocationError?.errorMessage)
		}
	}

	// Verify that we call back with an error if the provider doesn't provide a location
	@Test
	fun sendLocation_NoLocation_ReportsError() {
		runBlocking {
			setup(this)

			platformLocationManager.bestProvider = "bogusProvider"

			var sendLocationError: SendLocationError? = null
			locationManager.sendLocation(transport, Criteria(), SentLocationCallback { location, error ->
				assertNull(location)
				sendLocationError = error
			})
			yield()

			val listener = platformLocationManager.locationListener
			assertNotNull(listener)
			listener?.onLocationChanged(null)
			yield()

			assertNotNull("Did not report error.", sendLocationError)
			assertEquals("Wrong error message.", "Location could not be found", sendLocationError?.errorMessage)
		}
	}

	// Verify that we call back with a location if the provider gives us one
	@Test
	fun sendLocation_NoLastKnownProviderGivesLocationNoReverseGeocoding_SendsLocation() {
		runBlocking {
			setup(this)

			platformLocationManager.bestProvider = "bogusProvider"
			platformLocationManager.lastKnownLocation = null
			geocoderFactory.isGeocoderPresent = false

			var actualLocation: Location? = null
			locationManager.sendLocation(transport, Criteria(), SentLocationCallback { location, error ->
				assertNull(error)
				actualLocation = location
			})
			assertTrue("Location not requested.", platformLocationManager.singleLocationRequested)
			assertEquals("Got location request for wrong provider.", "bogusProvider", platformLocationManager.requestedProvider)
			assertNotNull("No location listener in request.", platformLocationManager.locationListener)
			val listener = platformLocationManager.locationListener ?: return@runBlocking
			listener.onLocationChanged(bogusLocation)
			yield()

			val sentCommand = transport.sentCommand
			assertNotNull("No command sent.", sentCommand)
			if (sentCommand == null) return@runBlocking
			assertEquals("Sent wrong command.", "send_location", sentCommand)
			val json = transport.sentJson
			assertNotNull("Missing command payload", json)
			if (json == null) return@runBlocking
			assertEquals("Wrong latitude", 23.0, json.optDouble("latitude"))
			assertEquals("Wrong longitude", 15.0, json.optDouble("longitude"))
			assertEquals("Wrong accuracy.", 100.0, json.optDouble("accuracy"))
			assertNull("Address should be absent", json.optString("formatted_address", null))

			assertNotNull(actualLocation)
			assertEquals(23.0, actualLocation?.latitude)
			assertEquals(15.0, actualLocation?.longitude)
			assertEquals(100.0, actualLocation?.accuracy)
			assertNull(actualLocation?.address)
		}
	}

	// Verify that we call back with a location and address if the providers give them to us
	@Test
	fun sendLocation_NoLastKnownProviderGivesLocationWithReverseGeocoding_CallsBack() {
		runBlocking {
			setup(this)

			platformLocationManager.bestProvider = "bogusProvider"
			platformLocationManager.lastKnownLocation = null
			geocoderFactory.isGeocoderPresent = true
			geocoderFactory.address = capitolAddress

			var actualLocation: Location? = null
			locationManager.sendLocation(transport, Criteria(), SentLocationCallback { location, error ->
				assertNull(error)
				actualLocation = location
			})
			assertTrue("Location not requested.", platformLocationManager.singleLocationRequested)
			assertEquals("Got location request for wrong provider.", "bogusProvider", platformLocationManager.requestedProvider)
			assertNotNull("No location listener in request.", platformLocationManager.locationListener)
			val listener = platformLocationManager.locationListener ?: return@runBlocking
			listener.onLocationChanged(bogusLocation)
			yield()

			assertNotNull(actualLocation)
			assertEquals(23.0, actualLocation?.latitude)
			assertEquals(15.0, actualLocation?.longitude)
			assertEquals(100.0, actualLocation?.accuracy)
			assertEquals("Incorrect reverse geocoded address.", "1100 Congress Ave\nAustin, TX 78701", actualLocation?.address)
		}
	}
}
