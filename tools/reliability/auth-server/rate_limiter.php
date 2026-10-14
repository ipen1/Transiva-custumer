<?php

declare(strict_types=1);

/** File-backed fixed-window limiter designed for shared hosting. */
function transiva_rate_limit_bootstrap(): void
{
    if (PHP_SAPI === 'cli') {
        return;
    }

    $script = strtolower(basename((string)($_SERVER['SCRIPT_NAME'] ?? $_SERVER['PHP_SELF'] ?? 'unknown.php')));

    // Bypass TERKONTROL untuk stress test read-only. Rate limit endpoint lain tetap aktif.
    if (transiva_is_stress_target($script)) {
        if (transiva_rate_limit_stress_bypass($script)) {
            header('X-Transiva-RateLimit: stress-test-bypass');
            header('X-Transiva-Stress-Auth: valid');
            return;
        }

        // Jangan diam-diam memakai limiter default: itu membuat 240 sukses lalu 429
        // dan menyamarkan konfigurasi secret yang salah.
        header('X-Transiva-Stress-Auth: invalid');
        http_response_code(403);
        exit(json_encode([
            'success' => false,
            'code' => 'STRESS_KEY_INVALID',
            'message' => 'Stress test key tidak valid atau tidak terbaca server.',
        ], JSON_UNESCAPED_UNICODE));
    }

    [$limit, $window] = transiva_rate_limit_policy($script);
    $ip = transiva_rate_limit_client_ip();
    $root = dirname(__DIR__, 2) . '/transiva-runtime/rate-limit';
    // Independent IP ceiling: changing unverified device/Bearer headers cannot bypass this.
    $ipLimit = transiva_rate_limit_ip_ceiling($script, $limit);
    $ipState = transiva_rate_limit_consume($root, hash('sha256', $script . '|ip|' . $ip), $ipLimit, $window);
    if ($ipState !== null && $ipState['blocked']) transiva_rate_limit_reject($ipState['reset']);
    $identity = transiva_rate_limit_identity();
    $key = hash('sha256', $script . '|identity|' . $ip . '|' . $identity);
    $state = transiva_rate_limit_consume($root, $key, $limit, $window);
    if ($state === null || $ipState === null) {
        error_log('Transiva rate-limit storage unavailable for ' . $script);
        // Authentication/OTP must not silently lose brute-force protection.
        if (preg_match('/(login|register|otp|password|pin|verifyemail|verification|auth_check)/', $script)) {
            http_response_code(503); header('Retry-After: 30');
            exit(json_encode(['success'=>false,'code'=>'SECURITY_TEMPORARILY_UNAVAILABLE',
                'message'=>'Layanan keamanan sedang sibuk. Coba kembali sebentar lagi.']));
        }
        return;
    }
    if (preg_match('/(login|register)/', $script)) {
        $body=json_decode((string)file_get_contents('php://input'),true);
        $target=strtolower(trim((string)($body['username']??$body['email']??'')));
        if($target!==''){
            $accountState=transiva_rate_limit_consume($root,hash('sha256',$script.'|target|'.$target),max(30,$limit*2),$window);
            if($accountState===null){http_response_code(503);exit(json_encode(['success'=>false,'message'=>'Keamanan sedang sibuk.']));}
            if($accountState['blocked'])transiva_rate_limit_reject($accountState['reset']);
        }
    }
    header('X-RateLimit-Limit: ' . $limit);
    header('X-RateLimit-Remaining: ' . $state['remaining']);
    header('X-RateLimit-Reset: ' . $state['reset']);
    if ($state['blocked']) transiva_rate_limit_reject($state['reset']);
    if (random_int(1, 500) === 1) transiva_rate_limit_cleanup($root, time() - 86400);
}

function transiva_rate_limit_ip_ceiling(string $script, int $limit): int {
    // Allow shared NAT/mobile networks while bounding header-rotation abuse.
    if (preg_match('/(otp|verifyemail|verification)/', $script)) return max(300, $limit * 50);
    if (preg_match('/(login|register|pin|password|session)/', $script)) return max(600, $limit * 40);
    return $limit * 20;
}

