# Creates linked demonstration records through the local ERP API. Never resets existing data.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$config = @{}
Get-Content (Join-Path $projectRoot '.env.local') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $config[$Matches[1]] = $Matches[2] }
}
if ($config['SPRING_PROFILES_ACTIVE'] -ne 'local' -or
    $config['ERP_DB_URL'] -notmatch '^jdbc:mysql://127\.0\.0\.1:3307/bebefish_erp\?') {
    throw 'This script only supports the configured local bebefish_erp database.'
}
$baseUrl = 'http://127.0.0.1:8080'
$marker = 'LOCAL-DEMO-20261014'
$statePath = Join-Path $projectRoot '.tools/demo-seed-state.json'
$state = @{ adjustments = @{}; orders = @{} }
if (Test-Path -LiteralPath $statePath) { $state = Get-Content $statePath -Raw | ConvertFrom-Json -AsHashtable }
function Save-State { $state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $statePath -Encoding utf8 }

$backupDirectory = Join-Path $projectRoot 'backups'
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$backupPath = Join-Path $backupDirectory ('before-demo-seed-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
$env:MYSQL_PWD = $config['ERP_DB_PASSWORD']
try {
    & (Join-Path $projectRoot '.tools/mysql8/mysql-8.0.44-winx64/bin/mysqldump.exe') `
        '--host=127.0.0.1' '--port=3307' ('--user=' + $config['ERP_DB_USERNAME']) `
        '--single-transaction' '--no-tablespaces' '--set-gtid-purged=OFF' ('--result-file=' + $backupPath) 'bebefish_erp'
    if ($LASTEXITCODE -ne 0) { throw 'Database backup failed; no demonstration records were created.' }
} finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }

$loginBody = @{ mobile = $config['ERP_LOCAL_ADMIN_MOBILE']; password = $config['ERP_LOCAL_ADMIN_PASSWORD'] } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$baseUrl/api/auth/login/password" -Method Post -ContentType 'application/json' -Body $loginBody
$headers = @{ Authorization = 'Bearer ' + $login.data.accessToken }
$loginBody = $null
function Api([string]$Path, [string]$Method = 'GET', $Body = $null) {
    $arguments = @{ Uri = $baseUrl + $Path; Method = $Method; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 20 -Compress))
    }
    $response = Invoke-RestMethod @arguments
    if ($response.code -ne 'SUCCESS') { throw "API operation failed: $Path ($($response.code))" }
    return $response.data
}
function All([string]$Path) {
    $records = [System.Collections.Generic.List[object]]::new()
    $separator = if ($Path.Contains('?')) { '&' } else { '?' }
    for ($page = 1; ; $page++) {
        $result = Api ($Path + $separator + "page=$page&size=100")
        foreach ($record in $result.records) { $records.Add($record) }
        if ($records.Count -ge $result.total -or $result.records.Count -eq 0) { break }
    }
    return $records.ToArray()
}
function Ensure-Record([string]$Path, [string]$Key, $Payload) {
    $existing = @(All $Path | Where-Object { $_.$Key -eq $Payload[$Key] })
    if ($existing.Count -gt 0) {
        if ($existing.Count -ne 1 -or $existing[0].remark -notlike "$marker*") {
            throw "An existing non-demo record uses the reserved identifier: $($Payload[$Key])"
        }
        return $existing[0]
    }
    return Api $Path 'POST' $Payload
}

