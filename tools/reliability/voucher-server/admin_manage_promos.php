<?php
declare(strict_types=1);


require_once __DIR__.'/schema_runtime_guard.php';
header('Content-Type: application/json; charset=utf-8');

header('Access-Control-Allow-Headers: Content-Type, Authorization');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('Expires: 0');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

require_once __DIR__ . '/config.php';

if (is_file(__DIR__ . '/transiva_push_helper.php')) {
    require_once __DIR__ . '/transiva_push_helper.php';
}
require_once __DIR__ . '/p0_security_helper.php';
$__admin = p0_require_admin($conn);

function json_response(
    bool $success,
    string $message,
    array $extra = [],
    int $status = 200
): void {
    http_response_code($status);

    echo json_encode(
        array_merge(
            [
                'success' => $success,
                'message' => $message
            ],
            $extra
        ),
        JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
    );

    exit;
}

function request_payload(): array {
    $raw = file_get_contents('php://input');

    if (!$raw) {
        return $_POST ?: [];
    }

    $decoded = json_decode($raw, true);

    return is_array($decoded)
        ? $decoded
        : ($_POST ?: []);
}

/**
 * Selalu mengembalikan string.
 * Nilai null tidak boleh diteruskan ke mysqli_real_escape_string().
 */
function safe_string($value, string $fallback = ''): string {
    if ($value === null) {
        return $fallback;
    }

    if (is_bool($value)) {
        return $value ? '1' : '0';
    }

    if (is_scalar($value)) {
        return trim((string)$value);
    }

    return $fallback;
}

function parse_nonnegative_int($value, int $fallback = 0): int {
    if ($value === null || $value === '') return max(0, $fallback);
    if (is_int($value)) return max(0, $value);
    if (is_float($value)) return max(0, (int)floor($value));
    $raw = trim((string)$value);
    if ($raw === '') return max(0, $fallback);
    // Form admin mengirim digit polos. Fallback ini juga menerima format Indonesia seperti 2.000 / Rp 2.000.
    if (preg_match('/^-?\d+$/', $raw)) return max(0, (int)$raw);
    $digits = preg_replace('/[^0-9]/', '', $raw);
    return ($digits === null || $digits === '') ? max(0, $fallback) : max(0, (int)$digits);
}

function nullable_datetime($value): ?string {
    $value = safe_string($value);

    if ($value === '') {
        return null;
    }

    $timestamp = strtotime($value);

    return $timestamp === false
        ? null
        : date('Y-m-d H:i:s', $timestamp);
}

function column_exists(
    mysqli $conn,
    string $table,
    string $column
): bool {
    $stmt = mysqli_prepare(
        $conn,
        'SELECT COUNT(*) AS total
         FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = ?
           AND COLUMN_NAME = ?'
    );

    if (!$stmt) {
        throw new RuntimeException(mysqli_error($conn));
    }

    mysqli_stmt_bind_param($stmt, 'ss', $table, $column);
    mysqli_stmt_execute($stmt);

    $result = mysqli_stmt_get_result($stmt);
    $row = $result ? mysqli_fetch_assoc($result) : null;

    mysqli_stmt_close($stmt);

    return (int)($row['total'] ?? 0) > 0;
}

