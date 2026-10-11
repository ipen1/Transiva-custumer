<?php

if (function_exists("mysqli_report")) {
    mysqli_report(MYSQLI_REPORT_OFF);
}

function profile_out(
    $success,
    $message,
    $extra = array(),
    $code = 200
) {
    http_response_code((int)$code);
    header("Content-Type: application/json; charset=utf-8");
    header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");

    echo json_encode(
        array_merge(
            array(
                "success" => (bool)$success,
                "message" => (string)$message
            ),
            is_array($extra)
                ? $extra
                : array()
        ),
        JSON_UNESCAPED_UNICODE
    );

    exit;
}

function profile_column_exists(
    $conn,
    $column
) {
    $safe =
        mysqli_real_escape_string(
            $conn,
            $column
        );

    $result =
        mysqli_query(
            $conn,
            "SHOW COLUMNS FROM users LIKE '$safe'"
        );

    return $result
        && mysqli_num_rows($result) > 0;
}

function profile_bootstrap($conn) {
    // Schema profile customer sudah disiapkan saat deployment; tanpa DDL runtime.
}

function profile_user(
    $conn,
    $id
) {
    static $known=[];
    $key=spl_object_id($conn);
    if(!isset($known[$key])){
        $columns=mysqli_query($conn,"SHOW COLUMNS FROM users");
        if(!$columns)throw new RuntimeException('PROFILE_SCHEMA_UNAVAILABLE');
        $known[$key]=[];while($column=mysqli_fetch_assoc($columns))$known[$key][(string)$column['Field']]=true;
    }
    $fields=['id'=>'NULL','username'=>"''",'email'=>"''",'phone'=>"''",'role'=>"''",'balance'=>'0','profile_photo'=>"''",'delivery_address'=>"''",'delivery_lat'=>'NULL','delivery_lng'=>'NULL','email_verified'=>'0','verified_by_admin'=>'0','email_verified_at'=>'NULL','created_at'=>'NULL'];
    foreach(['id','username','role'] as $required)if(!isset($known[$key][$required]))throw new RuntimeException('PROFILE_REQUIRED_COLUMN_MISSING');
    $select=[];foreach($fields as $field=>$fallback)$select[]=isset($known[$key][$field])?"`$field`":"$fallback AS `$field`";
    $stmt=mysqli_prepare($conn,'SELECT '.implode(',',$select).' FROM users WHERE id=? LIMIT 1');
    if(!$stmt)throw new RuntimeException('PROFILE_READ_UNAVAILABLE');

    mysqli_stmt_bind_param(
        $stmt,
        "i",
        $id
    );

    if(!mysqli_stmt_execute($stmt)){mysqli_stmt_close($stmt);throw new RuntimeException("PROFILE_READ_UNAVAILABLE");}

    mysqli_stmt_bind_result(
        $stmt,
        $dbId,
        $username,
        $email,
        $phone,
        $role,
        $balance,
        $profilePhoto,
        $deliveryAddress,
        $deliveryLat,
        $deliveryLng,
        $emailVerified,
        $verifiedByAdmin,
        $emailVerifiedAt,
        $createdAt
    );

    if (!mysqli_stmt_fetch($stmt)) {
        mysqli_stmt_close($stmt);
        return null;
    }

    mysqli_stmt_close($stmt);

    return array(
        "id" => (int)$dbId,
        "username" => (string)$username,
        "email" => (string)$email,
        "phone" => (string)$phone,
        "phone_number" => (string)$phone,
        "role" => (string)$role,
        "balance" => (int)$balance,
        "profile_photo" => (string)$profilePhoto,
        "photo" => (string)$profilePhoto,
        "delivery_address" => (string)$deliveryAddress,
        "delivery_lat" => (string)$deliveryLat,
        "delivery_lng" => (string)$deliveryLng,
        "email_verified" => (int)$emailVerified,
        "verified_by_admin" => (int)$verifiedByAdmin,
        "email_verified_at" => $emailVerifiedAt,
        "created_at" => $createdAt
    );
}

function profile_clean_phone(
    $value
) {
    $clean = preg_replace('/[^0-9]/', '', (string)$value);
    $clean = (string)$clean;

    if (strpos($clean, '0062') === 0) {
        $clean = substr($clean, 2);
    }
    if (strpos($clean, '0') === 0) {
        $clean = '62' . substr($clean, 1);
    } elseif (strpos($clean, '8') === 0) {
        $clean = '62' . $clean;
    } elseif ($clean !== '' && strpos($clean, '62') !== 0) {
        $clean = '62' . $clean;
    }

    return substr($clean, 0, 15);
}
