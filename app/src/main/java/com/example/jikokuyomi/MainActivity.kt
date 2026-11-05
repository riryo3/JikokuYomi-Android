package com.example.jikokuyomi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.jikokuyomi.data.ChimePreferences
import com.example.jikokuyomi.model.*
import com.example.jikokuyomi.pack.SavedVoicePack
import com.example.jikokuyomi.pack.VoicePackLibrary
import com.example.jikokuyomi.player.AudioPlayer
import com.example.jikokuyomi.service.ChimeScheduler
import com.example.jikokuyomi.tts.SpeechManager
import com.example.jikokuyomi.util.BatteryOptimizationHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var prefs: ChimePreferences
    private lateinit var speechManager: SpeechManager
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var library: VoicePackLibrary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = ChimePreferences(this)
        speechManager = SpeechManager(this)
        audioPlayer = AudioPlayer(this)
        library = VoicePackLibrary(this)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = MaterialTheme.colorScheme.primary
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    JikokuYomiMainScreen(
                        prefs = prefs,
                        library = library,
                        onPlaySlot = { slot, settings, custom ->
                            val minuteOnly = settings.omitMode.isMinuteOnly(slot.minute, settings.interval)
                            val speakerPackId = custom?.speakerPackId ?: settings.defaultPackId
                            val forceTts = (speakerPackId == "__tts__" || (speakerPackId == null && settings.defaultVoiceSource == VoiceSource.TTS))

                            val audioFile = if (!forceTts && speakerPackId != null) {
                                library.getAudioFileForSlot(speakerPackId, slot, minuteOnly)
                            } else {
                                null
                            }

                            if (audioFile != null && audioFile.exists()) {
                                audioPlayer.playFile(audioFile)
                            } else {
                                val text = if (!custom?.customLine.isNullOrBlank()) {
                                    CustomLines.fill(custom!!.customLine!!, slot.hour, slot.minute)
                                } else {
                                    settings.resolveText(slot)
                                }
                                speechManager.speak(text, settings.language, settings.speechRate)
                            }
                        },
                        onPlayPackSample = { pack ->
                            val sample = library.getAudioFile(pack.id, "t1200")
                                ?: library.getAudioFile(pack.id, "t0700")
                                ?: pack.dir.listFiles()?.firstOrNull()
                            if (sample != null) {
                                audioPlayer.playFile(sample)
                            }
                        },
                        onSettingsChanged = { settings ->
                            prefs.saveSettings(settings)
                            ChimeScheduler.scheduleNext(this)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.shutdown()
        audioPlayer.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JikokuYomiMainScreen(
    prefs: ChimePreferences,
    library: VoicePackLibrary,
    onPlaySlot: (Slot, ChimeSettings, SlotCustomization?) -> Unit,
    onPlayPackSample: (SavedVoicePack) -> Unit,
    onSettingsChanged: (ChimeSettings) -> Unit
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(prefs.loadSettings()) }
    var packs by remember { mutableStateOf(library.getAllPacks()) }
    var editingSlot by remember { mutableStateOf<Slot?>(null) }
    var isBatteryOptIgnored by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val pack = library.importPack(uri)
            if (pack != null) {
                packs = library.getAllPacks()
                val updated = settings.copy(
                    defaultVoiceSource = VoiceSource.VOICE_PACK,
                    defaultPackId = pack.id
                )
                settings = updated
                onSettingsChanged(updated)
                Toast.makeText(context, "「${pack.name}」を取り込みました (${pack.fileCount}ファイル)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "有効な音声ファイル(t0715.wav等)が見つかりませんでした", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val nextChime = remember(settings) {
        ChimeScheduler.getNextChimeTime(settings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("時刻読み (JikokuYomi)", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Main Toggle
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.enabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "時報の自動読み上げ",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (settings.enabled) "有効" else "停止中",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = {
                                val updated = settings.copy(enabled = it)
                                settings = updated
                                onSettingsChanged(updated)
                            }
                        )
                    }

                    if (settings.enabled && nextChime != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "次回予定: ${timeFormat.format(nextChime.time)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Card 2: 誰に（話者・ボイスパック管理）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("誰に喋らせるか（話者ライブラリ）", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                            Icon(Icons.Default.Add, contentDescription = "パック追加")
                        }
                    }
                    Text(
                        "VOICEVOXや録音フォルダを自由に追加して、キャラクターを切り替えられます",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // TTS Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val updated = settings.copy(
                                    defaultVoiceSource = VoiceSource.TTS,
                                    defaultPackId = null
                                )
                                settings = updated
                                onSettingsChanged(updated)
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = settings.defaultVoiceSource == VoiceSource.TTS || settings.defaultPackId == null,
                            onClick = {
                                val updated = settings.copy(
                                    defaultVoiceSource = VoiceSource.TTS,
                                    defaultPackId = null
                                )
                                settings = updated
                                onSettingsChanged(updated)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("標準TTS (Google音声合成)", fontSize = 14.sp)
                    }

                    // Registered Voice Packs
                    packs.forEach { pack ->
                        val isSelected = settings.defaultVoiceSource == VoiceSource.VOICE_PACK && settings.defaultPackId == pack.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = settings.copy(
                                        defaultVoiceSource = VoiceSource.VOICE_PACK,
                                        defaultPackId = pack.id
                                    )
                                    settings = updated
                                    onSettingsChanged(updated)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    val updated = settings.copy(
                                        defaultVoiceSource = VoiceSource.VOICE_PACK,
                                        defaultPackId = pack.id
                                    )
                                    settings = updated
                                    onSettingsChanged(updated)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pack.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("${pack.fileCount} ファイル", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onPlayPackSample(pack) }) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "試聴")
                            }
                            IconButton(onClick = {
                                library.deletePack(pack.id)
                                packs = library.getAllPacks()
                                if (settings.defaultPackId == pack.id) {
                                    val updated = settings.copy(defaultPackId = null, defaultVoiceSource = VoiceSource.TTS)
                                    settings = updated
                                    onSettingsChanged(updated)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("新しいボイスパックを取り込む")
                    }
                }
            }

            // Card 3: 何時を・どう喋らせるか（タイムライン・セリフ・話者個別設定）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("何時を・どう喋らせるか（個別時刻設定）", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("特定の時間だけ別のキャラに喋らせたり、専用のセリフを指定できます", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // List key hours (0..23 by interval)
                    val slots = remember(settings.interval) {
                        val list = mutableListOf<Slot>()
                        for (h in 0..23) {
                            for (m in listOf(0, 15, 30, 45)) {
                                if (m % settings.interval == 0) {
                                    list.add(Slot(h, m))
                                }
                            }
                        }
                        list
                    }

                    // 時ごとに折りたたみ（タップで開閉）
                    var expandedHours by remember { mutableStateOf(setOf<Int>()) }
                    slots.groupBy { it.hour }.forEach { (hour, hourSlots) ->
                    val expanded = hour in expandedHours
                    val offCount = hourSlots.count { s ->
                        val k = String.format(Locale.US, "t%02d%02d", s.hour, s.minute)
                        !(settings.customizations[k]?.enabled ?: settings.isWithinActiveHours(s.totalMinutes))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedHours = if (expanded) expandedHours - hour else expandedHours + hour
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${hour}時台", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (offCount == hourSlots.size) "すべて停止" else "${hourSlots.size - offCount}/${hourSlots.size}件 ON",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    if (expanded) hourSlots.forEach { slot ->
                        val slotKey = String.format(Locale.US, "t%02d%02d", slot.hour, slot.minute)
                        val custom = settings.customizations[slotKey]
                        val isEnabled = custom?.enabled ?: settings.isWithinActiveHours(slot.totalMinutes)

                        val speakerLabel = when (custom?.speakerPackId) {
                            null -> "デフォルト"
                            "__tts__" -> "TTS"
                            else -> packs.find { it.id == custom.speakerPackId }?.name ?: "カスタムパック"
                        }

                        val linePreview = if (!custom?.customLine.isNullOrBlank()) {
                            CustomLines.fill(custom!!.customLine!!, slot.hour, slot.minute)
                        } else {
                            settings.resolveText(slot)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingSlot = slot }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    val currentCustom = custom ?: SlotCustomization(slotKey)
                                    val updatedMap = settings.customizations.toMutableMap().apply {
                                        put(slotKey, currentCustom.copy(enabled = checked))
                                    }
                                    val updated = settings.copy(customizations = updatedMap)
                                    settings = updated
                                    onSettingsChanged(updated)
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(slot.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    SuggestionChip(
                                        onClick = { editingSlot = slot },
                                        label = { Text(speakerLabel, fontSize = 11.sp) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                                Text(
                                    text = "「$linePreview」",
                                    fontSize = 12.sp,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(onClick = { onPlaySlot(slot, settings, custom) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "テスト再生")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                    }

                    run {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("※ 各時刻をタップすると話者とセリフを自由にカスタマイズできます", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Card 4: 基本設定
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("基本設定", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("鳴動間隔", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 60).forEach { intervalMinutes ->
                            val selected = settings.interval == intervalMinutes
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = settings.copy(interval = intervalMinutes)
                                    settings = updated
                                    onSettingsChanged(updated)
                                },
                                label = { Text("${intervalMinutes}分ごと") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("時の省略", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OmitMode.values().forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = settings.omitMode == mode,
                                onClick = {
                                    val updated = settings.copy(omitMode = mode)
                                    settings = updated
                                    onSettingsChanged(updated)
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(mode.getDisplayLabel(settings.interval), fontSize = 14.sp)
                        }
                    }
                }
            }

            // Card 5: 野良アプリ用バッテリー最適化除外
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isBatteryOptIgnored)
                        MaterialTheme.colorScheme.surface
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isBatteryOptIgnored) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isBatteryOptIgnored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("常駐動作・バッテリー最適化", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isBatteryOptIgnored)
                            "バッテリー最適化から除外されています（スリープ時も正確に発話します）"
                        else
                            "スリープ中の定時発火を確実にするため、バッテリー最適化を無効化してください",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isBatteryOptIgnored) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                isBatteryOptIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("バッテリー最適化を無効化する")
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: Customize specific time slot ("誰に何時をどう喋らせるか")
    editingSlot?.let { slot ->
        SlotCustomizationDialog(
            slot = slot,
            settings = settings,
            packs = packs,
            onDismiss = { editingSlot = null },
            onSave = { updatedCustom ->
                val slotKey = String.format(Locale.US, "t%02d%02d", slot.hour, slot.minute)
                val updatedMap = settings.customizations.toMutableMap().apply {
                    put(slotKey, updatedCustom)
                }
                val updated = settings.copy(customizations = updatedMap)
                settings = updated
                onSettingsChanged(updated)
                editingSlot = null
            },
            onTest = { testCustom ->
                onPlaySlot(slot, settings, testCustom)
            }
        )
    }
}

@Composable
fun SlotCustomizationDialog(
    slot: Slot,
    settings: ChimeSettings,
    packs: List<SavedVoicePack>,
    onDismiss: () -> Unit,
    onSave: (SlotCustomization) -> Unit,
    onTest: (SlotCustomization) -> Unit
) {
    val slotKey = String.format(Locale.US, "t%02d%02d", slot.hour, slot.minute)
    val existing = settings.customizations[slotKey]

    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var speakerPackId by remember { mutableStateOf(existing?.speakerPackId) }
    var customLine by remember { mutableStateOf(existing?.customLine ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${slot.label} のカスタマイズ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // 1. 有効/無効
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("この時刻を鳴らす", fontSize = 14.sp)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                HorizontalDivider()

                // 2. 誰に（話者選択）
                Text("話者 (誰が喋るか)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { speakerPackId = null }
                    ) {
                        RadioButton(selected = speakerPackId == null, onClick = { speakerPackId = null })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("デフォルト設定に従う", fontSize = 13.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { speakerPackId = "__tts__" }
                    ) {
                        RadioButton(selected = speakerPackId == "__tts__", onClick = { speakerPackId = "__tts__" })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("標準TTS (Google音声合成)", fontSize = 13.sp)
                    }

                    packs.forEach { pack ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { speakerPackId = pack.id }
                        ) {
                            RadioButton(selected = speakerPackId == pack.id, onClick = { speakerPackId = pack.id })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(pack.name, fontSize = 13.sp)
                        }
                    }
                }

                HorizontalDivider()

                // 3. どう喋らせるか（セリフ入力）
                Text("セリフ (どう喋らせるか)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("空欄の場合は標準の時刻読み上げになります。{時}や{分}を変数として使えます。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = customLine,
                    onValueChange = { customLine = it },
                    placeholder = { Text("例: 朝ごはんだよ！{時}時だよ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )

                val preview = if (customLine.isNotBlank()) {
                    CustomLines.fill(customLine, slot.hour, slot.minute)
                } else {
                    settings.resolveText(slot)
                }
                Text("プレビュー: 「$preview」", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                val currentObj = SlotCustomization(
                    slotKey = slotKey,
                    enabled = enabled,
                    speakerPackId = speakerPackId,
                    customLine = customLine.ifBlank { null }
                )

                OutlinedButton(
                    onClick = { onTest(currentObj) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("この設定でテスト再生")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(currentObj) }) { Text("保存") }
                }
            }
        }
    }
}
