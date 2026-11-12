package com.brycewg.asrkb.ui.settings

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.brycewg.asrkb.R
import com.brycewg.asrkb.ime.shortcutModifiers
import com.brycewg.asrkb.store.Prefs
import com.brycewg.asrkb.ui.BaseActivity
import com.brycewg.asrkb.ui.settings.compose.components.SettingsActionButton
import com.brycewg.asrkb.ui.settings.compose.components.SettingsActionButtonRow
import com.brycewg.asrkb.ui.settings.compose.components.SettingsDetailScaffold
import com.brycewg.asrkb.ui.settings.compose.components.SettingsLazyColumn
import com.brycewg.asrkb.ui.settings.compose.core.BibiSettingsTheme
import com.brycewg.asrkb.ui.settings.compose.core.BibiUiMode
import com.brycewg.asrkb.ui.settings.compose.core.SettingsLayoutMetrics

/** A foreground-only key inspector; no text field or accessibility permission is required. */
class HardwareKeyboardSettingsActivity : BaseActivity() {
    private lateinit var prefs: Prefs
    private var capturing by mutableStateOf(true)
    private var candidate by mutableStateOf<CapturedHardwareKey?>(null)
    private var events by mutableStateOf<List<CapturedHardwareKey>>(emptyList())
    private var binding by mutableStateOf("")
    private var devices by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        refreshBinding()
        setContent {
            val uiMode = BibiUiMode.fromId(prefs.settingsUiMode)
            BibiSettingsTheme(uiMode = uiMode, themeMode = prefs.settingsThemeMode) {
                KeyboardSettingsContent(uiMode)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDevices()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val device = event.device
        if (capturing && device?.isExternal == true && device.supportsSource(InputDevice.SOURCE_KEYBOARD)) {
            val key = CapturedHardwareKey(
                keyCode = event.keyCode,
                scanCode = event.scanCode,
                modifiers = shortcutModifiers(event),
                action = event.action,
                repeatCount = event.repeatCount,
                deviceName = device.name
            )
            events = (listOf(key) + events).take(30)
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !event.isCanceled) {
                candidate = key
            }
            refreshDevices()
            // While inspecting, prevent detected keys from navigating or typing into this page.
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun refreshDevices() {
        devices = InputDevice.getDeviceIds().map(InputDevice::getDevice).filterNotNull()
            .filter { it.isExternal && it.supportsSource(InputDevice.SOURCE_KEYBOARD) }
            .joinToString("\n") { it.name }
    }

    private fun refreshBinding() {
        binding = if (prefs.hardwareVoiceKeyCode == 0 && prefs.hardwareVoiceScanCode == 0) {
            getString(R.string.hardware_voice_shortcut_disabled)
        } else {
            hardwareKeyDescription(prefs.hardwareVoiceKeyCode, prefs.hardwareVoiceScanCode, prefs.hardwareVoiceMetaState)
        }
    }

    @Composable
    private fun KeyboardSettingsContent(uiMode: BibiUiMode) {
        SettingsDetailScaffold(
            uiMode = uiMode,
            titleRes = R.string.title_hardware_keyboard_settings,
            onBack = ::finish
        ) { innerPadding, scrollModifier ->
            SettingsLazyColumn(
                uiMode = uiMode,
                modifier = Modifier.fillMaxSize(),
                miuixScrollModifier = scrollModifier,
                contentPadding = SettingsLayoutMetrics.pageContentPadding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item("instructions") {
                    Text(stringResource(R.string.hardware_keyboard_capture_instructions))
                }
                item("devices") {
                    Text(stringResource(R.string.hardware_keyboard_devices, devices.ifEmpty { getString(R.string.hardware_keyboard_no_device) }))
                }
                item("binding") {
                    Text(stringResource(R.string.hardware_keyboard_current_binding, binding))
                }
                item("capture_state") {
                    SettingsActionButtonRow(uiMode = uiMode, padded = false) {
                        SettingsActionButton(
                            uiMode = uiMode,
                            text = stringResource(if (capturing) R.string.hardware_keyboard_pause_capture else R.string.hardware_keyboard_start_capture),
                            onClick = { capturing = !capturing }
                        )
                        SettingsActionButton(
                            uiMode = uiMode,
                            text = stringResource(R.string.hardware_keyboard_clear_binding),
                            onClick = {
                                prefs.setHardwareVoiceShortcut(0, 0, 0)
                                refreshBinding()
                            }
                        )
                    }
                }
                item("candidate") {
                    val selected = candidate
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            selected?.let { stringResource(R.string.hardware_keyboard_detected_key, it.description) }
                                ?: stringResource(R.string.hardware_keyboard_waiting_for_key)
                        )
                        SettingsActionButton(
                            uiMode = uiMode,
                            text = stringResource(R.string.hardware_keyboard_bind_key),
                            enabled = selected != null && (selected.keyCode != 0 || selected.scanCode > 0),
                            onClick = {
                                selected?.let {
                                    prefs.setHardwareVoiceShortcut(it.keyCode, it.scanCode, it.modifiers)
                                    refreshBinding()
                                    capturing = false
                                }
                            }
                        )
                    }
                }
                item("limitations") {
                    Text(stringResource(R.string.hardware_keyboard_system_key_note), style = MaterialTheme.typography.bodySmall)
                }
                item("log_title") {
                    Text(stringResource(R.string.hardware_keyboard_event_log), style = MaterialTheme.typography.titleMedium)
                }
                items(events) { key ->
                    SelectionContainer {
                        Text(
                            text = "${key.actionName}  repeat=${key.repeatCount}\n${key.description}\n${key.deviceName}",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

private data class CapturedHardwareKey(
    val keyCode: Int,
    val scanCode: Int,
    val modifiers: Int,
    val action: Int,
    val repeatCount: Int,
    val deviceName: String
) {
    val description: String
        get() = hardwareKeyDescription(keyCode, scanCode, modifiers)

    val actionName: String
        get() = when (action) {
            KeyEvent.ACTION_DOWN -> "ACTION_DOWN"
            KeyEvent.ACTION_UP -> "ACTION_UP"
            else -> "ACTION_MULTIPLE"
        }
}

private fun hardwareKeyDescription(keyCode: Int, scanCode: Int, modifiers: Int): String = "${KeyEvent.keyCodeToString(keyCode)} ($keyCode)  scanCode=$scanCode  modifiers=0x${modifiers.toString(16)}"
