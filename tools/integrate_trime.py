#!/usr/bin/env python3
"""Apply the small host adapter to the pinned Trime v3.3.13 checkout."""
from pathlib import Path

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
patch(build, '        versionCode = 20261101', '        versionCode = 20261112')
patch(build, '        versionName = "3.3.13"', '        versionName = "0.2.0-alpha.2"')
patch(build, '    buildFeatures {', '''    sourceSets.getByName("main") {
        java.srcDir("../../semantic-core/src/main/java")
        java.srcDir("../../android-integration/src/main/java")
    }

    buildFeatures {''')
patch(build, 'resValue("string", "trime_app_name", "@string/app_name_debug")', 'resValue("string", "trime_app_name", "外星人输入法·测试")')
patch(build, '    id("com.osfans.trime.opencc-data")', '    // OpenCC data come from the pinned official APK in development builds.')
patch(build, 'android {', '''if (!file("prebuilt/official-assets.json").exists()) {
    apply(plugin = "com.osfans.trime.opencc-data")
}

android {''')

manifest = 'app/src/main/AndroidManifest.xml'
patch(manifest, '        <!-- Settings activity -->', '''        <provider
            android:name="io.github.alieninput.androidsemantic.AlienImageProvider"
            android:authorities="${applicationId}.alien-images"
            android:exported="false"
            android:grantUriPermissions="true" />

        <!-- Settings activity -->''')
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
