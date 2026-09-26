param(
    [string]$Source = "C:\yutnori\Yutnori\store-assets\yutnori-launcher-icon-source.png",
    [string]$PlayIcon = "C:\yutnori\Yutnori\store-assets\yutnori-play-icon-512.png",
    [string]$OutputDir = "C:\yutnori\Yutnori\store-assets\play-store"
)

Add-Type -AssemblyName System.Drawing

if (!(Test-Path -LiteralPath $Source)) {
    throw "Launcher icon source does not exist: $Source"
}
if (!(Test-Path -LiteralPath $PlayIcon)) {
    throw "Play icon does not exist: $PlayIcon"
}

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$iconOutput = Join-Path $OutputDir "app-icon-512.png"
$featureOutput = Join-Path $OutputDir "feature-graphic-1024x500.png"
Copy-Item -LiteralPath $PlayIcon -Destination $iconOutput -Force

$sourceImage = [System.Drawing.Image]::FromFile($Source)
$canvas = New-Object System.Drawing.Bitmap 1024, 500, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$graphics = [System.Drawing.Graphics]::FromImage($canvas)

try {
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    $canvasRect = New-Object System.Drawing.Rectangle 0, 0, 1024, 500
    $background = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $canvasRect,
        [System.Drawing.ColorTranslator]::FromHtml("#F4D486"),
        [System.Drawing.ColorTranslator]::FromHtml("#FFF0C4"),
        15
    )
    $graphics.FillRectangle($background, $canvasRect)

    $random = New-Object System.Random 725
    for ($i = 0; $i -lt 900; $i++) {
        $alpha = $random.Next(5, 15)
        $fiberColor = [System.Drawing.Color]::FromArgb($alpha, 104, 71, 28)
        $fiberPen = New-Object System.Drawing.Pen $fiberColor, 1
        $x = $random.Next(0, 1024)
        $y = $random.Next(0, 500)
        $graphics.DrawLine(
            $fiberPen,
            $x,
            $y,
            $x + $random.Next(-16, 17),
            $y + $random.Next(-8, 9)
        )
        $fiberPen.Dispose()
    }

    $artRect = New-Object System.Drawing.Rectangle 524, 0, 500, 500
    $graphics.DrawImage($sourceImage, $artRect)

    $blendRect = New-Object System.Drawing.Rectangle 470, 0, 130, 500
    $blend = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $blendRect,
        [System.Drawing.Color]::FromArgb(255, 247, 222, 159),
        [System.Drawing.Color]::FromArgb(0, 247, 222, 159),
        0
    )
    $graphics.FillRectangle($blend, $blendRect)

    $titleFont = New-Object System.Drawing.Font "Segoe UI", 70, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $subtitleFont = New-Object System.Drawing.Font "Segoe UI", 18, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $titleBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#173D42"))
    $subtitleBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#76552D"))

    $graphics.DrawString("Yutnori", $titleFont, $titleBrush, 62, 158)
    $graphics.DrawString("KOREAN TRADITIONAL BOARD GAME", $subtitleFont, $subtitleBrush, 67, 248)

    $swatches = @("#F25F5C", "#226FD1", "#F0AF32")
    for ($i = 0; $i -lt $swatches.Count; $i++) {
        $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($swatches[$i]))
        $graphics.FillEllipse($brush, 67 + ($i * 42), 302, 24, 24)
        $brush.Dispose()
    }

    $canvas.Save($featureOutput, [System.Drawing.Imaging.ImageFormat]::Png)
}
finally {
    if ($background) { $background.Dispose() }
    if ($blend) { $blend.Dispose() }
    if ($titleFont) { $titleFont.Dispose() }
    if ($subtitleFont) { $subtitleFont.Dispose() }
    if ($titleBrush) { $titleBrush.Dispose() }
    if ($subtitleBrush) { $subtitleBrush.Dispose() }
    $graphics.Dispose()
    $canvas.Dispose()
    $sourceImage.Dispose()
}

Get-Item $iconOutput, $featureOutput |
    Select-Object FullName, Length, LastWriteTime
