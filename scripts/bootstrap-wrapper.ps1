# Download a fixed Git blob of Google's open-source Gradle wrapper component.
# SHA is verified as a Git blob, so a changed upstream main branch cannot silently change this jar.
$ErrorActionPreference = 'Stop'
$expected = 'eddabd2eef8d94a5437d6168ff9c87a78ff725b3'
$url = 'https://api.github.com/repos/android/nowinandroid/git/blobs/' + $expected
$target = Join-Path $PSScriptRoot '..\gradle\wrapper\gradle-wrapper.jar'
$blob = Invoke-RestMethod -Uri $url -Headers @{
    Accept = 'application/vnd.github+json'
    'User-Agent' = 'SecurityPatrolBuild'
}
if ($blob.sha -ne $expected -or $blob.encoding -ne 'base64') {
    throw 'GitHub returned an unexpected Gradle wrapper blob.'
}
$bytes = [Convert]::FromBase64String(($blob.content -replace '\s', ''))
$header = [Text.Encoding]::ASCII.GetBytes('blob ' + $bytes.Length + [char]0)
$combined = New-Object byte[] ($header.Length + $bytes.Length)
[Array]::Copy($header, 0, $combined, 0, $header.Length)
[Array]::Copy($bytes, 0, $combined, $header.Length, $bytes.Length)
$hash = [BitConverter]::ToString(
    [Security.Cryptography.SHA1]::Create().ComputeHash($combined)
).Replace('-', '').ToLowerInvariant()
if ($hash -ne $expected) {
    throw 'Gradle wrapper integrity check failed.'
}
[IO.File]::WriteAllBytes($target, $bytes)
Write-Host 'Verified Gradle wrapper component installed.'
