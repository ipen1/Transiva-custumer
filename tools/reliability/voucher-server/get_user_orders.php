<?php
declare(strict_types=1);
require_once __DIR__.'/voucher_discount_rules.php';


require_once __DIR__.'/schema_runtime_guard.php';
header("Content-Type: application/json; charset=utf-8");

header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");

require_once __DIR__ . "/config.php";
require_once __DIR__ . "/p0_security_helper.php";
$__p0auth = p0_require_customer($conn);
require_once __DIR__ . "/payment_upgrade_helper.php";
// Jangan jadikan ALTER TABLE runtime sebagai syarat endpoint dapat dibaca.
// Endpoint tetap kompatibel walau migrasi kolom pembayaran belum sempat dijalankan.
pu_ensure_schema($conn,"orders");
pu_ensure_schema($conn,"pickup_orders");
$ordersCols = pu_columns($conn, "orders");
function optional_order_select(array $cols, string $name, string $fallback): string {
    return isset($cols[$name]) ? "`$name`" : "$fallback AS `$name`";
}
$newPaymentSelect = implode(", ", [
    optional_order_select($ordersCols, "original_price", "price"),
    optional_order_select($ordersCols, "customer_received", "0"),
    optional_order_select($ordersCols, "price_change_status", "'none'"),
    optional_order_select($ordersCols, "price_change_requested", "0"),
    optional_order_select($ordersCols, "price_change_reason", "''")
]);

function out_json(
    bool $success,
    string $message,
    array $extra = [],
    int $status = 200
): void {
    http_response_code($status);

    echo json_encode(
        array_merge(
            [
                "success" => $success,
                "message" => $message,
                "server_time" => date("Y-m-d H:i:s")
            ],
            $extra
        ),
        JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
    );

    exit;
}

function string_value($value): string {
    if ($value === null) {
        return "";
    }

    return trim((string)$value);
}

function float_value($value): float {
    return is_numeric($value)
        ? (float)$value
        : 0.0;
}

function int_value($value): int {
    return is_numeric($value)
        ? (int)$value
        : 0;
}

function decode_order_note($value): array {
    $raw = string_value($value);

    if ($raw === "") {
        return [];
    }

    $decoded = json_decode($raw, true);

    return is_array($decoded)
        ? $decoded
        : [];
}

function normalize_food_items(array $note): array {
    $items = $note["items"] ?? [];

    if (!is_array($items)) {
        return [];
    }

    $result = [];

    foreach ($items as $item) {
        if (!is_array($item)) {
            continue;
        }

        $menuId = int_value(
            $item["id"]
            ?? $item["menu_id"]
            ?? 0
        );

        $qty = int_value(
            $item["qty"]
            ?? $item["quantity"]
            ?? 1
        );

        if ($menuId <= 0) {
            continue;
        }

        if ($qty <= 0) {
            $qty = 1;
        }

        $result[] = [
            "menu_id" => $menuId,
            "id" => $menuId,
            "name" => string_value(
                $item["name"] ?? ""
            ),
            "qty" => $qty,
            "base_price" => int_value($item["base_price"] ?? $item["price"] ?? 0),
            "original_price" => int_value($item["original_price"] ?? $item["base_price"] ?? $item["price"] ?? 0),
            "discount_percent" => float_value($item["discount_percent"] ?? 0),
            "discount_amount" => int_value($item["discount_amount"] ?? 0),
            "merchant_price" => int_value($item["merchant_price"] ?? $item["price"] ?? 0),
            "option_total" => int_value($item["option_total"] ?? 0),
            "price" => int_value(
                $item["price"] ?? $item["unit_price"] ?? 0
            ),
            "unit_price" => int_value($item["unit_price"] ?? $item["price"] ?? 0),
            "subtotal" => int_value(
                $item["subtotal"] ?? 0
            ),
            "category" => string_value(
                $item["category"] ?? ""
            ),
            "options" => string_value($item["options"] ?? ""),
            "selected_options" => is_array($item["selected_options"] ?? null) ? $item["selected_options"] : []
        ];
    }

    return $result;
}



