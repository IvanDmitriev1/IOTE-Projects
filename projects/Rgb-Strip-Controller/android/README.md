# Android app architecture

The Android project uses one `:app` module with UI and data layers. `ui/control` contains the Compose screen, UI state, and `ControlViewModel`; `ui/theme` contains the app theme. `data/model` holds the light and rainbow models, while `data` defines `LightService`, endpoint validation, and its HTTP implementation, `NetworkLightService`. `di/IoDispatcher.kt` qualifies the I/O dispatcher used by the network service.

The UI depends on the `LightService` contract and shared models. `RgbStripApplication.ControllerModule` binds `NetworkLightService` to that contract and provides `Dispatchers.IO`. The ViewModel and service are scoped to `ViewModelComponent`. `MainActivity` owns the Android local-network permission flow and hosts the Compose screen.

Run these checks from this directory:

```text
gradlew.bat :app:assembleDebug
gradlew.bat :app:lintDebug
```
