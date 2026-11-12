package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContain

/** Tests the packaged upstream layouts through the production theme decoder. */
class AlienKeyboardIntegrationTest : FunSpec({
    for (file in listOf("trime.yaml", "tongwenfeng.trime.yaml", "tongwenfeng_plus.trime.yaml")) {
        test("$file keeps full nine-key navigation and correct digit events") {
            val theme = ThemeTestSupport.decodeBuiltinTheme(file)
            theme.toolBar.primaryButton?.background?.normal shouldBe "0x00000000"
            val keyboard = theme.presetKeyboards.getValue("clover_jiugong")
            keyboard.keys.size shouldBe 20
            keyboard.asciiMode shouldBe 0
            keyboard.asciiKeyboard shouldBe "default_jiugong_letter"
            val actions = keyboard.keys.map { (it.click as? KeyActionToken.Plain)?.token }
            for (action in listOf("liquid_keyboard_switch", "K_jiugongNumber", "K_jiugongLetter", "K_jiugong_use", "BackSpace", "Return", ",", ".")) actions shouldContain action
            for (digit in 2..9) {
                val id = "Clover_digit_$digit"
                actions shouldContain id
                theme.presetKeys.getValue(id).send shouldBe digit.toString()
            }
            for (action in listOf("K_jiugongNumber", "K_jiugongLetter", "K_jiugong_use")) {
                theme.presetKeyboards.containsKey(theme.presetKeys.getValue(action).select) shouldBe true
            }
            theme.presetKeys.getValue("liquid_keyboard_switch").command shouldBe "liquid_keyboard"
            theme.presetKeys.getValue("liquid_keyboard_switch").send shouldBe "FUNCTION"
            theme.presetKeys.getValue("K_jiugongDefault").select shouldBe ".default"
            theme.presetKeys.getValue("Mode_switch").send shouldBe "SWITCH_CHARSET"
            theme.presetKeys.getValue("Clover_switch_keyboard").select shouldBe ".ascii"
            theme.presetKeyboards.getValue("default_jiugong_letter").asciiKeyboard shouldBe "luna_jiugong"
            theme.presetKeyboards.getValue("letter").asciiKeyboard shouldBe "default"
            for ((_, action) in theme.presetKeys) {
                if (action.send == "Eisu_toggle" && action.select.isNotEmpty() && !action.select.startsWith(".")) {
                    theme.presetKeyboards.containsKey(action.select) shouldBe true
                }
            }
            theme.liquidKeyboard.keyboards.map { it.id } shouldContain "cn"
            theme.liquidKeyboard.keyboards.map { it.id } shouldContain "emoji"
            theme.presetKeyboards.getValue("default_jiugong_letter").keys.any {
                it.click == KeyActionToken.Plain("Shift_L")
            } shouldBe true
            theme.presetKeyboards.getValue("alien_t9").keys shouldBe keyboard.keys
        }
    }
})