function driver_public_profile(mysqli $conn, string $username): array {
    $username = trim($username);
    $empty = [
        "driver_photo" => "",
        "vehicle_photo" => "",
        "driver_plate" => "",
        "driver_rating" => 0
    ];
    if ($username === "") return $empty;

    // Foto akun driver bersumber dari driver_profiles, sama seperti menu Akun driver.
    // profile_photo pada users dipakai hanya sebagai fallback avatar lama.
    $sql = "SELECT
                COALESCE(NULLIF(dp.driver_photo, ''), NULLIF(u.profile_photo, ''), '') AS driver_photo,
                COALESCE(dp.vehicle_photo, '') AS vehicle_photo,
                COALESCE(dp.plate, '') AS plate,
                COALESCE(dp.rating, 0) AS rating
            FROM users u
            LEFT JOIN driver_profiles dp ON dp.user_id = u.id
            WHERE u.username = ? AND LOWER(u.role) = 'driver'
            LIMIT 1";
    $st = mysqli_prepare($conn, $sql);
    if (!$st) return $empty;

    mysqli_stmt_bind_param($st, "s", $username);
    if (!mysqli_stmt_execute($st)) {
        mysqli_stmt_close($st);
        return $empty;
    }
    $rs = mysqli_stmt_get_result($st);
    $r = $rs ? mysqli_fetch_assoc($rs) : null;
    mysqli_stmt_close($st);
    if (!$r) return $empty;

    return [
        "driver_photo" => string_value($r["driver_photo"] ?? ""),
        "vehicle_photo" => string_value($r["vehicle_photo"] ?? ""),
        "driver_plate" => string_value($r["plate"] ?? ""),
        "driver_rating" => float_value($r["rating"] ?? 0)
    ];
}

function native_table_exists_compat(mysqli $conn, string $table): bool {
    $safe = mysqli_real_escape_string($conn, $table);
    $q = transiva_schema_query($conn, "SHOW TABLES LIKE '{$safe}'");
    return $q && mysqli_num_rows($q) > 0;
}
function pickup_column_exists(mysqli $conn, string $column): bool {
    $safe = mysqli_real_escape_string($conn, $column);
    $q = transiva_schema_query($conn, "SHOW COLUMNS FROM pickup_orders LIKE '{$safe}'");
    return $q && mysqli_num_rows($q) > 0;
}
function driver_tip_amount_for_order(mysqli $conn, string $source, int $orderDbId, int $customerId): int {
    if (!native_table_exists_compat($conn, "driver_tips") || $orderDbId <= 0 || $customerId <= 0) return 0;
    $st = mysqli_prepare($conn, "SELECT amount FROM driver_tips WHERE source=? AND order_db_id=? AND customer_id=? AND status='success' LIMIT 1");
    if (!$st) return 0;
    mysqli_stmt_bind_param($st, "sii", $source, $orderDbId, $customerId);
    mysqli_stmt_execute($st);
    $rs = mysqli_stmt_get_result($st);
    $r = $rs ? mysqli_fetch_assoc($rs) : null;
    mysqli_stmt_close($st);
    return (int)($r["amount"] ?? 0);
}

$userId = (int)$__p0auth["id"];

if ($userId <= 0) {
    out_json(
        false,
        "User ID tidak valid",
        ["orders" => [], "total" => 0],
        422
    );
}

$stmt = mysqli_prepare(
    $conn,
    "
    SELECT
        id,
        order_id,
        order_type,
        driver_type,

        pickup_lat,
        pickup_lng,
        pickup_address,

        delivery_lat,
        delivery_lng,
        delivery_address,

        distance_km,
        duration_minutes,
        price,

        payment_method,
        payment_status,
        {$newPaymentSelect},

        user_lat,
        user_lng,

        note,
        status,
        driver,

        room_id,
        created_at,

        rating,
        review,
        reviewed_at

    FROM orders
    WHERE user_id = ?
    ORDER BY id DESC
    LIMIT 100
    "
);

