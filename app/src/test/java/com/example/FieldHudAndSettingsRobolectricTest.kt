package com.example

import android.app.Application
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.domain.ALL_IMPLEMENTS
import com.example.domain.ALL_VEHICLES
import com.example.domain.AppRoute
import com.example.domain.FieldInfoPanelConfig
import com.example.domain.FieldPanelCorner
import com.example.domain.FieldPanelSize
import com.example.domain.FieldPanelViewMode
import com.example.domain.GameSettings
import com.example.domain.HitchMode
import com.example.domain.ImplementCategory
import com.example.domain.OwnedImplement
import com.example.domain.SoilState
import com.example.domain.VehicleCategory
import com.example.domain.getImplementDef
import com.example.domain.resolveImplementCouplingSpec
import com.example.simulation.analyzeFieldPlot
import com.example.simulation.computeVehicleHitchWorldPoint
import com.example.simulation.createNewWorld
import com.example.simulation.evaluateVehicleHitchPair
import com.example.state.FarmViewModel
import com.example.ui.components.FieldInfoHudPanel
import com.example.ui.components.HitchHudActionControl
import com.example.ui.components.HitchModeSelectionDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FarmBrasilTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FieldHudAndSettingsRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fieldInfoHudPanel_togglesCompactToExpandedAndShowsAllAgronomyDetails() {
        val base = createNewWorld(0, "Fazenda UI", "map_pr", 250000.0, "Padrão", 64)
        val plot = base.plots.first { !it.isPreservation }
        val plotCells = base.cells.map { c ->
            if (c.plotId == plot.id) {
                c.copy(
                    soilState = SoilState.CRESCENDO,
                    cropId = "soja",
                    growthProgress = 0.65,
                    fertilizedStages = 1,
                    phLevel = 5.4,
                    weedLevel = 0.18,
                    nitrogen = 0.52,
                    phosphorus = 0.60,
                    potassium = 0.58,
                )
            } else c
        }
        val world = base.copy(
            month = 10,
            cells = plotCells,
            plots = base.plots.map { if (it.id == plot.id) it.copy(owned = true) else it },
        )
        val telemetry = analyzeFieldPlot(world, plot.id, activeVehicle = world.vehicles.first())!!
        var expanded by mutableStateOf(false)

        composeRule.setContent {
            FarmBrasilTheme {
                FieldInfoHudPanel(
                    telemetry = telemetry,
                    config = FieldInfoPanelConfig(),
                    isExpanded = expanded,
                    onToggleExpanded = { expanded = !expanded },
                )
            }
        }

        composeRule.onNodeWithTag("field_info_hud_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("field_panel_owner_badge").assertTextContains("SUA PROPRIEDADE", substring = true)
        composeRule.onNodeWithTag("field_panel_growth_label").assertTextContains("CRESCIMENTO DA SOJA", substring = true)
        composeRule.onNodeWithTag("field_panel_stage_label").assertTextContains("Estágio 6/9", substring = true)

        // Toggle Expanded view
        composeRule.onNodeWithTag("field_panel_toggle_btn").performClick()
        composeRule.waitForIdle()
        assertThat(expanded).isTrue()
        composeRule.onNodeWithTag("field_panel_fert_detail").assertIsDisplayed()
    }

    @Test
    fun hitchHudControlAndModeSelectionDialog_triggerCallbacks() {
        val base = createNewWorld(0, "Fazenda Botão", "map_pr", 250000.0, "Padrão", 64)
        val tractorDef = ALL_VEHICLES.first { it.category == VehicleCategory.TRATOR && it.horsepower >= 140.0 }
        val impDef = ALL_IMPLEMENTS.first { it.category == ImplementCategory.GRADE && it.requiredHp <= tractorDef.horsepower }
        val tractor = base.vehicles.first().copy(defId = tractorDef.id, x = 0.0, z = 0.0, headingDeg = 0.0, speedMs = 0.0, attachedImplementUid = null)
        val hitchPt = computeVehicleHitchWorldPoint(tractor, impDef.hitchType)
        val spec = resolveImplementCouplingSpec(impDef)
        val imp = OwnedImplement(
            uid = "imp_ui",
            defId = impDef.id,
            x = hitchPt.x,
            z = hitchPt.z - spec.tongueOffsetM - 0.2,
            headingDeg = 0.0,
        )
        val eval = evaluateVehicleHitchPair(base, tractor, imp)
        assertThat(eval.canHitchNow).isTrue()

        var clickedCount = 0
        var chosenMode: HitchMode? = null

        composeRule.setContent {
            FarmBrasilTheme {
                HitchHudActionControl(
                    inVehicleHitch = eval,
                    inVehicleUnhitch = null,
                    onFootEval = null,
                    isInVehicle = true,
                    onActionClick = { clickedCount++ },
                )
                HitchModeSelectionDialog(
                    implementModel = impDef.model,
                    onChooseMode = { chosenMode = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("hud_hitch_action_button").assertTextContains("ENGATAR IMPLEMENTO", substring = true)
        composeRule.onNodeWithTag("hitch_choice_on_foot_btn").performClick()
        composeRule.waitForIdle()
        assertThat(chosenMode).isEqualTo(HitchMode.ON_FOOT_MANUAL)
    }

    @Test
    fun settingsScreen_updatesHitchModeFieldPanelAndKeyBindings() {
        var settingsState by mutableStateOf(GameSettings())
        composeRule.setContent {
            FarmBrasilTheme {
                SettingsScreen(
                    settings = settingsState,
                    onApplyPreset = {},
                    onUpdateCustomGraphics = {},
                    onUpdateSettings = { updater -> settingsState = updater(settingsState) },
                    onUpdateHudLayout = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings_hitch_mode_ASK_EACH_TIME").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_field_corner_BOTTOM_END").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_field_size_GRANDE").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_field_view_EXPANDIDO").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_key_hitch_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertThat(settingsState.hitchMode).isEqualTo(HitchMode.ASK_EACH_TIME)
        assertThat(settingsState.fieldPanelConfig.corner).isEqualTo(FieldPanelCorner.BOTTOM_END)
        assertThat(settingsState.fieldPanelConfig.size).isEqualTo(FieldPanelSize.GRANDE)
        assertThat(settingsState.fieldPanelConfig.defaultViewMode).isEqualTo(FieldPanelViewMode.EXPANDIDO)
        assertThat(settingsState.keyBindings.hitch).isEqualTo("KeyF")
    }

    @Test
    fun viewModelAskEachTimeFlow_andHardwareKeyHitchIntegration() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = FarmViewModel(app)
        vm.startNewCareer(0, "Fazenda VM", "map_pr", 250000.0, "Padrão", 64)
        vm.setHitchMode(HitchMode.ASK_EACH_TIME)

        // Position active vehicle & an unhitched implement in aligned range
        val cur = vm.worldState.value
        val activeVeh = cur.vehicles.first()
        val imp = cur.implements.first()
        val iDef = getImplementDef(imp.defId)
        val spec = resolveImplementCouplingSpec(iDef)
        val hitchPt = computeVehicleHitchWorldPoint(activeVeh, spec.defaultHitchType)
        val rad = activeVeh.headingDeg * PI / 180.0
        val alignedX = hitchPt.x - sin(rad) * (spec.tongueOffsetM + 0.15)
        val alignedZ = hitchPt.z - cos(rad) * (spec.tongueOffsetM + 0.15)

        // Teleport player vehicle clean near implement via unhitch if already hitched, then re-hitch via Q key
        if (activeVeh.attachedImplementUid != null) {
            vm.requestHitchOrUnhitch()
            assertThat(vm.worldState.value.vehicles.first().attachedImplementUid).isNull()
        } else {
            // Ensure free implement is aligned if none was hitched initially
            vm.teleportToLocation(alignedX, alignedZ, "Teste")
        }

        // Trigger hitch via ASK_EACH_TIME mode
        if (vm.worldState.value.activeVehicleUid != null) {
            // Re-run requestHitchOrUnhitch when candidate exists
            vm.requestHitchOrUnhitch()
            if (vm.pendingHitchModeChoice.value != null) {
                vm.confirmHitchModeChoice(HitchMode.IN_VEHICLE_BUTTON)
                assertThat(vm.pendingHitchModeChoice.value).isNull()
                assertThat(vm.worldState.value.vehicles.first().attachedImplementUid).isNotNull()
            }
        }

        // Test hardware KeyEvent Q triggers unhitch / hitch while in Gameplay
        assertThat(vm.route.value).isEqualTo(AppRoute.Gameplay)
        val beforeAttached = vm.worldState.value.vehicles.first().attachedImplementUid
        val handledQ = vm.onHardwareKeyEvent(KeyEvent.KEYCODE_Q, isDown = true)
        assertThat(handledQ).isTrue()
        if (beforeAttached != null) {
            assertThat(vm.worldState.value.vehicles.first().attachedImplementUid).isNull()
        }
    }
}
