# Найденные дефекты — 22 октября 2026

Все шесть воспроизведений находятся в `app/src/test/java/com/example/scratched/regressions/KnownDefectsRegressionTest.kt`. Тесты проверяют правильное ожидаемое поведение, остаются активными и сейчас падают. Это не шесть отключённых проверок и не подтверждение работоспособности перечисленных сценариев.

База: QA-ветка после merge `137da9c1279cb2e02e5c653fba495c308acefe48`, код разработчика из `origin/master` на `9e94f6cb2eb6b28b2217ce55522dd2fda54d2e03`. Строки ниже относятся к рабочей версии с добавленным источником времени.

## B01. Выключенный Bluetooth отображается как включённый

- Тест: `B01_disabledBluetooth_mustNotBeDisplayedAsEnabled`.
- Шаги: адаптер возвращает DISABLED → создать ViewModel → вызвать `refreshState()`.
- Ожидается: `uiState.isBluetoothEnabled == false`.
- Фактически: `true`.
- Причина: `MainViewModel.kt:181`, `updateBaseState()` присваивает константу `true`, а не состояние адаптера. Проверка запрета сканирования по этому флагу тоже не отражает реальность.

## B02. Повторный запуск пропускает отключённую геолокацию

- Тест: `B02_returningUserWithDisabledLocation_mustSeeEnableLocation`.
- Шаги: onboarding ранее завершён, все разрешения выданы, Bluetooth включён, геолокация выключена → `startAppFlow()`.
- Ожидается: `ENABLE_LOCATION`, как предусмотрено обычной проверкой недостающих условий.
- Фактически: `COMPLETED`.
- Причина: `onboarding/OnboardingFlowCoordinator.kt:75`, быстрый путь повторного запуска проверяет только разрешения и Bluetooth; до проверки геолокации выполнение не доходит.

## B03. Stop не отменяет отложенный запуск сканера

- Тест: `B03_stop_mustCancelPendingCooldownStart`.
- Шаги: Start → Stop → немедленно Start (попадает в пятисекундную задержку) → Stop → продвинуть время на 5000 мс.
- Ожидается: радио больше не запускается; `startScan` вызван один раз, в начале сценария.
- Фактически: `startScan` вызван дважды, второй раз после отмены пользователем.
- Причина: `meshMVP/transport/BluetoothGattClientManager.kt:185`, отложенная корутина не сохраняется/не отменяется; `stopScanning()` на строке 210 её не останавливает.

## B04. Ошибка радио не доходит до экранного состояния

- Тест: `B04_radioFailures_mustReachScreenInsteadOfRemainingScanning`.
- Шаги: запустить сканирование через реальную связку GATT manager + ViewModel → передать в callback `onScanFailed`.
- Коды: ALREADY_STARTED, APPLICATION_REGISTRATION_FAILED, INTERNAL_ERROR, FEATURE_UNSUPPORTED, OUT_OF_HARDWARE_RESOURCES и неизвестный код 999. Тест собирает результат для каждого кода до итоговой проверки.
- Ожидается: для каждого отказа `uiState.scanStatus == ERROR`.
- Фактически: для всех кодов остаётся `SCANNING`.
- Причина: `meshMVP/transport/BluetoothGattClientManager.kt:82`, ошибки только записываются в журнал; механизма передачи ошибки/состояния в ViewModel нет. В ветке ALREADY_STARTED радио останавливается, но экран продолжает показывать поиск.

## B05. Отсутствующий сканер выглядит как успешный запуск

- Тест: `B05_unavailableScanner_mustNotReportSuccessfulScan`.
- Шаги: доступен BluetoothAdapter, но его `bluetoothLeScanner == null` → нажать Start через ViewModel.
- Ожидается: состояние ошибки, а не продолжающийся поиск.
- Фактически: `SCANNING`, хотя радио API не был вызван.
- Причина: `meshMVP/transport/BluetoothGattClientManager.kt:201`, безопасный вызов `bleScanner?.startScan(...)` пропускает запуск, но менеджер уже выставил `isScanning = true`, а ViewModel — `SCANNING`; отрицательный результат не возвращается.

## B06. Неконнектируемое устройство помечается как доступное для подключения

- Тест: `B06_nonConnectableAdvertisement_mustNotBeMarkedConnectable`.
- Шаги: передать рекламный ScanResult с `isConnectable == false` → прочитать устройство из `discoveredDevices`.
- Ожидается: в UI-модели `isConnectable == false`.
- Фактически: `true`.
- Причина: `meshMVP/transport/BluetoothGattClientManager.kt:155`, при разборе используется временная константа вместо признака из ScanResult.

## Повторная проверка

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.example.scratched.regressions.KnownDefectsRegressionTest' --console=plain
```

После исправления каждый соответствующий тест должен стать зелёным без ослабления ожидаемого результата. Затем нужен полный запуск без фильтра. Не отключать класс и не добавлять `ignoreFailures`, чтобы скрыть эти отказы.