if (!$stmt) {
    out_json(
        false,
        mysqli_error($conn),
        ["orders" => [], "total" => 0],
        500
    );
}

mysqli_stmt_bind_param($stmt, "i", $userId);
mysqli_stmt_execute($stmt);

$result = mysqli_stmt_get_result($stmt);

if (!$result) {
    mysqli_stmt_close($stmt);

    out_json(
        false,
        mysqli_error($conn),
        ["orders" => [], "total" => 0],
        500
    );
}

$orders = [];

while ($row = mysqli_fetch_assoc($result)) {
    $note = decode_order_note(
        $row["note"] ?? ""
    );

    $orderType = strtolower(
        string_value(
            $row["order_type"] ?? ""
        )
    );

    $isFood =
        $orderType === "food"
        || $orderType === "transfood"
        || string_value($note["type"] ?? "") === "food";

    $isShop =
        strpos($orderType, "shop") !== false
        || strpos($orderType, "mart") !== false
        || string_value($note["type"] ?? "") === "shop";

    $restaurantId = $isFood
        ? int_value($note["restaurant_id"] ?? 0)
        : 0;

    $restaurantName = $isFood
        ? string_value(
            $note["restaurant_name"] ?? ""
        )
        : "";

    $foodItems = $isFood
        ? normalize_food_items($note)
        : [];

    $menuIds = [];

    foreach ($foodItems as $item) {
        $menuIds[] = (int)$item["menu_id"];
    }

    $orders[] = [
        "id" => int_value($row["id"] ?? 0),
        "order_id" => string_value(
            $row["order_id"] ?? ""
        ),
        "order_type" => string_value(
            $row["order_type"] ?? "Transbike"
        ),
        "driver_type" => string_value(
            $row["driver_type"] ?? ""
        ),

        "pickup_lat" => float_value(
            $row["pickup_lat"] ?? 0
        ),
        "pickup_lng" => float_value(
            $row["pickup_lng"] ?? 0
        ),
        "pickup_address" => string_value(
            $row["pickup_address"] ?? ""
        ),

        "delivery_lat" => float_value(
            $row["delivery_lat"] ?? 0
        ),
        "delivery_lng" => float_value(
            $row["delivery_lng"] ?? 0
        ),
        "delivery_address" => string_value(
            $row["delivery_address"] ?? ""
        ),

        "distance_km" => float_value(
            $row["distance_km"] ?? 0
        ),
        "duration_minutes" => int_value(
            $row["duration_minutes"] ?? 0
        ),
        "price" => int_value(
            $row["price"] ?? 0
        ),

        "payment_method" => string_value(
            $row["payment_method"] ?? ""
        ),
        "payment_status" => string_value(
            $row["payment_status"] ?? ""
        ),
        "payment_label" => string_value($note["payment_label"] ?? ""),
        "voucher_code" => string_value($note["voucher_code"] ?? ""),
        "voucher_title" => string_value($note["voucher_title"] ?? ""),
        "discount_breakdown_version" => 2,
        "voucher_discount" => vd_order_breakdown($row)["voucher_discount"],
        "point_discount" => vd_order_breakdown($row)["point_discount"],
        "total_discount" => vd_order_breakdown($row)["total_discount"],
        "base_original_price" => int_value($note["base_original_price"] ?? 0),
        "rain_surcharge" => int_value($note["rain_surcharge"] ?? 0),
        "rain_active" => !empty($note["rain_active"]) ? 1 : 0,
        "original_price" => int_value($row["original_price"] ?? $note["original_price"] ?? $row["price"] ?? 0),
        "customer_received" => int_value($row["customer_received"] ?? 0),
        "price_change_status" => string_value($row["price_change_status"] ?? "none"),
        "price_change_requested" => int_value($row["price_change_requested"] ?? 0),
        "price_change_reason" => string_value($row["price_change_reason"] ?? ""),

        "user_lat" => float_value(
            $row["user_lat"] ?? 0
        ),
        "user_lng" => float_value(
            $row["user_lng"] ?? 0
        ),

        "note" => string_value(
            $row["note"] ?? ""
        ),
        "plain_note" => $isFood
            ? ""
            : ($isShop ? string_value($note["text"] ?? "") : string_value($row["note"] ?? "")),
        "status" => string_value(
            $row["status"] ?? ""
        ),
        "driver" => string_value(
            $row["driver"] ?? ""
        ),

        "room_id" => string_value(
            $row["room_id"] ?? ""
        ),
        "created_at" => string_value(
            $row["created_at"] ?? ""
        ),

        "rating" => int_value(
            $row["rating"] ?? 0
        ),
        "review" => string_value(
            $row["review"] ?? ""
        ),
        "reviewed_at" => string_value(
            $row["reviewed_at"] ?? ""
        ),
        "driver_tip_amount" => driver_tip_amount_for_order($conn, "orders", int_value($row["id"] ?? 0), $userId),

        /*
         * Data khusus repeat-order TransFood.
         * create_food_order.php menyimpan data ini di JSON kolom note.
         */
        "restaurant_id" => $restaurantId,
        "restaurant_name" => $restaurantName,
        "food_items" => $foodItems,
        "menu_ids" => $menuIds,
        "delivery_mode" => string_value(
            $note["delivery_mode"] ?? "standard"
        ),
        "food_total" => int_value(
            $note["food_total"] ?? 0
        ),
        "delivery_fee" => int_value(
            $note["delivery_fee"] ?? 0
        ),
        "original_delivery_fee" => int_value($note["original_delivery_fee"] ?? $note["delivery_fee"] ?? 0),
        "voucher_code" => string_value($note["voucher_code"] ?? ""),
        "voucher_title" => string_value($note["voucher_title"] ?? ""),
        "discount_breakdown_version" => 2,
        "voucher_discount" => vd_order_breakdown($row)["voucher_discount"],
        "point_discount" => vd_order_breakdown($row)["point_discount"],
        "total_discount" => vd_order_breakdown($row)["total_discount"],
        "voucher_sponsor" => string_value($note["voucher_sponsor"] ?? ""),
        "hemat_discount" => int_value($note["hemat_discount"] ?? 0),
        "delivery_label" => string_value($note["delivery_label"] ?? ""),
        "merchant_discount" => array_reduce($foodItems, static function(int $sum, array $item): int {
            return $sum + max(0, int_value($item["discount_amount"] ?? 0)) * max(1, int_value($item["qty"] ?? 1));
        }, 0),

        /*
         * Flag agar Android tahu data ini aman dipakai untuk Pesan Lagi.
         */
        "repeat_supported" => true
    ];
    $idx = count($orders)-1;
    $orders[$idx] = array_merge($orders[$idx], driver_public_profile($conn, string_value($row["driver"] ?? "")));
}

