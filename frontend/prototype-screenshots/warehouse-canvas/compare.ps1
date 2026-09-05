param([string]$State = 'overview', [string]$Suffix = 'before', [int]$X = 0, [int]$Y = 0, [int]$Width = 1440, [int]$Height = 1024, [string]$Region = 'full')
Add-Type -AssemblyName System.Drawing
$source = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot "source-$State.png"))
$implementation = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot "implementation-$State-$Suffix.png"))
if ($X -lt 0 -or $Y -lt 0 -or $Width -le 0 -or $Height -le 0 -or ($X + $Width) -gt [Math]::Min($source.Width, $implementation.Width) -or ($Y + $Height) -gt [Math]::Min($source.Height, $implementation.Height)) {
  $source.Dispose(); $implementation.Dispose()
  throw 'The comparison crop must fit both original images at 1:1 scale.'
}
$result = New-Object System.Drawing.Bitmap ($Width * 2), $Height
$graphics = [System.Drawing.Graphics]::FromImage($result)
$crop = New-Object System.Drawing.Rectangle $X, $Y, $Width, $Height
$left = New-Object System.Drawing.Rectangle 0, 0, $Width, $Height
$right = New-Object System.Drawing.Rectangle $Width, 0, $Width, $Height
$graphics.DrawImage($source, $left, $crop, [System.Drawing.GraphicsUnit]::Pixel)
$graphics.DrawImage($implementation, $right, $crop, [System.Drawing.GraphicsUnit]::Pixel)
$path = Join-Path $PSScriptRoot "comparison-$State-$Region-$Suffix.png"
$result.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose(); $result.Dispose(); $source.Dispose(); $implementation.Dispose()
Write-Output $path
