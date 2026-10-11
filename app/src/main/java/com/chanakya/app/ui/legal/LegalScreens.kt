package com.chanakya.app.ui.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chanakya.app.BuildConfig
import com.chanakya.app.R
import com.chanakya.core.designsystem.component.ChanakyaCard
import com.chanakya.core.designsystem.theme.MonoTextStyle

/**
 * The About, Terms and Privacy screens.
 *
 * The body text lives here as plain strings rather than in `strings.xml`: it is long-form
 * prose, not UI labels, and keeping it in one place makes it obvious what the app is
 * promising. Only the screen titles are localised.
 *
 * Both legal documents describe what the app *actually* does. If a future change makes
 * one of these statements untrue, the statement has to change with it - a privacy claim
 * that has drifted from the code is worse than no claim at all.
 */

/** One titled block of prose. */
private data class LegalSection(val heading: String, val body: String)

// ---- About -----------------------------------------------------------------

private val ABOUT_SECTIONS = listOf(
    LegalSection(
        "What Chanakya is",
        "Chanakya is a tracker blocker for Android that runs entirely on your device. " +
            "It watches the domain lookups your apps make, blocks the ones belonging to " +
            "advertising and analytics networks, and shows you which app contacted what. " +
            "Nothing is sent anywhere: there is no account, no cloud and no Chanakya server.",
    ),
    LegalSection(
        "How it works",
        "Android gives apps no way to filter traffic without root except the VPN " +
            "interface, so Chanakya uses a local one. A VPN normally means a remote " +
            "server that your traffic passes through; this one never leaves the phone. " +
            "Lookups are examined in the tunnel, blocked ones are answered with a dead " +
            "address so the connection is never made, and everything else is forwarded " +
            "straight out over your normal connection.",
    ),
    LegalSection(
        "Design principles",
        "Three things shape every decision in the app. Nothing leaves the device - if a " +
            "feature could not be built that way, it was not built. You stay in control - " +
            "every block is visible and reversible, by app, by domain or by list. And the " +
            "app is honest about what it cannot do: it stops new tracking, but it cannot " +
            "erase a profile that already exists on someone else's servers.",
    ),
    LegalSection(
        "Built with",
        "Kotlin and Jetpack Compose, with a hand-written dependency graph instead of a " +
            "framework, and no third-party analytics, advertising or crash-reporting SDKs. " +
            "A packet parser, a DNS message reader and a small rule engine do the work.",
    ),
    LegalSection(
        "Open source",
        "Chanakya is open source, so every claim on this screen can be checked against " +
            "the code rather than taken on trust. The source, the licence and the release " +
            "history are all in the project repository.",
    ),
    LegalSection(
        "Version",
        "Chanakya ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
    ),
)

// ---- Terms -----------------------------------------------------------------

private val TERMS_SECTIONS = listOf(
    LegalSection(
        "1. Acceptance",
        "By installing or using Chanakya you agree to these terms. If you do not agree " +
            "with them, please uninstall the app.",
    ),
    LegalSection(
        "2. What the app is",
        "Chanakya is a free, open-source tool that filters network requests on your own " +
            "device. It is a utility, not a service, and no account or subscription is " +
            "involved. You install it, you run it, and it acts only on your phone.",
    ),
    LegalSection(
        "3. No guarantee of blocking",
        "Chanakya blocks what the blocklists and rules identify. Tracker networks change " +
            "domains, rotate addresses and serve from infrastructure shared with ordinary " +
            "services, so no blocker of this kind can promise complete coverage. Apps that " +
            "use their own encrypted DNS, or that connect to hardcoded addresses, may not " +
            "be caught unless IP-level blocking is switched on.",
    ),
    LegalSection(
        "4. Your responsibility",
        "You are responsible for how you use the app and for the device it runs on. Do " +
            "not use Chanakya to break any law that applies to you. Because the app routes " +
            "traffic through a local VPN interface, you should be aware that Android " +
            "permits only one active VPN at a time, so Chanakya cannot run alongside " +
            "another VPN app.",
    ),
    LegalSection(
        "5. No warranty",
        "The app is provided \"as is\", without warranty of any kind, express or implied, " +
            "including but not limited to the implied warranties of merchantability, " +
            "fitness for a particular purpose and non-infringement. You use it at your own " +
            "risk.",
    ),
    LegalSection(
        "6. Limitation of liability",
        "To the fullest extent permitted by law, the authors and contributors of Chanakya " +
            "are not liable for any indirect, incidental, special, consequential or " +
            "punitive damages, or any loss of data, profits or connectivity, arising out of " +
            "or in connection with your use of the app.",
    ),
    LegalSection(
        "7. Licence",
        "Chanakya is distributed under the open-source licence published in the project " +
            "repository. Your rights to use, copy, modify and redistribute the software " +
            "come from that licence, and it governs the software where it conflicts with " +
            "anything written here.",
    ),
    LegalSection(
        "8. Changes to these terms",
        "These terms may be updated as the app changes. The version published with each " +
            "release is the version that applies to that release, and the history is " +
            "visible in the repository.",
    ),
    LegalSection(
        "9. Governing law",
        "These terms are governed by the laws of India, without regard to conflict of law " +
            "rules.",
    ),
)

