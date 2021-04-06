package com.tangem.tasks

import com.tangem.*
import com.tangem.commands.*
import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.CardDeserializer
import com.tangem.commands.common.card.FirmwareVersion
import com.tangem.commands.common.card.masks.Product
import com.tangem.commands.common.card.masks.Settings
import com.tangem.commands.wallet.CheckWalletCommand
import com.tangem.commands.wallet.WalletIndex
import com.tangem.commands.wallet.WalletStatus
import com.tangem.common.CompletionResult
import com.tangem.common.TangemSdkConstants
import com.tangem.common.extensions.guard

/**
 * Task that allows to read Tangem card and verify its private key.
 *
 * It performs two commands, [ReadCommand] and [CheckWalletCommand], subsequently.
 */
class ScanTask(
    private var walletIndex: WalletIndex? = null
) : CardSessionRunnable<Card>, PreflightReadCapable {

    override val requiresPin2 = false

    override fun preflightReadSettings(): PreflightReadSettings = when (walletIndex) {
        null -> PreflightReadSettings.FullCardRead
        else -> PreflightReadSettings.ReadWallet(walletIndex!!)
    }

    override fun run(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        if (session.connectedTag == TagType.Slix) {
            readSlixTag(session, callback)
            return
        }

        val card = session.environment.card
        if (card == null) {
            callback(CompletionResult.Failure(TangemSdkError.CardError()))
            return
        }
        card.isPin1Default = session.environment.pin1?.isDefault == true

        val firmwareNumber = card.firmwareVersion
        if (firmwareNumber != FirmwareVersion.zero && firmwareNumber > FirmwareVersion(1, 19) // >1.19 cards without SD on CheckPin
            && card.settingsMask?.contains(Settings.ProhibitDefaultPIN1) != true) {
            CheckPinCommand().run(session) { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        card.isPin2Default = result.data.isPin2Default
                        session.environment.card = card
                        runCheckWalletIfNeeded(card, session, callback)
                    }
                    is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
                }
            }
        } else {
            session.environment.card = card
            runCheckWalletIfNeeded(card, session, callback)
        }
    }

    private fun runCheckWalletIfNeeded(
        card: Card, session: CardSession,
        callback: (result: CompletionResult<Card>) -> Unit
    ) {
        if (card.cardData?.productMask?.contains(Product.Tag) != false) {
            callback(CompletionResult.Success(card))
            return
        }

        val index = if (card.firmwareVersion < FirmwareConstraints.AvailabilityVersions.walletData) {
            WalletIndex.Index(TangemSdkConstants.oldCardDefaultWalletIndex)
        } else {
            if (walletIndex == null) {
                callback(CompletionResult.Success(card))
                return
            } else {
                walletIndex!!
            }
        }

        val wallet = card.wallet(index).guard {
            callback(CompletionResult.Failure(TangemSdkError.WalletNotFound()))
            return
        }

        when {
            wallet.status != WalletStatus.Loaded -> {
                callback(CompletionResult.Success(card))
                return
            }
            wallet.curve == null || wallet.publicKey == null -> {
                callback(CompletionResult.Failure(TangemSdkError.WalletError()))
                return
            }
        }

        val checkWalletCommand = CheckWalletCommand(wallet.curve!!, wallet.publicKey!!)
        checkWalletCommand.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> callback(CompletionResult.Success(card))
                is CompletionResult.Failure -> callback(CompletionResult.Failure(result.error))
            }
        }

    }

    private fun readSlixTag(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        session.readSlixTag { result ->
            when (result) {
                is CompletionResult.Success -> {
                    try {
                        val card = CardDeserializer.deserialize(result.data)
                        callback(CompletionResult.Success(card))
                    } catch (error: TangemSdkError) {
                        callback(CompletionResult.Failure(error))
                    }
                }
                is CompletionResult.Failure -> {
                    callback(CompletionResult.Failure(result.error))
                }
            }
        }
    }
}