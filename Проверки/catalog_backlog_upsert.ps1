# Required secrets (set in terminal/CI, never hardcode in repo):
# $env:PG_CONN='host=<host> port=5432 dbname=<db> user=<user> password=<password> sslmode=require'
# $env:JIRA_BASE_URL='https://<org>.atlassian.net'
# $env:JIRA_EMAIL='<jira-email>'
# $env:JIRA_API_TOKEN='<jira-api-token>'
# $env:JIRA_PROJECT_KEY='<project-key>'
# $env:JIRA_ISSUE_TYPE='Task'


param(
    [ValidateSet("jira", "linear")]
    [string]$Provider = "jira",
    [string]$PgConn = $env:PG_CONN,
    [string]$PsqlPath = "psql",
    [int]$MinIssueCount = 1,
    [switch]$Apply
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($PgConn)) {
    throw "Set PG_CONN, example: export/setx PG_CONN 'host=... port=5432 dbname=... user=... password=... sslmode=require'"
}

function Invoke-PsqlCsv {
    param([string]$Sql)

    $errFile = New-TemporaryFile
    try {
        $raw = & $PsqlPath $PgConn --csv -X -v ON_ERROR_STOP=1 -c $Sql 2> $errFile.FullName
        if ($LASTEXITCODE -ne 0) {
            $err = (Get-Content $errFile.FullName -Raw)
            throw "psql failed: $err"
        }

        $text = ($raw | Out-String).Trim()
        if ([string]::IsNullOrWhiteSpace($text)) { return @() }
        return ($text | ConvertFrom-Csv)
    } finally {
        Remove-Item $errFile.FullName -Force -ErrorAction SilentlyContinue
    }
}

function Get-KeyHash {
    param([string]$Text)
    $sha1 = [System.Security.Cryptography.SHA1]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        $hash = $sha1.ComputeHash($bytes)
        return ([BitConverter]::ToString($hash) -replace "-", "").ToLowerInvariant()
    } finally {
        $sha1.Dispose()
    }
}

function Get-Priority {
    param([string]$IssueType, [int]$IssueCount)
    switch ($IssueType) {
        "ZERO_RESULTS" {
            if ($IssueCount -ge 50) { return 1 } # Urgent
            return 2                             # High
        }
        "UNKNOWN_ATTRIBUTE" {
            if ($IssueCount -ge 100) { return 2 }
            return 3
        }
        "NORMALIZATION_CONFLICT" {
            if ($IssueCount -ge 100) { return 2 }
            return 3
        }
        "STAGE4_UNKNOWN_CLOSED_SET_VALUE" {
            if ($IssueCount -ge 50) { return 2 }
            return 3
        }
        "STAGE4_INCOMPATIBLE_VALUE" {
            if ($IssueCount -ge 50) { return 2 }
            return 3
        }
        default { return 3 }
    }
}

$sqlZeroResults = @"
WITH zero_results AS (
    SELECT
        t.category_code,
        s.track_id,
        s.computed_at,
        s.last_error,
        s.fail_count,
        s.explanation_json ->> 'summary' AS summary
    FROM track_top10_snapshots s
    JOIN tracks t ON t.id = s.track_id
    WHERE JSONB_ARRAY_LENGTH(COALESCE(s.items_json, '[]'::jsonb)) = 0
)
SELECT
    'ZERO_RESULTS' AS issue_type,
    COALESCE(category_code, 'UNKNOWN') AS category_code,
    COALESCE(summary, last_error, 'no-items') AS issue_key,
    COUNT(*)::int AS issue_count,
    MIN(TO_TIMESTAMP(computed_at / 1000.0))::text AS first_seen_at,
    MAX(TO_TIMESTAMP(computed_at / 1000.0))::text AS last_seen_at,
    (CURRENT_DATE + INTERVAL '3 day')::date::text AS sla_due_date
FROM zero_results
GROUP BY COALESCE(category_code, 'UNKNOWN'), COALESCE(summary, last_error, 'no-items')
ORDER BY issue_count DESC, last_seen_at DESC;
"@

