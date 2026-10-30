<?php
declare(strict_types=1);
/** These credentials are CI inputs; the database name is deliberately fixed and disposable. */
function voucher_ci_db(): mysqli {
    $db=getenv('TEST_DB_NAME')?:'transiva_voucher_ci_test';
    if($db!=='transiva_voucher_ci_test')throw new RuntimeException('Only transiva_voucher_ci_test is allowed. Never use a hosting database.');
    mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
    $c=new mysqli(getenv('TEST_DB_HOST')?:'127.0.0.1',getenv('TEST_DB_USER')?:'root',getenv('TEST_DB_PASSWORD')?:'',null,(int)(getenv('TEST_DB_PORT')?:3306));
    $c->query("CREATE DATABASE IF NOT EXISTS `$db`");$c->select_db($db);
    $c->set_charset('utf8mb4');
    return $c;
}
