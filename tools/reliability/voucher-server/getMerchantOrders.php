<?php

declare(strict_types=1);
require_once __DIR__.'/voucher_discount_rules.php';

// V18 PERFORMANCE:
// Urutan kritis anti-bottleneck:
// 1) cache hit -> selesai tanpa file rate-limiter / MySQL,
// 2) single-flight -> hanya satu leader per merchant+device+scope,
// 3) hanya leader yang masuk rate limiter + DB admission gate,
// 4) baru buka koneksi MySQL.
require_once __DIR__ . '/merchant_orders_fastpath.php';

$scope = strtolower(trim((string)($_GET['scope'] ?? 'active')));
if (!in_array($scope, ['active', 'history', 'all'], true)) {
    $scope = 'active';
}

$fastKey = merchant_orders_fast_identity($scope);
merchant_orders_fast_try_hit($fastKey);
$singleFlight = merchant_orders_fast_acquire($fastKey);

// Pastikan semua lock/gate dilepas jika config/auth menghentikan request.
register_shutdown_function(static function () use (&$singleFlight): void {
    merchant_orders_fast_release($singleFlight);
});

// Hanya leader/cold fall-through yang perlu menyentuh limiter.
// Follower yang berhasil coalesce sudah exit dari fastpath di atas.
merchant_orders_fast_rate_limit_bootstrap();

if (!defined('TRANSIVA_SKIP_AUTH_TOUCH')) {
    define('TRANSIVA_SKIP_AUTH_TOUCH', true);
}
if (!defined('TRANSIVA_DB_READ_BURST_MODE')) {
    define('TRANSIVA_DB_READ_BURST_MODE', true);
}

// Lindungi MySQL dari gelombang merchant berbeda pada cold-cache.
merchant_orders_fast_acquire_db_gate($singleFlight);

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/merchant_auth.php';
require_once __DIR__ . '/merchant_order_index_helper.php';
require_once __DIR__ . '/merchant_p3_helper.php';

$totalStarted = microtime(true);
$authStarted = microtime(true);
$merchant = merchant_require($conn, false);
$authMs = (microtime(true) - $authStarted) * 1000;

$restaurantId = (int)$merchant['restaurant_id'];
$restaurantName = '';
$grossupRules = merchant_p3_fee_rules($conn);

if ($scope === 'active') {
    $statusSql = "AND COALESCE(o.status,'') NOT IN ('finished','completed','finish','cancelled','canceled','merchant_rejected')";
    $limit = 80;
} elseif ($scope === 'history') {
    $statusSql = "AND COALESCE(o.status,'') IN ('finished','completed','finish','cancelled','canceled','merchant_rejected')";
    $limit = 120;
} else {
    $statusSql = '';
    $limit = 160;
}

$queryStarted = microtime(true);
$candidateLimit = $scope === 'active' ? 240 : ($scope === 'history' ? 200 : 180);

$sql = "SELECT o.*, cu.username AS customer_username, cu.phone AS customer_phone, du.phone AS driver_phone
        FROM (
            SELECT order_db_id
            FROM merchant_order_lookup FORCE INDEX (idx_mol_restaurant_order)
            WHERE restaurant_id=?
            ORDER BY order_db_id DESC
            LIMIT {$candidateLimit}
        ) mol
        STRAIGHT_JOIN orders o ON o.id = mol.order_db_id
        LEFT JOIN users cu ON cu.id=o.user_id
        LEFT JOIN users du ON du.username=o.driver AND LOWER(COALESCE(du.role,''))='driver'
        WHERE o.order_type='food'
          {$statusSql}
        ORDER BY mol.order_db_id DESC
        LIMIT {$limit}";

