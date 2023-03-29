package com.tangem.crypto.bip39

import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError

class DefaultMnemonic : Mnemonic {

    override val mnemonicComponents: List<String>
    override val wordlist: Wordlist

    private val bip39 = DefaultBIP39()

    init {
        wordlist = bip39.wordlist
    }

    @Throws(TangemSdkError.MnemonicException::class)
    constructor(mnemonic: String) {
        mnemonicComponents = bip39.parse(mnemonic)
    }

    @Throws(TangemSdkError.MnemonicException::class)
    constructor(entropy: EntropyLength, wordlist: Wordlist) {
        mnemonicComponents = bip39.generateMnemonic(entropy, wordlist)
    }

    override fun generateSeed(passphrase: String): CompletionResult<ByteArray> {
        return bip39.generateSeed(mnemonicComponents, passphrase)
    }
}