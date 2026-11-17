#!/usr/bin/env python3
"""Apply the small host adapter to the pinned Trime v3.3.13 checkout."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / 'trime'


def patch(path, before, after):
    path = root / path
    text = path.read_text()
    if after in text:
        return
    if text.count(before) != 1:
        raise SystemExit('Upstream changed; inspect adapter before applying: ' + str(path))
    path.write_text(text.replace(before, after, 1))


service = 'app/src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt'
patch(service, '    private lateinit var rime: RimeSession', '''    private var alien: io.github.alieninput.androidsemantic.AlienPanel? = null
    val alienPanel: io.github.alieninput.androidsemantic.AlienPanel get() = checkNotNull(alien)

    // Every text/key operation is redirected to the internal query editor in alien mode.
    override fun getCurrentInputConnection(): android.view.inputmethod.InputConnection? =
        if (alien?.capturesInput() == true) alien?.connection else super.getCurrentInputConnection()

    private fun realEditorConnection() = super.getCurrentInputConnection()

    private fun createAlienPanel() = io.github.alieninput.androidsemantic.AlienPanel(
        object : io.github.alieninput.androidsemantic.AlienPanel.Host {
            override fun context() = this@TrimeInputMethodService
            override fun editor() = realEditorConnection()
            override fun editorInfo() = currentInputEditorInfo
            override fun isComposing() = rime.run { statusCached.isComposing }
            override fun imageUri(file: java.io.File) = android.net.Uri.Builder()
                .scheme("content").authority("$packageName.alien-images").appendPath(file.name).build()
            override fun resetComposition(completed: Runnable) {
                lifecycleScope.launch {
                    postRimeJob { clearComposition() }.join()
                    composingText = ""
                    completed.run()
                }
            }
        },
    )

    private lateinit var rime: RimeSession''')
patch(service, '        super.onCreate()\n        Timber.d("onCreate")', '        super.onCreate()\n        alien = createAlienPanel()\n        Timber.d("onCreate")')
patch(service, '        cursorUpdateIndex += 1', '        if (alien?.capturesInput() == true) return\n        cursorUpdateIndex += 1')
patch(service, '        composingText = ""\n        Timber.d("onStartInput: restarting=$restarting")', '        alien?.start(attribute, restarting)\n        composingText = ""\n        Timber.d("onStartInput: restarting=$restarting")')
patch(service, '    override fun onFinishInputView(finishingInput: Boolean) {', '''    override fun onFinishInput() {
        alien?.finish()
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {''')

patch(service, 'currentInputConnection.performEditorAction(action)', 'currentInputConnection?.performEditorAction(action)')
patch(service, 'val et = currentInputConnection.getExtractedText(etr, 0)', 'val et = currentInputConnection?.getExtractedText(etr, 0)')
key_view = 'app/src/main/java/com/osfans/trime/ime/keyboard/KeyView.kt'
patch(key_view, 'ic.getTextBeforeCursor(1, 0)', 'ic?.getTextBeforeCursor(1, 0)')
patch(key_view, 'ic.deleteSurroundingText(1, 0)', 'ic?.deleteSurroundingText(1, 0)')
patch(key_view, 'ic.commitText(deletedTextBuffer.removeFirst(), 1)', 'ic?.commitText(deletedTextBuffer.removeFirst(), 1)')
test_panel = 'app/src/main/java/com/osfans/trime/ui/main/TestInputPanel.kt'
patch(test_panel, 'import splitties.views.dsl.core.editText', 'import splitties.views.dsl.core.view')
patch(test_panel, 'private val input: EditText = editText {', 'private val input: EditText = view({ context -> io.github.alieninput.androidsemantic.AlienPreviewEditor(context) }) {')
user_data = root / 'app/src/main/java/com/osfans/trime/core/data/UserDataManager.kt'
user_data.write_text(user_data.read_text().replace('          - schema: alien_t9', '          - schema: clover_jiugong'))
patch('app/src/main/java/com/osfans/trime/core/data/UserDataManager.kt', '''          - schema: luna_pinyin
          - schema: luna_pinyin_simp''', '''          - schema: clover_jiugong
          - schema: luna_pinyin_simp
          - schema: double_pinyin_flypy''')

view = 'app/src/main/java/com/osfans/trime/ime/core/InputView.kt'
patch(view, '    val keyboardView: View', '''    private val alienPanelView = service.alienPanel.createView().apply { id = View.generateViewId() }

    val keyboardView: View''')
patch(view, '''                add(
                    inputBar.view,
                    lParams(matchParent, dp(inputBar.themedHeight)) {
                        topOfParent()
                        centerHorizontally()
                    },
                )''', '''                add(
                    alienPanelView,
                    lParams(matchParent, wrapContent) {
                        topOfParent()
                        centerHorizontally()
                    },
                )
                add(
                    inputBar.view,
                    lParams(matchParent, dp(inputBar.themedHeight)) {
                        below(alienPanelView)
                        centerHorizontally()
                    },
                )''')

always = 'app/src/main/java/com/osfans/trime/ime/bar/ui/AlwaysUi.kt'
patch(always, '    val buttonsUi = ButtonsBarUi(ctx, scope, onButtonClick)', '''    private val alienButton = ToolButton(
        ctx,
        ToolBar.Button(foreground = ToolBar.Button.Foreground(style = "外星", fontSize = 12f)),
        scope,
    ).apply {
        contentDescription = "展开或收起外星输入"
        setOnClickListener { onButtonClick?.invoke("__alien_toggle") }
    }

    val buttonsUi = ButtonsBarUi(ctx, scope, onButtonClick)''')
patch(always, '''        add(
            animator,
            lParams(matchConstraints, matchParent) {
                after(leftMostButton)''', '''        add(
            alienButton,
            lParams(leftWidth, leftHeight) {
                after(leftMostButton)
                centerVertically()
            },
        )
        add(
            animator,
            lParams(matchConstraints, matchParent) {
                after(alienButton)''')
patch(always, '''    fun refreshColors() {
        leftMostButton.refreshColors()''', '''    fun refreshColors() {
        alienButton.refreshColors()
        leftMostButton.refreshColors()''')
bar = 'app/src/main/java/com/osfans/trime/ime/bar/InputBarDelegate.kt'
patch(bar, '''            if (action.isNotEmpty()) {
                commonKeyboardActionListener''', '''            if (action == "__alien_toggle") {
                service.alienPanel.toggleVisibility()
                return@AlwaysUi
            }
            if (action.isNotEmpty()) {
                commonKeyboardActionListener''')

build = 'app/build.gradle.kts'
patch(build, '        applicationId = "com.osfans.trime"', '        applicationId = "io.github.alieninput.trime"')
patch(build, '        minSdk = 21', '        minSdk = 26')
build_file = root / build
build_text = build_file.read_text()
version = (root.parent / 'VERSION').read_text().strip()
build_text, changed_code = re.subn(r'(?m)^        versionCode = [0-9]+$', '        versionCode = 2026111701', build_text)
build_text, changed_name = re.subn(r'(?m)^        versionName = "[^"]+"$', f'        versionName = "{version}"', build_text)
if changed_code != 1 or changed_name != 1:
    raise SystemExit('Inspect upstream version fields before building')
build_file.write_text(build_text)
patch(build, '    buildFeatures {', '''    sourceSets.getByName("main") {
        java.srcDir("../../semantic-core/src/main/java")
        java.srcDir("../../android-integration/src/main/java")
    }

    buildFeatures {''')
build_file.write_text(build_file.read_text().replace('外星人输入法·测试', '外星人输入法'))
patch(build, 'resValue("string", "trime_app_name", "@string/app_name_debug")', 'resValue("string", "trime_app_name", "外星人输入法")')
patch(build, '    id("com.osfans.trime.opencc-data")', '    // OpenCC data come from the pinned official APK in development builds.')
patch(build, 'android {', '''if (!file("prebuilt/official-assets.json").exists()) {
    apply(plugin = "com.osfans.trime.opencc-data")
}

android {''')

# Rebuild only our manifest entries so repeated integration stays idempotent.
manifest_file = root / 'app/src/main/AndroidManifest.xml'
manifest_text = manifest_file.read_text()
manifest_text = re.sub(r'(?m)^[ \t]*<(?:provider|activity)\s+android:name="io\.github\.alieninput\.androidsemantic\.(?:AlienImageProvider|ReadImageActivity|HomeActivity|WriteMessageActivity)"[\s\S]*?/>[ \t]*\n?', '', manifest_text)
components = '        <provider android:name="io.github.alieninput.androidsemantic.AlienImageProvider" android:authorities="${applicationId}.alien-images" android:exported="false" android:grantUriPermissions="true" />\n\n'
for activity in ['ReadImageActivity']:
    components += f'        <activity android:name="io.github.alieninput.androidsemantic.{activity}" android:exported="false" android:windowSoftInputMode="adjustResize" android:theme="@android:style/Theme.Material.Light.NoActionBar" />\n\n'
manifest_text = re.sub(r'(?m)^[ \t]*<!-- Settings activity -->', lambda m: components + '        <!-- Settings activity -->', manifest_text)
manifest_text = manifest_text.replace('android:targetActivity="io.github.alieninput.androidsemantic.HomeActivity"', 'android:targetActivity=".ui.main.MainActivity"')
manifest_file.write_text(re.sub(r'\n(?:[ \t]*\n){2,}', '\n\n', manifest_text))
patch('app/src/main/java/com/osfans/trime/ui/setup/SetupActivity.kt',
      '        startActivity<io.github.alieninput.androidsemantic.HomeActivity>()', '        startActivity<MainActivity>()')
patch('app/src/main/java/com/osfans/trime/ui/main/MainActivity.kt',
      '        val optionMenuItems = listOf(',
      '''        val readImageItem = menu.add("读图").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                startActivity(Intent(this@MainActivity, io.github.alieninput.androidsemantic.ReadImageActivity::class.java))
                true
            }
        }
        val optionMenuItems = listOf(
            readImageItem,''')
patch('app/src/main/java/com/osfans/trime/ui/main/MainActivity.kt',
      'import android.view.Menu\n', 'import android.view.Menu\nimport android.view.MenuItem\n')
patch('app/src/main/java/com/osfans/trime/ui/main/MainActivity.kt',
      'navController.navigateUp() || onSupportNavigateUp() || moveTaskToBack(false)',
      'navController.navigateUp() || onSupportNavigateUp() || run { finish(); true }')
patch('app/src/main/java/com/osfans/trime/ui/main/NavigationRoute.kt',
      'label = ctx.getString(R.string.trime_app_name)', 'label = ctx.getString(R.string.alien_settings)')
(root / 'app/src/main/res/drawable/ic_app_icon_foreground.xml').write_bytes(
    (root.parent / 'android-integration/src/main/res/drawable/alien_launcher.xml').read_bytes())
icon_background = root / 'app/src/main/res/values/ic_app_icon_background.xml'
icon_background.write_text(re.sub(r'(<color name="ic_app_icon_background">)[^<]+', r'\g<1>#292A25', icon_background.read_text()))
# Product labels: keep diagnostics in the developer area, use ordinary language in the main flow.
for relative, title, slogan, try_label in [
    ('values-zh-rCN', '外星人输入法', '写下你的外星来信', '试写'),
    ('values-zh-rTW', '外星人輸入法', '寫下你的外星來信', '試寫'),
    ('values', 'Alien Input', 'Write your own message from aliens', 'Try keyboard'),
]:
    strings = root / f'app/src/main/res/{relative}/strings.xml'
    text = strings.read_text()
    for name, value in [('app_name_release', title), ('app_name_debug', title), ('trime_app_slogan', slogan), ('test_input', try_label)]:
        text = re.sub(r'(<string name="' + name + r'">)[^<]*(</string>)', lambda m: m[1]+value+m[2], text)
    text = re.sub(r'(<string name="setup__[^"]+">)(.*?)(</string>)',
                  lambda m: m[1]+m[2].replace('同文输入法', '外星人输入法').replace('同文輸入法', '外星人輸入法').replace('<b>Trime</b>', '<b>Alien Input</b>')+m[3], text)
    settings_label = '设置' if relative == 'values-zh-rCN' else '設定' if relative == 'values-zh-rTW' else 'Settings'
    if 'name="alien_settings"' not in text:
        text = text.replace('</resources>', f'    <string name="alien_settings">{settings_label}</string>\n</resources>')
    strings.write_text(text)

# Keep image styling in Settings > Keyboard; no new toolbar/home controls.
patch('app/src/main/java/com/osfans/trime/ui/main/settings/KeyboardSettingsFragment.kt',
      '        screen.findPreference<Preference>("custom_sound_effect_name")?.apply {',
      '''        val imageColors = androidx.preference.ListPreference(requireContext()).apply {
            key = "alien_image_palette"
            title = "外星图片配色"
            dialogTitle = "外星图片配色"
            entries = io.github.alieninput.androidsemantic.AlienPalette.LABELS
            entryValues = io.github.alieninput.androidsemantic.AlienPalette.IDS
            isPersistent = false
            val prefs = requireContext().getSharedPreferences(
                io.github.alieninput.androidsemantic.AlienPalette.PREFERENCES, 0,
            )
            value = io.github.alieninput.androidsemantic.AlienPalette.load(requireContext()).id
            summaryProvider = androidx.preference.ListPreference.SimpleSummaryProvider.getInstance()
            setOnPreferenceChangeListener { _, selected ->
                prefs.edit().putString(io.github.alieninput.androidsemantic.AlienPalette.KEY, selected as String).apply()
                true
            }
        }
        screen.addPreference(imageColors)
        screen.findPreference<Preference>("custom_sound_effect_name")?.apply {''')

print('Trime adapter applied')