// ---- Privacy ---------------------------------------------------------------

private val PRIVACY_SECTIONS = listOf(
    LegalSection(
        "The short version",
        "Chanakya keeps everything on your phone. It has no servers, no account and no " +
            "analytics. Nothing it observes is transmitted to the developers or to anyone " +
            "else, because there is no mechanism in the app that could transmit it.",
    ),
    LegalSection(
        "What the app processes",
        "To block trackers the app has to look at the requests your apps make. For each " +
            "domain lookup it records the domain, the app that asked for it, and the time. " +
            "That record is written to a database on your device so the Home, Apps and " +
            "Reports screens have something to show. It never leaves the phone.",
    ),
    LegalSection(
        "What never leaves your device",
        "No account details, because there are no accounts. No usage analytics or " +
            "telemetry. No crash reports. No advertising identifiers. No device " +
            "identifiers sent anywhere. No domain history uploaded for \"improving the " +
            "service\", because there is no service to improve. The app contains no " +
            "third-party analytics, advertising or crash-reporting libraries.",
    ),
    LegalSection(
        "Why it needs a VPN",
        "Android offers no way to filter traffic without root except the VPN interface, " +
            "which is why the app asks for it. A VPN usually means a remote server; " +
            "Chanakya's tunnel is local to your phone. Requests are inspected on the device " +
            "and then forwarded over your normal connection. Your traffic is never " +
            "redirected through infrastructure belonging to the developers.",
    ),
    LegalSection(
        "Permissions and why",
        "VPN service - to create the local tunnel that filters requests.\n\n" +
            "Network and internet access - to read your network's DNS resolvers and to " +
            "forward lookups that are not blocked.\n\n" +
            "Query installed apps - to tell you which app made a request, and to show app " +
            "names and icons. The list is read on your device and is not transmitted.\n\n" +
            "Notifications - for the ongoing protection notification and the periodic " +
            "summary.\n\n" +
            "Run at startup - only if you enable it, so protection resumes after a reboot.",
    ),
    LegalSection(
        "Blocklist imports",
        "If you import a blocklist from a file, the file is read on your device and its " +
            "contents are stored locally. Nothing about the import is uploaded.",
    ),
    LegalSection(
        "The advertising ID tool",
        "Android ties advertising identifiers to your device, and those identifiers are " +
            "how ad networks link activity across apps. Chanakya can open the system screen " +
            "where you can reset that identifier, and can remind you periodically. The app " +
            "never reads your advertising ID and never changes it for you.",
    ),
    LegalSection(
        "How long data is kept",
        "Detailed events are kept on the device for a limited window, after which they " +
            "are rolled up into daily totals and the detail is discarded. Daily totals " +
            "remain on the device so long-term reports work. Uninstalling the app removes " +
            "the database with it.",
    ),
    LegalSection(
        "Third parties",
        "There are none. The app does not include advertising networks, analytics " +
            "providers or crash reporters, and it shares nothing with anyone.",
    ),
    LegalSection(
        "Children",
        "Chanakya is a general-purpose utility and is not directed at children. It " +
            "collects no personal information from anyone, of any age.",
    ),
    LegalSection(
        "Changes to this policy",
        "If a future version of the app changes what it does with your data, this screen " +
            "changes with it, in the same release. Because the project is open source, the " +
            "history of that text is public and can be compared against the code.",
    ),
)

// ---- Screens ---------------------------------------------------------------

/** The About screen: what the app is, how it works, and its design principles. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    LegalScreen(
        title = stringResource(R.string.about_title),
        subtitle = stringResource(R.string.about_subtitle),
        sections = ABOUT_SECTIONS,
        onBack = onBack,
    )
}

/** The terms of use. */
@Composable
fun TermsScreen(onBack: () -> Unit) {
    LegalScreen(
        title = stringResource(R.string.terms_title),
        subtitle = stringResource(R.string.terms_subtitle),
        sections = TERMS_SECTIONS,
        onBack = onBack,
    )
}

/** The privacy policy. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    LegalScreen(
        title = stringResource(R.string.privacy_title),
        subtitle = stringResource(R.string.privacy_subtitle),
        sections = PRIVACY_SECTIONS,
        onBack = onBack,
    )
}

/** Shared layout for the three long-form screens. */
@Composable
private fun LegalScreen(
    title: String,
    subtitle: String,
    sections: List<LegalSection>,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(sections.size) { index ->
            val section = sections[index]
            ChanakyaCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = section.heading,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.legal_footer),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
