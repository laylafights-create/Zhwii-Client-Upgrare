param([string]$Dir)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
. (Join-Path $Dir 'common.ps1')
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) {
  $t = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
  $t.Wait(-1) | Out-Null
  $t.Result
}
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$lastKey = ''
$thumb = ''
$n = 0
while ($true) {
  try {
    $s = $mgr.GetCurrentSession()
    if ($null -eq $s) {
      '{"ok":false}'
    } else {
      $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
      $tl = $s.GetTimelineProperties()
      $pb = $s.GetPlaybackInfo()
      $playing = ($pb.PlaybackStatus.ToString() -eq 'Playing')
      $dur = ($tl.EndTime - $tl.StartTime).TotalMilliseconds
      $pos = $tl.Position.TotalMilliseconds
      if ($playing) { $pos += ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalMilliseconds }
      if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
      if ($pos -lt 0) { $pos = 0 }
      $key = "$($p.Title)|$($p.Artist)|$dur"
      if ($key -ne $lastKey) {
        $lastKey = $key
        $thumb = ''
        try {
          if ($null -ne $p.Thumbnail) {
            $st = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
            $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($st)
            $n++
            $f = Join-Path $Dir ("cover$n.img")
            $fs = [IO.File]::Create($f)
            $net.CopyTo($fs)
            $fs.Close(); $net.Close(); $st.Dispose()
            $thumb = $f
          }
        } catch { $thumb = '' }
      }
      $vol = [int][math]::Round([ZVol]::Get() * 100)
      [ordered]@{
        ok = $true; title = [string]$p.Title; artist = [string]$p.Artist; album = [string]$p.AlbumTitle
        playing = $playing; pos = [long]$pos; dur = [long]$dur; vol = $vol; thumb = $thumb
      } | ConvertTo-Json -Compress
    }
  } catch { '{"ok":false}' }
  Start-Sleep -Milliseconds 1000
}
