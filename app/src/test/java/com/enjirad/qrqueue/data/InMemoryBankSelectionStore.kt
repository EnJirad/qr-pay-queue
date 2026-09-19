package com.enjirad.qrqueue.data

import com.enjirad.qrqueue.domain.BankInfo

/**
 * A process-local [BankSelectionStore] for tests.
 *
 * It is backed by a map so a test can create a second instance over the same map
 * to simulate "close the app → open it again" without touching Android
 * SharedPreferences. The encode/decode rules are the real ones from
 * [BankSelectionCodec].
 */
class InMemoryBankSelectionStore(
    private val backing: MutableMap<String, String> = mutableMapOf(),
) : BankSelectionStore {

    override fun save(bank: BankInfo): Boolean {
        backing[BankSelectionCodec.KEY_BANK_PACKAGE] = bank.packageName
        backing[BankSelectionCodec.KEY_BANK_ID] = bank.id
        return true
    }

    override fun load(): BankInfo? = BankSelectionCodec.decode(
        packageName = backing[BankSelectionCodec.KEY_BANK_PACKAGE],
        bankId = backing[BankSelectionCodec.KEY_BANK_ID],
    )

    override fun clear() = backing.clear()
}
