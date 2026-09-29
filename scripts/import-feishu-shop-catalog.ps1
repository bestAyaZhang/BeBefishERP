param(
  [string]$BaseUrl = 'http://127.0.0.1:8085',
  [string]$ConfigPath = (Join-Path $PSScriptRoot '../docs/platform-shop-feishu-config-2026-09-29.json')
)
$ErrorActionPreference = 'Stop'
if ($BaseUrl -notmatch '^http://(127\.0\.0\.1|localhost):\d+$') { throw '此导入脚本只允许访问本机测试服务' }
if (-not $env:ERP_IMPORT_ADMIN_MOBILE -or -not $env:ERP_IMPORT_ADMIN_PASSWORD) {
  throw '请在当前进程设置 ERP_IMPORT_ADMIN_MOBILE 和 ERP_IMPORT_ADMIN_PASSWORD'
}
$config = Get-Content -LiteralPath $ConfigPath -Raw -Encoding utf8 | ConvertFrom-Json
if ($config.platforms.Count -ne 4 -or $config.shops.Count -ne 21) { throw '预期为 4 个平台、21 家店铺；请先核对清单' }
$seen = [System.Collections.Generic.HashSet[string]]::new()
foreach ($shop in $config.shops) {
  if ($shop.platform -notin $config.platforms -or $shop.channelType -notin @('ecommerce','private') -or
      [string]::IsNullOrWhiteSpace($shop.name) -or [string]::IsNullOrWhiteSpace($shop.ownerName) -or
      [string]::IsNullOrWhiteSpace($shop.optionLabel) -or -not $seen.Add("$($shop.platform)|$($shop.name)")) {
    throw '清单包含无效或重复店铺'
  }
  $segment = if ($shop.channelType -eq 'private' -and $shop.platform.EndsWith('代发')) { '代发' } else { $shop.platform }
  $generated = "$(if ($shop.channelType -eq 'private') { '私域' } else { '电商' })`_$segment`_$($shop.name)"
  if ($shop.channelType -eq 'ecommerce' -and $shop.optionLabel -ne $generated) {
    throw "电商门店选项应由系统生成：$($shop.name)"
  }
}
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login/password" -ContentType 'application/json' -Body (
  @{ mobile = $env:ERP_IMPORT_ADMIN_MOBILE; password = $env:ERP_IMPORT_ADMIN_PASSWORD } | ConvertTo-Json -Compress)
$token = $login.data.accessToken
if (-not $token) { throw '未取得测试管理员令牌' }
$headers = @{ Authorization = "Bearer $token" }
function Get-Rows([string]$path) {
  $rows = @(); $page = 1
  do {
    $response = Invoke-RestMethod -Method Get -Uri "$BaseUrl${path}?page=$page&size=100" -Headers $headers
    $rows += @($response.data.records)
    $page++
  } while ($rows.Count -lt $response.data.total)
  return $rows
}
function Send-Json([string]$method, [string]$path, [object]$body) {
  return (Invoke-RestMethod -Method $method -Uri "$BaseUrl$path" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Compress -Depth 8)).data
}
$platforms = @(Get-Rows '/api/platforms')
$createdPlatforms = 0; $createdShops = 0; $updatedShops = 0
foreach ($name in $config.platforms) {
  if (-not @($platforms | Where-Object name -eq $name).Count) {
    $platforms += Send-Json 'Post' '/api/platforms' @{ name = $name }
    $createdPlatforms++
  }
}
$shops = @(Get-Rows '/api/platform-shops')
foreach ($entry in $config.shops) {
  $platform = @($platforms | Where-Object name -eq $entry.platform)[0]
  $current = @($shops | Where-Object { $_.platformId -eq $platform.id -and $_.name -eq $entry.name })
  if ($current.Count -gt 1) { throw "重复店铺：$($entry.platform) / $($entry.name)" }
  $fields = @{ name = $entry.name; channelType = $entry.channelType; ownerName = $entry.ownerName }
  $segment = if ($entry.channelType -eq 'private' -and $entry.platform.EndsWith('代发')) { '代发' } else { $entry.platform }
  $generated = "$(if ($entry.channelType -eq 'private') { '私域' } else { '电商' })`_$segment`_$($entry.name)"
  if ($entry.channelType -eq 'private' -and $entry.optionLabel -ne $generated) {
    $fields.optionLabelOverride = $entry.optionLabel
  }
  if ($current.Count -eq 0) {
    $fields.platformId = $platform.id
    $shops += Send-Json 'Post' '/api/platform-shops' $fields
    $createdShops++
  } elseif ($current[0].channelType -ne $entry.channelType -or $current[0].ownerName -ne $entry.ownerName -or
      $current[0].optionLabel -ne $entry.optionLabel) {
    $fields.sortOrder = $current[0].sortOrder
    $fields.remark = $current[0].remark
    $fields.version = $current[0].version
    [void](Send-Json 'Put' "/api/platform-shops/$($current[0].id)" $fields)
    $updatedShops++
  }
}
$verified = @(Get-Rows '/api/platform-shops')
foreach ($entry in $config.shops) {
  $platform = @($platforms | Where-Object name -eq $entry.platform)[0]
  $actual = @($verified | Where-Object { $_.platformId -eq $platform.id -and $_.name -eq $entry.name })
  if ($actual.Count -ne 1 -or $actual[0].optionLabel -cne $entry.optionLabel) {
    throw "门店选项核对失败：$($entry.platform) / $($entry.name)"
  }
}
Write-Output "导入完成：新增平台 $createdPlatforms，新增店铺 $createdShops，更新店铺 $updatedShops。"
