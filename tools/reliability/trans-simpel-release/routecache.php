<?php
require __DIR__.'/web/transiva_server_route.php';
$variants=[[[119.9,-0.9],[119.92,-0.92]],[[119.9,-0.9],[119.91,-0.915],[119.92,-0.92]],[[119.903,-0.904],[119.923,-0.923]],[[119.903,-0.904],[119.92,-0.92]],[[119.903,-0.904],[119.91,-0.915],[119.92,-0.92]]];
foreach($variants as $geometry){
    $points=tsr_points(array_map(fn($coord)=>['latitude'=>$coord[1],'longitude'=>$coord[0]],$geometry));
    $hash=hash('sha256',json_encode($points));
    $legs=array_fill(0,count($geometry)-1,['distance'=>2500,'duration'=>300]);
    $route=['code'=>'Ok','waypoints'=>array_fill(0,count($geometry),['distance'=>0]),
        'routes'=>[['distance'=>count($legs)*2500,'duration'=>count($legs)*300,'legs'=>$legs,
            'geometry'=>['coordinates'=>$geometry,'type'=>'LineString']]],
        'transiva'=>['route_hash'=>$hash,'points'=>$points]];
    file_put_contents(__DIR__.'/tmp/transiva_route_v2/'.$hash.'.json',json_encode($route));
}
