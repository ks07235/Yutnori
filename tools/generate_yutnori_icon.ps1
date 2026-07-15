Add-Type -AssemblyName System.Drawing

$root = "C:\yutnori\Yutnori\store-assets"
$out = Join-Path $root "yutnori-app-icon-final-locked.png"
$out512 = Join-Path $root "yutnori-play-icon-512-final-locked.png"
$size = 1254
$bmp = New-Object System.Drawing.Bitmap $size, $size
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

function New-Brush($hex) {
    return New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($hex))
}

function New-RoundPen($hex, $width) {
    $pen = New-Object System.Drawing.Pen ([System.Drawing.ColorTranslator]::FromHtml($hex)), $width
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    return $pen
}

function New-RoundedRectPath($x, $y, $w, $h, $radius) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $radius * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function Draw-Circle($x, $y, $r, $fill) {
    $g.FillEllipse($fill, $x - $r, $y - $r, $r * 2, $r * 2)
    $g.DrawEllipse((New-RoundPen "#0A3B42" 9), $x - $r, $y - $r, $r * 2, $r * 2)
    $g.DrawEllipse((New-RoundPen "#176168" 7), $x - $r, $y - $r, $r * 2, $r * 2)
}

function Draw-Piece($cx, $cy, $c1, $c2, $trailHex) {
    $trail = [System.Drawing.ColorTranslator]::FromHtml($trailHex)
    for ($i = 0; $i -lt 3; $i++) {
        $trailPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(95 - $i * 24, $trail.R, $trail.G, $trail.B)), (9 - $i)
        $trailPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
        $trailPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
        $g.DrawBezier($trailPen, $cx - 185 + $i * 24, $cy + 58 + $i * 7, $cx - 130, $cy + 5, $cx - 62, $cy - 7, $cx - 12, $cy - 4)
        $trailPen.Dispose()
    }

    $g.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(45, 0, 0, 0))), $cx - 60, $cy + 47, 130, 34)
    $rect = New-Object System.Drawing.Rectangle ($cx - 61), ($cy - 61), 122, 122
    $pieceBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, ([System.Drawing.ColorTranslator]::FromHtml($c1)), ([System.Drawing.ColorTranslator]::FromHtml($c2)), 70
    $g.FillEllipse($pieceBrush, $rect)
    $g.DrawEllipse((New-RoundPen "#FFFFFF" 5), $rect)
    $g.DrawEllipse((New-RoundPen $c2 6), $cx - 42, $cy - 42, 84, 84)

    $flowerPen = New-RoundPen "#FFFFFF" 3
    for ($angle = 0; $angle -lt 360; $angle += 60) {
        $state = $g.Save()
        $g.TranslateTransform($cx, $cy)
        $g.RotateTransform($angle)
        $g.DrawEllipse($flowerPen, -9, -33, 18, 38)
        $g.Restore($state)
    }
    $g.FillEllipse((New-Brush "#FFFFFF"), $cx - 8, $cy - 8, 16, 16)
}

function Draw-Stick($angle, $drawMarks) {
    $state = $g.Save()
    $g.TranslateTransform(627, 626)
    $g.RotateTransform($angle)

    $x = -394
    $y = -63
    $w = 788
    $h = 126
    $radius = 63

    $shadowPath = New-RoundedRectPath ($x + 16) ($y + 22) $w $h $radius
    $g.FillPath((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(65, 0, 0, 0))), $shadowPath)

    $path = New-RoundedRectPath $x $y $w $h $radius
    $rect = New-Object System.Drawing.Rectangle $x, $y, $w, $h
    $woodBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, ([System.Drawing.ColorTranslator]::FromHtml("#B86916")), ([System.Drawing.ColorTranslator]::FromHtml("#FFD170")), 90
    $g.FillPath($woodBrush, $path)
    $g.DrawPath((New-RoundPen "#88490E" 3), $path)

    $grainPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(50, 97, 47, 8)), 2
    for ($xx = -350; $xx -le 350; $xx += 26) {
        $g.DrawBezier($grainPen, $xx, -42, $xx + 48, -29, $xx - 42, 23, $xx + 44, 42)
    }

    if ($drawMarks) {
        $dark = New-RoundPen "#713200" 14
        $light = New-RoundPen "#FFD58A" 5
        foreach ($mx in @(-255, -85, 85, 255)) {
            $len = 28
            $g.DrawLine($dark, $mx - $len, -$len, $mx + $len, $len)
            $g.DrawLine($dark, $mx - $len, $len, $mx + $len, -$len)
            $g.DrawLine($light, $mx - $len + 3, -$len + 3, $mx + $len - 3, $len - 3)
            $g.DrawLine($light, $mx - $len + 3, $len - 3, $mx + $len - 3, -$len + 3)
        }
    }

    $g.Restore($state)
}