$sqlUnknownAttribute = @"
WITH offer_attrs AS (
    SELECT
        p.category AS category_code,
        key AS attribute_code
    FROM offers o
    JOIN products p ON p.id = o.product_id
    CROSS JOIN LATERAL JSONB_EACH_TEXT(COALESCE(o.attributes, '{}'::jsonb))
),
known_category_attrs AS (
    SELECT category_code, attribute_code
    FROM category_attributes
)
SELECT
    'UNKNOWN_ATTRIBUTE' AS issue_type,
    oa.category_code,
    oa.attribute_code AS issue_key,
    COUNT(*)::int AS issue_count,
    NULL::text AS first_seen_at,
    NULL::text AS last_seen_at,
    (CURRENT_DATE + INTERVAL '7 day')::date::text AS sla_due_date
FROM offer_attrs oa
LEFT JOIN known_category_attrs kca
    ON kca.category_code = oa.category_code
   AND kca.attribute_code = oa.attribute_code
WHERE kca.attribute_code IS NULL
GROUP BY oa.category_code, oa.attribute_code
ORDER BY issue_count DESC, oa.category_code, oa.attribute_code;
"@

$sqlNormalizationConflict = @"
WITH offer_attrs AS (
    SELECT
        p.category AS category_code,
        key AS attribute_code,
        LOWER(BTRIM(value)) AS raw_value
    FROM offers o
    JOIN products p ON p.id = o.product_id
    CROSS JOIN LATERAL JSONB_EACH_TEXT(COALESCE(o.attributes, '{}'::jsonb))
    WHERE value IS NOT NULL AND BTRIM(value) <> ''
),
dict_tokens AS (
    SELECT LOWER(attribute_code) AS attribute_code, LOWER(canonical_code) AS token
    FROM attribute_value_dict
    UNION ALL
    SELECT LOWER(attribute_code) AS attribute_code, LOWER(canonical_value) AS token
    FROM attribute_value_dict
    UNION ALL
    SELECT LOWER(d.attribute_code) AS attribute_code, LOWER(elem.value) AS token
    FROM attribute_value_dict d
    CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS_TEXT(COALESCE(d.synonyms, '[]'::jsonb)) AS elem(value)
),
dict_attrs AS (
    SELECT DISTINCT LOWER(attribute_code) AS attribute_code
    FROM attribute_value_dict
)
SELECT
    'NORMALIZATION_CONFLICT' AS issue_type,
    oa.category_code,
    oa.attribute_code || ':' || oa.raw_value AS issue_key,
    COUNT(*)::int AS issue_count,
    NULL::text AS first_seen_at,
    NULL::text AS last_seen_at,
    (CURRENT_DATE + INTERVAL '7 day')::date::text AS sla_due_date
FROM offer_attrs oa
JOIN dict_attrs da
    ON da.attribute_code = LOWER(oa.attribute_code)
LEFT JOIN dict_tokens dt
    ON dt.attribute_code = LOWER(oa.attribute_code)
   AND dt.token = oa.raw_value
WHERE dt.token IS NULL
GROUP BY oa.category_code, oa.attribute_code, oa.raw_value
ORDER BY issue_count DESC, oa.category_code, oa.attribute_code, oa.raw_value;
"@

$sqlStage4UnknownClosedSet = @"
WITH offer_attrs AS (
    SELECT
        p.category AS category_code,
        LOWER(BTRIM(key)) AS attribute_code,
        LOWER(BTRIM(value)) AS raw_value
    FROM offers o
    JOIN products p ON p.id = o.product_id
    CROSS JOIN LATERAL JSONB_EACH_TEXT(COALESCE(o.attributes, '{}'::jsonb))
    WHERE value IS NOT NULL
      AND BTRIM(value) <> ''
),
stage4_closed_set_attrs AS (
    SELECT LOWER(attribute_code) AS attribute_code
    FROM catalog_stage4_normalization_rules
    WHERE dictionary_backed = TRUE
      AND accepts_free_text = FALSE
),
dict_tokens AS (
    SELECT LOWER(attribute_code) AS attribute_code, LOWER(canonical_code) AS token
    FROM attribute_value_dict
    UNION ALL
    SELECT LOWER(attribute_code) AS attribute_code, LOWER(canonical_value) AS token
    FROM attribute_value_dict
    UNION ALL
    SELECT LOWER(d.attribute_code) AS attribute_code, LOWER(elem.value) AS token
    FROM attribute_value_dict d
    CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS_TEXT(COALESCE(d.synonyms, '[]'::jsonb)) AS elem(value)
)
SELECT
    'STAGE4_UNKNOWN_CLOSED_SET_VALUE' AS issue_type,
    oa.category_code,
    oa.attribute_code || ':' || oa.raw_value AS issue_key,
    COUNT(*)::int AS issue_count,
    NULL::text AS first_seen_at,
    NULL::text AS last_seen_at,
    (CURRENT_DATE + INTERVAL '3 day')::date::text AS sla_due_date
