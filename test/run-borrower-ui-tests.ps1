#Requires -Version 7.0

param(
    [string]$Classes = 'build/classes/java/main'
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$dataFile = Join-Path $repository ('build/ui-borrowers-' + [guid]::NewGuid().ToString('N') + '.properties')
$transcript = [System.Collections.Generic.List[string]]::new()
$transcript.Add('# Borrower console test results')
$transcript.Add('Output comparisons preserve the final prompt space; code blocks omit it for Markdown formatting.')
$cases = @(
    @{
        Name = 'Case 1: add, duplicate rejection, and list'
        Input = "add-borrower A0123456X n/Jamie Tan p/91234567 e/jamie@u.nus.edu`nadd-borrower A0123456X n/Other p/91234567 e/other@u.nus.edu`nloans A0123456X`n"
        Expected = "LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.`nlablog> [OK] Added borrower A0123456X: Jamie Tan.`nPhone: 91234567 | Email: jamie@u.nus.edu`nlablog> Error: Borrower ID A0123456X already exists. Choose a different borrower ID.`nNo changes saved.`nlablog> No active loans for A0123456X - Jamie Tan.`nlablog> "
    },
    @{
        Name = 'Case 2: reload persisted borrower'
        Input = "loans A0123456X`n"
        Expected = "LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.`nlablog> No active loans for A0123456X - Jamie Tan.`nlablog> "
    }
)

foreach ($case in $cases) {
    $info = [System.Diagnostics.ProcessStartInfo]::new('java')
    $info.WorkingDirectory = $repository
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in @('-cp', $Classes, 'lablog.BorrowerCli', $dataFile)) {
        $info.ArgumentList.Add($argument)
    }
    $process = [System.Diagnostics.Process]::Start($info)
    $process.StandardInput.Write($case.Input)
    $process.StandardInput.Close()
    $actual = $process.StandardOutput.ReadToEnd().Replace("`r`n", "`n")
    $errors = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $transcript.Add("## $($case.Name)`n`nCommand: java -cp $Classes lablog.BorrowerCli $dataFile`n")
    $transcript.Add("Input:`n``````text`n$($case.Input)```````n`nActual output:`n``````text`n$($actual.TrimEnd())`n``````")
    $transcript.Add("Exit code: $($process.ExitCode). Stderr: $(if ($errors.Length -eq 0) { '(empty)' } else { $errors })")
    if ($actual -cne $case.Expected -or $errors.Length -ne 0 -or $process.ExitCode -ne 0) {
        $transcript.Add("FAILED. Expected:`n$($case.Expected)")
        $transcript | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'borrower-ui-transcript.md')
        throw "$($case.Name) failed. See test/borrower-ui-transcript.md."
    }
    $transcript.Add('PASS: exact match after normalizing line endings.')
    $process.Dispose()
}
$transcript | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'borrower-ui-transcript.md')
Write-Output 'Both borrower console cases passed.'
