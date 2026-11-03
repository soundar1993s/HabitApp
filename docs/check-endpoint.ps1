param(
    [Parameter(Mandatory)][string]$AppUrl,
    [Parameter(Mandatory)][string]$Stage,
    [string]$Output = 'reports/endpoint-checks.csv'
)
$ErrorActionPreference = 'Stop'
$uri = [Uri]$AppUrl
if ($uri.Scheme -notin @('http', 'https')) { throw 'Expected an HTTP application URL' }
$response = Invoke-WebRequest -Uri $AppUrl -TimeoutSec 20
$health = Invoke-RestMethod -Uri ($AppUrl.TrimEnd('/') + '/actuator/health/readiness') -TimeoutSec 20
$row = [pscustomobject]@{
    TimestampUTC = [DateTime]::UtcNow.ToString('o')
    Stage = $Stage
    HomepageHTTP = $response.StatusCode
    Readiness = $health.status
}
$row | Export-Csv -LiteralPath $Output -NoTypeInformation -Append
$row
# These are timestamped observations, not continuous availability measurements.
