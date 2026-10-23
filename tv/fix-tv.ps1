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
    & $adb -s $Tv install -r $Apk | Out-Host
    $stick = (& $adb -s $Tv shell sm list-volumes private) -match '^private:' | ForEach-Object { ($_ -split ' ')[-1] } | Select-Object -First 1
    if ($stick) { & $adb -s $Tv shell pm move-package com.solbyoriana.screensaver $stick | Out-Host }
}

& $adb -s $Tv shell @'
settings put global window_animation_scale 0.5
settings put global transition_animation_scale 0.5
settings put global animator_duration_scale 0.5
settings put system screen_off_timeout 300000
settings put secure sleep_timeout 14400000
settings put secure screensaver_enabled 1
settings put secure screensaver_components com.solbyoriana.screensaver/.SolDream
settings put secure screensaver_default_component com.solbyoriana.screensaver/.SolDream
echo screensaver: $(settings get secure screensaver_components)
'@ | Out-Host
