param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [int[]]$IncludeTextures = @()
)

$ErrorActionPreference = 'Stop'

$data = Get-Content -Raw -LiteralPath $InputPath | ConvertFrom-Json
$resW = [double]$data.resolution.width
$resH = [double]$data.resolution.height
$textureSizes = @{}
foreach ($texture in $data.textures) {
    $uvWidth = if ($texture.uv_width) { [double]$texture.uv_width } else { [double]$texture.width }
    $uvHeight = if ($texture.uv_height) { [double]$texture.uv_height } else { [double]$texture.height }
    $textureSizes[[int]$texture.id] = [double[]]@($uvWidth, $uvHeight)
    $textureSizes[$textureSizes.Count - 1] = [double[]]@($uvWidth, $uvHeight)
}

function Rotate-Point {
    param([double[]]$Point, [double[]]$Origin, [double[]]$Rotation)

    $px = [double]$Point[0]
    $py = [double]$Point[1]
    $pz = [double]$Point[2]
    $ox = [double]$Origin[0]
    $oy = [double]$Origin[1]
    $oz = [double]$Origin[2]

    $x = $px - $ox
    $y = $py - $oy
    $z = $pz - $oz

    $rx = [Math]::PI * $Rotation[0] / 180.0
    $ry = [Math]::PI * $Rotation[1] / 180.0
    $rz = [Math]::PI * $Rotation[2] / 180.0

    if ([Math]::Abs($rx) -gt 0.0000001) {
        $c = [Math]::Cos($rx)
        $s = [Math]::Sin($rx)
        $ny = $y * $c - $z * $s
        $nz = $y * $s + $z * $c
        $y = $ny
        $z = $nz
    }
    if ([Math]::Abs($ry) -gt 0.0000001) {
        $c = [Math]::Cos($ry)
        $s = [Math]::Sin($ry)
        $nx = $x * $c + $z * $s
        $nz = -$x * $s + $z * $c
        $x = $nx
        $z = $nz
    }
    if ([Math]::Abs($rz) -gt 0.0000001) {
        $c = [Math]::Cos($rz)
        $s = [Math]::Sin($rz)
        $nx = $x * $c - $y * $s
        $ny = $x * $s + $y * $c
        $x = $nx
        $y = $ny
    }

    $rxOut = [double]$x + [double]$ox
    $ryOut = [double]$y + [double]$oy
    $rzOut = [double]$z + [double]$oz
    return ,([double[]]@($rxOut, $ryOut, $rzOut))
}

function Rotate-Vector {
    param([double[]]$Vector, [double[]]$Rotation)
    return ,(Rotate-Point -Point $Vector -Origin ([double[]]@(0, 0, 0)) -Rotation $Rotation)
}

function Round-Array {
    param([double[]]$Values)
    @($Values | ForEach-Object { [Math]::Round($_, 6) })
}

function Normalize-Point {
    param($Point)
    $p = $Point
    while ($p -is [array] -and $p.Count -eq 1 -and $p[0] -is [array]) {
        $p = $p[0]
    }
    return ,([double[]]@([double]$p[0], [double]$p[1], [double]$p[2]))
}

function Subtract-Point {
    param($A, $B)
    $a = Normalize-Point $A
    $b = Normalize-Point $B
    $ax = [double]$a[0]
    $ay = [double]$a[1]
    $az = [double]$a[2]
    $bx = [double]$b[0]
    $by = [double]$b[1]
    $bz = [double]$b[2]
    $dx = $ax - $bx
    $dy = $ay - $by
    $dz = $az - $bz
    return ,([double[]]@($dx, $dy, $dz))
}

function Cross-Point {
    param($A, $B)
    $a = Normalize-Point $A
    $b = Normalize-Point $B
    $ax = [double]$a[0]
    $ay = [double]$a[1]
    $az = [double]$a[2]
    $bx = [double]$b[0]
    $by = [double]$b[1]
    $bz = [double]$b[2]
    $cx = ($ay * $bz) - ($az * $by)
    $cy = ($az * $bx) - ($ax * $bz)
    $cz = ($ax * $by) - ($ay * $bx)
    return ,([double[]]@($cx, $cy, $cz))
}

function Dot-Point {
    param($A, $B)
    $a = Normalize-Point $A
    $b = Normalize-Point $B
    $ax = [double]$a[0]
    $ay = [double]$a[1]
    $az = [double]$a[2]
    $bx = [double]$b[0]
    $by = [double]$b[1]
    $bz = [double]$b[2]
    ($ax * $bx) + ($ay * $by) + ($az * $bz)
}