FROM offer_attrs oa
JOIN stage4_closed_set_attrs s4
    ON s4.attribute_code = oa.attribute_code
LEFT JOIN dict_tokens dt
    ON dt.attribute_code = oa.attribute_code
   AND dt.token = oa.raw_value
WHERE dt.token IS NULL
GROUP BY oa.category_code, oa.attribute_code, oa.raw_value
ORDER BY issue_count DESC, oa.category_code, oa.attribute_code, oa.raw_value;
"@

$sqlStage4Incompatible = @"
WITH offer_attrs AS (
    SELECT
        p.category AS category_code,
        LOWER(BTRIM(key)) AS attribute_code,
        BTRIM(value) AS raw_value
    FROM offers o
    JOIN products p ON p.id = o.product_id
    CROSS JOIN LATERAL JSONB_EACH_TEXT(COALESCE(o.attributes, '{}'::jsonb))
    WHERE value IS NOT NULL
      AND BTRIM(value) <> ''
),
stage4_types AS (
    SELECT
        LOWER(attribute_code) AS attribute_code,
        value_type
    FROM catalog_stage4_immutable_attributes
),
typed_offer_attrs AS (
    SELECT
        oa.category_code,
        oa.attribute_code,
        oa.raw_value,
        st.value_type
    FROM offer_attrs oa
    JOIN stage4_types st
        ON st.attribute_code = oa.attribute_code
)
SELECT
    'STAGE4_INCOMPATIBLE_VALUE' AS issue_type,
    toa.category_code,
    toa.attribute_code || ':' || toa.raw_value AS issue_key,
    COUNT(*)::int AS issue_count,
    NULL::text AS first_seen_at,
    NULL::text AS last_seen_at,
    (CURRENT_DATE + INTERVAL '3 day')::date::text AS sla_due_date
FROM typed_offer_attrs toa
WHERE (toa.value_type = 'NUMBER' AND BTRIM(REGEXP_REPLACE(toa.raw_value, '\s+', '', 'g')) !~ '^-?[0-9]+([.,][0-9]+)?$')
   OR (toa.value_type = 'BOOLEAN' AND LOWER(BTRIM(toa.raw_value)) NOT IN (
        'true', 'false', '1', '0', 'yes', 'no', 'y', 'n', 'да', 'нет', 'истина', 'ложь'
   ))
GROUP BY toa.category_code, toa.attribute_code, toa.raw_value
ORDER BY issue_count DESC, toa.category_code, toa.attribute_code, toa.raw_value;
"@

$rows = @()
$rows += Invoke-PsqlCsv -Sql $sqlZeroResults
$rows += Invoke-PsqlCsv -Sql $sqlUnknownAttribute
$rows += Invoke-PsqlCsv -Sql $sqlNormalizationConflict
$rows += Invoke-PsqlCsv -Sql $sqlStage4UnknownClosedSet
$rows += Invoke-PsqlCsv -Sql $sqlStage4Incompatible