try {
    $categories = @{}
    $categoryNames = @('玻璃杯具', '陶瓷餐具', '厨房收纳', '不锈钢用品', '清洁工具', '酒店用品')
    for ($index = 0; $index -lt $categoryNames.Count; $index++) {
        $categories[$index] = Ensure-Record '/api/categories' 'categoryCode' @{
            categoryCode = ('DEMO-CAT-{0:00}' -f ($index + 1)); categoryName = $categoryNames[$index] + '（演示）'
            sortOrder = ($index + 1) * 10; remark = "$marker：日用百货演示分类"
        }
    }
    Write-Output 'Categories ready: 6'

    $suppliers = @()
    $supplierNames = @('义乌晶彩玻璃制品厂', '潮州悦瓷餐具厂', '台州优纳塑业', '永康恒益五金厂', '杭州洁悦日用品厂')
    $supplierAddresses = @('浙江省义乌市演示工业园1号', '广东省潮州市演示工业园2号', '浙江省台州市演示工业园3号', '浙江省永康市演示工业园4号', '浙江省杭州市演示工业园5号')
    for ($index = 0; $index -lt $supplierNames.Count; $index++) {
        $suppliers += Ensure-Record '/api/suppliers' 'supplierNo' @{
            supplierNo = ('DEMO-SUP-{0:00}' -f ($index + 1)); supplierName = $supplierNames[$index] + '（演示）'
            contactPerson = '演示联系人' + ($index + 1); mobile = ('13800000{0:000}' -f ($index + 1))
            telephone = ''; address = $supplierAddresses[$index]; remark = "$marker：虚构供应商，联系方式仅用于演示"
        }
    }
    Write-Output 'Suppliers ready: 5'

    # Variant dimensions exclude product measurements, which are kept on the SKU itself.
    $definitions = @(
        @{ name='高硼硅玻璃水杯'; cat=0; sup=0; unit='只'; price=12.80; cost=6.20; a='款式'; av=@('透明','烟灰'); b='型号'; bv=@('350ml','500ml'); carton=48; capacity=350 },
        @{ name='玻璃凉水壶'; cat=0; sup=0; unit='个'; price=35.00; cost=19.50; a='盖色'; av=@('竹色','黑色'); b='型号'; bv=@('1L','1.5L'); carton=12; capacity=1000 },
        @{ name='日式陶瓷饭碗'; cat=1; sup=1; unit='只'; price=9.90; cost=4.60; a='花色'; av=@('青花','米白'); b='尺寸'; bv=@('4.5英寸','5英寸'); carton=60 },
        @{ name='陶瓷马克杯'; cat=1; sup=1; unit='只'; price=16.80; cost=8.30; a='颜色'; av=@('奶白','浅蓝'); b='型号'; bv=@('300ml','400ml'); carton=36; capacity=300 },
        @{ name='密封保鲜盒'; cat=2; sup=2; unit='个'; price=15.50; cost=7.10; a='盖色'; av=@('薄荷绿','奶油白'); b='型号'; bv=@('800ml','1200ml'); carton=32; capacity=800 },
        @{ name='厨房沥水篮'; cat=2; sup=2; unit='个'; price=22.00; cost=11.00; a='颜色'; av=@('灰色','米白'); b='尺寸'; bv=@('小号','大号'); carton=24 },
        @{ name='304不锈钢汤勺'; cat=3; sup=3; unit='把'; price=5.80; cost=2.40; a='表面'; av=@('亮面','磨砂'); b='尺寸'; bv=@('16cm','20cm'); carton=120 },
        @{ name='不锈钢保温杯'; cat=3; sup=3; unit='个'; price=42.00; cost=23.00; a='颜色'; av=@('黑色','白色'); b='型号'; bv=@('450ml','600ml'); carton=24; capacity=450 },
        @{ name='超细纤维清洁布'; cat=4; sup=4; unit='条'; price=3.50; cost=1.20; a='颜色'; av=@('蓝色','灰色'); b='尺寸'; bv=@('30×30cm','40×40cm'); carton=200 },
        @{ name='多功能清洁刷'; cat=4; sup=4; unit='把'; price=6.90; cost=2.80; simple=$true; carton=100 },
        @{ name='酒店防滑衣架'; cat=5; sup=2; unit='个'; price=4.50; cost=1.80; a='颜色'; av=@('黑色','木色'); b='款式'; bv=@('成人款','儿童款'); carton=100 },
        @{ name='酒店纸巾盒'; cat=5; sup=2; unit='个'; price=18.00; cost=8.50; simple=$true; carton=36 }
    )
    $products = @()
    for ($index = 0; $index -lt $definitions.Count; $index++) {
        $definition = $definitions[$index]
        $itemNo = 'DEMO-G{0:000}' -f ($index + 1)
        $specifications = @()
        $combinations = @()
        if ($definition.simple) { $combinations = ,@() }
        else {
            $specifications = @(@{name=$definition.a;values=$definition.av}, @{name=$definition.b;values=$definition.bv})
            foreach ($first in $definition.av) { foreach ($second in $definition.bv) { $combinations += ,@($first,$second) } }
        }
        $skus = @()
        for ($skuIndex = 0; $skuIndex -lt $combinations.Count; $skuIndex++) {
            $price = [decimal]$definition.price + $skuIndex * [decimal]1.5
            $cost = [decimal]$definition.cost + $skuIndex * [decimal]0.6
            $skus += @{
                skuName = if ($definition.simple) { '标准款' } else { $combinations[$skuIndex] -join ' / ' }
                specificationValues = @($combinations[$skuIndex]); barcode = ''
                salesUnit=$definition.unit; defaultSalePrice=$price; standardCost=$cost; safetyStockQuantity=50
                status='enabled'; defaultSku=($skuIndex -eq 0)
                packageLengthCm=48; packageWidthCm=36; packageHeightCm=30; packageVolumeCm3=51840
                innerPackageLengthCm=12; innerPackageWidthCm=9; innerPackageHeightCm=10
                productLengthCm=8; productWidthCm=8; productHeightCm=12; capacityMl=$definition.capacity
                netWeightKg=0.25; grossWeightKg=0.32; gramWeightG=250; innerPackageWeightKg=0.04
                packagingMethod='独立彩盒 + 外箱'; cartonQuantity=$definition.carton
                supplierQuotes=@(
                    @{supplierId=$suppliers[$definition.sup].id; supplierItemNo="$itemNo-A-$skuIndex"; purchasePrice=$cost; minPurchaseQuantity=$definition.carton; defaultQuote=$true; status='enabled'},
                    @{supplierId=$suppliers[($definition.sup+1)%5].id; supplierItemNo="$itemNo-B-$skuIndex"; purchasePrice=($cost+[decimal]0.5); minPurchaseQuantity=($definition.carton*2); defaultQuote=$false; status='enabled'}
                )
            }
        }
        $products += Ensure-Record '/api/products' 'itemNo' @{
            itemNo=$itemNo; productName=$definition.name+'（演示）'; categoryId=$categories[$definition.cat].id
            brand='贝贝鱼演示'; productType=if ($definition.simple) {'simple'} else {'variant'}
            status='enabled'; remark="$marker：虚构日用百货商品，可用于开单练习"
            specifications=$specifications; skus=$skus
        }
    }
    $allSkus = @($products | ForEach-Object { $_.skus })
    Write-Output "Products ready: $($products.Count); SKUs: $($allSkus.Count)"

    $warehouses = @()
    for ($index=0; $index -lt 2; $index++) {
        $warehouses += Ensure-Record '/api/warehouses' 'warehouseNo' @{
            warehouseNo=('DEMO-WH-{0:00}' -f ($index+1)); warehouseName=@('杭州演示仓','义乌演示仓')[$index]
            address=@('浙江省杭州市演示园区1号','浙江省义乌市演示园区2号')[$index]
            defaultWarehouse=$false; remark="$marker：模拟库存仓库"
        }
    }
    foreach ($warehouse in $warehouses) {
        $key = [string]$warehouse.id
        if ($state.adjustments.ContainsKey($key)) { $adjustment = Api ('/api/inventory/adjustments/'+$state.adjustments[$key]) }
        else {
            $existingBalances = @(All ('/api/inventory/balances?warehouseId='+$warehouse.id) | Where-Object { $_.skuId -in $allSkus.id -and $_.quantity -ne 0 })
            if ($existingBalances.Count) { throw 'Demo warehouse has inventory but no seed checkpoint; refusing to add stock twice.' }
            $items = @()
            for ($index=0; $index -lt $allSkus.Count; $index++) {
                $items += @{skuId=$allSkus[$index].id; quantityDelta=if ($warehouse.id -eq $warehouses[0].id) {300+$index*10} else {150+$index*5}}
            }
            $adjustment = Api '/api/inventory/adjustments' 'POST' @{
                warehouseId=$warehouse.id; items=$items; reason='演示数据初始库存'; remark="$marker：模拟入库"
            }
            $state.adjustments[$key]=$adjustment.id; Save-State
        }
        if ($adjustment.status -eq 'draft') { $null = Api ('/api/inventory/adjustments/'+$adjustment.id+'/confirm') 'POST' }
        elseif ($adjustment.status -ne 'confirmed') { throw 'Seed inventory adjustment has been voided; manual review is required.' }
    }
    Write-Output 'Warehouses and opening stock ready: 2'

    $platforms = @(Api '/api/customers/platform-options')
    $customerNames = @('杭州晶彩家居店','义乌悦享百货商行','宁波优品酒店用品','苏州小周餐饮店','绍兴陈姐日用品店','金华简约生活馆')
    $platformNames = @('拼多多','1688','1688','散户','散户','拼多多')
    $customers = @()
    for ($index=0; $index -lt $customerNames.Count; $index++) {
        $platform = $platforms | Where-Object { $_.name -eq $platformNames[$index] -and $_.status -eq 'enabled' } | Select-Object -First 1
        if (!$platform) { throw "Demo platform unavailable: $($platformNames[$index])" }
        $customers += Ensure-Record '/api/customers' 'customerNo' @{
            customerNo=('DEMO-CUS-{0:00}' -f ($index+1)); customerName=$customerNames[$index]+'（演示）'
            contactPerson='演示客户'+($index+1); mobile=('13900000{0:000}' -f ($index+1)); telephone=''
            province='浙江省'; city='杭州市'; district='余杭区'; detailAddress=('演示街道{0}号（虚构地址）' -f ($index+1))
            transportMethod=if ($platform.name -eq '散户') {'pickup'} else {'delivery'}
            settlementCycle=if ($platform.name -eq '散户') {'cash'} else {'monthly'}
            platformId=$platform.id; remark="$marker：虚构客户，联系方式仅用于演示"
        }
        $existingPrices = @(Api ('/api/customers/'+$customers[-1].id+'/prices'))
        for ($skuIndex=0; $skuIndex -lt 4; $skuIndex++) {
            $sku = $allSkus[($index*3+$skuIndex)%$allSkus.Count]
            if ($sku.id -notin $existingPrices.skuId) {
                $price = [decimal]::Round([decimal]$sku.defaultSalePrice*([decimal]0.80+$index*[decimal]0.02),2,[MidpointRounding]::AwayFromZero)
                $null = Api ('/api/customers/'+$customers[-1].id+'/prices/'+$sku.id) 'PUT' @{unitPrice=$price}
            }
        }
    }
    Write-Output 'Customers ready: 6; customer prices: 24'

    $existingOrders = @(All '/api/sales-orders')
    $orders = @()
    for ($index=0; $index -lt 8; $index++) {
        $orderMarker = "$marker-SALE-" + ($index+1)
        $order = $existingOrders | Where-Object { $_.remark -eq $orderMarker } | Select-Object -First 1
        if (!$order -and $state.orders.ContainsKey($orderMarker)) { $order=Api ('/api/sales-orders/'+$state.orders[$orderMarker]) }
        if (!$order) {
            $customer=$customers[$index%6]
            $orderLines=@(@{skuId=$allSkus[($index*3)%$allSkus.Count].id;quantity=12}, @{skuId=$allSkus[($index*3+1)%$allSkus.Count].id;quantity=24})
            $order = Api '/api/sales-orders' 'POST' @{
                customerId=$customer.id; warehouseId=$warehouses[0].id
                orderDate=(Get-Date).AddDays(-($index*2)).ToString('yyyy-MM-dd')
                freight=if ($customer.transportMethod -eq 'pickup') {0} else {15}
                receivedAmount=0; lines=$orderLines; remark=$orderMarker; packageNote='演示订单，请勿实际发货'
            }
            $state.orders[$orderMarker]=$order.id; Save-State
            # Full, partial and unpaid examples; calculate from the authoritative saved total.
            $received=if ($index%3 -eq 0) {$order.totalAmount} elseif ($index%3 -eq 1) {[decimal]::Round([decimal]$order.totalAmount/2,2,[MidpointRounding]::AwayFromZero)} else {0}
            $order = Api ('/api/sales-orders/'+$order.id) 'PUT' @{
                customerId=$customer.id;warehouseId=$warehouses[0].id;orderDate=$order.orderDate
                freight=$order.freight;receivedAmount=$received;lines=$orderLines;remark=$orderMarker
                packageNote='演示订单，请勿实际发货'
            }
        }
        if ($index -lt 6 -and $order.status -eq 'draft') { $order=Api ('/api/sales-orders/'+$order.id+'/confirm') 'POST' }
        $orders += $order
    }
    $verifiedProducts = @(All '/api/products' | Where-Object { $_.remark -like "$marker*" })
    $verifiedSkus = @($verifiedProducts | ForEach-Object { $_.skus })
    if ($verifiedProducts.Count -ne 12 -or $verifiedSkus.Count -ne 42 -or @($verifiedSkus | Where-Object { $_.supplierQuotes.Count -ne 2 }).Count) {
        throw 'Product/variant/supplier quote verification failed.'
    }
    $verifiedBalances = @()
    foreach ($warehouse in $warehouses) {
        $verifiedBalances += @(All ('/api/inventory/balances?warehouseId='+$warehouse.id) | Where-Object { $_.skuId -in $verifiedSkus.id })
    }
    if ($verifiedBalances.Count -ne 84 -or @($verifiedBalances | Where-Object { $_.quantity -lt 0 }).Count) {
        throw 'Demo inventory verification failed.'
    }
    $verifiedPriceCount = 0
    foreach ($customer in $customers) {
        $verifiedPriceCount += @(Api ('/api/customers/'+$customer.id+'/prices')).Count
        $bills = Api ('/api/customers/'+$customer.id+'/bills?page=1&size=100')
        if ($bills.total -lt 1) { throw 'Customer bill verification failed.' }
    }
    $summary = [ordered]@{
        marker=$marker; categories=6; suppliers=5; products=$verifiedProducts.Count; skus=$verifiedSkus.Count
        supplierQuotes=84; warehouses=2; inventoryBalances=$verifiedBalances.Count; customers=6; customerPrices=$verifiedPriceCount
        confirmedOrders=@($orders | Where-Object status -eq 'confirmed').Count
        draftOrders=@($orders | Where-Object status -eq 'draft').Count
        backup=$backupPath; customerIds=@($customers.id); productIds=@($verifiedProducts.id)
    }
    $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $projectRoot '.tools/demo-seed-summary.json') -Encoding utf8
    $summary | ConvertTo-Json -Depth 5
} finally {
    Invoke-RestMethod -Uri "$baseUrl/api/auth/logout" -Headers $headers -Method Post | Out-Null
    $headers.Clear(); $config.Clear(); $login=$null
}