$points = [System.Collections.Generic.List[double[]]]::new()
foreach ($element in $data.elements) {
    $origin = if ($null -eq $element.origin) { [double[]]@(0, 0, 0) } else { [double[]]$element.origin }
    $rotation = if ($null -eq $element.rotation) { [double[]]@(0, 0, 0) } else { [double[]]$element.rotation }
    foreach ($x in @([double]$element.from[0], [double]$element.to[0])) {
        foreach ($y in @([double]$element.from[1], [double]$element.to[1])) {
            foreach ($z in @([double]$element.from[2], [double]$element.to[2])) {
                $points.Add((Rotate-Point -Point ([double[]]@($x, $y, $z)) -Origin $origin -Rotation $rotation))
            }
        }
    }
}

$min = [double[]]@(999999.0, 999999.0, 999999.0)
$max = [double[]]@(-999999.0, -999999.0, -999999.0)
foreach ($point in $points) {
    for ($i = 0; $i -lt 3; $i++) {
        if ($point[$i] -lt $min[$i]) { $min[$i] = $point[$i] }
        if ($point[$i] -gt $max[$i]) { $max[$i] = $point[$i] }
    }
}

$span = [double[]]@(
    [Math]::Max(0.000001, $max[0] - $min[0]),
    [Math]::Max(0.000001, $max[1] - $min[1]),
    [Math]::Max(0.000001, $max[2] - $min[2])
)

function Map-Point {
    param([double[]]$Point)
    return ,([double[]]@(
        (($Point[2] - $min[2]) / $span[2]),
        (-1.0 + (($Point[1] - $min[1]) / $span[1]) * 3.0),
        (-1.0 + (($Point[0] - $min[0]) / $span[0]) * 3.0)
    ))
}

function Map-Normal {
    param([double[]]$Normal)
    $nx = [double]$Normal[0]
    $ny = [double]$Normal[1]
    $nz = [double]$Normal[2]
    $sx = [double]$span[0]
    $sy = [double]$span[1]
    $sz = [double]$span[2]
    $x = $nz * (1.0 / $sz)
    $y = $ny * (1.0 / $sy)
    $z = $nx * (1.0 / $sx)
    $length = [Math]::Sqrt($x * $x + $y * $y + $z * $z)
    if ($length -lt 0.0000001) {
        return ,([double[]]@(0, 1, 0))
    }
    $invLength = 1.0 / [double]$length
    $outX = ([double]$x) * $invLength
    $outY = ([double]$y) * $invLength
    $outZ = ([double]$z) * $invLength
    return ,([double[]]@($outX, $outY, $outZ))
}

$faceDefs = @{
    north = @{ corners = @(0, 2, 6, 4); normal = [double[]]@(0, 0, -1) }
    south = @{ corners = @(1, 5, 7, 3); normal = [double[]]@(0, 0, 1) }
    west = @{ corners = @(0, 1, 3, 2); normal = [double[]]@(-1, 0, 0) }
    east = @{ corners = @(4, 6, 7, 5); normal = [double[]]@(1, 0, 0) }
    down = @{ corners = @(0, 4, 5, 1); normal = [double[]]@(0, -1, 0) }
    up = @{ corners = @(2, 3, 7, 6); normal = [double[]]@(0, 1, 0) }
}

$boundsMin = [double[]]@(999999.0, 999999.0, 999999.0)
$boundsMax = [double[]]@(-999999.0, -999999.0, -999999.0)
$quads = [System.Collections.Generic.List[object]]::new()