$issues = @(
    $rows | ForEach-Object {
        $externalKey = "$($_.issue_type)|$($_.category_code)|$($_.issue_key)"
        $hash = Get-KeyHash -Text $externalKey
        $hash10 = $hash.Substring(0, 10)
        $count = [int]$_.issue_count
        $priority = Get-Priority -IssueType $_.issue_type -IssueCount $count

        [PSCustomObject]@{
            IssueType   = $_.issue_type
            Category    = $_.category_code
            IssueKey    = $_.issue_key
            IssueCount  = $count
            FirstSeenAt = $_.first_seen_at
            LastSeenAt  = $_.last_seen_at
            SlaDueDate  = $_.sla_due_date
            ExternalKey = $externalKey
            Hash10      = $hash10
            Priority    = $priority
            Summary     = "[Catalog][$($_.issue_type)] $($_.category_code) #$hash10"
            Description = @"
ExternalKey: $externalKey
IssueType: $($_.issue_type)
Category: $($_.category_code)
IssueKey: $($_.issue_key)
IssueCount: $count
FirstSeenAt: $($_.first_seen_at)
LastSeenAt: $($_.last_seen_at)
SLA Due Date: $($_.sla_due_date)

Source: server/src/main/resources/db/checks/catalog_model_backlog.sql
"@
        }
    } | Where-Object { $_.IssueCount -ge $MinIssueCount }
)

# dedupe by ExternalKey
$issues = @(
    $issues | Group-Object ExternalKey | ForEach-Object {
        $_.Group | Sort-Object IssueCount -Descending | Select-Object -First 1
    }
)

Write-Host "Backlog rows: $($issues.Count). Mode: $Provider. Apply: $($Apply.IsPresent)"

# ---------- Jira ----------
function Get-JiraAuthHeader {
    param([string]$Email, [string]$ApiToken)
    $pair = "$Email`:$ApiToken"
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($pair)
    $basic = [Convert]::ToBase64String($bytes)
    return @{
        Authorization = "Basic $basic"
        Accept        = "application/json"
    }
}

function Invoke-JiraApi {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [string]$Method,
        [string]$Path,
        [object]$Body = $null
    )
    $uri = "$BaseUrl$Path"
    if ($Body -ne $null) {
        $json = $Body | ConvertTo-Json -Depth 30
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -ContentType "application/json" -Body $json
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers
}

