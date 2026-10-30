<?php

declare(strict_types=1);
require_once __DIR__.'/voucher_discount_rules.php';
require_once __DIR__.'/smart_waiting_helper.php';sw_bootstrap($conn);
require_once __DIR__ . "/payment_upgrade_helper.php";
require_once __DIR__ . "/driver_dispatch_helper.php";
require_once __DIR__ . "/ride_ecosystem_helper.php";

function driver_order_value(array $row, string ...$keys): string {
    foreach ($keys as $key) {
        if (isset($row[$key])) {
            $value = trim((string)$row[$key]);
            if ($value !== "" && strtolower($value) !== "null") return $value;
        }
    }
    return "";
}


function driver_order_attach_customer_context(mysqli $conn, array $row): array {
    static $userCache = [];
    static $familyCache = [];

    $userId = (int)($row['user_id'] ?? 0);
    $username = driver_order_value($row, 'username', 'customer_name', 'customer');
    if ($userId > 0) {
        if (!array_key_exists($userId, $userCache)) {
            $userCache[$userId] = '';
            $st = mysqli_prepare($conn, "SELECT username FROM users WHERE id=? LIMIT 1");
            if ($st) {
                mysqli_stmt_bind_param($st, 'i', $userId);
                if (mysqli_stmt_execute($st)) {
                    $rs = mysqli_stmt_get_result($st);
                    $u = $rs ? mysqli_fetch_assoc($rs) : null;
                    if ($u) $userCache[$userId] = trim((string)($u['username'] ?? ''));
                }
                mysqli_stmt_close($st);
            }
        }
        if ($username === '') $username = (string)$userCache[$userId];
    }

    $familyId = (int)($row['family_member_id'] ?? 0);
    $accountType = $familyId > 0 ? 'family' : 'personal';
    $familyName = '';
    $familyRelation = '';
    if ($familyId > 0 && $userId > 0 && native_table_exists($conn, 'customer_family_members')) {
        $cacheKey = $userId . ':' . $familyId;
        if (!array_key_exists($cacheKey, $familyCache)) {
            $familyCache[$cacheKey] = null;
            $st = mysqli_prepare($conn, "SELECT name,relationship FROM customer_family_members WHERE id=? AND customer_id=? LIMIT 1");
            if ($st) {
                mysqli_stmt_bind_param($st, 'ii', $familyId, $userId);
                if (mysqli_stmt_execute($st)) {
                    $rs = mysqli_stmt_get_result($st);
                    $fm = $rs ? mysqli_fetch_assoc($rs) : null;
                    if ($fm) $familyCache[$cacheKey] = $fm;
                }
                mysqli_stmt_close($st);
            }
        }
        $fm = $familyCache[$cacheKey];
        if (is_array($fm)) {
            $accountType = 'family';
            $familyName = trim((string)($fm['name'] ?? ''));
            $familyRelation = trim((string)($fm['relationship'] ?? 'Keluarga'));
        }
    }

    $row['_account_type'] = $accountType;
    $row['_customer_name'] = $username !== '' ? $username : 'Customer';
    $row['_family_name'] = $familyName;
    $row['_family_relationship'] = $familyRelation;
    $publicOrderId = driver_order_value($row, 'order_id', 'id');
    if ($publicOrderId !== '' && function_exists('eco_get')) {
        try { $row['_ecosystem'] = eco_get($conn, $publicOrderId); } catch (Throwable $ignored) { $row['_ecosystem'] = ['waypoints'=>[]]; }
    }
    return $row;
}