function transiva_rate_limit_reject(int $reset): void {
    $retry = max(1, $reset - time());
    http_response_code(429); header('Retry-After: ' . $retry);
    exit(json_encode(['success'=>false,'code'=>'RATE_LIMIT_EXCEEDED',
        'message'=>'Terlalu banyak permintaan. Silakan coba kembali beberapa saat lagi.','retry_after'=>$retry],JSON_UNESCAPED_UNICODE));
}

/** Atomic fixed-window bucket, also callable from CLI regression tests. */
function transiva_rate_limit_consume(string $root, string $key, int $limit, int $window, ?int $now = null): ?array {
    if (!preg_match('/^[0-9a-f]{64}$/', $key) || $limit < 1 || $window < 1) return null;
    $bucket = $root . '/' . substr($key, 0, 2);
    if (!is_dir($bucket) && !@mkdir($bucket, 0700, true) && !is_dir($bucket)) return null;
    $handle = @fopen($bucket . '/' . $key . '.json', 'c+');
    if ($handle === false) return null;
    $now = $now ?? time();
    try {
        if (!flock($handle, LOCK_EX)) return null;
        rewind($handle); $raw = stream_get_contents($handle, 4096);
        $state = $raw ? json_decode($raw, true) : null;
        if (!is_array($state) || !isset($state['start'],$state['count'])
            || $now < (int)$state['start'] || $now - (int)$state['start'] >= $window) {
            $state = ['start'=>$now,'count'=>0];
        }
        $state['count'] = min($limit + 1, (int)$state['count'] + 1);
        $encoded = json_encode($state);
        rewind($handle);
        if (!ftruncate($handle,0) || fwrite($handle,$encoded) !== strlen($encoded) || !fflush($handle)) return null;
        return ['blocked'=>$state['count']>$limit,'remaining'=>max(0,$limit-$state['count']),
            'reset'=>(int)$state['start']+$window];
    } finally { flock($handle,LOCK_UN); fclose($handle); }
}


/**
 * Ambil custom header dengan beberapa fallback agar kompatibel dengan Apache,
 * LiteSpeed, CGI/FastCGI, dan shared hosting.
 */
function transiva_request_header(string $name): string
{
    $normalized = strtoupper(str_replace('-', '_', $name));
    $serverKey = 'HTTP_' . $normalized;

    if (isset($_SERVER[$serverKey])) {
        return trim((string)$_SERVER[$serverKey]);
    }

    // Beberapa setup proxy/CGI dapat meneruskan header dengan REDIRECT_HTTP_*.
    $redirectKey = 'REDIRECT_HTTP_' . $normalized;
    if (isset($_SERVER[$redirectKey])) {
        return trim((string)$_SERVER[$redirectKey]);
    }

    if (function_exists('getallheaders')) {
        $headers = getallheaders();
        if (is_array($headers)) {
            foreach ($headers as $key => $value) {
                if (strcasecmp((string)$key, $name) === 0) {
                    return trim((string)$value);
                }
            }
        }
    }

    return '';
}

function transiva_is_stress_target(string $script = ''): bool
{
    // V6: endpoint getMerchantOrders.php menetapkan flag ini SEBELUM config.php dimuat.
    // Ini stabil walaupun LiteSpeed/Apache rewrite mengubah SCRIPT_NAME/PHP_SELF.
    if (defined('TRANSIVA_STRESS_TARGET') && TRANSIVA_STRESS_TARGET === true) {
        return strtoupper(trim((string)($_SERVER['REQUEST_METHOD'] ?? ''))) === 'GET'
            && (string)($_GET['load_test'] ?? '') === '1';
    }

    // Fallback untuk server tanpa rewrite.
    return strtolower($script) === 'getmerchantorders.php'
        && strtoupper(trim((string)($_SERVER['REQUEST_METHOD'] ?? ''))) === 'GET'
        && (string)($_GET['load_test'] ?? '') === '1';
}