$backgroundRect = New-Object System.Drawing.Rectangle 0, 0, $size, $size
$backgroundBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $backgroundRect, ([System.Drawing.ColorTranslator]::FromHtml("#F3D27C")), ([System.Drawing.ColorTranslator]::FromHtml("#FFF0C6")), 40
$g.FillRectangle($backgroundBrush, $backgroundRect)

$rng = New-Object System.Random 777
for ($i = 0; $i -lt 2300; $i++) {
    $fiberPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb($rng.Next(7, 20), 118, 84, 33)), $rng.Next(1, 3)
    $x = $rng.Next(0, $size)
    $y = $rng.Next(0, $size)
    $g.DrawLine($fiberPen, $x, $y, $x + $rng.Next(-30, 31), $y + $rng.Next(-16, 17))
    $fiberPen.Dispose()
}

$left = 190
$right = 1064
$top = 190
$bottom = 1064
$center = 627
$shadowPen = New-RoundPen "#093940" 18
$linePen = New-RoundPen "#176168" 11
$lines = @(
    @($left, $top, $right, $top),
    @($right, $top, $right, $bottom),
    @($right, $bottom, $left, $bottom),
    @($left, $bottom, $left, $top),
    @($left, $top, $center, $center),
    @($right, $top, $center, $center),
    @($left, $bottom, $center, $center),
    @($right, $bottom, $center, $center)
)
foreach ($line in $lines) {
    $g.DrawLine($shadowPen, $line[0], $line[1], $line[2], $line[3])
}
foreach ($line in $lines) {
    $g.DrawLine($linePen, $line[0], $line[1], $line[2], $line[3])
}

$nodeFill = New-Brush "#FFE9AD"
$centerFill = New-Brush "#F1BB42"
$outerNodes = @(
    @(190, 190, 48), @(365, 190, 35), @(540, 190, 35), @(714, 190, 35), @(889, 190, 35), @(1064, 190, 48),
    @(1064, 365, 35), @(1064, 540, 35), @(1064, 714, 35), @(1064, 889, 35), @(1064, 1064, 48),
    @(889, 1064, 35), @(714, 1064, 35), @(540, 1064, 35), @(365, 1064, 35), @(190, 1064, 48),
    @(190, 889, 35), @(190, 714, 35), @(190, 540, 35), @(190, 365, 35)
)
foreach ($node in $outerNodes) {
    Draw-Circle $node[0] $node[1] $node[2] $nodeFill
}
Draw-Circle $center $center 45 $centerFill

Draw-Piece 220 820 "#FF5144" "#BA1512" "#FF6B55"
Draw-Piece 624 1068 "#177DFF" "#0648B9" "#26A9FF"
Draw-Piece 1032 840 "#FFC741" "#D37A00" "#FFD75A"

Draw-Stick -38 $false
Draw-Stick 38 $true

$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)

$small = New-Object System.Drawing.Bitmap 512, 512
$sg = [System.Drawing.Graphics]::FromImage($small)
$sg.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$sg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$sg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$sg.DrawImage($bmp, 0, 0, 512, 512)
$small.Save($out512, [System.Drawing.Imaging.ImageFormat]::Png)

$sg.Dispose()
$small.Dispose()
$g.Dispose()
$bmp.Dispose()

Get-Item $out, $out512 | Select-Object FullName, Length, LastWriteTime
