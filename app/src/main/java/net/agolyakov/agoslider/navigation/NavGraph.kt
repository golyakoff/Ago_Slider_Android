package net.agolyakov.agoslider.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.delay
import net.agolyakov.agoslider.data.model.ble.AgoSliderDevice
import net.agolyakov.agoslider.ui.screen.device.DeviceScreen
import net.agolyakov.agoslider.ui.screen.device.DeviceViewModel
import net.agolyakov.agoslider.ui.screen.firmware.FirmwareScreen
import net.agolyakov.agoslider.ui.screen.home.HomeScreen
import net.agolyakov.agoslider.ui.screen.home.HomeViewModel

@Composable
fun SetupNavGraph(
    navController: NavHostController
) {
    NavHost(navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(route = Screen.Home.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                delay(50)
                homeViewModel.bluetoothService.setShouldMaintainConnection(false)
                delay(50)
                homeViewModel.startScan()
            }
            HomeScreen(navController, homeViewModel)
        }

        composable(route = Screen.Device.route) {
            val device: AgoSliderDevice? =
                navController.previousBackStackEntry?.savedStateHandle?.get<AgoSliderDevice>("device")

            val deviceViewModel: DeviceViewModel = hiltViewModel()

            DeviceScreen(navController, device)
        }

        composable(route = Screen.FirmwareUpdate.route) {
            FirmwareScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