function driver_order_normalize(
    array $row,
    string $source,
    bool $offer = false
): array {
    $id = driver_order_value($row, "id", "order_id");
    $orderType = strtolower(driver_order_value(
        $row, "order_type", "service_type", "type"
    ));

    $service = "TransRide";
    if ($source === "pickup_orders") {
        $service = "TransSend";
    } elseif (strpos($orderType, "food") !== false) {
        $service = "TransFood";
    } elseif (strpos($orderType, "shop") !== false || strpos($orderType, "mart") !== false) {
        $service = "TransShop";
    } elseif (
        strpos($orderType, "car") !== false
        || strpos($orderType, "mobil") !== false
        || strtolower(driver_order_value($row, "driver_type", "vehicle_type"))
            === "car"
    ) {
        $service = "TransCar";
    }

    $expiresAt = driver_order_value(
        $row, "offer_expired_at", "offer_expires_at", "expires_at"
    );
    $remaining = -1;
    if ($expiresAt !== "") {
        $timestamp = strtotime($expiresAt);
        if ($timestamp !== false) {
            // Semua layanan memakai satu jendela tawaran maksimal 15 detik.
            $remaining = max(0, min(15, $timestamp - time()));
        }
    }

    $offerToken = $offer
        ? driver_order_value($row, "offer_action_token")
        : "";
    $offerCycleKey = $offerToken !== ""
        ? $offerToken
        : ($expiresAt !== "" ? $expiresAt : $id);

    $noteJson = [];
    $rawNote = driver_order_value($row, "note");
    if ($rawNote !== "" && substr(ltrim($rawNote),0,1)==="{") { $decoded=json_decode($rawNote,true); if(is_array($decoded)) $noteJson=$decoded; }
    $discounts=vd_order_breakdown($row);
    $voucherDiscount=$discounts["voucher_discount"];
    $voucherCode=(string)($row["voucher_code"] ?? $noteJson["voucher_code"] ?? "");
    $originalDelivery=(int)($noteJson["original_delivery_fee"] ?? $noteJson["delivery_fee"] ?? $row["original_price"] ?? $row["price"] ?? 0);
    return [
        "id" => $id,
        "order_id" => $id,
        "source" => $source,
        "service_name" => $service,
        "service_type" => $service,
        "order_type" => driver_order_value($row, "order_type", "type"),
        "status" => driver_order_value($row, "status"),
        "pickup_address" => driver_order_value(
            $row, "pickup_address", "pickup"
        ),
        "destination_address" => driver_order_value(
            $row,
            "destination_address",
            "delivery_address",
            "destination",
            "delivery"
        ),
        "pickup_lat" => driver_order_value($row, "pickup_lat", "user_lat"),
        "pickup_lng" => driver_order_value($row, "pickup_lng", "user_lng"),
        "delivery_lat" => driver_order_value($row, "delivery_lat"),
        "delivery_lng" => driver_order_value($row, "delivery_lng"),
        "driver_earning" => isset($row["driver_earning"]) && (int)$row["driver_earning"]>0 ? (int)$row["driver_earning"] : ($service === "TransFood" ? $originalDelivery : (int)($row["original_price"] ?? $row["price"] ?? 0)),
        "smart_waiting" => sw_snapshot($GLOBALS["conn"],$source,$row),
        "price" => (int)driver_order_value(
            $row, "price", "fare", "total", "total_price"
        ),
        "distance_km" => driver_order_value($row, "distance_km"),
        "pickup_distance_text" => driver_order_value(
            $row, "pickup_distance_text", "pickup_distance_km", "distance_km"
        ),
        "queued" => !empty($row["_queued"]),
        "queue_position" => (int)($row["_queue_position"] ?? 0),
        "remaining_seconds" => $remaining,
        "offer_action_token" => $offerToken,
        "offer_expired_at" => $expiresAt,
        "offer_cycle_key" => $offerCycleKey,
        "voucher_code" => $voucherCode,
        "discount_breakdown_version" => 2,
        "voucher_discount" => $voucherDiscount,
        "point_discount" => $discounts["point_discount"],
        "total_discount" => $discounts["total_discount"],
        "voucher_sponsor" => $discounts["total_discount"]>0 ? "TRANSIVA" : "",
        "original_delivery_fee" => $originalDelivery,
        "driver_income_affected" => false,
        "payment_method" => pu_payment_method($row),
        "payment_label" => pu_payment_label($row),
        "customer_received" => (int)($row["customer_received"] ?? 0),
        "original_price" => (float)($row["original_price"] ?? $row["price"] ?? 0),
        "price_change_status" => driver_order_value($row, "price_change_status"),
        "price_change_requested" => (float)($row["price_change_requested"] ?? 0),
        "price_change_reason" => driver_order_value($row, "price_change_reason"),
        // Catatan customer diteruskan apa adanya. Untuk TransFood nilainya dapat berupa JSON
        // yang akan diparse oleh aplikasi driver; layanan lain umumnya berupa teks biasa.
        "note" => driver_order_value($row, "note", "customer_note", "order_note", "special_instructions", "instructions"),
        "customer_note" => driver_order_value($row, "customer_note", "order_note", "special_instructions", "instructions"),
        "room_id" => pu_canonical_room($source, $row),
        "customer_name" => driver_order_value($row, "_customer_name", "username", "customer_name", "customer"),
        "account_type" => driver_order_value($row, "_account_type") === "family" ? "family" : "personal",
        "account_label" => driver_order_value($row, "_account_type") === "family" ? "Transiva Family" : "Akun Pribadi",
        "family_member_id" => (int)($row["family_member_id"] ?? 0),
        "family_name" => driver_order_value($row, "_family_name"),
        "family_relationship" => driver_order_value($row, "_family_relationship"),
        "ecosystem" => (isset($row["_ecosystem"]) && is_array($row["_ecosystem"])) ? $row["_ecosystem"] : ["waypoints"=>[]],
        // OTP TransSend tidak pernah dikirim ke aplikasi driver.
        // Driver hanya memasukkan kode yang diberikan customer/penerima.
        "raw_status" => driver_order_value($row, "status")
    ];
}

