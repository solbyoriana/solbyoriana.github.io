# Re-applies the studio TV setup (run it if a Google TV update brings back apps or Google's screensaver).
#   powershell -ExecutionPolicy Bypass -File tv\fix-tv.ps1 -Tv 192.168.1.136:39111 [-Apk path\to\sol-screensaver.apk]
# -Tv is the "IP address & Port" shown on the TV under Settings > System > Developer options > Wireless debugging.
param(
    [Parameter(Mandatory = $true)][string]$Tv,
    [string]$Apk
)

$adb = (Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools*" -Recurse -Filter adb.exe | Select-Object -First 1).FullName
if (-not $adb) { throw "ADB not found. Install it with: winget install Google.PlatformTools" }
& $adb connect $Tv | Out-Host

# Everything except Netflix, Spotify, Settings, HDMI inputs, iCast and the Play Store.
$off = @(
    'com.google.android.youtube.tv', 'com.google.android.youtube.tvmusic', 'com.google.android.play.games',
    'fusion.android.tv.demo', 'org.liskovsoft.androidtv.rukeyboard', 'com.android.adservices.api',
    'com.google.android.feedback', 'com.android.tv.feedbackconsent', 'com.google.android.katniss',
    'com.Smartdevice.emanual', 'com.smartdevice.dailymanage', 'com.smartdevice.myapp', 'com.tianci.localmedia'
)
foreach ($p in $off) { & $adb -s $Tv shell pm disable-user --user 0 $p | Out-Host }

if ($Apk) {
    # Wake the TV first: replacing the app while its screensaver runs ends the screensaver, and Android then
    # puts the TV to sleep (which also unmounts the USB stick).
    & $adb -s $Tv shell input keyevent KEYCODE_WAKEUP | Out-Host
    # Straight onto the USB stick (pm move-package is refused while the TV's spare user profile is locked).
    $stick = (& $adb -s $Tv shell sm list-volumes private) -match '^private:' | ForEach-Object { ($_ -split ' ')[-1] } | Select-Object -First 1
    & $adb -s $Tv push $Apk /data/local/tmp/sol.apk | Out-Host
    & $adb -s $Tv shell "pm install -r --user 0 --force-uuid $stick /data/local/tmp/sol.apk; rm /data/local/tmp/sol.apk" | Out-Host
}

& $adb -s $Tv shell @'
settings put global window_animation_scale 0.5
settings put global transition_animation_scale 0.5
settings put global animator_duration_scale 0.5
settings put system screen_off_timeout 300000
settings put secure sleep_timeout -1
settings put secure attentive_timeout -1
settings put global no_signal_auto_power_off 0
settings put secure screensaver_enabled 1
settings put secure screensaver_components com.solbyoriana.screensaver/.SolDream
settings put secure screensaver_default_component com.solbyoriana.screensaver/.SolDream
echo screensaver: $(settings get secure screensaver_components)
'@ | Out-Host
