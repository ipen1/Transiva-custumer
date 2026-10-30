<?php

declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');

require_once __DIR__.'/transiva_server_route.php';
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/native_auth.php';
require_once __DIR__ . '/ride_voucher_helper.php';
require_once __DIR__ . '/loyalty_helper.php';
require_once __DIR__ . '/customer_benefit_helper.php';
require_once __DIR__ . '/loyalty_season_helper.php';
require_once __DIR__ . '/customer_coin_helper.php';
require_once __DIR__ . '/weather_pricing_helper.php';
require_once __DIR__ . '/transiva_regional_pricing.php';
require_once __DIR__ . '/ride_ecosystem_helper.php';

function ride_quote_response(bool $success, string $message, array $extra = []): void
{
    http_response_code($success ? 200 : 400);

    echo json_encode(
        array_merge([
            'success' => $success,
            'message' => $message,
        ], $extra),
        JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
    );

    exit;
}

function ride_quote_coordinate(array $data, string $group, string $axis): float
{
    $nested = $data[$group] ?? null;

    if (is_array($nested)) {
        $nestedKey = $axis === 'lat' ? 'latitude' : 'longitude';

        if (isset($nested[$nestedKey])) {
            return (float) $nested[$nestedKey];
        }
    }

    $flatKey = $group . '_' . $axis;
    return isset($data[$flatKey]) ? (float) $data[$flatKey] : 0.0;
}

function ride_quote_valid_coordinate(float $lat, float $lng): bool
{
    return is_finite($lat)
        && is_finite($lng)
        && $lat >= -90.0
        && $lat <= 90.0
        && $lng >= -180.0
        && $lng <= 180.0
        && !($lat == 0.0 && $lng == 0.0);
}

function ride_quote_distance_km(
    float $pickupLat,
    float $pickupLng,
    float $deliveryLat,
    float $deliveryLng
): float {
    $earthRadiusKm = 6371.0;
    $latDelta = deg2rad($deliveryLat - $pickupLat);
    $lngDelta = deg2rad($deliveryLng - $pickupLng);

    $a = sin($latDelta / 2) ** 2
        + cos(deg2rad($pickupLat))
        * cos(deg2rad($deliveryLat))
        * sin($lngDelta / 2) ** 2;

    $straightDistance = $earthRadiusKm
        * (2 * atan2(sqrt($a), sqrt(max(0.0, 1.0 - $a))));

    // Faktor jalan agar estimasi tidak memakai garis lurus murni.
    return max(0.1, round($straightDistance * 1.25, 2));
}

function ride_quote_price(mysqli $conn, string $serviceType, float $distanceKm, float $pickupLat=0, float $pickupLng=0): int
{
    return trp_calculate(trp_get_rule($conn,$serviceType,$pickupLat,$pickupLng),$distanceKm);
}


