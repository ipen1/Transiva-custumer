<?php

require_once __DIR__."/legacy_auth_bridge.php";
$profileAuth=legacy_require_user($conn,["customer"]);
require_once "customer_profile_helper.php";

try {
    profile_bootstrap($conn);

    $id = (int)$profileAuth["id"];

    if ($id <= 0) {
        profile_out(
            false,
            "ID pengguna tidak valid",
            array(),
            422
        );
    }

    $user =
        profile_user(
            $conn,
            $id
        );

    if (!$user) {
        profile_out(
            false,
            "Pengguna tidak ditemukan",
            array(),
            404
        );
    }

    profile_out(
        true,
        "Profil berhasil dimuat",
        array(
            "user" => $user
        )
    );

} catch (Throwable $error) {
    $reason=in_array($error->getMessage(),["PROFILE_SCHEMA_UNAVAILABLE","PROFILE_REQUIRED_COLUMN_MISSING","PROFILE_READ_UNAVAILABLE"],true)?$error->getMessage():"UNEXPECTED";
    error_log("PROFILE_READ_FAILED reason=".$reason." type=".get_class($error));
    profile_out(
        false,
        "Gagal memuat profil",
        array(
            "code" => "PROFILE_UNAVAILABLE"
        ),
        500
    );
}
