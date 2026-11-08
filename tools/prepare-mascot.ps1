Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$generated = 'C:/Users/zlxld/.codex/generated_images/01a1b976-dfc0-7760-8868-1e61feb57432'
$dest = Join-Path $root 'app/src/main/res/drawable-nodpi'
New-Item -ItemType Directory -Force -Path $dest | Out-Null
function Save-Scaled($source, $name, $edge) {
    $img = [System.Drawing.Image]::FromFile($source)
    $ratio = $edge / [Math]::Max($img.Width, $img.Height)
    $bitmap = New-Object System.Drawing.Bitmap ([int]($img.Width * $ratio)), ([int]($img.Height * $ratio))
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($img, 0, 0, $bitmap.Width, $bitmap.Height)
    $bitmap.Save((Join-Path $dest "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose(); $bitmap.Dispose(); $img.Dispose()
}
Save-Scaled (Join-Path $root 'design/character-default-blue.png') 'mascot_normal' 720
$states = @{
    fuel = 'exec-a5842764-5934-43d4-96ec-75b67cb4b7d2.png'
    wash = 'exec-93970899-a8aa-4ef0-aa4d-c84f4ce74893.png'
    oil = 'exec-15affbf3-7fca-4042-b57c-8348bfa01ab3.png'
    angry = 'exec-1049eadc-edbb-4fd4-a1ef-b3f866386f48.png'
    exhausted = 'exec-24b01373-3bad-44a1-a05a-33a963182dd7.png'
}
foreach ($state in $states.Keys) { Save-Scaled (Join-Path $generated $states[$state]) "mascot_$state" 720 }
$faces = @{ normal = 'icon 1.png'; fuel = 'fuel-low-yellow.png'; wash = 'wash-needed-brown.png'; oil = 'oil-change-due-v1.png'; angry = 'angry-red-v3.png'; exhausted = 'exhausted-charcoal.png' }
foreach ($state in $faces.Keys) { Save-Scaled (Join-Path $root "design/$($faces[$state])") "face_$state" 256 }
