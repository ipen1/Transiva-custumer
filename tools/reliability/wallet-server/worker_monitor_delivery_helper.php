<?php
declare(strict_types=1);
/** Log reminders and mail retries use separate clocks. No recipient is invented. */
function worker_monitor_delivery_alert(array $snapshot, string $email, string $stateFile, ?callable $transport=null, ?int $now=null): array {
    $now=$now??time();$email=trim($email);
    $configured=filter_var($email,FILTER_VALIDATE_EMAIL)!==false && !preg_match('/[\r\n]/',$email);
    $old=umask(0077);$lock=null;
    try {
        $lock=fopen($stateFile,'c+');
        if(!$lock||!flock($lock,LOCK_EX))throw new RuntimeException('Alert state unavailable');
        $prior=json_decode((string)stream_get_contents($lock,65536),true);if(!is_array($prior))$prior=[];
        $bad=empty($snapshot['healthy']);$wasBad=!empty($prior['incident']);
        $changed=$bad!==$wasBad;
        $last=(int)($prior['last_log']??$prior['last_attempt']??0);
        $emit=($bad&&(!$wasBad||$now<$last||$now-$last>=900))||(!$bad&&$wasBad);
        $answer=['logged'=>false,'email_configured'=>$configured,'email_accepted'=>false,'suppressed'=>!$emit];
        if($emit) {
            $event=['at'=>gmdate('c',$now),'state'=>$bad?'unhealthy':'recovered','reasons'=>$snapshot['reasons']??[],
                'heartbeat_age_seconds'=>$snapshot['heartbeat_age_seconds']??null,'queue'=>$snapshot['queue']??[]];
            $line=json_encode($event,JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR).PHP_EOL;
            $log=$stateFile.'.events.jsonl';if(is_file($log)&&filesize($log)>1024*1024){@rename($log,$log.'.previous');}
            if(file_put_contents($log,$line,FILE_APPEND|LOCK_EX)===false)throw new RuntimeException('Alert log unavailable');
            error_log($bad?'TRANSIVA_WORKER_UNHEALTHY':'TRANSIVA_WORKER_RECOVERED');$answer['logged']=true;
        }
        $pending=$changed||$emit||!empty($prior['mail_pending']);
        $failures=$changed?0:(int)($prior['mail_failures']??0);
        $nextTry=$changed?0:(int)($prior['mail_next_try']??0);
        $recipientChanged=$configured&&($prior['recipient_hash']??'')!==hash('sha256',$email);
        if($pending&&$configured&&($recipientChanged||$now>=$nextTry||$now<$last)) {
            $send=$transport??static function(string $to,string $subject,string $body):bool{return mail($to,$subject,$body);};
            $event=['at'=>gmdate('c',$now),'state'=>$bad?'unhealthy':'recovered','reasons'=>$snapshot['reasons']??[],
                'heartbeat_age_seconds'=>$snapshot['heartbeat_age_seconds']??null,'queue'=>$snapshot['queue']??[]];
            $line=json_encode($event,JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR).PHP_EOL;
            try{$answer['email_accepted']=(bool)$send($email,$bad?'Transiva: worker bermasalah':'Transiva: worker pulih',"Pemeriksaan worker Transiva:\n".$line);}
            catch(Throwable $error){error_log('WORKER_ALERT_MAIL_FAILED');}
            if($answer['email_accepted']){$pending=false;$failures=0;$nextTry=0;}
            else{$failures++;$nextTry=$now+min(900,60*(2**min(4,$failures-1)));}
            $answer['suppressed']=false;
        }
        $next=['incident'=>$bad,'last_log'=>$emit?$now:$last,'last_attempt'=>$emit?$now:$last,
            'mail_pending'=>$pending,'mail_failures'=>$failures,'mail_next_try'=>$nextTry,
            'recipient_hash'=>$configured?hash('sha256',$email):''];
        $json=json_encode($next,JSON_THROW_ON_ERROR);rewind($lock);
        if(!ftruncate($lock,0)||fwrite($lock,$json)!==strlen($json)||!fflush($lock))throw new RuntimeException('Alert state write failed');
        return $answer;
    }catch(Throwable $error){error_log('WORKER_MONITOR_ALERT_UNAVAILABLE');return ['logged'=>false,'email_configured'=>$configured,'email_accepted'=>false,'error'=>'ALERT_UNAVAILABLE'];}
    finally{if(is_resource($lock)){flock($lock,LOCK_UN);fclose($lock);}umask($old);}
}
