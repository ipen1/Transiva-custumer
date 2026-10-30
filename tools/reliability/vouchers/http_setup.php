<?php
if(PHP_SAPI!=='cli')exit(1);
require_once __DIR__.'/db.php';$c=voucher_ci_db();
$c->multi_query(file_get_contents(__DIR__.'/http-schema.sql'));do{if($r=$c->store_result())$r->free();}while($c->more_results()&&$c->next_result());
