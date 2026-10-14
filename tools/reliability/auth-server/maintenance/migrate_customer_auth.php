<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
require dirname(__DIR__).'/config.php';require dirname(__DIR__).'/customer_registration_helper.php';
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
try{
    $engine=$conn->query("SHOW TABLE STATUS WHERE Name='users'")->fetch_assoc();
    if(!$engine || strcasecmp($engine['Engine'],'InnoDB')!==0)throw new RuntimeException('users harus InnoDB.');
    $seen=['username'=>[],'email'=>[],'phone'=>[]];$conflicts=[];$phones=[];
    $rows=$conn->query('SELECT id,username,email,phone,role FROM users');
    while($r=$rows->fetch_assoc()){
        $keys=['username'=>strtolower(trim($r['username']))];
        if(strtolower(trim($r['role']))==='customer'){
            $keys['email']=strtolower(trim((string)$r['email']));$keys['phone']=cr_phone((string)$r['phone']);$phones[(int)$r['id']]=$keys['phone'];
        }
        foreach($keys as $k=>$v){if($v==='')continue;if(isset($seen[$k][$v]))$conflicts[]=$k.' IDs '.$seen[$k][$v].', '.$r['id'];else $seen[$k][$v]=$r['id'];}
    }
    if($conflicts)throw new RuntimeException("Duplikat harus ditinjau manual, tidak ada akun dihapus:\n".implode("\n",$conflicts));
    if(!in_array('--apply',$argv,true)){echo "PASS preflight. Jalankan dengan --apply saat signup dihentikan sementara dan backup tersedia.\n";exit;}
    // Run in a maintenance window: MySQL ALTER commits implicitly.
    $st=$conn->prepare('UPDATE users SET phone=? WHERE id=?');foreach($phones as $id=>$phone){$st->bind_param('si',$phone,$id);$st->execute();}
    foreach(['email_verified'=>'TINYINT NOT NULL DEFAULT 0','email_verified_at'=>'DATETIME NULL'] as $col=>$def){if(!$conn->query("SHOW COLUMNS FROM users LIKE '$col'")->num_rows)$conn->query("ALTER TABLE users ADD COLUMN `$col` $def");}
    $defs=[
      'auth_username_key'=>"VARCHAR(191) GENERATED ALWAYS AS (NULLIF(LOWER(TRIM(username)),'')) STORED",
      'auth_customer_email_key'=>"VARCHAR(254) GENERATED ALWAYS AS (CASE WHEN LOWER(TRIM(role))='customer' THEN NULLIF(LOWER(TRIM(email)),'') ELSE NULL END) STORED",
      'auth_customer_phone_key'=>"VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN LOWER(TRIM(role))='customer' THEN NULLIF(phone,'') ELSE NULL END) STORED"
    ];
    foreach($defs as $col=>$def){if(!$conn->query("SHOW COLUMNS FROM users LIKE '$col'")->num_rows)$conn->query("ALTER TABLE users ADD COLUMN `$col` $def");}
    foreach(array_keys($defs) as $col){$idx='uq_'.$col;if(!$conn->query("SHOW INDEX FROM users WHERE Key_name='$idx'")->num_rows)$conn->query("ALTER TABLE users ADD UNIQUE INDEX `$idx` (`$col`)");}
    $conn->query("CREATE TABLE IF NOT EXISTS customer_registration_receipts(operation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, request_hash CHAR(64) CHARACTER SET ascii NOT NULL,password_proof VARCHAR(255) NOT NULL,response_json MEDIUMTEXT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
    $conn->query("CREATE TABLE IF NOT EXISTS customer_email_challenges(user_id INT NOT NULL PRIMARY KEY,email VARCHAR(254) NOT NULL,otp_hash VARCHAR(255) NULL,expires_at DATETIME NULL,resend_after DATETIME NULL,attempts INT NOT NULL DEFAULT 0,proof_hash CHAR(64) NULL,proof_expires_at DATETIME NULL,consumed_at DATETIME NULL) ENGINE=InnoDB");
    echo "PASS customer auth schema installed.\n";
}catch(Throwable $ex){fwrite(STDERR,'FAIL '.$ex->getMessage()."\n");exit(1);}
