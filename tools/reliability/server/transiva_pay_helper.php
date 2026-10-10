<?php

function tp_clean($conn, $value){
  return mysqli_real_escape_string($conn, trim((string)($value ?? "")));
}

function tp_wallet_table_exists($conn){
  $q = mysqli_query($conn, "SHOW TABLES LIKE 'wallet_transactions'");
  return $q && mysqli_num_rows($q) > 0;
}

function tp_col_exists($conn, $table, $col){
  $table = tp_clean($conn, $table);
  $col = tp_clean($conn, $col);
  $q = mysqli_query($conn, "SHOW COLUMNS FROM `$table` LIKE '$col'");
  return $q && mysqli_num_rows($q) > 0;
}

function tp_note_array($note){
  $decoded = json_decode((string)$note, true);
  return is_array($decoded) ? $decoded : [];
}

function tp_save_note($conn, $orderId, $note){
  $id = intval($orderId);
  $safe = tp_clean($conn, json_encode($note, JSON_UNESCAPED_UNICODE));
  return mysqli_query($conn, "UPDATE orders SET note='$safe' WHERE id='$id' LIMIT 1");
}

function tp_is_balance_method($method){
  $method = strtolower(trim((string)$method));
  return in_array($method, ["balance", "saldo", "wallet", "transiva_pay", "transivapay"], true);
}

function tp_is_food_order($order){
  $note = tp_note_array($order["note"] ?? "");
  $orderType = strtolower(trim((string)($order["order_type"] ?? "")));
  $noteType = strtolower(trim((string)($note["type"] ?? "")));
  return $orderType === "food" || $noteType === "food";
}

function tp_is_ride_order($order){
  $note = tp_note_array($order["note"] ?? "");
  $orderType = strtolower(trim((string)($order["order_type"] ?? "")));
  $noteType = strtolower(trim((string)($note["type"] ?? "")));
  return in_array($orderType, ["transbike", "transride", "transcar"], true) || $noteType === "ride";
}

function tp_is_supported_escrow_order($order){
  return tp_is_food_order($order) || tp_is_ride_order($order);
}

function tp_get_payment_method($order){
  $note = tp_note_array($order["note"] ?? "");
  $method = $order["payment_method"] ?? ($note["payment_method"] ?? "cash");
  return strtolower(trim((string)$method));
}

function tp_get_payment_status($order){
  $note = tp_note_array($order["note"] ?? "");
  $status = $order["payment_status"] ?? ($note["payment_status"] ?? "unpaid");
  return strtolower(trim((string)$status));
}

function tp_get_escrow_amount($order){
  $note = tp_note_array($order["note"] ?? "");
  $amount = intval($order["escrow_amount"] ?? 0);
  if($amount <= 0){
    $amount = intval($note["escrow_amount"] ?? 0);
  }
  if($amount <= 0){
    $amount = intval($order["price"] ?? 0);
  }
  return $amount;
}

