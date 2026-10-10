<?php
declare(strict_types=1);

/*
 * Transiva Smart Offline Driver Nudge
 * -----------------------------------
 * Mengirim FCM ke driver OFFLINE yang lokasi terakhirnya dekat pickup ketika:
 * - tidak ada driver online yang tersedia, atau
 * - seluruh driver online di gelombang tersebut sedang sibuk.
 *
 * Notifikasi ini BUKAN offer order dan tidak menyentuh offered_driver.
 * Driver tetap harus membuka aplikasi dan mengaktifkan status ONLINE sendiri.
 */

if (!function_exists('don_clean')) {
function don_clean($v): string { return trim((string)($v ?? '')); }

function don_table_exists(mysqli $conn, string $table): bool {
    static $cache = [];
    $key = strtolower($table);
    if (array_key_exists($key, $cache)) return $cache[$key];
    $t = mysqli_real_escape_string($conn, $table);
    $q = mysqli_query($conn, "SHOW TABLES LIKE '$t'");
    $ok = $q && mysqli_num_rows($q) > 0;
    if ($q) mysqli_free_result($q);
    return $cache[$key] = $ok;
}

function don_column_exists(mysqli $conn, string $table, string $column): bool {
    static $cache = [];
    $key = strtolower($table.'|'.$column);
    if (array_key_exists($key, $cache)) return $cache[$key];
    $t = mysqli_real_escape_string($conn, $table);
    $c = mysqli_real_escape_string($conn, $column);
    $q = mysqli_query($conn, "SHOW COLUMNS FROM `$t` LIKE '$c'");
    $ok = $q && mysqli_num_rows($q) > 0;
    if ($q) mysqli_free_result($q);
    return $cache[$key] = $ok;
}

function don_type_sql(mysqli $conn, string $type): string {
    if (!don_column_exists($conn, 'driver_profiles', 'driver_type')) return '1=1';
    $t = strtolower(don_clean($type));
    if (in_array($t, ['car','mobil','driver_car','transcar'], true)) {
        return "LOWER(COALESCE(dp.driver_type,'')) IN ('car','mobil','driver_car','transcar')";
    }
    return "LOWER(COALESCE(dp.driver_type,'')) IN ('bike','motor','motorcycle','driver_bike','driver_motor','transbike','transride')";
}

function don_haversine_sql(float $lat, float $lng): string {
    $lat = max(-90, min(90, $lat));
    $lng = max(-180, min(180, $lng));
    return "(6371 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(dp.latitude-$lat)/2),2)+COS(RADIANS($lat))*COS(RADIANS(dp.latitude))*POWER(SIN(RADIANS(dp.longitude-$lng)/2),2))))";
}

function don_copy(string $reason, float $distanceKm, string $service): array {
    $oldTz = date_default_timezone_get();
    date_default_timezone_set('Asia/Makassar');
    $hour = (int)date('G');
    if ($oldTz) date_default_timezone_set($oldTz);

    if ($hour >= 5 && $hour < 11) $greeting = 'Pagi';
    elseif ($hour >= 11 && $hour < 15) $greeting = 'Siang';
    elseif ($hour >= 15 && $hour < 19) $greeting = 'Sore';
    elseif ($hour >= 19 && $hour < 24) $greeting = 'Malam';
    else $greeting = 'Masih bangun';

    $distanceText = number_format(max(0.1, $distanceKm), 1, ',', '.').' km';
    $service = don_clean($service) !== '' ? don_clean($service) : 'Transiva';

    if ($reason === 'all_online_busy') {
        $title = 'Customer lagi nunggu nih 👀';
        $body = "$greeting! Semua driver online di sekitar sedang sibuk. Ada $service sekitar $distanceText dari lokasi terakhirmu. Online-kan status dan siap terima cuan.";
    } else {
        $title = 'Ada order di sekitar kamu 💸';
        $body = "$greeting! Belum ada driver online yang siap di sekitar pickup. Ada $service sekitar $distanceText dari lokasi terakhirmu. Online-kan status kalau siap narik.";
    }
    return [$title, $body];
}

function don_notify_offline_nearby(
    mysqli $conn,
    string $source,
    int $orderId,
    string $driverType,
    float $pickupLat,
    float $pickupLng,
    string $pickupAddress,
    string $service,
    string $reason = 'no_available_driver',
    int $radiusKm = 8,
    int $limit = 5
): array {
    if ($orderId <= 0 || $pickupLat == 0.0 || $pickupLng == 0.0) return ['sent'=>0,'reason'=>'invalid_order'];
    if (!function_exists('send_fcm_notification')) return ['sent'=>0,'reason'=>'fcm_unavailable'];
    if (!don_table_exists($conn, 'offline_driver_nudge_log')) return ['sent'=>0,'reason'=>'migration_required'];
    if (!don_table_exists($conn, 'users') || !don_table_exists($conn, 'driver_profiles')) return ['sent'=>0,'reason'=>'driver_tables_missing'];
    if (!don_column_exists($conn,'driver_profiles','latitude') || !don_column_exists($conn,'driver_profiles','longitude')) return ['sent'=>0,'reason'=>'location_columns_missing'];

    $radiusKm = max(2, min(15, $radiusKm));
    $limit = max(1, min(10, $limit));
    $distance = don_haversine_sql($pickupLat, $pickupLng);
    $typeSql = don_type_sql($conn, $driverType);
    $sourceEsc = mysqli_real_escape_string($conn, $source);

    $latDelta = max(0.01, $radiusKm / 111.0);
    $cosLat = max(0.15, abs(cos(deg2rad($pickupLat))));
    $lngDelta = max(0.01, $radiusKm / (111.0 * $cosLat));
    $minLat = $pickupLat - $latDelta; $maxLat = $pickupLat + $latDelta;
    $minLng = $pickupLng - $lngDelta; $maxLng = $pickupLng + $lngDelta;

    // Lokasi offline tidak lagi diperbarui. Batasi lokasi terakhir maksimal 72 jam
    // agar "dekat pickup" tetap masuk akal dan tidak memakai posisi yang terlalu lama.
    $freshSql = '1=1';
    if (don_column_exists($conn,'driver_profiles','last_location_at')) {
        $freshSql = "dp.last_location_at IS NOT NULL AND dp.last_location_at>=DATE_SUB(NOW(),INTERVAL 72 HOUR)";
    } elseif (don_column_exists($conn,'driver_profiles','last_update')) {
        $freshSql = "dp.last_update IS NOT NULL AND dp.last_update>=DATE_SUB(NOW(),INTERVAL 72 HOUR)";
    }

    $sql = "SELECT u.id AS user_id,u.username,u.fcm_token,dp.latitude,dp.longitude,$distance AS distance_km
            FROM users u
            INNER JOIN driver_profiles dp ON dp.user_id=u.id
            WHERE LOWER(COALESCE(u.role,''))='driver'
              AND COALESCE(dp.is_online,0)=0
              AND COALESCE(u.fcm_token,'')<>''
              AND $typeSql
              AND dp.latitude IS NOT NULL AND dp.longitude IS NOT NULL
              AND dp.latitude<>0 AND dp.longitude<>0
              AND dp.latitude BETWEEN $minLat AND $maxLat
              AND dp.longitude BETWEEN $minLng AND $maxLng
              AND $freshSql
              AND NOT EXISTS (
                  SELECT 1 FROM offline_driver_nudge_log n1
                  WHERE n1.driver_user_id=u.id
                    AND n1.sent_at>=DATE_SUB(NOW(),INTERVAL 30 MINUTE)
              )
              AND NOT EXISTS (
                  SELECT 1 FROM offline_driver_nudge_log n2
                  WHERE n2.driver_user_id=u.id
                    AND n2.source_table='$sourceEsc'
                    AND n2.order_id=$orderId
              )
            HAVING distance_km<=$radiusKm
            ORDER BY distance_km ASC,u.id ASC
            LIMIT $limit";

    $q = mysqli_query($conn, $sql);
    if (!$q) {
        error_log('OFFLINE_NUDGE_QUERY_FAILED: '.mysqli_error($conn));
        return ['sent'=>0,'reason'=>'query_failed'];
    }

    $sent = 0; $attempted = 0;
    while ($row = mysqli_fetch_assoc($q)) {
        $attempted++;
        $uid = (int)($row['user_id'] ?? 0);
        $token = don_clean($row['fcm_token'] ?? '');
        $distanceKm = (float)($row['distance_km'] ?? 0);
        if ($uid <= 0 || $token === '') continue;

        [$title, $body] = don_copy($reason, $distanceKm, $service);
        $payload = [
            'app'=>'transiva',
            'type'=>'driver_opportunity',
            'event'=>'offline_nearby_order',
            'screen'=>'driver_dashboard',
            'source'=>$source,
            'order_db_id'=>(string)$orderId,
            'order_id'=>(string)$orderId,
            'service'=>$service,
            'reason'=>$reason,
            'pickup_distance_km'=>number_format($distanceKm,2,'.',''),
            'pickup_address'=>mb_substr($pickupAddress,0,120),
            'suggest_online'=>'1',
            'click_action'=>'OPEN_TRANSIVA'
        ];

        try {
            $result = send_fcm_notification($token, $title, $body, $payload);
            $ok = is_array($result) ? !empty($result['success']) : (bool)$result;
            if ($ok) {
                $reasonEsc = mysqli_real_escape_string($conn, $reason);
                $serviceEsc = mysqli_real_escape_string($conn, $service);
                $distanceSql = round($distanceKm, 3);
                @mysqli_query($conn, "INSERT IGNORE INTO offline_driver_nudge_log(source_table,order_id,driver_user_id,reason,service,distance_km,sent_at) VALUES('$sourceEsc',$orderId,$uid,'$reasonEsc','$serviceEsc',$distanceSql,NOW())");
                $sent++;
            }
        } catch (Throwable $e) {
            error_log('OFFLINE_NUDGE_FCM_FAILED user='.$uid.' order='.$orderId.' error='.$e->getMessage());
        }
    }
    mysqli_free_result($q);
    return ['sent'=>$sent,'attempted'=>$attempted,'reason'=>$reason,'radius_km'=>$radiusKm];
}
}
