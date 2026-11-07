# KeyPop permanent Android signing - run locally on your trusted Windows PC.
# Usage: powershell -ExecutionPolicy Bypass -File .\Setup-KeyPop-Signing.ps1
# Requires JDK 17+ (keytool), GitHub CLI (gh), and admin rights on this repository.
# NEVER commit, email, or paste the .p12 or the recovery text into GitHub.

[CmdletBinding()]
param(
    [string]$Repository = "amjadrajamemon/KeyPop",
    [switch]$SkipWorkflowTrigger
)
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$environmentName = "keypop-signing"
$workflowBranch = "feature/rc15-permanent-signing-20261107"
$alias = "keypop-stable-v1"

function Assert-Command([string]$cmd, [string]$help) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        throw "$cmd not found. $help"
    }
}
function Assert-LastExit([string]$description) {
    if ($LASTEXITCODE -ne 0) { throw "$description failed (exit $LASTEXITCODE)." }
}
function New-RandomPassword {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [BitConverter]::ToString($bytes).Replace("-", "")
}
function Set-SecretStdin([string]$name, [string]$value) {
    # Write the secret to gh subprocess stdin WITHOUT a trailing newline.
    # Unlike passing --body, this avoids passwords in process arguments.
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = (Get-Command gh).Source
    $psi.Arguments = "secret set $name --repo $Repository --env $environmentName"
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $proc = [System.Diagnostics.Process]::Start($psi)
    try {
        $proc.StandardInput.Write($value)
        $proc.StandardInput.Close()
        $stdout = $proc.StandardOutput.ReadToEnd()
        $stderr = $proc.StandardError.ReadToEnd()
        $proc.WaitForExit()
        if ($proc.ExitCode -ne 0) {
            throw "Unable to upload GitHub environment secret $name. $stderr"
        }
    } finally {
        $proc.Dispose()
    }
}

Assert-Command "keytool" "Install a JDK, e.g. Eclipse Temurin JDK 17, and reopen PowerShell."
Assert-Command "gh" "Install GitHub CLI using winget install --id GitHub.cli, then reopen PowerShell."

& gh auth status --hostname github.com 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Sign in to GitHub as the owner of $Repository..."
    & gh auth login --hostname github.com --web --git-protocol https
    Assert-LastExit "GitHub login"
}

$backup = Join-Path ([Environment]::GetFolderPath("MyDocuments")) "KeyPop-Signing-Backup"
if (-not (Test-Path $backup)) { New-Item -Path $backup -ItemType Directory | Out-Null }
$keystore = Join-Path $backup "keypop-stable-v1.p12"
$recovery = Join-Path $backup "keypop-recovery.txt"

if (Test-Path $keystore) {
    if (-not (Test-Path $recovery)) {
        throw "Existing keystore found but local recovery file is missing. Do NOT generate another key; restore the ORIGINAL password backup."
    }
    $matching = @(Get-Content $recovery | Where-Object { $_.StartsWith("StorePassword=") })
    if ($matching.Count -ne 1) { throw "Signing password was not found in the recovery backup." }
    $password = $matching[0].Substring("StorePassword=".Length)
    if ($password.Length -lt 16) { throw "Signing password is unexpectedly short." }
    Write-Host "Reusing your existing signing keystore (not replacing it)."
} else {
    if (Test-Path $recovery) {
        throw "The recovery backup exists but the keystore does not. Restore the ORIGINAL .p12 file; do NOT rotate the signing key."
    }
    $password = New-RandomPassword
    $env:KEYPOP_SIGNING_STORE_PASSWORD = $password
    $env:KEYPOP_SIGNING_KEY_PASSWORD = $password
    $keyArgs = @("-genkeypair", "-noprompt", "-alias", $alias, "-keyalg", "RSA",
        "-keysize", "3072", "-validity", "10000", "-storetype", "PKCS12",
        "-keystore", $keystore, "-dname",
        "CN=KeyPop Android Signing, OU=KeyPop, O=KeyPop, C=PK",
        "-storepass:env", "KEYPOP_SIGNING_STORE_PASSWORD",
        "-keypass:env", "KEYPOP_SIGNING_KEY_PASSWORD")
    & keytool @keyArgs
    Assert-LastExit "Keystore generation"
    $recoveryText = "KEYPOP SIGNING KEY -- KEEP PRIVATE AND BACK UP OFFLINE" + [Environment]::NewLine +
        "Repository=$Repository" + [Environment]::NewLine +
        "KeyAlias=$alias" + [Environment]::NewLine +
        "StorePassword=$password" + [Environment]::NewLine +
        "KeyPassword=$password" + [Environment]::NewLine
    [System.IO.File]::WriteAllText($recovery, $recoveryText, [System.Text.Encoding]::UTF8)
}
$env:KEYPOP_SIGNING_STORE_PASSWORD = $password
$env:KEYPOP_SIGNING_KEY_PASSWORD = $password
try {
    $listArgs = @("-list", "-v", "-keystore", $keystore, "-alias", $alias,
        "-storepass:env", "KEYPOP_SIGNING_STORE_PASSWORD")
    $keyListing = & keytool @listArgs
    Assert-LastExit "Keystore verification"
    $match = [regex]::Match(($keyListing -join [Environment]::NewLine), "SHA256:\s*([0-9a-fA-F:]+)")
    if (-not $match.Success) { throw "Unable to determine keystore certificate fingerprint." }
    $fingerprint = $match.Groups[1].Value.Replace(":", "").ToUpperInvariant()
    if ($fingerprint.Length -ne 64) { throw "Invalid SHA-256 certificate fingerprint." }

    Write-Host "Creating/confirming the GitHub Actions signing environment..."
    & gh api --method PUT "repos/$Repository/environments/$environmentName" | Out-Null
    Assert-LastExit "Signing environment setup"
    Write-Host "Uploading encrypted keystore and credentials to GitHub environment secrets..."
    Set-SecretStdin "KEYPOP_SIGNING_KEYSTORE_B64" ([Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore)))
    Set-SecretStdin "KEYPOP_SIGNING_STORE_PASSWORD" $password
    Set-SecretStdin "KEYPOP_SIGNING_KEY_ALIAS" $alias
    Set-SecretStdin "KEYPOP_SIGNING_KEY_PASSWORD" $password

    & gh variable set "KEYPOP_SIGNING_CERT_SHA256" --repo $Repository --env $environmentName --body $fingerprint
    Assert-LastExit "Publishing public certificate fingerprint"
    Write-Host "Public signing certificate SHA-256: $fingerprint"
    Write-Host "Signing secrets uploaded. Private key NEVER entered the Git repository."

    if (-not $SkipWorkflowTrigger) {
        Write-Host "Starting the signed-build workflow..."
        & gh workflow run "android-debug-apk.yml" --repo $Repository --ref $workflowBranch
        Assert-LastExit "Dispatching signed Android workflow"
    }
    Write-Host "SETUP COMPLETE. Backup BOTH these files to a secure offline drive:"
    Write-Host "  $keystore"
    Write-Host "  $recovery"
    Write-Host "Never publish either file, and never rotate the key for an existing app package."
} finally {
    Remove-Item Env:\KEYPOP_SIGNING_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\KEYPOP_SIGNING_KEY_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
}
