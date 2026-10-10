<?php

require_once __DIR__.'/schema_runtime_guard.php';
// Shared schema + payment helpers for TransRide/TransCar/TransFood/TransSend.

function pu_table_exists(mysqli $conn, string $table): bool
{
    if ($table === '') {
        return false;
    }

    // SHOW TABLES LIKE ? tidak dapat diprepare secara konsisten pada beberapa versi MySQL/MariaDB.
    // information_schema aman untuk prepared statement dan tidak memicu fatal error.
    $sql = "SELECT 1
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = ?
            LIMIT 1";

    $stmt = mysqli_prepare($conn, $sql);
    if (!$stmt) {
        error_log('pu_table_exists prepare gagal: ' . mysqli_error($conn));
        return false;
    }

    mysqli_stmt_bind_param($stmt, 's', $table);

    if (!mysqli_stmt_execute($stmt)) {
        error_log('pu_table_exists execute gagal: ' . mysqli_stmt_error($stmt));
        mysqli_stmt_close($stmt);
        return false;
    }

    mysqli_stmt_store_result($stmt);
    $exists = mysqli_stmt_num_rows($stmt) > 0;
    mysqli_stmt_close($stmt);

    return $exists;
}

function pu_safe_table_name(string $table): string
{
    return preg_match('/^[A-Za-z0-9_]+$/', $table) ? $table : '';
}

function pu_columns(mysqli $conn, string $table): array
{
    $table = pu_safe_table_name($table);
    if ($table === '' || !pu_table_exists($conn, $table)) {
        return [];
    }

    $out = [];
    $query = transiva_schema_query($conn, "SHOW COLUMNS FROM `{$table}`");

    if (!$query) {
        error_log("pu_columns gagal untuk {$table}: " . mysqli_error($conn));
        return [];
    }

    while ($row = mysqli_fetch_assoc($query)) {
        $field = (string)($row['Field'] ?? '');
        if ($field !== '') {
            $out[$field] = true;
        }
    }

    mysqli_free_result($query);
    return $out;
}

function pu_ensure_schema(mysqli $conn, string $table): array
{
    // Struktur payment/order sudah final. Hanya baca metadata, tanpa ALTER TABLE runtime.
    $table = pu_safe_table_name($table);
    if ($table === '' || !pu_table_exists($conn, $table)) {
        return [];
    }
    return pu_columns($conn, $table);
}


function pu_payment_method(array $row): string
{
    $method = strtolower(trim((string)($row['payment_method'] ?? '')));

    if ($method === '') {
        $note = json_decode((string)($row['note'] ?? ''), true);
        if (is_array($note)) {
            $method = strtolower(trim((string)($note['payment_method'] ?? '')));
        }
    }

    return in_array(
        $method,
        ['balance', 'saldo', 'wallet', 'transpay', 'transiva_pay', 'transivapay'],
        true
    ) ? 'balance' : 'cash';
}

function pu_payment_label(array $row): string
{
    return pu_payment_method($row) === 'balance' ? 'TransPay' : 'Tunai';
}

function pu_canonical_room(string $source, array $row): string
{
    $room = trim((string)($row['room_id'] ?? ''));
    if ($room !== '') {
        return strtoupper(preg_replace('/[^A-Z0-9\-]/', '-', str_replace('_', '-', $room)));
    }

    $public = trim((string)($row['order_id'] ?? $row['id'] ?? ''));
    return 'ROOM-'
        . ($source === 'pickup_orders' ? 'SEND-' : 'ORDER-')
        . strtoupper(preg_replace('/[^A-Z0-9]/', '', $public));
}

function pu_notify_username(
    mysqli $conn,
    string $username,
    string $title,
    string $body,
    array $data = []
): bool {
    if ($username === '') {
        return false;
    }

    if (function_exists('transiva_get_user_by_username')
        && function_exists('transiva_send_fcm_token')) {
        $user = transiva_get_user_by_username($conn, $username);
        $token = trim((string)($user['fcm_token'] ?? ''));

        return $token !== ''
            ? (bool)transiva_send_fcm_token($token, $title, $body, $data)
            : false;
    }

    return false;
}
