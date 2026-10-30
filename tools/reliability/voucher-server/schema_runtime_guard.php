<?php
declare(strict_types=1);
/** Deployment DDL must never implicitly commit a live payment/refund transaction. */
function transiva_schema_query(mysqli $conn,string $sql,int $resultMode=MYSQLI_STORE_RESULT) {
    if(!preg_match('/^\s*(CREATE\s+(?:TABLE|(?:UNIQUE\s+)?INDEX)|ALTER\s+TABLE)\b/i',$sql))
        return mysqli_query($conn,$sql,$resultMode);
    if(PHP_SAPI==='cli' && defined('TRANSIVA_SCHEMA_DEPLOYMENT') && TRANSIVA_SCHEMA_DEPLOYMENT)
        return mysqli_query($conn,$sql,$resultMode);
    static $ready=[];
    $key=spl_object_id($conn).':'.hash('sha256',$sql);
    if(isset($ready[$key]))return true;
    if(preg_match('/^\s*CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?/i',$sql,$m)){
        $st=mysqli_prepare($conn,'SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? LIMIT 1');
        if($st){mysqli_stmt_bind_param($st,'s',$m[1]);mysqli_stmt_execute($st);$r=mysqli_stmt_get_result($st);$exists=$r&&mysqli_num_rows($r)>0;mysqli_stmt_close($st);if($exists){$ready[$key]=true;return true;}}
    }
    // ALTER callers only reach here when a required column is missing. They must fail safely.
    error_log('SCHEMA_DEPLOYMENT_REQUIRED statement_hash='.hash('sha256',$sql));
    return false;
}