function tp_add_wallet_log($conn, $userId, $type, $amount, $before, $after, $reference, $description){
  if(!tp_wallet_table_exists($conn)){
    return true;
  }

  $userId = intval($userId);
  $type = tp_clean($conn, $type);
  $amount = intval($amount);
  $before = intval($before);
  $after = intval($after);
  $reference = tp_clean($conn, $reference);
  $description = tp_clean($conn, $description);

  $incomingTypes = [
    "deposit",
    "transfer_in",
    "refund",
    "withdraw_refund",
    "escrow_release",
    "admin_credit",
    "promo_subsidy",
    "topup"
  ];

  $direction = (
    in_array(strtolower($type), $incomingTypes, true)
    || strpos(strtolower($type), "refund") !== false
    || strpos(strtolower($type), "release") !== false
  ) ? "in" : "out";

  if(tp_col_exists($conn, "wallet_transactions", "direction")){
    return mysqli_query($conn, "
      INSERT INTO wallet_transactions
      (user_id, type, direction, amount, balance_before, balance_after, status, reference, description, created_at)
      VALUES
      ('$userId', '$type', '$direction', '$amount', '$before', '$after', 'success', '$reference', '$description', NOW())
    ");
  }

  return mysqli_query($conn, "
    INSERT INTO wallet_transactions
    (user_id, type, amount, balance_before, balance_after, status, reference, description, created_at)
    VALUES
    ('$userId', '$type', '$amount', '$before', '$after', 'success', '$reference', '$description', NOW())
  ");
}


function tp_adjust_driver_wallet($conn, $driver, $amount, $type, $reference, $description, $direction = "in"){
  $driver = tp_clean($conn, $driver);
  $amount = max(0, intval($amount));
  $type = trim((string)$type);
  $reference = trim((string)$reference);
  $description = trim((string)$description);
  $direction = strtolower(trim((string)$direction)) === "out" ? "out" : "in";

  if($driver === "" || $amount <= 0){
    return ["applied" => false, "amount" => 0, "balance" => null];
  }

  if(tp_wallet_table_exists($conn) && $reference !== ""){
    $safeRef = tp_clean($conn, $reference);
    $safeType = tp_clean($conn, $type);
    $check = mysqli_query($conn, "SELECT id FROM wallet_transactions WHERE type='$safeType' AND reference='$safeRef' LIMIT 1");
    if(!$check) throw new Exception("Gagal memeriksa mutasi saldo driver");
    if(mysqli_num_rows($check) > 0){
      return ["applied" => false, "amount" => 0, "balance" => null, "already_applied" => true];
    }
  }

  $q = mysqli_query($conn, "SELECT id,balance FROM users WHERE username='$driver' AND role='driver' LIMIT 1 FOR UPDATE");
  if(!$q || mysqli_num_rows($q) <= 0) throw new Exception("Driver tidak ditemukan untuk mutasi saldo");
  $u = mysqli_fetch_assoc($q);
  $driverId = intval($u['id'] ?? 0);
  $before = intval($u['balance'] ?? 0);
  $after = $direction === "out" ? $before - $amount : $before + $amount;
  if($after < 0) throw new Exception("Saldo driver tidak cukup untuk potongan");

  $op = $direction === "out" ? "-" : "+";
  $upd = mysqli_query($conn, "UPDATE users SET balance = balance $op $amount WHERE id='$driverId' LIMIT 1");
  if(!$upd || mysqli_affected_rows($conn) < 1) throw new Exception("Gagal memperbarui saldo driver");

  if(!tp_add_wallet_log($conn, $driverId, $type, $amount, $before, $after, $reference, $description)){
    throw new Exception("Gagal mencatat mutasi saldo driver");
  }
  return ["applied" => true, "amount" => $amount, "balance_before" => $before, "balance" => $after];
}

function tp_refund_held_order($conn, $order, $reason = "Refund pembatalan pesanan"){
  if(!tp_is_supported_escrow_order($order) || !tp_is_balance_method(tp_get_payment_method($order))){
    return ["refunded" => false, "amount" => 0, "balance" => null];
  }

  $paymentStatus = tp_get_payment_status($order);
  if(!in_array($paymentStatus, ["paid", "paid_held", "held"], true)){
    return ["refunded" => false, "amount" => 0, "balance" => null, "status" => $paymentStatus];
  }

  $userId = intval($order["user_id"] ?? 0);
  $orderCode = tp_clean($conn, $order["order_id"] ?? "");
  $amount = tp_get_escrow_amount($order);

  // Split Bill: refund harus kembali ke setiap peserta sesuai nominal hold, bukan seluruhnya ke pemesan.
  $splitTable=mysqli_query($conn,"SHOW TABLES LIKE 'split_bill_payments'");
  if($splitTable && mysqli_num_rows($splitTable)>0 && $orderCode!==""){
    $safeOrder=tp_clean($conn,$orderCode);$sq=mysqli_query($conn,"SELECT id,user_id,amount,status FROM split_bill_payments WHERE order_id='$safeOrder' AND status='held' FOR UPDATE");
    if($sq && mysqli_num_rows($sq)>0){$totalRefund=0;$balances=[];while($sp=mysqli_fetch_assoc($sq)){$sid=(int)$sp['id'];$suid=(int)$sp['user_id'];$samt=(int)$sp['amount'];if($suid<=0||$samt<=0)continue;$uq=mysqli_query($conn,"SELECT balance FROM users WHERE id='$suid' LIMIT 1 FOR UPDATE");$uu=$uq?mysqli_fetch_assoc($uq):null;if(!$uu)throw new Exception("Peserta Split Bill tidak ditemukan saat refund");$before=(int)$uu['balance'];$after=$before+$samt;if(!mysqli_query($conn,"UPDATE users SET balance=balance+$samt WHERE id='$suid' LIMIT 1"))throw new Exception("Gagal refund peserta Split Pay");if(!tp_add_wallet_log($conn,$suid,'refund',$samt,$before,$after,'REFUND-SPLIT-'.$orderCode.'-U'.$suid,'Refund Split Pay '.$orderCode))throw new Exception("Gagal mencatat refund Split Bill");mysqli_query($conn,"UPDATE split_bill_payments SET status='refunded' WHERE id='$sid' LIMIT 1");$totalRefund+=$samt;$balances[(string)$suid]=$after;}
      $note=tp_note_array($order["note"]??"");$note["payment_status"]="refunded";$note["refunded_at"]=date("Y-m-d H:i:s");tp_save_note($conn,intval($order["id"]),$note);mysqli_query($conn,"UPDATE orders SET payment_status='refunded',refunded_at=NOW(),driver_settlement_status='refunded' WHERE id='".intval($order["id"])."' LIMIT 1");return ["refunded"=>true,"amount"=>$totalRefund,"balance"=>null,"split"=>true,"participant_balances"=>$balances];}
  }

  if($userId <= 0 || $orderCode === "" || $amount <= 0){
    throw new Exception("Data refund tidak valid");
  }

  $reference = "REFUND-" . $orderCode;

  if(tp_wallet_table_exists($conn)){
    $ref = tp_clean($conn, $reference);
    $check = mysqli_query($conn, "
      SELECT id FROM wallet_transactions
      WHERE user_id='$userId'
      AND type='refund'
      AND reference='$ref'
      LIMIT 1
    ");

    if(!$check){
      throw new Exception("Gagal memeriksa refund");
    }

    if(mysqli_num_rows($check) > 0){
      return ["refunded" => false, "amount" => 0, "balance" => null, "already_refunded" => true];
    }
  }

  $lockUser = mysqli_query($conn, "
    SELECT id, balance
    FROM users
    WHERE id='$userId'
    LIMIT 1
    FOR UPDATE
  ");

  if(!$lockUser || mysqli_num_rows($lockUser) <= 0){
    throw new Exception("Customer tidak ditemukan untuk refund");
  }

  $user = mysqli_fetch_assoc($lockUser);
  $before = intval($user["balance"] ?? 0);
  $after = $before + $amount;

  $update = mysqli_query($conn, "
    UPDATE users
    SET balance = balance + $amount
    WHERE id='$userId'
    LIMIT 1
  ");

  if(!$update || mysqli_affected_rows($conn) < 1){
    throw new Exception("Gagal mengembalikan saldo customer");
  }

  if(!tp_add_wallet_log($conn, $userId, "refund", $amount, $before, $after, $reference, $reason . " " . $orderCode)){
    throw new Exception("Gagal mencatat refund saldo");
  }

  $note = tp_note_array($order["note"] ?? "");
  $note["payment_status"] = "refunded";
  $note["refunded_at"] = date("Y-m-d H:i:s");
  tp_save_note($conn, intval($order["id"]), $note);

  mysqli_query($conn, "
    UPDATE orders
    SET payment_status='refunded', refunded_at=NOW(), driver_settlement_status='refunded'
    WHERE id='".intval($order["id"])."'
    LIMIT 1
  ");

  return ["refunded" => true, "amount" => $amount, "balance" => $after];
}

function tp_release_held_order_to_driver($conn, $order, $releaseAmount = null){
  if(!tp_is_supported_escrow_order($order) || !tp_is_balance_method(tp_get_payment_method($order))){
    return ["released" => false, "amount" => 0, "balance" => null];
  }

  $paymentStatus = tp_get_payment_status($order);
  if($paymentStatus === "released"){
    return ["released" => false, "amount" => 0, "balance" => null, "already_released" => true];
  }

  if(!in_array($paymentStatus, ["paid", "paid_held", "held"], true)){
    return ["released" => false, "amount" => 0, "balance" => null, "status" => $paymentStatus];
  }

  $driver = tp_clean($conn, $order["driver"] ?? "");
  $orderCode = tp_clean($conn, $order["order_id"] ?? "");
  $amount = $releaseAmount === null ? tp_get_escrow_amount($order) : intval($releaseAmount);

  if($driver === "" || $orderCode === "" || $amount <= 0){
    throw new Exception("Data pencairan saldo tidak valid");
  }

  $reference = "RELEASE-" . $orderCode;

  if(tp_wallet_table_exists($conn)){
    $ref = tp_clean($conn, $reference);
    $check = mysqli_query($conn, "
      SELECT id FROM wallet_transactions
      WHERE type='escrow_release'
      AND reference='$ref'
      LIMIT 1
    ");

    if(!$check){
      throw new Exception("Gagal memeriksa pencairan saldo");
    }

    if(mysqli_num_rows($check) > 0){
      return ["released" => false, "amount" => 0, "balance" => null, "already_released" => true];
    }
  }

  $lockDriver = mysqli_query($conn, "
    SELECT id, balance
    FROM users
    WHERE username='$driver'
    AND role='driver'
    LIMIT 1
    FOR UPDATE
  ");

  if(!$lockDriver || mysqli_num_rows($lockDriver) <= 0){
    throw new Exception("Driver tidak ditemukan untuk pencairan saldo");
  }

  $driverData = mysqli_fetch_assoc($lockDriver);
  $driverId = intval($driverData["id"] ?? 0);
  $before = intval($driverData["balance"] ?? 0);
  $after = $before + $amount;

  $update = mysqli_query($conn, "
    UPDATE users
    SET balance = balance + $amount
    WHERE id='$driverId'
    LIMIT 1
  ");

  if(!$update || mysqli_affected_rows($conn) < 1){
    throw new Exception("Gagal mencairkan saldo ke driver");
  }

  if(!tp_add_wallet_log($conn, $driverId, "escrow_release", $amount, $before, $after, $reference, "Pencairan saldo tertahan pesanan " . $orderCode)){
    throw new Exception("Gagal mencatat pencairan saldo driver");
  }

  $note = tp_note_array($order["note"] ?? "");
  $note["payment_status"] = "released";
  $note["escrow_released_at"] = date("Y-m-d H:i:s");
  tp_save_note($conn, intval($order["id"]), $note);

  mysqli_query($conn, "
    UPDATE orders
    SET payment_status='released', escrow_released_at=NOW(), driver_settlement_status='paid_to_driver'
    WHERE id='".intval($order["id"])."'
    LIMIT 1
  ");

  $safeSplitOrder=tp_clean($conn,(string)($order["order_id"]??""));
  if($safeSplitOrder!=="") @mysqli_query($conn,"UPDATE split_bill_payments SET status='settled' WHERE order_id='$safeSplitOrder' AND status='held'");
  return ["released" => true, "amount" => $amount, "balance" => $after];
}

?>
