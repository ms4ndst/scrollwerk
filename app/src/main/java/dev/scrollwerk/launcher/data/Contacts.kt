package dev.scrollwerk.launcher.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dev.scrollwerk.launcher.search.ContactEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Contact names for launcher search, read on demand and kept only in memory. */
class ContactsRepository(context: Context) {
    private val ctx = context.applicationContext

    fun hasPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun load(): List<ContactEntry> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )
        val out = mutableListOf<ContactEntry>()
        runCatching {
            ctx.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val id = c.getLong(0)
                    out += ContactEntry(
                        id = id,
                        name = name,
                        lookupUri = ContactsContract.Contacts.getLookupUri(id, c.getString(1)).toString(),
                        hasPhone = c.getInt(3) > 0,
                    )
                }
            }
        }
        out
    }

    /** The contact's primary (else first) phone number. */
    suspend fun phoneNumber(contact: ContactEntry): String? = withContext(Dispatchers.IO) {
        runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contact.id.toString()),
                "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    fun open(contact: ContactEntry) = start(Intent(Intent.ACTION_VIEW, Uri.parse(contact.lookupUri)))

    /** Opens the dialer with the number filled in; no call permission needed. */
    fun dial(number: String) = start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    fun message(number: String) = start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

    private fun start(intent: Intent) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