function Upsert-Jira {
    param(
        [PSCustomObject]$Item,
        [string]$BaseUrl,
        [hashtable]$Headers,
        [string]$ProjectKey,
        [string]$IssueTypeName,
        [switch]$DoApply
    )

    $jql = "project = $ProjectKey AND summary ~ `"$($Item.Hash10)`" ORDER BY created DESC"
    $searchPath = "/rest/api/2/search?maxResults=1&jql=$([uri]::EscapeDataString($jql))"
    $search = Invoke-JiraApi -BaseUrl $BaseUrl -Headers $Headers -Method GET -Path $searchPath
    $existing = if ($search.issues.Count -gt 0) { $search.issues[0] } else { $null }

    $labels = @("catalog-backlog", "catalog-" + $Item.IssueType.ToLower())
    $jiraPriorityName = switch ($Item.Priority) {
        1 { "Highest" }
        2 { "High" }
        default { "Medium" }
    }

    if ($existing) {
        if (-not $DoApply) {
            Write-Host "[DRY][JIRA] UPDATE $($existing.key) <- $($Item.Summary)"
            return
        }
        $body = @{
            fields = @{
                summary     = $Item.Summary
                description = $Item.Description
                duedate     = $Item.SlaDueDate
                labels      = $labels
                priority    = @{ name = $jiraPriorityName }
            }
        }
        Invoke-JiraApi -BaseUrl $BaseUrl -Headers $Headers -Method PUT -Path "/rest/api/2/issue/$($existing.key)" -Body $body | Out-Null
        Write-Host "[JIRA] UPDATED $($existing.key)"
    } else {
        if (-not $DoApply) {
            Write-Host "[DRY][JIRA] CREATE <- $($Item.Summary)"
            return
        }
        $body = @{
            fields = @{
                project     = @{ key = $ProjectKey }
                issuetype   = @{ name = $IssueTypeName }
                summary     = $Item.Summary
                description = $Item.Description
                duedate     = $Item.SlaDueDate
                labels      = $labels
                priority    = @{ name = $jiraPriorityName }
            }
        }
        $created = Invoke-JiraApi -BaseUrl $BaseUrl -Headers $Headers -Method POST -Path "/rest/api/2/issue" -Body $body
        Write-Host "[JIRA] CREATED $($created.key)"
    }
}

# ---------- Linear ----------
function Invoke-LinearApi {
    param(
        [string]$ApiToken,
        [string]$Query,
        [hashtable]$Variables
    )
    $headers = @{
        Authorization = $ApiToken
        "Content-Type" = "application/json"
    }
    $body = @{
        query = $Query
        variables = $Variables
    } | ConvertTo-Json -Depth 30

    $res = Invoke-RestMethod -Method POST -Uri "https://api.linear.app/graphql" -Headers $headers -Body $body
    if ($res.errors) {
        throw "Linear API error: $($res.errors | ConvertTo-Json -Depth 10)"
    }
    return $res
}

function Upsert-Linear {
    param(
        [PSCustomObject]$Item,
        [string]$ApiToken,
        [string]$TeamId,
        [switch]$DoApply
    )

    $findQuery = @'
query($teamId: String!, $needle: String!) {
  issues(first: 1, filter: { team: { id: { eq: $teamId } }, title: { containsIgnoreCase: $needle } }) {
    nodes { id identifier title }
  }
}
'@
    $findRes = Invoke-LinearApi -ApiToken $ApiToken -Query $findQuery -Variables @{ teamId = $TeamId; needle = $Item.Hash10 }
    $existing = $findRes.data.issues.nodes | Select-Object -First 1

    $input = @{
        title       = $Item.Summary
        description = $Item.Description
        dueDate     = $Item.SlaDueDate
        priority    = $Item.Priority
    }

    if ($existing) {
        if (-not $DoApply) {
            Write-Host "[DRY][LINEAR] UPDATE $($existing.identifier) <- $($Item.Summary)"
            return
        }
        $updateQuery = @'
mutation($id: String!, $input: IssueUpdateInput!) {
  issueUpdate(id: $id, input: $input) {
    success
    issue { id identifier title }
  }
}
'@
        $upd = Invoke-LinearApi -ApiToken $ApiToken -Query $updateQuery -Variables @{ id = $existing.id; input = $input }
        Write-Host "[LINEAR] UPDATED $($upd.data.issueUpdate.issue.identifier)"
    } else {
        if (-not $DoApply) {
            Write-Host "[DRY][LINEAR] CREATE <- $($Item.Summary)"
            return
        }
        $createQuery = @'
mutation($input: IssueCreateInput!) {
  issueCreate(input: $input) {
    success
    issue { id identifier title }
  }
}
'@
        $createInput = $input + @{ teamId = $TeamId }
        $crt = Invoke-LinearApi -ApiToken $ApiToken -Query $createQuery -Variables @{ input = $createInput }
        Write-Host "[LINEAR] CREATED $($crt.data.issueCreate.issue.identifier)"
    }
}

if ($Provider -eq "jira") {
    $jiraBaseUrl = $env:JIRA_BASE_URL
    $jiraEmail = $env:JIRA_EMAIL
    $jiraApiToken = $env:JIRA_API_TOKEN
    $jiraProject = $env:JIRA_PROJECT_KEY
    $jiraIssueType = if ($env:JIRA_ISSUE_TYPE) { $env:JIRA_ISSUE_TYPE } else { "Task" }

    if ([string]::IsNullOrWhiteSpace($jiraBaseUrl) -or
        [string]::IsNullOrWhiteSpace($jiraEmail) -or
        [string]::IsNullOrWhiteSpace($jiraApiToken) -or
        [string]::IsNullOrWhiteSpace($jiraProject)) {
        throw "Set JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN, JIRA_PROJECT_KEY"
    }

    $headers = Get-JiraAuthHeader -Email $jiraEmail -ApiToken $jiraApiToken
    foreach ($item in $issues) {
        Upsert-Jira -Item $item -BaseUrl $jiraBaseUrl -Headers $headers -ProjectKey $jiraProject -IssueTypeName $jiraIssueType -DoApply:$Apply
    }
}
elseif ($Provider -eq "linear") {
    $linearToken = $env:LINEAR_API_TOKEN
    $linearTeamId = $env:LINEAR_TEAM_ID

    if ([string]::IsNullOrWhiteSpace($linearToken) -or [string]::IsNullOrWhiteSpace($linearTeamId)) {
        throw "Set LINEAR_API_TOKEN, LINEAR_TEAM_ID"
    }

    foreach ($item in $issues) {
        Upsert-Linear -Item $item -ApiToken $linearToken -TeamId $linearTeamId -DoApply:$Apply
    }
}

Write-Host "Done."