$stmt = mysqli_prepare($conn, $sql);
if (!$stmt && (int)mysqli_errno($conn) === 1146) {
    if (merchant_order_index_bootstrap($conn)) {
        $stmt = mysqli_prepare($conn, $sql);
    }
}
if (!$stmt) {
    error_log('ORDER_LIST_PREPARE errno=' . mysqli_errno($conn) . ' error=' . mysqli_error($conn));
    merchant_json(false, 'Gagal menyiapkan daftar pesanan.', [], 500, 'ORDER_LIST_PREPARE');
}

mysqli_stmt_bind_param($stmt, 'i', $restaurantId);
if (!mysqli_stmt_execute($stmt)) {
    $error = mysqli_stmt_error($stmt);
    mysqli_stmt_close($stmt);
    error_log('ORDER_LIST_EXECUTE: ' . $error);
    merchant_json(false, 'Gagal menjalankan query daftar pesanan.', [], 500, 'ORDER_LIST_EXECUTE');
}

$result = mysqli_stmt_get_result($stmt);
if (!$result) {
    $error = mysqli_stmt_error($stmt);
    mysqli_stmt_close($stmt);
    error_log('ORDER_LIST_RESULT: ' . $error);
    merchant_json(false, 'Gagal membaca hasil daftar pesanan.', [], 500, 'ORDER_LIST_RESULT');
}

$orders = [];
while ($row = mysqli_fetch_assoc($result)) {
    $meta = merchant_note_array($row['note'] ?? '');
    $row['raw_note'] = (string)($row['note'] ?? '');
    $row['note'] = (string)($meta['customer_note'] ?? $meta['note'] ?? '');
    $row['customer_note'] = $row['note'];
    $row['items'] = is_array($meta['items'] ?? null) ? $meta['items'] : [];
    $row['restaurant_id'] = (int)($meta['restaurant_id'] ?? $restaurantId);
    $row['restaurant_name'] = (string)($meta['restaurant_name'] ?? $restaurantName);
    $row['food_total'] = (int)($meta['food_total'] ?? 0);
    $row['delivery_fee'] = (int)($meta['delivery_fee'] ?? 0);
    $row['payment_label'] = (string)($meta['payment_label'] ?? '');
    $row['customer_name'] = (string)($row['customer_username'] ?? '');
    $row['customer_phone'] = (string)($row['customer_phone'] ?? '');
    $row['driver_phone'] = (string)($row['driver_phone'] ?? '');
    $row['merchant_reject_reason'] = (string)($meta['merchant_reject_reason'] ?? '');
    $row['reject_reason'] = $row['merchant_reject_reason'];
    $row['cook_minutes'] = (int)($meta['cook_minutes'] ?? 0);
    $row['estimated_cook_minutes'] = $row['cook_minutes'];
    $row['estimated_ready_at'] = (string)($meta['estimated_ready_at'] ?? '');
    foreach (['estimated_ready_at','created_at'] as $timeField) {
        $rawTime = trim((string)($row[$timeField] ?? ''));
        $parsedTime = $rawTime !== '' ? strtotime($rawTime) : false;
        $row[$timeField . '_epoch_ms'] = $parsedTime !== false ? $parsedTime * 1000 : 0;
    }

    // Finance snapshot untuk UI merchant. Ongkir sengaja dipisah total karena bukan hak merchant.
    $finance = merchant_p3_order_finance($row, $grossupRules);
    $row['merchant_net_revenue'] = (int)($finance['merchant_revenue'] ?? 0);
    $row['merchant_grossup_fee'] = (int)($finance['grossup_fee'] ?? 0);
    $row['merchant_discount_total'] = (int)($finance['merchant_discount'] ?? 0);
    $row['merchant_option_revenue'] = (int)($finance['option_revenue'] ?? 0);
    $row['merchant_item_count'] = (int)($finance['item_count'] ?? 0);
    $row['customer_food_total'] = (int)($meta['food_total'] ?? ($finance['food_total'] ?? 0));
    $row['customer_delivery_fee'] = (int)($meta['delivery_fee'] ?? 0);
    $discounts=vd_order_breakdown($row);
    $row['customer_voucher_discount']=$discounts['voucher_discount'];
    $row['customer_point_discount']=$discounts['point_discount'];
    $row['customer_total_discount']=$discounts['total_discount'];
    $row['customer_order_total'] = (int)($meta['total'] ?? $row['price'] ?? 0);
    // Angka otoritatif untuk UI merchant: harga customer makanan = hak merchant + gross-up Transiva.
    // Ongkir tidak pernah masuk settlement merchant.
    $row['customer_food_total_with_grossup'] = (int)$row['merchant_net_revenue'] + (int)$row['merchant_grossup_fee'];
    $row['merchant_receivable_total'] = (int)$row['merchant_net_revenue'];
    $row['merchant_settlement_note'] = 'Total diterima merchant adalah harga bersih makanan/opsi tanpa gross-up dan tanpa ongkir. Gross-up sepenuhnya milik Transiva.';

    $globalStatus = strtolower(trim((string)($row['status'] ?? '')));
    $merchantStatus = strtolower(trim((string)($meta['merchant_status'] ?? '')));
    $row['global_status'] = $globalStatus;
    $row['merchant_status'] = $merchantStatus;

    if (in_array($globalStatus, ['merchant_accepted','driver_accepted','taken','arrived_pickup'], true)) {
        if (in_array($merchantStatus, ['preparing','merchant_preparing','ready','merchant_ready'], true)) {
            $row['status'] = $merchantStatus;
        } elseif ($merchantStatus === '' || $merchantStatus === 'merchant_accepted') {
            $row['status'] = 'merchant_accepted';
        }
    }

    $orders[] = $row;
}
mysqli_stmt_close($stmt);

