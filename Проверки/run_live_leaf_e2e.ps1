param(
    [string]$BaseUrl = $env:BACKEND_BASE_URL,
    [string]$BearerToken = $env:BACKEND_BEARER_TOKEN,
    [string]$OutDir = "Проверки/out",
    [ValidateSet("key", "all")]
    [string]$CategoryMode = "all",
    [switch]$StrictFailOnReasonCodes,
    [switch]$AllowReasonCodes,
    [switch]$AllowAlreadyExists
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    throw "Set BACKEND_BASE_URL or pass -BaseUrl."
}
if ([string]::IsNullOrWhiteSpace($BearerToken)) {
    throw "Set BACKEND_BEARER_TOKEN or pass -BearerToken."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$taxonomyRoot = Join-Path $repoRoot "domain/src/main/resources/taxonomy/stage2/2.2"
$profilesPattern = "profiles.*.json"
$valueDictPath = Join-Path $taxonomyRoot "_registry/value_dictionaries.json"

if (-not [System.IO.Path]::IsPathRooted($OutDir)) {
    $OutDir = Join-Path $repoRoot $OutDir
}
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

$nowTag = Get-Date -Format "yyyyMMdd_HHmmss"
$reportPath = Join-Path $OutDir "live_leaf_e2e_${CategoryMode}_$nowTag.json"

function Get-ProfileIndex {
    param([string]$TaxonomyRootPath)

    $profilesByCode = @{}
    $allCodes = New-Object 'System.Collections.Generic.HashSet[string]'
    $parentCodes = New-Object 'System.Collections.Generic.HashSet[string]'

    $files = Get-ChildItem -Path $TaxonomyRootPath -Recurse -Filter $profilesPattern
    foreach ($file in $files) {
        $arr = Get-Content -Raw $file.FullName | ConvertFrom-Json
        foreach ($profile in @($arr)) {
            $category = $profile.category
            if ($null -eq $category) { continue }
            $codeRaw = [string]$category.code
            if ([string]::IsNullOrWhiteSpace($codeRaw)) { continue }
            $code = $codeRaw.Trim().ToUpperInvariant()
            $allCodes.Add($code) | Out-Null

            $parentRaw = [string]$category.parentCode
            if (-not [string]::IsNullOrWhiteSpace($parentRaw)) {
                $parentCodes.Add($parentRaw.Trim().ToUpperInvariant()) | Out-Null
            }

            $attributesByCode = @{}
            foreach ($attr in @($profile.attributes)) {
                $attrCodeRaw = [string]$attr.code
                if ([string]::IsNullOrWhiteSpace($attrCodeRaw)) { continue }
                $attrCode = $attrCodeRaw.Trim().ToLowerInvariant()
                $attributesByCode[$attrCode] = [PSCustomObject]@{
                    dataType = ([string]$attr.dataType).Trim().ToUpperInvariant()
                    valueDictCode = ([string]$attr.valueDictCode).Trim().ToLowerInvariant()
                }
            }

            $required = @(
                @($profile.categoryAttributes) |
                    Where-Object { $_.isRequiredForCategory -eq $true } |
                    ForEach-Object { ([string]$_.attributeCode).Trim().ToLowerInvariant() } |
                    Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
            )

            $profilesByCode[$code] = [PSCustomObject]@{
                categoryCode = $code
                requiredAttributeCodes = $required
                attributesByCode = $attributesByCode
            }
        }
    }

    $leafCategoryCodes = @($allCodes | Where-Object { -not $parentCodes.Contains($_) } | Sort-Object)

    return [PSCustomObject]@{
        profilesByCode = $profilesByCode
        leafCategoryCodes = $leafCategoryCodes
    }
}

function Get-DictDefaults {
    param([string]$Path)

    $defaults = @{}
    if (-not (Test-Path $Path)) {
        return $defaults
    }

    $json = Get-Content -Raw $Path | ConvertFrom-Json
    foreach ($dict in @($json.dictionaries)) {
        $attributeCode = ([string]$dict.attributeCode).Trim().ToLowerInvariant()
        if ([string]::IsNullOrWhiteSpace($attributeCode)) { continue }

        $firstEntry = @($dict.entries) | Select-Object -First 1
        if ($null -eq $firstEntry) { continue }

        $valueCode = ([string]$firstEntry.valueCode).Trim()
        if ([string]::IsNullOrWhiteSpace($valueCode)) { continue }

        if (-not $defaults.ContainsKey($attributeCode)) {
            $defaults[$attributeCode] = $valueCode
        }
    }

    return $defaults
}

$baseAttributeDefaults = @{
    "condition" = "NEW"
    "brand" = "E2EBrand"
    "model" = "E2EModel"
    "product_name" = "E2E Product"
    "ingredient_list" = "water,salt"
    "allergen_profile" = "NONE"
    "storage_regime" = "AMBIENT"
    "shelf_life_days" = "30"
    "portion_size_gram" = "300"
    "restaurant_name" = "E2E Kitchen"
    "delivery_channel" = "delivery"
    "price_tier" = "MEDIUM"
}

function New-KeyCategoryAttributes {
    param([string]$CategoryCode)

    switch ($CategoryCode) {
        "TECH.PHONES" {
            return @{
                condition = "NEW"
                memory_gb = "256"
                ram_gb = "8"
                network_type = "5G"
            }
        }
        "TECH.LAPTOPS" {
            return @{
                condition = "NEW"
                cpu_family = "INTEL_CORE_I7"
                ram_gb = "16"
                screen_size_inch = "15.6"
                storage_gb = "512"
                storage_type = "SSD"
            }
        }
        "TECH.TV_VIDEO" {
            return @{
                condition = "NEW"
                screen_size_inch = "55"
            }
        }
        "APPL.SMALL" {
            return @{
                condition = "NEW"
                coffee_type = "CAPSULE"
                power_watt = "1200"
                used_for = "HOME"
            }
        }
        "APPL.MAJOR" {
            return @{
                condition = "NEW"
                power_watt = "2200"
            }
        }
        "AUTO.PARTS" {
            return @{
                condition = "NEW"
                material = "STEEL"
            }
        }
        "AUTO.TIRES_WHEELS" {
            return @{
                condition = "NEW"
                diameter_cm = "45"
            }
        }
        "FOOD.READY_MEALS" {
            return @{
                allergen_profile = "NONE"
                cuisine_type = "JAPANESE"
                ingredient_list = "rice,salmon,seaweed"
                portion_size_gram = "350"
                restaurant_name = "E2E Kitchen"
                shelf_life_days = "3"
                storage_regime = "CHILLED"
            }
        }
        "FOOD.GROCERIES" {
            return @{
                allergen_profile = "NONE"
                form_factor = "POWDER"
                ingredient_list = "oats"
                product_name = "E2E Oat Mix"
                shelf_life_days = "120"
                storage_regime = "AMBIENT"
            }
        }
        "FOOD.DRINKS" {
            return @{
                allergen_profile = "NONE"
                flavor = "VANILLA"
                ingredient_list = "water,vanilla"
                product_name = "E2E Vanilla Drink"
                shelf_life_days = "20"
                storage_regime = "CHILLED"
            }
        }
        "HOME.FURNITURE" {
            return @{
                condition = "NEW"
                material = "WOOD"
            }
        }
        "BEAUTY.SKINCARE" {
            return @{
                form_factor = "LIQUID"
                product_name = "E2E Skin Tonic"
                volume_ml = "200"
            }
        }
        default { return @{} }
    }
}

function Resolve-DefaultAttributeValue {
    param(
        [string]$AttributeCode,
        $AttributeMeta,
        [hashtable]$DictDefaults
    )

    $code = $AttributeCode.Trim().ToLowerInvariant()
    if ($baseAttributeDefaults.ContainsKey($code)) {
        return [string]$baseAttributeDefaults[$code]
    }

    $dictCode = ([string]$AttributeMeta.valueDictCode).Trim().ToLowerInvariant()
    if (-not [string]::IsNullOrWhiteSpace($dictCode) -and $DictDefaults.ContainsKey($dictCode)) {
        return [string]$DictDefaults[$dictCode]
    }

    if ($DictDefaults.ContainsKey($code)) {
        return [string]$DictDefaults[$code]
    }

    $dataType = ([string]$AttributeMeta.dataType).Trim().ToUpperInvariant()
    switch ($dataType) {
        "BOOL" { return "true" }
        "INT" { return "1" }
        "LONG" { return "1" }
        "FLOAT" { return "1.0" }
        "DOUBLE" { return "1.0" }
        "DECIMAL" { return "1.0" }
        "STRING" { return "E2E $code" }
        default { return $null }
    }
}

function New-AttributesForCategory {
    param(
        [string]$CategoryCode,
        $Profile,
        [hashtable]$DictDefaults
    )

    $attributes = @{}

    $manual = New-KeyCategoryAttributes -CategoryCode $CategoryCode
    foreach ($entry in $manual.GetEnumerator()) {
        $attributes[$entry.Key] = [string]$entry.Value
    }

    if ($null -eq $Profile) {
        return $attributes
    }

    foreach ($requiredCode in @($Profile.requiredAttributeCodes)) {
        if ([string]::IsNullOrWhiteSpace($requiredCode)) { continue }
        if ($attributes.ContainsKey($requiredCode)) { continue }
        if ($requiredCode -eq "brand" -or $requiredCode -eq "model" -or $requiredCode -eq "product_name") { continue }

        $meta = $Profile.attributesByCode[$requiredCode]
        if ($null -eq $meta) {
            $meta = [PSCustomObject]@{ dataType = "STRING"; valueDictCode = "" }
        }

        $value = Resolve-DefaultAttributeValue -AttributeCode $requiredCode -AttributeMeta $meta -DictDefaults $DictDefaults
        if (-not [string]::IsNullOrWhiteSpace([string]$value)) {
            $attributes[$requiredCode] = [string]$value
        }
    }

    return $attributes
}

$profileIndex = Get-ProfileIndex -TaxonomyRootPath $taxonomyRoot
$dictDefaults = Get-DictDefaults -Path $valueDictPath

$keyLeafCategories = @(
    "TECH.PHONES",
    "TECH.LAPTOPS",
    "TECH.TV_VIDEO",
    "APPL.SMALL",
    "APPL.MAJOR",
    "AUTO.PARTS",
    "AUTO.TIRES_WHEELS",
    "FOOD.READY_MEALS",
    "FOOD.GROCERIES",
    "FOOD.DRINKS",
    "HOME.FURNITURE",
    "BEAUTY.SKINCARE"
)

$leafCategories = if ($CategoryMode -eq "all") {
    @($profileIndex.leafCategoryCodes)
} else {
    @($keyLeafCategories)
}

if ($leafCategories.Count -eq 0) {
    throw "No leaf categories resolved for mode '$CategoryMode'."
}

$strictReasonCodes = $StrictFailOnReasonCodes.IsPresent -or (-not $AllowReasonCodes.IsPresent)
$acceptedStatuses = if ($AllowAlreadyExists.IsPresent) { @("CREATED", "ALREADY_EXISTS") } else { @("CREATED") }

Write-Host "Category mode: $CategoryMode. Leaf categories: $($leafCategories.Count). Strict reason-codes: $strictReasonCodes. Accept statuses: $($acceptedStatuses -join ',')"

$headers = @{
    Authorization = if ($BearerToken.StartsWith("Bearer ")) { $BearerToken } else { "Bearer $BearerToken" }
    "Content-Type" = "application/json"
}

$results = @()
$failed = $false

foreach ($categoryCode in $leafCategories) {
    $slug = ($categoryCode.ToLowerInvariant() -replace "[^a-z0-9]+", "_")
    $profile = $profileIndex.profilesByCode[$categoryCode]
    $attributes = New-AttributesForCategory -CategoryCode $categoryCode -Profile $profile -DictDefaults $dictDefaults

    $payload = @{
        userId = "live-e2e"
        title = "Live E2E $categoryCode"
        categoryCode = $categoryCode
        brand = "E2EBrand"
        model = "E2EModel"
        priceValue = 19999.0
        currency = "RUB"
        imageUrls = @("https://picsum.photos/seed/$slug/640/480")
        attributes = $attributes
        source = @{
            sourceType = "AVITO"
            url = "https://www.avito.ru/moskva/test/${slug}_$nowTag"
        }
    }

    $url = "$($BaseUrl.TrimEnd('/'))/api/offers/tracked"
    try {
        $body = $payload | ConvertTo-Json -Depth 30
        $response = Invoke-RestMethod -Method POST -Uri $url -Headers $headers -Body $body
        $status = [string]$response.status
        $reasonCodes = @($response.reasonCodes)

        $okStatus = $acceptedStatuses -contains $status
        $okReasonCodes = if ($strictReasonCodes) { $reasonCodes.Count -eq 0 } else { $true }
        $ok = $okStatus -and $okReasonCodes

        if (-not $ok) { $failed = $true }

        $results += [PSCustomObject]@{
            categoryCode = $categoryCode
            status = $status
            reasonCodes = $reasonCodes
            strictReasonCodes = $strictReasonCodes
            attributesCount = $attributes.Count
            requiredAttributeCount = @($profile.requiredAttributeCodes).Count
            ok = $ok
        }
        Write-Host "[$categoryCode] status=$status reasons=$($reasonCodes -join ',') attrs=$($attributes.Count)"
    } catch {
        $failed = $true
        $results += [PSCustomObject]@{
            categoryCode = $categoryCode
            status = "REQUEST_FAILED"
            reasonCodes = @($_.Exception.Message)
            strictReasonCodes = $strictReasonCodes
            attributesCount = $attributes.Count
            requiredAttributeCount = @($profile.requiredAttributeCodes).Count
            ok = $false
        }
        Write-Warning "[$categoryCode] request failed: $($_.Exception.Message)"
    }
}

$results | ConvertTo-Json -Depth 30 | Set-Content -Path $reportPath -Encoding UTF8
Write-Host "Live leaf e2e report: $reportPath"

if ($failed) {
    throw "Live leaf e2e failed for one or more categories."
}

Write-Host "Live leaf e2e passed for $($leafCategories.Count) categories."
