param([string]$Dir)
# Trình phát nhạc trong game: dùng MediaPlayer có sẵn của Windows (mp3, wav, wma, m4a, aac, flac; cả đường link http).
# Nhận lệnh qua stdin:  open:<gen>|<đường dẫn hoặc link>   pause   resume   stop   vol:<0-100>   seek:<ms>   quit
# Trả trạng thái qua stdout (mỗi ~250ms), dạng JSON: {"gen":1,"state":"playing|paused|ended|failed|stopped","pos":ms,"dur":ms}
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName PresentationCore

# đọc stdin ở luồng riêng để vòng lặp chính không bị chặn
$q = New-Object 'System.Collections.Concurrent.ConcurrentQueue[string]'
$rs = [runspacefactory]::CreateRunspace()
$rs.Open()
$rs.SessionStateProxy.SetVariable('q', $q)
$ps = [powershell]::Create()
$ps.Runspace = $rs
[void]$ps.AddScript({
  while (($l = [Console]::In.ReadLine()) -ne $null) { $q.Enqueue($l) }
  $q.Enqueue('quit')
})
[void]$ps.BeginInvoke()

$p = New-Object System.Windows.Media.MediaPlayer
$p.Volume = 0.5
$state = 'stopped'
$gen = 0
$opened = [DateTime]::UtcNow
$last = [DateTime]::MinValue

while ($true) {
  $l = $null
  while ($q.TryDequeue([ref]$l)) {
    try {
      $t = $l.Trim()
      if ($t -eq 'quit') { $p.Close(); exit }
      elseif ($t -match '^open:(\d+)\|(.+)$') {
        $gen = [int]$Matches[1]
        $target = $Matches[2]
        $p.Close()
        if ($target -match '^https?://') { $uri = New-Object System.Uri $target }
        else { $uri = New-Object System.Uri ([IO.Path]::GetFullPath($target)) }
        $p.Open($uri)
        $p.Play()
        $state = 'playing'
        $opened = [DateTime]::UtcNow
      }
      elseif ($t -eq 'pause') { $p.Pause(); $state = 'paused' }
      elseif ($t -eq 'resume') { $p.Play(); $state = 'playing' }
      elseif ($t -eq 'stop') { $p.Stop(); $p.Close(); $state = 'stopped' }
      elseif ($t -match '^vol:(\d+)$') { $p.Volume = [math]::Min(100, [int]$Matches[1]) / 100.0 }
      elseif ($t -match '^seek:(\d+)$') { $p.Position = [TimeSpan]::FromMilliseconds([double]$Matches[1]) }
    } catch { $state = 'failed' }
  }

  $dur = 0
  if ($p.NaturalDuration.HasTimeSpan) { $dur = [long]$p.NaturalDuration.TimeSpan.TotalMilliseconds }
  $pos = [long]$p.Position.TotalMilliseconds
  if ($state -eq 'playing') {
    if ($dur -gt 0 -and $pos -ge ($dur - 400)) { $state = 'ended' }
    elseif ($dur -le 0 -and $pos -le 0 -and ([DateTime]::UtcNow - $opened).TotalSeconds -gt 20) { $state = 'failed' }
  }

  if (([DateTime]::UtcNow - $last).TotalMilliseconds -ge 250 -or $state -eq 'ended' -or $state -eq 'failed') {
    $last = [DateTime]::UtcNow
    [ordered]@{ gen = $gen; state = $state; pos = $pos; dur = $dur } | ConvertTo-Json -Compress
    if ($state -eq 'ended' -or $state -eq 'failed') { $p.Close(); $state = 'stopped' }
  }
  Start-Sleep -Milliseconds 60
}
