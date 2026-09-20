@file:JvmName("AuthenticationHelperKt")
@file:JvmMultifileClass

package eu.pretix.libpretixnfc.commands.nxp.mf0aes

import org.bouncycastle.jce.provider.BouncyCastleProvider
import javax.crypto.Mac

val staticAesCMac: Mac = Mac.getInstance("AESCMAC", BouncyCastleProvider())
