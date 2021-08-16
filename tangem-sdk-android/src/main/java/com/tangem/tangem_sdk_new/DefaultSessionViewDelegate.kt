package com.tangem.tangem_sdk_new

import android.app.Activity
import android.os.Build
import com.tangem.Log
import com.tangem.Message
import com.tangem.SessionViewDelegate
import com.tangem.WrongValueType
import com.tangem.common.UserCodeType
import com.tangem.common.core.Config
import com.tangem.common.core.TangemError
import com.tangem.common.extensions.VoidCallback
import com.tangem.common.nfc.CardReader
import com.tangem.tangem_sdk_new.extensions.sdkThemeContext
import com.tangem.tangem_sdk_new.nfc.NfcAntennaLocationProvider
import com.tangem.tangem_sdk_new.nfc.NfcManager
import com.tangem.tangem_sdk_new.ui.AttestationFailedDialog
import com.tangem.tangem_sdk_new.ui.NfcSessionDialog

/**
 * Default implementation of [SessionViewDelegate].
 * If no customisation is required, this is the preferred way to use Tangem SDK.
 */
class DefaultSessionViewDelegate(
    private val nfcManager: NfcManager,
    private val reader: CardReader
) : SessionViewDelegate {

    var sdkConfig: Config? = null

    lateinit var activity: Activity
    private var readingDialog: NfcSessionDialog? = null
    private var stoppedBySession: Boolean = false

    override fun onSessionStarted(cardId: String?, message: Message?, enableHowTo: Boolean) {
        Log.view { "Session started" }
        createAndShowState(SessionViewDelegateState.Ready(formatCardId(cardId), message), enableHowTo)
    }

    override fun onSessionStopped(message: Message?) {
        Log.view { "Session stopped" }
        stoppedBySession = true
        readingDialog?.show(SessionViewDelegateState.Success(message))
    }

    override fun onSecurityDelay(ms: Int, totalDurationSeconds: Int) {
        Log.view { "Showing security delay: $ms, $totalDurationSeconds" }
        readingDialog?.show(SessionViewDelegateState.SecurityDelay(ms, totalDurationSeconds))
    }

    override fun onDelay(total: Int, current: Int, step: Int) {
        Log.view { "Showing delay" }
        readingDialog?.show(SessionViewDelegateState.Delay(total, current, step))
    }

    override fun onTagLost() {
        Log.view { "Tag lost" }
        readingDialog?.show(SessionViewDelegateState.TagLost)
    }

    override fun onTagConnected() {
        Log.view { "Tag connected" }
        readingDialog?.show(SessionViewDelegateState.TagConnected)
    }

    override fun onWrongCard(wrongValueType: WrongValueType) {
        Log.view { "Wrong card detected" }
        readingDialog?.show(SessionViewDelegateState.WrongCard(wrongValueType))
    }

    override fun onError(error: TangemError) {
        readingDialog?.show(SessionViewDelegateState.Error(error))
    }

    override fun requestUserCode(type: UserCodeType, isFirstAttempt: Boolean, callback: (pin: String) -> Unit) {
        Log.view { "Showing pin request with type: $type" }
        readingDialog?.show(SessionViewDelegateState.PinRequested(type, isFirstAttempt, callback))
    }

    override fun requestUserCodeChange(type: UserCodeType, callback: (pin: String) -> Unit) {
        Log.view { "Showing pin change request with type: $type" }
        createAndShowState(SessionViewDelegateState.PinChangeRequested(type, callback), false)
    }

    override fun dismiss() {
        readingDialog?.dismiss()
    }

    override fun setConfig(config: Config) {
        sdkConfig = config
    }

    override fun setMessage(message: Message?) {
        Log.view { "Set message with header: ${message?.header}, and body: ${message?.body}" }
        readingDialog?.setMessage(message)
    }


    override fun attestationDidFail(isDevCard: Boolean, positive: VoidCallback, negative: VoidCallback) {
        AttestationFailedDialog.didFail(activity, isDevCard, positive) {
            negative()
            dismiss()
        }
    }

    override fun attestationCompletedOffline(positive: VoidCallback, negative: VoidCallback, retry: VoidCallback) {
        AttestationFailedDialog.completedOffline(activity, positive, {
            negative()
            dismiss()
        }, retry)
    }

    override fun attestationCompletedWithWarnings(positive: VoidCallback) {
        AttestationFailedDialog.completedWithWarnings(activity, positive)
    }

    private fun createAndShowState(state: SessionViewDelegateState, enableHowTo: Boolean, message: Message? = null) {
        postUI {
            if (readingDialog == null) createReadingDialog(activity)
            readingDialog?.enableHowTo(enableHowTo)
            readingDialog?.setMessage(message)
            readingDialog?.show(state)
        }
    }

    private fun createReadingDialog(activity: Activity) {
        val nfcLocationProvider = NfcAntennaLocationProvider(Build.DEVICE)
        readingDialog = NfcSessionDialog(activity.sdkThemeContext(), nfcManager, nfcLocationProvider).apply {
            setOwnerActivity(activity)
            dismissWithAnimation = true
            stoppedBySession = false
            create()
            setOnCancelListener {
                if (!stoppedBySession) reader.stopSession(true)
                createReadingDialog(activity)
            }
        }
    }

    private fun formatCardId(cardId: String?): String? {
        val cardId = cardId ?: return null
        val displayedNumbersCount = sdkConfig?.cardIdDisplayedNumbersCount ?: cardId.length
        val converter = cardId.dropLast(1).takeLast(displayedNumbersCount)

        return converter.chunked(4).joinToString(" ")
    }
}