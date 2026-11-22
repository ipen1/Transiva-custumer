<?php
require __DIR__.'/web/config.php';
require __DIR__.'/web/driver_dispatch_helper.php';
require __DIR__.'/web/food_pending_expiry_helper.php';
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
date_default_timezone_set('Asia/Makassar');
$conn->query("SET time_zone='+08:00'");
if ($argv[1]==='dispatch') {
  $cursor=[];
  echo json_encode(dd_dispatch_due_orders($conn,20,15,20,null,$cursor));
} else {
  echo fpe_expire_batch($conn,10);
}
