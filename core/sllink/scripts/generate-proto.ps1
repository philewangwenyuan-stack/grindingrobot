param(
    [string]$ProtocPath = $env:PROTOC_EXE,
    [string]$ProtocInclude = $env:PROTOC_INCLUDE
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProtocPath) -or -not (Test-Path -LiteralPath $ProtocPath)) {
    throw "Set PROTOC_EXE to a protoc 25.1 executable."
}
if ([string]::IsNullOrWhiteSpace($ProtocInclude) -or -not (Test-Path -LiteralPath $ProtocInclude)) {
    throw "Set PROTOC_INCLUDE to the include directory from the protoc 25.1 distribution."
}

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..\..")).Path
$protoDir = Join-Path $projectRoot "core\sllink\proto"
$protoFile = Join-Path $protoDir "sl_link.proto"
$outputDir = Join-Path $projectRoot "core\sllink\src\message_gen"

& $ProtocPath "-I=$protoDir" "-I=$ProtocInclude" "--java_out=$outputDir" $protoFile
if ($LASTEXITCODE -ne 0) {
    throw "protoc failed with exit code $LASTEXITCODE"
}

$generatedJava = Join-Path $outputDir "sl_link\SlLink.java"
$encoding = [System.Text.UTF8Encoding]::new($false)
$contents = [System.IO.File]::ReadAllText($generatedJava)
$contents = [System.Text.RegularExpressions.Regex]::Replace(
    $contents,
    "[ \t]+(?=\r?$)",
    "",
    [System.Text.RegularExpressions.RegexOptions]::Multiline
)
[System.IO.File]::WriteAllText($generatedJava, $contents, $encoding)

Write-Output "Generated core/sllink/src/message_gen/sl_link/SlLink.java from core/sllink/proto/sl_link.proto"
