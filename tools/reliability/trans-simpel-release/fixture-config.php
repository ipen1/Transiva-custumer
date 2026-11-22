<?php
date_default_timezone_set('Asia/Makassar');
mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);
define('TRANSIVA_DEVICE_HASH_SECRET','local-courier-fixture');
$conn=new mysqli('127.0.0.1','root',getenv('TRANSIVA_TEST_DB_PASSWORD')?:'',
    'courier_test',(int)(getenv('TRANSIVA_TEST_DB_PORT')?:3306));
$conn->set_charset('utf8mb4');
$conn->query("SET time_zone='+08:00'");
