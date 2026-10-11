# Saves the Instagram token currently on your clipboard as the IG_TOKEN GitHub secret.
# Copy the token first (Ctrl+C), then run:  .\scripts\set-ig-token.ps1
$token = (Get-Clipboard -Raw)
if ($token) { $token = $token.Trim() }
if (-not $token -or $token.Length -lt 50 -or $token -match '\s') {
    Write-Host "The clipboard doesn't look like an Instagram token. Copy the token again (Ctrl+C) and re-run." -ForegroundColor Yellow
    exit 1
}
$token | gh secret set IG_TOKEN --repo xClusterCode/sol-website
if ($LASTEXITCODE -eq 0) {
    Write-Host "Saved ($($token.Length) characters, starting with $($token.Substring(0,4))...)." -ForegroundColor Green
}
