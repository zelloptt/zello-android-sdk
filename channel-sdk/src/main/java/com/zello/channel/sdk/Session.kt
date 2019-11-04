package com.zello.channel.sdk

import android.content.Context
import android.graphics.Bitmap
import com.zello.channel.sdk.commands.Command
import com.zello.channel.sdk.commands.CommandLogon
import com.zello.channel.sdk.platform.Utils
import com.zello.channel.sdk.transport.Transport
import com.zello.channel.sdk.transport.TransportEvents
import com.zello.channel.sdk.transport.TransportReadAck
import org.json.JSONObject
import java.util.EnumSet

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

    private val voiceManager = VoiceManager(context)
    private var transport: Transport? = null
    private var commandLogon: CommandLogon? = null

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

    init {
        initialized = context.loadNativeLibraries(context.getLogger())
        if (!initialized) state = SessionState.ERROR
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
    fun disconnect() {
        if (!initialized) return
        if (performDisconnect()) {
            sessionListener?.onDisconnected(this)
        }
    }

	// TODO: Make sure this is still easily usable from Java
	enum class ChannelFeature {
		ImageMessages,
		TextMessages,
		LocationMessages
	}

	var channelFeatures: EnumSet<ChannelFeature> = EnumSet.noneOf(ChannelFeature::class.java)
		private set



	/// NEW API
	/**
	 * Sends a text message to the channel
	 *
	 * @param message the message to send
	 */
	fun sendText(message: String) {
		// TODO: Implement sendText(message:)
	}

	/**
	 * Sends a text message to the channel
	 *
	 * @param message the message to send
	 * @param recipient The username for the user to send the message to. Other users in the channel
	 * won't receive the message.
	 */
	fun sendText(message: String, recipient: String) {
		// TODO: Implement sendText(message:, recipient:)
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
	fun sendImage(image: Bitmap): Boolean {
		// TODO: Implement sendImage(image:)
		return false
	}

	fun sendImage(image: Bitmap, recipient: String): Boolean {
		// TODO: Implement sendImage(image:, recipient:)
		return false
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
	// TODO: Define error type for location messages
	fun sendLocation(continuation: SentLocationCallback?) {
		// TODO: Implement sendLocation(continuation:)
	}

	fun sendLocation(recipient: String, continuation: SentLocationCallback?) {
		// TODO: Implement sendLocation(recipient:, continuation:)
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

    private fun performDisconnect(): Boolean {
        val transport = transport
        state = SessionState.DISCONNECTED
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
        nextReconnectDelay = Math.min(nextReconnectDelay * 2.0, 60.0)
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
        }
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
        if (sender == null || channel == null) {
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
        private var initialized: Boolean = false
    }

}
