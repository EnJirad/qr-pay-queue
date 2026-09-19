package com.enjirad.qrqueue.data

import android.content.Context
import com.enjirad.qrqueue.domain.BankInfo
import com.enjirad.qrqueue.domain.BankRegistry

/**
 * Persists the banking app the user chose as the payment destination.
 *
 * The interface exists so the selection rules can be unit-tested without Android:
 * production uses [SharedPreferencesBankSelectionStore], and tests use a small
 * in-memory implementation. The stored value must survive every form of process
 * death (V0.4.2 §5/§6), so the contract is deliberately tiny.
 */
interface BankSelectionStore {

    /** Stores [bank] as the current selection. Returns false if it did not reach disk. */
    fun save(bank: BankInfo): Boolean

    /** The stored bank, or null when nothing was saved yet (first install). */
    fun load(): BankInfo?

    /** Forgets the stored selection. */
    fun clear()
}

/**
 * The exact encode/decode rules for a persisted bank selection, kept pure and
 * unit-tested: the stable [BankInfo.id] is preferred, the package name is the
 * fallback for older values, and a stored value that no longer matches a known
 * bank decodes to null so the user chooses again instead of the app guessing.
 */
object BankSelectionCodec {

    const val KEY_BANK_PACKAGE = "selected_bank_package"
    const val KEY_BANK_ID = "selected_bank_id"

    fun decode(packageName: String?, bankId: String?): BankInfo? {
        if (packageName.isNullOrBlank()) return null
        val bank = bankId?.let { BankRegistry.findById(it) }
            ?: BankRegistry.findByPackage(packageName)
        // Reject a mismatched pair (id from one bank, package from another).
        return bank?.takeIf { it.packageName == packageName }
    }
}

/**
 * The production store: two app-private SharedPreferences strings. Uninstalling
 * the app removes the selection, which is the intended behaviour.
 */
class SharedPreferencesBankSelectionStore(context: Context) : BankSelectionStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun save(bank: BankInfo): Boolean = runCatching {
        prefs.edit()
            .putString(BankSelectionCodec.KEY_BANK_PACKAGE, bank.packageName)
            .putString(BankSelectionCodec.KEY_BANK_ID, bank.id)
            .apply()
    }.isSuccess

    override fun load(): BankInfo? = BankSelectionCodec.decode(
        packageName = prefs.getString(BankSelectionCodec.KEY_BANK_PACKAGE, null),
        bankId = prefs.getString(BankSelectionCodec.KEY_BANK_ID, null),
    )

    override fun clear() {
        prefs.edit()
            .remove(BankSelectionCodec.KEY_BANK_PACKAGE)
            .remove(BankSelectionCodec.KEY_BANK_ID)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "qr_queue_bank"
    }
}
