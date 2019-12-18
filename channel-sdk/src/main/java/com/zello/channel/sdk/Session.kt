package com.zello.channel.sdk

import android.content.Context
import android.graphics.Bitmap
import android.location.Criteria
import com.zello.channel.sdk.commands.Command
import com.zello.channel.sdk.commands.CommandLogon
import com.zello.channel.sdk.commands.CommandSendText
import com.zello.channel.sdk.image.Dimensions
import com.zello.channel.sdk.image.ImageMessageManagerListener
import com.zello.channel.sdk.platform.Utils
import com.zello.channel.sdk.transport.Transport
import com.zello.channel.sdk.transport.TransportEvents
import com.zello.channel.sdk.transport.TransportReadAck
import org.json.JSONObject
import java.util.EnumSet
import kotlin.math.min

/**
 * Main SDK object that manages the outgoing and incoming messages
 * and notifies the client about it's state changes through a callback object.
 *
 * @property address the URL of the server to connect to
 * @property authToken the authentication token to send to the server during logon
 * @property username the username to send to the server during logon. If no username is provided,
 * the session will connect in listen-only mode.
 * @property password the account password for [username] to send to the server during logon. If no password is
 * provided, the session will connect in listen-only mode.
 * @property channel the name of the channel to connect to
 */
class Session internal constructor(
        private val context: SessionContext,
        val address: String,
        val authToken: String,
        val username: String?,
        val password: String?,
        val channel: String) {

    /**
     * Requests to the Zello channels server will fail with a timed out error if requestTimeoutSec
	 * seconds pass without a response from the server. Defaults to 30 seconds.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var requestTimeoutSec: Long = 30

	private val initialized: Boolean
		get() = loadedNativeLibraries
    private val voiceManager = VoiceManager(context)
    private var transport: Transport? = null
    private var commandLogon: CommandLogon? = null

	private val imageMessageManager = context.createImageMessageManager(object : ImageMessageManagerListener {
			override fun onImageMessage(message: ImageInfo) {
				sessionListener?.onImageMessage(this@Session, message)
			}

			override fun onInvalidImageMessage(error: InvalidImageMessageError) {
				sessionListener?.onError(this@Session, error)
			}
		})

    internal val logger = context.getLogger()

    /**
     * A collection of all active incoming and outgoing streams
     */
    @Suppress("UNUSED")
    val activeStreams: Collection<VoiceStream> = voiceManager.activeStreams

    // Number of seconds to wait before the next automatic reconnect attempt
    private var nextReconnectDelay = 1.0
    private var refreshToken: String? = null

    /**
     * A callback that is used to notify the client app about state changes.
     * Must be accessed from the main UI thread.
     */
    var sessionListener: SessionListener? = null

    /**
     * The current state of the session object
     */
    var state: SessionState = SessionState.DISCONNECTED
        private set

	/**
	 * Features supported by the currently connected channel
	 */
	var channelFeatures: EnumSet<ChannelFeature> = EnumSet.noneOf(ChannelFeature::class.java)
		private set

	/**
	 * The number of users that are connected to the channel
	 */
	var channelUsersOnline: Int = 0
		private set

    init {
		if (!loadedNativeLibraries) {
			loadedNativeLibraries = context.loadNativeLibraries(context.getLogger())
		}
        if (!loadedNativeLibraries) state = SessionState.ERROR
    }

    /**
     * Builder objects are used to create instances of Session class.
     *
     * @param address Server address
     * @param authToken Your authentication token
     * @param channel Channel name
     */
    class Builder(context: Context, private val address: String, private val authToken: String, private val channel: String) {

        private val context: SessionContext
        init {
            this.context = SessionContextAndroid(context)
        }

        private var username: String? = null
        private var password: String? = null
        private var logger: SessionLogger? = SessionLogger.ADB

        /**
         * Set the username and password used to connect to the server and channel
         */
		@Suppress("unused") // It's used by SDK consumers
        fun setUsername(username: String?, password: String?): Builder {
            this.username = username
            this.password = password
            return this
        }

		/**
		 * Configures the logging behavior of the Zello channels session. Log messages will be written
		 * to the ADB log by default. You can disable logging from the SDK by setting the logger to
		 * [SessionLogger.NONE] or perform custom logging by providing your own implementation of
		 * [SessionLogger].
		 *
		 * @param logger the SessionLogger implementation to use for this session
		 */
		@Suppress("unused") // It's used by SDK consumers
		fun setLogger(logger: SessionLogger): Builder {
			this.logger = logger
			return this
		}

        /**
         * Generate a [Session] object from the arguments passed to this builder
         */
        fun build(): Session {
            context.setLogger(logger)
            return Session(context, address, authToken, username, password, channel)
        }
    }

    /**
     * Asynchronously connect to a server.
     * Must be called on the main UI thread.
     * Connection state change callbacks are guaranteed to be called on the main UI thread.
     *
     * @return True if connection was initiated, a callback will be invoked later to notify about connection status changes
     *         False if connection was not initiated, no callbacks will be invoked
     */
    fun connect(): Boolean {
        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(channel) || !initialized) return false
        disconnect() // FIXME: Why do we call disconnect() here?
        return performConnect()
    }

    /**
     * Close the open connection.
     * Must be called on the main UI thread.
     */
	@Suppress("MemberVisibilityCanBePrivate") // Part of public API
	fun disconnect() {
        if (!initialized) return
        if (performDisconnect()) {
            sessionListener?.onDisconnected(this)
        }
    }


	// NEW API
	/**
	 * Sends a text message to the channel
	 *
	 * @param message the message to send
	 */
	fun sendText(message: String) {
		if (!initialized) return
		val transport = transport ?: return
		CommandSendText(transport, message).send()
	}

	/**
	 * Sends a text message to a specific user in the channel
	 *
	 * @param message the message to send
	 * @param recipient The username for the user to send the message to. Other users in the channel
	 * won't receive the message.
	 */
	fun sendText(message: String, recipient: String) {
		if (!initialized) return
		val transport = transport ?: return
		CommandSendText(transport, message, recipient = recipient).send()
	}

	/**
	 * Sends an image message to the channel
	 *
	 * The Zello channels client will resize images that are larger than 1,280x1,280 to have a maximum
	 * height or width of 1,280 pixels. A 90x90 thumbnail will also be generated and sent before the
	 * full-sized image data is sent.
	 *
	 * If an error is encountered while sending the image, the `SessionListener` method `onError()` will
	 * be called with an error describing what went wrong.
	 *
	 * @param image the image to send
	 *
	 * @return `true` if the image metadata was sent successfully. `false` if an error was encountered
	 * before the image metadata could be sent.
	 */
	fun sendImage(image: Bitmap) {
		if (!initialized) return
		val transport = transport ?: return
		imageMessageManager.sendImage(image, transport, continuation = { _, error ->
			if (error != null) {
				sessionListener?.onError(this, error)
			}
		})
	}

	/**
	 * Sends an image message to a specific user in the channel
	 *
	 * The Zello channels client will resize images that are larger than 1,280x1,280 to have a maximum
	 * height or width of 1,280 pixels. A 90x90 thumbnail will also be generated and sent before the
	 * full-sized image data is sent.
	 *
	 * If an error is encountered while sending the image, the `SessionListener` method `onError()` will
	 * be called with an error describing what went wrong.
	 *
	 * @param image the image to send
	 * @param recipient The username for the use to send the message to. Other users in the channel
	 * won't receive the message.
	 *
	 * @return `true` if the image metadata was sent successfully. `false` if an error was encountered
	 * before the image metadata could be sent.
	 */
	fun sendImage(image: Bitmap, recipient: String) {
		if (!initialized) return
		val transport = transport ?: return
		imageMessageManager.sendImage(image, transport, recipient = recipient, continuation = { _, error ->
			if (error != null) {
				sessionListener?.onError(this, error)
			}
		})
	}

	/**
	 * Criteria used when determining the user's location for `sendLocation` calls.
	 *
	 * If not set, Android's default `Criteria` is used. Altitude, speed, and bearing are not sent
	 * to the channel. If a Criteria is set that has those features enabled, they will be ignored.
	 */
	@Suppress("MemberVisibilityCanBePrivate") // Part of public API
	var locationCriteria: Criteria = Criteria()
		set(newCriteria) {
			newCriteria.isBearingRequired = false
			newCriteria.isSpeedRequired = false
			newCriteria.isAltitudeRequired = false
			field = newCriteria
		}

	/**
	 * Sends the user's current location to the channel
	 *
	 * When the user's location is found, `continuation` is also called with the location so you can
	 * update your app to reflect the location they are reporting to the channel.
	 *
	 * @param continuation Called after the current location is found and reverse geocoding is performed.
	 * If the location was found, it reports the location as well as a reverse geocoded description
	 * if available. If an error was encountered acquiring the location, it reports the error.
	 */
	fun sendLocation(continuation: SentLocationCallback?): Boolean {
		if (!initialized) return false
		val transport = transport ?: return false
		if (!context.hasLocationPermission) return false

		context.locationManager.sendLocation(transport, locationCriteria, continuation)
		return true
	}

	/**
	 * Sends the user's current location to a specific user in the channel
	 *
	 * When the user's location is found, `continuation` is also called with the location so you can
	 * update your app to reflect the location they are reporting to the channel.
	 *
	 * @param recipient The username for the user to send the message to. Other users in the channel
	 * won't receive the message.
	 * @param continuation Called after the current location is found and reverse geocoding is performed.
	 * If the location was found, it reports the location as well as a reverse geocoded description
	 * if available. If an error was encountered acquiring the location, it reports the error.
	 */
	fun sendLocation(recipient: String, continuation: SentLocationCallback?): Boolean {
		if (!initialized) return false
		val transport = transport ?: return false
		if (!context.hasLocationPermission) return false

		context.locationManager.sendLocation(transport, locationCriteria, recipient, continuation)
		return true
	}

    /**
     * Start an outgoing voice message.
     * Must be called on the main UI thread.
     *
     * @return A non-null voice stream object if a message was successfully started.
     */
    fun startVoiceMessage(): OutgoingVoiceStream? {
        if (!initialized) return null
        val transport = transport ?: return null
        return voiceManager.startVoiceOut(this, sessionListener, transport)
    }

	/**
	 * Start an outgoing voice message.
	 * Must be called on the main UI thread.
	 *
	 * @param recipient The username for the user to send the message to. Other users in the channel
	 * won't receive the message.
	 * @return A non-null voice stream object if a message was successfully started.
	 */
	fun startVoiceMessage(recipient: String): OutgoingVoiceStream? {
		if (!initialized) return null
		val transport = transport ?: return null
		return voiceManager.startVoiceOut(this, sessionListener, transport, recipient = recipient)
	}

	/**
     * Creates and starts a voice stream to the server using a custom voice source instead of the
     * device microphone.
     * Must be called on the main UI thread.
     *
     * @param sourceConfig specifies the voice source object for the message
     * @return the stream that will be handling the voice message
     */
    fun startVoiceMessage(sourceConfig: OutgoingVoiceConfiguration): OutgoingVoiceStream? {
        if (!initialized) return null
        val transport = transport ?: return null
        return voiceManager.startVoiceOut(this, sessionListener, transport, voiceConfiguration = sourceConfig)
    }

	/**
	 * Creates and starts a voice stream to the server using a custom voice source instead of the
	 * device microphone.
	 * Must be called on the main UI thread.
	 *
	 * @param recipient The username for the user to send the message to. Other users in the channel
	 * won't receive the message.
	 * @param sourceConfig specifies the voice source object for the message
	 * @return the stream that will be handling the voice message
	 */
	fun startVoiceMessage(recipient: String, sourceConfig: OutgoingVoiceConfiguration): OutgoingVoiceStream? {
		if (!initialized) return null
		val transport = transport ?: return null
		return voiceManager.startVoiceOut(this, sessionListener, transport, recipient = recipient, voiceConfiguration = sourceConfig)
	}

	private fun performConnect(): Boolean {
        val address = this.address
        if (transport != null) return false
        val transport = context.transportFactory.instantiate()
        return try {
            transport.connect(object : TransportEvents {
                override fun onConnectSucceeded() {
                    nextReconnectDelay = 1.0
                    this@Session.onConnectSucceeded()
                }

                override fun onDisconnected() {
                    this@Session.onDisconnected()
                }

                override fun onConnectFailed() {
                    this@Session.onConnectFailed()
                }

                override fun onIncomingCommand(command: String, json: JSONObject, ack: TransportReadAck?) {
                    this@Session.onIncomingCommand(command, json, ack)
                }

                override fun onIncomingVoiceStreamData(streamId: Int, packetId: Int, data: ByteArray) {
                    this@Session.onIncomingVoiceStreamData(streamId, packetId, data)
                }

				override fun onIncomingImageData(imageId: Int, imageType: Int, data: ByteArray) {
					this@Session.onIncomingImageData(imageId, imageType, data)
				}
            }, address, requestTimeoutSec)
            state = SessionState.CONNECTING
            this.transport = transport
            sessionListener?.onConnectStarted(this)
            true
        } catch (exception: SessionConnectErrorException) {
            sessionListener?.onConnectFailed(this, exception.error)
            false
        }
    }

	private fun onIncomingImageData(imageId: Int, imageType: Int, data: ByteArray) {
		imageMessageManager.onImageData(imageId, imageType, data)
	}

    private fun performDisconnect(): Boolean {
        val transport = transport
        state = SessionState.DISCONNECTED
		channelFeatures = EnumSet.noneOf(ChannelFeature::class.java)
		channelUsersOnline = 0
        this.transport = null
        voiceManager.reset()
        if (transport == null) return false
        commandLogon?.close()
        commandLogon = null
        transport.disconnect()
        return true
    }

    private fun startLogon() {
        val transport = transport ?: return
        val commandLogon = object : CommandLogon(transport, authToken, null, username, password, channel) {
            override fun onSuccess(refreshToken: String?) {
                close()
                if (this != this@Session.commandLogon) {
                    return
                }

                this@Session.refreshToken = refreshToken
                state = SessionState.CONNECTED
                sessionListener?.onConnectSucceeded(this@Session)
            }

            override fun onFailure(error: SessionConnectError) {
                close()
                if (this != this@Session.commandLogon) {
                    return
                }

                state = SessionState.DISCONNECTED
                performDisconnect()
                setConnectError(error)
            }
        }
        this.commandLogon = commandLogon
        commandLogon.send()
    }

    private fun setConnectError(error: SessionConnectError) {
        sessionListener?.onConnectFailed(this, error)
    }

    private fun onConnectSucceeded() {
        startLogon()
    }

    private fun onConnectFailed() {
        if (reconnectWithRefreshToken()) {
            return
        }

        state = SessionState.DISCONNECTED
        performDisconnect()
        setConnectError(SessionConnectError.CONNECT_FAILED)
    }

    private fun onDisconnected() {
        if (reconnectWithRefreshToken()) {
            return
        }

        state = SessionState.DISCONNECTED
        performDisconnect()
        sessionListener?.onDisconnected(this)
    }

    /**
     * Handles automatically reconnecting after a disconnection
     *
     * @return true if we're reconnecting and false if we're not
     */
    private fun reconnectWithRefreshToken(): Boolean {
        val refreshToken = refreshToken
        if (refreshToken != null) {
            val shouldReconnect = sessionListener?.onSessionWillReconnect(this, ReconnectReason.UNKNOWN) ?: false
            if (shouldReconnect) {
                performDisconnect()
                state = SessionState.CONNECTING
                reconnectAfterDelay()
                return true
            }
        }

        return false
    }

    private fun reconnectAfterDelay() {
        // Adjustment in [0.5, 1.5) gives us a random value around the delay increment
        val adjustment = Math.random() + 0.5
        val delay = nextReconnectDelay * adjustment
        // Exponential backoff, capped at one minute
        nextReconnectDelay = min(nextReconnectDelay * 2.0, 60.0)
        context.runOnUiThread(Runnable {
            performConnect()
        }, (delay * 1000).toLong())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onIncomingCommand(command: String, json: JSONObject, ack: TransportReadAck?) {
        when (command) {
            Command.eventOnStreamStart -> startIncomingVoiceStream(json)
            Command.eventOnStreamStop  -> stopIncomingVoiceStream(json)
            Command.eventOnError -> handleServerError(json)
			Command.eventOnChannelStatus -> handleChannelStatus(json)
            Command.eventOnTextMessage -> handleTextMessage(json)
			Command.eventOnImageMessage -> handleImageMessage(json)
			Command.eventOnLocationMessage -> handleLocationMessage(json)
		}
	}

	private fun handleLocationMessage(json: JSONObject) {
		val maxLatitude = 90.0
		val minLatitude = -90.0
		val maxLongitude = 180.0
		val minLongitude = -180.0
		val minAccuracy = 0.0
		val sender = json.optString(Command.keyFrom)
		if (!json.has(Command.keyLatitude)) {
			val error = InvalidMessageFormatError(Command.eventOnLocationMessage, Command.keyLatitude, json, "Missing latitude")
			sessionListener?.onError(this, error)
			return
		}
		val latitude = json.optDouble(Command.keyLatitude)
		// Validate latitude value
		if (latitude < minLatitude || latitude > maxLatitude) {
			val error = InvalidMessageFormatError(Command.eventOnLocationMessage, Command.keyLatitude, json, "Latitude $latitude out of range $minLatitude..$maxLatitude")
			sessionListener?.onError(this, error)
			return
		}
		if (!json.has(Command.keyLongitude)) {
			val error = InvalidMessageFormatError(Command.eventOnLocationMessage, Command.keyLongitude, json, "Missing longitude")
			sessionListener?.onError(this, error)
			return
		}
		val longitude = json.optDouble(Command.keyLongitude)
		// Validate longitude value
		if (longitude < minLongitude || longitude > maxLongitude) {
			val error = InvalidMessageFormatError(Command.eventOnLocationMessage, Command.keyLongitude, json, "Longitude $longitude out of range $minLongitude..$maxLongitude")
			sessionListener?.onError(this, error)
			return
		}
		if (!json.has(Command.keyAccuracy)) {
			val error = InvalidMessageFormatError(Command.eventOnLocationMessage, Command.keyAccuracy, json, "Missing accuracy")
			sessionListener?.onError(this, error)
			return
		}
		val accuracy = json.optDouble(Command.keyAccuracy)
		// Validate accuracy value
		if (accuracy < minAccuracy) {
			val error = InvalidMessageFormatError(Command.eventOnLocationMessage, Command.keyAccuracy, json, "Accuracy $accuracy may not be negative")
			sessionListener?.onError(this, error)
			return
		}
		val address = if (json.has(Command.keyFormattedAddress)) { json.getString(Command.keyFormattedAddress) } else { null }

		val location = Location(latitude, longitude, accuracy, address)
		sessionListener?.onLocationMessage(this, sender, location)
	}

	private fun handleImageMessage(json: JSONObject) {
		val absurdDimension = 3000 // Don't allow width or height greater than this

		val sender = json.optString(Command.keyFrom)
		if (!json.has(Command.keyMessageId)) {
			val error = InvalidMessageFormatError(Command.eventOnImageMessage, Command.keyMessageId, json, "Missing image id")
			sessionListener?.onError(this, error)
			return
		}
		val imageId = json.optInt(Command.keyMessageId)
		if (!json.has(Command.keyType)) {
			val error = InvalidMessageFormatError(Command.eventOnImageMessage, Command.keyType, json, "Missing image type")
			sessionListener?.onError(this, error)
			return
		}
		val compressionType = json.getString(Command.keyType)
		if (!imageMessageManager.isTypeValid(compressionType)) {
			val error = InvalidMessageFormatError(Command.eventOnImageMessage, Command.keyType, json, "Invalid image type '$compressionType'")
			sessionListener?.onError(this, error)
			return
		}
		if (!json.has(Command.keyImageHeight)) {
			val error = InvalidMessageFormatError(Command.eventOnImageMessage, Command.keyImageHeight, json, "Missing image height")
			sessionListener?.onError(this, error)
			return
		}
		val height = json.optInt(Command.keyImageHeight)
		if (height < 1 || height > absurdDimension) {
			val error = InvalidMessageFormatError(Command.eventOnImageMessage, Command.keyImageHeight, json, "Height $height out of allowed range")
			sessionListener?.onError(this, error)
			return
		}
		if (!json.has(Command.keyImageWidth)) {
			val error = InvalidMessageFormatError(Command.eventOnImageMessage, Command.keyImageWidth, json, "Missing image width")
			sessionListener?.onError(this, error)
			return
		}
		val width = json.optInt(Command.keyImageWidth)
		if (width < 1 || width > absurdDimension) {
			val error = InvalidMessageFormatError(Command.eventOnImageMessage, Command.keyImageWidth, json, "Width $width out of allowed range")
			sessionListener?.onError(this, error)
			return
		}

		imageMessageManager.onImageHeader(imageId, sender, Dimensions(width, height))
	}

	private fun handleChannelStatus(json: JSONObject) {
		val imagesSupported = json.optBoolean(Command.keyImagesSupported, false)
		val textingSupported = json.optBoolean(Command.keyTextingSupported, false)
		val locationsSupported = json.optBoolean(Command.keyLocationsSupported, false)
		val features = EnumSet.noneOf(ChannelFeature::class.java)
		if (imagesSupported) {
			features.add(ChannelFeature.ImageMessages)
		}
		if (textingSupported) {
			features.add(ChannelFeature.TextMessages)
		}
		if (locationsSupported) {
			features.add(ChannelFeature.LocationMessages)
		}
		channelFeatures = features
		channelUsersOnline = json.optInt(Command.keyUsersOnline, 0)

		sessionListener?.onChannelStatusUpdate(this)
	}

    private fun handleTextMessage(json: JSONObject) {
        val message = json.optString(Command.keyTextMessageBody) ?: return
        val sender = json.optString(Command.keyFrom) ?: return
        sessionListener?.onTextMessage(this, message = message, sender = sender)
    }

    private fun handleServerError(json: JSONObject) {
        val error = json.optString(Command.keyError) ?: return
        logger.log("Got server error: $error")
        if (error == Command.errorServerClosedConnection) {
            // Don't reconnect
            refreshToken = null
        }
    }

    private fun onIncomingVoiceStreamData(streamId: Int, packetId: Int, data: ByteArray) {
        // Important: the voice message manager does not do anything with the order
        // of incoming audio packets. It's possible that some packets arrive out of order
        // or even get lost. If the server does not start handling at least the problem
        // packet reordering, we will have to implement the logic here on the client side.
        voiceManager.findIncomingVoice(streamId)?.onData(packetId, data)
    }

    private fun startIncomingVoiceStream(json: JSONObject) {
        val sender = json.optString(Command.keyFrom)
        val channel = json.optString(Command.keyChannel)
        val config: IncomingVoiceConfiguration?
        if (sender.isEmpty() || channel.isEmpty()) {
            sessionListener?.onConnectFailed(this, SessionConnectError.BAD_RESPONSE(json))
            return
        }

        config = sessionListener?.onIncomingVoiceWillStart(this, IncomingVoiceStreamInfo(sender, channel))

        voiceManager.startVoiceIn(
                this, sessionListener, json.optInt(Command.keyStreamId),
                json.optString(Command.keyCodec), Utils.decodeBase64(json.optString(Command.keyCodecHeader)),
                json.optInt(Command.keyPacketDuration), json.optString(Command.keyChannel),
                json.optString(Command.keyFrom), config)
    }

    private fun stopIncomingVoiceStream(json: JSONObject) {
        val voice = voiceManager.findIncomingVoice(json.optInt(Command.keyStreamId, json.optInt(Command.keyStreamIdAlt)))
        if (voice == null) {
            logger.log("Incoming voice matching id ${Command.keyStreamId} not found")
        }
        voice?.finish()
    }

    private companion object {
        private var loadedNativeLibraries: Boolean = false
    }

}