$queryMs = (microtime(true) - $queryStarted) * 1000;
$totalMs = (microtime(true) - $totalStarted) * 1000;
$serverTiming = 'auth;dur=' . number_format($authMs, 1, '.', '')
    . ', orders;dur=' . number_format($queryMs, 1, '.', '')
    . ', app;dur=' . number_format($totalMs, 1, '.', '');

$runtimeInfo = merchant_orders_fast_root_info();

$response = [
    'success' => true,
    'message' => 'Pesanan merchant berhasil dimuat.',
    'code' => '',
    'restaurant_id' => $restaurantId,
    'scope' => $scope,
    'server_timezone' => date_default_timezone_get(),
    'server_time' => date(DATE_ATOM),
    'server_time_epoch_ms' => (int)round(microtime(true)*1000),
    'orders' => $orders,
    '_transiva_diag' => [
        'plan' => 'atomic-burst-shield-v16.1',
        'cache' => 'miss-leader-v16.1',
        'runtime' => (string)($runtimeInfo['mode'] ?? 'unknown'),
        'auth_ms' => round($authMs, 1),
        'orders_ms' => round($queryMs, 1),
        'total_ms' => round($totalMs, 1),
    ],
];

$body = json_encode($response, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
if (!is_string($body)) {
    merchant_orders_fast_release($singleFlight);
    $singleFlight = null;
    merchant_json(false, 'Gagal membuat response daftar pesanan.', [], 500, 'ORDER_LIST_ENCODE');
}

// Simpan hasil sukses saja. Error auth/DB tidak pernah dimasukkan cache.
merchant_orders_fast_store_and_release($singleFlight, $body, 200, $serverTiming);
$singleFlight = null;

http_response_code(200);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('X-Content-Type-Options: nosniff');
header('Server-Timing: ' . $serverTiming);
header('X-Transiva-Orders-Scope: ' . $scope);
header('X-Transiva-Auth-Touch: skipped-readonly');
header('X-Transiva-Orders-Candidate-Limit: ' . (string)$candidateLimit);
header('X-Transiva-Orders-Plan: atomic-burst-shield-v16.1');
header('X-Transiva-Orders-Cache: miss-leader-v16.1');
header('X-Transiva-Orders-Runtime: ' . (string)($runtimeInfo['mode'] ?? 'unknown'));

echo $body;
exit;
