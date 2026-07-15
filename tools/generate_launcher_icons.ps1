param(
    [Parameter(Mandatory = $true)]
    [string]$Source
)

Add-Type -AssemblyName System.Drawing

$projectRoot = "C:\yutnori\Yutnori"
$storeDir = Join-Path $projectRoot "store-assets"
$resDir = Join-Path $projectRoot "app\src\main\res"
$store512 = Join-Path $storeDir "yutnori-play-icon-512.png"
$adaptiveArt = Join-Path $resDir "drawable-nodpi\ic_launcher_art.png"

New-Item -ItemType Directory -Path $storeDir -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path $adaptiveArt) -Force | Out-Null

$densitySizes = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

function Save-SquareIcon($image, [int]$size, [string]$path) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $graphics = [System.Drawing.Graphics]::FromImage($bmp)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.DrawImage($image, 0, 0, $size, $size)
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bmp.Dispose()
}

function Save-RoundIcon($image, [int]$size, [string]$path) {
    $pixelFormat = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    $bmp = New-Object System.Drawing.Bitmap $size, $size, $pixelFormat
    $graphics = [System.Drawing.Graphics]::FromImage($bmp)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $clip = New-Object System.Drawing.Drawing2D.GraphicsPath
    $clip.AddEllipse(0, 0, $size, $size)
    $graphics.SetClip($clip)
    $graphics.DrawImage($image, 0, 0, $size, $size)
    $graphics.ResetClip()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)

    $clip.Dispose()
    $graphics.Dispose()
    $bmp.Dispose()
}

if (!(Test-Path -LiteralPath $Source)) {
    throw "Source image does not exist: $Source"
}

Copy-Item -LiteralPath $Source -Destination (Join-Path $storeDir "yutnori-launcher-icon-source.png") -Force

$image = [System.Drawing.Image]::FromFile($Source)
try {
    foreach ($density in $densitySizes.Keys) {
        $densityDir = Join-Path $resDir $density
        New-Item -ItemType Directory -Path $densityDir -Force | Out-Null
        $size = $densitySizes[$density]
        Save-SquareIcon $image $size (Join-Path $densityDir "ic_launcher.png")
        Save-RoundIcon $image $size (Join-Path $densityDir "ic_launcher_round.png")
    }

    Save-SquareIcon $image 512 $store512
    Copy-Item -LiteralPath $store512 -Destination $adaptiveArt -Force
}
finally {
    $image.Dispose()
}

Get-ChildItem -Path $resDir -Recurse -Include "ic_launcher*.png" |
    Sort-Object FullName |
    Select-Object FullName, Length, LastWriteTime
