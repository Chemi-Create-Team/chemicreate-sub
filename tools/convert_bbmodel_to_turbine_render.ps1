param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [int[]]$IncludeTextures = @(),

    [double]$ScaleOverride = 0.0,

    [switch]$FitAxisToBlock
)

$ErrorActionPreference = 'Stop'

$data = Get-Content -Raw -Path $InputPath | ConvertFrom-Json
$resW = [double]$data.resolution.width
$resH = [double]$data.resolution.height
$textureSizes = @{}
foreach ($texture in $data.textures) {
    $textureSizes[[int]$texture.id] = [double[]]@([double]$texture.width, [double]$texture.height)
}

function Rotate-Point {
    param([double[]]$Point, [double[]]$Origin, [double[]]$Rotation)

    $x = $Point[0] - $Origin[0]
    $y = $Point[1] - $Origin[1]
    $z = $Point[2] - $Origin[2]

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

    return ,([double[]]@(($x + $Origin[0]), ($y + $Origin[1]), ($z + $Origin[2])))
}

function Rotate-Vector {
    param([double[]]$Vector, [double[]]$Rotation)

    $x = $Vector[0]
    $y = $Vector[1]
    $z = $Vector[2]

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

    return ,([double[]]@($x, $y, $z))
}

function Map-Point {
    param([double[]]$Point, [double]$Scale)
    return ,([double[]]@((0.5 + $Point[2] / $Scale), (0.5 + $Point[1] / $Scale), (0.5 + $Point[0] / $Scale)))
}

function Map-Normal {
    param([double[]]$Normal)
    $x = $Normal[2]
    $y = $Normal[1]
    $z = $Normal[0]
    $length = [Math]::Sqrt($x * $x + $y * $y + $z * $z)
    if ($length -lt 0.0000001) {
        return ,([double[]]@(0.0, 1.0, 0.0))
    }
    return ,([double[]]@(($x / $length), ($y / $length), ($z / $length)))
}

function Subtract-Point {
    param([double[]]$A, [double[]]$B)
    return ,([double[]]@(($A[0] - $B[0]), ($A[1] - $B[1]), ($A[2] - $B[2])))
}

function Cross-Point {
    param([double[]]$A, [double[]]$B)
    return ,([double[]]@(
        ($A[1] * $B[2] - $A[2] * $B[1]),
        ($A[2] * $B[0] - $A[0] * $B[2]),
        ($A[0] * $B[1] - $A[1] * $B[0])
    ))
}

function Dot-Point {
    param([double[]]$A, [double[]]$B)
    $A[0] * $B[0] + $A[1] * $B[1] + $A[2] * $B[2]
}

function Round-Array {
    param([double[]]$Values)
    @($Values | ForEach-Object { [Math]::Round($_, 6) })
}

$allPoints = [System.Collections.Generic.List[double[]]]::new()
foreach ($element in $data.elements) {
    $origin = if ($null -eq $element.origin) { [double[]]@(0, 0, 0) } else { [double[]]$element.origin }
    $rotation = if ($null -eq $element.rotation) { [double[]]@(0, 0, 0) } else { [double[]]$element.rotation }
    $xs = @([double]$element.from[0], [double]$element.to[0])
    $ys = @([double]$element.from[1], [double]$element.to[1])
    $zs = @([double]$element.from[2], [double]$element.to[2])
    foreach ($x in $xs) {
        foreach ($y in $ys) {
            foreach ($z in $zs) {
                $allPoints.Add((Rotate-Point -Point ([double[]]@($x, $y, $z)) -Origin $origin -Rotation $rotation))
            }
        }
    }
}

$maxRadius = 0.0
foreach ($point in $allPoints) {
    $radius = [Math]::Sqrt($point[0] * $point[0] + $point[1] * $point[1])
    if ($radius -gt $maxRadius) {
        $maxRadius = $radius
    }
}
if ($ScaleOverride -gt 0.0) {
    $maxRadius = $ScaleOverride
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
        Map-Point -Point (Rotate-Point -Point $_ -Origin $origin -Rotation $rotation) -Scale $maxRadius
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
        $vertices = @($indices | ForEach-Object { $corners[$_] })
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
        $textureWidth = $textureSize[0]
        $textureHeight = $textureSize[1]
        $uv = if ($null -eq $face.uv) {
            [double[]]@(0, 0, $textureWidth, $textureHeight)
        } else {
            [double[]]$face.uv
        }
        $uvs = [double[]]@(
            ($uv[0] / $textureWidth), ($uv[1] / $textureHeight),
            ($uv[2] / $textureWidth), ($uv[1] / $textureHeight),
            ($uv[2] / $textureWidth), ($uv[3] / $textureHeight),
            ($uv[0] / $textureWidth), ($uv[3] / $textureHeight)
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

if ($FitAxisToBlock -and $quads.Count -gt 0) {
    $axisMin = 999999.0
    $axisMax = -999999.0
    foreach ($quad in $quads) {
        for ($i = 0; $i -lt $quad.vertices.Count; $i += 3) {
            $value = [double]$quad.vertices[$i]
            if ($value -lt $axisMin) { $axisMin = $value }
            if ($value -gt $axisMax) { $axisMax = $value }
        }
    }

    $axisLength = $axisMax - $axisMin
    if ($axisLength -gt 1.0) {
        $scale = 1.0 / $axisLength
        foreach ($quad in $quads) {
            for ($i = 0; $i -lt $quad.vertices.Count; $i += 3) {
                $quad.vertices[$i] = [Math]::Round((([double]$quad.vertices[$i] - 0.5) * $scale) + 0.5, 6)
            }
        }
    }
}

if ($quads.Count -gt 0) {
    $boundsMin = [double[]]@(999999.0, 999999.0, 999999.0)
    $boundsMax = [double[]]@(-999999.0, -999999.0, -999999.0)
    foreach ($quad in $quads) {
        for ($i = 0; $i -lt $quad.vertices.Count; $i += 3) {
            for ($j = 0; $j -lt 3; $j++) {
                $value = [double]$quad.vertices[$i + $j]
                if ($value -lt $boundsMin[$j]) { $boundsMin[$j] = $value }
                if ($value -gt $boundsMax[$j]) { $boundsMax[$j] = $value }
            }
        }
    }
}

$output = [ordered]@{
    source = $InputPath
    texture_size = @([int]$resW, [int]$resH)
    quad_count = $quads.Count
    radius_blocks = 1.0
    source_radius = [Math]::Round($maxRadius, 6)
    local_bounds = [ordered]@{
        min = Round-Array $boundsMin
        max = Round-Array $boundsMax
    }
    quads = @($quads)
}

$directory = Split-Path -Parent $OutputPath
if ($directory -and -not (Test-Path $directory)) {
    New-Item -ItemType Directory -Path $directory | Out-Null
}

$output | ConvertTo-Json -Depth 20 -Compress | Set-Content -Path $OutputPath -Encoding UTF8
Write-Host "Wrote $OutputPath with $($quads.Count) quads, source radius $([Math]::Round($maxRadius, 6))"