foreach ($element in $data.elements) {
    $origin = if ($null -eq $element.origin) { [double[]]@(0, 0, 0) } else { [double[]]$element.origin }
    $rotation = if ($null -eq $element.rotation) { [double[]]@(0, 0, 0) } else { [double[]]$element.rotation }
    $x0 = [Math]::Min([double]$element.from[0], [double]$element.to[0])
    $x1 = [Math]::Max([double]$element.from[0], [double]$element.to[0])
    $y0 = [Math]::Min([double]$element.from[1], [double]$element.to[1])
    $y1 = [Math]::Max([double]$element.from[1], [double]$element.to[1])
    $z0 = [Math]::Min([double]$element.from[2], [double]$element.to[2])
    $z1 = [Math]::Max([double]$element.from[2], [double]$element.to[2])

    $rawCorners = @(
        [double[]]@($x0, $y0, $z0), [double[]]@($x0, $y0, $z1),
        [double[]]@($x0, $y1, $z0), [double[]]@($x0, $y1, $z1),
        [double[]]@($x1, $y0, $z0), [double[]]@($x1, $y0, $z1),
        [double[]]@($x1, $y1, $z0), [double[]]@($x1, $y1, $z1)
    )
    $corners = @($rawCorners | ForEach-Object {
        Normalize-Point (Map-Point -Point (Rotate-Point -Point $_ -Origin $origin -Rotation $rotation))
    })

    foreach ($faceProp in $element.faces.PSObject.Properties) {
        $name = $faceProp.Name
        if (-not $faceDefs.ContainsKey($name)) {
            continue
        }
        $face = $faceProp.Value
        if ($null -eq $face.texture -or [int]$face.texture -lt 0) {
            continue
        }
        if ($IncludeTextures.Count -gt 0 -and -not ($IncludeTextures -contains [int]$face.texture)) {
            continue
        }

        $indices = $faceDefs[$name].corners
        $vertices = @($indices | ForEach-Object { Normalize-Point $corners[$_] })
        foreach ($point in $vertices) {
            for ($i = 0; $i -lt 3; $i++) {
                if ($point[$i] -lt $boundsMin[$i]) { $boundsMin[$i] = $point[$i] }
                if ($point[$i] -gt $boundsMax[$i]) { $boundsMax[$i] = $point[$i] }
            }
        }

        $textureSize = if ($textureSizes.ContainsKey([int]$face.texture)) {
            $textureSizes[[int]$face.texture]
        } else {
            [double[]]@($resW, $resH)
        }
        $uv = if ($null -eq $face.uv) {
            [double[]]@(0, 0, $textureSize[0], $textureSize[1])
        } else {
            [double[]]$face.uv
        }
        $u0 = [double]$uv[0]
        $v0 = [double]$uv[1]
        $u1 = [double]$uv[2]
        $v1 = [double]$uv[3]
        $tw = [double]$textureSize[0]
        $th = [double]$textureSize[1]
        $invTw = [double](1.0 / $tw)
        $invTh = [double](1.0 / $th)
        $uu0 = [double]($u0 * $invTw)
        $vv0 = [double]($v0 * $invTh)
        $uu1 = [double]($u1 * $invTw)
        $vv1 = [double]($v1 * $invTh)
        $uvs = [double[]]@(
            $uu0, $vv0,
            $uu1, $vv0,
            $uu1, $vv1,
            $uu0, $vv1
        )

        $normal = Map-Normal -Normal (Rotate-Vector -Vector $faceDefs[$name].normal -Rotation $rotation)
        $actual = Cross-Point `
            -A (Subtract-Point -A $vertices[1] -B $vertices[0]) `
            -B (Subtract-Point -A $vertices[2] -B $vertices[0])
        if ((Dot-Point -A $actual -B $normal) -lt 0) {
            [array]::Reverse($vertices)
            $uvs = [double[]]@($uvs[6], $uvs[7], $uvs[4], $uvs[5], $uvs[2], $uvs[3], $uvs[0], $uvs[1])
        }

        $flatVertices = [System.Collections.Generic.List[double]]::new()
        foreach ($vertex in $vertices) {
            foreach ($component in $vertex) {
                $flatVertices.Add([Math]::Round($component, 6))
            }
        }

        $quads.Add([ordered]@{
            texture = [int]$face.texture
            vertices = @($flatVertices)
            uv = Round-Array $uvs
            normal = Round-Array $normal
        })
    }
}

if ($quads.Count -eq 0) {
    $boundsMin = [double[]]@(0, 0, 0)
    $boundsMax = [double[]]@(0, 0, 0)
}

$output = [ordered]@{
    source = $InputPath
    texture_size = @([int]$resW, [int]$resH)
    quad_count = $quads.Count
    target_size_blocks = @(3, 3, 1)
    source_bounds = [ordered]@{
        min = Round-Array $min
        max = Round-Array $max
    }
    local_bounds = [ordered]@{
        min = Round-Array $boundsMin
        max = Round-Array $boundsMax
    }
    quads = @($quads)
}

$directory = Split-Path -Parent $OutputPath
if ($directory -and -not (Test-Path -LiteralPath $directory)) {
    New-Item -ItemType Directory -Path $directory | Out-Null
}

$output | ConvertTo-Json -Depth 20 -Compress | Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Host "Wrote $OutputPath with $($quads.Count) quads"