/**
 * Bypass rate limiter hanya untuk stress test read-only Merchant Orders.
 * Secret salah/tidak terbaca TIDAK dibiarkan jatuh ke limiter default karena itu
 * menghasilkan false-positive 429 setelah 240 request. Untuk target load_test,
 * secret invalid akan ditolak eksplisit 403 pada bootstrap.
 */
function transiva_rate_limit_stress_bypass(string $script = ''): bool
{
    if (!transiva_is_stress_target($script)) {
        return false;
    }

    $expected = defined('TRANSIVA_STRESS_TEST_KEY')
        ? trim((string)TRANSIVA_STRESS_TEST_KEY)
        : '';

    if (strlen($expected) < 32) {
        return false;
    }

    $provided = transiva_request_header('X-Transiva-Stress-Key');
    if ($provided === '') {
        return false;
    }

    return hash_equals($expected, $provided);
}

function transiva_rate_limit_policy(string $script): array
{
    if($script==='customer_auth_check.php')return [90,60];
    if($script==='customer_login_preview.php')return [20,60];
    // High-frequency polling/location endpoints: 12 requests/second average.
    if (preg_match('/(location|status|updates|poll|idle_drivers|unified_orders|background_order)/', $script)) {
        return [720, 60];
    }

    // OTP and verification must be strict to prevent SMS/email abuse.
    if (preg_match('/(otp|verifyemail|verification)/', $script)) {
        return [6, 600];
    }

    // Authentication and PIN brute-force protection.
    if (preg_match('/(login|register|pin|password|session)/', $script)) {
        return [15, 300];
    }

    // Money movement and balance mutation.
    if (preg_match('/(wallet|withdraw|topup|balance|transfer)/', $script)) {
        return [20, 300];
    }

    // Order creation/acceptance/cancellation actions.
    if (preg_match('/(createorder|placeorder|takeorder|accept|cancel|order_action|confirm)/', $script)) {
        return [30, 60];
    }

    // Safe default for all other PHP endpoints.
    return [240, 60];
}

function transiva_rate_limit_client_ip(): string
{
    $remote = trim((string)($_SERVER['REMOTE_ADDR'] ?? '0.0.0.0'));
    if (defined('TRUST_PROXY_HEADERS') && TRUST_PROXY_HEADERS) {
        $forwarded = trim((string)($_SERVER['HTTP_X_FORWARDED_FOR'] ?? ''));
        if ($forwarded !== '') {
            $candidate = trim(explode(',', $forwarded)[0]);
            if (filter_var($candidate, FILTER_VALIDATE_IP)) {
                return $candidate;
            }
        }
        $real = trim((string)($_SERVER['HTTP_X_REAL_IP'] ?? ''));
        if (filter_var($real, FILTER_VALIDATE_IP)) {
            return $real;
        }
    }
    return filter_var($remote, FILTER_VALIDATE_IP) ? $remote : '0.0.0.0';
}

function transiva_rate_limit_identity(): string
{
    $headers = [
        $_SERVER['HTTP_X_DEVICE_UUID'] ?? '',
        $_SERVER['HTTP_X_DEVICE_ID'] ?? '',
        $_SERVER['HTTP_X_TRANSIVA_DEVICE'] ?? '',
        $_SERVER['HTTP_AUTHORIZATION'] ?? '',
    ];
    foreach ($headers as $value) {
        $value = trim((string)$value);
        if ($value !== '') {
            return substr(hash('sha256', $value), 0, 24);
        }
    }
    return 'anonymous';
}

function transiva_rate_limit_cleanup(string $root, int $olderThan): void
{
    foreach (glob($root . '/*/*.json') ?: [] as $file) {
        $mtime = @filemtime($file);
        if ($mtime !== false && $mtime < $olderThan) {
            @unlink($file);
        }
    }
}