function driver_find_orders(
    mysqli $conn,
    string $username,
    string $driverType
): array {
    $active = null;
    $activeOrders = [];
    $offers = [];

    $tables = ["orders", "pickup_orders"];

    foreach ($tables as $table) {
        if (!native_table_exists($conn, $table)) continue;

        $driverColumn = "";
        foreach (["driver", "driver_username"] as $candidate) {
            if (native_column_exists($conn, $table, $candidate)) {
                $driverColumn = $candidate;
                break;
            }
        }

        $offeredColumn = native_column_exists(
            $conn, $table, "offered_driver"
        ) ? "offered_driver" : "";

        if ($driverColumn !== "") {
            $orderColumns = [];
            if (native_column_exists($conn, $table, "accepted_at")) {
                $orderColumns[] = "accepted_at ASC";
            }
            if (native_column_exists($conn, $table, "created_at")) {
                $orderColumns[] = "created_at ASC";
            }
            $orderColumns[] = "id ASC";

            // TransSend harus tetap tampil sebagai order aktif selama belum selesai/dibatalkan,
            // termasuk status lama atau status tambahan yang mungkin tersimpan di database.
            $activeStatusCondition = $table === "pickup_orders"
                ? "LOWER(COALESCE(status,'')) NOT IN ('finished','finish','completed','complete','done','selesai','canceled','cancelled','dibatalkan','rejected')"
                : "LOWER(COALESCE(status,'')) IN ('taken','driver_accepted','accepted','arrived','arrived_pickup','picked_up','on_delivery','arrived_delivery','on_trip','in_progress','processing','ongoing','started')";

            $stmt = mysqli_prepare(
                $conn,
                "SELECT *
                 FROM `{$table}`
                 WHERE `{$driverColumn}` = ?
                   AND {$activeStatusCondition}
                 ORDER BY " . implode(", ", $orderColumns) . "
                 LIMIT 2"
            );
            if ($stmt) {
                mysqli_stmt_bind_param($stmt, "s", $username);
                mysqli_stmt_execute($stmt);
                $result = mysqli_stmt_get_result($stmt);
                while ($result && ($row = mysqli_fetch_assoc($result))) {
                    $row = driver_order_attach_customer_context($conn, $row);
                    $normalizedActive = driver_order_normalize($row, $table, false);
                    $normalizedActive["remaining_seconds"] = -1;
                    $activeOrders[] = $normalizedActive;
                }
                mysqli_stmt_close($stmt);
            } else {
                error_log("driver_find_orders active prepare failed {$table}: " . mysqli_error($conn));
            }
        }

        if ($offeredColumn !== "") {
            $typeCondition = "";
            if (native_column_exists($conn, $table, "driver_type")) {
                $typeCondition = $driverType === "car"
                    ? " AND LOWER(COALESCE(o.driver_type,'')) IN ('car','mobil','driver_car','transcar','')"
                    : " AND LOWER(COALESCE(o.driver_type,'')) IN ('bike','motor','motorcycle','driver_bike','transbike','transride','transshop','shop','transmart','mart','')";
            } elseif (native_column_exists($conn, $table, "vehicle_type")) {
                $typeCondition = $driverType === "car"
                    ? " AND LOWER(COALESCE(o.vehicle_type,'')) IN ('car','mobil','driver_car','transcar','')"
                    : " AND LOWER(COALESCE(o.vehicle_type,'')) IN ('bike','motor','motorcycle','driver_bike','transbike','transride','transshop','shop','transmart','mart','')";
            }
            $offerStatusCondition = $table === "orders"
                ? "AND ((LOWER(COALESCE(o.order_type,''))='food' AND LOWER(o.status)='merchant_accepted') OR (LOWER(COALESCE(o.order_type,''))<>'food' AND LOWER(o.status) IN ('pending','offered')))"
                : "AND LOWER(o.status) IN ('pending','offered')";

            // V16 batch dispatch: tawaran berasal dari driver_dispatch_offers, bukan lagi satu offered_driver saja.
            if (function_exists('dd_ensure_schema')) dd_ensure_schema($conn);
            if (native_table_exists($conn, 'driver_dispatch_offers')) {
                $sourceEsc = mysqli_real_escape_string($conn, $table);
                $stmt = mysqli_prepare($conn,
                    "SELECT o.*, dbo.driver_username AS batch_offered_driver, dbo.action_token AS batch_action_token,
                            dbo.expired_at AS batch_expired_at, dbo.dispatch_wave, dbo.batch_no, dbo.distance_km AS offer_distance_km
                     FROM `{$table}` o
                     INNER JOIN driver_dispatch_offers dbo ON dbo.order_id=o.id AND dbo.source_table='{$sourceEsc}'
                     WHERE dbo.driver_username=? AND dbo.outcome='offered' AND dbo.expired_at>NOW()
                       {$offerStatusCondition} {$typeCondition}
                     ORDER BY dbo.expired_at ASC, o.id DESC LIMIT 8"
                );
                if ($stmt) {
                    mysqli_stmt_bind_param($stmt, "s", $username); mysqli_stmt_execute($stmt); $result=mysqli_stmt_get_result($stmt);
                    while ($result && ($row=mysqli_fetch_assoc($result))) {
                        $row['offered_driver']=$row['batch_offered_driver'];
                        $row['offer_action_token']=$row['batch_action_token'];
                        $row['offer_expired_at']=$row['batch_expired_at'];
                        if(!empty($row['offer_distance_km']))$row['pickup_distance_km']=$row['offer_distance_km'];
                        $row=driver_order_attach_customer_context($conn,$row);$offer=driver_order_normalize($row,$table,true);
                        $offer['dispatch_wave']=(int)($row['dispatch_wave']??1);$offer['batch_no']=(int)($row['batch_no']??1);
                        if($offer['remaining_seconds']>0)$offers[]=$offer;
                    }
                    mysqli_stmt_close($stmt);
                }
            } else {
                $stmt = mysqli_prepare($conn,
                    "SELECT * FROM `{$table}` WHERE `{$offeredColumn}`=? AND offer_expired_at IS NOT NULL AND offer_expired_at>NOW() " .
                    ($table==='orders' ? "AND ((LOWER(COALESCE(order_type,''))='food' AND LOWER(status)='merchant_accepted') OR (LOWER(COALESCE(order_type,''))<>'food' AND LOWER(status) IN ('pending','offered'))) " : "AND LOWER(status) IN ('pending','offered') ") .
                    "ORDER BY id DESC LIMIT 8"
                );
                if($stmt){mysqli_stmt_bind_param($stmt,'s',$username);mysqli_stmt_execute($stmt);$result=mysqli_stmt_get_result($stmt);while($result&&($row=mysqli_fetch_assoc($result))){$row=driver_order_attach_customer_context($conn,$row);$offer=driver_order_normalize($row,$table,true);if($offer['remaining_seconds']>0)$offers[]=$offer;}mysqli_stmt_close($stmt);}
            }
        }

        // Tidak ada fallback order publik. Semua tawaran wajib melalui dispatcher radius 20 km.

    }

    usort($offers, static function(array $a, array $b): int {
        return (int)$a["remaining_seconds"] <=> (int)$b["remaining_seconds"];
    });

    usort($activeOrders, static function(array $a, array $b): int {
        return (int)$a["id"] <=> (int)$b["id"];
    });
    if (!empty($activeOrders)) {
        $active = $activeOrders[0];
    }

    return [
        "active_order" => $active,
        "active_orders" => array_slice($activeOrders, 0, 2),
        "active_order_count" => min(2, count($activeOrders)),
        "max_active_orders" => 2,
        "offers" => $offers
    ];
}
