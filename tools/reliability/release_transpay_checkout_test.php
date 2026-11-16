<?php
declare(strict_types=1);

// The integration check loads the same helper that is shipped in server.zip.
require_once dirname(__DIR__, 2) . '/server/checkout_transpay_proof.php';
require_once dirname(__DIR__, 2) . '/server/transiva_pay_helper.php';

function native_bearer_token(): string {
    return $GLOBALS['testSession'];
}

function check(bool $condition, string $scenario): void {
    if (!$condition) throw new RuntimeException('Failed: ' . $scenario);
}

mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);
$port = (int) (getenv('TEST_DB_PORT') ?: 3306);
$db = new mysqli('127.0.0.1', 'root', (string) getenv('TEST_DB_PASSWORD'), '', $port);
$db->query('CREATE DATABASE IF NOT EXISTS transiva_checkout_ci');
$db->select_db('transiva_checkout_ci');
$db->query('CREATE TEMPORARY TABLE user_pins (user_id INT PRIMARY KEY, pin_hash VARCHAR(255) NOT NULL)');
$hash = password_hash('654321', PASSWORD_DEFAULT);
$stmt = $db->prepare('INSERT INTO user_pins VALUES (7,?)');
$stmt->bind_param('s', $hash);
$stmt->execute();
$stmt->close();

$testSession = 'session-A';
$route = 'create_food_order.php';
$attempt = 'ORD-CI-1';
$order = ['payment_method'=>'balance','user_id'=>'7','id'=>$attempt,
    'restaurant_id'=>51,'items'=>[['id'=>9,'qty'=>2]],'expected_quote'=>str_repeat('a', 64)];
$proof = checkout_transpay_proof($hash, 7, [
    'order_route'=>$route,'order_attempt'=>$attempt,'order_payload'=>$order,
]);
$valid = static fn(array $payload, mixed $value, string $file = 'create_food_order.php',
    string $key = 'ORD-CI-1', int $user = 7): bool =>
        checkout_transpay_authorized($db, $user, $file, $key, $payload, $value);

check($valid($order, $proof), 'valid PIN proof for authorized checkout');
check($valid(array_reverse($order, true), $proof), 'JSON key order');
check(!$valid(array_replace($order, ['expected_quote'=>str_repeat('b', 64)]), $proof), 'changed quote');
check(!$valid(array_replace($order, ['items'=>[['id'=>9,'qty'=>3]]]), $proof), 'changed quantity');
check(!$valid($order, $proof, 'createOrder.php'), 'different endpoint');
check(!$valid($order, $proof, $route, 'ORD-CI-2'), 'different attempt');
check(!$valid($order, $proof, $route, $attempt, 8), 'different account');
check(!$valid($order, null), 'missing approval');
check(!$valid($order, '000000'), 'raw PIN is not an order proof');

$expires = time() - 1;
$expired = $expires . '.' . checkout_transpay_signature($hash, 7, $route, $attempt, $order, $expires);
check(!$valid($order, $expired), 'expired approval');
$testSession = 'session-B';
check(!$valid($order, $proof), 'different bearer session');
$testSession = 'session-A';
$db->query("UPDATE user_pins SET pin_hash='rotated' WHERE user_id=7");
check(!$valid($order, $proof), 'PIN change invalidates approval');

check(tp_is_supported_escrow_order(['order_type'=>'TransShop']), 'TransShop uses escrow for refunds and settlement');

echo "PASS TransPay order proof: session, owner, route, attempt, payload, expiry and PIN rotation\n";

$db->query('CREATE TEMPORARY TABLE users (id INT PRIMARY KEY, balance INT NOT NULL) ENGINE=InnoDB');
$db->query('INSERT INTO users VALUES (7,1000)');
$db->query("CREATE TEMPORARY TABLE orders (
    id INT PRIMARY KEY, user_id INT NOT NULL, order_id VARCHAR(80) NOT NULL,
    order_type VARCHAR(16) NOT NULL, price INT NOT NULL, note TEXT NOT NULL,
    payment_method VARCHAR(16) NOT NULL, payment_status VARCHAR(16) NOT NULL,
    driver_settlement_status VARCHAR(16) NOT NULL, refunded_at DATETIME NULL
) ENGINE=InnoDB");
$db->query('CREATE TEMPORARY TABLE duitku_sandbox_payments (
    merchant_order_id VARCHAR(48) PRIMARY KEY, user_id INT, amount INT, state VARCHAR(16)
) ENGINE=InnoDB');
$db->query('CREATE TEMPORARY TABLE duitku_internal_order_fulfillment (
    merchant_order_id VARCHAR(48) PRIMARY KEY, order_db_id INT
) ENGINE=InnoDB');
$db->query('CREATE TABLE IF NOT EXISTS wallet_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY, user_id INT, type VARCHAR(32), direction VARCHAR(3),
    amount INT, balance_before INT, balance_after INT, status VARCHAR(16), reference VARCHAR(100),
    description TEXT, created_at DATETIME, UNIQUE KEY wallet_refund (user_id, type, reference)
) ENGINE=InnoDB');
$db->query('DELETE FROM wallet_transactions');
$suffix = str_repeat('1', 28);
$db->query("INSERT INTO orders VALUES
    (81,7,'ORD-QR-$suffix','Transcar',5000,'{}','qris','paid','none', NULL)");
$db->query("INSERT INTO duitku_sandbox_payments VALUES ('TXS-$suffix',7,5000,'paid')");
$db->query("INSERT INTO duitku_internal_order_fulfillment VALUES ('TXS-$suffix',81)");

putenv('DUITKU_SANDBOX_DRIVER_WALLET_DATABASE=wrong_test_database');
$db->begin_transaction();
$orderRow = $db->query('SELECT * FROM orders WHERE id=81 FOR UPDATE')->fetch_assoc();
$pending = tp_refund_held_order($db, $orderRow);
$db->commit();
check(!empty($pending['refund_pending']), 'unavailable wallet keeps refund pending');
check((int) $db->query('SELECT balance FROM users WHERE id=7')->fetch_row()[0] === 1000, 'pending refund changes no balance');

putenv('DUITKU_SANDBOX_DRIVER_WALLET_DATABASE=transiva_checkout_ci');
$db->begin_transaction();
$orderRow = $db->query('SELECT * FROM orders WHERE id=81 FOR UPDATE')->fetch_assoc();
$refund = tp_refund_held_order($db, $orderRow);
$db->commit();
check(!empty($refund['refunded']) && $refund['balance'] === 6000, 'verified QRIS credits TransPay');
$db->begin_transaction();
$orderRow = $db->query('SELECT * FROM orders WHERE id=81 FOR UPDATE')->fetch_assoc();
$replay = tp_refund_held_order($db, $orderRow);
$db->commit();
check(empty($replay['refunded']) && $orderRow['payment_status'] === 'refunded', 'retry respects recorded payment state');
$row = $db->query("SELECT COUNT(*) AS mutations, SUM(amount) AS amount FROM wallet_transactions
    WHERE type='refund' AND reference='REFUND-ORD-QR-$suffix'")->fetch_assoc();
check((int) $row['mutations'] === 1 && (int) $row['amount'] === 5000, 'one exact refund ledger entry');
echo "PASS QRIS refund: pending state, verified amount, wallet credit and idempotent retry\n";