function ensure_schema(mysqli $conn): void {
    $create = '
        CREATE TABLE IF NOT EXISTS customer_promos (
            id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            title VARCHAR(150) NOT NULL,
            description TEXT NOT NULL,
            promo_code VARCHAR(80) NOT NULL DEFAULT "",
            discount_type VARCHAR(20) NOT NULL DEFAULT "percent",
            discount_value INT NOT NULL DEFAULT 0,
            max_discount INT NULL,
            min_order INT NOT NULL DEFAULT 0,
            service_type VARCHAR(30) NOT NULL DEFAULT "all",
            quota_total INT NULL,
            per_user_limit INT NOT NULL DEFAULT 1,
            image_url TEXT NULL,
            action_url TEXT NULL,
            target_role VARCHAR(30) NOT NULL DEFAULT "all",
            notification_sent_count INT NOT NULL DEFAULT 0,
            last_notified_at DATETIME NULL,
            theme_start VARCHAR(20) NOT NULL DEFAULT "#0759E8",
            theme_end VARCHAR(20) NOT NULL DEFAULT "#18B5FF",
            sort_order INT NOT NULL DEFAULT 0,
            is_active TINYINT(1) NOT NULL DEFAULT 1,
            starts_at DATETIME NULL,
            ends_at DATETIME NULL,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (id)
        ) ENGINE=InnoDB
          DEFAULT CHARSET=utf8mb4
          COLLATE=utf8mb4_unicode_ci
    ';

    if (!transiva_schema_query($conn, $create)) {
        throw new RuntimeException(mysqli_error($conn));
    }

    $columns = [
        'promo_code' =>
            'ALTER TABLE customer_promos
             ADD COLUMN promo_code VARCHAR(80)
             NOT NULL DEFAULT "" AFTER description',

        'discount_type' =>
            'ALTER TABLE customer_promos ADD COLUMN discount_type VARCHAR(20) NOT NULL DEFAULT "percent" AFTER promo_code',
        'discount_value' =>
            'ALTER TABLE customer_promos ADD COLUMN discount_value INT NOT NULL DEFAULT 0 AFTER discount_type',
        'max_discount' =>
            'ALTER TABLE customer_promos ADD COLUMN max_discount INT NULL AFTER discount_value',
        'min_order' =>
            'ALTER TABLE customer_promos ADD COLUMN min_order INT NOT NULL DEFAULT 0 AFTER max_discount',
        'service_type' =>
            'ALTER TABLE customer_promos ADD COLUMN service_type VARCHAR(30) NOT NULL DEFAULT "all" AFTER min_order',
        'quota_total' =>
            'ALTER TABLE customer_promos ADD COLUMN quota_total INT NULL AFTER service_type',
        'per_user_limit' =>
            'ALTER TABLE customer_promos ADD COLUMN per_user_limit INT NOT NULL DEFAULT 1 AFTER quota_total',

        'image_url' =>
            'ALTER TABLE customer_promos
             ADD COLUMN image_url TEXT NULL AFTER promo_code',

        'action_url' =>
            'ALTER TABLE customer_promos
             ADD COLUMN action_url TEXT NULL AFTER image_url',

        'target_role' =>
            'ALTER TABLE customer_promos
             ADD COLUMN target_role VARCHAR(30)
             NOT NULL DEFAULT "all" AFTER action_url',

        'notification_sent_count' =>
            'ALTER TABLE customer_promos
             ADD COLUMN notification_sent_count INT NOT NULL DEFAULT 0 AFTER target_role',

        'last_notified_at' =>
            'ALTER TABLE customer_promos
             ADD COLUMN last_notified_at DATETIME NULL AFTER notification_sent_count',

        'theme_start' =>
            'ALTER TABLE customer_promos
             ADD COLUMN theme_start VARCHAR(20)
             NOT NULL DEFAULT "#0759E8" AFTER target_role',

        'theme_end' =>
            'ALTER TABLE customer_promos
             ADD COLUMN theme_end VARCHAR(20)
             NOT NULL DEFAULT "#18B5FF" AFTER theme_start',

        'sort_order' =>
            'ALTER TABLE customer_promos
             ADD COLUMN sort_order INT
             NOT NULL DEFAULT 0 AFTER theme_end',

        'is_active' =>
            'ALTER TABLE customer_promos
             ADD COLUMN is_active TINYINT(1)
             NOT NULL DEFAULT 1 AFTER sort_order',

        'starts_at' =>
            'ALTER TABLE customer_promos
             ADD COLUMN starts_at DATETIME NULL AFTER is_active',

        'ends_at' =>
            'ALTER TABLE customer_promos
             ADD COLUMN ends_at DATETIME NULL AFTER starts_at',

        'created_at' =>
            'ALTER TABLE customer_promos
             ADD COLUMN created_at DATETIME
             NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER ends_at',

        'updated_at' =>
            'ALTER TABLE customer_promos
             ADD COLUMN updated_at DATETIME
             NOT NULL DEFAULT CURRENT_TIMESTAMP
             ON UPDATE CURRENT_TIMESTAMP AFTER created_at'
    ];

    foreach ($columns as $name => $alter) {
        if (!column_exists($conn, 'customer_promos', $name)) {
            if (!transiva_schema_query($conn, $alter)) {
                throw new RuntimeException(
                    'Gagal menambah kolom '
                    . $name
                    . ': '
                    . mysqli_error($conn)
                );
            }
        }
    }
}

