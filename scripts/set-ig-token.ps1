# Saves the token currently on your clipboard as a GitHub secret (default: IG_TOKEN).
# Copy the token first (Ctrl+C), then run:  .\scripts\set-ig-token.ps1
# For the GitHub token used to auto-save refreshed Instagram tokens:  .\scripts\set-ig-token.ps1 -Name SECRETS_PAT
param([string]$Name = 'IG_TOKEN')
$token = (Get-Clipboard -Raw)
if ($token) { $token = $token.Trim() }
if (-not $token -or $token.Length -lt 30 -or $token -match '\s') {
    Write-Host "The clipboard doesn't look like a token. Copy it again (Ctrl+C) and re-run." -ForegroundColor Yellow
    exit 1
}
$token | gh secret set $Name --repo solbyoriana/solbyoriana.github.io
if ($LASTEXITCODE -eq 0) {
    Write-Host "Saved $Name ($($token.Length) characters, starting with $($token.Substring(0,4))...)." -ForegroundColor Green
}
