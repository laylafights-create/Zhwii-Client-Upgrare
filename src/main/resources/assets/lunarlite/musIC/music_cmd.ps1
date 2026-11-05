param([string]$Dir)
. (Join-Path $Dir 'common.ps1')
while (($l = [Console]::In.ReadLine()) -ne $null) {
  try {
    switch -Regex ($l.Trim()) {
      '^play$' { [ZKey]::Press(0xB3) }
      '^next$' { [ZKey]::Press(0xB0) }
      '^prev$' { [ZKey]::Press(0xB1) }
      '^vol:(\d+)$' { [ZVol]::Set([math]::Min(100, [int]$Matches[1]) / 100.0) }
    }
  } catch { }
}
