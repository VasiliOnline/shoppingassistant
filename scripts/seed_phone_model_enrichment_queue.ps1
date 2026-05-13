param(
    [string]$BackendUrl = "http://127.0.0.1:8081",
    [string]$Password = "CatalogSeed123!",
    [string]$ReadyBrand = "Samsung",
    [string]$ReadyModel = "Galaxy S25 Edge",
    [string]$ReadyModelLine = "Galaxy S25",
    [string]$MonitoringBrand = "Oukitel",
    [string]$MonitoringModel = "WP99 Ultra",
    [string]$MonitoringModelLine = "WP99",
    [string]$CategoryCode = "TECH.PHONES"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Post-Json([string]$url, $body, [hashtable]$headers = @{}) {
    $json = $body | ConvertTo-Json -Depth 10
    return Invoke-RestMethod -Uri $url -Method Post -ContentType "application/json" -Headers $headers -Body $json
}

function Get-Json([string]$url) {
    return Invoke-RestMethod -Uri $url -Method Get
}

function Register-Or-LoginUser(
    [string]$baseUrl,
    [string]$email,
    [string]$password,
    [string]$displayName
) {
    try {
        return Post-Json "$baseUrl/api/auth/register" @{
            email = $email
            password = $password
            displayName = $displayName
        }
    } catch {
        return Post-Json "$baseUrl/api/auth/login" @{
            email = $email
            password = $password
        }
    }
}

function New-TrackedOfferPayload(
    [string]$userToken,
    [string]$title,
    [string]$brand,
    [string]$model,
    [string]$modelLine,
    [double]$priceValue,
    [string]$sourceUrl,
    [string]$imageUrl,
    [double]$categoryConfidence
) {
    return @{
        userId = "0"
        title = $title
        categoryCode = $CategoryCode
        categoryConfidence = $categoryConfidence
        parserVersion = "seed_phone_model_enrichment_queue_v1"
        brand = $brand
        model = $model
        priceValue = $priceValue
        currency = "RUB"
        imageUrls = @($imageUrl)
        description = "$title · seeded for model enrichment queue"
        attributes = @{
            model_line = $modelLine
            condition = "new"
            network_type = "5g"
            memory_gb = "256"
        }
        source = @{
            sourceType = "AVITO"
            url = $sourceUrl
            canonicalUrl = $sourceUrl
            listingId = [System.Guid]::NewGuid().ToString("N")
            domainName = "avito.ru"
            sourceIconUrl = "https://www.avito.ru/favicon.ico"
        }
    }
}

function Create-TrackedOffer(
    [string]$baseUrl,
    [string]$token,
    $payload
) {
    $headers = @{
        Authorization = "Bearer $token"
    }
    return Post-Json "$baseUrl/api/offers/tracked" $payload $headers
}

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$seedUsers = @(
    @{
        email = "catalog.enrichment.seed.a.$suffix@example.com"
        displayName = "Catalog Seed A $suffix"
    },
    @{
        email = "catalog.enrichment.seed.b.$suffix@example.com"
        displayName = "Catalog Seed B $suffix"
    }
)

Write-Host "Register/login 2 seed users..." -ForegroundColor Cyan
$authA = Register-Or-LoginUser -baseUrl $BackendUrl -email $seedUsers[0].email -password $Password -displayName $seedUsers[0].displayName
$authB = Register-Or-LoginUser -baseUrl $BackendUrl -email $seedUsers[1].email -password $Password -displayName $seedUsers[1].displayName

if (-not $authA.token -or -not $authB.token) {
    throw "Seed auth failed: token missing."
}

$readyOffers = @(
    @{
        token = $authA.token
        title = "$ReadyBrand $ReadyModel 256GB"
        brand = $ReadyBrand
        model = $ReadyModel
        modelLine = $ReadyModelLine
        priceValue = 79990
        sourceUrl = "https://www.avito.ru/moskva/telefony/$($ReadyBrand.ToLower())_$($ReadyModel.ToLower().Replace(' ','_'))_${suffix}_a1"
        imageUrl = "https://images.example.com/$suffix/ready-a1.jpg"
        categoryConfidence = 0.96
    },
    @{
        token = $authA.token
        title = "$ReadyBrand $ReadyModel 512GB"
        brand = $ReadyBrand
        model = $ReadyModel
        modelLine = $ReadyModelLine
        priceValue = 86990
        sourceUrl = "https://www.avito.ru/spb/telefony/$($ReadyBrand.ToLower())_$($ReadyModel.ToLower().Replace(' ','_'))_${suffix}_a2"
        imageUrl = "https://images.example.com/$suffix/ready-a2.jpg"
        categoryConfidence = 0.94
    },
    @{
        token = $authB.token
        title = "$ReadyBrand $ReadyModel sealed"
        brand = $ReadyBrand
        model = $ReadyModel
        modelLine = $ReadyModelLine
        priceValue = 82490
        sourceUrl = "https://www.avito.ru/kazan/telefony/$($ReadyBrand.ToLower())_$($ReadyModel.ToLower().Replace(' ','_'))_${suffix}_b1"
        imageUrl = "https://images.example.com/$suffix/ready-b1.jpg"
        categoryConfidence = 0.95
    }
)

$monitoringOffers = @(
    @{
        token = $authA.token
        title = "$MonitoringBrand $MonitoringModel 256GB"
        brand = $MonitoringBrand
        model = $MonitoringModel
        modelLine = $MonitoringModelLine
        priceValue = 31990
        sourceUrl = "https://www.avito.ru/moskva/telefony/$($MonitoringBrand.ToLower())_$($MonitoringModel.ToLower().Replace(' ','_'))_${suffix}_a1"
        imageUrl = "https://images.example.com/$suffix/monitoring-a1.jpg"
        categoryConfidence = 0.93
    },
    @{
        token = $authB.token
        title = "$MonitoringBrand $MonitoringModel 512GB"
        brand = $MonitoringBrand
        model = $MonitoringModel
        modelLine = $MonitoringModelLine
        priceValue = 34990
        sourceUrl = "https://www.avito.ru/ekb/telefony/$($MonitoringBrand.ToLower())_$($MonitoringModel.ToLower().Replace(' ','_'))_${suffix}_b1"
        imageUrl = "https://images.example.com/$suffix/monitoring-b1.jpg"
        categoryConfidence = 0.91
    }
)

Write-Host "Create 3 READY-seed tracked offers..." -ForegroundColor Cyan
foreach ($offer in $readyOffers) {
    $payload = New-TrackedOfferPayload `
        -userToken $offer.token `
        -title $offer.title `
        -brand $offer.brand `
        -model $offer.model `
        -modelLine $offer.modelLine `
        -priceValue $offer.priceValue `
        -sourceUrl $offer.sourceUrl `
        -imageUrl $offer.imageUrl `
        -categoryConfidence $offer.categoryConfidence
    $result = Create-TrackedOffer -baseUrl $BackendUrl -token $offer.token -payload $payload
    Write-Host "READY seed -> status=$($result.status) offerId=$($result.offerId)" -ForegroundColor Green
}

Write-Host "Create 2 MONITORING-seed tracked offers..." -ForegroundColor Cyan
foreach ($offer in $monitoringOffers) {
    $payload = New-TrackedOfferPayload `
        -userToken $offer.token `
        -title $offer.title `
        -brand $offer.brand `
        -model $offer.model `
        -modelLine $offer.modelLine `
        -priceValue $offer.priceValue `
        -sourceUrl $offer.sourceUrl `
        -imageUrl $offer.imageUrl `
        -categoryConfidence $offer.categoryConfidence
    $result = Create-TrackedOffer -baseUrl $BackendUrl -token $offer.token -payload $payload
    Write-Host "MONITORING seed -> status=$($result.status) offerId=$($result.offerId)" -ForegroundColor Yellow
}

Start-Sleep -Seconds 2

$queue = Get-Json "$BackendUrl/api/catalog/governance/model-enrichment-queue?categoryCode=$([uri]::EscapeDataString($CategoryCode))&limit=50"

$relevant = @($queue | Where-Object {
    $_.modelRaw -in @($ReadyModel, $MonitoringModel)
})

Write-Host ""
Write-Host "Relevant enrichment candidates:" -ForegroundColor Cyan
$relevant |
    Select-Object id, status, brandRaw, modelRaw, observedCount, distinctSellerCount, officialSourceCode, officialEndpointCode, reasonCodes |
    Format-Table -AutoSize

Write-Host ""
Write-Host "Done. Open admin-web -> Enrichment and press reload if the section was already open." -ForegroundColor Green