function promo_by_id(mysqli $conn, int $id): ?array {
    $stmt = mysqli_prepare(
        $conn,
        'SELECT *
         FROM customer_promos
         WHERE id = ?
         LIMIT 1'
    );

    if (!$stmt) {
        throw new RuntimeException(mysqli_error($conn));
    }

    mysqli_stmt_bind_param($stmt, 'i', $id);
    mysqli_stmt_execute($stmt);

    $result = mysqli_stmt_get_result($stmt);
    $row = $result ? mysqli_fetch_assoc($result) : null;

    mysqli_stmt_close($stmt);

    return $row ?: null;
}

/**
 * Pengiriman push sengaja diisolasi.
 *
 * Kegagalan push tidak boleh mengubah hasil penyimpanan promo menjadi gagal.
 */
function send_promo_notification(array $promo): array {
    if (!function_exists('transiva_push_send_role')) {
        return [
            'success' => false,
            'message' =>
                'Promo tersimpan, tetapi fungsi '
                . 'transiva_push_send_role tidak ditemukan'
        ];
    }

    $targetRole = safe_string(
        $promo['target_role'] ?? 'all',
        'all'
    );

    if ($targetRole === '') {
        $targetRole = 'all';
    }

    $title = safe_string(
        $promo['title'] ?? 'Promo Transiva',
        'Promo Transiva'
    );

    if ($title === '') {
        $title = 'Promo Transiva';
    }

    $description = safe_string(
        $promo['description'] ?? '',
        ''
    );

    // Seluruh nilai data dipastikan string, tidak ada null.
    $data = [
        'type' => 'transiva_rich',
        'promo_id' => safe_string($promo['id'] ?? ''),
        'promo_code' => safe_string(
            $promo['promo_code'] ?? ''
        ),
        'image_url' => safe_string(
            $promo['image_url'] ?? ''
        ),
        'action_url' => safe_string(
            $promo['action_url'] ?? ''
        ),
        'campaign_id' => 'promo:' . safe_string($promo['id'] ?? ''),
        'target_role' => $targetRole,
        'click_action' => 'OPEN_TRANSIVA',
        'screen' => 'customer_dashboard'
    ];

    try {
        $result = transiva_push_send_role(
            $targetRole,
            $title,
            $description,
            $data
        );

        if (is_array($result)) {
            return array_merge(
                ['success' => false],
                $result
            );
        }

        return [
            'success' => (bool)$result,
            'message' => (bool)$result
                ? 'Notifikasi berhasil diproses'
                : 'Helper push mengembalikan status gagal'
        ];

    } catch (Throwable $error) {
        return [
            'success' => false,
            'message' =>
                'Promo tersimpan, tetapi notifikasi gagal: '
                . $error->getMessage()
        ];
    }
}

