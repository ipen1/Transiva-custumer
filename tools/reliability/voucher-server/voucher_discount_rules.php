<?php
declare(strict_types=1);
/** Voucher discounts cover delivery only. min_order always checks the full order total. */
function vd_delivery_discount(array $terms, int $total, int $delivery): int {
    if ($total < max(0, (int)($terms['min_order'] ?? 0))) return 0;
    $delivery = max(0, $delivery);
    $value = max(0, (int)($terms['discount_value'] ?? 0));
    $discount = ($terms['discount_type'] ?? 'fixed') === 'percent'
        ? intdiv($delivery * min(100, $value), 100) : $value;
    $cap = max(0, (int)($terms['max_discount'] ?? 0));
    return min($delivery, $cap > 0 ? min($discount, $cap) : $discount);
}
/** v2 notes keep voucher and points separate. Old notes remain readable without changing history. */
function vd_order_breakdown(array $order): array {
    $note = $order['note'] ?? [];
    if (!is_array($note)) $note = json_decode((string)$note, true) ?: [];
    $raw = max(0, (int)($note['voucher_discount'] ?? $order['voucher_discount'] ?? 0));
    $points = max(0, (int)($note['point_discount'] ?? $note['hemat_discount'] ?? $order['point_discount'] ?? $order['hemat_discount'] ?? 0));
    $voucher = (int)($note['discount_breakdown_version'] ?? $order['discount_breakdown_version'] ?? 0) >= 2
        ? $raw : max(0, $raw - $points);
    // Old ride notes did not store the coin amount; retain their aggregate subsidy unchanged.
    return ['voucher_discount'=>$voucher, 'point_discount'=>$points,
        'hemat_discount'=>$points, 'total_discount'=>$voucher+$points];
}