try {
    $user = native_require_user($conn);

    if (strtolower((string) ($user['role'] ?? '')) !== 'customer') {
        ride_quote_response(false, 'Endpoint ini khusus customer.');
    }

    $data = native_input();

    if (!is_array($data)) {
        ride_quote_response(false, 'Payload permintaan tidak valid.');
    }

    $pickupLat = ride_quote_coordinate($data, 'pickup', 'lat');
    $pickupLng = ride_quote_coordinate($data, 'pickup', 'lng');
    $deliveryLat = ride_quote_coordinate($data, 'delivery', 'lat');
    $deliveryLng = ride_quote_coordinate($data, 'delivery', 'lng');

    if (
        !ride_quote_valid_coordinate($pickupLat, $pickupLng)
        || !ride_quote_valid_coordinate($deliveryLat, $deliveryLng)
    ) {
        ride_quote_response(false, 'Koordinat jemput atau tujuan tidak valid.');
    }

    $ecosystem = eco_extract($data);
    $points=[['latitude'=>$pickupLat,'longitude'=>$pickupLng]];foreach($ecosystem['waypoints'] as $w)$points[]=$w;$points[]=['latitude'=>$deliveryLat,'longitude'=>$deliveryLng];
    $serverRoute=tsr_route($points);$distanceKm=tsr_distance($serverRoute,0,160000);

    if ($distanceKm > 160.0) {
        ride_quote_response(false, 'Jarak layanan melebihi batas maksimum.');
    }

    $requestedService = strtolower(trim((string) ($data['service_type'] ?? '')));
    $requestedDriver = strtolower(trim((string) ($data['driver_type'] ?? '')));
    $requestedOrder = strtolower(trim((string) ($data['order_type'] ?? '')));

    $isCar = in_array($requestedService, ['transcar', 'car', 'mobil'], true)
        || in_array($requestedDriver, ['car', 'mobil'], true)
        || in_array($requestedOrder, ['transcar', 'car', 'mobil'], true);

    $serviceType = $isCar ? 'Transcar' : 'Transbike';
    $voucherService = $isCar ? 'TransCar' : 'TransRide';
    $serviceLabel = $isCar ? 'Transcar' : 'TransRide';

    $baseOriginalPrice = ride_quote_price($conn, $serviceType, $distanceKm, $pickupLat, $pickupLng);
    $weatherRule = transiva_weather_rule($conn, $serviceType);
    $originalPrice = transiva_apply_weather_price($conn, $serviceType, $baseOriginalPrice);
    $rainSurcharge = $weatherRule['active'] ? $weatherRule['surcharge'] : 0;

    $voucherCode = strtoupper(trim((string) ($data['voucher_code'] ?? '')));
    // Same ownership, snapshot and discount formula as createOrder.php.
    // Preview never consumes the voucher; creation revalidates under a lock.
    $loyaltyVoucher = ly_apply_voucher($conn,(int)$user['id'],$voucherCode,$originalPrice,$originalPrice,false);
    if ($loyaltyVoucher) {
        $voucher = ['source'=>'loyalty','code'=>$loyaltyVoucher['code'],
            'discount'=>$loyaltyVoucher['discount'],'final_price'=>$originalPrice-$loyaltyVoucher['discount'],
            'title'=>$loyaltyVoucher['title']];
    } else {
        $voucher = rv_apply($conn,(int)$user['id'],$voucherCode,$originalPrice,$voucherService,false);
    }

    $discount = max(0, (int) ($voucher['discount'] ?? 0));
    $finalPrice = max(0, (int) ($voucher['final_price'] ?? $originalPrice));
    $priceMode=strtolower(trim((string)($data['price_mode']??'standard'))); $hematDiscount=0; $coinStats=coin_status($conn,(int)($user['id']??0)); if($priceMode==='hemat' && $finalPrice>0){ $coinQuote=coin_quote_redeem($conn,(int)($user['id']??0),$finalPrice); $hematDiscount=(int)$coinQuote['discount']; $finalPrice=max(0,$finalPrice-$hematDiscount); $discount+=$hematDiscount; } else { $priceMode='standard'; }
    $balance = max(0, (int) ($user['balance'] ?? 0));

    // Estimasi sederhana: motor 25 km/jam, mobil 30 km/jam.
    $averageSpeedKmh = $isCar ? 30.0 : 25.0;
    $durationMinutes=max(1,(int)ceil($serverRoute['routes'][0]['duration']/60));

    ride_quote_response(true, 'Quote ' . $serviceLabel . ' berhasil.', [
        'service' => $serviceLabel,
        'driver_type' => $isCar ? 'car' : 'bike',
        'service_type' => $serviceType,
        'distance_km' => $distanceKm,
        'duration_minutes' => $durationMinutes,
        'estimated_minutes' => $durationMinutes,
        'original_price' => $originalPrice,
        'base_original_price' => $baseOriginalPrice,
        'rain_active' => $weatherRule['active'],
        'rain_surcharge' => $rainSurcharge,
        'standard_price' => $originalPrice,
        'discount' => $discount,
        'discount_breakdown_version' => 2,
        'voucher_discount' => max(0,(int)($voucher['discount']??0)),
        'point_discount' => $hematDiscount,
        'total_discount' => $discount,
        'price' => $finalPrice,
        'final_price' => $finalPrice,
        'voucher_code' => (string) ($voucher['code'] ?? ''),
        'voucher_title' => (string) ($voucher['title'] ?? ''),
        'balance' => $balance,
        'balance_sufficient' => $balance >= $finalPrice,
        'price_mode' => $priceMode,
        'price_guaranteed' => true,
        'hemat_discount' => $hematDiscount,
        'hemat_tier' => $coinStats['tier'],
        'hemat_limit' => $coinStats['min_redeem_coins'],
        'hemat_used' => 0,
        'hemat_remaining' => $coinStats['balance'],
        'hemat_verified' => true,
        'coin_balance' => $coinStats['balance'],
        'coin_value_rupiah' => $coinStats['coin_value_rupiah'],
        'coin_min_redeem' => $coinStats['min_redeem_coins'],
        'coin_earn_percent' => $coinStats['earn_percent'],
        'route_hash'=>$serverRoute['transiva']['route_hash'],
        'waypoint_count' => count($ecosystem['waypoints']),
        'ecosystem' => $ecosystem,
    ]);
} catch (Throwable $error) {
    error_log('ride_payment_quote.php: ' . $error->getMessage());

    ride_quote_response(false, $error->getMessage());
}