mysqli_stmt_close($stmt);

/* FIX TRANSPICKUP: gabungkan pickup_orders ke menu Aktivitas customer. */
$username = string_value($__p0auth["username"] ?? "");
if (native_table_exists_compat($conn, "pickup_orders")) {
    $where = [];
    $types = "";
    $values = [];
    if (pickup_column_exists($conn, "user_id")) {
        $where[] = "user_id = ?";
        $types .= "i";
        $values[] = $userId;
    }
    if ($username !== "" && pickup_column_exists($conn, "username")) {
        $where[] = "username = ?";
        $types .= "s";
        $values[] = $username;
    }

    if ($where) {
        $sql = "SELECT * FROM pickup_orders WHERE (" . implode(" OR ", $where) . ") ORDER BY id DESC LIMIT 100";
        $pickupStmt = mysqli_prepare($conn, $sql);
        if ($pickupStmt) {
            mysqli_stmt_bind_param($pickupStmt, $types, ...$values);
            mysqli_stmt_execute($pickupStmt);
            $pickupResult = mysqli_stmt_get_result($pickupStmt);
            while ($pickupResult && ($row = mysqli_fetch_assoc($pickupResult))) {
                $id = int_value($row["id"] ?? 0);
                $orderId = string_value($row["order_id"] ?? $id);
                $vehicle = strtolower(string_value($row["vehicle_type"] ?? "bike"));
                $orders[] = [
                    "id" => $id,
                    "order_id" => $orderId,
                    "order_type" => "pickup",
                    "service_type" => "TransPickup",
                    "service_name" => "TransPickup",
                    "driver_type" => in_array($vehicle, ["car", "mobil"], true) ? "car" : "bike",
                    "vehicle_type" => $vehicle,
                    "pickup_lat" => float_value($row["pickup_lat"] ?? 0),
                    "pickup_lng" => float_value($row["pickup_lng"] ?? 0),
                    "pickup_address" => string_value($row["pickup_address"] ?? ""),
                    "delivery_lat" => float_value($row["delivery_lat"] ?? 0),
                    "delivery_lng" => float_value($row["delivery_lng"] ?? 0),
                    "delivery_address" => string_value($row["delivery_address"] ?? ""),
                    "distance_km" => float_value($row["distance_km"] ?? 0),
                    "duration_minutes" => 0,
                    "price" => int_value($row["price"] ?? 0),
                    "payment_method" => string_value($row["payment_method"] ?? "cash"),
                    "payment_status" => string_value($row["payment_status"] ?? ""),
                    "original_price" => int_value($row["original_price"] ?? $row["price"] ?? 0),
                    "customer_received" => int_value($row["customer_received"] ?? 0),
                    "price_change_status" => string_value($row["price_change_status"] ?? "none"),
                    "price_change_requested" => int_value($row["price_change_requested"] ?? 0),
                    "price_change_reason" => string_value($row["price_change_reason"] ?? ""),
                    "note" => string_value($row["note"] ?? ""),
                    "plain_note" => string_value($row["note"] ?? ""),
                    "status" => string_value($row["status"] ?? "pending"),
                    "driver" => string_value($row["driver_username"] ?? ""),
                    "driver_username" => string_value($row["driver_username"] ?? ""),
                    "room_id" => "ROOM-" . $orderId,
                    "created_at" => string_value($row["created_at"] ?? ""),
                    "updated_at" => string_value($row["updated_at"] ?? ""),
                    "item_name" => string_value($row["item_name"] ?? ""),
                    "item_category" => string_value($row["item_category"] ?? ""),
                    "item_value" => int_value($row["item_value"] ?? 0),
                    "package_size" => string_value($row["package_size"] ?? ""),
                    "fragile" => int_value($row["fragile"] ?? 0),
                    "receiver_name" => string_value($row["receiver_name"] ?? ""),
                    "receiver_phone" => string_value($row["receiver_phone"] ?? ""),
                    "delivery_otp" => string_value($row["delivery_otp"] ?? ""),
                    "rating" => int_value($row["rating"] ?? 0),
                    "review" => string_value($row["review"] ?? ""),
                    "reviewed_at" => string_value($row["reviewed_at"] ?? ""),
                    "driver_tip_amount" => driver_tip_amount_for_order($conn, "pickup_orders", $id, $userId),
                    "_transiva_table" => "pickup_orders",
                    "source" => "pickup_orders",
                    "repeat_supported" => false
                ];
                $idx = count($orders)-1;
                $orders[$idx] = array_merge($orders[$idx], driver_public_profile($conn, string_value($row["driver_username"] ?? "")));
            }
            mysqli_stmt_close($pickupStmt);
        }
    }
}

require_once __DIR__.'/customer_order_change_helper.php';
rex_bootstrap($conn);
foreach($orders as &$customerOrder) $customerOrder=coc_enrich($conn,$customerOrder);
unset($customerOrder);

usort($orders, static function(array $a, array $b): int {
    return strcmp((string)($b["created_at"] ?? ""), (string)($a["created_at"] ?? ""));
});

out_json(
    true,
    "Riwayat order berhasil dimuat",
    [
        "total" => count($orders),
        "orders" => $orders
    ]
);
