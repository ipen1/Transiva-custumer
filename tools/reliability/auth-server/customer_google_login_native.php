<?php

declare(strict_types=1);

ini_set('display_errors', '0');
ini_set('display_startup_errors', '0');
error_reporting(E_ALL);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');

$transivaEnv = [];
$transivaEnvPath = dirname(__DIR__, 2) . '/transiva-env.php';
if (is_file($transivaEnvPath)) {
    $loadedEnv = require $transivaEnvPath;
    if (is_array($loadedEnv)) {
        $transivaEnv = $loadedEnv;
    }
}

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/device_security.php';
require_once __DIR__ . '/native_auth.php';
require_once __DIR__ . '/customer_registration_helper.php';
require_once __DIR__ . '/customer_auth_lookup_helper.php';
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
require_once __DIR__ . '/transiva_push_helper.php';

const CUSTOMER_TOKEN_LIFETIME = 2592000;

function cln_out(bool $success, string $message, array $extra = [], int $status = 200, string $code = ''): void
{
    http_response_code($status);
    echo json_encode(array_merge([
        'success' => $success,
        'message' => $message,
        'code' => $code,
    ], $extra), JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function cln_customer_role(string $role): bool
{
    $role = strtolower(trim($role));
    return in_array($role, ['', 'customer', 'pelanggan', 'user'], true);
}

function cln_device_bind(mysqli $conn, int $userId, array $in, bool $multiDeviceReviewer = false): array
{
    $uuid = device_normalize_uuid((string)($in['installation_uuid'] ?? ''));
    if (!device_uuid_valid($uuid)) {
        cln_out(false, 'Identitas perangkat tidak valid. Perbarui aplikasi Transiva.', [], 422, 'DEVICE_UUID_REQUIRED');
    }

    $hash = device_uuid_hash($uuid);
    $deviceName = mb_substr(trim((string)($in['device_name'] ?? 'Android')), 0, 150);
    $manufacturer = mb_substr(trim((string)($in['manufacturer'] ?? '')), 0, 100);
    $model = mb_substr(trim((string)($in['model'] ?? '')), 0, 100);
    $androidVersion = mb_substr(trim((string)($in['android_version'] ?? '')), 0, 40);
    $appVersion = mb_substr(trim((string)($in['app_version'] ?? '')), 0, 40);
    $platform = mb_substr(trim((string)($in['platform'] ?? 'android_native')), 0, 40);
    $fcmToken = trim((string)($in['fcm_token'] ?? ''));

    mysqli_begin_transaction($conn);
    try {
        $lock = mysqli_prepare($conn, 'SELECT active_customer_device_id FROM users WHERE id=? LIMIT 1 FOR UPDATE');
        if (!$lock) throw new RuntimeException('USER_DEVICE_LOCK_PREPARE: ' . mysqli_error($conn));
        mysqli_stmt_bind_param($lock, 'i', $userId);
        if (!mysqli_stmt_execute($lock)) throw new RuntimeException('USER_DEVICE_LOCK_EXEC: ' . mysqli_stmt_error($lock));
        $userRow = mysqli_fetch_assoc(mysqli_stmt_get_result($lock));
        mysqli_stmt_close($lock);
        if (!$userRow) throw new RuntimeException('USER_NOT_FOUND_AFTER_AUTH');
        $activeId = (int)($userRow['active_customer_device_id'] ?? 0);

        $byHash = mysqli_prepare($conn, 'SELECT id,user_id,status,ban_reason,device_name,fcm_token FROM customer_devices WHERE device_uuid_hash=? LIMIT 1 FOR UPDATE');
        if (!$byHash) throw new RuntimeException('DEVICE_LOOKUP_PREPARE: ' . mysqli_error($conn));
        mysqli_stmt_bind_param($byHash, 's', $hash);
        if (!mysqli_stmt_execute($byHash)) throw new RuntimeException('DEVICE_LOOKUP_EXEC: ' . mysqli_stmt_error($byHash));
        $device = mysqli_fetch_assoc(mysqli_stmt_get_result($byHash));
        mysqli_stmt_close($byHash);

        if ($device && strtolower((string)($device['status'] ?? '')) === 'banned') {
            mysqli_rollback($conn);
            cln_out(false, 'Perangkat ini telah diblokir oleh admin.', [
                'reason' => (string)($device['ban_reason'] ?? ''),
            ], 403, 'DEVICE_BANNED');
        }

        $handover = false;
        $replacedDeviceId = 0;
        $replacedDeviceName = '';
        $replacedFcm = '';

        if (!$multiDeviceReviewer && $activeId > 0) {
            $activeStmt = mysqli_prepare($conn, 'SELECT id,device_uuid_hash,status,device_name,fcm_token FROM customer_devices WHERE id=? AND user_id=? LIMIT 1 FOR UPDATE');
            if (!$activeStmt) throw new RuntimeException('ACTIVE_DEVICE_PREPARE: ' . mysqli_error($conn));
            mysqli_stmt_bind_param($activeStmt, 'ii', $activeId, $userId);
            if (!mysqli_stmt_execute($activeStmt)) throw new RuntimeException('ACTIVE_DEVICE_EXEC: ' . mysqli_stmt_error($activeStmt));
            $active = mysqli_fetch_assoc(mysqli_stmt_get_result($activeStmt));
            mysqli_stmt_close($activeStmt);

            if ($active && strtolower((string)($active['status'] ?? '')) === 'active') {
                $activeHash = (string)($active['device_uuid_hash'] ?? '');
                if ($activeHash !== '' && !hash_equals($activeHash, $hash)) {
                    // Login valid dari perangkat B langsung mengambil alih sesi.
                    // Perangkat A ditandai reset dan seluruh token lamanya akan dicabut.
                    // Ini juga membuat login setelah "Hapus data" di HP yang sama tetap bisa
                    // masuk meskipun installation UUID aplikasi berubah.
                    $handover = true;
                    $replacedDeviceId = (int)$active['id'];
                    $replacedDeviceName = trim((string)($active['device_name'] ?? 'Perangkat lama'));
                    $replacedFcm = trim((string)($active['fcm_token'] ?? ''));
                    $resetReason = 'Sesi otomatis dipindahkan karena login baru';

                    $resetOld = mysqli_prepare($conn, "UPDATE customer_devices SET status='reset',reset_at=NOW(),reset_by=NULL,reset_reason=?,updated_at=NOW() WHERE id=? AND user_id=? LIMIT 1");
                    if (!$resetOld) throw new RuntimeException('OLD_DEVICE_RESET_PREPARE: ' . mysqli_error($conn));
                    mysqli_stmt_bind_param($resetOld, 'sii', $resetReason, $replacedDeviceId, $userId);
                    if (!mysqli_stmt_execute($resetOld)) throw new RuntimeException('OLD_DEVICE_RESET_EXEC: ' . mysqli_stmt_error($resetOld));
                    mysqli_stmt_close($resetOld);
                }
            }
        }

        if ($device) {
            $deviceId = (int)$device['id'];
            if ((int)($device['user_id'] ?? 0) !== $userId && strtolower((string)($device['status'] ?? '')) === 'active') {
                mysqli_rollback($conn);
                cln_out(false, 'Perangkat ini masih terhubung dengan akun customer lain.', [], 409, 'DEVICE_BOUND_TO_OTHER_ACCOUNT');
            }

            $up = mysqli_prepare($conn, "UPDATE customer_devices SET user_id=?,installation_uuid=?,device_name=?,manufacturer=?,model=?,android_version=?,app_version=?,platform=?,fcm_token=?,status='active',bound_at=IF(status='active',bound_at,NOW()),last_login_at=NOW(),last_seen_at=NOW(),reset_at=NULL,reset_by=NULL,reset_reason=NULL WHERE id=? LIMIT 1");
            if (!$up) throw new RuntimeException('DEVICE_UPDATE_PREPARE: ' . mysqli_error($conn));
            mysqli_stmt_bind_param($up, 'issssssssi', $userId,$uuid,$deviceName,$manufacturer,$model,$androidVersion,$appVersion,$platform,$fcmToken,$deviceId);
            if (!mysqli_stmt_execute($up)) throw new RuntimeException('DEVICE_UPDATE_EXEC: ' . mysqli_stmt_error($up));
            mysqli_stmt_close($up);
        } else {
            $ins = mysqli_prepare($conn, "INSERT INTO customer_devices(user_id,installation_uuid,device_uuid_hash,device_name,manufacturer,model,android_version,app_version,platform,fcm_token,status,bound_at,last_login_at,last_seen_at) VALUES(?,?,?,?,?,?,?,?,?,?,'active',NOW(),NOW(),NOW())");
            if (!$ins) throw new RuntimeException('DEVICE_INSERT_PREPARE: ' . mysqli_error($conn));
            mysqli_stmt_bind_param($ins, 'isssssssss', $userId,$uuid,$hash,$deviceName,$manufacturer,$model,$androidVersion,$appVersion,$platform,$fcmToken);
            if (!mysqli_stmt_execute($ins)) throw new RuntimeException('DEVICE_INSERT_EXEC: ' . mysqli_stmt_error($ins));
            $deviceId = (int)mysqli_insert_id($conn);
            mysqli_stmt_close($ins);
        }

        // Akun Google Play reviewer boleh memiliki beberapa perangkat aktif sekaligus.
        // Pointer active_customer_device_id tetap diperbarui untuk kompatibilitas UI/admin lama,
        // tetapi bukan satu-satunya perangkat valid untuk akun reviewer.
        $set = mysqli_prepare($conn, 'UPDATE users SET active_customer_device_id=?,device_binding_updated_at=NOW() WHERE id=? LIMIT 1');
        if (!$set) throw new RuntimeException('USER_BIND_PREPARE: ' . mysqli_error($conn));
        mysqli_stmt_bind_param($set, 'ii', $deviceId, $userId);
        if (!mysqli_stmt_execute($set)) throw new RuntimeException('USER_BIND_EXEC: ' . mysqli_stmt_error($set));
        mysqli_stmt_close($set);

        // Customer normal: login baru mencabut sesi customer lama (kebijakan 1 perangkat).
        // Akun reviewer Play Console: jangan cabut token perangkat reviewer lainnya.
        if (!$multiDeviceReviewer) {
            $revoke = mysqli_prepare($conn, "UPDATE native_api_tokens SET revoked_at=NOW(),revoke_reason=IF(?, 'customer_session_replaced', 'customer_relogin'),fcm_token=NULL WHERE user_id=? AND app_scope='customer' AND revoked_at IS NULL");
            if (!$revoke) throw new RuntimeException('TOKEN_REVOKE_PREPARE: ' . mysqli_error($conn));
            $handoverInt = $handover ? 1 : 0;
            mysqli_stmt_bind_param($revoke, 'ii', $handoverInt, $userId);
            if (!mysqli_stmt_execute($revoke)) throw new RuntimeException('TOKEN_REVOKE_EXEC: ' . mysqli_stmt_error($revoke));
            mysqli_stmt_close($revoke);
        }

        mysqli_commit($conn);
        return [
            'device_id' => $deviceId,
            'device_uuid_hash' => $hash,
            'installation_uuid' => $uuid,
            'session_handover' => $handover,
            'replaced_device_id' => $replacedDeviceId,
            'replaced_device_name' => $replacedDeviceName,
            'replaced_fcm_token' => $replacedFcm,
        ];
    } catch (Throwable $e) {
        mysqli_rollback($conn);
        throw $e;
    }
}

function cln_notify_replaced_session(array $device): void
{
    if (empty($device['session_handover'])) return;
    $oldFcm = trim((string)($device['replaced_fcm_token'] ?? ''));
    if ($oldFcm === '') return;

    try {
        $account = transiva_push_service_account();
        $accessToken = transiva_push_access_token();
        transiva_push_send_token(
            $oldFcm,
            'Sesi Transiva dipindahkan',
            'Akun Anda telah login di perangkat lain.',
            [
                'type' => 'force_logout',
                'force_logout' => '1',
                'reason' => 'SESSION_REPLACED',
                'code' => 'SESSION_REPLACED',
            ],
            $accessToken,
            (string)$account['project_id']
        );
    } catch (Throwable $pushError) {
        // Keamanan tidak bergantung pada push. Token lama sudah dicabut di DB.
        error_log('Customer session replacement push failed: ' . $pushError->getMessage());
    }
}


try {
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
        cln_out(false, 'Method tidak diizinkan.', [], 405, 'METHOD_NOT_ALLOWED');
    }
    $raw = file_get_contents('php://input');
    $in = json_decode((string)$raw, true);
    if (!is_array($in)) cln_out(false, 'Format request tidak valid.', [], 400, 'INVALID_JSON');

    $requestedScope = strtolower(trim((string)($in['app_scope'] ?? 'customer')));
    if ($requestedScope !== 'customer') cln_out(false, 'Endpoint ini khusus aplikasi Customer.', [], 403, 'CUSTOMER_ONLY');

    $idToken = trim((string)($in['id_token'] ?? ''));
    if ($idToken === '') cln_out(false, 'Token Google tidak ditemukan.', [], 422, 'GOOGLE_TOKEN_REQUIRED');

    $expectedAud = trim((string)($transivaEnv['TRANSIVA_GOOGLE_WEB_CLIENT_ID'] ?? ''));
    if ($expectedAud === '') {
        $expectedAud = trim((string)(getenv('TRANSIVA_GOOGLE_WEB_CLIENT_ID') ?: ''));
    }
    if ($expectedAud === '') {
        cln_out(false, 'Google Login belum dikonfigurasi di server.', [], 503, 'GOOGLE_SERVER_NOT_CONFIGURED');
    }

    $url = 'https://oauth2.googleapis.com/tokeninfo?id_token=' . rawurlencode($idToken);
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 8,
        CURLOPT_TIMEOUT => 12,
        CURLOPT_HTTPHEADER => ['Accept: application/json'],
    ]);
    $verifiedRaw = curl_exec($ch);
    $verifiedCode = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $curlError = curl_error($ch);
    curl_close($ch);
    if ($verifiedRaw === false || $verifiedCode !== 200) {
        error_log('Google token verify failed HTTP=' . $verifiedCode . ' err=' . $curlError);
        cln_out(false, 'Token Google tidak valid atau sudah kedaluwarsa.', [], 401, 'GOOGLE_TOKEN_INVALID');
    }
    $google = json_decode((string)$verifiedRaw, true);
    if (!is_array($google)) cln_out(false, 'Verifikasi Google gagal.', [], 401, 'GOOGLE_VERIFY_INVALID');

    $aud = trim((string)($google['aud'] ?? ''));
    $iss = trim((string)($google['iss'] ?? ''));
    $email = strtolower(trim((string)($google['email'] ?? '')));
    $emailVerified = strtolower((string)($google['email_verified'] ?? 'false'));
    if (!hash_equals($expectedAud, $aud)) cln_out(false, 'OAuth client Google tidak cocok.', [], 401, 'GOOGLE_AUDIENCE_MISMATCH');
    if (!in_array($iss, ['accounts.google.com', 'https://accounts.google.com'], true)) cln_out(false, 'Penerbit token Google tidak valid.', [], 401, 'GOOGLE_ISSUER_INVALID');
    if ((int)($google['exp'] ?? 0) <= time()) cln_out(false, 'Token Google kedaluwarsa.', [], 401, 'GOOGLE_TOKEN_EXPIRED');
    if ($email === '' || !in_array($emailVerified, ['true', '1'], true)) cln_out(false, 'Email Google belum terverifikasi.', [], 403, 'GOOGLE_EMAIL_UNVERIFIED');

    $stmt = mysqli_prepare($conn, 'SELECT id,username,email,profile_photo,role,balance,restaurant_id,wisata_owner_id,delivery_address,delivery_lat,delivery_lng,email_verified,verified_by_admin,email_verified_at FROM users WHERE LOWER(email)=LOWER(?) AND LOWER(role)=\'customer\' LIMIT 1');
    if (!$stmt) throw new RuntimeException('GOOGLE_LOGIN_PREPARE: ' . mysqli_error($conn));
    mysqli_stmt_bind_param($stmt, 's', $email);
    if (!mysqli_stmt_execute($stmt)) throw new RuntimeException('GOOGLE_LOGIN_EXEC: ' . mysqli_stmt_error($stmt));
    $user = mysqli_fetch_assoc(mysqli_stmt_get_result($stmt));
    mysqli_stmt_close($stmt);

    if (!$user) {
        if(!cal_signup_ready($conn))cln_out(false,'Pendaftaran belum diaktifkan oleh admin. Silakan hubungi admin Transiva.',[],503,'AUTH_SCHEMA_REQUIRED');
        if(empty($in['signup_username']) || empty($in['signup_phone']))cln_out(false,'Lengkapi nama pengguna dan nomor HP untuk membuat akun Google.', ['email'=>$email],422,'GOOGLE_REGISTRATION_REQUIRED');
        $registration=['username'=>$in['signup_username'],'phone'=>$in['signup_phone'],'email'=>$email,
            'password'=>bin2hex(random_bytes(32)),'operation_id'=>'google-'.hash('sha256',$google['sub'].':'.$email.':'.strtolower(trim($in['signup_username'])).':'.cr_phone($in['signup_phone']))];
        $created=cr_register($conn,$registration,true);
        if(!$created['success'])cln_out(false,$created['message'],[],409,'ACCOUNT_EXISTS');
        $stmt=mysqli_prepare($conn,"SELECT * FROM users WHERE email=? AND role='customer' LIMIT 1");mysqli_stmt_bind_param($stmt,'s',$email);mysqli_stmt_execute($stmt);$user=mysqli_fetch_assoc(mysqli_stmt_get_result($stmt));
        if(!$user)throw new RuntimeException('GOOGLE_USER_NOT_FOUND');
    }
    $uid=(int)$user['id'];
    $verifiedStmt=$conn->prepare('UPDATE users SET email_verified=1,email_verified_at=COALESCE(email_verified_at,NOW()) WHERE id=?');$verifiedStmt->bind_param('i',$uid);$verifiedStmt->execute();
    if (!cln_customer_role((string)($user['role'] ?? ''))) {
        cln_out(false, 'Akun ini bukan akun customer.', [], 403, 'APP_SCOPE_ROLE_MISMATCH');
    }

    $device = cln_device_bind($conn, (int)$user['id'], $in, false);
    $apiToken = native_issue_token(
        $conn, (int)$user['id'], (string)($in['device_name'] ?? 'Android'),
        (string)($in['platform'] ?? 'android_native'), (string)($in['fcm_token'] ?? ''),
        (int)$device['device_id'], (string)$device['device_uuid_hash'], 'customer'
    );
    cln_notify_replaced_session($device);

    $fcm = trim((string)($in['fcm_token'] ?? ''));
    if ($fcm !== '') {
        $uf = mysqli_prepare($conn, 'UPDATE users SET fcm_token=?,email_verified=1,email_verified_at=COALESCE(email_verified_at,NOW()) WHERE id=? LIMIT 1');
        if ($uf) { $uid=(int)$user['id']; mysqli_stmt_bind_param($uf,'si',$fcm,$uid); mysqli_stmt_execute($uf); mysqli_stmt_close($uf); }
    }

    $photo = trim((string)($user['profile_photo'] ?? ''));
    if ($photo === '') $photo = trim((string)($google['picture'] ?? ''));
    $payload = [
        'id'=>(int)$user['id'], 'user_id'=>(int)$user['id'],
        'username'=>(string)$user['username'], 'name'=>(string)$user['username'],
        'email'=>(string)$user['email'], 'role'=>'customer', 'photo'=>$photo, 'profile_photo'=>$photo,
        'balance'=>(int)($user['balance'] ?? 0), 'restaurant_id'=>$user['restaurant_id'] ?? null,
        'wisata_owner_id'=>$user['wisata_owner_id'] ?? null,
        'delivery_address'=>(string)($user['delivery_address'] ?? ''),
        'delivery_lat'=>(string)($user['delivery_lat'] ?? ''), 'delivery_lng'=>(string)($user['delivery_lng'] ?? ''),
        'email_verified'=>1, 'verified_by_admin'=>(int)($user['verified_by_admin'] ?? 0),
        'email_verified_at'=>$user['email_verified_at'] ?? null,
        'token'=>$apiToken, 'device_id'=>(int)$device['device_id'],
        'installation_uuid'=>(string)$device['installation_uuid'], 'app_scope'=>'customer',
        'auth_provider'=>'google'
    ];
    cln_out(true, 'Login Google berhasil', [
        'user'=>$payload, 'token'=>$apiToken, 'token_type'=>'Bearer',
        'expires_in'=>CUSTOMER_TOKEN_LIFETIME,
        'session_handover'=>!empty($device['session_handover']),
        'replaced_device_name'=>(string)($device['replaced_device_name'] ?? '')
    ], 200, 'GOOGLE_LOGIN_SUCCESS');
} catch (Throwable $e) {
    error_log('Customer Google native login error: ' . $e->getMessage());
    cln_out(false, 'Terjadi kesalahan server saat login Google.', [], 500, 'CUSTOMER_GOOGLE_LOGIN_SERVER_ERROR');
}
