import eu.pretix.libpretixnfc.commands.nxp.GetVersion
import eu.pretix.libpretixnfc.commands.nxp.ReadPages
import eu.pretix.libpretixnfc.commands.nxp.WritePage
import eu.pretix.libpretixnfc.commands.nxp.mf0aes.AuthenticatePart1
import eu.pretix.libpretixnfc.commands.nxp.mf0aes.AuthenticatePart2
import eu.pretix.libpretixnfc.commands.nxp.mf0aes.AuthenticationHelper
import eu.pretix.libpretixnfc.commands.nxp.mf0aes.UidHelper
import eu.pretix.libpretixnfc.commands.nxp.ntag21x.PwdAuth
import eu.pretix.libpretixnfc.communication.AbstractNfcA
import eu.pretix.libpretixnfc.cryptography.An10922KeyDiversification
import eu.pretix.libpretixnfc.tagtypes.TagType

/*
 * Swift-facing entry points.
 *
 * Kotlin terminates the process when an exception leaves a function that is not declared as
 * throwing, so calling the library's own methods directly from Swift turns a tag leaving the
 * field, a NAK, a CMAC mismatch or a failing [eu.pretix.libpretixnfc.cryptography.Cryptography]
 * implementation into a crash. These functions declare the same work as throwing, so Swift
 * receives an `NSError` carrying the Kotlin exception and can report the failure instead.
 *
 * They are declared to throw `Throwable` rather than the library's own error types because the
 * point is that nothing reaches Swift unhandled. the Kotlin exception is available on the
 * `NSError` under `KotlinException`.
 *
 */

/** Reads, and optionally encodes, a MIFARE Ultralight AES tag. Returns its UID as hex. */
@Throws(Throwable::class)
fun PretixMf0aes.processTag(nfca: AbstractNfcA, encodeWith: Mf0aesKeySet? = null): String =
    process(nfca, encodeWith)

/** Runs the three-way AES authentication and returns the reader to use for further commands. */
@Throws(Throwable::class)
fun AuthenticationHelper.authenticateTag(): AbstractNfcA = authenticate()

/** Reads whether the tag requires commands to carry a CMAC. */
@Throws(Throwable::class)
fun AuthenticationHelper.readCmacEnabled(): Boolean = checkCmacEnabled()

/** Reads the tag's UID and verifies its check bytes. */
@Throws(Throwable::class)
fun UidHelper.readTagUid(): ByteArray = readUid()

/*
 * One per command rather than one on `Command<T>`: an extension on the generic base is exported
 * as a category on the non-generic class, which would give Swift `Any?` instead of the command's
 * own result type.
 */

/** Reads the tag's version bytes and identifies the chip. */
@Throws(Throwable::class)
fun GetVersion.executeCommand(nfca: AbstractNfcA): TagType = execute(nfca)

/** Reads four pages starting at this command's page number. */
@Throws(Throwable::class)
fun ReadPages.executeCommand(nfca: AbstractNfcA): ByteArray = execute(nfca)

/** Writes one page. */
@Throws(Throwable::class)
fun WritePage.executeCommand(nfca: AbstractNfcA): ByteArray = execute(nfca)

/** Starts the three-way AES authentication. */
@Throws(Throwable::class)
fun AuthenticatePart1.executeCommand(nfca: AbstractNfcA): ByteArray = execute(nfca)

/** Completes the three-way AES authentication. */
@Throws(Throwable::class)
fun AuthenticatePart2.executeCommand(nfca: AbstractNfcA): ByteArray = execute(nfca)

/** Authenticates against an NTAG 21x with a password. */
@Throws(Throwable::class)
fun PwdAuth.executeCommand(nfca: AbstractNfcA): ByteArray = execute(nfca)

/** Derives an AN10922 diversified AES-128 key. */
@Throws(Throwable::class)
fun An10922KeyDiversification.generateDiversifiedKey(
    masterKey: ByteArray,
    uid: ByteArray,
    applicationId: ByteArray,
    systemId: ByteArray,
): ByteArray = generateDiversifiedKeyAES128(masterKey, uid, applicationId, systemId)