try {
    if (!isset($conn) || !($conn instanceof mysqli)) {
        json_response(
            false,
            'Koneksi mysqli $conn tidak ditemukan',
            [],
            500
        );
    }

    mysqli_set_charset($conn, 'utf8mb4');
    ensure_schema($conn);

    $input = request_payload();
    $action = safe_string(
        $input['action'] ?? 'list',
        'list'
    );

    if ($action === 'list') {
        $query = transiva_schema_query(
            $conn,
            'SELECT *
             FROM customer_promos
             ORDER BY sort_order ASC, id DESC'
        );

        if (!$query) {
            throw new RuntimeException(mysqli_error($conn));
        }

        $promos = [];

        while ($row = mysqli_fetch_assoc($query)) {
            $promos[] = $row;
        }

        json_response(
            true,
            'Daftar promo berhasil diambil',
            ['promos' => $promos, 'voucher_services'=>['all','TransRide','TransCar','TransFood'], 'voucher_discount_scope'=>'delivery']
        );
    }

    if ($action === 'create' || $action === 'update') {
        $id = (int)($input['id'] ?? 0);

        $title = safe_string(
            $input['title'] ?? ''
        );

        $description = safe_string(
            $input['description'] ?? ''
        );

        if ($title === '' || $description === '') {
            json_response(
                false,
                'Judul dan deskripsi wajib diisi',
                [],
                422
            );
        }

        $promoCode = strtoupper(safe_string(
            $input['promo_code'] ?? ''
        ));

        $discountType = safe_string($input['discount_type'] ?? 'percent', 'percent');
        if (!in_array($discountType, ['percent', 'fixed'], true)) $discountType = 'percent';
        $discountValue = parse_nonnegative_int($input['discount_value'] ?? 0);
        $maxDiscountRaw = parse_nonnegative_int($input['max_discount'] ?? 0);
        $maxDiscount = $maxDiscountRaw > 0 ? $maxDiscountRaw : null;
        $minOrder = parse_nonnegative_int($input['min_order'] ?? 0);
        $serviceType = safe_string($input['service_type'] ?? 'all', 'all');
        if (!in_array($serviceType, ['all', 'TransRide', 'TransCar', 'TransFood'], true)) json_response(false, 'Layanan voucher tidak valid.', [], 422);
        $quotaRaw = parse_nonnegative_int($input['quota_total'] ?? 0);
        $quotaTotal = $quotaRaw > 0 ? $quotaRaw : null;
        $perUserLimit = max(1, parse_nonnegative_int($input['per_user_limit'] ?? 1, 1));
        if ($promoCode !== '' && $discountValue <= 0) {
            json_response(false, 'Nilai diskon voucher harus lebih dari 0', [], 422);
        }
        if ($discountType === 'percent' && $discountValue > 100) {
            json_response(false, 'Diskon persen maksimal 100%', [], 422);
        }

        $imageUrl = safe_string(
            $input['image_url'] ?? ''
        );

        $actionUrl = safe_string(
            $input['action_url'] ?? ''
        );
        if ($actionUrl !== '' && stripos($actionUrl, 'https://') !== 0) {
            json_response(false, 'Link aksi notifikasi harus menggunakan HTTPS', [], 422);
        }

        $targetRole = safe_string(
            $input['target_role'] ?? 'all',
            'all'
        );

        $roles = [
            'all',
            'customer',
            'driver',
            'merchant',
            'admin',
            'wisata'
        ];

        if (!in_array($targetRole, $roles, true)) {
            $targetRole = 'all';
        }

        $sortOrder = (int)($input['sort_order'] ?? 0);

        $isActive =
            (int)($input['is_active'] ?? 1) === 1
                ? 1
                : 0;

        $startsAt = nullable_datetime(
            $input['starts_at'] ?? null
        );

        $endsAt = nullable_datetime(
            $input['ends_at'] ?? null
        );

        if (
            $startsAt !== null
            && $endsAt !== null
            && strtotime($endsAt) <= strtotime($startsAt)
        ) {
            json_response(
                false,
                'Waktu berakhir harus setelah waktu mulai',
                [],
                422
            );
        }

        mysqli_begin_transaction($conn);

        try {
            if ($action === 'create') {
                $stmt = mysqli_prepare(
                    $conn,
                    'INSERT INTO customer_promos
                    (
                        title,
                        description,
                        promo_code,
                        discount_type,
                        discount_value,
                        max_discount,
                        min_order,
                        service_type,
                        quota_total,
                        per_user_limit,
                        image_url,
                        action_url,
                        target_role,
                        sort_order,
                        is_active,
                        starts_at,
                        ends_at,
                        created_at,
                        updated_at
                    )
                    VALUES
                    (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())'
                );

                if (!$stmt) {
                    throw new RuntimeException(
                        mysqli_error($conn)
                    );
                }

                mysqli_stmt_bind_param(
                    $stmt,
                    'ssssiiisiisssiiss',
                    $title,
                    $description,
                    $promoCode,
                    $discountType,
                    $discountValue,
                    $maxDiscount,
                    $minOrder,
                    $serviceType,
                    $quotaTotal,
                    $perUserLimit,
                    $imageUrl,
                    $actionUrl,
                    $targetRole,
                    $sortOrder,
                    $isActive,
                    $startsAt,
                    $endsAt
                );

                if (!mysqli_stmt_execute($stmt)) {
                    throw new RuntimeException(
                        mysqli_stmt_error($stmt)
                    );
                }

                $id = mysqli_insert_id($conn);
                mysqli_stmt_close($stmt);

            } else {
                if (
                    $id <= 0
                    || promo_by_id($conn, $id) === null
                ) {
                    throw new RuntimeException(
                        'Promo tidak ditemukan'
                    );
                }

                $stmt = mysqli_prepare(
                    $conn,
                    'UPDATE customer_promos
                     SET
                        title = ?,
                        description = ?,
                        promo_code = ?,
                        discount_type = ?,
                        discount_value = ?,
                        max_discount = ?,
                        min_order = ?,
                        service_type = ?,
                        quota_total = ?,
                        per_user_limit = ?,
                        image_url = ?,
                        action_url = ?,
                        target_role = ?,
                        sort_order = ?,
                        is_active = ?,
                        starts_at = ?,
                        ends_at = ?,
                        updated_at = NOW()
                     WHERE id = ?'
                );

                if (!$stmt) {
                    throw new RuntimeException(
                        mysqli_error($conn)
                    );
                }

                mysqli_stmt_bind_param(
                    $stmt,
                    'ssssiiisiisssiissi',
                    $title,
                    $description,
                    $promoCode,
                    $discountType,
                    $discountValue,
                    $maxDiscount,
                    $minOrder,
                    $serviceType,
                    $quotaTotal,
                    $perUserLimit,
                    $imageUrl,
                    $actionUrl,
                    $targetRole,
                    $sortOrder,
                    $isActive,
                    $startsAt,
                    $endsAt,
                    $id
                );

                if (!mysqli_stmt_execute($stmt)) {
                    throw new RuntimeException(
                        mysqli_stmt_error($stmt)
                    );
                }

                mysqli_stmt_close($stmt);
            }

            mysqli_commit($conn);

        } catch (Throwable $error) {
            mysqli_rollback($conn);
            throw $error;
        }

        $promo = promo_by_id($conn, $id);
        $notification = null;

        if (
            (int)($input['send_notification'] ?? 0) === 1
            && $promo !== null
        ) {
            $notification = send_promo_notification($promo);
            if ((bool)($notification['success'] ?? false)) {
                @transiva_schema_query($conn, 'UPDATE customer_promos SET notification_sent_count = notification_sent_count + 1, last_notified_at = NOW() WHERE id = ' . (int)$id);
                $promo = promo_by_id($conn, $id);
            }
        }

        // Promo tetap dianggap berhasil disimpan meskipun push gagal.
        $notificationSuccess =
            $notification === null
            || (bool)($notification['success'] ?? false);

        $message = $notificationSuccess
            ? 'Promo berhasil disimpan'
            : 'Promo berhasil disimpan, tetapi notifikasi gagal';

        json_response(
            true,
            $message,
            [
                'promo' => $promo,
                'notification' => $notification,
                'notification_success' =>
                    $notificationSuccess
            ]
        );
    }

    $id = (int)($input['id'] ?? 0);

    if ($id <= 0) {
        json_response(
            false,
            'ID promo tidak valid',
            [],
            422
        );
    }

    $promo = promo_by_id($conn, $id);

    if ($promo === null) {
        json_response(
            false,
            'Promo tidak ditemukan',
            [],
            404
        );
    }

    if ($action === 'toggle') {
        $active =
            (int)($input['is_active'] ?? 0) === 1
                ? 1
                : 0;

        $stmt = mysqli_prepare(
            $conn,
            'UPDATE customer_promos
             SET is_active = ?, updated_at = NOW()
             WHERE id = ?'
        );

        mysqli_stmt_bind_param($stmt, 'ii', $active, $id);

        if (!mysqli_stmt_execute($stmt)) {
            throw new RuntimeException(
                mysqli_stmt_error($stmt)
            );
        }

        mysqli_stmt_close($stmt);

        json_response(
            true,
            $active
                ? 'Promo berhasil diaktifkan'
                : 'Promo berhasil dinonaktifkan'
        );
    }

    if ($action === 'delete') {
        $stmt = mysqli_prepare(
            $conn,
            'DELETE FROM customer_promos WHERE id = ?'
        );

        mysqli_stmt_bind_param($stmt, 'i', $id);

        if (!mysqli_stmt_execute($stmt)) {
            throw new RuntimeException(
                mysqli_stmt_error($stmt)
            );
        }

        mysqli_stmt_close($stmt);

        json_response(true, 'Promo berhasil dihapus');
    }

    if ($action === 'send_notification') {
        $notification = send_promo_notification($promo);
        $ok = (bool)($notification['success'] ?? false);
        if ($ok) {
            @transiva_schema_query($conn, 'UPDATE customer_promos SET notification_sent_count = notification_sent_count + 1, last_notified_at = NOW() WHERE id = ' . (int)$id);
        }

        json_response(
            $ok,
            $ok
                ? 'Notifikasi promo berhasil diproses'
                : safe_string(
                    $notification['message']
                    ?? 'Notifikasi gagal'
                ),
            ['notification' => $notification],
            $ok ? 200 : 500
        );
    }

    json_response(
        false,
        'Action tidak dikenal',
        [],
        400
    );

} catch (Throwable $error) {
    json_response(
        false,
        $error->getMessage(),
        [],
        500
    );
}
